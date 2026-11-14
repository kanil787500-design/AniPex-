package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.auth.GoogleAuthHelper
import com.example.data.GameRepository
import com.example.data.GuestGameRepository
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.GameViewportScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.GameViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    WindowCompat.getInsetsController(window, window.decorView).apply {
      systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
      hide(WindowInsetsCompat.Type.systemBars())
    }
    setContent {
      MyApplicationTheme {
        AppRoot()
      }
    }
  }
}

@Composable
private fun AppRoot() {
  var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }
  var isGuestMode by remember { mutableStateOf(false) }

  DisposableEffect(Unit) {
    val listener = FirebaseAuth.AuthStateListener { auth ->
      currentUser = auth.currentUser
      if (auth.currentUser != null) {
        isGuestMode = false
      }
    }
    Firebase.auth.addAuthStateListener(listener)
    onDispose {
      Firebase.auth.removeAuthStateListener(listener)
    }
  }

  val user = currentUser
  if (user == null && !isGuestMode) {
    AuthScreen(
      onAuthSuccess = {
        isGuestMode = false
        currentUser = Firebase.auth.currentUser
      },
      onGuestLogin = {
        isGuestMode = true
      },
    )
  } else {
    val activeGuest = user == null && isGuestMode
    val uid = user?.uid ?: "guest_local"
    val displayName = user?.displayName ?: "Guest Traveler"
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }

    key(uid) {
      val viewModel: GameViewModel =
        viewModel(
          key = uid,
          factory =
            viewModelFactory {
              initializer {
                if (activeGuest) {
                  GameViewModel(
                    repository = GuestGameRepository(),
                    currentUserId = uid,
                    initialDisplayName = displayName,
                    isGuestMode = true,
                  )
                } else {
                  val app =
                    checkNotNull(this[APPLICATION_KEY]) {
                      "APPLICATION_KEY missing from CreationExtras"
                    }
                  val databaseId = app.getString(R.string.firestore_database_id)
                  val db = FirebaseFirestore.getInstance(databaseId)
                  GameViewModel(
                    repository = GameRepository(db),
                    currentUserId = uid,
                    initialDisplayName = displayName,
                    isGuestMode = false,
                  )
                }
              }
            },
        )

      GameViewportScreen(
        viewModel = viewModel,
        onSignOut = {
          if (activeGuest) {
            isGuestMode = false
          } else {
            GoogleAuthHelper.signOut(credentialManager, scope) {
              currentUser = null
            }
          }
        },
      )
    }
  }
}
