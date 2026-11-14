package com.example.ui.shader

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.DrawModifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import com.example.ui.theme.AstralCyan
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Target rendering preset for [animeCelLighting], tuning light-ramp hardness,
 * Angel-Ring character highlights vs. atmospheric environment glow, and ink line weight.
 */
enum class CelShaderPreset(val modeIndex: Float) {
  CHARACTER(0f),
  ENVIRONMENT(1f),
}

/**
 * Immutable configuration for the cel-shaded anime lighting Compose modifier.
 */
@Immutable
data class CelLightingConfig(
  val preset: CelShaderPreset = CelShaderPreset.CHARACTER,
  val lightAzimuthDeg: Float = 42f,
  val lightElevationDeg: Float = 52f,
  val rimColor: Color = AstralCyan,
  val celBands: Float = 3f,
  val rimStrength: Float = 0.72f,
  val outlineStrength: Float = 0.55f,
  val specularStrength: Float = 0.65f,
  val animateRimPulse: Boolean = false,
) {
  fun normalizedLightVector(): Triple<Float, Float, Float> {
    val az = lightAzimuthDeg * (PI.toFloat() / 180f)
    val el = lightElevationDeg.coerceIn(5f, 85f) * (PI.toFloat() / 180f)
    val cosEl = cos(el)
    val lx = cos(az) * cosEl
    val ly = -sin(el) // Screen-space Y points down; elevated sun comes from above (-Y)
    val lz = sin(az) * cosEl + 0.55f
    val len = sqrt(lx * lx + ly * ly + lz * lz).coerceAtLeast(0.0001f)
    return Triple(lx / len, ly / len, lz / len)
  }
}

/**
 * AGSL (Android Graphics Shading Language) runtime shader implementing:
 * 1. Luminance-gradient pseudo-normal estimation for 3D-like directional lighting
 * 2. Crisp multi-band anime cel-shading ramp with cool indigo shadows & warm key highlights
 * 3. Sobel-style ink edge detection for crisp anime silhouettes
 * 4. Character Angel-Ring specular band & Fresnel rim glow (or Environment atmospheric bloom)
 */
