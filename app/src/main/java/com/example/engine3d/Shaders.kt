package com.example.engine3d

import android.opengl.GLES30
import android.util.Log

object Shaders {

  private const val TAG = "AetherShaders"

  // --- 0. DIRECTIONAL SUN SHADOW MAP DEPTH SHADER (WITH ALPHA-CUTOUT LEAF SUPPORT) ---
  const val SHADOW_DEPTH_VERTEX =
    """#version 300 es
    layout(location = 0) in vec3 aPos;
    layout(location = 2) in vec2 aUv;

    uniform mat4 uLightMVP;
    uniform float uTime;
    uniform float uWindSway;

    out vec2 vUv;

    void main() {
      vec3 localPos = aPos;
      if (uWindSway > 0.001) {
        float h = max(localPos.y, 0.0);
        float phase = localPos.x * 0.9 + localPos.z * 0.7 + uTime * 2.4;
        localPos.x += sin(phase) * h * uWindSway;
        localPos.z += cos(phase * 0.85) * h * uWindSway * 0.6;
      }
      vUv = aUv;
      gl_Position = uLightMVP * vec4(localPos, 1.0);
    }
    """

  const val SHADOW_DEPTH_FRAGMENT =
    """#version 300 es
    precision mediump float;
    in vec2 vUv;
    uniform sampler2D uTex;
    uniform float uAlphaCutout;
    void main() {
      if (uAlphaCutout > 0.5) {
        if (texture(uTex, vUv).a < 0.38) discard;
      }
    }
    """

