package dev.csaba.diygpstracker.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.ktx.Firebase
import com.google.firebase.ktx.app
import com.google.firebase.ktx.initialize
import dev.csaba.diygpstracker.ApplicationSingleton
import dev.csaba.diygpstracker.R
import dev.csaba.diygpstracker.data.getAssetId
import dev.csaba.diygpstracker.data.getSecondaryFirebaseConfiguration
import dev.csaba.diygpstracker.data.setAssetId
import dev.csaba.diygpstracker.ui.adapter.AssetAdapter
import dev.csaba.diygpstracker.ui.adapter.OnAssetInputListener
import dev.csaba.diygpstracker.viewmodel.MainViewModel
import kotlinx.android.synthetic.main.activity_main.*
import timber.log.Timber


class MainActivity : AppCompatActivityWithActionBar(), OnAssetInputListener {

    companion object {
        private const val SECONDARY_NAME = "secondary"
        private const val RC_SIGN_IN = 9001
    }

    private lateinit var viewModel: MainViewModel
    private val assetAdapter = AssetAdapter(this)
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val assetId = getAssetId()
        if (assetId.isNotEmpty()) {
            onTrackClick(assetId)
        }

        recycler.layoutManager = LinearLayoutManager(this, RecyclerView.VERTICAL, false)
        recycler.adapter = assetAdapter

        val appSingleton = application as ApplicationSingleton
        val projectConfiguration = this.getSecondaryFirebaseConfiguration()
        // Get or initialize secondary FirebaseApp.
        if (appSingleton.firebaseApp == null) {
            val options = FirebaseOptions.Builder()
                .setProjectId(projectConfiguration.projectId)
                .setApplicationId(projectConfiguration.applicationId)
                .setApiKey(projectConfiguration.apiKey)
                .build()

            Firebase.initialize(applicationContext, options, SECONDARY_NAME)
            appSingleton.firebaseApp = Firebase.app(SECONDARY_NAME)
        }

        // Get FireStore for the secondary app.
        if (appSingleton.firestore == null) {
            appSingleton.firestore = FirebaseFirestore.getInstance(appSingleton.firebaseApp!!).apply {
                firestoreSettings = FirebaseFirestoreSettings.Builder()
                    .setPersistenceEnabled(true)
                    .build()
            }
        }

        // Authenticate
        auth = FirebaseAuth.getInstance(appSingleton.firebaseApp!!)
        val shouldAuthenticate = auth.currentUser == null || auth.currentUser!!.uid.isBlank() ||
                projectConfiguration.authType != "anonymous" && auth.currentUser!!.isAnonymous
        if (shouldAuthenticate) {
            if (projectConfiguration.authType == "google") {
                val signInOptions = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                    .requestIdToken(getString(R.string.default_web_client_id))
                    .requestEmail()
                    .build()

                val account = GoogleSignIn.getLastSignedInAccount(this)
                if (account != null) {
                    firebaseAuthWithGoogle(account)
                } else {
                    val googleSignInClient = GoogleSignIn.getClient(this, signInOptions)
                    val signInIntent = googleSignInClient.signInIntent
                    startActivityForResult(signInIntent, RC_SIGN_IN)
                }
            } else if (projectConfiguration.authType == "email") {
                auth.signInWithEmailAndPassword(projectConfiguration.email, projectConfiguration.code)
                    .addOnCompleteListener(this) { task ->
                        if (task.isSuccessful) {
                            Timber.d("signInWithEmailAndPassword:success")
                            populateViewModel(appSingleton.firestore!!)
                        } else {
                            Timber.e(task.exception, "signInWithEmailAndPassword:failure")
                            Snackbar.make(
                                window.decorView.rootView,
                                applicationContext.getString(R.string.authentication_failed),
                                Snackbar.LENGTH_INDEFINITE
                            ).setAction(R.string.acknowledge) {
                                Timber.d(getString(R.string.authentication_failed))
                            }.show()
                        }
                    }
            } else {
                auth.signInAnonymously()
                    .addOnCompleteListener(this) { task ->
                        if (task.isSuccessful) {
                            Timber.d("signInAnonymously:success")
                            populateViewModel(appSingleton.firestore!!)
                        } else {
                            Timber.e(task.exception, "signInAnonymously:failure")
                            Snackbar.make(
                                window.decorView.rootView,
                                applicationContext.getString(R.string.authentication_failed),
                                Snackbar.LENGTH_INDEFINITE
                            ).setAction(R.string.acknowledge) {
                                Timber.d(getString(R.string.authentication_failed))
                            }.show()
                        }
                    }
            }
        } else {
            populateViewModel(appSingleton.firestore!!)
        }

        showBgLocationWarning()
    }

    private fun showBgLocationWarning() {
        val builder = AlertDialog.Builder(this)
        val arWarning = resources.getString(R.string.bg_location_warning)
        val title = resources.getString(R.string.bg_location_warning_title)
        builder.setMessage(arWarning).setTitle(title).setPositiveButton("OK", null)
        val dialog = builder.create()
        dialog.show()
    }

    public override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == RC_SIGN_IN) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            try {
                // Google Sign In was successful, authenticate with Firebase
                val account = task.getResult(ApiException::class.java)
                firebaseAuthWithGoogle(account!!)
            } catch (e: ApiException) {
                // Google Sign In failed, update UI appropriately
                Timber.e(e, "Google sign in failed")
            }
        }
    }

    private fun firebaseAuthWithGoogle(account: GoogleSignInAccount) {
        Timber.d("firebaseAuthWithGoogle: ${account.id!!}")

        val credential = GoogleAuthProvider.getCredential(account.idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    // Sign in success, update UI with the signed-in user's information
                    Timber.d("signInWithCredential:success")
                    val appSingleton = application as ApplicationSingleton
                    populateViewModel(appSingleton.firestore!!)
                } else {
                    // If sign in fails, display a message to the user.
                    Timber.e(task.exception, "signInWithCredential:failure")
                    Snackbar.make(
                        window.decorView.rootView,
                        applicationContext.getString(R.string.authentication_failed),
                        Snackbar.LENGTH_INDEFINITE
                    ).setAction(R.string.acknowledge) {
                        Timber.d(getString(R.string.authentication_failed))
                    }.show()
                }
            }
    }

    private fun populateViewModel(firestore: FirebaseFirestore) {
        viewModel = MainViewModel(firestore)

        viewModel.assetList.observe(this, {
            assetAdapter.setItems(it)
        })
    }

    override fun onTrackClick(assetId: String) {
        val intent = Intent(this, TrackerActivity::class.java)
        setAssetId(assetId)
        intent.putExtra("assetId", assetId)
        startActivity(intent)
    }
}
