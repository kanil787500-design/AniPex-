package com.example.data

import kotlinx.coroutines.flow.Flow

interface GameDataSource {
  suspend fun ensurePlayerInitialized(userId: String, displayName: String): Result<Unit>

  fun observePlayerProfile(userId: String): Flow<PlayerProfile?>

  fun observeHeroes(userId: String): Flow<List<HeroProgression>>

  suspend fun setActiveHero(heroId: String): Result<Unit>

  suspend fun unlockWaypoint(waypointId: String, currentProfile: PlayerProfile): Result<Unit>

  suspend fun openWorldChest(chestId: String, currentProfile: PlayerProfile): Result<Unit>

  suspend fun upgradeHeroLevel(profile: PlayerProfile, hero: HeroProgression): Result<Unit>

  suspend fun ascendHero(profile: PlayerProfile, hero: HeroProgression): Result<Unit>

  suspend fun unlockConstellation(profile: PlayerProfile, hero: HeroProgression): Result<Unit>

  suspend fun upgradeTalent(profile: PlayerProfile, hero: HeroProgression, slot: String): Result<Unit>

  suspend fun equipAndUpgradeWeapon(
    profile: PlayerProfile,
    hero: HeroProgression,
    weaponId: String,
    upgradeLevel: Boolean,
    refine: Boolean,
  ): Result<Unit>

  suspend fun upgradeArtifact(
    profile: PlayerProfile,
    hero: HeroProgression,
    slotField: String,
    currentLv: Int,
    setName: String,
  ): Result<Unit>

  fun observeOpenRaidRooms(): Flow<List<RaidRoom>>

  fun observeRaidRoom(roomId: String): Flow<RaidRoom?>

  fun observeRaidMembers(roomId: String): Flow<List<RaidMember>>

  suspend fun createRaidRoom(
    profile: PlayerProfile,
    hero: HeroProgression,
    dungeon: DungeonSpec,
    difficulty: String,
  ): Result<String>

  suspend fun joinRaidRoom(
    room: RaidRoom,
    profile: PlayerProfile,
    hero: HeroProgression,
  ): Result<Unit>

  suspend fun syncOwnMemberRoster(roomId: String, memberUids: List<String>): Result<Unit>

  suspend fun startRaidRoom(roomId: String): Result<Unit>

  suspend fun leaveRaidRoom(room: RaidRoom): Result<Unit>

  suspend fun syncRaidCombatTick(
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
  ): Result<Unit>

  suspend fun claimRaidVictoryRewards(profile: PlayerProfile, difficulty: String): Result<Unit>
}
