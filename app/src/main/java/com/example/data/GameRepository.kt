package com.example.data

import android.content.Context
import com.example.R
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

class GameRepository(private val db: FirebaseFirestore) : GameDataSource {

  constructor(context: Context) : this(
    FirebaseFirestore.getInstance(
      context.applicationContext.getString(R.string.firestore_database_id)
    )
  )

  private val auth = Firebase.auth

  private fun requireUserId(): String {
    return auth.currentUser?.uid
      ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
  }

  // --- PLAYER PROFILE & HERO PROGRESSION ---

  override suspend fun ensurePlayerInitialized(userId: String, displayName: String): Result<Unit> {
    return try {
      val cleanName = displayName.trim().ifBlank { "Traveler" }.take(40)
      val userRef = db.collection("users").document(userId)
      val existing = userRef.get().await()
      if (!existing.exists()) {
        val batch = db.batch()
        batch.set(
          userRef,
          mapOf(
            "userId" to userId,
            "displayName" to cleanName,
            "adventureRank" to 1,
            "adventureXp" to 0,
            "starlightGems" to 1600,
            "mora" to 50000,
            "expTomes" to 30,
            "bossCores" to 6,
            "activeHeroId" to "kaelen",
            "unlockedWaypoints" to listOf("wp_dawn_ridge"),
            "openedChests" to emptyList<String>(),
            "raidsCleared" to 0,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
          ),
        )
        GameCatalog.heroes.forEach { arch ->
          val heroRef = userRef.collection("heroes").document(arch.id)
          batch.set(
            heroRef,
            mapOf(
              "userId" to userId,
              "heroId" to arch.id,
              "level" to 10,
              "ascensionTier" to 1,
              "xp" to 0,
              "constellationLevel" to 0,
              "talentNormalLv" to 1,
              "talentSkillLv" to 1,
              "talentBurstLv" to 1,
              "weaponId" to arch.defaultWeaponId,
              "weaponLevel" to 10,
              "weaponRefinement" to 1,
              "artifactFlowerLv" to 4,
              "artifactFeatherLv" to 4,
              "artifactSandsLv" to 0,
              "artifactGobletLv" to 0,
              "artifactCircletLv" to 0,
              "equippedSet" to arch.defaultArtifactSet,
              "createdAt" to FieldValue.serverTimestamp(),
              "updatedAt" to FieldValue.serverTimestamp(),
            ),
          )
        }
        batch.commit().await()
      }
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, "users/$userId")
      Result.failure(e)
    }
  }

  override fun observePlayerProfile(userId: String): Flow<PlayerProfile?> = flow {
    val path = "users/$userId"
    db.collection("users").document(userId)
      .snapshots()
      .map { snapshot ->
        if (snapshot.exists()) {
          snapshot.toObject(PlayerProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        } else {
          null
        }
      }
      .catch { error ->
        val firestoreError =
          generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val exceptionToLog = firestoreError ?: (error as? Exception)
        if (exceptionToLog != null && auth.currentUser?.uid == userId) {
          handleFirestoreError(exceptionToLog, OperationType.GET, path)
        }
        throw error
      }
      .collect { emit(it) }
  }

  override fun observeHeroes(userId: String): Flow<List<HeroProgression>> = flow {
    val path = "users/$userId/heroes"
    db.collection("users").document(userId).collection("heroes")
      .snapshots()
      .map { snapshot ->
        snapshot.toObjects(HeroProgression::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
      }
      .catch { error ->
        val firestoreError =
          generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val exceptionToLog = firestoreError ?: (error as? Exception)
        if (exceptionToLog != null && auth.currentUser?.uid == userId) {
          handleFirestoreError(exceptionToLog, OperationType.LIST, path)
        }
        throw error
      }
      .collect { emit(it) }
  }

  override suspend fun setActiveHero(heroId: String): Result<Unit> {
    val uid = requireUserId()
    val docRef = db.collection("users").document(uid)
    return try {
      docRef.update(
        mapOf(
          "activeHeroId" to heroId,
          "updatedAt" to FieldValue.serverTimestamp(),
        )
      ).await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docRef.path)
      Result.failure(e)
    }
  }

  override suspend fun unlockWaypoint(waypointId: String, currentProfile: PlayerProfile): Result<Unit> {
    val uid = requireUserId()
    if (waypointId in currentProfile.unlockedWaypoints) return Result.success(Unit)
    val nextWaypoints = (currentProfile.unlockedWaypoints + waypointId).distinct().take(10)
    val newXp = currentProfile.adventureXp + 150
    val newRank = (1 + newXp / 500).coerceAtMost(60)
    val docRef = db.collection("users").document(uid)
    return try {
      docRef.update(
        mapOf(
          "unlockedWaypoints" to nextWaypoints,
          "adventureXp" to newXp,
          "adventureRank" to newRank,
          "starlightGems" to (currentProfile.starlightGems + 50),
          "updatedAt" to FieldValue.serverTimestamp(),
        )
      ).await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docRef.path)
      Result.failure(e)
    }
  }

  override suspend fun openWorldChest(chestId: String, currentProfile: PlayerProfile): Result<Unit> {
    val uid = requireUserId()
    if (chestId in currentProfile.openedChests) return Result.success(Unit)
    val nextChests = (currentProfile.openedChests + chestId).distinct().take(20)
    val newXp = currentProfile.adventureXp + 200
    val newRank = (1 + newXp / 500).coerceAtMost(60)
    val docRef = db.collection("users").document(uid)
    return try {
      docRef.update(
        mapOf(
          "openedChests" to nextChests,
          "adventureXp" to newXp,
          "adventureRank" to newRank,
          "starlightGems" to (currentProfile.starlightGems + 80),
          "mora" to (currentProfile.mora + 8000),
          "expTomes" to (currentProfile.expTomes + 5),
          "updatedAt" to FieldValue.serverTimestamp(),
        )
      ).await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docRef.path)
      Result.failure(e)
    }
  }

  override suspend fun upgradeHeroLevel(profile: PlayerProfile, hero: HeroProgression): Result<Unit> {
    val uid = requireUserId()
    val levelCap = (hero.ascensionTier + 1) * 15
    if (hero.level >= levelCap.coerceAtMost(90) || profile.expTomes < 2 || profile.mora < 1500) {
      return Result.failure(IllegalStateException("Insufficient materials or ascension tier required."))
    }
    val userRef = db.collection("users").document(uid)
    val heroRef = userRef.collection("heroes").document(hero.heroId)
    return try {
      val batch = db.batch()
      batch.update(
        userRef,
        mapOf(
          "expTomes" to (profile.expTomes - 2),
          "mora" to (profile.mora - 1500),
          "updatedAt" to FieldValue.serverTimestamp(),
        ),
      )
      batch.update(
        heroRef,
        mapOf(
          "level" to (hero.level + 1).coerceAtMost(90),
          "updatedAt" to FieldValue.serverTimestamp(),
        ),
      )
      batch.commit().await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, heroRef.path)
      Result.failure(e)
    }
  }

  override suspend fun ascendHero(profile: PlayerProfile, hero: HeroProgression): Result<Unit> {
    val uid = requireUserId()
    if (hero.ascensionTier >= 6 || profile.bossCores < 2 || profile.mora < 5000) {
      return Result.failure(IllegalStateException("Need 2 Boss Cores & 5,000 Mora to Ascend."))
    }
    val userRef = db.collection("users").document(uid)
    val heroRef = userRef.collection("heroes").document(hero.heroId)
    return try {
      val batch = db.batch()
      batch.update(
        userRef,
        mapOf(
          "bossCores" to (profile.bossCores - 2),
          "mora" to (profile.mora - 5000),
          "updatedAt" to FieldValue.serverTimestamp(),
        ),
      )
      batch.update(
        heroRef,
        mapOf(
          "ascensionTier" to (hero.ascensionTier + 1).coerceAtMost(6),
          "updatedAt" to FieldValue.serverTimestamp(),
        ),
      )
      batch.commit().await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, heroRef.path)
      Result.failure(e)
    }
  }

  override suspend fun unlockConstellation(profile: PlayerProfile, hero: HeroProgression): Result<Unit> {
    val uid = requireUserId()
    val gemCost = 300
    if (hero.constellationLevel >= 6 || profile.starlightGems < gemCost) {
      return Result.failure(IllegalStateException("Need 300 Starlight Gems to awaken Constellation."))
    }
    val userRef = db.collection("users").document(uid)
    val heroRef = userRef.collection("heroes").document(hero.heroId)
    return try {
      val batch = db.batch()
      batch.update(
        userRef,
        mapOf(
          "starlightGems" to (profile.starlightGems - gemCost),
          "updatedAt" to FieldValue.serverTimestamp(),
        ),
      )
      batch.update(
        heroRef,
        mapOf(
          "constellationLevel" to (hero.constellationLevel + 1).coerceAtMost(6),
          "updatedAt" to FieldValue.serverTimestamp(),
        ),
      )
      batch.commit().await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, heroRef.path)
      Result.failure(e)
    }
  }

  override suspend fun upgradeTalent(profile: PlayerProfile, hero: HeroProgression, slot: String): Result<Unit> {
    val uid = requireUserId()
    if (profile.mora < 2500 || profile.bossCores < 1) {
      return Result.failure(IllegalStateException("Need 1 Boss Core & 2,500 Mora."))
    }
    val fieldName =
      when (slot) {
        "normal" -> "talentNormalLv"
        "skill" -> "talentSkillLv"
        else -> "talentBurstLv"
      }
    val currentLv =
      when (slot) {
        "normal" -> hero.talentNormalLv
        "skill" -> hero.talentSkillLv
        else -> hero.talentBurstLv
      }
    if (currentLv >= 10) return Result.failure(IllegalStateException("Talent already at Max Lv.10"))

    val userRef = db.collection("users").document(uid)
    val heroRef = userRef.collection("heroes").document(hero.heroId)
    return try {
      val batch = db.batch()
      batch.update(
        userRef,
        mapOf(
          "bossCores" to (profile.bossCores - 1),
          "mora" to (profile.mora - 2500),
          "updatedAt" to FieldValue.serverTimestamp(),
        ),
      )
      batch.update(
        heroRef,
        mapOf(
          fieldName to (currentLv + 1),
          "updatedAt" to FieldValue.serverTimestamp(),
        ),
      )
      batch.commit().await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, heroRef.path)
      Result.failure(e)
    }
  }

  override suspend fun equipAndUpgradeWeapon(
    profile: PlayerProfile,
    hero: HeroProgression,
    weaponId: String,
    upgradeLevel: Boolean,
    refine: Boolean,
  ): Result<Unit> {
    val uid = requireUserId()
    val moraCost = if (refine) 4000 else if (upgradeLevel) 1800 else 0
    val gemCost = if (refine) 150 else 0
    if (profile.mora < moraCost || profile.starlightGems < gemCost) {
      return Result.failure(IllegalStateException("Insufficient Mora or Starlight Gems."))
    }
    val userRef = db.collection("users").document(uid)
    val heroRef = userRef.collection("heroes").document(hero.heroId)
    return try {
      val batch = db.batch()
      if (moraCost > 0 || gemCost > 0) {
        batch.update(
          userRef,
          mapOf(
            "mora" to (profile.mora - moraCost),
            "starlightGems" to (profile.starlightGems - gemCost),
            "updatedAt" to FieldValue.serverTimestamp(),
          ),
        )
      }
      batch.update(
        heroRef,
        mapOf(
          "weaponId" to weaponId,
          "weaponLevel" to (if (upgradeLevel) (hero.weaponLevel + 2).coerceAtMost(90) else hero.weaponLevel),
          "weaponRefinement" to (if (refine) (hero.weaponRefinement + 1).coerceAtMost(5) else hero.weaponRefinement),
          "updatedAt" to FieldValue.serverTimestamp(),
        ),
      )
      batch.commit().await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, heroRef.path)
      Result.failure(e)
    }
  }

  override suspend fun upgradeArtifact(
    profile: PlayerProfile,
    hero: HeroProgression,
    slotField: String,
    currentLv: Int,
    setName: String,
  ): Result<Unit> {
    val uid = requireUserId()
    if (profile.mora < 1200) return Result.failure(IllegalStateException("Need 1,200 Mora."))
    val nextLv = (currentLv + 2).coerceAtMost(20)
    val userRef = db.collection("users").document(uid)
    val heroRef = userRef.collection("heroes").document(hero.heroId)
    return try {
      val batch = db.batch()
      batch.update(
        userRef,
        mapOf(
          "mora" to (profile.mora - 1200),
          "updatedAt" to FieldValue.serverTimestamp(),
        ),
      )
      batch.update(
        heroRef,
        mapOf(
          slotField to nextLv,
          "equippedSet" to setName,
          "updatedAt" to FieldValue.serverTimestamp(),
        ),
      )
      batch.commit().await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, heroRef.path)
      Result.failure(e)
    }
  }

  // --- MULTIPLAYER DUNGEON RAIDING ---

  override fun observeOpenRaidRooms(): Flow<List<RaidRoom>> = flow {
    val path = "raidRooms"
    db.collection("raidRooms")
      .whereEqualTo("status", "waiting")
      .snapshots()
      .map { snapshot ->
        snapshot.toObjects(RaidRoom::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
      }
      .catch { error ->
        val firestoreError =
          generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val exceptionToLog = firestoreError ?: (error as? Exception)
        if (exceptionToLog != null && auth.currentUser != null) {
          handleFirestoreError(exceptionToLog, OperationType.LIST, path)
        }
        throw error
      }
      .collect { emit(it) }
  }

  override fun observeRaidRoom(roomId: String): Flow<RaidRoom?> = flow {
    val path = "raidRooms/$roomId"
    db.collection("raidRooms").document(roomId)
      .snapshots()
      .map { snapshot ->
        if (snapshot.exists()) {
          snapshot.toObject(RaidRoom::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        } else {
          null
        }
      }
      .catch { error ->
        val firestoreError =
          generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val exceptionToLog = firestoreError ?: (error as? Exception)
        if (exceptionToLog != null && auth.currentUser != null) {
          handleFirestoreError(exceptionToLog, OperationType.GET, path)
        }
        throw error
      }
      .collect { emit(it) }
  }

  override fun observeRaidMembers(roomId: String): Flow<List<RaidMember>> = flow {
    val uid = requireUserId()
    val path = "raidRooms/$roomId/members"
    db.collection("raidRooms").document(roomId).collection("members")
      .whereArrayContains("memberUids", uid)
      .snapshots()
      .map { snapshot ->
        snapshot.toObjects(RaidMember::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
      }
      .catch { error ->
        val firestoreError =
          generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val exceptionToLog = firestoreError ?: (error as? Exception)
        if (exceptionToLog != null && auth.currentUser?.uid == uid) {
          handleFirestoreError(exceptionToLog, OperationType.LIST, path)
        }
        throw error
      }
      .collect { emit(it) }
  }

  override suspend fun createRaidRoom(
    profile: PlayerProfile,
    hero: HeroProgression,
    dungeon: DungeonSpec,
    difficulty: String,
  ): Result<String> {
    val uid = requireUserId()
    val roomId = "raid_" + UUID.randomUUID().toString().replace("-", "").take(12)
    val mult =
      when (difficulty) {
        "Heroic" -> 1.6f
        "Abyssal" -> 2.4f
        else -> 1.0f
      }
    val bossHp = (dungeon.baseHp * mult).toInt()
    val bossShield = (dungeon.baseShield * mult).toInt()
    val stats = GameCatalog.computeStats(hero)

    val roomRef = db.collection("raidRooms").document(roomId)
    val memberRef = roomRef.collection("members").document(uid)

    return try {
      val batch = db.batch()
      batch.set(
        roomRef,
        mapOf(
          "hostUserId" to uid,
          "hostName" to profile.displayName.take(40),
          "dungeonId" to dungeon.id,
          "dungeonName" to dungeon.name,
          "difficulty" to difficulty,
          "status" to "waiting",
          "bossName" to dungeon.bossName,
          "bossElement" to dungeon.bossElement.label,
          "bossHp" to bossHp,
          "bossMaxHp" to bossHp,
          "bossShield" to bossShield,
          "bossMaxShield" to bossShield,
          "phase" to 1,
          "memberUids" to listOf(uid),
          "memberCount" to 1,
          "createdAt" to FieldValue.serverTimestamp(),
          "updatedAt" to FieldValue.serverTimestamp(),
        ),
      )
      batch.set(
        memberRef,
        mapOf(
          "userId" to uid,
          "roomId" to roomId,
          "displayName" to profile.displayName.take(40),
          "heroId" to hero.heroId,
          "heroLevel" to hero.level,
          "combatPower" to stats.combatPower,
          "posX" to 0.0,
          "posY" to 0.0,
          "posZ" to 12.0,
          "yaw" to 180.0,
          "hp" to stats.maxHp,
          "maxHp" to stats.maxHp,
          "damageDealt" to 0,
          "isReady" to true,
          "lastAction" to "idle",
          "memberUids" to listOf(uid),
          "updatedAt" to FieldValue.serverTimestamp(),
        ),
      )
      batch.commit().await()
      Result.success(roomId)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, roomRef.path)
      Result.failure(e)
    }
  }

  override suspend fun joinRaidRoom(
    room: RaidRoom,
    profile: PlayerProfile,
    hero: HeroProgression,
  ): Result<Unit> {
    val uid = requireUserId()
    if (uid in room.memberUids) return Result.success(Unit)
    if (room.memberUids.size >= 4 || room.status != "waiting") {
      return Result.failure(IllegalStateException("Raid room is full or already active."))
    }
    val nextMembers = (room.memberUids + uid).distinct()
    val stats = GameCatalog.computeStats(hero)
    val roomRef = db.collection("raidRooms").document(room.id)
    val memberRef = roomRef.collection("members").document(uid)

    return try {
      val batch = db.batch()
      batch.update(
        roomRef,
        mapOf(
          "memberUids" to nextMembers,
          "memberCount" to nextMembers.size,
          "updatedAt" to FieldValue.serverTimestamp(),
        ),
      )
      batch.set(
        memberRef,
        mapOf(
          "userId" to uid,
          "roomId" to room.id,
          "displayName" to profile.displayName.take(40),
          "heroId" to hero.heroId,
          "heroLevel" to hero.level,
          "combatPower" to stats.combatPower,
          "posX" to (nextMembers.size - 1) * 2.5,
          "posY" to 0.0,
          "posZ" to 12.0,
          "yaw" to 180.0,
          "hp" to stats.maxHp,
          "maxHp" to stats.maxHp,
          "damageDealt" to 0,
          "isReady" to true,
          "lastAction" to "idle",
          "memberUids" to nextMembers,
          "updatedAt" to FieldValue.serverTimestamp(),
        ),
      )
      batch.commit().await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, roomRef.path)
      Result.failure(e)
    }
  }

  override suspend fun syncOwnMemberRoster(roomId: String, memberUids: List<String>): Result<Unit> {
    val uid = requireUserId()
    val memberRef = db.collection("raidRooms").document(roomId).collection("members").document(uid)
    return try {
      memberRef.update(
        mapOf(
          "memberUids" to memberUids,
          "updatedAt" to FieldValue.serverTimestamp(),
        )
      ).await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, memberRef.path)
      Result.failure(e)
    }
  }

  override suspend fun startRaidRoom(roomId: String): Result<Unit> {
    requireUserId()
    val roomRef = db.collection("raidRooms").document(roomId)
    return try {
      roomRef.update(
        mapOf(
          "status" to "active",
          "updatedAt" to FieldValue.serverTimestamp(),
        )
      ).await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, roomRef.path)
      Result.failure(e)
    }
  }

  override suspend fun leaveRaidRoom(room: RaidRoom): Result<Unit> {
    val uid = requireUserId()
    val roomRef = db.collection("raidRooms").document(room.id)
    return try {
      if (room.hostUserId == uid) {
        roomRef.delete().await()
      } else {
        val nextMembers = room.memberUids.filter { it != uid }
        val batch = db.batch()
        batch.update(
          roomRef,
          mapOf(
            "memberUids" to nextMembers,
            "memberCount" to nextMembers.size,
            "updatedAt" to FieldValue.serverTimestamp(),
          ),
        )
        batch.delete(roomRef.collection("members").document(uid))
        batch.commit().await()
      }
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, roomRef.path)
      Result.failure(e)
    }
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
    val uid = requireUserId()
    val roomRef = db.collection("raidRooms").document(roomId)
    val memberRef = roomRef.collection("members").document(uid)
    return try {
      val batch = db.batch()
      batch.update(
        memberRef,
        mapOf(
          "posX" to posX.toDouble(),
          "posY" to posY.toDouble(),
          "posZ" to posZ.toDouble(),
          "yaw" to yaw.toDouble(),
          "hp" to hp.coerceAtLeast(0),
          "maxHp" to maxHp.coerceAtLeast(1),
          "damageDealt" to totalDamageDealt.coerceAtLeast(0),
          "lastAction" to lastAction.take(40),
          "memberUids" to memberUids,
          "updatedAt" to FieldValue.serverTimestamp(),
        ),
      )
      if (bossHp != null && bossShield != null && phase != null && nextStatus != null) {
        batch.update(
          roomRef,
          mapOf(
            "bossHp" to bossHp.coerceAtLeast(0),
            "bossShield" to bossShield.coerceAtLeast(0),
            "phase" to phase.coerceIn(1, 3),
            "status" to nextStatus,
            "updatedAt" to FieldValue.serverTimestamp(),
          ),
        )
      }
      batch.commit().await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, roomRef.path)
      Result.failure(e)
    }
  }

  override suspend fun claimRaidVictoryRewards(profile: PlayerProfile, difficulty: String): Result<Unit> {
    val uid = requireUserId()
    val mult = when (difficulty) {
      "Abyssal" -> 3
      "Heroic" -> 2
      else -> 1
    }
    val newXp = profile.adventureXp + 350 * mult
    val newRank = (1 + newXp / 500).coerceAtMost(60)
    val userRef = db.collection("users").document(uid)
    return try {
      userRef.update(
        mapOf(
          "adventureXp" to newXp,
          "adventureRank" to newRank,
          "starlightGems" to (profile.starlightGems + 180 * mult),
          "mora" to (profile.mora + 12000 * mult),
          "expTomes" to (profile.expTomes + 8 * mult),
          "bossCores" to (profile.bossCores + 3 * mult),
          "raidsCleared" to (profile.raidsCleared + 1),
          "updatedAt" to FieldValue.serverTimestamp(),
        )
      ).await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, userRef.path)
      Result.failure(e)
    }
  }
}
