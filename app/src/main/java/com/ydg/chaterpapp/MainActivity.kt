package com.ydg.chaterpapp

import android.accounts.Account
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.*

class MainActivity : AppCompatActivity() {

    companion object {
        private const val RC_SIGN_IN = 9001
        private const val TAG = "MainActivity"
        private const val SHEETS_SCOPE = "oauth2:email https://www.googleapis.com/auth/spreadsheets"
    }

    private lateinit var auth: FirebaseAuth
    private lateinit var googleSignInClient: GoogleSignInClient

    // UI references
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var headerContainer: View
    private lateinit var headerProjectName: TextView
    private lateinit var signInContainer: View
    private lateinit var signInButton: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var userBubble: TextView
    private lateinit var mainUIContainer: View
    private lateinit var sidebarList: LinearLayout

    // State
    private var sheetId: String? = null
    private var accessToken: String? = null
    private var userPopup: PopupWindow? = null
    private var currentProject: String = "Project A"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Bind UI elements
        drawerLayout        = findViewById(R.id.drawer_layout)
        headerContainer     = findViewById(R.id.header_container)
        headerProjectName   = findViewById(R.id.header_project_name)
        signInContainer     = findViewById(R.id.sign_in_container)
        signInButton        = findViewById(R.id.sign_in_button)
        progressBar         = findViewById(R.id.progress_bar)
        userBubble          = findViewById(R.id.user_bubble)
        mainUIContainer     = findViewById(R.id.main_ui_container)
        sidebarList         = findViewById(R.id.sidebar_list)

        // Initial UI state
        progressBar.visibility = View.GONE
        updateUI(signedIn = false)

        // Firebase Auth
        auth = FirebaseAuth.getInstance()

