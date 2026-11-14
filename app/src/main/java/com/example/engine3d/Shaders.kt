package com.example.engine3d

import android.opengl.GLES30
import android.util.Log

object Shaders {

  private const val TAG = "AetherShaders"

  // --- 1. CEL-SHADED ANIME PBR-LITE SHADER (Genshin / Wuthering Waves / NTE Style) ---
  const val CEL_VERTEX =
    """#version 300 es
    layout(location = 0) in vec3 aPos;
    layout(location = 1) in vec3 aNormal;
    layout(location = 2) in vec2 aUv;
    layout(location = 3) in vec4 aColor;

    uniform mat4 uMVP;
    uniform mat4 uModel;
    uniform mat3 uNormalMat;
    uniform float uTime;
    uniform float uWindSway;

    out vec3 vWorldPos;
    out vec3 vWorldNormal;
    out vec2 vUv;
    out vec4 vColor;

    void main() {
      vec3 localPos = aPos;
      if (uWindSway > 0.001) {
        float h = max(localPos.y, 0.0);
        float phase = localPos.x * 0.9 + localPos.z * 0.7 + uTime * 2.4;
        localPos.x += sin(phase) * h * uWindSway;
        localPos.z += cos(phase * 0.85) * h * uWindSway * 0.6;
      }
      vec4 worldPos = uModel * vec4(localPos, 1.0);
      vWorldPos = worldPos.xyz;
      vWorldNormal = normalize(uNormalMat * aNormal);
      vUv = aUv;
      vColor = aColor;
      gl_Position = uMVP * vec4(localPos, 1.0);
    }
    """

  const val CEL_FRAGMENT =
    """#version 300 es
    precision highp float;

    in vec3 vWorldPos;
    in vec3 vWorldNormal;
    in vec2 vUv;
    in vec4 vColor;

    uniform sampler2D uTex;
    uniform vec3 uLightDir;
    uniform vec3 uCameraPos;
    uniform vec4 uTint;
    uniform vec3 uRimColor;
    uniform float uRimStrength;
    uniform float uSpecStrength;
    uniform float uEmissive;
    uniform float uUvScale;
    uniform vec3 uFogColor;
    uniform float uFogDensity;

    out vec4 fragColor;

    void main() {
      vec3 N = normalize(vWorldNormal);
      vec3 L = normalize(uLightDir);
      vec3 V = normalize(uCameraPos - vWorldPos);
      vec3 H = normalize(L + V);

      vec4 texSample = texture(uTex, vUv * uUvScale);
      vec3 baseColor = texSample.rgb * vColor.rgb * uTint.rgb;

      // Subtle micro-detail variation from world coordinates for organic richness
      float micro = sin(vWorldPos.x * 2.7 + vWorldPos.z * 2.3) * 0.035;

      // Stylized anime 3-band ramp with crisp warm/cool chromatic shift
      float ndl = dot(N, L) + micro;
      float bandShadow = smoothstep(-0.12, 0.02, ndl);
      float bandHighlight = smoothstep(0.45, 0.60, ndl);

      // Cool indigo-violet shadow bounce vs warm golden sunlight
      vec3 shadowTint = vec3(0.42, 0.48, 0.72);
      vec3 midTint = vec3(0.88, 0.91, 0.98);
      vec3 keyTint = vec3(1.12, 1.05, 0.95);
      vec3 celLight = mix(shadowTint, midTint, bandShadow);
      celLight = mix(celLight, keyTint, bandHighlight * 0.72);

      // Sky hemisphere ambient bounce
      float skyBounce = N.y * 0.5 + 0.5;
      celLight += vec3(0.06, 0.10, 0.16) * skyBounce;

      // Crisp anime specular band (for metal, hair, armor, crystal)
      float specDot = max(dot(N, H), 0.0);
      float celSpec = smoothstep(0.86, 0.92, specDot) * uSpecStrength;

      // Vibrant Fresnel rim light
      float fresnel = pow(1.0 - max(dot(N, V), 0.0), 2.8);
      float rimMask = smoothstep(0.32, 0.76, fresnel) * (0.45 + 0.55 * max(ndl, 0.0));
      vec3 rim = uRimColor * rimMask * uRimStrength;

      vec3 lit = baseColor * celLight + vec3(celSpec) + rim + baseColor * uEmissive;

      // Atmospheric aerial perspective fog
      float dist = length(uCameraPos - vWorldPos);
      float fogFactor = 1.0 - exp(-pow(dist * uFogDensity, 1.38));
      fogFactor = clamp(fogFactor, 0.0, 0.90);
      vec3 finalRgb = mix(lit, uFogColor, fogFactor);

      fragColor = vec4(finalRgb, vColor.a * uTint.a);
    }
    """

