package com.example.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.util.UUID

/**
 * Local in-memory repository for Guest mode.
 * Performs zero Firebase Auth or Firestore calls so offline/guest players can explore the
 * 3D world, progress characters, and play solo practice raids without a Google account.
 */
class GuestGameRepository : GameDataSource {

  private val _profile = MutableStateFlow<PlayerProfile?>(null)
  private val _heroes = MutableStateFlow<List<HeroProgression>>(emptyList())
  private val _activeRoom = MutableStateFlow<RaidRoom?>(null)
  private val _activeMembers = MutableStateFlow<List<RaidMember>>(emptyList())

  override suspend fun ensurePlayerInitialized(userId: String, displayName: String): Result<Unit> {
    if (_profile.value == null) {
      val cleanName = displayName.trim().ifBlank { "Guest Traveler" }.take(40)
      _profile.value =
        PlayerProfile(
          userId = userId,
          displayName = cleanName,
          adventureRank = 1,
          adventureXp = 0,
          starlightGems = 1600,
          mora = 50000,
          expTomes = 30,
          bossCores = 6,
          activeHeroId = "kaelen",
          unlockedWaypoints = listOf("wp_dawn_ridge"),
          openedChests = emptyList(),
          raidsCleared = 0,
        )
    }
    if (_heroes.value.isEmpty()) {
      _heroes.value =
        GameCatalog.heroes.map { arch ->
          HeroProgression(
            id = arch.id,
            userId = userId,
            heroId = arch.id,
            level = 10,
            ascensionTier = 1,
            xp = 0,
            constellationLevel = 0,
            talentNormalLv = 1,
            talentSkillLv = 1,
            talentBurstLv = 1,
            weaponId = arch.defaultWeaponId,
            weaponLevel = 10,
            weaponRefinement = 1,
            artifactFlowerLv = 4,
            artifactFeatherLv = 4,
            artifactSandsLv = 0,
            artifactGobletLv = 0,
            artifactCircletLv = 0,
            equippedSet = arch.defaultArtifactSet,
          )
        }
    }
    return Result.success(Unit)
  }

  override fun observePlayerProfile(userId: String): Flow<PlayerProfile?> = _profile

  override fun observeHeroes(userId: String): Flow<List<HeroProgression>> = _heroes

  override suspend fun setActiveHero(heroId: String): Result<Unit> {
    val cur = _profile.value ?: return Result.failure(IllegalStateException("Guest profile missing"))
    _profile.value = cur.copy(activeHeroId = heroId)
    return Result.success(Unit)
  }

  override suspend fun unlockWaypoint(waypointId: String, currentProfile: PlayerProfile): Result<Unit> {
    if (waypointId in currentProfile.unlockedWaypoints) return Result.success(Unit)
    val nextWaypoints = (currentProfile.unlockedWaypoints + waypointId).distinct().take(10)
    val newXp = currentProfile.adventureXp + 150
    val newRank = (1 + newXp / 500).coerceAtMost(60)
    _profile.value =
      currentProfile.copy(
        unlockedWaypoints = nextWaypoints,
        adventureXp = newXp,
        adventureRank = newRank,
        starlightGems = currentProfile.starlightGems + 50,
      )
    return Result.success(Unit)
  }

  override suspend fun openWorldChest(chestId: String, currentProfile: PlayerProfile): Result<Unit> {
    if (chestId in currentProfile.openedChests) return Result.success(Unit)
    val nextChests = (currentProfile.openedChests + chestId).distinct().take(20)
    val newXp = currentProfile.adventureXp + 200
    val newRank = (1 + newXp / 500).coerceAtMost(60)
    _profile.value =
      currentProfile.copy(
        openedChests = nextChests,
        adventureXp = newXp,
        adventureRank = newRank,
        starlightGems = currentProfile.starlightGems + 80,
        mora = currentProfile.mora + 8000,
        expTomes = currentProfile.expTomes + 5,
      )
    return Result.success(Unit)
  }

  override suspend fun upgradeHeroLevel(profile: PlayerProfile, hero: HeroProgression): Result<Unit> {
    val levelCap = (hero.ascensionTier + 1) * 15
    if (hero.level >= levelCap.coerceAtMost(90) || profile.expTomes < 2 || profile.mora < 1500) {
      return Result.failure(IllegalStateException("Insufficient materials or ascension tier required."))
    }
    _profile.value =
      profile.copy(
        expTomes = profile.expTomes - 2,
        mora = profile.mora - 1500,
      )
    updateHero(hero.heroId) { it.copy(level = (it.level + 1).coerceAtMost(90)) }
    return Result.success(Unit)
  }

