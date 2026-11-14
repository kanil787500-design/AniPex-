package com.example

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.example.ui.shader.CelShaderPreset
import com.example.ui.shader.animeCelLighting
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Aetherbound", appName)
  }

  @Test
  fun `animeCelLighting renders character and environment composables`() {
    composeTestRule.setContent {
      Box(
        modifier =
          Modifier.size(120.dp)
            .animeCelLighting(preset = CelShaderPreset.CHARACTER, rimColor = Color.Cyan)
            .testTag("cel_char_box")
      ) {
        Text("Hero")
      }
      Box(
        modifier =
          Modifier.size(120.dp)
            .animeCelLighting(preset = CelShaderPreset.ENVIRONMENT, rimColor = Color.Yellow)
            .testTag("cel_env_box")
      ) {
        Text("Vista")
      }
    }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("cel_char_box").assertIsDisplayed()
    composeTestRule.onNodeWithTag("cel_env_box").assertIsDisplayed()
  }
}
