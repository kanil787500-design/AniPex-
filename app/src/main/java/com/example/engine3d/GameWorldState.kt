package com.example.engine3d

import com.example.data.ElementType
import com.example.data.HeroCombatStats
import com.example.data.RaidMember
import com.example.data.RaidRoom
import java.util.concurrent.ConcurrentLinkedQueue

enum class WorldMode {
  OPEN_WORLD,
  RAID_ARENA,
}

data class WorldWaypoint(
  val id: String,
  val name: String,
  val x: Float,
  val z: Float,
)

data class WorldChest(
  val id: String,
  val name: String,
  val x: Float,
  val z: Float,
)

data class WorldEnemy(
  val id: String,
  val name: String,
  val element: ElementType,
  var x: Float,
  var y: Float,
  var z: Float,
  val homeX: Float,
  val homeZ: Float,
  var yaw: Float,
  var hp: Int,
  val maxHp: Int,
  var isGolem: Boolean,
  var attackTimer: Float = 0f,
  var hitFlash: Float = 0f,
  var respawnTimer: Float = 0f,
  var appliedElement: ElementType? = null,
)

data class FloatingDamageText(
  val id: Long,
  val text: String,
  val screenX: Float,
  val screenY: Float,
  val colorHex: Long,
  val isReaction: Boolean,
  val age: Float,
)

data class WorldNpc(
  val id: String,
  val name: String,
  val role: String,
  var x: Float,
  var y: Float,
  var z: Float,
  val homeX: Float,
  val homeZ: Float,
  val patrolX: Float,
  val patrolZ: Float,
  var yaw: Float,
  val hairColorHex: Long,
  val coatColorHex: Long,
  val accentColorHex: Long,
  val faceVariant: Int, // 0=cyan eyes, 1=amber eyes, 2=violet eyes
  val hairStyle: Int, // 0=short spiked, 1=twin-tails, 2=long wave
  val greeting: String,
  val benefitLabel: String,
  val rewardType: String, // "heal", "energy", "mora", "stamina"
  var walkForward: Boolean = true,
  var gaitPhase: Float = 0f,
  var moveSpeed: Float = 0f,
)

data class NpcDialogueInfo(
  val npcId: String,
  val name: String,
  val role: String,
  val line: String,
  val benefitLabel: String,
)

data class InteractiveTarget(
  val type: String, // "waypoint", "chest", "raid_portal", "npc"
  val id: String,
  val label: String,
)

data class HudSnapshot(
  val mode: WorldMode = WorldMode.OPEN_WORLD,
  val playerHp: Int = 1280,
  val playerMaxHp: Int = 1280,
  val stamina: Float = 100f,
  val energy: Float = 60f,
  val skillCooldown: Float = 0f,
  val burstCooldown: Float = 0f,
  val isGliding: Boolean = false,
  val isGrounded: Boolean = true,
  val playerX: Float = 0f,
  val playerZ: Float = 0f,
  val playerYaw: Float = 0f,
  val camYaw: Float = 180f,
  val nearbyTarget: InteractiveTarget? = null,
  val floatingNumbers: List<FloatingDamageText> = emptyList(),
  val bossTelegraphPct: Float = 0f,
  val localDamageDealt: Int = 0,
  val lastAction: String = "idle",
)

sealed interface GameInputCommand {
  data object NormalAttack : GameInputCommand
  data object ElementalSkill : GameInputCommand
  data object ElementalBurst : GameInputCommand
  data object JumpOrGlide : GameInputCommand
  data object Dash : GameInputCommand
  data class TeleportTo(val x: Float, val z: Float) : GameInputCommand
  data class ApplyNpcBenefit(val rewardType: String) : GameInputCommand
}

enum class GraphicsQualityTier(val scale: Int, val label: String) {
  STANDARD_1X(1, "1X STD"),
  HIGH_2X(2, "2X HIGH"),
  ULTRA_3X(3, "3X ULTRA"),
}