  override suspend fun ascendHero(profile: PlayerProfile, hero: HeroProgression): Result<Unit> {
    if (hero.ascensionTier >= 6 || profile.bossCores < 2 || profile.mora < 5000) {
      return Result.failure(IllegalStateException("Need 2 Boss Cores & 5,000 Mora to Ascend."))
    }
    _profile.value =
      profile.copy(
        bossCores = profile.bossCores - 2,
        mora = profile.mora - 5000,
      )
    updateHero(hero.heroId) { it.copy(ascensionTier = (it.ascensionTier + 1).coerceAtMost(6)) }
    return Result.success(Unit)
  }

  override suspend fun unlockConstellation(profile: PlayerProfile, hero: HeroProgression): Result<Unit> {
    val gemCost = 300
    if (hero.constellationLevel >= 6 || profile.starlightGems < gemCost) {
      return Result.failure(IllegalStateException("Need 300 Starlight Gems to awaken Constellation."))
    }
    _profile.value = profile.copy(starlightGems = profile.starlightGems - gemCost)
    updateHero(hero.heroId) { it.copy(constellationLevel = (it.constellationLevel + 1).coerceAtMost(6)) }
    return Result.success(Unit)
  }

  override suspend fun upgradeTalent(profile: PlayerProfile, hero: HeroProgression, slot: String): Result<Unit> {
    if (profile.mora < 2500 || profile.bossCores < 1) {
      return Result.failure(IllegalStateException("Need 1 Boss Core & 2,500 Mora."))
    }
    val currentLv =
      when (slot) {
        "normal" -> hero.talentNormalLv
        "skill" -> hero.talentSkillLv
        else -> hero.talentBurstLv
      }
    if (currentLv >= 10) return Result.failure(IllegalStateException("Talent already at Max Lv.10"))

    _profile.value =
      profile.copy(
        bossCores = profile.bossCores - 1,
        mora = profile.mora - 2500,
      )
    updateHero(hero.heroId) { h ->
      when (slot) {
        "normal" -> h.copy(talentNormalLv = h.talentNormalLv + 1)
        "skill" -> h.copy(talentSkillLv = h.talentSkillLv + 1)
        else -> h.copy(talentBurstLv = h.talentBurstLv + 1)
      }
    }
    return Result.success(Unit)
  }

  override suspend fun equipAndUpgradeWeapon(
    profile: PlayerProfile,
    hero: HeroProgression,
    weaponId: String,
    upgradeLevel: Boolean,
    refine: Boolean,
  ): Result<Unit> {
    val moraCost = if (refine) 4000 else if (upgradeLevel) 1800 else 0
    val gemCost = if (refine) 150 else 0
    if (profile.mora < moraCost || profile.starlightGems < gemCost) {
      return Result.failure(IllegalStateException("Insufficient Mora or Starlight Gems."))
    }
    if (moraCost > 0 || gemCost > 0) {
      _profile.value =
        profile.copy(
          mora = profile.mora - moraCost,
          starlightGems = profile.starlightGems - gemCost,
        )
    }
    updateHero(hero.heroId) { h ->
      h.copy(
        weaponId = weaponId,
        weaponLevel = if (upgradeLevel) (h.weaponLevel + 2).coerceAtMost(90) else h.weaponLevel,
        weaponRefinement = if (refine) (h.weaponRefinement + 1).coerceAtMost(5) else h.weaponRefinement,
      )
    }
    return Result.success(Unit)
  }

  override suspend fun upgradeArtifact(
    profile: PlayerProfile,
    hero: HeroProgression,
    slotField: String,
    currentLv: Int,
    setName: String,
  ): Result<Unit> {
    if (profile.mora < 1200) return Result.failure(IllegalStateException("Need 1,200 Mora."))
    val nextLv = (currentLv + 2).coerceAtMost(20)
    _profile.value = profile.copy(mora = profile.mora - 1200)
    updateHero(hero.heroId) { h ->
      val updated =
        when (slotField) {
          "artifactFlowerLv" -> h.copy(artifactFlowerLv = nextLv)
          "artifactFeatherLv" -> h.copy(artifactFeatherLv = nextLv)
          "artifactSandsLv" -> h.copy(artifactSandsLv = nextLv)
          "artifactGobletLv" -> h.copy(artifactGobletLv = nextLv)
          "artifactCircletLv" -> h.copy(artifactCircletLv = nextLv)
          else -> h
        }
      updated.copy(equippedSet = setName)
    }
    return Result.success(Unit)
  }

  override fun observeOpenRaidRooms(): Flow<List<RaidRoom>> = flowOf(emptyList())

  override fun observeRaidRoom(roomId: String): Flow<RaidRoom?> =
    _activeRoom.map { room -> if (room?.id == roomId) room else null }

  override fun observeRaidMembers(roomId: String): Flow<List<RaidMember>> =
    _activeMembers.map { members -> members.filter { it.roomId == roomId } }

