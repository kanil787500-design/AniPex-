package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Upgrade
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.GameCatalog
import com.example.data.HeroProgression
import com.example.data.PlayerProfile
import com.example.ui.shader.CelShaderPreset
import com.example.ui.shader.animeCelLighting
import com.example.ui.theme.AstralCyan
import com.example.ui.theme.AstralGold
import com.example.ui.theme.AstralNight
import com.example.ui.theme.AstralSurface
import com.example.ui.theme.AstralSurfaceVariant
import com.example.viewmodel.GameViewModel
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

fun heroDrawableRes(heroId: String): Int =
  when (heroId) {
    "lyra" -> R.drawable.img_avatar_lyra_1794649213075
    "seraphina" -> R.drawable.img_avatar_seraphina_1794649213075
    else -> R.drawable.img_avatar_kael_1794649212867
  }

@Composable
fun CharacterProgressionOverlay(
  profile: PlayerProfile?,
  heroes: List<HeroProgression>,
  viewModel: GameViewModel,
  onClose: () -> Unit,
) {
  var selectedHeroId by remember(profile?.activeHeroId) {
    mutableStateOf(profile?.activeHeroId ?: "kaelen")
  }
  var selectedTab by remember { mutableIntStateOf(0) }
  var celLightAzimuth by remember { mutableFloatStateOf(viewModel.worldState.celLightAzimuthDeg) }

  val heroProg = heroes.firstOrNull { it.heroId == selectedHeroId } ?: heroes.firstOrNull()
  val arch = GameCatalog.heroById(selectedHeroId)
  val elemColor = Color(arch.element.colorHex)

  Box(
    modifier =
      Modifier.fillMaxSize()
        .background(AstralNight.copy(alpha = 0.94f))
        .windowInsetsPadding(WindowInsets.safeDrawing)
  ) {
    Column(
      modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 10.dp)
    ) {
      // Top Header + Currencies + Close
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
          Text("CHARACTER ARMORY", style = MaterialTheme.typography.headlineMedium, color = Color.White)
          if (profile != null) {
            Text(
              "Gems: ${profile.starlightGems}  •  Mora: ${profile.mora}  •  Tomes: ${profile.expTomes}  •  Cores: ${profile.bossCores}",
              style = MaterialTheme.typography.labelMedium,
              color = AstralGold,
            )
          }
        }
        IconButton(onClick = onClose, modifier = Modifier.size(48.dp).testTag("close_character_overlay")) {
          Icon(Icons.Default.Close, contentDescription = "Close Armory", tint = Color.White)
        }
      }

      Spacer(modifier = Modifier.height(8.dp))

      Row(
        modifier = Modifier.weight(1f).fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
      ) {
        // Left Pane: Hero Roster + Selected Hero Banner
        Column(
          modifier = Modifier.weight(0.42f).verticalScroll(rememberScrollState()),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          GameCatalog.heroes.forEach { h ->
            val isSelected = h.id == selectedHeroId
            val prog = heroes.firstOrNull { it.heroId == h.id }
            Surface(
              color = if (isSelected) Color(h.primaryColorHex).copy(alpha = 0.24f) else AstralSurface,
              shape = RoundedCornerShape(14.dp),
              border = BorderStroke(if (isSelected) 2.dp else 1.dp, if (isSelected) Color(h.primaryColorHex) else Color.White.copy(alpha = 0.14f)),
              modifier =
                Modifier.fillMaxWidth()
                  .clickable { selectedHeroId = h.id }
                  .testTag("select_hero_${h.id}"),
            ) {
              Row(
                modifier = Modifier.padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Image(
                  painter = painterResource(heroDrawableRes(h.id)),
                  contentDescription = h.name,
                  contentScale = ContentScale.Crop,
                  modifier =
                    Modifier.size(44.dp)
                      .clip(CircleShape)
                      .animeCelLighting(
                        preset = CelShaderPreset.CHARACTER,
                        rimColor = Color(h.primaryColorHex),
                        lightAzimuthDeg = celLightAzimuth,
                        rimStrength = 0.72f,
                        outlineStrength = 0.50f,
                      ),
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                  Text(h.name, style = MaterialTheme.typography.titleMedium, color = Color.White)
                  Text(
                    "Lv.${prog?.level ?: 10} • ${h.element.label} • C${prog?.constellationLevel ?: 0}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(h.primaryColorHex),
                  )
                }
                if (profile?.activeHeroId == h.id) {
                  Text("ACTIVE", style = MaterialTheme.typography.labelSmall, color = AstralGold)
                }
              }
            }
          }

          if (heroProg != null) {
            val stats = GameCatalog.computeStats(heroProg)
            Surface(
              color = AstralSurface,
              shape = RoundedCornerShape(16.dp),
              border = BorderStroke(1.dp, elemColor.copy(alpha = 0.45f)),
              modifier = Modifier.fillMaxWidth(),
            ) {
              Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                  Image(
                    painter = painterResource(heroDrawableRes(arch.id)),
                    contentDescription = "${arch.name} cel-shaded portrait",
                    contentScale = ContentScale.Crop,
                    modifier =
                      Modifier.size(64.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .animeCelLighting(
                          preset = CelShaderPreset.CHARACTER,
                          rimColor = elemColor,
                          lightAzimuthDeg = celLightAzimuth,
                          celBands = 3f,
                          rimStrength = 0.88f,
                          outlineStrength = 0.62f,
                          animateRimPulse = true,
                        )
                        .testTag("cel_shader_hero_preview"),
                  )
                  Column(modifier = Modifier.weight(1f)) {
                    Text("${arch.name} — ${arch.title}", style = MaterialTheme.typography.titleMedium, color = Color.White)
                    Text(
                      "PWR ${stats.combatPower}  •  Tier ${heroProg.ascensionTier}  •  ATK ${stats.attack}  •  HP ${stats.maxHp}",
                      style = MaterialTheme.typography.labelSmall,
                      color = elemColor,
                    )
                  }
                }
                Text(
                  "Cel Rim Light Angle: ${celLightAzimuth.toInt()}°",
                  style = MaterialTheme.typography.labelSmall,
                  color = Color.White.copy(alpha = 0.8f),
                )
                Slider(
                  value = celLightAzimuth,
                  onValueChange = { angle ->
                    celLightAzimuth = angle
                    viewModel.worldState.celLightAzimuthDeg = angle
                  },
                  valueRange = 0f..360f,
                  colors =
                    SliderDefaults.colors(
                      thumbColor = elemColor,
                      activeTrackColor = elemColor,
                      inactiveTrackColor = Color.White.copy(alpha = 0.16f),
                    ),
                  modifier = Modifier.fillMaxWidth().height(28.dp).testTag("cel_light_azimuth_slider"),
                )
                if (profile?.activeHeroId != arch.id) {
                  Button(
                    onClick = { viewModel.switchHero(arch.id) },
                    colors = ButtonDefaults.buttonColors(containerColor = elemColor, contentColor = AstralNight),
                    modifier = Modifier.fillMaxWidth().height(40.dp),
                  ) {
                    Text("Deploy Hero")
                  }
                }
              }
            }
          }
        }

        // Right Pane: Tabs + Upgrade Controls
        if (heroProg != null) {
          val stats = GameCatalog.computeStats(heroProg)
          Column(modifier = Modifier.weight(0.58f)) {
            TabRow(
              selectedTabIndex = selectedTab,
              containerColor = AstralSurface,
              contentColor = elemColor,
            ) {
              listOf("Ascend", "Constellations", "Talents", "Gear").forEachIndexed { idx, title ->
                Tab(
                  selected = selectedTab == idx,
                  onClick = { selectedTab = idx },
                  text = { Text(title, style = MaterialTheme.typography.labelMedium) },
                )
              }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
              when (selectedTab) {
                0 -> AttributesTab(heroProg, stats, elemColor, viewModel)
                1 -> ConstellationsTab(heroProg, elemColor, viewModel)
                2 -> TalentsTab(heroProg, arch.skillName, arch.skillDesc, arch.burstName, arch.burstDesc, elemColor, viewModel)
                3 -> GearAndArtifactsTab(heroProg, elemColor, viewModel)
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun AttributesTab(
  hero: HeroProgression,
  stats: com.example.data.HeroCombatStats,
  elemColor: Color,
  viewModel: GameViewModel,
) {
  val cap = ((hero.ascensionTier + 1) * 15).coerceAtMost(90)
  Surface(color = AstralSurfaceVariant, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Text("Combat Attributes", style = MaterialTheme.typography.titleMedium, color = Color.White)
      StatLine("Max HP", "${stats.maxHp}")
      StatLine("Attack", "${stats.attack}")
      StatLine("Defense", "${stats.defense}")
      StatLine("Crit Rate / Crit DMG", "${stats.critRatePct}% / ${stats.critDamagePct}%")
      StatLine("Elemental Mastery", "${stats.elementalMastery}")

      Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        Button(
          onClick = { viewModel.upgradeHeroLevel(hero) },
          enabled = hero.level < cap,
          colors = ButtonDefaults.buttonColors(containerColor = elemColor, contentColor = AstralNight),
          modifier = Modifier.weight(1f).height(48.dp).testTag("level_up_hero_btn"),
        ) {
          Icon(Icons.Default.Upgrade, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(Modifier.width(6.dp))
          Text("Level Up (2 Tomes)")
        }
        OutlinedButton(
          onClick = { viewModel.ascendHero(hero) },
          enabled = hero.ascensionTier < 6,
          border = BorderStroke(1.dp, AstralGold),
          modifier = Modifier.weight(1f).height(48.dp).testTag("ascend_hero_btn"),
        ) {
          Text("Ascend (2 Cores)", color = AstralGold)
        }
      }
    }
  }
}

@Composable
private fun ConstellationsTab(
  hero: HeroProgression,
  elemColor: Color,
  viewModel: GameViewModel,
) {
  val nodeLabels =
    listOf(
      "C1: Flowing Resonance (+10% Speed)",
      "C2: Astral Eye (+12% Crit Rate)",
      "C3: Skill Mastery (+35% Skill Mult)",
      "C4: Ley-Line Surge (+20 EM)",
      "C5: Celestial Zenith (+60% Burst Mult)",
      "C6: Sovereign Crown (Full Elemental Overdrive)",
    )
  Surface(color = AstralSurfaceVariant, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
    Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
      Canvas(modifier = Modifier.fillMaxWidth().height(150.dp)) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val rx = size.width * 0.36f
        val ry = size.height * 0.36f
        val pts =
          (0 until 6).map { i ->
            val a = (-90.0 + i * 60.0) * PI / 180.0
            Offset(cx + cos(a).toFloat() * rx, cy + sin(a).toFloat() * ry)
          }
        for (i in 0 until 5) {
          val lit = i + 1 < hero.constellationLevel
          drawLine(
            color = if (lit) AstralGold else Color.White.copy(alpha = 0.2f),
            start = pts[i],
            end = pts[i + 1],
            strokeWidth = 4f,
            pathEffect = if (lit) null else PathEffect.dashPathEffect(floatArrayOf(10f, 8f)),
          )
        }
        pts.forEachIndexed { idx, pt ->
          val unlocked = idx < hero.constellationLevel
          drawCircle(
            color = if (unlocked) elemColor else AstralNight,
            radius = 16f,
            center = pt,
          )
          drawCircle(
            color = if (unlocked) AstralGold else Color.White.copy(alpha = 0.35f),
            radius = 18f,
            center = pt,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f),
          )
        }
      }
      Spacer(Modifier.height(8.dp))
      nodeLabels.forEachIndexed { idx, desc ->
        val unlocked = idx < hero.constellationLevel
        Row(
          modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Icon(
            Icons.Default.Star,
            contentDescription = null,
            tint = if (unlocked) AstralGold else Color.White.copy(alpha = 0.25f),
            modifier = Modifier.size(16.dp),
          )
          Spacer(Modifier.width(8.dp))
          Text(desc, style = MaterialTheme.typography.bodyMedium, color = if (unlocked) Color.White else Color.Gray)
        }
      }
      Spacer(Modifier.height(12.dp))
      Button(
        onClick = { viewModel.awakenConstellation(hero) },
        enabled = hero.constellationLevel < 6,
        colors = ButtonDefaults.buttonColors(containerColor = AstralGold, contentColor = AstralNight),
        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("awaken_constellation_btn"),
      ) {
        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(if (hero.constellationLevel >= 6) "Constellation Maxed (C6)" else "Awaken Node (300 Gems)")
      }
    }
  }
}

