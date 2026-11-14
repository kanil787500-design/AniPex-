package com.example.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.DungeonSpec
import com.example.data.GameCatalog
import com.example.data.GameDataSource
import com.example.data.HeroProgression
import com.example.data.PlayerProfile
import com.example.data.RaidMember
import com.example.data.RaidRoom
import com.example.engine3d.GameInputCommand
import com.example.engine3d.GameWorldState
import com.example.engine3d.HudSnapshot
import com.example.engine3d.WorldMode
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface UiState<out T> {
  data object Loading : UiState<Nothing>
  data class Success<T>(val data: T) : UiState<T>
  data class Error(val message: String) : UiState<Nothing>
}

enum class OverlayPanel {
  NONE,
  CHARACTER,
  WORLD_MAP,
  RAID_HUB,
}

class GameViewModel(
  private val repository: GameDataSource,
  val currentUserId: String,
  private val initialDisplayName: String,
  val isGuestMode: Boolean = false,
) : ViewModel() {

  val worldState = GameWorldState().apply { localUserId = currentUserId }

  private val _hudSnapshot = MutableStateFlow(HudSnapshot())
  val hudSnapshot: StateFlow<HudSnapshot> = _hudSnapshot.asStateFlow()

  private val _activeOverlay = MutableStateFlow(OverlayPanel.NONE)
  val activeOverlay: StateFlow<OverlayPanel> = _activeOverlay.asStateFlow()

  private val _bannerMessage = MutableStateFlow<String?>(null)
  val bannerMessage: StateFlow<String?> = _bannerMessage.asStateFlow()

  private val _activeRaidRoomId = MutableStateFlow<String?>(null)
  val activeRaidRoomId: StateFlow<String?> = _activeRaidRoomId.asStateFlow()

  private val _victoryRewardClaimedRoomId = MutableStateFlow<String?>(null)

  private var lastRaidSyncTimeMs = 0L
  private var pendingBossHpDmg = 0
  private var pendingBossShieldDmg = 0

  val profileState: StateFlow<UiState<PlayerProfile?>> =
    repository
      .observePlayerProfile(currentUserId)
      .map<PlayerProfile?, UiState<PlayerProfile?>> { profile ->
        if (profile != null) {
          worldState.activeHeroId = profile.activeHeroId
          worldState.unlockedWaypoints = profile.unlockedWaypoints.toSet()
          worldState.openedChests = profile.openedChests.toSet()
        }
        UiState.Success(profile)
      }
      .catch { error ->
        val fsErr = unwrapFirestoreError(error)
        if (fsErr == null && error is CancellationException) throw error
        val root = fsErr ?: error
        Log.w(TAG, "Error observing profile", root)
        emit(UiState.Error(root.message ?: "Failed to load profile"))
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

  val heroesState: StateFlow<UiState<List<HeroProgression>>> =
    repository
      .observeHeroes(currentUserId)
      .map<List<HeroProgression>, UiState<List<HeroProgression>>> { heroes ->
        val activeHero = heroes.firstOrNull { it.heroId == worldState.activeHeroId } ?: heroes.firstOrNull()
        if (activeHero != null) {
          worldState.activeWeaponId = activeHero.weaponId
          worldState.heroStats = GameCatalog.computeStats(activeHero)
        }
        UiState.Success(heroes)
      }
      .catch { error ->
        val fsErr = unwrapFirestoreError(error)
        if (fsErr == null && error is CancellationException) throw error
        val root = fsErr ?: error
        Log.w(TAG, "Error observing heroes", root)
        emit(UiState.Error(root.message ?: "Failed to load heroes"))
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

  val openRaidRoomsState: StateFlow<UiState<List<RaidRoom>>> =
    repository
      .observeOpenRaidRooms()
      .map<List<RaidRoom>, UiState<List<RaidRoom>>> { UiState.Success(it) }
      .catch { error ->
        val fsErr = unwrapFirestoreError(error)
        if (fsErr == null && error is CancellationException) throw error
        val root = fsErr ?: error
        Log.w(TAG, "Error observing raid rooms", root)
        emit(UiState.Error(root.message ?: "Failed to load raid rooms"))
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

  @OptIn(ExperimentalCoroutinesApi::class)
  val currentRaidRoom: StateFlow<RaidRoom?> =
    _activeRaidRoomId
      .flatMapLatest { roomId ->
        if (roomId == null) flowOf(null)
        else
          repository.observeRaidRoom(roomId).catch { error ->
            val fsErr = unwrapFirestoreError(error)
            if (fsErr == null && error is CancellationException) throw error
            emit(null)
          }
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

  @OptIn(ExperimentalCoroutinesApi::class)
  val currentRaidMembers: StateFlow<List<RaidMember>> =
    _activeRaidRoomId
      .flatMapLatest { roomId ->
        if (roomId == null) flowOf(emptyList())
        else
          repository.observeRaidMembers(roomId).catch { error ->
            val fsErr = unwrapFirestoreError(error)
            if (fsErr == null && error is CancellationException) throw error
            emit(emptyList())
          }
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

  init {
    viewModelScope.launch {
      repository.ensurePlayerInitialized(currentUserId, initialDisplayName)
    }

    worldState.onHudUpdate = { snap ->
      _hudSnapshot.value = snap
      maybeSyncRaidState(snap)
    }

    worldState.onBossHit = { hpDmg, shieldDmg ->
      pendingBossHpDmg += hpDmg
      pendingBossShieldDmg += shieldDmg
    }

    worldState.onEnemySlain = { name ->
      _bannerMessage.value = "Defeated $name! +150 Mora"
    }

    viewModelScope.launch {
      currentRaidRoom.collect { room ->
        worldState.activeRaidRoom = room
        if (room != null && room.status == "active" && worldState.mode != WorldMode.RAID_ARENA) {
          worldState.mode = WorldMode.RAID_ARENA
          _activeOverlay.value = OverlayPanel.NONE
          _bannerMessage.value = "RAID COMMENCED: ${room.bossName}"
        }
        if (room != null && room.status == "waiting") {
          val ownMember = currentRaidMembers.value.firstOrNull { it.userId == currentUserId }
          if (ownMember != null && ownMember.memberUids.toSet() != room.memberUids.toSet()) {
            repository.syncOwnMemberRoster(room.id, room.memberUids)
          }
        }
        if (room != null && room.status == "cleared" && _victoryRewardClaimedRoomId.value != room.id) {
          _victoryRewardClaimedRoomId.value = room.id
          val profile = (profileState.value as? UiState.Success)?.data
          if (profile != null) {
            repository.claimRaidVictoryRewards(profile, room.difficulty)
            _bannerMessage.value = "RAID CLEARED! Rewards claimed!"
          }
        }
      }
    }

    viewModelScope.launch {
      currentRaidMembers.collect { members ->
        worldState.remoteRaidMembers = members
      }
    }
  }

  private fun maybeSyncRaidState(snap: HudSnapshot) {
    val room = currentRaidRoom.value ?: return
    if (room.status != "active") return
    val now = System.currentTimeMillis()
    val hasDamage = pendingBossHpDmg > 0 || pendingBossShieldDmg > 0
    if (!hasDamage && now - lastRaidSyncTimeMs < 550L) return
    if (hasDamage && now - lastRaidSyncTimeMs < 240L) return

    lastRaidSyncTimeMs = now
    val dmgToSend = pendingBossHpDmg
    val shieldToSend = pendingBossShieldDmg
    pendingBossHpDmg = 0
    pendingBossShieldDmg = 0

    var nextBossHp: Int? = null
    var nextShield: Int? = null
    var nextPhase: Int? = null
    var nextStatus: String? = null

    if (dmgToSend > 0 || shieldToSend > 0) {
      val hpAfter = (room.bossHp - dmgToSend).coerceAtLeast(0)
      val shieldAfter = (room.bossShield - shieldToSend).coerceAtLeast(0)
      val hpRatio = hpAfter.toFloat() / room.bossMaxHp.coerceAtLeast(1)
      val computedPhase =
        when {
          hpRatio <= 0.33f -> 3
          hpRatio <= 0.66f -> 2
          else -> 1
        }
      nextBossHp = hpAfter
      nextShield = shieldAfter
      nextPhase = computedPhase.coerceAtLeast(room.phase)
      nextStatus = if (hpAfter <= 0) "cleared" else "active"
    }

    viewModelScope.launch {
      repository.syncRaidCombatTick(
        roomId = room.id,
        memberUids = room.memberUids,
        posX = snap.playerX,
        posY = 0f,
        posZ = snap.playerZ,
        yaw = snap.playerYaw,
        hp = snap.playerHp,
        maxHp = snap.playerMaxHp,
        totalDamageDealt = snap.localDamageDealt,
        lastAction = snap.lastAction,
        bossHp = nextBossHp,
        bossShield = nextShield,
        phase = nextPhase,
        nextStatus = nextStatus,
      )
    }
  }

  fun setOverlay(panel: OverlayPanel) {
    _activeOverlay.value = panel
  }

  fun clearBanner() {
    _bannerMessage.value = null
  }

  fun sendCommand(cmd: GameInputCommand) {
    worldState.commandQueue.offer(cmd)
  }

  fun switchHero(heroId: String) {
    val heroes = (heroesState.value as? UiState.Success)?.data ?: return
    val target = heroes.firstOrNull { it.heroId == heroId } ?: return
    worldState.activeHeroId = heroId
    worldState.activeWeaponId = target.weaponId
    worldState.heroStats = GameCatalog.computeStats(target)
    viewModelScope.launch {
      repository.setActiveHero(heroId)
    }
  }

  fun interactNearby() {
    val target = _hudSnapshot.value.nearbyTarget ?: return
    val profile = (profileState.value as? UiState.Success)?.data ?: return
    when (target.type) {
      "raid_portal" -> _activeOverlay.value = OverlayPanel.RAID_HUB
      "waypoint" ->
        viewModelScope.launch {
          val res = repository.unlockWaypoint(target.id, profile)
          if (res.isSuccess) _bannerMessage.value = "Waypoint Resonated! +50 Starlight Gems"
        }
      "chest" ->
        viewModelScope.launch {
          val res = repository.openWorldChest(target.id, profile)
          if (res.isSuccess) _bannerMessage.value = "Chest Looted! +80 Gems, +8,000 Mora, +5 Tomes"
        }
    }
  }

  fun teleportToWaypoint(x: Float, z: Float, name: String) {
    worldState.mode = WorldMode.OPEN_WORLD
    worldState.commandQueue.offer(GameInputCommand.TeleportTo(x, z))
    _activeOverlay.value = OverlayPanel.NONE
    _bannerMessage.value = "Teleported to $name"
  }

  // --- CHARACTER PROGRESSION ACTIONS ---

  fun upgradeHeroLevel(hero: HeroProgression) {
    val profile = (profileState.value as? UiState.Success)?.data ?: return
    viewModelScope.launch {
      val res = repository.upgradeHeroLevel(profile, hero)
      _bannerMessage.value =
        if (res.isSuccess) "${GameCatalog.heroById(hero.heroId).name} reached Lv.${hero.level + 1}!"
        else res.exceptionOrNull()?.message
    }
  }

  fun ascendHero(hero: HeroProgression) {
    val profile = (profileState.value as? UiState.Success)?.data ?: return
    viewModelScope.launch {
      val res = repository.ascendHero(profile, hero)
      _bannerMessage.value =
        if (res.isSuccess) "Ascension Tier ${hero.ascensionTier + 1} Unlocked!"
        else res.exceptionOrNull()?.message
    }
  }

  fun awakenConstellation(hero: HeroProgression) {
    val profile = (profileState.value as? UiState.Success)?.data ?: return
    viewModelScope.launch {
      val res = repository.unlockConstellation(profile, hero)
      _bannerMessage.value =
        if (res.isSuccess) "Constellation C${hero.constellationLevel + 1} Awakened!"
        else res.exceptionOrNull()?.message
    }
  }

  fun upgradeTalent(hero: HeroProgression, slot: String) {
    val profile = (profileState.value as? UiState.Success)?.data ?: return
    viewModelScope.launch {
      val res = repository.upgradeTalent(profile, hero, slot)
      _bannerMessage.value =
        if (res.isSuccess) "Talent Upgraded!" else res.exceptionOrNull()?.message
    }
  }

  fun equipWeapon(hero: HeroProgression, weaponId: String) {
    val profile = (profileState.value as? UiState.Success)?.data ?: return
    viewModelScope.launch {
      repository.equipAndUpgradeWeapon(profile, hero, weaponId, upgradeLevel = false, refine = false)
      _bannerMessage.value = "Equipped ${GameCatalog.weaponById(weaponId).name}"
    }
  }

  fun upgradeWeapon(hero: HeroProgression, refine: Boolean) {
    val profile = (profileState.value as? UiState.Success)?.data ?: return
    viewModelScope.launch {
      val res =
        repository.equipAndUpgradeWeapon(profile, hero, hero.weaponId, upgradeLevel = !refine, refine = refine)
      _bannerMessage.value =
        if (res.isSuccess) if (refine) "Weapon Refined!" else "Weapon Upgraded!"
        else res.exceptionOrNull()?.message
    }
  }

  fun upgradeArtifact(hero: HeroProgression, slotField: String, currentLv: Int, setName: String) {
    val profile = (profileState.value as? UiState.Success)?.data ?: return
    viewModelScope.launch {
      val res = repository.upgradeArtifact(profile, hero, slotField, currentLv, setName)
      _bannerMessage.value =
        if (res.isSuccess) "Artifact Enhanced!" else res.exceptionOrNull()?.message
    }
  }

  // --- MULTIPLAYER RAID ACTIONS ---

  fun createRaidRoom(dungeon: DungeonSpec, difficulty: String) {
    val profile = (profileState.value as? UiState.Success)?.data ?: return
    val heroes = (heroesState.value as? UiState.Success)?.data ?: return
    val activeHero = heroes.firstOrNull { it.heroId == profile.activeHeroId } ?: heroes.firstOrNull() ?: return
    viewModelScope.launch {
      val res = repository.createRaidRoom(profile, activeHero, dungeon, difficulty)
      if (res.isSuccess) {
        _activeRaidRoomId.value = res.getOrThrow()
      } else {
        _bannerMessage.value = res.exceptionOrNull()?.message ?: "Failed to create raid room"
      }
    }
  }

  fun joinRaidRoom(room: RaidRoom) {
    val profile = (profileState.value as? UiState.Success)?.data ?: return
    val heroes = (heroesState.value as? UiState.Success)?.data ?: return
    val activeHero = heroes.firstOrNull { it.heroId == profile.activeHeroId } ?: heroes.firstOrNull() ?: return
    viewModelScope.launch {
      val res = repository.joinRaidRoom(room, profile, activeHero)
      if (res.isSuccess) {
        _activeRaidRoomId.value = room.id
      } else {
        _bannerMessage.value = res.exceptionOrNull()?.message ?: "Failed to join raid room"
      }
    }
  }

  fun startRaid() {
    val roomId = _activeRaidRoomId.value ?: return
    viewModelScope.launch {
      val res = repository.startRaidRoom(roomId)
      if (res.isFailure) _bannerMessage.value = res.exceptionOrNull()?.message
    }
  }

  fun exitRaidToOpenWorld() {
    val room = currentRaidRoom.value
    worldState.mode = WorldMode.OPEN_WORLD
    worldState.activeRaidRoom = null
    worldState.remoteRaidMembers = emptyList()
    _activeRaidRoomId.value = null
    if (room != null && room.status != "cleared") {
      viewModelScope.launch {
        repository.leaveRaidRoom(room)
      }
    }
  }

  private fun unwrapFirestoreError(error: Throwable): FirebaseFirestoreException? {
    return generateSequence(error) { it.cause }
      .filterIsInstance<FirebaseFirestoreException>()
      .firstOrNull()
  }

  private companion object {
    const val TAG = "GameViewModel"
  }
}