  override suspend fun createRaidRoom(
    profile: PlayerProfile,
    hero: HeroProgression,
    dungeon: DungeonSpec,
    difficulty: String,
  ): Result<String> {
    val roomId = "solo_" + UUID.randomUUID().toString().replace("-", "").take(10)
    val mult =
      when (difficulty) {
        "Heroic" -> 1.6f
        "Abyssal" -> 2.4f
        else -> 1.0f
      }
    val bossHp = (dungeon.baseHp * mult).toInt()
    val bossShield = (dungeon.baseShield * mult).toInt()
    val stats = GameCatalog.computeStats(hero)

    _activeRoom.value =
      RaidRoom(
        id = roomId,
        hostUserId = profile.userId,
        hostName = profile.displayName,
        dungeonId = dungeon.id,
        dungeonName = dungeon.name,
        difficulty = difficulty,
        status = "active",
        bossName = dungeon.bossName,
        bossElement = dungeon.bossElement.label,
        bossHp = bossHp,
        bossMaxHp = bossHp,
        bossShield = bossShield,
        bossMaxShield = bossShield,
        phase = 1,
        memberUids = listOf(profile.userId),
        memberCount = 1,
      )
    _activeMembers.value =
      listOf(
        RaidMember(
          id = profile.userId,
          userId = profile.userId,
          roomId = roomId,
          displayName = profile.displayName,
          heroId = hero.heroId,
          heroLevel = hero.level,
          combatPower = stats.combatPower,
          hp = stats.maxHp,
          maxHp = stats.maxHp,
          memberUids = listOf(profile.userId),
        )
      )
    return Result.success(roomId)
  }

  override suspend fun joinRaidRoom(
    room: RaidRoom,
    profile: PlayerProfile,
    hero: HeroProgression,
  ): Result<Unit> {
    return Result.failure(IllegalStateException("Sign in with Google to join live co-op squads."))
  }

  override suspend fun syncOwnMemberRoster(roomId: String, memberUids: List<String>): Result<Unit> =
    Result.success(Unit)

  override suspend fun startRaidRoom(roomId: String): Result<Unit> {
    val room = _activeRoom.value
    if (room != null && room.id == roomId) {
      _activeRoom.value = room.copy(status = "active")
    }
    return Result.success(Unit)
  }

  override suspend fun leaveRaidRoom(room: RaidRoom): Result<Unit> {
    if (_activeRoom.value?.id == room.id) {
      _activeRoom.value = null
      _activeMembers.value = emptyList()
    }
    return Result.success(Unit)
  }

  override suspend fun syncRaidCombatTick(
    roomId: String,
    memberUids: List<String>,
    posX: Float,
    posY: Float,
    posZ: Float,
    yaw: Float,
    hp: Int,
    maxHp: Int,
    totalDamageDealt: Int,
    lastAction: String,
    bossHp: Int?,
    bossShield: Int?,
    phase: Int?,
    nextStatus: String?,
  ): Result<Unit> {
    _activeMembers.value =
      _activeMembers.value.map { m ->
        m.copy(
          posX = posX.toDouble(),
          posY = posY.toDouble(),
          posZ = posZ.toDouble(),
          yaw = yaw.toDouble(),
          hp = hp.coerceAtLeast(0),
          maxHp = maxHp.coerceAtLeast(1),
          damageDealt = totalDamageDealt.coerceAtLeast(0),
          lastAction = lastAction.take(40),
        )
      }
    val room = _activeRoom.value
    if (room != null && room.id == roomId && bossHp != null && bossShield != null && phase != null && nextStatus != null) {
      _activeRoom.value =
        room.copy(
          bossHp = bossHp.coerceAtLeast(0),
          bossShield = bossShield.coerceAtLeast(0),
          phase = phase.coerceIn(1, 3),
          status = nextStatus,
        )
    }
    return Result.success(Unit)
  }

  override suspend fun claimRaidVictoryRewards(profile: PlayerProfile, difficulty: String): Result<Unit> {
    val mult =
      when (difficulty) {
        "Abyssal" -> 3
        "Heroic" -> 2
        else -> 1
      }
    val newXp = profile.adventureXp + 350 * mult
    val newRank = (1 + newXp / 500).coerceAtMost(60)
    _profile.value =
      profile.copy(
        adventureXp = newXp,
        adventureRank = newRank,
        starlightGems = profile.starlightGems + 180 * mult,
        mora = profile.mora + 12000 * mult,
        expTomes = profile.expTomes + 8 * mult,
        bossCores = profile.bossCores + 3 * mult,
        raidsCleared = profile.raidsCleared + 1,
      )
    return Result.success(Unit)
  }

  private fun updateHero(heroId: String, transform: (HeroProgression) -> HeroProgression) {
    _heroes.value = _heroes.value.map { if (it.heroId == heroId) transform(it) else it }
  }
}
