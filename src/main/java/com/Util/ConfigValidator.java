package com.Util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Startup consistency check for {@code config.yml}.
 *
 * <p><b>Why this exists.</b> {@code config.yml} carries roughly 150 keys with several
 * groups whose meanings overlap, plus invariants that are only implicit. The most
 * expensive example: {@code MaxLevel} must equal the length of every enabled price list
 * plus one. Nothing enforced that, so raising {@code MaxLevel} without appending a price
 * silently produced an out-of-range read which the historical empty {@code catch} blocks
 * converted into a free upgrade.</p>
 *
 * <p>This class is deliberately free of Bukkit types and works against {@link ConfigView}
 * so the rules are executable in a unit test. {@code Main} supplies an adapter over the
 * live {@code FileConfiguration} and logs whatever comes back.</p>
 */
public final class ConfigValidator {

   /** Minimal read-only view of a configuration, so the rules can be tested headlessly. */
   public interface ConfigView {
      boolean contains(String key);

      int getInt(String key, int fallback);

      long getLong(String key, long fallback);

      boolean getBoolean(String key, boolean fallback);

      String getString(String key, String fallback);

      List<Integer> getIntegerList(String key);

      List<String> getStringList(String key);
   }

   public enum Severity {
      /** The server will behave incorrectly or a feature cannot work at all. */
      ERROR,
      /** Very likely a misconfiguration; the plugin compensates but the intent is lost. */
      WARN,
      /** A setting that currently has no effect. */
      INFO
   }

   public static final class Finding {
      private final Severity severity;
      private final String key;
      private final String message;

      public Finding(Severity severity, String key, String message) {
         this.severity = severity;
         this.key = key;
         this.message = message;
      }

      public Severity getSeverity() {
         return severity;
      }

      public String getKey() {
         return key;
      }

      public String getMessage() {
         return message;
      }

      @Override
      public String toString() {
         return severity + " [" + key + "] " + message;
      }
   }

   private ConfigValidator() {
   }

   /** Runs every rule. Returns findings in declaration order; empty means healthy. */
   public static List<Finding> validate(ConfigView config) {
      List<Finding> findings = new ArrayList<>();
      if (config == null) {
         return findings;
      }

      checkUpgradeTables(config, findings);
      checkLevelSizes(config, findings);
      checkConnectionPool(config, findings);
      checkVipAdd(config, findings);
      checkMemberUpgrade(config, findings);
      checkMobSpawning(config, findings);
      checkDimensionSwitches(config, findings);
      checkCreationQueue(config, findings);
      checkMoveListener(config, findings);
      checkHomeLimits(config, findings);
      checkNetherSuffix(config, findings);

      return findings;
   }

   // ---------------------------------------------------------------- rules

   /**
    * {@code MaxLevel} counts levels; the price lists count <em>upgrades</em>. Twelve
    * levels therefore need eleven prices.
    */
   private static void checkUpgradeTables(ConfigView config, List<Finding> findings) {
      int maxLevel = config.getInt("MaxLevel", 12);
      int required = Math.max(0, maxLevel - 1);

      boolean money = config.getBoolean("Upgrade.EnableMoney", false);
      boolean points = config.getBoolean("Upgrade.EnablePoints", false);
      boolean items = config.getBoolean("Upgrade.EnableItems", false);

      if (!money && !points && !items) {
         findings.add(new Finding(Severity.ERROR, "Upgrade", "All of EnableMoney/EnablePoints/EnableItems are false: no upgrade path is reachable."));
      }

      checkPriceListLength(config, findings, "MoneyNeed", required, money);
      checkPriceListLength(config, findings, "PointsNeed", required, points);
      checkPriceListLength(config, findings, "ItemsNeed", required, items);

      int itemsNeed = config.getStringList("ItemsNeed").size();
      int itemNames = config.getStringList("ItemsChineseName").size();
      if (items && itemsNeed != itemNames) {
         findings.add(
            new Finding(
               Severity.WARN,
               "ItemsChineseName",
               "has " + itemNames + " entries but ItemsNeed has " + itemsNeed + "; placeholder text will be wrong for the surplus levels."
            )
         );
      }
   }

   private static void checkPriceListLength(ConfigView config, List<Finding> findings, String key, int required, boolean enabled) {
      int size = "PointsNeed".equals(key) || "MoneyNeed".equals(key) ? numericListSize(config, key) : config.getStringList(key).size();
      if (size == required) {
         return;
      }
      Severity severity = enabled ? Severity.ERROR : Severity.INFO;
      String tail = enabled
         ? "Upgrades past level " + (size + 1) + " will be shown as unavailable."
         : "This payment method is disabled, so the mismatch is currently harmless.";
      findings.add(new Finding(severity, key, "has " + size + " entries but MaxLevel=" + (required + 1) + " needs " + required + ". " + tail));
   }

