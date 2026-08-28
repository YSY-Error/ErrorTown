package com.Util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Guards the game rule name translation ErrorTown needs to span Minecraft 1.21 through 26.2.
 *
 * <p>1.21.11 renamed every game rule, and the server APIs disagree about it: Spigot 26.2 renamed its
 * {@code GameRule} constants, Paper 26.2 kept the old ones. Getting a name wrong does not fail the
 * build — it silently stops a home rule from applying — so the mapping is pinned here.</p>
 */
class GameRuleNamesTest {

   @Test
   @DisplayName("normalize folds namespace, case and underscores together")
   void normalizeFoldsMechanicalRenames() {
      assertEquals("keepinventory", GameRuleNames.normalize("keepInventory"));
      assertEquals("keepinventory", GameRuleNames.normalize("keep_inventory"));
      assertEquals("keepinventory", GameRuleNames.normalize("minecraft:keep_inventory"));
      assertEquals("keepinventory", GameRuleNames.normalize("  MINECRAFT:KEEP_INVENTORY  "));
      assertEquals("mobgriefing", GameRuleNames.normalize("mobGriefing"));
      assertEquals("mobgriefing", GameRuleNames.normalize("minecraft:mob_griefing"));
   }

   @Test
   @DisplayName("normalize is total")
   void normalizeHandlesEmptyInput() {
      assertEquals("", GameRuleNames.normalize(null));
      assertEquals("", GameRuleNames.normalize("   "));
   }

   @Test
   @DisplayName("mechanically re-cased rules need no alias at all")
   void recasedRulesResolveThroughNormalizationAlone() {
      assertIterableEquals(List.of("keepinventory"), GameRuleNames.candidates("keepInventory"));
      assertIterableEquals(List.of("keepinventory"), GameRuleNames.candidates("minecraft:keep_inventory"));
      assertIterableEquals(List.of("randomtickspeed"), GameRuleNames.candidates("random_tick_speed"));
   }

   @Test
   @DisplayName("a legacy name also offers its 1.21.11 replacement")
   void legacyNamesOfferTheModernName() {
      assertIterableEquals(List.of("domobspawning", "spawnmobs"), GameRuleNames.candidates("doMobSpawning"));
      assertIterableEquals(List.of("dodaylightcycle", "advancetime"), GameRuleNames.candidates("doDaylightCycle"));
      assertIterableEquals(List.of("doinsomnia", "spawnphantoms"), GameRuleNames.candidates("doInsomnia"));
      assertIterableEquals(List.of("dotraderspawning", "spawnwanderingtraders"), GameRuleNames.candidates("doTraderSpawning"));
   }

   @Test
   @DisplayName("a modern name also offers the pre-1.21.11 name, so 1.21 servers still resolve it")
   void modernNamesOfferTheLegacyName() {
      assertIterableEquals(List.of("spawnmobs", "domobspawning"), GameRuleNames.candidates("minecraft:spawn_mobs"));
      assertIterableEquals(List.of("advanceweather", "doweathercycle"), GameRuleNames.candidates("advance_weather"));
      assertIterableEquals(List.of("respawnradius", "spawnradius"), GameRuleNames.candidates("respawn_radius"));
   }

   @Test
   @DisplayName("candidates is empty for a blank request rather than matching everything")
   void blankRequestHasNoCandidates() {
      assertTrue(GameRuleNames.candidates(null).isEmpty());
      assertTrue(GameRuleNames.candidates("").isEmpty());
   }

   @Test
   @DisplayName("rules whose sense flipped with the rename are reported as inverted")
   void renamesThatFlippedMeaningInvert() {
      assertTrue(GameRuleNames.isInverted("disableRaids", "raids"));
      assertTrue(GameRuleNames.isInverted("raids", "disableRaids"));
      assertTrue(GameRuleNames.isInverted("disableElytraMovementCheck", "elytra_movement_check"));
      assertTrue(GameRuleNames.isInverted("disablePlayerMovementCheck", "player_movement_check"));
   }

   @Test
   @DisplayName("asking for a name and getting that same name never inverts")
   void sameNameNeverInverts() {
      assertFalse(GameRuleNames.isInverted("disableRaids", "disableRaids"));
      assertFalse(GameRuleNames.isInverted("raids", "minecraft:raids"));
      assertFalse(GameRuleNames.isInverted("keepInventory", "keep_inventory"));
   }

   @Test
   @DisplayName("plain renames do not invert")
   void plainRenamesDoNotInvert() {
      assertFalse(GameRuleNames.isInverted("doMobSpawning", "spawn_mobs"));
      assertFalse(GameRuleNames.isInverted("doDaylightCycle", "advance_time"));
      assertFalse(GameRuleNames.isInverted("naturalRegeneration", "natural_health_regeneration"));
   }

   @Test
   @DisplayName("the deleted fire booleans are recognised, and have no name mapping")
   void deletedFireRulesAreRecognised() {
      assertTrue(GameRuleNames.isLegacyFireTickRule("doFireTick"));
      assertTrue(GameRuleNames.isLegacyFireTickRule("minecraft:do_fire_tick"));
      assertTrue(GameRuleNames.isLegacyFireTickRule("allowFireTicksAwayFromPlayer"));
      assertFalse(GameRuleNames.isLegacyFireTickRule("mobGriefing"));
      assertFalse(GameRuleNames.isLegacyFireTickRule("fire_spread_radius_around_player"));

      // doFireTick was replaced by an integer radius, not renamed, so no alias may claim otherwise.
      assertIterableEquals(List.of("dofiretick"), GameRuleNames.candidates("doFireTick"));
   }

   @Test
   @DisplayName("the fire spread replacement is spelled in normalized form")
   void fireSpreadConstantIsNormalized() {
      assertEquals(GameRuleNames.normalize("minecraft:fire_spread_radius_around_player"), GameRuleNames.FIRE_SPREAD_RADIUS);
   }
}