  // --- 2. DISTANCE-ADAPTIVE INVERTED-HULL ANIME INK OUTLINE SHADER ---
  const val OUTLINE_VERTEX =
    """#version 300 es
    layout(location = 0) in vec3 aPos;
    layout(location = 1) in vec3 aNormal;

    uniform mat4 uMVP;
    uniform float uOutlineWidth;

    void main() {
      vec4 clipPos = uMVP * vec4(aPos, 1.0);
      float distScale = clamp(clipPos.w * 0.14, 0.45, 2.2);
      vec3 extruded = aPos + normalize(aNormal) * (uOutlineWidth * distScale);
      gl_Position = uMVP * vec4(extruded, 1.0);
    }
    """

  const val OUTLINE_FRAGMENT =
    """#version 300 es
    precision mediump float;
    uniform vec4 uOutlineColor;
    out vec4 fragColor;
    void main() {
      fragColor = uOutlineColor;
    }
    """

  // --- 3. SHIMMERING ANIME WATER SHADER ---
  const val WATER_VERTEX =
    """#version 300 es
    layout(location = 0) in vec3 aPos;
    layout(location = 2) in vec2 aUv;

    uniform mat4 uMVP;
    uniform mat4 uModel;
    uniform float uTime;

    out vec3 vWorldPos;
    out vec2 vUv;

    void main() {
      vec3 p = aPos;
      p.y += sin(p.x * 0.8 + uTime * 2.1) * 0.06 + cos(p.z * 0.9 + uTime * 1.7) * 0.06;
      vec4 wp = uModel * vec4(p, 1.0);
      vWorldPos = wp.xyz;
      vUv = aUv;
      gl_Position = uMVP * vec4(p, 1.0);
    }
    """

  const val WATER_FRAGMENT =
    """#version 300 es
    precision highp float;

    in vec3 vWorldPos;
    in vec2 vUv;

    uniform vec3 uCameraPos;
    uniform vec3 uLightDir;
    uniform vec3 uFogColor;
    uniform float uFogDensity;
    uniform float uTime;

    out vec4 fragColor;

    void main() {
      vec2 p = vWorldPos.xz;
      float w1 = sin(p.x * 1.6 + uTime * 2.2) * cos(p.y * 1.4 - uTime * 1.8);
      float w2 = sin(p.x * 3.4 - uTime * 2.8 + p.y * 2.1);
      vec3 N = normalize(vec3((w1 + w2 * 0.45) * 0.14, 1.0, (w2 - w1 * 0.5) * 0.14));

      vec3 V = normalize(uCameraPos - vWorldPos);
      vec3 L = normalize(uLightDir);
      vec3 H = normalize(L + V);

      float fresnel = pow(1.0 - max(dot(N, V), 0.0), 3.0);
      vec3 deepColor = vec3(0.08, 0.38, 0.64);
      vec3 shallowColor = vec3(0.22, 0.82, 0.88);
      vec3 skyReflect = vec3(0.75, 0.90, 1.0);

      float radial = length(vUv - vec2(0.5)) * 2.0;
      vec3 waterCol = mix(deepColor, shallowColor, smoothstep(0.25, 0.92, radial));
      waterCol = mix(waterCol, skyReflect, clamp(fresnel * 0.78, 0.0, 0.78));

      // Sparkling anime sun glints
      float glint = smoothstep(0.96, 0.995, max(dot(N, H), 0.0)) * 1.4;
      // Caustic shimmer bands
      float caustic = smoothstep(0.65, 0.95, sin(p.x * 1.9 + uTime * 2.0) * sin(p.y * 1.9 - uTime * 1.5));
      waterCol += vec3(0.30, 0.85, 0.95) * caustic * 0.14 + vec3(1.0, 0.96, 0.85) * glint;

      // Shoreline soft foam ring
      float foam = smoothstep(0.84, 0.96, radial);
      waterCol = mix(waterCol, vec3(0.88, 0.98, 1.0), foam * 0.55);

      float dist = length(uCameraPos - vWorldPos);
      float fogFactor = clamp(1.0 - exp(-pow(dist * uFogDensity, 1.38)), 0.0, 0.88);
      waterCol = mix(waterCol, uFogColor, fogFactor);

      float alpha = mix(0.82, 0.35, smoothstep(0.82, 1.0, radial));
      fragColor = vec4(waterCol, alpha);
    }
    """

