package com.example.ui.screens

import android.opengl.GLSurfaceView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.KeyboardDoubleArrowUp
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SportsMartialArts
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.GameCatalog
import com.example.data.HeroProgression
import com.example.data.PlayerProfile
import com.example.data.RaidRoom
import com.example.engine3d.AetherRenderer
import com.example.engine3d.GameInputCommand
import com.example.engine3d.HudSnapshot
import com.example.engine3d.WorldMode
import com.example.ui.theme.AstralCryo
import com.example.ui.theme.AstralCyan
import com.example.ui.theme.AstralGold
import com.example.ui.theme.AstralNight
import com.example.ui.theme.AstralRose
import com.example.ui.theme.AstralSurface
import com.example.viewmodel.GameViewModel
import com.example.viewmodel.OverlayPanel
import com.example.viewmodel.UiState
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

@Composable
fun GameViewportScreen(
  viewModel: GameViewModel,
  onSignOut: () -> Unit,
) {
  val context = LocalContext.current
  val profileState by viewModel.profileState.collectAsStateWithLifecycle()
  val heroesState by viewModel.heroesState.collectAsStateWithLifecycle()
  val openRaidsState by viewModel.openRaidRoomsState.collectAsStateWithLifecycle()
  val currentRaidRoom by viewModel.currentRaidRoom.collectAsStateWithLifecycle()
  val currentRaidMembers by viewModel.currentRaidMembers.collectAsStateWithLifecycle()
  val hud by viewModel.hudSnapshot.collectAsStateWithLifecycle()
  val activeOverlay by viewModel.activeOverlay.collectAsStateWithLifecycle()
  val bannerMessage by viewModel.bannerMessage.collectAsStateWithLifecycle()

  val profile = (profileState as? UiState.Success)?.data
  val heroes = (heroesState as? UiState.Success)?.data ?: emptyList()

  // Dismiss secondary overlays on system Back button
  BackHandler(enabled = activeOverlay != OverlayPanel.NONE) {
    viewModel.setOverlay(OverlayPanel.NONE)
  }

  LaunchedEffect(bannerMessage) {
    if (bannerMessage != null) {
      delay(2800L)
      viewModel.clearBanner()
    }
  }

  val glView = remember {
    GLSurfaceView(context).apply {
      setEGLContextClientVersion(3)
      setRenderer(AetherRenderer(viewModel.worldState))
      renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
    }
  }

  DisposableEffect(glView) {
    glView.onResume()
    onDispose { glView.onPause() }
  }

  BoxWithConstraints(modifier = Modifier.fillMaxSize().background(AstralNight)) {
    // 1. Live 3D Cel-Shaded OpenGL ES 3.0 Canvas
    AndroidView(
      factory = { glView },
      modifier = Modifier.fillMaxSize(),
    )

    // 2. Dual-Zone Full Finger Touch Control Surface (Left Thumbstick + Right 360 Orbit & Pinch Zoom)
    var joystickOrigin by remember { mutableStateOf<Offset?>(null) }
    var joystickKnob by remember { mutableStateOf<Offset?>(null) }
    val density = LocalDensity.current
    val stickMaxRadiusPx = with(density) { 68.dp.toPx() }

    Row(modifier = Modifier.fillMaxSize()) {
      // Left 42% -> Dynamic Floating Virtual Joystick
      Box(
        modifier =
          Modifier.fillMaxHeight()
            .fillMaxWidth(0.42f)
            .pointerInput(Unit) {
              awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                joystickOrigin = down.position
                joystickKnob = down.position
                viewModel.worldState.moveInputX = 0f
                viewModel.worldState.moveInputY = 0f

                do {
                  val event = awaitPointerEvent()
                  val change = event.changes.firstOrNull { it.id == down.id }
                  if (change != null && change.pressed) {
                    val delta = change.position - down.position
                    val dist = sqrt(delta.x * delta.x + delta.y * delta.y)
                    val clamped =
                      if (dist > stickMaxRadiusPx) {
                        Offset(delta.x / dist * stickMaxRadiusPx, delta.y / dist * stickMaxRadiusPx)
                      } else {
                        delta
                      }
                    joystickKnob = down.position + clamped
                    viewModel.worldState.moveInputX = (clamped.x / stickMaxRadiusPx).coerceIn(-1f, 1f)
                    viewModel.worldState.moveInputY = (clamped.y / stickMaxRadiusPx).coerceIn(-1f, 1f)
                  } else {
                    break
                  }
                } while (true)

                joystickOrigin = null
                joystickKnob = null
                viewModel.worldState.moveInputX = 0f
                viewModel.worldState.moveInputY = 0f
              }
            }
      )

      // Right 58% -> 360-degree Camera Orbit Drag + Two-Finger Pinch-to-Zoom
      Box(
        modifier =
          Modifier.fillMaxHeight()
            .weight(1f)
            .pointerInput(Unit) {
              detectTransformGestures { _, pan, zoom, _ ->
                viewModel.worldState.camYaw =
                  (viewModel.worldState.camYaw - pan.x * 0.24f) % 360f
                viewModel.worldState.camPitch =
                  (viewModel.worldState.camPitch + pan.y * 0.18f).coerceIn(-8f, 65f)
                if (zoom != 1f) {
                  viewModel.worldState.camDistance =
                    (viewModel.worldState.camDistance / zoom).coerceIn(4.4f, 14.5f)
                }
              }
            }
      )
    }

    // Floating Joystick Visual Indicator
    val jOrigin = joystickOrigin
    val jKnob = joystickKnob
    if (jOrigin != null && jKnob != null) {
      Canvas(modifier = Modifier.fillMaxSize()) {
        drawCircle(
          color = Color.White.copy(alpha = 0.16f),
          radius = stickMaxRadiusPx,
          center = jOrigin,
        )
        drawCircle(
          color = AstralCyan.copy(alpha = 0.65f),
          radius = stickMaxRadiusPx,
          center = jOrigin,
          style = Stroke(width = 3f),
        )
        drawCircle(
          color = AstralCyan.copy(alpha = 0.85f),
          radius = stickMaxRadiusPx * 0.36f,
          center = jKnob,
        )
      }
    }

    // 3. 3D-Projected Floating Damage Numbers & Elemental Reaction Popups
    hud.floatingNumbers.forEach { dmg ->
      val alpha = (1f - (dmg.age / 1.15f)).coerceIn(0f, 1f)
      Text(
        text = dmg.text,
        style = if (dmg.isReaction) MaterialTheme.typography.titleMedium else MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.ExtraBold,
        color = Color(dmg.colorHex),
        modifier =
          Modifier.offset {
              IntOffset(
                (dmg.screenX - 40f).roundToInt(),
                (dmg.screenY - 24f).roundToInt(),
              )
            }
            .alpha(alpha),
      )
    }

    // 4. Translucent Genshin/NTE-Style Game HUD (Safe Insets Applied)
    Box(
      modifier =
        Modifier.fillMaxSize()
          .windowInsetsPadding(WindowInsets.safeDrawing)
          .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
      // TOP-LEFT: Mini-Radar + Player Adventure Rank & Gems
      Row(
        modifier = Modifier.align(Alignment.TopStart),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        MiniRadarDisc(
          hud = hud,
          unlockedWaypoints = viewModel.worldState.unlockedWaypoints,
          openedChests = viewModel.worldState.openedChests,
          waypoints = viewModel.worldState.waypoints,
          chests = viewModel.worldState.chests,
          portalPos = viewModel.worldState.raidPortalPos,
          onClick = { viewModel.setOverlay(OverlayPanel.WORLD_MAP) },
        )

        Surface(
          color = AstralNight.copy(alpha = 0.72f),
          shape = RoundedCornerShape(14.dp),
          border = BorderStroke(1.dp, AstralCyan.copy(alpha = 0.3f)),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column {
              val prefix = if (viewModel.isGuestMode) "GUEST • AR" else "AR"
              Text(
                text = "$prefix ${profile?.adventureRank ?: 1} • ${profile?.displayName ?: "Traveler"}",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
              )
              Text(
                text = "Gems ${profile?.starlightGems ?: 0} • Mora ${profile?.mora ?: 0}",
                style = MaterialTheme.typography.labelSmall,
                color = AstralGold,
              )
            }
            Spacer(Modifier.width(4.dp))
            IconButton(
              onClick = onSignOut,
              modifier = Modifier.size(48.dp).testTag("sign_out_button"),
            ) {
              Icon(
                Icons.AutoMirrored.Filled.Logout,
                contentDescription = if (viewModel.isGuestMode) "Exit Guest / Sign In" else "Sign Out",
                tint = Color.White.copy(alpha = 0.75f),
                modifier = Modifier.size(18.dp),
              )
            }
          }
        }
      }

      // TOP-CENTER: Raid Boss HP Bar OR World Toast Banner
      Column(
        modifier = Modifier.align(Alignment.TopCenter).padding(top = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        if (hud.mode == WorldMode.RAID_ARENA && currentRaidRoom != null) {
          RaidBossHeaderBar(
            room = currentRaidRoom!!,
            telegraphPct = hud.bossTelegraphPct,
            onExitRaid = { viewModel.exitRaidToOpenWorld() },
          )
        }
        AnimatedVisibility(visible = bannerMessage != null, enter = fadeIn(), exit = fadeOut()) {
          Surface(
            color = AstralSurface.copy(alpha = 0.90f),
            shape = RoundedCornerShape(50),
            border = BorderStroke(1.dp, AstralGold.copy(alpha = 0.7f)),
            modifier = Modifier.padding(top = 6.dp),
          ) {
            Text(
              text = bannerMessage.orEmpty(),
              style = MaterialTheme.typography.labelMedium,
              color = AstralGold,
              modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp),
            )
          }
        }
      }

      // TOP-RIGHT: Quick Menu Buttons + Compact Party Quick-Swap Strip underneath
      Column(
        modifier = Modifier.align(Alignment.TopEnd),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          HudTopIconButton(
            icon = Icons.Default.Person,
            label = "Heroes",
            tint = AstralCyan,
            testTag = "open_character_btn",
            onClick = { viewModel.setOverlay(OverlayPanel.CHARACTER) },
          )
          HudTopIconButton(
            icon = Icons.Default.Explore,
            label = "Map",
            tint = AstralGold,
            testTag = "open_map_btn",
            onClick = { viewModel.setOverlay(OverlayPanel.WORLD_MAP) },
          )
          HudTopIconButton(
            icon = Icons.Default.Groups,
            label = "Raid",
            tint = AstralCryo,
            testTag = "open_raid_btn",
            onClick = { viewModel.setOverlay(OverlayPanel.RAID_HUB) },
          )
        }

        if (hud.mode == WorldMode.RAID_ARENA && currentRaidMembers.isNotEmpty()) {
          currentRaidMembers.forEach { m ->
            Surface(
              color = AstralNight.copy(alpha = 0.76f),
              shape = RoundedCornerShape(12.dp),
              border = BorderStroke(1.dp, AstralCyan.copy(alpha = 0.45f)),
            ) {
              Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(horizontalAlignment = Alignment.End) {
                  Text(m.displayName.take(10), style = MaterialTheme.typography.labelSmall, color = Color.White)
                  Text("DMG ${m.damageDealt}", style = MaterialTheme.typography.labelSmall, color = AstralGold)
                }
                Spacer(Modifier.width(6.dp))
                Image(
                  painter = painterResource(heroDrawableRes(m.heroId)),
                  contentDescription = m.displayName,
                  contentScale = ContentScale.Crop,
                  modifier = Modifier.size(28.dp).clip(CircleShape),
                )
              }
            }
          }
        } else {
          Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            GameCatalog.heroes.forEach { arch ->
              val isActive = (profile?.activeHeroId ?: "kaelen") == arch.id
              val elemColor = Color(arch.element.colorHex)
              Surface(
                color = if (isActive) elemColor.copy(alpha = 0.28f) else AstralNight.copy(alpha = 0.68f),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(if (isActive) 2.dp else 1.dp, if (isActive) elemColor else Color.White.copy(alpha = 0.2f)),
                modifier =
                  Modifier.clickable { viewModel.switchHero(arch.id) }
                    .testTag("swap_hero_${arch.id}"),
              ) {
                Row(
                  modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Text(
                    arch.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isActive) Color.White else Color.White.copy(alpha = 0.75f),
                  )
                  Spacer(Modifier.width(4.dp))
                  Image(
                    painter = painterResource(heroDrawableRes(arch.id)),
                    contentDescription = "Switch to ${arch.name}",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(26.dp).clip(CircleShape),
                  )
                }
              }
            }
          }
        }
      }

      // BOTTOM-CENTER: Player HP + Stamina Bar + Interactive Prompt
      Column(
        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        val nearby = hud.nearbyTarget
        if (nearby != null) {
          Button(
            onClick = { viewModel.interactNearby() },
            colors = ButtonDefaults.buttonColors(containerColor = AstralGold, contentColor = AstralNight),
            shape = RoundedCornerShape(50),
            modifier = Modifier.padding(bottom = 8.dp).height(42.dp).testTag("interact_prompt_btn"),
          ) {
            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(nearby.label, style = MaterialTheme.typography.labelMedium)
          }
        }

        Surface(
          color = AstralNight.copy(alpha = 0.75f),
          shape = RoundedCornerShape(14.dp),
          border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f)),
        ) {
          Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp).width(220.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
              Text("HP ${hud.playerHp}/${hud.playerMaxHp}", style = MaterialTheme.typography.labelSmall, color = Color.White)
              Text("STM ${hud.stamina.toInt()}%", style = MaterialTheme.typography.labelSmall, color = AstralGold)
            }
            Spacer(Modifier.height(3.dp))
            LinearProgressIndicator(
              progress = { (hud.playerHp.toFloat() / hud.playerMaxHp.coerceAtLeast(1)).coerceIn(0f, 1f) },
              color = Color(0xFF42E695),
              trackColor = Color.White.copy(alpha = 0.15f),
              modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(4.dp)),
            )
            Spacer(Modifier.height(3.dp))
            LinearProgressIndicator(
              progress = { (hud.stamina / 100f).coerceIn(0f, 1f) },
              color = AstralGold,
              trackColor = Color.White.copy(alpha = 0.12f),
              modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(4.dp)),
            )
          }
        }
      }

      // BOTTOM-RIGHT: Genshin/NTE Combat Action Cluster (Attack, Dash, Jump/Glide, Skill, Burst)
      val activeArch = GameCatalog.heroById(profile?.activeHeroId ?: "kaelen")
      val elemColor = Color(activeArch.element.colorHex)
      Box(
        modifier = Modifier.align(Alignment.BottomEnd).size(width = 216.dp, height = 176.dp),
      ) {
        val burstReady = hud.energy >= 100f && hud.burstCooldown <= 0f
        CombatCircleButton(
          icon = Icons.Default.AutoAwesome,
          label = if (burstReady) "BURST" else "${hud.energy.toInt()}%",
          sizeDp = 52,
          color = if (burstReady) AstralGold else elemColor.copy(alpha = 0.45f),
          highlighted = burstReady,
          testTag = "burst_button",
          modifier = Modifier.align(Alignment.TopStart).offset(x = 26.dp, y = 6.dp),
          onClick = { viewModel.sendCommand(GameInputCommand.ElementalBurst) },
        )

        val skillReady = hud.skillCooldown <= 0f
        CombatCircleButton(
          icon = Icons.Default.Bolt,
          label = if (skillReady) "SKILL" else "${String.format("%.1f", hud.skillCooldown)}s",
          sizeDp = 52,
          color = if (skillReady) elemColor else Color.Gray,
          highlighted = skillReady,
          testTag = "skill_button",
          modifier = Modifier.align(Alignment.CenterStart).offset(x = 0.dp, y = 14.dp),
          onClick = { viewModel.sendCommand(GameInputCommand.ElementalSkill) },
        )

        CombatCircleButton(
          icon = if (hud.isGrounded) Icons.Default.KeyboardDoubleArrowUp else Icons.Default.Flight,
          label = if (hud.isGliding) "GLIDE" else if (hud.isGrounded) "JUMP" else "WINGS",
          sizeDp = 48,
          color = AstralCryo,
          highlighted = hud.isGliding,
          testTag = "jump_button",
          modifier = Modifier.align(Alignment.TopEnd).offset(x = (-8).dp, y = 0.dp),
          onClick = { viewModel.sendCommand(GameInputCommand.JumpOrGlide) },
        )

        CombatCircleButton(
          icon = Icons.Default.FlashOn,
          label = "DASH",
          sizeDp = 50,
          color = Color.White,
          highlighted = false,
          testTag = "dash_button",
          modifier = Modifier.align(Alignment.BottomCenter).offset(x = (-18).dp, y = 0.dp),
          onClick = { viewModel.sendCommand(GameInputCommand.Dash) },
        )

        CombatCircleButton(
          icon = Icons.Default.SportsMartialArts,
          label = "ATK",
          sizeDp = 74,
          color = elemColor,
          highlighted = true,
          testTag = "attack_button",
          modifier = Modifier.align(Alignment.BottomEnd).offset(x = (-4).dp, y = (-4).dp),
          onClick = { viewModel.sendCommand(GameInputCommand.NormalAttack) },
        )
      }

      // VICTORY OVERLAY MODAL when Raid Boss defeated
      if (hud.mode == WorldMode.RAID_ARENA && currentRaidRoom?.status == "cleared") {
        RaidVictoryBanner(
          room = currentRaidRoom!!,
          localDamage = hud.localDamageDealt,
          onReturnToWorld = { viewModel.exitRaidToOpenWorld() },
          modifier = Modifier.align(Alignment.Center),
        )
      }
    }

    // 5. Fullscreen Modals (Character Armory, World Map, Multiplayer Raid Sanctum)
    when (activeOverlay) {
      OverlayPanel.CHARACTER ->
        CharacterProgressionOverlay(
          profile = profile,
          heroes = heroes,
          viewModel = viewModel,
          onClose = { viewModel.setOverlay(OverlayPanel.NONE) },
        )
      OverlayPanel.WORLD_MAP ->
        WorldMapOverlay(
          profile = profile,
          playerX = hud.playerX,
          playerZ = hud.playerZ,
          viewModel = viewModel,
          onClose = { viewModel.setOverlay(OverlayPanel.NONE) },
        )
      OverlayPanel.RAID_HUB ->
        RaidHubOverlay(
          currentRoom = currentRaidRoom,
          members = currentRaidMembers,
          openRoomsState = openRaidsState,
          currentUserId = viewModel.currentUserId,
          isGuestMode = viewModel.isGuestMode,
          viewModel = viewModel,
          onSwitchToGoogleSignIn = onSignOut,
          onClose = { viewModel.setOverlay(OverlayPanel.NONE) },
        )
      OverlayPanel.NONE -> Unit
    }
  }
}