object AnimeCelAgsl {
  const val SHADER_SOURCE = """
    uniform shader content;
    uniform float2 iResolution;
    uniform float iTime;
    uniform float3 iLightDir;
    uniform float4 iRimColor;
    uniform float iCelBands;
    uniform float iRimStrength;
    uniform float iOutlineStrength;
    uniform float iSpecStrength;
    uniform float iMode;

    float lumaOf(float3 c) {
      return dot(c, float3(0.2126, 0.7152, 0.0722));
    }

    float3 sampleUnpremul(float2 coord) {
      float2 maxC = max(iResolution - float2(1.0, 1.0), float2(1.0, 1.0));
      float2 p = clamp(coord, float2(0.0, 0.0), maxC);
      half4 s = content.eval(p);
      float a = max(float(s.a), 0.001);
      return float3(s.rgb) / a;
    }

    half4 main(float2 fragCoord) {
      half4 src = content.eval(fragCoord);
      float alpha = float(src.a);
      if (alpha <= 0.001) {
        return src;
      }

      float3 base = float3(src.rgb) / alpha;
      float2 safeRes = max(iResolution, float2(1.0, 1.0));
      float2 uv = fragCoord / safeRes;

      // Neighbor luminance sampling for pseudo-3D surface normal & anime ink edges
      float stepPx = (iMode < 0.5) ? 1.35 : 1.60;
      float lL = lumaOf(sampleUnpremul(fragCoord + float2(-stepPx, 0.0)));
      float lR = lumaOf(sampleUnpremul(fragCoord + float2(stepPx, 0.0)));
      float lU = lumaOf(sampleUnpremul(fragCoord + float2(0.0, -stepPx)));
      float lD = lumaOf(sampleUnpremul(fragCoord + float2(0.0, stepPx)));

      float dx = lR - lL;
      float dy = lD - lU;
      float edgeMag = sqrt(dx * dx + dy * dy);

      // Reconstruct view-space pseudo-normal from gradient & dome curvature
      float2 centered = (uv - float2(0.5, 0.5)) * 1.4;
      float3 surfN = normalize(float3(-dx * 3.8 + centered.x * 0.42, -dy * 3.8 + centered.y * 0.42, 0.85));
      float3 L = normalize(iLightDir);
      float3 V = float3(0.0, 0.0, 1.0);
      float3 H = normalize(L + V);

      float lum = lumaOf(base);
      float ndl = dot(surfN, L);
      float shadedLum = clamp(lum * 0.78 + (ndl * 0.5 + 0.5) * 0.34, 0.0, 1.0);

      // Quantized multi-band anime cel lighting ramp
      float bands = max(iCelBands, 2.0);
      float stepped = floor(shadedLum * bands + 0.32) / bands;
      float smoothCel = mix(shadedLum, stepped, 0.52);

      // Cool indigo shadow tint vs. warm sunlit highlight tint
      float shadowMask = 1.0 - smoothstep(0.18, 0.48, smoothCel);
      float highMask = smoothstep(0.62, 0.88, smoothCel);
      float3 shadowTone = mix(float3(0.76, 0.80, 0.96), float3(0.72, 0.78, 0.95), iMode);
      float3 keyTone = mix(float3(1.10, 1.06, 0.98), float3(1.14, 1.08, 0.94), iMode);

      float3 celRgb = base * mix(float3(1.0, 1.0, 1.0), shadowTone, shadowMask * 0.52);
      celRgb *= mix(float3(1.0, 1.0, 1.0), keyTone, highMask * 0.42);

      // Mode-specific highlights: Character Angel-Ring vs. Environment Sun-Shaft Glow
      float fresnel = pow(clamp(1.0 - max(dot(surfN, V), 0.0), 0.0, 1.0), 2.4);
      float pulse = 0.86 + 0.14 * sin(iTime * 2.6);
      float3 rimGlow = iRimColor.rgb * fresnel * iRimStrength * pulse;

      if (iMode < 0.5) {
        // Character mode: Crisp Angel-Ring highlight band across upper crown/armor + directional sheen
        float angelBand = smoothstep(0.68, 0.94, 1.0 - abs(uv.y * 2.1 - 0.58 + surfN.y * 0.35));
        float specDot = smoothstep(0.84, 0.93, max(dot(surfN, H), 0.0));
        celRgb += float3(0.95, 0.98, 1.0) * (angelBand * 0.14 + specDot * 0.24) * iSpecStrength * lum;
      } else {
        // Environment mode: Subtle golden-hour atmospheric bounce & upper sky sheen
        float skyGlow = smoothstep(0.70, 0.05, uv.y) * 0.10 * iSpecStrength;
        celRgb += mix(iRimColor.rgb, float3(1.0, 0.92, 0.74), 0.6) * skyGlow;
      }

      celRgb += rimGlow;

      // Crisp anime ink silhouette outline on high-contrast luminance edges
      float inkMask = smoothstep(0.16, 0.44, edgeMag) * iOutlineStrength * 0.72;
      float3 inkColor = float3(0.08, 0.07, 0.16);
      float3 finalRgb = mix(celRgb, inkColor, clamp(inkMask, 0.0, 0.78));

      return half4(half3(clamp(finalRgb, 0.0, 1.0) * alpha), half(alpha));
    }
  """

  /**
   * Pure-Kotlin cel-band quantization helper mirroring the AGSL cel ramp for unit verification.
   */
  fun quantizeCelLuminance(luminance: Float, bands: Float): Float {
    val clamped = luminance.coerceIn(0f, 1f)
    val b = bands.coerceAtLeast(2f)
    val stepped = kotlin.math.floor(clamped * b + 0.32f) / b
    return clamped + (stepped - clamped) * 0.52f
  }
}

/**
 * Custom Compose shader modifier that applies a cel-shaded, anime-style directional lighting,
 * Fresnel rim-glow, Angel-Ring specular, and ink-edge effect to any Composable (3D character portraits,
 * hero cards, environment vistas, and HUD frames).
 *
 * Uses an AGSL [RuntimeShader] backed by [RenderEffect.createRuntimeShaderEffect] on Android 13+ (API 33+),
 * and gracefully falls back to a custom [DrawModifier] cel-lighting sweep & rim highlight on API 24–32.
 */
fun Modifier.animeCelLighting(
  config: CelLightingConfig = CelLightingConfig(),
  enabled: Boolean = true,
): Modifier {
  if (!enabled) return this
  return composed {
    val animTime =
      if (config.animateRimPulse) {
        val t by
          rememberInfiniteTransition(label = "celRimPulse").animateFloat(
            initialValue = 0f,
            targetValue = (2.0 * PI).toFloat(),
            animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Restart),
            label = "celTime",
          )
        t
      } else {
        0f
      }

    val runtimeShader =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        remember { runCatching { RuntimeShader(AnimeCelAgsl.SHADER_SOURCE) }.getOrNull() }
      } else {
        null
      }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && runtimeShader != null) {
      this.graphicsLayer {
        applyAgslCelUniforms(
          shader = runtimeShader,
          width = size.width,
          height = size.height,
          time = animTime,
          config = config,
        )
        renderEffect = RenderEffect.createRuntimeShaderEffect(runtimeShader, "content").asComposeRenderEffect()
      }
    } else {
      val fallback = remember(config, animTime) { CelLightingFallbackModifier(config, animTime) }
      this.then(fallback)
    }
  }
}