  // --- 4. ATMOSPHERIC ANIME SKY DOME SHADER ---
  const val SKY_VERTEX =
    """#version 300 es
    layout(location = 0) in vec3 aPos;
    uniform mat4 uMVP;
    out vec3 vDir;
    void main() {
      vDir = aPos;
      gl_Position = uMVP * vec4(aPos, 1.0);
    }
    """

  const val SKY_FRAGMENT =
    """#version 300 es
    precision highp float;
    in vec3 vDir;
    uniform vec3 uZenithColor;
    uniform vec3 uHorizonColor;
    uniform vec3 uSunDir;
    uniform float uTime;
    uniform float uIsRaid;
    out vec4 fragColor;

    float hash(vec2 p) {
      return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
    }
    float noise(vec2 p) {
      vec2 i = floor(p);
      vec2 f = fract(p);
      vec2 u = f * f * (3.0 - 2.0 * f);
      return mix(mix(hash(i), hash(i + vec2(1.0, 0.0)), u.x),
                 mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0, 1.0)), u.x), u.y);
    }

    void main() {
      vec3 dir = normalize(vDir);
      float h = clamp(dir.y, 0.0, 1.0);
      vec3 sky = mix(uHorizonColor, uZenithColor, pow(h, 0.52));

      // Glowing sun disc + warm atmospheric halo
      float sunAmt = max(dot(dir, normalize(uSunDir)), 0.0);
      sky += vec3(1.0, 0.86, 0.58) * pow(sunAmt, 10.0) * 0.50;
      sky += vec3(1.0, 0.96, 0.88) * smoothstep(0.994, 0.9985, sunAmt) * 1.15;

      // Multi-octave painterly anime cumulus clouds
      if (dir.y > 0.03) {
        vec2 uv = dir.xz / (dir.y + 0.22) * 1.45 + vec2(uTime * 0.014, uTime * 0.006);
        float n = noise(uv) * 0.60 + noise(uv * 2.2) * 0.28 + noise(uv * 4.5) * 0.12;
        float cloud = smoothstep(0.52, 0.68, n) * smoothstep(0.03, 0.24, dir.y);
        vec3 cloudLit = mix(vec3(0.95, 0.97, 1.0), vec3(1.0, 0.92, 0.80), pow(sunAmt, 2.0));
        vec3 cloudShade = mix(vec3(0.68, 0.77, 0.92), vec3(0.32, 0.28, 0.48), uIsRaid);
        vec3 cloudCol = mix(cloudShade, cloudLit, smoothstep(0.50, 0.72, n));
        sky = mix(sky, cloudCol, cloud * 0.78);
      }

      // Distant misty mountain range silhouette on horizon
      if (uIsRaid < 0.5 && dir.y > -0.02 && dir.y < 0.17) {
        float ang = atan(dir.z, dir.x);
        float ridge = 0.055 + 0.045 * sin(ang * 5.0 + 0.4) + 0.025 * cos(ang * 13.0);
        if (dir.y < ridge) {
          vec3 mtnCol = mix(vec3(0.45, 0.62, 0.84), uHorizonColor, 0.35);
          sky = mix(sky, mtnCol, 0.82);
        }
      }

      fragColor = vec4(sky, 1.0);
    }
    """

  // --- 5. GPU ADDITIVE PARTICLE POINT-SPRITE SHADER ---
  const val PARTICLE_VERTEX =
    """#version 300 es
    layout(location = 0) in vec3 aPos;
    layout(location = 1) in float aSize;
    layout(location = 2) in vec4 aColor;

    uniform mat4 uVP;
    uniform float uScreenScale;

    out vec4 vColor;

    void main() {
      gl_Position = uVP * vec4(aPos, 1.0);
      float w = max(gl_Position.w, 0.5);
      gl_PointSize = clamp((aSize * uScreenScale) / w, 2.0, 110.0);
      vColor = aColor;
    }
    """

  const val PARTICLE_FRAGMENT =
    """#version 300 es
    precision mediump float;
    in vec4 vColor;
    out vec4 fragColor;

    void main() {
      vec2 p = gl_PointCoord * 2.0 - 1.0;
      float r2 = dot(p, p);
      if (r2 > 1.0) discard;
      float core = exp(-r2 * 4.2);
      float star = max(0.0, 1.0 - abs(p.x) * 3.0) * max(0.0, 1.0 - abs(p.y) * 3.0);
      float alpha = clamp(core + star * 0.55, 0.0, 1.0) * vColor.a;
      fragColor = vec4(vColor.rgb * (1.0 + core * 0.75), alpha);
    }
    """

