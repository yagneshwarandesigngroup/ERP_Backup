package com.ydg.chaterpapp

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.PopupWindowCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider

class MainActivity : AppCompatActivity() {

    companion object {
        private const val RC_SIGN_IN = 9001
        private const val TAG = "MainActivity"
    }

    private lateinit var auth: FirebaseAuth
    private lateinit var googleSignInClient: GoogleSignInClient

    // UI references
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var headerContainer: View
    private lateinit var hamburgerIcon: View
    private lateinit var userBubble: TextView
    private lateinit var signInContainer: View
    private lateinit var signInButton: Button
    private lateinit var mainUIContainer: View
    private lateinit var progressBar: ProgressBar

    // PopupWindow reference for the user dropdown
    private var userPopup: PopupWindow? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize Firebase Auth
        auth = FirebaseAuth.getInstance()

        // Find views
        drawerLayout = findViewById(R.id.drawer_layout)
        headerContainer = findViewById(R.id.header_container)
        hamburgerIcon = findViewById(R.id.hamburger_icon)
        userBubble = findViewById(R.id.user_bubble)
        signInContainer = findViewById(R.id.sign_in_container)
        signInButton = findViewById(R.id.sign_in_button)
        mainUIContainer = findViewById(R.id.main_ui_container)
        progressBar = findViewById(R.id.progress_bar)

        // Check if user is already signed in and update UI accordingly
        updateUI(auth.currentUser?.email)

        // Configure Google Sign-In
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()

        googleSignInClient = GoogleSignIn.getClient(this, gso)

        // Sign in button click
        signInButton.setOnClickListener { signIn() }

        // Hamburger icon click to open sidebar
        hamburgerIcon.setOnClickListener {
            drawerLayout.openDrawer(androidx.core.view.GravityCompat.START)
        }

        // User bubble click to show popup dropdown anchored to bubble from right
        userBubble.setOnClickListener {
            if(userPopup?.isShowing == true){
                userPopup?.dismiss()
            } else {
                showUserPopup()
            }
        }
    }

    // Create and show the PopupWindow anchored to the user bubble
    private fun showUserPopup() {
        val inflater = LayoutInflater.from(this)
        val popupView = inflater.inflate(R.layout.popup_user_dropdown, null)

        // Get popup views
        val popupEmail = popupView.findViewById<TextView>(R.id.popup_email)
        val popupLogout = popupView.findViewById<Button>(R.id.popup_logout)

        // Set email text from current user
        popupEmail.text = auth.currentUser?.email ?: ""

        // Set logout click behavior
        popupLogout.setOnClickListener {
            signOut()
            userPopup?.dismiss()
        }

        // Create the PopupWindow
        userPopup = PopupWindow(popupView, LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT, true)

        // Show the popup anchored to the user bubble with right alignment
        PopupWindowCompat.showAsDropDown(userPopup!!, userBubble, 0, 0, android.view.Gravity.END)
    }

    private fun signIn() {
        // Show progress animation
        progressBar.visibility = View.VISIBLE
        val signInIntent = googleSignInClient.signInIntent
        startActivityForResult(signInIntent, RC_SIGN_IN)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == RC_SIGN_IN) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            try {
                // Google Sign-In succeeded; authenticate with Firebase
                val account = task.getResult(ApiException::class.java)!!
                firebaseAuthWithGoogle(account.idToken!!)
            } catch (e: ApiException) {
                Log.w(TAG, "Google sign in failed", e)
                progressBar.visibility = View.GONE
            }
        }
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                progressBar.visibility = View.GONE
                if (task.isSuccessful) {
                    Log.d(TAG, "signInWithCredential: success")
                    updateUI(auth.currentUser?.email)
                } else {
                    Log.w(TAG, "signInWithCredential: failure", task.exception)
                }
            }
    }

    // Update UI elements based on sign-in state
    private fun updateUI(email: String?) {
        if (email.isNullOrEmpty()) {
            // Not signed in: show sign-in container; hide header and main UI
            signInContainer.visibility = View.VISIBLE
            headerContainer.visibility = View.GONE
            mainUIContainer.visibility = View.GONE
        } else {
            // Signed in: hide sign-in container; show header and main UI
            signInContainer.visibility = View.GONE
            headerContainer.visibility = View.VISIBLE
            mainUIContainer.visibility = View.VISIBLE

            // Set the user bubble text to the first two characters of the email
            val initials = if (email.length >= 2) email.substring(0, 2) else email
            userBubble.text = initials.uppercase()
        }
    }

    private fun signOut() {
        auth.signOut()
        googleSignInClient.signOut().addOnCompleteListener {
            updateUI(null)
            // Dismiss the popup if it is showing
            userPopup?.dismiss()
        }
    }
}