class GameWorldState {
  @Volatile var mode: WorldMode = WorldMode.OPEN_WORLD
  @Volatile var activeHeroId: String = "kaelen"
  @Volatile var activeWeaponId: String = "skyward_zephyr"
  @Volatile var heroStats: HeroCombatStats? = null
  @Volatile var graphicsQuality: GraphicsQualityTier = GraphicsQualityTier.ULTRA_3X
  @Volatile var cinematicMode: Boolean = false

  // Movement joystick (-1..1)
  @Volatile var moveInputX: Float = 0f
  @Volatile var moveInputY: Float = 0f

  // Unlocked waypoints & opened chests mirrored from Firestore
  @Volatile var unlockedWaypoints: Set<String> = setOf("wp_dawn_ridge")
  @Volatile var openedChests: Set<String> = emptySet()

  // Active Raid Room & Remote Co-op Party mirrored from Firestore
  @Volatile var activeRaidRoom: RaidRoom? = null
  @Volatile var remoteRaidMembers: List<RaidMember> = emptyList()
  @Volatile var localUserId: String = ""

  // Camera orbit parameters (controlled by right-side drag & pinch zoom)
  @Volatile var camYaw: Float = 16f
  @Volatile var camPitch: Float = 12f
  @Volatile var camDistance: Float = 4.4f

  // Shared anime cel-lighting azimuth synced between Compose shader modifier and 3D GL cel renderer
  @Volatile var celLightAzimuthDeg: Float = 42f

  val commandQueue = ConcurrentLinkedQueue<GameInputCommand>()

  // Callbacks invoked from engine when player deals boss damage or triggers world actions
  @Volatile var onBossHit: ((hpDamage: Int, shieldDamage: Int) -> Unit)? = null
  @Volatile var onEnemySlain: ((enemyName: String) -> Unit)? = null
  @Volatile var onHudUpdate: ((HudSnapshot) -> Unit)? = null

  val waypoints =
    listOf(
      WorldWaypoint("wp_dawn_ridge", "Astral Courtyard Waypoint", 0f, -6f),
      WorldWaypoint("wp_emerald_crest", "Emerald Crest Overlook", 34f, -28f),
      WorldWaypoint("wp_starfall_arch", "Starfall Monolith Spire", -38f, 24f),
    )

  val chests =
    listOf(
      WorldChest("chest_courtyard", "Astral Reliquary Chest", 10f, 8f),
      WorldChest("chest_ridge", "Zephyr Crest Chest", 38f, -22f),
      WorldChest("chest_valley", "Sunlit Meadow Chest", -22f, -32f),
      WorldChest("chest_monolith", "Starlight Vault Chest", -34f, 30f),
      WorldChest("chest_highland", "Sovereign Overlook Chest", 28f, 34f),
    )