   private static int numericListSize(ConfigView config, String key) {
      List<Integer> numbers = config.getIntegerList(key);
      if (numbers != null && !numbers.isEmpty()) {
         return numbers.size();
      }
      return config.getStringList(key).size();
   }

   /** The level table must be ascending and inside the global 8..96 block range. */
   private static void checkLevelSizes(ConfigView config, List<Finding> findings) {
      if (!config.getBoolean("HomeTerrain.Enabled", false)) {
         return;
      }
      List<Integer> sizes = config.getIntegerList("HomeUpgrade.LevelSizes");
      if (sizes == null || sizes.isEmpty()) {
         findings.add(new Finding(Severity.WARN, "HomeUpgrade.LevelSizes", "is empty while HomeTerrain.Enabled is true; the built-in 8..96 table is used."));
         return;
      }

      int previous = Integer.MIN_VALUE;
      boolean ascending = true;
      List<Integer> outOfRange = new ArrayList<>();
      List<Integer> duplicates = new ArrayList<>();
      java.util.Set<Integer> seen = new java.util.HashSet<>();
      for (Integer size : sizes) {
         if (size == null) {
            continue;
         }
         if (size < HomeTerrainPolicy.MINIMUM_SIZE || size > HomeTerrainPolicy.MAXIMUM_SIZE) {
            outOfRange.add(size);
         } else if (!seen.add(size) && !duplicates.contains(size)) {
            duplicates.add(size);
         }
         if (size <= previous) {
            ascending = false;
         }
         previous = size;
      }

      if (!ascending) {
         findings.add(
            new Finding(Severity.WARN, "HomeUpgrade.LevelSizes", "is not strictly ascending; it is sorted at runtime so displayed level numbers may shift.")
         );
      }
      if (!duplicates.isEmpty()) {
         findings.add(
            new Finding(
               Severity.WARN,
               "HomeUpgrade.LevelSizes",
               "repeats size(s) " + duplicates + ". A repeated size would be an upgrade that costs money and changes nothing, "
                  + "so duplicates are collapsed at runtime — which shifts every later level number."
            )
         );
      }
      if (!outOfRange.isEmpty()) {
         findings.add(
            new Finding(
               Severity.WARN,
               "HomeUpgrade.LevelSizes",
               "contains value(s) outside " + HomeTerrainPolicy.MINIMUM_SIZE + ".." + HomeTerrainPolicy.MAXIMUM_SIZE + ": " + outOfRange
                  + ". They are dropped, which shifts every later level."
            )
         );
      }

      int usable = HomeTerrainPolicy.levelCount(toArray(sizes));
      int maxLevel = config.getInt("MaxLevel", 12);
      if (usable < maxLevel) {
         findings.add(
            new Finding(
               Severity.WARN,
               "HomeUpgrade.LevelSizes",
               "yields only " + usable + " usable level(s) but MaxLevel=" + maxLevel + "; levels above " + usable + " will not grow the border."
            )
         );
      }
   }

   /** HikariCP rejects {@code minimumIdle > maximumPoolSize}. */
   private static void checkConnectionPool(ConfigView config, List<Finding> findings) {
      int minIdle = config.getInt("HikariCP.minimumIdle", 10);
      int maxPool = config.getInt("HikariCP.maximumPoolSize", 10);

      if (maxPool < 1) {
         findings.add(new Finding(Severity.ERROR, "HikariCP.maximumPoolSize", "must be at least 1, got " + maxPool + "."));
      }
      if (minIdle > maxPool) {
         findings.add(
            new Finding(
               Severity.ERROR,
               "HikariCP.minimumIdle",
               "is " + minIdle + " but maximumPoolSize is " + maxPool + ". HikariCP forces them equal, so " + minIdle
                  + " idle connections are held open. Set minimumIdle well below maximumPoolSize."
            )
         );
      }
      if (maxPool > 20) {
         findings.add(
            new Finding(Severity.WARN, "HikariCP.maximumPoolSize", "is " + maxPool + "; a Minecraft plugin rarely benefits above ~10 and larger pools add contention.")
         );
      }
      long timeout = config.getLong("HikariCP.connectionTimeout", 30000L);
      if (timeout < 250L) {
         findings.add(new Finding(Severity.WARN, "HikariCP.connectionTimeout", "is " + timeout + " ms; HikariCP enforces a 250 ms minimum."));
      }
   }

