package com.example.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import kotlin.math.roundToInt

enum class ElementType(val label: String, val colorHex: Long) {
  AERO("Aero", 0xFF34E8C6),
  PYRO("Pyro", 0xFFFF6D3F),
  CRYO("Cryo", 0xFF8FB8FF),
}

data class HeroArchetype(
  val id: String,
  val name: String,
  val title: String,
  val element: ElementType,
  val weaponType: String,
  val defaultWeaponId: String,
  val defaultArtifactSet: String,
  val baseHp: Int,
  val baseAtk: Int,
  val baseDef: Int,
  val skillName: String,
  val skillDesc: String,
  val burstName: String,
  val burstDesc: String,
  val primaryColorHex: Long,
  val accentColorHex: Long,
)

data class WeaponSpec(
  val id: String,
  val name: String,
  val rarity: Int,
  val baseAtk: Int,
  val substatLabel: String,
  val passiveName: String,
  val passiveDesc: String,
  val bladeColorHex: Long,
)

data class DungeonSpec(
  val id: String,
  val name: String,
  val bossName: String,
  val bossElement: ElementType,
  val description: String,
  val baseHp: Int,
  val baseShield: Int,
  val recommendedPower: Int,
)

data class HeroCombatStats(
  val maxHp: Int,
  val attack: Int,
  val defense: Int,
  val critRatePct: Int,
  val critDamagePct: Int,
  val elementalMastery: Int,
  val combatPower: Int,
  val normalMultiplier: Float,
  val skillMultiplier: Float,
  val burstMultiplier: Float,
)

object GameCatalog {
  val heroes: List<HeroArchetype> =
    listOf(
      HeroArchetype(
        id = "kaelen",
        name = "Kaelen",
        title = "Tempest Blade Dancer",
        element = ElementType.AERO,
        weaponType = "Astral Greatsword",
        defaultWeaponId = "skyward_zephyr",
        defaultArtifactSet = "Tempest Wanderer",
        baseHp = 1280,
        baseAtk = 148,
        baseDef = 78,
        skillName = "Gale Vortex",
        skillDesc = "Slashes a cyan cyclone that pulls nearby foes and triggers Swirl reactions.",
        burstName = "Skyrend Tempest",
        burstDesc = "Leaps into an astral hurricane, unleashing sweeping wind blades in a wide 3D arc.",
        primaryColorHex = 0xFF2FE0C8,
        accentColorHex = 0xFFE6FFF9,
      ),
      HeroArchetype(
        id = "lyra",
        name = "Lyra",
        title = "Solaris Valkyrie",
        element = ElementType.PYRO,
        weaponType = "Solaris Halberd",
        defaultWeaponId = "crimson_oath",
        defaultArtifactSet = "Crimson Witchflame",
        baseHp = 1190,
        baseAtk = 166,
        baseDef = 72,
        skillName = "Blazing Dive",
        skillDesc = "Ignites her lance in phoenix fire, lunging forward for high Pyro AoE damage.",
        burstName = "Supernova Edict",
        burstDesc = "Summons a descending solar flare that shatters enemy shields and triggers Melt.",
        primaryColorHex = 0xFFFF6D3F,
        accentColorHex = 0xFFFFD166,
      ),
      HeroArchetype(
        id = "seraphina",
        name = "Seraphina",
        title = "Astral Frost Arcanist",
        element = ElementType.CRYO,
        weaponType = "Starlight Catalyst",
        defaultWeaponId = "astral_codex",
        defaultArtifactSet = "Glacial Noblesse",
        baseHp = 1360,
        baseAtk = 142,
        baseDef = 86,
        skillName = "Glacial Prism",
        skillDesc = "Conjures crystalline frost spikes around targets while healing the active squad.",
        burstName = "Celestial Eclipse",
        burstDesc = "Calls down orbiting astral rings of absolute zero, freezing and shattering bosses.",
        primaryColorHex = 0xFF8FB8FF,
        accentColorHex = 0xFFD9C7FF,
      ),
    )