@Composable
private fun HudTopIconButton(
  icon: ImageVector,
  label: String,
  tint: Color,
  testTag: String,
  onClick: () -> Unit,
) {
  Surface(
    color = AstralNight.copy(alpha = 0.76f),
    shape = RoundedCornerShape(14.dp),
    border = BorderStroke(1.dp, tint.copy(alpha = 0.48f)),
    modifier = Modifier.size(48.dp).clickable(onClick = onClick).testTag(testTag),
  ) {
    Column(
      modifier = Modifier.fillMaxSize(),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
    ) {
      Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(20.dp))
      Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White)
    }
  }
}

@Composable
private fun CombatCircleButton(
  icon: ImageVector,
  label: String,
  sizeDp: Int,
  color: Color,
  highlighted: Boolean,
  testTag: String,
  modifier: Modifier = Modifier,
  onClick: () -> Unit,
) {
  Surface(
    color = AstralNight.copy(alpha = 0.78f),
    shape = CircleShape,
    border = BorderStroke(if (highlighted) 2.5.dp else 1.5.dp, color),
    modifier = modifier.size(sizeDp.dp).clip(CircleShape).clickable(onClick = onClick).testTag(testTag),
  ) {
    Box(
      modifier =
        Modifier.fillMaxSize()
          .background(
            Brush.radialGradient(
              colors = listOf(color.copy(alpha = if (highlighted) 0.34f else 0.16f), Color.Transparent)
            )
          ),
      contentAlignment = Alignment.Center,
    ) {
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, contentDescription = label, tint = color, modifier = Modifier.size((sizeDp * 0.42f).dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White)
      }
    }
  }
}

