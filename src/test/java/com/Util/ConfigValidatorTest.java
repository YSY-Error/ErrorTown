package com.Util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.Util.ConfigValidator.Finding;
import com.Util.ConfigValidator.Severity;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Locks in the startup configuration rules.
 *
 * <p>The most important case is {@code MaxLevel} versus the price-list length: that
 * invariant used to be implicit, and breaking it produced an out-of-range read that the
 * historical empty {@code catch} blocks converted into a free upgrade.</p>
 */
class ConfigValidatorTest {

   /** A config that satisfies every rule, used as the baseline for the negative cases. */
   private static Map<String, Object> healthy() {
      Map<String, Object> values = new HashMap<>();
      values.put("MaxLevel", 12);
      values.put("Upgrade.EnableMoney", false);
      values.put("Upgrade.EnablePoints", false);
      values.put("Upgrade.EnableItems", true);
      values.put("MoneyNeed", elevenNumbers());
      values.put("PointsNeed", elevenNumbers());
      values.put("ItemsNeed", elevenStrings());
      values.put("ItemsChineseName", elevenStrings());
      values.put("HomeTerrain.Enabled", true);
      values.put("HomeUpgrade.LevelSizes", Arrays.asList(8, 16, 24, 32, 40, 48, 56, 64, 72, 80, 88, 96));
      values.put("HikariCP.minimumIdle", 2);
      values.put("HikariCP.maximumPoolSize", 10);
      values.put("HikariCP.connectionTimeout", 30000L);
      values.put("VIPAdd", Arrays.asList("v1up,0", "v2up,4"));
      values.put("MemberUpgrade.Enable", false);
      values.put("doMobSpawning", true);
      values.put("EnableHomeNether", false);
      values.put("HomeTravel.AllowNetherEnd", true);
      values.put("HomeNetherScale", 1);
      values.put("HomeNetherSuffix", "_nether");
      values.put("HomeCreationQueue.MaxConcurrent", 2);
      values.put("HomeCreationQueue.TimeoutSeconds", 300L);
      values.put("BungeeCord", false);
      values.put("EnableMoveListener", true);
      values.put("MaxOwnedHomes", 3);
      values.put("InviteAccess.MaxTotalHomes", 3);
      values.put("MaxOP", 2);
      values.put("MaxJoin", 10);
      return values;
   }

   private static List<Integer> elevenNumbers() {
      List<Integer> out = new ArrayList<>();
      for (int i = 0; i < 11; i++) {
         out.add(1000);
      }
      return out;
   }

   private static List<String> elevenStrings() {
      List<String> out = new ArrayList<>();
      for (int i = 0; i < 11; i++) {
         out.add("DIAMOND,16");
      }
      return out;
   }

   private static List<Finding> run(Map<String, Object> values) {
      return ConfigValidator.validate(ConfigValidator.viewOf(values));
   }

   private static boolean hasFinding(List<Finding> findings, Severity severity, String key) {
      return findings.stream().anyMatch(f -> f.getSeverity() == severity && f.getKey().equals(key));
   }

   @Test
   @DisplayName("the shipped default configuration produces no findings")
   void healthyConfigIsClean() {
      List<Finding> findings = run(healthy());
      assertTrue(findings.isEmpty(), "expected no findings, got: " + findings);
   }

   @Test
   @DisplayName("raising MaxLevel without extending the enabled price list is an ERROR")
   void maxLevelBeyondPriceList() {
      Map<String, Object> values = healthy();
      values.put("MaxLevel", 13);
      List<Finding> findings = run(values);
      assertTrue(hasFinding(findings, Severity.ERROR, "ItemsNeed"), findings.toString());
   }

   @Test
   @DisplayName("a mismatch on a disabled payment method is only informational")
   void disabledPriceListMismatchIsInfo() {
      Map<String, Object> values = healthy();
      values.put("MoneyNeed", Arrays.asList(1000, 2000));
      List<Finding> findings = run(values);
      assertTrue(hasFinding(findings, Severity.INFO, "MoneyNeed"), findings.toString());
      assertFalse(hasFinding(findings, Severity.ERROR, "MoneyNeed"), findings.toString());
   }

   @Test
   @DisplayName("disabling every upgrade method is an ERROR")
   void noUpgradePathIsAnError() {
      Map<String, Object> values = healthy();
      values.put("Upgrade.EnableItems", false);
      List<Finding> findings = run(values);
      assertTrue(hasFinding(findings, Severity.ERROR, "Upgrade"), findings.toString());
   }