  // --- 6. FULLSCREEN POST-PROCESS BLOOM + ACES FILMIC TONE MAPPING ---
  const val QUAD_VERTEX =
    """#version 300 es
    layout(location = 0) in vec2 aPos;
    layout(location = 1) in vec2 aUv;
    out vec2 vUv;
    void main() {
      vUv = aUv;
      gl_Position = vec4(aPos, 0.0, 1.0);
    }
    """

  const val BLOOM_EXTRACT_FRAGMENT =
    """#version 300 es
    precision mediump float;
    in vec2 vUv;
    uniform sampler2D uSceneTex;
    out vec4 fragColor;
    void main() {
      vec3 c = texture(uSceneTex, vUv).rgb;
      float lum = dot(c, vec3(0.2126, 0.7152, 0.0722));
      float bright = smoothstep(0.76, 1.05, lum);
      fragColor = vec4(c * bright, 1.0);
    }
    """

  const val BLOOM_BLUR_FRAGMENT =
    """#version 300 es
    precision mediump float;
    in vec2 vUv;
    uniform sampler2D uTex;
    uniform vec2 uTexelStep;
    out vec4 fragColor;
    void main() {
      vec3 sum = texture(uTex, vUv).rgb * 0.227027;
      sum += texture(uTex, vUv + uTexelStep * 1.3846).rgb * 0.316216;
      sum += texture(uTex, vUv - uTexelStep * 1.3846).rgb * 0.316216;
      sum += texture(uTex, vUv + uTexelStep * 3.2308).rgb * 0.070270;
      sum += texture(uTex, vUv - uTexelStep * 3.2308).rgb * 0.070270;
      fragColor = vec4(sum, 1.0);
    }
    """

  const val COMPOSITE_FRAGMENT =
    """#version 300 es
    precision highp float;
    in vec2 vUv;
    uniform sampler2D uSceneTex;
    uniform sampler2D uBloomTex;
    out vec4 fragColor;

    vec3 acesFilm(vec3 x) {
      return clamp((x * (2.51 * x + 0.03)) / (x * (2.43 * x + 0.59) + 0.14), 0.0, 1.0);
    }

    void main() {
      vec3 scene = texture(uSceneTex, vUv).rgb;
      vec3 bloom = texture(uBloomTex, vUv).rgb;
      vec3 col = scene + bloom * 0.58;

      // Gentle vibrance boost & ACES filmic roll-off for rich Genshin/NTE saturation
      float luma = dot(col, vec3(0.299, 0.587, 0.114));
      col = mix(vec3(luma), col, 1.14);
      col = acesFilm(col * 1.08);

      // Subtle cinematic vignette at edges
      vec2 d = vUv - vec2(0.5);
      float vig = 1.0 - dot(d, d) * 0.24;
      col *= vig;

      fragColor = vec4(col, 1.0);
    }
    """

  // --- 7. SOFT GROUND CONTACT SHADOW DECAL ---
  const val SHADOW_FRAGMENT =
    """#version 300 es
    precision mediump float;
    in vec2 vUv;
    out vec4 fragColor;
    void main() {
      vec2 d = vUv * 2.0 - 1.0;
      float r = dot(d, d);
      if (r > 1.0) discard;
      float alpha = pow(1.0 - r, 2.0) * 0.42;
      fragColor = vec4(0.08, 0.10, 0.24, alpha);
    }
    """

  fun compileProgram(vertSrc: String, fragSrc: String): Int {
    val vs = compileShader(GLES30.GL_VERTEX_SHADER, vertSrc)
    val fs = compileShader(GLES30.GL_FRAGMENT_SHADER, fragSrc)
    val program = GLES30.glCreateProgram()
    GLES30.glAttachShader(program, vs)
    GLES30.glAttachShader(program, fs)
    GLES30.glLinkProgram(program)
    val linkStatus = IntArray(1)
    GLES30.glGetProgramiv(program, GLES30.GL_LINK_STATUS, linkStatus, 0)
    if (linkStatus[0] == 0) {
      Log.e(TAG, "Program link error: ${GLES30.glGetProgramInfoLog(program)}")
      GLES30.glDeleteProgram(program)
      return 0
    }
    GLES30.glDeleteShader(vs)
    GLES30.glDeleteShader(fs)
    return program
  }

  private fun compileShader(type: Int, src: String): Int {
    val shader = GLES30.glCreateShader(type)
    GLES30.glShaderSource(shader, src)
    GLES30.glCompileShader(shader)
    val status = IntArray(1)
    GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, status, 0)
    if (status[0] == 0) {
      Log.e(TAG, "Shader compile error: ${GLES30.glGetShaderInfoLog(shader)}")
    }
    return shader
  }
}