/**
 * Convenience overload for applying [animeCelLighting] directly with explicit parameters.
 */
fun Modifier.animeCelLighting(
  preset: CelShaderPreset = CelShaderPreset.CHARACTER,
  rimColor: Color = AstralCyan,
  lightAzimuthDeg: Float = 42f,
  lightElevationDeg: Float = 52f,
  celBands: Float = 3f,
  rimStrength: Float = 0.72f,
  outlineStrength: Float = 0.55f,
  specularStrength: Float = 0.65f,
  animateRimPulse: Boolean = false,
  enabled: Boolean = true,
): Modifier =
  animeCelLighting(
    config =
      CelLightingConfig(
        preset = preset,
        lightAzimuthDeg = lightAzimuthDeg,
        lightElevationDeg = lightElevationDeg,
        rimColor = rimColor,
        celBands = celBands,
        rimStrength = rimStrength,
        outlineStrength = outlineStrength,
        specularStrength = specularStrength,
        animateRimPulse = animateRimPulse,
      ),
    enabled = enabled,
  )

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun applyAgslCelUniforms(
  shader: RuntimeShader,
  width: Float,
  height: Float,
  time: Float,
  config: CelLightingConfig,
) {
  val (lx, ly, lz) = config.normalizedLightVector()
  shader.setFloatUniform("iResolution", width.coerceAtLeast(1f), height.coerceAtLeast(1f))
  shader.setFloatUniform("iTime", time)
  shader.setFloatUniform("iLightDir", lx, ly, lz)
  shader.setFloatUniform(
    "iRimColor",
    config.rimColor.red,
    config.rimColor.green,
    config.rimColor.blue,
    config.rimColor.alpha,
  )
  shader.setFloatUniform("iCelBands", config.celBands.coerceIn(2f, 6f))
  shader.setFloatUniform("iRimStrength", config.rimStrength.coerceIn(0f, 1.5f))
  shader.setFloatUniform("iOutlineStrength", config.outlineStrength.coerceIn(0f, 1.2f))
  shader.setFloatUniform("iSpecStrength", config.specularStrength.coerceIn(0f, 1.5f))
  shader.setFloatUniform("iMode", config.preset.modeIndex)
}

/**
 * Pre-API-33 DrawModifier fallback synthesizing directional cel-shading bands,
 * Fresnel rim lighting, and anime ink edge accents cleanly without RuntimeShader.
 */
internal class CelLightingFallbackModifier(
  private val config: CelLightingConfig,
  private val animTime: Float,
) : DrawModifier {
  override fun ContentDrawScope.draw() {
    drawContent()

    val (lx, ly, _) = config.normalizedLightVector()
    val pulse = 0.85f + 0.15f * sin(animTime * 2.6f)
    val start = Offset(size.width * (0.5f + lx * 0.5f), size.height * (0.5f + ly * 0.5f))
    val end = Offset(size.width * (0.5f - lx * 0.5f), size.height * (0.5f - ly * 0.5f))

    // Directional cel key-to-shadow gradient band
    val celOverlay =
      Brush.linearGradient(
        colorStops =
          arrayOf(
            0.0f to Color.White.copy(alpha = 0.16f * config.specularStrength),
            0.42f to Color.Transparent,
            0.78f to Color(0xFF1D2448).copy(alpha = 0.22f),
            1.0f to Color(0xFF121834).copy(alpha = 0.34f),
          ),
        start = start,
        end = end,
      )
    drawRect(brush = celOverlay)

    // Fresnel rim highlight & anime ink contour
    val rimAlpha = (0.38f * config.rimStrength * pulse).coerceIn(0f, 0.75f)
    if (rimAlpha > 0.01f) {
      drawRect(
        brush =
          Brush.radialGradient(
            colors = listOf(Color.Transparent, config.rimColor.copy(alpha = rimAlpha)),
            center = Offset(size.width * 0.5f, size.height * 0.45f),
            radius = maxOf(size.width, size.height) * 0.72f,
          )
      )
    }

    if (config.outlineStrength > 0.05f) {
      drawRect(
        color = Color(0xFF120F24).copy(alpha = (config.outlineStrength * 0.65f).coerceIn(0f, 0.85f)),
        style = Stroke(width = 2.5f),
      )
    }
  }
}