@Composable
private fun TalentsTab(
  hero: HeroProgression,
  skillName: String,
  skillDesc: String,
  burstName: String,
  burstDesc: String,
  elemColor: Color,
  viewModel: GameViewModel,
) {
  Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    TalentCard(
      title = "Normal Attack: Celestial Combo",
      desc = "3-hit sweeping anime slash combo. Current Lv.${hero.talentNormalLv}/10",
      elemColor = elemColor,
      enabled = hero.talentNormalLv < 10,
      onUpgrade = { viewModel.upgradeTalent(hero, "normal") },
    )
    TalentCard(
      title = "Elemental Skill: $skillName",
      desc = "$skillDesc Current Lv.${hero.talentSkillLv}/10",
      elemColor = elemColor,
      enabled = hero.talentSkillLv < 10,
      onUpgrade = { viewModel.upgradeTalent(hero, "skill") },
    )
    TalentCard(
      title = "Elemental Burst: $burstName",
      desc = "$burstDesc Current Lv.${hero.talentBurstLv}/10",
      elemColor = elemColor,
      enabled = hero.talentBurstLv < 10,
      onUpgrade = { viewModel.upgradeTalent(hero, "burst") },
    )
  }
}

@Composable
private fun TalentCard(
  title: String,
  desc: String,
  elemColor: Color,
  enabled: Boolean,
  onUpgrade: () -> Unit,
) {
  Surface(color = AstralSurfaceVariant, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
    Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
      Column(modifier = Modifier.weight(1f)) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = Color.White)
        Text(desc, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
      Spacer(Modifier.width(10.dp))
      Button(
        onClick = onUpgrade,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(containerColor = elemColor, contentColor = AstralNight),
      ) {
        Text("Upgrade")
      }
    }
  }
}

