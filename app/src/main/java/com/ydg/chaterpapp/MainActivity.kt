package com.ydg.chaterpapp

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
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
    private lateinit var loginContainer: View
    private lateinit var userInfoContainer: View
    private lateinit var signInButton: Button
    private lateinit var signOutButton: Button
    private lateinit var userInitialsTextView: TextView
    private lateinit var userEmailTextView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize view references
        loginContainer = findViewById(R.id.login_container)
        userInfoContainer = findViewById(R.id.user_info_container)
        signInButton = findViewById(R.id.sign_in_button)
        signOutButton = findViewById(R.id.sign_out_button)
        userInitialsTextView = findViewById(R.id.user_initials)
        userEmailTextView = findViewById(R.id.user_email)

        // Initialize Firebase Auth
        auth = FirebaseAuth.getInstance()

        // Check if a user is already signed in and update UI accordingly
        updateUI(auth.currentUser?.email)

        // Configure Google Sign-In. Ensure that strings.xml includes a valid default_web_client_id.
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()

        googleSignInClient = GoogleSignIn.getClient(this, gso)

        // Set click listener for the sign-in button
        signInButton.setOnClickListener { signIn() }
        // Set click listener for the logout button
        signOutButton.setOnClickListener { signOut() }
    }

    private fun signIn() {
        val signInIntent = googleSignInClient.signInIntent
        startActivityForResult(signInIntent, RC_SIGN_IN)
    }

    // Handle sign-in results
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == RC_SIGN_IN) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            try {
                // Google Sign-In succeeded, now authenticate with Firebase
                val account = task.getResult(ApiException::class.java)!!
                firebaseAuthWithGoogle(account.idToken!!)
            } catch (e: ApiException) {
                Log.w(TAG, "Google sign in failed", e)
            }
        }
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    Log.d(TAG, "signInWithCredential: success")
                    updateUI(auth.currentUser?.email)
                } else {
                    Log.w(TAG, "signInWithCredential: failure", task.exception)
                }
            }
    }

    // Update UI based on sign-in state
    private fun updateUI(email: String?) {
        if (email.isNullOrEmpty()) {
            // Not signed in: show login container
            loginContainer.visibility = View.VISIBLE
            userInfoContainer.visibility = View.GONE
        } else {
            // Signed in: show user info container
            loginContainer.visibility = View.GONE
            userInfoContainer.visibility = View.VISIBLE

            // Get the first two characters from the email (or the whole email if it's less than 2 characters)
            val initials = if (email.length >= 2) email.substring(0, 2) else email
            userInitialsTextView.text = initials.uppercase()
            userEmailTextView.text = email
        }
    }

    // Sign out the current user
    private fun signOut() {
        // Firebase sign out
        auth.signOut()
        // Google sign out (optional, but recommended)
        googleSignInClient.signOut().addOnCompleteListener {
            // Update UI to show sign-in screen after signing out
            updateUI(null)
        }
    }
}
