package com.example.data

import com.example.base.FirestoreEmulatorTestBase
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GameRepositoryRuleTest : FirestoreEmulatorTestBase() {

  @Test
  fun initializePlayer_authenticatedUser_createsProfileAndAllHeroes() = runBlocking {
    val uid = signInTestUser(ALICE_EMAIL)
    val repo = GameRepository(firestore)

    val initResult = withTimeout(TIMEOUT_MS) { repo.ensurePlayerInitialized(uid, "KaelenRunner") }
    assertTrue(initResult.isSuccess)

    val profile = withTimeout(FLOW_TIMEOUT_MS) { repo.observePlayerProfile(uid).first { it != null } }
    assertNotNull(profile)
    assertEquals(uid, profile!!.userId)

    val heroes = withTimeout(FLOW_TIMEOUT_MS) { repo.observeHeroes(uid).first { it.size == 3 } }
    assertEquals(3, heroes.size)
  }

  @Test
  fun upgradeHeroAndExplore_authenticatedUser_persistsProgression() = runBlocking {
    val uid = signInTestUser(ALICE_EMAIL)
    val repo = GameRepository(firestore)
    withTimeout(TIMEOUT_MS) { repo.ensurePlayerInitialized(uid, "Alice").getOrThrow() }

    val profile = withTimeout(FLOW_TIMEOUT_MS) { repo.observePlayerProfile(uid).first { it != null } }!!
    val heroes = withTimeout(FLOW_TIMEOUT_MS) { repo.observeHeroes(uid).first { it.size == 3 } }
    val kaelen = heroes.first { it.heroId == "kaelen" }

    val levelUp = withTimeout(TIMEOUT_MS) { repo.upgradeHeroLevel(profile, kaelen) }
    assertTrue(levelUp.isSuccess)

    val chestRes = withTimeout(TIMEOUT_MS) { repo.openWorldChest("chest_ruins_01", profile) }
    assertTrue(chestRes.isSuccess)
  }

  @Test
  fun crossUserProfileAccess_failsWithPermissionDenied() = runBlocking {
    val aliceUid = signInTestUser(ALICE_EMAIL)
    val repo = GameRepository(firestore)
    withTimeout(TIMEOUT_MS) { repo.ensurePlayerInitialized(aliceUid, "Alice").getOrThrow() }

    signInTestUser(BOB_EMAIL)
    var caught: FirebaseFirestoreException? = null
    try {
      withTimeout(FLOW_TIMEOUT_MS) { repo.observePlayerProfile(aliceUid).first() }
    } catch (e: Exception) {
      caught = e.unwrapFirestoreException()
    }
    assertNotNull("Expected FirebaseFirestoreException", caught)
    assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, caught!!.code)
  }

  @Test
  fun unauthenticatedOpenRaids_failsWithPermissionDenied() = runBlocking {
    auth.signOut()
    val repo = GameRepository(firestore)
    var caught: FirebaseFirestoreException? = null
    try {
      withTimeout(FLOW_TIMEOUT_MS) { repo.observeOpenRaidRooms().first() }
    } catch (e: Exception) {
      caught = e.unwrapFirestoreException()
    }
    assertNotNull("Expected FirebaseFirestoreException", caught)
    assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, caught!!.code)
  }

  @Test
  fun multiplayerRaidLifecycle_createJoinStartDamage_succeeds() = runBlocking {
    val aliceUid = signInTestUser(ALICE_EMAIL)
    val repo = GameRepository(firestore)
    withTimeout(TIMEOUT_MS) { repo.ensurePlayerInitialized(aliceUid, "Alice").getOrThrow() }
    val aliceProfile = withTimeout(FLOW_TIMEOUT_MS) { repo.observePlayerProfile(aliceUid).first { it != null } }!!
    val aliceHero = withTimeout(FLOW_TIMEOUT_MS) { repo.observeHeroes(aliceUid).first { it.isNotEmpty() } }.first()

    val roomId =
      withTimeout(TIMEOUT_MS) {
        repo.createRaidRoom(aliceProfile, aliceHero, GameCatalog.dungeons.first(), "Normal").getOrThrow()
      }

    // Bob joins the waiting room
    val bobUid = signInTestUser(BOB_EMAIL)
    withTimeout(TIMEOUT_MS) { repo.ensurePlayerInitialized(bobUid, "Bob").getOrThrow() }
    val bobProfile = withTimeout(FLOW_TIMEOUT_MS) { repo.observePlayerProfile(bobUid).first { it != null } }!!
    val bobHero = withTimeout(FLOW_TIMEOUT_MS) { repo.observeHeroes(bobUid).first { it.isNotEmpty() } }.first()

    val room = withTimeout(FLOW_TIMEOUT_MS) { repo.observeRaidRoom(roomId).first { it != null } }!!
    val joinRes = withTimeout(TIMEOUT_MS) { repo.joinRaidRoom(room, bobProfile, bobHero) }
    assertTrue(joinRes.isSuccess)

    // Host Alice starts raid and syncs damage
    signInTestUser(ALICE_EMAIL)
    val startRes = withTimeout(TIMEOUT_MS) { repo.startRaidRoom(roomId) }
    assertTrue(startRes.isSuccess)

    val dmgRes =
      withTimeout(TIMEOUT_MS) {
        repo.syncRaidCombatTick(
          roomId = roomId,
          memberUids = listOf(aliceUid, bobUid),
          posX = 1.5f,
          posY = 0f,
          posZ = 8f,
          yaw = 180f,
          hp = 1200,
          maxHp = 1500,
          totalDamageDealt = 75000,
          lastAction = "burst",
          bossHp = 0,
          bossShield = 0,
          phase = 3,
          nextStatus = "cleared",
        )
      }
    assertTrue(dmgRes.isSuccess)
  }

  private companion object {
    const val ALICE_EMAIL = "alice.raider@test.com"
    const val BOB_EMAIL = "bob.raider@test.com"
    const val TIMEOUT_MS = 6000L
    const val FLOW_TIMEOUT_MS = 4000L
  }
}
