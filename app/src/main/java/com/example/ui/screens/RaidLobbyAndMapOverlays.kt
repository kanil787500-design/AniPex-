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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.data.GameCatalog
import com.example.data.PlayerProfile
import com.example.data.RaidMember
import com.example.data.RaidRoom
import com.example.ui.shader.CelShaderPreset
import com.example.ui.shader.animeCelLighting
import com.example.ui.theme.AstralCryo
import com.example.ui.theme.AstralCyan
import com.example.ui.theme.AstralGold
import com.example.ui.theme.AstralNight
import com.example.ui.theme.AstralRose
import com.example.ui.theme.AstralSurface
import com.example.ui.theme.AstralSurfaceVariant
import com.example.viewmodel.GameViewModel
import com.example.viewmodel.UiState

@Composable
fun WorldMapOverlay(
  profile: PlayerProfile?,
  playerX: Float,
  playerZ: Float,
  viewModel: GameViewModel,
  onClose: () -> Unit,
) {
  val unlocked = profile?.unlockedWaypoints?.toSet() ?: setOf("wp_dawn_ridge")
  val openedChests = profile?.openedChests?.toSet() ?: emptySet()

  Box(
    modifier =
      Modifier.fillMaxSize()
        .background(AstralNight.copy(alpha = 0.94f))
        .windowInsetsPadding(WindowInsets.safeDrawing)
  ) {
    Column(
      modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 10.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
          Text("ATLAS OF AETHERIA", style = MaterialTheme.typography.headlineMedium, color = Color.White)
          Text(
            "Waypoints: ${unlocked.size}/${viewModel.worldState.waypoints.size}  •  Chests: ${openedChests.size}/${viewModel.worldState.chests.size}",
            style = MaterialTheme.typography.labelMedium,
            color = AstralCyan,
          )
        }
        IconButton(onClick = onClose, modifier = Modifier.size(48.dp)) {
          Icon(Icons.Default.Close, contentDescription = "Close Map", tint = Color.White)
        }
      }

      Spacer(Modifier.height(8.dp))

      Row(
        modifier = Modifier.weight(1f).fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
      ) {
        // Left: Interactive Tactical World Map Canvas
        Surface(
          color = Color(0xFF102238),
          shape = RoundedCornerShape(18.dp),
          border = BorderStroke(1.dp, AstralCyan.copy(alpha = 0.4f)),
          modifier =
            Modifier.weight(0.48f)
              .fillMaxSize()
              .clip(RoundedCornerShape(18.dp))
              .animeCelLighting(
                preset = CelShaderPreset.ENVIRONMENT,
                rimColor = AstralCyan,
                lightAzimuthDeg = viewModel.worldState.celLightAzimuthDeg,
                rimStrength = 0.55f,
                outlineStrength = 0.35f,
              ),
        ) {
          Canvas(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            val mapSpan = 120f
            fun toMap(wx: Float, wz: Float): Offset {
              val nx = (wx + mapSpan * 0.5f) / mapSpan
              val nz = (wz + mapSpan * 0.5f) / mapSpan
              return Offset(nx * size.width, nz * size.height)
            }

            val center = toMap(0f, 0f)
            for (r in 1..4) {
              drawCircle(
                color = AstralCyan.copy(alpha = 0.14f),
                radius = r * (size.minDimension * 0.12f),
                center = center,
                style = androidx.compose.ui.graphics.drawscope.Stroke(2f),
              )
            }
            viewModel.worldState.chests.forEach { c ->
              if (c.id !in openedChests) {
                drawCircle(AstralGold, 7f, toMap(c.x, c.z))
              }
            }
            val (px, pz) = viewModel.worldState.raidPortalPos
            drawCircle(Color(0xFFA87CFF), 12f, toMap(px, pz))

            viewModel.worldState.waypoints.forEach { wp ->
              val pos = toMap(wp.x, wp.z)
              drawCircle(if (wp.id in unlocked) AstralCyan else AstralRose, 10f, pos)
            }

            drawCircle(Color.White, 9f, toMap(playerX, playerZ))
          }
        }

        // Right: Fast-Travel Waypoints
        Column(
          modifier = Modifier.weight(0.52f).verticalScroll(rememberScrollState()),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Text("Fast-Travel Waypoints", style = MaterialTheme.typography.titleMedium, color = Color.White)
          viewModel.worldState.waypoints.forEach { wp ->
            val isUnlocked = wp.id in unlocked
            Surface(
              color = AstralSurfaceVariant,
              shape = RoundedCornerShape(14.dp),
              modifier = Modifier.fillMaxWidth(),
            ) {
              Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
              ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                  Icon(
                    Icons.Default.Explore,
                    contentDescription = null,
                    tint = if (isUnlocked) AstralCyan else AstralRose,
                  )
                  Spacer(Modifier.width(10.dp))
                  Column {
                    Text(wp.name, style = MaterialTheme.typography.titleMedium, color = Color.White)
                    Text(
                      if (isUnlocked) "Resonated — Ready for Teleport" else "Undiscovered",
                      style = MaterialTheme.typography.bodyMedium,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                  }
                }
                Button(
                  onClick = { viewModel.teleportToWaypoint(wp.x, wp.z, wp.name) },
                  enabled = isUnlocked,
                  colors = ButtonDefaults.buttonColors(containerColor = AstralCyan, contentColor = AstralNight),
                ) {
                  Text("Teleport")
                }
              }
            }
          }

          Surface(
            color = AstralSurfaceVariant,
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, Color(0xFFA87CFF).copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Row(
              modifier = Modifier.padding(12.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text("Co-op Raid Portal Monolith", style = MaterialTheme.typography.titleMedium, color = Color.White)
                Text("Teleport to the Northern Courtyard Gate", style = MaterialTheme.typography.bodyMedium, color = AstralCryo)
              }
              Button(
                onClick = {
                  val (rx, rz) = viewModel.worldState.raidPortalPos
                  viewModel.teleportToWaypoint(rx, rz + 3f, "Raid Portal")
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFA87CFF), contentColor = AstralNight),
              ) {
                Text("Travel")
              }
            }
          }
        }
      }
    }
  }
}