   /** Each {@code VIPAdd} entry must be {@code permission,radius}. */
   private static void checkVipAdd(ConfigView config, List<Finding> findings) {
      for (String entry : config.getStringList("VIPAdd")) {
         if (entry == null) {
            continue;
         }
         String[] parts = entry.split(",");
         if (parts.length < 2) {
            findings.add(new Finding(Severity.ERROR, "VIPAdd", "entry '" + entry + "' is not 'permission,radius'; it is ignored."));
            continue;
         }
         try {
            int extra = Integer.parseInt(parts[1].trim());
            if (extra < 0) {
               findings.add(new Finding(Severity.WARN, "VIPAdd", "entry '" + entry + "' has a negative radius; it is treated as 0."));
            }
         } catch (NumberFormatException invalid) {
            findings.add(new Finding(Severity.ERROR, "VIPAdd", "entry '" + entry + "' has a non-numeric radius; it is ignored."));
         }
      }
   }

   /** Each {@code MemberUpgrade.Plans} entry must be {@code money,points,members}. */
   private static void checkMemberUpgrade(ConfigView config, List<Finding> findings) {
      if (!config.getBoolean("MemberUpgrade.Enable", false)) {
         return;
      }
      List<String> plans = config.getStringList("MemberUpgrade.Plans");
      if (plans.isEmpty()) {
         findings.add(new Finding(Severity.WARN, "MemberUpgrade.Plans", "is empty while MemberUpgrade.Enable is true; the feature does nothing."));
         return;
      }
      int total = 0;
      for (String plan : plans) {
         if (plan == null) {
            continue;
         }
         String[] parts = plan.split(",");
         if (parts.length < 3) {
            findings.add(new Finding(Severity.ERROR, "MemberUpgrade.Plans", "entry '" + plan + "' is not 'money,points,members'."));
            continue;
         }
         try {
            total += Integer.parseInt(parts[2].trim());
         } catch (NumberFormatException invalid) {
            findings.add(new Finding(Severity.ERROR, "MemberUpgrade.Plans", "entry '" + plan + "' has a non-numeric member count."));
         }
      }

      int base = config.getInt("MaxJoin", 10);
      int absolute = config.getInt("MemberUpgrade.AbsoluteMax", 50);
      if (base + total > absolute) {
         findings.add(
            new Finding(
               Severity.INFO,
               "MemberUpgrade.AbsoluteMax",
               "is " + absolute + " while MaxJoin(" + base + ") plus every plan(" + total + ") reaches " + (base + total)
                  + "; the last plans cannot be fully used."
            )
         );
      }
   }

   /** Mob spawning is driven by one switch; the dependent features must agree with it. */
   private static void checkMobSpawning(ConfigView config, List<Finding> findings) {
      boolean spawning = config.getBoolean("doMobSpawning", true);
      if (spawning) {
         return;
      }
      if (config.getBoolean("HomeSpawnCompensation.Enable", true)) {
         findings.add(
            new Finding(
               Severity.INFO,
               "HomeSpawnCompensation.Enable",
               "is true but doMobSpawning is false, so no mobs spawn and the compensation has nothing to raise."
            )
         );
      }
      if (config.getInt("HomeRulesDefaults.MaxMobCount", 0) > 0) {
         findings.add(
            new Finding(Severity.INFO, "HomeRulesDefaults.MaxMobCount", "is set but doMobSpawning is false, so the per-home mob rules have no effect.")
         );
      }
      if (config.getBoolean("EnableHomeNether", false)) {
         findings.add(
            new Finding(
               Severity.WARN,
               "EnableHomeNether",
               "is true but doMobSpawning is false; the documented dual-dimension mob spawning will not happen in the overworld half."
            )
         );
      }
   }

   /** The nether / end switches overlap and must not contradict each other. */
   private static void checkDimensionSwitches(ConfigView config, List<Finding> findings) {
      if (config.getBoolean("EnableHomeNether", false) && !config.getBoolean("HomeTravel.AllowNetherEnd", false)) {
         findings.add(
            new Finding(
               Severity.WARN,
               "HomeTravel.AllowNetherEnd",
               "is false while EnableHomeNether is true; dimension travel is blocked, so the per-home nether cannot be entered."
            )
         );
      }
      if (config.getBoolean("EnableHomeNether", false) && config.getBoolean("DisablePortalCreate", true)) {
         findings.add(
            new Finding(
               Severity.INFO,
               "DisablePortalCreate",
               "is true, but EnableHomeNether overrides it inside homes so nether portals can still be built there."
            )
         );
      }
      int scale = config.getInt("HomeNetherScale", 1);
      if (scale != 1 && scale != 8) {
         findings.add(new Finding(Severity.WARN, "HomeNetherScale", "is " + scale + "; only 1 (1:1) and 8 (vanilla) are documented."));
      }
   }