@Composable
private fun MiniRadarDisc(
  hud: HudSnapshot,
  unlockedWaypoints: Set<String>,
  openedChests: Set<String>,
  waypoints: List<com.example.engine3d.WorldWaypoint>,
  chests: List<com.example.engine3d.WorldChest>,
  portalPos: Pair<Float, Float>,
  onClick: () -> Unit,
) {
  Surface(
    color = Color(0xCC0D182F),
    shape = CircleShape,
    border = BorderStroke(2.dp, AstralCyan.copy(alpha = 0.65f)),
    modifier = Modifier.size(64.dp).clip(CircleShape).clickable(onClick = onClick).testTag("mini_radar_btn"),
  ) {
    Canvas(modifier = Modifier.fillMaxSize().padding(6.dp)) {
      val c = Offset(size.width / 2f, size.height / 2f)
      val scale = size.minDimension / 110f
      drawCircle(Color.White.copy(alpha = 0.12f), size.minDimension * 0.48f, c, style = Stroke(1.5f))

      if (hud.mode == WorldMode.OPEN_WORLD) {
        chests.forEach { ch ->
          if (ch.id !in openedChests) {
            val dx = (ch.x - hud.playerX) * scale
            val dz = (ch.z - hud.playerZ) * scale
            if (dx * dx + dz * dz < (size.minDimension * 0.45f) * (size.minDimension * 0.45f)) {
              drawCircle(AstralGold, 3.5f, Offset(c.x + dx, c.y + dz))
            }
          }
        }
        waypoints.forEach { wp ->
          val dx = ((wp.x - hud.playerX) * scale).coerceIn(-22f, 22f)
          val dz = ((wp.z - hud.playerZ) * scale).coerceIn(-22f, 22f)
          drawCircle(if (wp.id in unlockedWaypoints) AstralCyan else AstralRose, 4f, Offset(c.x + dx, c.y + dz))
        }
        val pdx = ((portalPos.first - hud.playerX) * scale).coerceIn(-22f, 22f)
        val pdz = ((portalPos.second - hud.playerZ) * scale).coerceIn(-22f, 22f)
        drawCircle(Color(0xFFA87CFF), 5f, Offset(c.x + pdx, c.y + pdz))
      } else {
        // Boss dot in center
        val bdx = ((0f - hud.playerX) * scale).coerceIn(-22f, 22f)
        val bdz = ((0f - hud.playerZ) * scale).coerceIn(-22f, 22f)
        drawCircle(AstralRose, 6f, Offset(c.x + bdx, c.y + bdz))
      }

      // Player heading arrow
      val rad = (hud.playerYaw * PI / 180.0).toFloat()
      drawLine(Color.White, c, Offset(c.x + sin(rad) * 10f, c.y + cos(rad) * 10f), strokeWidth = 3f)
      drawCircle(Color.White, 4.5f, c)
    }
  }
}