@Composable
private fun GearAndArtifactsTab(
  hero: HeroProgression,
  elemColor: Color,
  viewModel: GameViewModel,
) {
  val currentWeapon = GameCatalog.weaponById(hero.weaponId)
  Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    Surface(color = AstralSurfaceVariant, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
      Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
          "Equipped Weapon: ${currentWeapon.name} (Lv.${hero.weaponLevel} • R${hero.weaponRefinement})",
          style = MaterialTheme.typography.titleMedium,
          color = AstralGold,
        )
        Text(
          "${currentWeapon.substatLabel} • ${currentWeapon.passiveDesc}",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          Button(
            onClick = { viewModel.upgradeWeapon(hero, refine = false) },
            colors = ButtonDefaults.buttonColors(containerColor = elemColor, contentColor = AstralNight),
            modifier = Modifier.weight(1f),
          ) { Text("Enhance +2 Lv") }
          OutlinedButton(
            onClick = { viewModel.upgradeWeapon(hero, refine = true) },
            enabled = hero.weaponRefinement < 5,
            modifier = Modifier.weight(1f),
          ) { Text("Refine (150 Gems)", color = AstralGold) }
        }
        Text("Switch Armament (updates 3D weapon mesh):", style = MaterialTheme.typography.labelMedium, color = Color.White)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
          GameCatalog.weapons.forEach { w ->
            val equipped = w.id == hero.weaponId
            Surface(
              color = if (equipped) Color(w.bladeColorHex).copy(alpha = 0.25f) else AstralSurface,
              shape = RoundedCornerShape(10.dp),
              border = BorderStroke(1.dp, if (equipped) Color(w.bladeColorHex) else Color.White.copy(alpha = 0.15f)),
              modifier = Modifier.weight(1f).clickable { viewModel.equipWeapon(hero, w.id) },
            ) {
              Text(
                w.name.substringBefore(" "),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                modifier = Modifier.padding(8.dp),
              )
            }
          }
        }
      }
    }

    Surface(color = AstralSurfaceVariant, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
      Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(Icons.Default.Shield, contentDescription = null, tint = elemColor, modifier = Modifier.size(18.dp))
          Spacer(Modifier.width(6.dp))
          Text("Artifacts — Set: ${hero.equippedSet}", style = MaterialTheme.typography.titleMedium, color = Color.White)
        }
        val slots =
          listOf(
            Triple("Flower of Life (+HP)", "artifactFlowerLv", hero.artifactFlowerLv),
            Triple("Plume of Death (+ATK)", "artifactFeatherLv", hero.artifactFeatherLv),
            Triple("Sands of Eon (+Crit DMG & EM)", "artifactSandsLv", hero.artifactSandsLv),
            Triple("Goblet of Resonance (+ATK & Crit)", "artifactGobletLv", hero.artifactGobletLv),
            Triple("Circlet of Logos (+Crit Rate)", "artifactCircletLv", hero.artifactCircletLv),
          )
        slots.forEach { (label, field, lv) ->
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Text("$label  +$lv/20", style = MaterialTheme.typography.bodyMedium, color = Color.White)
            OutlinedButton(
              onClick = { viewModel.upgradeArtifact(hero, field, lv, hero.equippedSet) },
              enabled = lv < 20,
            ) { Text("+2", color = AstralCyan) }
          }
        }
      }
    }
  }
}

@Composable
private fun StatLine(label: String, value: String) {
  Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
    Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(value, style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold)
  }
}