   /** Concurrency is hard-capped in code; tell the operator instead of silently clamping. */
   private static void checkCreationQueue(ConfigView config, List<Finding> findings) {
      int configured = config.getInt("HomeCreationQueue.MaxConcurrent", 2);
      int effective = HomeTerrainPolicy.normalizeCreationLimit(configured);
      if (configured != effective) {
         findings.add(
            new Finding(
               Severity.WARN,
               "HomeCreationQueue.MaxConcurrent",
               "is " + configured + " but the hard cap is " + HomeTerrainPolicy.DEFAULT_MAX_CONCURRENT_CREATIONS + "; " + effective + " is used."
            )
         );
      }
      if (config.getBoolean("BungeeCord", false) && config.getBoolean("HomeTerrain.Enabled", false)) {
         findings.add(
            new Finding(
               Severity.WARN,
               "HomeTerrain.Enabled",
               "is true but BungeeCord mode is on. The bounded natural-terrain path and its creation queue are skipped in cross-server mode; "
                  + "borders fall back to WorldBoard/UpdateRadius."
            )
         );
      }
      long timeout = config.getLong("HomeCreationQueue.TimeoutSeconds", 300L);
      if (timeout < 30L) {
         findings.add(new Finding(Severity.WARN, "HomeCreationQueue.TimeoutSeconds", "is " + timeout + "s; values below 30s are raised to 30s."));
      }
   }

   /** The three border-reaction options only apply when the move listener is enabled. */
   private static void checkMoveListener(ConfigView config, List<Finding> findings) {
      if (config.getBoolean("EnableMoveListener", false)) {
         return;
      }
      List<String> dead = new ArrayList<>();
      if (config.getBoolean("PlayerMoveOverBorderHit", false)) {
         dead.add("PlayerMoveOverBorderHit");
      }
      if (config.getBoolean("PlayerMoveOverBorderBuff", false)) {
         dead.add("PlayerMoveOverBorderBuff");
      }
      if (config.getBoolean("EnableAdventureMode", false)) {
         dead.add("EnableAdventureMode");
      }
      if (!dead.isEmpty()) {
         findings.add(new Finding(Severity.INFO, "EnableMoveListener", "is false, so these have no effect: " + String.join(", ", dead) + "."));
      }
   }

   private static void checkHomeLimits(ConfigView config, List<Finding> findings) {
      int owned = config.getInt("MaxOwnedHomes", 3);
      int participation = config.getInt("InviteAccess.MaxTotalHomes", 3);
      if (owned > participation) {
         findings.add(
            new Finding(
               Severity.WARN,
               "InviteAccess.MaxTotalHomes",
               "is " + participation + " but MaxOwnedHomes is " + owned + "; an owner cannot hold all their own homes plus any invitation."
            )
         );
      }
      if (config.getInt("MaxOP", 2) > config.getInt("MaxJoin", 10)) {
         findings.add(new Finding(Severity.WARN, "MaxOP", "exceeds MaxJoin; not every manager slot can be filled."));
      }
   }

   private static void checkNetherSuffix(ConfigView config, List<Finding> findings) {
      if (!config.getBoolean("EnableHomeNether", false)) {
         return;
      }
      String suffix = config.getString("HomeNetherSuffix", "_nether");
      if (suffix == null || suffix.trim().isEmpty()) {
         findings.add(new Finding(Severity.ERROR, "HomeNetherSuffix", "is empty; the per-home nether world would collide with the overworld name."));
      }
   }

   private static int[] toArray(List<Integer> values) {
      if (values == null || values.isEmpty()) {
         return new int[0];
      }
      int[] out = new int[values.size()];
      for (int i = 0; i < values.size(); i++) {
         Integer value = values.get(i);
         out[i] = value == null ? 0 : value;
      }
      return out;
   }

   /** Convenience view over a plain map, used by the tests. */
   public static ConfigView viewOf(java.util.Map<String, Object> values) {
      java.util.Map<String, Object> source = values == null ? Collections.emptyMap() : values;
      return new ConfigView() {
         @Override
         public boolean contains(String key) {
            return source.containsKey(key);
         }

         @Override
         public int getInt(String key, int fallback) {
            Object value = source.get(key);
            return value instanceof Number number ? number.intValue() : fallback;
         }

         @Override
         public long getLong(String key, long fallback) {
            Object value = source.get(key);
            return value instanceof Number number ? number.longValue() : fallback;
         }

         @Override
         public boolean getBoolean(String key, boolean fallback) {
            Object value = source.get(key);
            return value instanceof Boolean flag ? flag : fallback;
         }

         @Override
         public String getString(String key, String fallback) {
            Object value = source.get(key);
            return value instanceof String text ? text : fallback;
         }

         @Override
         @SuppressWarnings("unchecked")
         public List<Integer> getIntegerList(String key) {
            Object value = source.get(key);
            return value instanceof List<?> list ? (List<Integer>)list : Collections.emptyList();
         }

         @Override
         @SuppressWarnings("unchecked")
         public List<String> getStringList(String key) {
            Object value = source.get(key);
            return value instanceof List<?> list ? (List<String>)list : Collections.emptyList();
         }
      };
   }
}