@Composable
fun RaidHubOverlay(
  currentRoom: RaidRoom?,
  members: List<RaidMember>,
  openRoomsState: UiState<List<RaidRoom>>,
  currentUserId: String,
  isGuestMode: Boolean,
  viewModel: GameViewModel,
  onSwitchToGoogleSignIn: () -> Unit,
  onClose: () -> Unit,
) {
  var selectedDungeonId by remember { mutableStateOf(GameCatalog.dungeons.first().id) }
  var selectedDifficulty by remember { mutableStateOf("Normal") }

  Box(
    modifier =
      Modifier.fillMaxSize()
        .background(AstralNight.copy(alpha = 0.94f))
        .windowInsetsPadding(WindowInsets.safeDrawing)
  ) {
    Column(
      modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 10.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(Icons.Default.Groups, contentDescription = null, tint = AstralCyan, modifier = Modifier.size(26.dp))
          Spacer(Modifier.width(10.dp))
          Text("MULTIPLAYER RAID SANCTUM", style = MaterialTheme.typography.headlineMedium, color = Color.White)
        }
        IconButton(onClick = onClose, modifier = Modifier.size(48.dp)) {
          Icon(Icons.Default.Close, contentDescription = "Close Raid Hub", tint = Color.White)
        }
      }

      Spacer(Modifier.height(8.dp))

      if (currentRoom != null) {
        Surface(
          color = AstralSurface,
          shape = RoundedCornerShape(18.dp),
          border = BorderStroke(1.dp, AstralCyan.copy(alpha = 0.5f)),
          modifier = Modifier.weight(1f).fillMaxWidth(),
        ) {
          Column(modifier = Modifier.padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("${currentRoom.dungeonName} (${currentRoom.difficulty})", style = MaterialTheme.typography.titleMedium, color = AstralGold)
            Text("Boss: ${currentRoom.bossName}  •  Status: ${currentRoom.status.uppercase()} (${currentRoom.memberCount}/4)", style = MaterialTheme.typography.bodyMedium, color = AstralCyan)

            members.forEach { m ->
              Surface(color = AstralSurfaceVariant, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                  Image(
                    painter = painterResource(heroDrawableRes(m.heroId)),
                    contentDescription = m.displayName,
                    contentScale = ContentScale.Crop,
                    modifier =
                      Modifier.size(38.dp)
                        .clip(CircleShape)
                        .animeCelLighting(
                          preset = CelShaderPreset.CHARACTER,
                          rimColor = Color(GameCatalog.heroById(m.heroId).primaryColorHex),
                          lightAzimuthDeg = viewModel.worldState.celLightAzimuthDeg,
                          rimStrength = 0.70f,
                          outlineStrength = 0.45f,
                        ),
                  )
                  Spacer(Modifier.width(10.dp))
                  Column(modifier = Modifier.weight(1f)) {
                    Text(m.displayName, style = MaterialTheme.typography.titleMedium, color = Color.White)
                    Text("${GameCatalog.heroById(m.heroId).name} Lv.${m.heroLevel} • PWR ${m.combatPower}", style = MaterialTheme.typography.labelSmall, color = AstralCyan)
                  }
                  if (m.userId == currentRoom.hostUserId) {
                    Text("HOST", style = MaterialTheme.typography.labelMedium, color = AstralGold)
                  }
                }
              }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
              OutlinedButton(
                onClick = { viewModel.exitRaidToOpenWorld() },
                modifier = Modifier.weight(1f).height(48.dp),
              ) {
                Text("Leave Squad", color = AstralRose)
              }
              if (currentRoom.hostUserId == currentUserId && currentRoom.status == "waiting") {
                Button(
                  onClick = { viewModel.startRaid() },
                  colors = ButtonDefaults.buttonColors(containerColor = AstralCyan, contentColor = AstralNight),
                  modifier = Modifier.weight(1f).height(48.dp).testTag("launch_raid_btn"),
                ) {
                  Icon(Icons.Default.PlayArrow, contentDescription = null)
                  Spacer(Modifier.width(6.dp))
                  Text("Launch 3D Raid")
                }
              }
            }
          }
        }
      } else {
        Row(
          modifier = Modifier.weight(1f).fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
          // Left Pane: Host Dungeon Selector
          Surface(
            color = AstralSurface,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.weight(0.55f).fillMaxSize(),
          ) {
            Column(
              modifier = Modifier.padding(14.dp).verticalScroll(rememberScrollState()),
              verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Text(
                if (isGuestMode) "Solo Practice Dungeon Raid" else "Host a Co-op Dungeon Raid",
                style = MaterialTheme.typography.titleMedium,
                color = AstralGold,
              )
              GameCatalog.dungeons.forEach { d ->
                val selected = d.id == selectedDungeonId
                Surface(
                  color = if (selected) Color(d.bossElement.colorHex).copy(alpha = 0.22f) else AstralSurfaceVariant,
                  shape = RoundedCornerShape(12.dp),
                  border = BorderStroke(1.dp, if (selected) Color(d.bossElement.colorHex) else Color.Transparent),
                  modifier = Modifier.fillMaxWidth().clickable { selectedDungeonId = d.id },
                ) {
                  Column(modifier = Modifier.padding(10.dp)) {
                    Text("${d.name} (${d.bossElement.label})", style = MaterialTheme.typography.titleMedium, color = Color.White)
                    Text("${d.bossName} • Rec. PWR ${d.recommendedPower}", style = MaterialTheme.typography.labelSmall, color = Color(d.bossElement.colorHex))
                  }
                }
              }

              Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                listOf("Normal", "Heroic", "Abyssal").forEach { diff ->
                  val sel = diff == selectedDifficulty
                  Surface(
                    color = if (sel) AstralCyan.copy(alpha = 0.24f) else AstralSurfaceVariant,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, if (sel) AstralCyan else Color.White.copy(alpha = 0.14f)),
                    modifier = Modifier.weight(1f).clickable { selectedDifficulty = diff },
                  ) {
                    Text(
                      diff,
                      style = MaterialTheme.typography.labelMedium,
                      color = if (sel) AstralCyan else Color.White,
                      modifier = Modifier.padding(vertical = 8.dp, horizontal = 10.dp),
                    )
                  }
                }
              }

              Button(
                onClick = { viewModel.createRaidRoom(GameCatalog.dungeonById(selectedDungeonId), selectedDifficulty) },
                colors = ButtonDefaults.buttonColors(containerColor = AstralCyan, contentColor = AstralNight),
                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("create_raid_room_btn"),
              ) {
                Text(if (isGuestMode) "Launch Solo Boss Raid" else "Create Co-op Lobby")
              }
            }
          }

          // Right Pane: Open Public Raid Lobbies or Guest Co-op Prompt
          Column(
            modifier = Modifier.weight(0.45f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            if (isGuestMode) {
              Text("Real-Time Co-op Squads", style = MaterialTheme.typography.titleMedium, color = Color.White)
              Surface(
                color = AstralSurfaceVariant,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, AstralGold.copy(alpha = 0.45f)),
                modifier = Modifier.fillMaxWidth(),
              ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                  Text(
                    "Multiplayer Raid Sanctum requires Google Sign-In",
                    style = MaterialTheme.typography.titleMedium,
                    color = AstralGold,
                  )
                  Text(
                    "You are currently in offline Guest mode. Launch a Solo Boss Raid on the left, or sign in with Google to host and join 4-player co-op rooms.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                  Button(
                    onClick = onSwitchToGoogleSignIn,
                    colors = ButtonDefaults.buttonColors(containerColor = AstralCyan, contentColor = AstralNight),
                    modifier = Modifier.fillMaxWidth().height(48.dp).testTag("guest_to_google_btn"),
                  ) {
                    Text("Sign in with Google")
                  }
                }
              }
            } else {
              Text("Open Squads (Real-Time)", style = MaterialTheme.typography.titleMedium, color = Color.White)
              when (openRoomsState) {
                is UiState.Loading -> Text("Scanning ley-lines for active squads...", color = AstralCryo)
                is UiState.Error -> Text(openRoomsState.message, color = AstralRose)
                is UiState.Success -> {
                  if (openRoomsState.data.isEmpty()) {
                    Text("No open squads right now. Host a dungeon raid on the left!", color = MaterialTheme.colorScheme.onSurfaceVariant)
                  } else {
                    openRoomsState.data.forEach { room ->
                      Surface(color = AstralSurfaceVariant, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                        Row(
                          modifier = Modifier.padding(12.dp),
                          verticalAlignment = Alignment.CenterVertically,
                          horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                          Column(modifier = Modifier.weight(1f)) {
                            Text("${room.dungeonName} (${room.difficulty})", style = MaterialTheme.typography.titleMedium, color = Color.White)
                            Text("Host: ${room.hostName} • ${room.memberCount}/4", style = MaterialTheme.typography.bodyMedium, color = AstralCyan)
                          }
                          Button(
                            onClick = { viewModel.joinRaidRoom(room) },
                            enabled = room.memberCount < 4,
                            colors = ButtonDefaults.buttonColors(containerColor = AstralGold, contentColor = AstralNight),
                          ) {
                            Text("Join")
                          }
                        }
                      }
                    }
                  }
                }
              }
            }
          }
        }
      }
    }
  }
}