  val weapons: List<WeaponSpec> =
    listOf(
      WeaponSpec(
        id = "skyward_zephyr",
        name = "Skyward Zephyr",
        rarity = 5,
        baseAtk = 48,
        substatLabel = "+18% Crit Rate",
        passiveName = "Windborne Edge",
        passiveDesc = "Increases Normal & Skill DMG by 24% (+6% per Refinement).",
        bladeColorHex = 0xFF4AF2DA,
      ),
      WeaponSpec(
        id = "crimson_oath",
        name = "Crimson Oath",
        rarity = 5,
        baseAtk = 54,
        substatLabel = "+32% Crit DMG",
        passiveName = "Phoenix Cinder",
        passiveDesc = "Elemental Burst hits gain +28% ATK & Melt bonus.",
        bladeColorHex = 0xFFFF7A45,
      ),
      WeaponSpec(
        id = "astral_codex",
        name = "Astral Codex",
        rarity = 5,
        baseAtk = 46,
        substatLabel = "+95 Elemental Mastery",
        passiveName = "Starlight Resonance",
        passiveDesc = "Skill casts restore squad Energy and boost Burst DMG by 26%.",
        bladeColorHex = 0xFFB8A1FF,
      ),
      WeaponSpec(
        id = "dawnbreaker_edge",
        name = "Dawnbreaker Edge",
        rarity = 4,
        baseAtk = 39,
        substatLabel = "+20% ATK",
        passiveName = "First Light",
        passiveDesc = "Sprinting or dashing empowers the next strike by 35%.",
        bladeColorHex = 0xFFFFD166,
      ),
    )

  val artifactSets: List<String> =
    listOf("Tempest Wanderer", "Crimson Witchflame", "Glacial Noblesse")

  val dungeons: List<DungeonSpec> =
    listOf(
      DungeonSpec(
        id = "sanctum_storms",
        name = "Sanctum of Eternal Storms",
        bossName = "Aetheris, Sovereign of Storms",
        bossElement = ElementType.AERO,
        description = "Suspended above the shattered ley-lines. Break Aetheris's Aero barrier during phase shifts.",
        baseHp = 75000,
        baseShield = 15000,
        recommendedPower = 1800,
      ),
      DungeonSpec(
        id = "crimson_forge",
        name = "Obsidian Caldera Forge",
        bossName = "Ignis Rex, Molten Colossus",
        bossElement = ElementType.PYRO,
        description = "Dodge expanding magma rings and counter with Cryo or Aero reactions.",
        baseHp = 95000,
        baseShield = 20000,
        recommendedPower = 2400,
      ),
      DungeonSpec(
        id = "glacial_throne",
        name = "Astral Spire of Absolute Zero",
        bossName = "Glacies, Starlight Empress",
        bossElement = ElementType.CRYO,
        description = "High-altitude co-op raid boss with frost nova telegraphs and crystal resonance.",
        baseHp = 120000,
        baseShield = 25000,
        recommendedPower = 3000,
      ),
    )

  fun heroById(id: String): HeroArchetype = heroes.firstOrNull { it.id == id } ?: heroes.first()

  fun weaponById(id: String): WeaponSpec = weapons.firstOrNull { it.id == id } ?: weapons.first()

  fun dungeonById(id: String): DungeonSpec = dungeons.firstOrNull { it.id == id } ?: dungeons.first()

