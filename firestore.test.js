const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_uid_001";
const BOB_UID = "bob_uid_002";
const CHARLIE_UID = "charlie_uid_003";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function serverTimestamp() {
  return new Date(Date.now() - 1000);
}

function validProfile(uid) {
  return {
    userId: uid,
    displayName: "Traveler_" + uid.slice(0, 5),
    adventureRank: 1,
    adventureXp: 0,
    starlightGems: 1600,
    mora: 50000,
    expTomes: 25,
    bossCores: 4,
    activeHeroId: "kaelen",
    unlockedWaypoints: ["wp_dawn_ridge"],
    openedChests: [],
    raidsCleared: 0,
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
  };
}

function validHero(uid, heroId) {
  return {
    userId: uid,
    heroId: heroId,
    level: 1,
    ascensionTier: 0,
    xp: 0,
    constellationLevel: 0,
    talentNormalLv: 1,
    talentSkillLv: 1,
    talentBurstLv: 1,
    weaponId: "skyward_zephyr",
    weaponLevel: 1,
    weaponRefinement: 1,
    artifactFlowerLv: 0,
    artifactFeatherLv: 0,
    artifactSandsLv: 0,
    artifactGobletLv: 0,
    artifactCircletLv: 0,
    equippedSet: "Tempest Wanderer",
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
  };
}

function validRaidRoom(hostUid) {
  return {
    hostUserId: hostUid,
    hostName: "Alice",
    dungeonId: "sanctum_storms",
    dungeonName: "Sanctum of Eternal Storms",
    difficulty: "Heroic",
    status: "waiting",
    bossName: "Aetheris, Sovereign of Storms",
    bossElement: "Aero",
    bossHp: 120000,
    bossMaxHp: 120000,
    bossShield: 24000,
    bossMaxShield: 24000,
    phase: 1,
    memberUids: [hostUid],
    memberCount: 1,
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
  };
}

function validRaidMember(uid, roomId, memberUids) {
  return {
    userId: uid,
    roomId: roomId,
    displayName: "Raider_" + uid.slice(0, 5),
    heroId: "kaelen",
    heroLevel: 20,
    combatPower: 1850,
    posX: 0,
    posY: 0,
    posZ: 12,
    yaw: 180,
    hp: 4500,
    maxHp: 4500,
    damageDealt: 0,
    isReady: true,
    lastAction: "idle",
    memberUids: memberUids,
    updatedAt: serverTimestamp(),
  };
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user cannot read or write profiles or raid rooms", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    unauthDb.collection("raidRooms").where("status", "==", "waiting").get()
  );
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).set(validProfile(ALICE_UID))
  );
});

test("User can create, read, and update own profile, but cannot access another user's profile", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set(validProfile(ALICE_UID))
  );
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).update({ mora: 999999, updatedAt: serverTimestamp() })
  );
});

test("Shadow update with ghost field on PlayerProfile fails", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set(validProfile(ALICE_UID))
  );
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      mora: 40000,
      isAdmin: true,
      updatedAt: serverTimestamp(),
    })
  );
});

test("User can create and upgrade own HeroProgression, cross-user blocked", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("heroes")
      .doc("kaelen")
      .set(validHero(ALICE_UID, "kaelen"))
  );

  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("heroes")
      .doc("kaelen")
      .update({
        level: 20,
        talentNormalLv: 3,
        updatedAt: serverTimestamp(),
      })
  );

  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("heroes")
      .doc("kaelen")
      .get()
  );
});

test("Multiplayer raid lifecycle: create, join, batch member, start, damage, terminal lock", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();

  const roomRef = aliceDb.collection("raidRooms").doc("room_001");
  await assertSucceeds(roomRef.set(validRaidRoom(ALICE_UID)));
  await assertSucceeds(
    roomRef
      .collection("members")
      .doc(ALICE_UID)
      .set(validRaidMember(ALICE_UID, "room_001", [ALICE_UID]))
  );

  // Waiting rooms are listable with matching status filter
  await assertSucceeds(
    bobDb.collection("raidRooms").where("status", "==", "waiting").get()
  );
  // Unfiltered raidRooms list must fail (rules are not filters)
  await assertFails(bobDb.collection("raidRooms").get());

  // Bob joins waiting room atomically with member doc
  const bobBatch = bobDb.batch();
  const bobRoomRef = bobDb.collection("raidRooms").doc("room_001");
  bobBatch.update(bobRoomRef, {
    memberUids: [ALICE_UID, BOB_UID],
    memberCount: 2,
    updatedAt: serverTimestamp(),
  });
  bobBatch.set(bobRoomRef.collection("members").doc(BOB_UID), validRaidMember(BOB_UID, "room_001", [ALICE_UID, BOB_UID]));
  await assertSucceeds(bobBatch.commit());

  // Non-host Bob cannot start the raid
  await assertFails(
    bobRoomRef.update({ status: "active", updatedAt: serverTimestamp() })
  );

  // Host Alice starts the raid
  await assertSucceeds(
    roomRef.update({ status: "active", updatedAt: serverTimestamp() })
  );

  // Non-member Charlie cannot read active room or update boss HP
  await assertFails(
    charlieDb.collection("raidRooms").doc("room_001").get()
  );
  await assertFails(
    charlieDb.collection("raidRooms").doc("room_001").update({
      bossHp: 0,
      status: "cleared",
      updatedAt: serverTimestamp(),
    })
  );

  // Member Bob deals damage & clears boss
  await assertSucceeds(
    bobRoomRef.update({
      bossHp: 60000,
      phase: 2,
      updatedAt: serverTimestamp(),
    })
  );
  await assertSucceeds(
    bobRoomRef.update({
      bossHp: 0,
      bossShield: 0,
      status: "cleared",
      updatedAt: serverTimestamp(),
    })
  );

  // Terminal state lock: once cleared, further updates fail
  await assertFails(
    roomRef.update({
      bossHp: 0,
      status: "active",
      updatedAt: serverTimestamp(),
    })
  );
});