  // --- 1. CEL-SHADED ANIME NTE SHADER (PCF SHADOWS + SKIN + ANGEL-RING HAIR + ALPHA LEAVES) ---
  const val CEL_VERTEX =
    """#version 300 es
    layout(location = 0) in vec3 aPos;
    layout(location = 1) in vec3 aNormal;
    layout(location = 2) in vec2 aUv;
    layout(location = 3) in vec4 aColor;

    uniform mat4 uMVP;
    uniform mat4 uModel;
    uniform mat3 uNormalMat;
    uniform mat4 uLightVP;
    uniform float uTime;
    uniform float uWindSway;

    out vec3 vWorldPos;
    out vec3 vWorldNormal;
    out vec2 vUv;
    out vec4 vColor;
    out vec4 vShadowCoord;

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
      vShadowCoord = uLightVP * worldPos;
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
    in vec4 vShadowCoord;

    uniform sampler2D uTex;
    uniform sampler2D uShadowMap;
    uniform float uShadowTexelSize;
    uniform float uUseShadow;
    uniform float uMaterialMode; // 0=PBR rock/prop, 1=terrain, 2=animeSkin, 3=animeHair, 4=wetAsphalt, 5=glass/clearcoat, 6=ledScreen/banner
    uniform float uAlphaCutout;
    uniform float uQualityScale; // 1.0=std, 2.0=high, 3.0=3x ultra
    uniform float uCinemaMode;   // 0.0=standard, 1.0=cinematic grading
    uniform float uTime;

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

    float hash12(vec2 p) {
      vec3 p3 = fract(vec3(p.xyx) * 0.1031);
      p3 += dot(p3, p3.yzx + 33.33);
      return fract((p3.x + p3.y) * p3.z);
    }

    float vnoise(vec2 p) {
      vec2 i = floor(p);
      vec2 f = fract(p);
      vec2 u = f * f * (3.0 - 2.0 * f);
      return mix(
        mix(hash12(i), hash12(i + vec2(1.0, 0.0)), u.x),
        mix(hash12(i + vec2(0.0, 1.0)), hash12(i + vec2(1.0, 1.0)), u.x),
        u.y
      );
    }

    float fbm2(vec2 p) {
      return vnoise(p) * 0.65 + vnoise(p * 2.13 + 7.4) * 0.35;
    }

    float calcShadow(vec4 shadowCoord, float ndl) {
      if (uUseShadow < 0.5) return 1.0;
      vec3 proj = shadowCoord.xyz / max(shadowCoord.w, 0.0001);
      proj = proj * 0.5 + 0.5;
      if (proj.x <= 0.01 || proj.x >= 0.99 || proj.y <= 0.01 || proj.y >= 0.99 || proj.z > 0.995) {
        return 1.0;
      }
      float bias = max(0.0022 * (1.0 - max(ndl, 0.0)), 0.0006);
      float currentDepth = proj.z - bias;
      float occ = 0.0;
      if (uQualityScale > 2.5) {
        vec2 offsets[12] = vec2[12](
          vec2(-0.94, -0.39), vec2(0.94, 0.39), vec2(-0.39, 0.94), vec2(0.39, -0.94),
          vec2(-0.70, 0.70), vec2(0.70, -0.70), vec2(-0.32, -0.45), vec2(0.45, 0.32),
          vec2(-1.45, 0.15), vec2(1.45, -0.15), vec2(0.15, 1.45), vec2(-0.15, -1.45)
        );
        for (int i = 0; i < 12; i++) {
          float mapDepth = texture(uShadowMap, proj.xy + offsets[i] * uShadowTexelSize * 1.25).r;
          occ += (currentDepth > mapDepth) ? 1.0 : 0.0;
        }
        return mix(0.26, 1.0, 1.0 - (occ / 12.0));
      } else {
        for (int ox = -1; ox <= 1; ox++) {
          for (int oy = -1; oy <= 1; oy++) {
            float mapDepth = texture(uShadowMap, proj.xy + vec2(float(ox), float(oy)) * uShadowTexelSize).r;
            occ += (currentDepth > mapDepth) ? 1.0 : 0.0;
          }
        }
        return mix(0.28, 1.0, 1.0 - (occ / 9.0));
      }
    }

    // Derivative screen-space bump normal from albedo micro-contrast (true ES 3.0 PBR micro-relief)
    vec3 applyMicroBump(vec3 N, vec3 pos, float heightVal, float strength) {
      vec3 dPosX = dFdx(pos);
      vec3 dPosY = dFdy(pos);
      float dhx = dFdx(heightVal);
      float dhy = dFdy(heightVal);
      vec3 r1 = cross(dPosY, N);
      vec3 r2 = cross(N, dPosX);
      float det = dot(dPosX, r1);
      if (abs(det) < 1e-6) return N;
      vec3 grad = sign(det) * (dhx * r1 + dhy * r2);
      return normalize(abs(det) * N - strength * grad);
    }

    // Analytic AAA IBL Environment Sky + Horizon + Sun + City Neon Bounce Reflection
    vec3 sampleEnvReflection(vec3 R, vec3 L, float gloss) {
      float ry = clamp(R.y, -0.25, 1.0);
      vec3 skyLow = vec3(0.76, 0.86, 0.96);
      vec3 skyHigh = vec3(0.18, 0.42, 0.78);
      vec3 groundBounce = vec3(0.14, 0.16, 0.22);
      vec3 env = (ry >= 0.0) ? mix(skyLow, skyHigh, pow(ry, 0.55)) : mix(skyLow * 0.6, groundBounce, clamp(-ry * 4.0, 0.0, 1.0));
      float sunLobe = pow(max(dot(R, L), 0.0), mix(12.0, 180.0, gloss));
      env += vec3(1.25, 1.08, 0.85) * sunLobe * (0.35 + 0.95 * gloss);
      // Subtle cyan/magenta urban bounce along street horizon
      float cityBand = exp(-abs(ry) * 9.0);
      float neonWave = sin(R.x * 5.0 + R.z * 3.0) * 0.5 + 0.5;
      env += mix(vec3(0.10, 0.52, 0.65), vec3(0.58, 0.20, 0.48), neonWave) * cityBand * 0.22;
      return env;
    }

    void main() {
      vec4 texSample = texture(uTex, vUv * uUvScale);
      if (uAlphaCutout > 0.5 && texSample.a < 0.36) discard;

      vec3 geoN = normalize(vWorldNormal);
      if (!gl_FrontFacing) geoN = -geoN;
      vec3 L = normalize(uLightDir);
      vec3 V = normalize(uCameraPos - vWorldPos);
      vec3 baseColor = texSample.rgb * vColor.rgb * uTint.rgb;

      float texHeight = dot(texSample.rgb, vec3(0.299, 0.587, 0.114));
      vec3 N = geoN;
      float puddleMask = 0.0;

      // Apply procedural AAA micro-bump mapping on environment/PBR surfaces (skip anime face/hair & emissive LED screens)
      bool isAnimeCharOrScreen = (uMaterialMode > 1.5 && uMaterialMode < 3.5) || (uMaterialMode > 5.5);
      if (!isAnimeCharOrScreen && uQualityScale > 1.5) {
        float bumpAmp = (uMaterialMode > 0.5 && uMaterialMode < 1.5) ? 0.55 : 0.38;
        N = applyMicroBump(geoN, vWorldPos, texHeight, bumpAmp);
      }

      // Mode 1: Open-World AAA Natural Terrain (rock cliff strata, wet lakeside shore, rolling meadow AO)
      if (uMaterialMode > 0.5 && uMaterialMode < 1.5) {
        float slope = 1.0 - max(geoN.y, 0.0);
        float lakeDist = length(vWorldPos.xz - vec2(32.0, 24.0));
        float distCenter = length(vWorldPos.xz);
        float meadowMask = (1.0 - smoothstep(0.16, 0.34, slope)) * smoothstep(13.0, 22.0, distCenter);

        // Rich multi-scale organic grass tone + wind wave
        float patchNoise = fbm2(vWorldPos.xz * 0.09);
        float waveA = sin(vWorldPos.x * 0.15 + vWorldPos.z * 0.11 - uTime * 1.40);
        float waveB = cos(vWorldPos.x * 0.29 - vWorldPos.z * 0.25 + uTime * 0.90);
        float windWave = smoothstep(0.18, 0.92, waveA * 0.65 + waveB * 0.35);
        vec3 deepGrass = baseColor * vec3(0.76, 0.88, 0.72);
        vec3 sunlitGrass = baseColor * vec3(1.16, 1.22, 0.84);
        baseColor = mix(baseColor, mix(deepGrass, sunlitGrass, patchNoise), meadowMask * 0.65);
        baseColor = mix(baseColor, baseColor * vec3(1.12, 1.16, 0.90), windWave * meadowMask * 0.28);

        // Damp rocky shoreline near the Moonlit Lake
        float wetShore = (1.0 - smoothstep(14.5, 22.5, lakeDist)) * smoothstep(-2.4, -0.4, vWorldPos.y);
        baseColor = mix(baseColor, baseColor * vec3(0.56, 0.54, 0.50), wetShore * 0.65);

        // Multi-octave granite cliff stratification & rock crevice AO
        float rockMacro = fbm2(vec2(vWorldPos.x + vWorldPos.z, vWorldPos.y * 2.2) * 0.42);
        float rockFissure = fbm2(vWorldPos.xz * 0.9 + vWorldPos.y * 1.8);
        vec3 cliffCol = mix(vec3(0.24, 0.25, 0.28), vec3(0.46, 0.45, 0.44), rockMacro);
        cliffCol *= mix(0.68, 1.12, smoothstep(0.25, 0.75, rockFissure));
        float mossTop = smoothstep(0.55, 0.88, geoN.y) * 0.30;
        cliffCol = mix(cliffCol, vec3(0.26, 0.40, 0.24), mossTop);
        baseColor = mix(baseColor, cliffCol, smoothstep(0.20, 0.46, slope));
        if (uQualityScale > 1.5 && slope > 0.22) {
          N = normalize(mix(N, normalize(geoN + vec3(rockFissure - 0.5, 0.0, rockMacro - 0.5) * 0.45), 0.6));
        }
      }

      // Mode 4: AAA Wet Urban Asphalt with Specular Rain Puddles
      if (uMaterialMode > 3.5 && uMaterialMode < 4.5) {
        float puddleNoise = fbm2(vWorldPos.xz * 0.22 + vec2(3.1, 7.7));
        puddleMask = smoothstep(0.54 - uCinemaMode * 0.08, 0.72, puddleNoise);
        // Slightly darken wet asphalt patches for physical water absorption
        baseColor *= mix(1.0, 0.56, puddleMask);
        N = normalize(mix(N, vec3(0.0, 1.0, 0.0), puddleMask * 0.90));
      }

      vec3 H = normalize(L + V);
      float rawNdl = dot(N, L);
      float ndv = max(dot(N, V), 0.0);
      float shadowVis = calcShadow(vShadowCoord, rawNdl);
      vec3 lit;

      if (uMaterialMode > 5.5) {
        // Mode 6: Animated NTE Building LED Screens & Street Festival Banners
        float sweep = smoothstep(0.84, 1.0, sin(vUv.y * 7.0 - uTime * 2.2 + vUv.x * 2.5));
        float scanline = 0.94 + 0.06 * sin(vUv.y * 190.0);
        float fresnel = pow(1.0 - ndv, 3.8);
        vec3 glassSheen = sampleEnvReflection(reflect(-V, N), L, 0.85) * fresnel * 0.35;
        float glowBoost = (1.05 + uEmissive * 0.65 + uCinemaMode * 0.22) * scanline;
        lit = baseColor * glowBoost + baseColor * sweep * 0.26 + glassSheen;
      } else if (uMaterialMode > 1.5 && uMaterialMode < 2.5) {
        // Mode 2: Soft Luminous Anime Skin (Genshin/NTE face clarity — free of harsh shadow blotches)
        float faceShadow = mix(0.86, 1.0, shadowVis);
        float wrap = smoothstep(-0.42, 0.34, rawNdl) * faceShadow;
        vec3 skinLight = mix(vec3(0.93, 0.84, 0.88), vec3(1.05, 1.02, 1.00), wrap);
        float fresnel = pow(1.0 - ndv, 3.0);
        vec3 rim = uRimColor * smoothstep(0.42, 0.84, fresnel) * uRimStrength * 0.65;
        lit = baseColor * skinLight + rim + baseColor * uEmissive;
      } else if (uMaterialMode > 2.5 && uMaterialMode < 3.5) {
        // Mode 3: Anime Hair with Crisp Angel-Ring Specular Halo
        float bandShadow = smoothstep(-0.12, 0.08, rawNdl) * mix(0.55, 1.0, shadowVis);
        vec3 hairLight = mix(vec3(0.52, 0.56, 0.78), vec3(1.07, 1.04, 0.99), bandShadow);
        hairLight += vec3(0.08, 0.11, 0.18) * (N.y * 0.5 + 0.5);
        float viewFacing = smoothstep(0.18, 0.72, ndv);
        float ringBand = smoothstep(0.60, 0.92, 1.0 - abs(N.y * 1.15 - 0.34));
        vec3 spec = vec3(0.98, 0.98, 1.0) * (ringBand * viewFacing * uSpecStrength * 0.98);
        float fresnel = pow(1.0 - ndv, 2.8);
        vec3 rim = uRimColor * smoothstep(0.35, 0.78, fresnel) * uRimStrength * (0.48 + 0.52 * max(rawNdl, 0.0));
        lit = baseColor * hairLight + spec + rim + baseColor * uEmissive;
      } else {
        // Modes 0, 1, 4, 5: Hybrid AAA PBR Lighting (Realistic Diffuse Wrap + Cook-Torrance Specular + Sky IBL)
        float ndl = max(rawNdl, 0.0);
        float wrapDiff = smoothstep(-0.18, 0.85, rawNdl) * 0.22 + ndl * 0.78;
        vec3 sunTone = mix(vec3(1.18, 1.08, 0.94), vec3(1.26, 1.04, 0.82), uCinemaMode * 0.65);
        vec3 sunKey = sunTone * (wrapDiff * shadowVis);

        // Dual Hemisphere Global Illumination (Cool Azure Sky Zenith + Warm Ground Bounce)
        float skyHemi = N.y * 0.5 + 0.5;
        vec3 skyAmbient = mix(vec3(0.18, 0.19, 0.24), vec3(0.34, 0.46, 0.72), skyHemi);
        // Subtle crevice AO darkening on concave texture grooves & undersides
        float microAO = mix(0.76, 1.06, clamp(texHeight * 0.7 + skyHemi * 0.35, 0.0, 1.0));
        vec3 diffuseTotal = baseColor * (sunKey + skyAmbient * microAO);

        // Physically-Based Gloss / Fresnel Reflections
        float gloss = clamp(uSpecStrength, 0.05, 0.95);
        if (uMaterialMode > 3.5 && uMaterialMode < 4.5) {
          gloss = mix(0.24, 0.94, puddleMask);
        } else if (uMaterialMode > 4.5) {
          gloss = max(gloss, 0.86);
        }
        float shininess = mix(14.0, 220.0, gloss * gloss);
        float normFactor = (shininess + 8.0) / 25.1327;
        float specLobe = pow(max(dot(N, H), 0.0), shininess) * normFactor * (0.04 + gloss * 0.38);
        vec3 specSun = vec3(1.18, 1.06, 0.90) * specLobe * shadowVis * max(ndl * 4.0, 0.25);

        // Schlick Fresnel Sky & Neon Street Reflection
        float f0 = mix(0.03, 0.18, gloss);
        float fresnel = f0 + (1.0 - f0) * pow(1.0 - ndv, 4.4);
        vec3 reflDir = reflect(-V, N);
        vec3 envRefl = sampleEnvReflection(reflDir, L, gloss);
        float envWeight =
          (uMaterialMode > 4.5) ? (0.64 + uCinemaMode * 0.14) :
          (uMaterialMode > 3.5 && uMaterialMode < 4.5) ? mix(0.10, 0.86, puddleMask) :
          (0.12 * gloss);
        vec3 reflection = envRefl * fresnel * envWeight;

        // Subtle atmospheric rim light on backlit edges
        vec3 rim = uRimColor * (pow(1.0 - ndv, 3.4) * uRimStrength * 0.45 * (0.35 + 0.65 * ndl));
        lit = diffuseTotal + specSun + reflection + rim + baseColor * uEmissive;
      }

      // Exponential Height + Distance Fog with Realistic Sun Mie In-Scattering (Aerial Perspective)
      float dist = length(uCameraPos - vWorldPos);
      float heightAtten = exp(-max(vWorldPos.y - 2.0, 0.0) * 0.022);
      float fogFactor = 1.0 - exp(-pow(dist * uFogDensity * (0.72 + 0.28 * heightAtten), 1.38));
      fogFactor = clamp(fogFactor, 0.0, 0.88);
      float sunScatter = pow(max(dot(-V, L), 0.0), 6.0);
      vec3 scatterFog = mix(uFogColor, vec3(1.0, 0.90, 0.74), sunScatter * 0.52);
      vec3 finalRgb = mix(lit, scatterFog, fogFactor);

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

  // --- 3. AAA REALISTIC SHIMMERING LAKE WATER SHADER (DUAL GERSTNER + FRESNEL + CAUSTICS) ---
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
      p.y += sin(p.x * 0.75 + uTime * 1.9) * 0.045 + cos(p.z * 0.85 - uTime * 1.6) * 0.045;
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
      // 4-octave directional capillary + wind wave normals for realistic photoreal water sparkle
      float w1 = sin(p.x * 1.45 + uTime * 1.85) * cos(p.y * 1.25 - uTime * 1.55);
      float w2 = sin(p.x * 3.10 - p.y * 2.20 + uTime * 2.40);
      float w3 = cos(p.x * 6.40 + p.y * 5.20 - uTime * 3.10) * 0.55;
      float w4 = sin(p.x * 11.5 - p.y * 9.8 + uTime * 3.80) * 0.25;
      vec3 N = normalize(vec3((w1 + w2 * 0.5 + w3 + w4) * 0.13, 1.0, (w2 - w1 * 0.5 + w3 * 0.8 - w4) * 0.13));

      vec3 V = normalize(uCameraPos - vWorldPos);
      vec3 L = normalize(uLightDir);
      vec3 H = normalize(L + V);
      vec3 R = reflect(-V, N);

      float ndv = max(dot(N, V), 0.0);
      float fresnel = 0.04 + 0.96 * pow(1.0 - ndv, 4.2);

      // Realistic deep emerald-sapphire to translucent teal coastal absorption
      vec3 deepColor = vec3(0.03, 0.16, 0.26);
      vec3 shallowColor = vec3(0.11, 0.44, 0.52);
      float radial = length(vUv - vec2(0.5)) * 2.0;
      vec3 baseWater = mix(deepColor, shallowColor, smoothstep(0.28, 0.94, radial));

      // Sky & warm sun reflection path
      vec3 skyReflect = mix(vec3(0.68, 0.84, 0.98), vec3(0.18, 0.46, 0.82), clamp(R.y, 0.0, 1.0));
      vec3 waterCol = mix(baseWater, skyReflect, clamp(fresnel * 0.88, 0.0, 0.90));

      float specPath = pow(max(dot(N, H), 0.0), 240.0) * 2.4 + pow(max(dot(N, H), 0.0), 32.0) * 0.28;
      float caustic = smoothstep(0.68, 0.96, sin(p.x * 2.1 + uTime * 1.9) * sin(p.y * 2.1 - uTime * 1.4));
      waterCol += vec3(0.22, 0.62, 0.72) * caustic * 0.12 * (1.0 - radial * 0.6);
      waterCol += vec3(1.0, 0.94, 0.80) * specPath;

      float foam = smoothstep(0.86, 0.97, radial);
      waterCol = mix(waterCol, vec3(0.88, 0.94, 0.98), foam * 0.52);

      float dist = length(uCameraPos - vWorldPos);
      float fogFactor = clamp(1.0 - exp(-pow(dist * uFogDensity, 1.38)), 0.0, 0.86);
      float sunScatter = pow(max(dot(-V, L), 0.0), 6.0);
      vec3 scatterFog = mix(uFogColor, vec3(1.0, 0.90, 0.74), sunScatter * 0.52);
      waterCol = mix(waterCol, scatterFog, fogFactor);

      float alpha = mix(0.92, 0.34, smoothstep(0.84, 1.0, radial));
      fragColor = vec4(waterCol, alpha);
    }
    """