  fun computeStats(hero: HeroProgression): HeroCombatStats {
    val arch = heroById(hero.heroId)
    val weapon = weaponById(hero.weaponId)
    val lvScale = 1f + (hero.level - 1) * 0.045f + hero.ascensionTier * 0.16f
    val weaponScale = weapon.baseAtk * (1f + (hero.weaponLevel - 1) * 0.05f) * (1f + (hero.weaponRefinement - 1) * 0.08f)

    val artifactHp = hero.artifactFlowerLv * 115 + hero.artifactSandsLv * 45
    val artifactAtk = hero.artifactFeatherLv * 14 + hero.artifactGobletLv * 10 + hero.artifactCircletLv * 6
    val artifactCritRate = (hero.artifactCircletLv * 1.1f).roundToInt() + if (hero.constellationLevel >= 2) 12 else 0
    val artifactCritDmg = hero.artifactSandsLv * 2 + hero.artifactGobletLv * 2

    val maxHp = (arch.baseHp * lvScale + artifactHp).roundToInt()
    val atk = (arch.baseAtk * lvScale + weaponScale + artifactAtk).roundToInt()
    val def = (arch.baseDef * lvScale + hero.artifactSandsLv * 8).roundToInt()
    val critRate = (12 + artifactCritRate + if (weapon.id == "skyward_zephyr") 18 else 0).coerceIn(5, 95)
    val critDmg = (60 + artifactCritDmg + if (weapon.id == "crimson_oath") 32 else 0)
    val em = 40 + hero.artifactSandsLv * 6 + hero.constellationLevel * 20 + if (weapon.id == "astral_codex") 95 else 0

    val normalMult = 1.0f + (hero.talentNormalLv - 1) * 0.12f
    val skillMult = 2.15f + (hero.talentSkillLv - 1) * 0.22f + if (hero.constellationLevel >= 3) 0.35f else 0f
    val burstMult = 3.65f + (hero.talentBurstLv - 1) * 0.36f + if (hero.constellationLevel >= 5) 0.6f else 0f

    val power =
      (atk * 2.4f + maxHp * 0.18f + def * 0.7f + critRate * 14f + critDmg * 5f + hero.constellationLevel * 140f)
        .roundToInt()

    return HeroCombatStats(
      maxHp = maxHp,
      attack = atk,
      defense = def,
      critRatePct = critRate,
      critDamagePct = critDmg,
      elementalMastery = em,
      combatPower = power,
      normalMultiplier = normalMult,
      skillMultiplier = skillMult,
      burstMultiplier = burstMult,
    )
  }
}

@IgnoreExtraProperties
data class PlayerProfile(
  val userId: String = "",
  val displayName: String = "Traveler",
  val adventureRank: Int = 1,
  val adventureXp: Int = 0,
  val starlightGems: Int = 1600,
  val mora: Int = 50000,
  val expTomes: Int = 25,
  val bossCores: Int = 4,
  val activeHeroId: String = "kaelen",
  val unlockedWaypoints: List<String> = listOf("wp_dawn_ridge"),
  val openedChests: List<String> = emptyList(),
  val raidsCleared: Int = 0,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class HeroProgression(
  @DocumentId val id: String = "",
  val userId: String = "",
  val heroId: String = "kaelen",
  val level: Int = 1,
  val ascensionTier: Int = 0,
  val xp: Int = 0,
  val constellationLevel: Int = 0,
  val talentNormalLv: Int = 1,
  val talentSkillLv: Int = 1,
  val talentBurstLv: Int = 1,
  val weaponId: String = "skyward_zephyr",
  val weaponLevel: Int = 1,
  val weaponRefinement: Int = 1,
  val artifactFlowerLv: Int = 0,
  val artifactFeatherLv: Int = 0,
  val artifactSandsLv: Int = 0,
  val artifactGobletLv: Int = 0,
  val artifactCircletLv: Int = 0,
  val equippedSet: String = "Tempest Wanderer",
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class RaidRoom(
  @DocumentId val id: String = "",
  val hostUserId: String = "",
  val hostName: String = "",
  val dungeonId: String = "sanctum_storms",
  val dungeonName: String = "Sanctum of Eternal Storms",
  val difficulty: String = "Normal",
  val status: String = "waiting",
  val bossName: String = "Aetheris, Sovereign of Storms",
  val bossElement: String = "Aero",
  val bossHp: Int = 75000,
  val bossMaxHp: Int = 75000,
  val bossShield: Int = 15000,
  val bossMaxShield: Int = 15000,
  val phase: Int = 1,
  val memberUids: List<String> = emptyList(),
  val memberCount: Int = 1,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class RaidMember(
  @DocumentId val id: String = "",
  val userId: String = "",
  val roomId: String = "",
  val displayName: String = "",
  val heroId: String = "kaelen",
  val heroLevel: Int = 1,
  val combatPower: Int = 1000,
  val posX: Double = 0.0,
  val posY: Double = 0.0,
  val posZ: Double = 12.0,
  val yaw: Double = 180.0,
  val hp: Int = 1500,
  val maxHp: Int = 1500,
  val damageDealt: Int = 0,
  val isReady: Boolean = true,
  val lastAction: String = "idle",
  val memberUids: List<String> = emptyList(),
  @ServerTimestamp val updatedAt: Timestamp? = null,
)