@Composable
private fun RaidBossHeaderBar(
  room: RaidRoom,
  telegraphPct: Float,
  onExitRaid: () -> Unit,
) {
  Surface(
    color = AstralNight.copy(alpha = 0.84f),
    shape = RoundedCornerShape(16.dp),
    border = BorderStroke(1.dp, if (telegraphPct > 0.1f) AstralRose else AstralGold.copy(alpha = 0.55f)),
    modifier = Modifier.fillMaxWidth(0.36f),
  ) {
    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          "${room.bossName} • Phase ${room.phase}",
          style = MaterialTheme.typography.labelMedium,
          color = Color.White,
        )
        Text(
          "Leave",
          style = MaterialTheme.typography.labelSmall,
          color = AstralRose,
          modifier = Modifier.clickable(onClick = onExitRaid).padding(4.dp),
        )
      }
      Spacer(Modifier.height(4.dp))
      LinearProgressIndicator(
        progress = { (room.bossHp.toFloat() / room.bossMaxHp.coerceAtLeast(1)).coerceIn(0f, 1f) },
        color = AstralRose,
        trackColor = Color.White.copy(alpha = 0.15f),
        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
      )
      if (room.bossShield > 0) {
        Spacer(Modifier.height(3.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(Icons.Default.Shield, contentDescription = null, tint = AstralCyan, modifier = Modifier.size(12.dp))
          Spacer(Modifier.width(4.dp))
          LinearProgressIndicator(
            progress = { (room.bossShield.toFloat() / room.bossMaxShield.coerceAtLeast(1)).coerceIn(0f, 1f) },
            color = AstralCyan,
            trackColor = Color.White.copy(alpha = 0.12f),
            modifier = Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(4.dp)),
          )
        }
      }
      if (telegraphPct > 0.15f) {
        Text(
          "BOSS CHARGING AoE — DASH OUT!",
          style = MaterialTheme.typography.labelSmall,
          color = AstralRose,
          modifier = Modifier.padding(top = 2.dp),
        )
      }
    }
  }
}

@Composable
private fun RaidVictoryBanner(
  room: RaidRoom,
  localDamage: Int,
  onReturnToWorld: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Surface(
    color = AstralSurface.copy(alpha = 0.95f),
    shape = RoundedCornerShape(22.dp),
    border = BorderStroke(2.dp, AstralGold),
    modifier = modifier.padding(24.dp),
  ) {
    Column(
      modifier = Modifier.padding(24.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Text("RAID VICTORY!", style = MaterialTheme.typography.headlineMedium, color = AstralGold)
      Text("${room.bossName} Vanquished (${room.difficulty})", style = MaterialTheme.typography.bodyMedium, color = Color.White)
      Text("Your Total Damage: $localDamage", style = MaterialTheme.typography.titleMedium, color = AstralCyan)
      Text("Rewards Claimed: Starlight Gems, Mora, XP Tomes & Boss Cores", style = MaterialTheme.typography.labelMedium, color = AstralGold)
      Spacer(Modifier.height(6.dp))
      Button(
        onClick = onReturnToWorld,
        colors = ButtonDefaults.buttonColors(containerColor = AstralCyan, contentColor = AstralNight),
        modifier = Modifier.height(48.dp),
      ) {
        Text("Return to Open World")
      }
    }
  }
}