  // --- 4. AAA VOLUMETRIC RAYLEIGH-MIE ATMOSPHERIC SKY & SKYLINE SHADER ---
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
    float fbmCloud(vec2 p) {
      return noise(p) * 0.52 + noise(p * 2.07 + 1.7) * 0.27 + noise(p * 4.21 + 4.3) * 0.14 + noise(p * 8.15) * 0.07;
    }

    void main() {
      vec3 dir = normalize(vDir);
      vec3 L = normalize(uSunDir);
      float h = clamp(dir.y, 0.0, 1.0);
      vec3 sky = mix(uHorizonColor, uZenithColor, pow(h, 0.48));

      // Mie Forward Solar Scattering Halo + Crisp Sun Corona
      float sunAmt = max(dot(dir, L), 0.0);
      sky += vec3(1.0, 0.84, 0.56) * pow(sunAmt, 5.0) * (1.0 - h * 0.55) * 0.42;
      sky += vec3(1.0, 0.90, 0.72) * pow(sunAmt, 36.0) * 0.55;
      sky += vec3(1.25, 1.18, 1.02) * smoothstep(0.9965, 0.9992, sunAmt) * 1.45;

      // Multi-layered Atmospheric Cumulus + High Cirrus Clouds with Silver Lining
      if (dir.y > 0.025) {
        vec2 uv = dir.xz / (dir.y + 0.18) * 1.35 + vec2(uTime * 0.011, uTime * 0.005);
        float cDensity = fbmCloud(uv);
        float cLightSample = fbmCloud(uv + L.xz * 0.08);
        float selfShadow = clamp(0.65 + (cDensity - cLightSample) * 2.4, 0.25, 1.0);
        float cloud = smoothstep(0.50, 0.68, cDensity) * smoothstep(0.025, 0.22, dir.y);
        vec3 cloudLit = mix(vec3(0.97, 0.98, 1.0), vec3(1.08, 0.95, 0.82), pow(sunAmt, 2.0));
        vec3 cloudShade = mix(vec3(0.46, 0.56, 0.74), vec3(0.24, 0.20, 0.36), uIsRaid);
        vec3 cloudCol = mix(cloudShade, cloudLit, selfShadow);
        cloudCol += vec3(1.0, 0.92, 0.78) * pow(sunAmt, 6.0) * (1.0 - cDensity * 0.6) * 0.32;
        sky = mix(sky, cloudCol, cloud * 0.86);
      }

      // Layered Far Mountain Ranges + Distant NTE Metropolis Skyscraper Horizon
      if (uIsRaid < 0.5 && dir.y > -0.02 && dir.y < 0.24) {
        float ang = atan(dir.z, dir.x);
        float ridgeFar = 0.068 + 0.048 * sin(ang * 4.0 + 0.6) + 0.024 * cos(ang * 11.0);
        if (dir.y < ridgeFar) {
          vec3 farMtn = mix(vec3(0.44, 0.56, 0.76), uHorizonColor, 0.45);
          sky = mix(sky, farMtn, 0.78);
        }
        float ridgeNear = 0.042 + 0.032 * sin(ang * 7.0 + 1.4) + 0.016 * cos(ang * 17.0);
        if (dir.y < ridgeNear) {
          vec3 nearMtn = mix(vec3(0.26, 0.36, 0.54), uHorizonColor, 0.28);
          sky = mix(sky, nearMtn, 0.85);
        }

        float cityBlock = floor(ang * 48.0);
        float bH = 0.028 + hash(vec2(cityBlock, 7.0)) * 0.145;
        float northernMask = smoothstep(0.40, -0.35, dir.z);
        float cityHeight = bH * (0.42 + 0.58 * northernMask);
        if (dir.y < cityHeight) {
          vec3 towerCol = mix(vec3(0.16, 0.22, 0.34), uHorizonColor, 0.34);
          float winGrid = step(0.80, hash(vec2(floor(ang * 210.0), floor(dir.y * 240.0))));
          float warmWin = step(0.5, hash(vec2(cityBlock, floor(dir.y * 120.0))));
          vec3 winTint = mix(vec3(0.42, 0.88, 1.0), vec3(1.0, 0.86, 0.58), warmWin);
          towerCol += winTint * winGrid * 0.32 * northernMask;
          // Subtle red aircraft warning beacons atop tallest towers
          if (dir.y > cityHeight - 0.004 && bH > 0.13) {
            float blink = 0.5 + 0.5 * sin(uTime * 3.5 + cityBlock);
            towerCol += vec3(1.0, 0.25, 0.25) * blink * 0.8;
          }
          sky = mix(sky, towerCol, 0.80);
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

  // --- 6. FULLSCREEN POST-PROCESS HDR BLOOM + SUN SHAFTS + ACES FILMIC COMPOSITOR ---
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
      float bright = smoothstep(0.84, 1.18, lum);
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
    uniform vec2 uTexelSize;
    uniform float uQualityScale;
    uniform float uCinemaMode;
    uniform vec2 uSunScreenPos;
    uniform float uSunVisible;
    out vec4 fragColor;

    vec3 acesFilm(vec3 x) {
      return clamp((x * (2.51 * x + 0.03)) / (x * (2.43 * x + 0.59) + 0.14), 0.0, 1.0);
    }

    float luma(vec3 c) {
      return dot(c, vec3(0.299, 0.587, 0.114));
    }

    void main() {
      vec3 center = texture(uSceneTex, vUv).rgb;
      vec3 scene = center;

      if (uQualityScale > 1.5) {
        // AAA Anti-Aliasing + Micro-Contrast Detail Clarity
        vec3 n = texture(uSceneTex, vUv + vec2(0.0, uTexelSize.y)).rgb;
        vec3 s = texture(uSceneTex, vUv - vec2(0.0, uTexelSize.y)).rgb;
        vec3 e = texture(uSceneTex, vUv + vec2(uTexelSize.x, 0.0)).rgb;
        vec3 w = texture(uSceneTex, vUv - vec2(uTexelSize.x, 0.0)).rgb;
        float lC = luma(center);
        float lN = luma(n); float lS = luma(s); float lE = luma(e); float lW = luma(w);
        float lMax = max(lC, max(max(lN, lS), max(lE, lW)));
        float lMin = min(lC, min(min(lN, lS), min(lE, lW)));
        float contrast = lMax - lMin;
        vec3 avgBlur = (n + s + e + w) * 0.25;

        if (uQualityScale > 2.5) {
          float edgeBlend = smoothstep(0.08, 0.28, contrast) * 0.36;
          vec3 smoothed = mix(center, avgBlur, edgeBlend);
          // Crisp micro-detail clarity for sharp anime eyes & textures
          vec3 detailBoost = (center - avgBlur) * 0.34 * (1.0 - smoothstep(0.18, 0.48, contrast));
          scene = clamp(smoothed + detailBoost, 0.0, 1.85);

          // Cinematic radial Sun God-Ray Shafts when sun is in frame
          if (uSunVisible > 0.05) {
            vec2 delta = (vUv - uSunScreenPos) * 0.11;
            vec2 sampleUv = vUv;
            vec3 rays = vec3(0.0);
            float illum = 1.0;
            for (int i = 0; i < 6; i++) {
              sampleUv -= delta;
              vec3 sCol = texture(uSceneTex, clamp(sampleUv, 0.0, 1.0)).rgb;
              float bright = smoothstep(0.82, 1.15, luma(sCol));
              rays += sCol * bright * illum;
              illum *= 0.72;
            }
            scene += rays * ((0.044 + uCinemaMode * 0.025) * uSunVisible);
          }
        } else {
          scene = mix(center, avgBlur, smoothstep(0.12, 0.32, contrast) * 0.28);
        }
      }

      vec3 bloom = texture(uBloomTex, vUv).rgb;
      float bloomWeight = (uQualityScale > 2.5) ? (0.38 + uCinemaMode * 0.12) : 0.30;
      vec3 col = scene + bloom * bloomWeight;

      // Subtle Anamorphic Horizontal Lens Streak on bright neon billboards, headlights & sun
      if (uQualityScale > 2.5) {
        vec3 streak =
          texture(uBloomTex, clamp(vUv + vec2(uTexelSize.x * 16.0, 0.0), 0.0, 1.0)).rgb +
          texture(uBloomTex, clamp(vUv - vec2(uTexelSize.x * 16.0, 0.0), 0.0, 1.0)).rgb +
          texture(uBloomTex, clamp(vUv + vec2(uTexelSize.x * 36.0, 0.0), 0.0, 1.0)).rgb * 0.65 +
          texture(uBloomTex, clamp(vUv - vec2(uTexelSize.x * 36.0, 0.0), 0.0, 1.0)).rgb * 0.65;
        col += streak * vec3(0.28, 0.54, 0.92) * (0.08 + uCinemaMode * 0.10);
      }

      // Cinematic Split-Toning (Deep Cool Teal Shadows + Warm Golden Highlights) & Filmic Contrast
      float lum = luma(col);
      if (uCinemaMode > 0.5) {
        vec3 shadowTint = vec3(0.88, 0.96, 1.14);
        vec3 hiTint = vec3(1.10, 1.02, 0.92);
        col *= mix(shadowTint, hiTint, smoothstep(0.15, 0.78, lum));
      }
      float sat = (uQualityScale > 2.5) ? (1.18 + uCinemaMode * 0.08) : 1.12;
      col = mix(vec3(lum), col, sat);
      col = acesFilm(col * (1.15 + uCinemaMode * 0.06));

      vec2 d = vUv - vec2(0.5);
      float vig = 1.0 - dot(d, d) * (0.19 + uCinemaMode * 0.14);
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
      float alpha = pow(1.0 - r, 2.2) * 0.44;
      fragColor = vec4(0.04, 0.06, 0.14, alpha);
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
