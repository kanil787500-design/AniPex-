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

data class InteractiveTarget(
  val type: String, // "waypoint", "chest", "raid_portal"
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
}

class GameWorldState {
  @Volatile var mode: WorldMode = WorldMode.OPEN_WORLD
  @Volatile var activeHeroId: String = "kaelen"
  @Volatile var activeWeaponId: String = "skyward_zephyr"
  @Volatile var heroStats: HeroCombatStats? = null

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
  @Volatile var camYaw: Float = 180f
  @Volatile var camPitch: Float = 22f
  @Volatile var camDistance: Float = 7.8f

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

  val raidPortalPos = Pair(0f, -15f)
}