  val npcs =
    listOf(
      WorldNpc(
        id = "npc_elena",
        name = "Sentinel Elena",
        role = "Sanctuary Vanguard",
        x = 4.6f, y = 0f, z = -13.5f,
        homeX = 4.6f, homeZ = -13.5f,
        patrolX = 4.6f, patrolZ = -13.5f,
        yaw = -24f,
        hairColorHex = 0xFFE6C87E,
        coatColorHex = 0xFF28406E,
        accentColorHex = 0xFF42F2E2,
        faceVariant = 0,
        hairStyle = 2,
        greeting = "The Hethereau Skybridge ahead leads straight into Aether Metro. Keep your Resonance Burst ready!",
        benefitLabel = "Tactical Charge (+35% Energy)",
        rewardType = "energy",
      ),
      WorldNpc(
        id = "npc_mika",
        name = "Barista Mika",
        role = "Starlight Sidewalk Cafe",
        x = -7.0f, y = 0.22f, z = -22.2f,
        homeX = -7.0f, homeZ = -22.2f,
        patrolX = -7.0f, patrolZ = -22.2f,
        yaw = 32f,
        hairColorHex = 0xFFFF98BA,
        coatColorHex = 0xFF6B2D4A,
        accentColorHex = 0xFFFFB4D2,
        faceVariant = 1,
        hairStyle = 1,
        greeting = "Welcome to Aether Downtown! Have a fresh Astral Espresso while watching the Sky-Rail glide by.",
        benefitLabel = "Astral Espresso (Full HP Restored)",
        rewardType = "heal",
      ),
      WorldNpc(
        id = "npc_jin",
        name = "Navigator Jin",
        role = "Metro Sector 07 Guide",
        x = 5.4f, y = 0.22f, z = -23.5f,
        homeX = 5.4f, homeZ = -23.5f,
        patrolX = 5.4f, patrolZ = -23.5f,
        yaw = -42f,
        hairColorHex = 0xFF2E3852,
        coatColorHex = 0xFF1E3248,
        accentColorHex = 0xFF3EF0FF,
        faceVariant = 0,
        hairStyle = 0,
        greeting = "Notice the Jumbotrons and street banners? Sector 07 runs on pure Astral Resonance grids.",
        benefitLabel = "Grid Overcharge (+45% Energy & Stamina)",
        rewardType = "overcharge",
      ),
      WorldNpc(
        id = "npc_kai",
        name = "Engineer Kai",
        role = "Billboard Display Tech",
        x = -5.8f, y = 0.22f, z = -39.0f,
        homeX = -5.8f, homeZ = -39.0f,
        patrolX = -5.8f, patrolZ = -39.0f,
        yaw = 88f,
        hairColorHex = 0xFF8AB4FF,
        coatColorHex = 0xFF343C54,
        accentColorHex = 0xFFFFD166,
        faceVariant = 2,
        hairStyle = 0,
        greeting = "Just calibrated the 4K facade screens along the crossing. Even the rain puddles reflect them!",
        benefitLabel = "Overdrive Tune (+100% Stamina)",
        rewardType = "stamina",
      ),
      WorldNpc(
        id = "npc_ren",
        name = "Courier Ren",
        role = "City Express Runner",
        x = -5.6f, y = 0.22f, z = -35.5f,
        homeX = -5.6f, homeZ = -35.5f,
        patrolX = -5.6f, patrolZ = -52.0f,
        yaw = 180f,
        hairColorHex = 0xFFC2D4F2,
        coatColorHex = 0xFF205468,
        accentColorHex = 0xFF4AF6D8,
        faceVariant = 0,
        hairStyle = 0,
        greeting = "On route across Neon Boulevard! Stay sharp around the Scramble Crossing.",
        benefitLabel = "Sprint Boost (+100% Stamina)",
        rewardType = "stamina",
        moveSpeed = 1.65f,
      ),
      WorldNpc(
        id = "npc_hana",
        name = "Hana",
        role = "Downtown Stroller",
        x = 6.2f, y = 0.22f, z = -50.0f,
        homeX = 6.2f, homeZ = -50.0f,
        patrolX = 6.2f, patrolZ = -35.2f,
        yaw = 0f,
        hairColorHex = 0xFF4B3056,
        coatColorHex = 0xFF883258,
        accentColorHex = 0xFFFF90B8,
        faceVariant = 2,
        hairStyle = 2,
        greeting = "The sakura blossoms look gorgeous next to the neon billboards under the skybridge!",
        benefitLabel = "Cheer (+25% Energy)",
        rewardType = "energy",
        moveSpeed = 1.35f,
      ),
      WorldNpc(
        id = "npc_yuna",
        name = "Scholar Yuna",
        role = "Lakeside Researcher",
        x = 13.5f, y = 0f, z = 12.0f,
        homeX = 13.5f, homeZ = 12.0f,
        patrolX = 19.5f, patrolZ = 17.0f,
        yaw = 45f,
        hairColorHex = 0xFF9AC8FF,
        coatColorHex = 0xFF2D4674,
        accentColorHex = 0xFF86E8FF,
        faceVariant = 2,
        hairStyle = 1,
        greeting = "I'm studying how Moonlit Lake ripples echo the Sanctuary's astral crystals.",
        benefitLabel = "Starlight Blessing (Full HP)",
        rewardType = "heal",
        moveSpeed = 1.15f,
      ),
    )

  val raidPortalPos = Pair(0f, -15f)
}