        // Google Sign-In config
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .requestScopes(Scope("https://www.googleapis.com/auth/spreadsheets"))
            .requestServerAuthCode(getString(R.string.default_web_client_id))
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)

        // Click handlers
        signInButton.setOnClickListener { startSignIn() }
        userBubble.setOnClickListener { toggleUserPopup() }

        // Make the hamburger icon open the sidebar
        findViewById<View>(R.id.hamburger_icon).setOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
        }
    }

    private fun startSignIn() {
        progressBar.visibility = View.VISIBLE
        startActivityForResult(googleSignInClient.signInIntent, RC_SIGN_IN)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == RC_SIGN_IN) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            try {
                val acct = task.getResult(ApiException::class.java)!!
                handleGoogleAccount(acct)
            } catch (e: ApiException) {
                Log.e(TAG, "Google sign-in failed", e)
                progressBar.visibility = View.GONE
            }
        }
    }

    private fun handleGoogleAccount(acct: com.google.android.gms.auth.api.signin.GoogleSignInAccount) {
        val credential = GoogleAuthProvider.getCredential(acct.idToken, null)
        auth.signInWithCredential(credential).addOnCompleteListener(this) { authTask ->
            progressBar.visibility = View.GONE
            if (!authTask.isSuccessful) {
                Log.e(TAG, "Firebase auth failed", authTask.exception)
                return@addOnCompleteListener
            }
            Log.d(TAG, "Firebase auth successful")
            fetchSheetsAccessToken(acct.account!!)
        }
    }

    private fun fetchSheetsAccessToken(account: Account) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val token = GoogleAuthUtil.getToken(this@MainActivity, account, SHEETS_SCOPE)
                accessToken = token
                Log.d(TAG, "Obtained Sheets token")

                // Retrieve or create spreadsheet
                val email = auth.currentUser?.email ?: ""
                sheetId = GoogleSheetsUtils.getUserSpreadsheet(this@MainActivity, email, token)

                withContext(Dispatchers.Main) {
                    Log.d(TAG, "Spreadsheet ID: $sheetId")
                    updateUI(signedIn = true)
                    loadSheetTabs(token)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Log.e(TAG, "Error getting token/sheet: ${e.message}")
                }
            }
        }
    }

    /** Load sheet tabs from the spreadsheet and populate sidebar. */
    private fun loadSheetTabs(token: String) {
        val sid = sheetId ?: return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val tabs = GoogleSheetsUtils.fetchSheetTabs(sid, token)
                withContext(Dispatchers.Main) {
                    populateSidebar(tabs)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading tabs: ${e.message}")
            }
        }
    }

    /** Dynamically draws the sidebar list of projects + a Logout item. */
    private fun populateSidebar(tabs: List<String>) {
        sidebarList.removeAllViews()
        tabs.forEach { proj ->
            sidebarList.addView(TextView(this).apply {
                text = proj
                textSize = 16f
                setPadding(16,16,16,16)
                setOnClickListener {
                    currentProject = proj
                    headerProjectName.text = proj
                    drawerLayout.closeDrawer(GravityCompat.START)
                    // TODO: refresh chat UI for the new project
                }
            })
        }
        sidebarList.addView(TextView(this).apply {
            text = "Logout"
            setTextColor(Color.RED)
            textSize = 16f
            setPadding(16,16,16,16)
            setOnClickListener {
                auth.signOut()
                googleSignInClient.signOut()
                updateUI(false)
                drawerLayout.closeDrawer(GravityCompat.START)
            }
        })
    }

    /** Shows or hides the user-bubble dropdown. */
    private fun toggleUserPopup() {
        if (userPopup?.isShowing == true) userPopup?.dismiss() else showUserPopup()
    }

    private fun showUserPopup() {
        val popupView = LayoutInflater.from(this)
            .inflate(R.layout.popup_user_dropdown, null)

        // Email label
        popupView.findViewById<TextView>(R.id.popup_email)
            .text = auth.currentUser?.email ?: ""

        // Rename Current Project
        popupView.findViewById<Button>(R.id.popup_rename)
            .setOnClickListener {
                val edit = EditText(this).apply { setText(currentProject) }
                AlertDialog.Builder(this)
                    .setTitle("Rename Current Project")
                    .setView(edit)
                    .setPositiveButton("Rename") { _, _ ->
                        val newName = edit.text.toString().trim()
                        val sid = sheetId; val tok = accessToken
                        if (sid != null && tok != null && newName.isNotEmpty() && newName != currentProject) {
                            CoroutineScope(Dispatchers.IO).launch {
                                try {
                                    GoogleSheetsUtils.renameProjectSheet(sid, currentProject, newName, tok)
                                    currentProject = newName
                                    withContext(Dispatchers.Main) {
                                        headerProjectName.text = newName
                                        loadSheetTabs(tok)
                                        userPopup?.dismiss()
                                    }
                                } catch (e: Exception) {
                                    Log.e(TAG, "Error renaming project: ${e.message}")
                                }
                            }
                        } else {
                            userPopup?.dismiss()
                        }
                    }
                    .setNegativeButton("Cancel") { _, _ ->
                        userPopup?.dismiss()
                    }
                    .show()
            }

        // Add Project
        popupView.findViewById<Button>(R.id.popup_add_project)
            .setOnClickListener {
                val edit = EditText(this)
                AlertDialog.Builder(this)
                    .setTitle("New Project Name")
                    .setView(edit)
                    .setPositiveButton("Create") { _, _ ->
                        val name = edit.text.toString().trim()
                        val sid = sheetId; val tok = accessToken
                        if (name.isNotEmpty() && sid != null && tok != null) {
                            CoroutineScope(Dispatchers.IO).launch {
                                GoogleSheetsUtils.createProjectSheet(sid, name, tok)
                                val newTabs = GoogleSheetsUtils.fetchSheetTabs(sid, tok)
                                withContext(Dispatchers.Main) {
                                    populateSidebar(newTabs)
                                    userPopup?.dismiss()
                                }
                            }
                        }
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }

        // Logout
        popupView.findViewById<Button>(R.id.popup_logout)
            .setOnClickListener {
                auth.signOut()
                googleSignInClient.signOut()
                userPopup?.dismiss()
                updateUI(false)
            }

        userPopup = PopupWindow(
            popupView,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            showAsDropDown(userBubble, 0, 0, GravityCompat.END)
        }
    }

    private fun updateUI(signedIn: Boolean) {
        signInContainer.visibility = if (signedIn) View.GONE else View.VISIBLE
        headerContainer.visibility = if (signedIn) View.VISIBLE else View.GONE
        mainUIContainer.visibility = if (signedIn) View.VISIBLE else View.GONE
        userBubble.visibility      = if (signedIn) View.VISIBLE else View.GONE
        if (signedIn) {
            userBubble.text = auth.currentUser?.email?.take(2)?.uppercase()
            headerProjectName.text = currentProject
        }
    }
}
