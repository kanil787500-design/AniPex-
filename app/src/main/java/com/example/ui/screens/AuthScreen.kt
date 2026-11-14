package com.example.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import com.example.R
import com.example.auth.GoogleAuthHelper
import com.example.ui.shader.CelShaderPreset
import com.example.ui.shader.animeCelLighting
import com.example.ui.theme.AstralCyan
import com.example.ui.theme.AstralGold
import com.example.ui.theme.AstralNight
import com.example.ui.theme.AstralSurface

@Composable
fun AuthScreen(
  onAuthSuccess: () -> Unit,
  onGuestLogin: () -> Unit,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val credentialManager = remember { CredentialManager.create(context) }

  var isLoading by remember { mutableStateOf(false) }
  var errorMessage by remember { mutableStateOf<String?>(null) }

  LaunchedEffect(Unit) {
    GoogleAuthHelper.attemptAutoSignIn(
      context = context,
      credentialManager = credentialManager,
      scope = scope,
      onAuthSuccess = onAuthSuccess,
      onUnauthenticated = {},
    )
  }

  val pulse by
    rememberInfiniteTransition(label = "titlePulse").animateFloat(
      initialValue = 0.78f,
      targetValue = 1f,
      animationSpec = infiniteRepeatable(tween(2200), RepeatMode.Reverse),
      label = "alpha",
    )

  Box(modifier = Modifier.fillMaxSize()) {
    Image(
      painter = painterResource(id = R.drawable.title_splash_art_1794649197942),
      contentDescription = "Aetherbound cel-shaded anime open world vista",
      contentScale = ContentScale.Crop,
      modifier =
        Modifier.fillMaxSize()
          .animeCelLighting(
            preset = CelShaderPreset.ENVIRONMENT,
            rimColor = AstralCyan,
            lightAzimuthDeg = 35f,
            celBands = 3f,
            rimStrength = 0.58f,
            outlineStrength = 0.32f,
            animateRimPulse = true,
          ),
    )

    Box(
      modifier =
        Modifier.fillMaxSize()
          .background(
            Brush.horizontalGradient(
              colors =
                listOf(
                  AstralNight.copy(alpha = 0.86f),
                  AstralNight.copy(alpha = 0.45f),
                  AstralNight.copy(alpha = 0.88f),
                )
            )
          )
    )

    Row(
      modifier =
        Modifier.fillMaxSize()
          .windowInsetsPadding(WindowInsets.safeDrawing)
          .padding(horizontal = 24.dp, vertical = 14.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      // Left Widescreen Branding Pane
      Column(
        modifier = Modifier.weight(1.1f).fillMaxHeight(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.Start,
      ) {
        Surface(
          color = AstralSurface.copy(alpha = 0.82f),
          shape = RoundedCornerShape(50),
          border = BorderStroke(1.dp, AstralCyan.copy(alpha = 0.45f)),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(
              imageVector = Icons.Default.AutoAwesome,
              contentDescription = null,
              tint = AstralGold,
              modifier = Modifier.size(15.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = "3D CEL-SHADED OPEN-WORLD ACTION RPG",
              style = MaterialTheme.typography.labelSmall,
              color = AstralCyan,
            )
          }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
          text = "AETHERBOUND",
          style = MaterialTheme.typography.displaySmall,
          color = Color.White,
          modifier = Modifier.alpha(pulse),
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
          text = "Chronicles of the Shattered Sky",
          style = MaterialTheme.typography.titleMedium,
          color = AstralGold,
        )

        Spacer(modifier = Modifier.height(18.dp))

        Row(
          modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          FeaturePill(Icons.Default.Explore, "3D Touch World", Modifier.weight(1f))
          FeaturePill(Icons.Default.Groups, "Co-op Raids", Modifier.weight(1f))
          FeaturePill(Icons.Default.Shield, "Deep Builds", Modifier.weight(1f))
        }
      }

      Spacer(modifier = Modifier.width(24.dp))

      // Right Widescreen Sign-In Card
      Surface(
        color = AstralSurface.copy(alpha = 0.90f),
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, AstralCyan.copy(alpha = 0.4f)),
        modifier = Modifier.weight(0.9f).widthIn(max = 380.dp),
      ) {
        Column(
          modifier = Modifier.padding(18.dp).verticalScroll(rememberScrollState()),
          horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          Text(
            text = "Enter the Realm",
            style = MaterialTheme.typography.headlineMedium,
            color = Color.White,
          )
          Spacer(modifier = Modifier.height(6.dp))
          Text(
            text = "Sync your hero constellations, weapons, and real-time multiplayer dungeon raids.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
          )

          if (errorMessage != null) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
              text = errorMessage!!,
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.error,
              textAlign = TextAlign.Center,
            )
          }

          Spacer(modifier = Modifier.height(18.dp))

          Button(
            onClick = {
              errorMessage = null
              isLoading = true
              GoogleAuthHelper.onGoogleSignInClicked(
                context = context,
                credentialManager = credentialManager,
                scope = scope,
                onAuthSuccess = {
                  isLoading = false
                  onAuthSuccess()
                },
                onAuthError = { msg ->
                  isLoading = false
                  errorMessage = msg
                },
                onAuthCancelled = {
                  isLoading = false
                },
              )
            },
            enabled = !isLoading,
            colors =
              ButtonDefaults.buttonColors(
                containerColor = AstralCyan,
                contentColor = AstralNight,
              ),
            shape = RoundedCornerShape(14.dp),
            modifier =
              Modifier.fillMaxWidth()
                .height(52.dp)
                .testTag("google_signin_button"),
          ) {
            if (isLoading) {
              CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                color = AstralNight,
                strokeWidth = 2.5.dp,
              )
            } else {
              Text(
                text = "Sign in with Google",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
              )
            }
          }

          Spacer(modifier = Modifier.height(10.dp))

          OutlinedButton(
            onClick = onGuestLogin,
            enabled = !isLoading,
            border = BorderStroke(1.dp, AstralGold.copy(alpha = 0.65f)),
            shape = RoundedCornerShape(14.dp),
            modifier =
              Modifier.fillMaxWidth()
                .height(48.dp)
                .testTag("guest_signin_button"),
          ) {
            Text(
              text = "Play as Guest (Offline)",
              style = MaterialTheme.typography.titleSmall,
              color = AstralGold,
              fontWeight = FontWeight.SemiBold,
            )
          }

          Spacer(modifier = Modifier.height(8.dp))

          Text(
            text = "Guest mode runs locally; Google Sign-In unlocks cloud save & co-op raids.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
          )
        }
      }
    }
  }
}

@Composable
private fun FeaturePill(icon: ImageVector, label: String, modifier: Modifier = Modifier) {
  Surface(
    color = AstralSurface.copy(alpha = 0.80f),
    shape = RoundedCornerShape(12.dp),
    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
    modifier = modifier,
  ) {
    Column(
      modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Icon(icon, contentDescription = null, tint = AstralCyan, modifier = Modifier.size(16.dp))
      Spacer(modifier = Modifier.height(3.dp))
      Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White, textAlign = TextAlign.Center)
    }
  }
}