   @Test
   @DisplayName("minimumIdle above maximumPoolSize is an ERROR")
   void invertedPoolSizes() {
      Map<String, Object> values = healthy();
      values.put("HikariCP.minimumIdle", 50);
      values.put("HikariCP.maximumPoolSize", 30);
      List<Finding> findings = run(values);
      assertTrue(hasFinding(findings, Severity.ERROR, "HikariCP.minimumIdle"), findings.toString());
      assertTrue(hasFinding(findings, Severity.WARN, "HikariCP.maximumPoolSize"), findings.toString());
   }

   @Test
   @DisplayName("a malformed VIPAdd entry is reported instead of silently ignored")
   void malformedVipAdd() {
      Map<String, Object> values = healthy();
      values.put("VIPAdd", Arrays.asList("v1up", "v2up,abc"));
      List<Finding> findings = run(values);
      assertEquals(2, findings.stream().filter(f -> f.getKey().equals("VIPAdd")).count(), findings.toString());
   }

   @Test
   @DisplayName("an unsorted or out-of-range level table is reported")
   void badLevelTable() {
      Map<String, Object> values = healthy();
      values.put("HomeUpgrade.LevelSizes", Arrays.asList(96, 8, 200));
      List<Finding> findings = run(values);
      assertTrue(hasFinding(findings, Severity.WARN, "HomeUpgrade.LevelSizes"), findings.toString());
   }

   @Test
   @DisplayName("a repeated level size is reported as a paid upgrade that changes nothing")
   void duplicateLevelSizesAreReported() {
      Map<String, Object> values = healthy();
      values.put("HomeUpgrade.LevelSizes", Arrays.asList(8, 16, 16, 24, 32, 40, 48, 56, 64, 72, 80, 96));
      List<Finding> findings = run(values);
      assertTrue(
         findings.stream().anyMatch(f -> f.getKey().equals("HomeUpgrade.LevelSizes") && f.getMessage().contains("repeats size")),
         "a duplicate size must be called out explicitly: " + findings
      );
   }

   @Test
   @DisplayName("dual-dimension mob spawning is flagged when doMobSpawning is off")
   void netherWithoutMobSpawning() {
      Map<String, Object> values = healthy();
      values.put("doMobSpawning", false);
      values.put("EnableHomeNether", true);
      List<Finding> findings = run(values);
      assertTrue(hasFinding(findings, Severity.WARN, "EnableHomeNether"), findings.toString());
   }

   @Test
   @DisplayName("BungeeCord plus HomeTerrain is flagged because the queue is skipped")
   void bungeeDisablesNaturalTerrain() {
      Map<String, Object> values = healthy();
      values.put("BungeeCord", true);
      List<Finding> findings = run(values);
      assertTrue(hasFinding(findings, Severity.WARN, "HomeTerrain.Enabled"), findings.toString());
   }

   @Test
   @DisplayName("a MaxConcurrent above the hard cap is reported")
   void concurrencyAboveHardCap() {
      Map<String, Object> values = healthy();
      values.put("HomeCreationQueue.MaxConcurrent", 8);
      List<Finding> findings = run(values);
      assertTrue(hasFinding(findings, Severity.WARN, "HomeCreationQueue.MaxConcurrent"), findings.toString());
   }

   @Test
   @DisplayName("border reaction options are reported as dead when the move listener is off")
   void deadMoveListenerOptions() {
      Map<String, Object> values = healthy();
      values.put("EnableMoveListener", false);
      values.put("PlayerMoveOverBorderBuff", true);
      List<Finding> findings = run(values);
      assertTrue(hasFinding(findings, Severity.INFO, "EnableMoveListener"), findings.toString());
   }

   @Test
   @DisplayName("an empty nether suffix would collide with the overworld name")
   void emptyNetherSuffix() {
      Map<String, Object> values = healthy();
      values.put("EnableHomeNether", true);
      values.put("HomeNetherSuffix", "");
      List<Finding> findings = run(values);
      assertTrue(hasFinding(findings, Severity.ERROR, "HomeNetherSuffix"), findings.toString());
   }

   @Test
   @DisplayName("a null configuration yields no findings instead of throwing")
   void nullConfigIsSafe() {
      assertTrue(ConfigValidator.validate(null).isEmpty());
   }
}
