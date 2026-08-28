package com.Util;

import java.util.Locale;
import org.bukkit.permissions.Permissible;

/**
 * Permission lookup with a legacy-node fallback.
 *
 * <p>The plugin was renamed from {@code SummerTown} to {@code ErrorTown}, which moved every
 * permission node from {@code SummerTown.*} to {@code ErrorTown.*}. Permissions live in an
 * external manager (LuckPerms and friends), so unlike the data folder or the MySQL tables
 * they cannot be migrated by this plugin — an operator who upgrades without editing their
 * permission groups would have every player silently lose access.</p>
 *
 * <p>Every permission check therefore goes through {@link #has}: the new node wins, and the
 * old one is honoured as a fallback so an existing server keeps working while its groups are
 * updated. Set {@code LegacyPermissions: false} in {@code config.yml} once the migration is
 * done to make the fallback stop applying.</p>
 */
public final class Perm {
   /** Current node prefix, matching {@code plugin.yml}. */
   public static final String PREFIX = "ErrorTown.";

   /** Node prefix used before the rename. */
   public static final String LEGACY_PREFIX = "SummerTown.";

   private Perm() {
   }

   /**
    * True when {@code target} holds {@code node}, or the pre-rename equivalent.
    *
    * @param node a node starting with {@link #PREFIX}; anything else is checked as-is
    */
   public static boolean has(Permissible target, String node) {
      if (target == null || node == null || node.isEmpty()) {
         return false;
      }
      if (target.hasPermission(node)) {
         return true;
      }
      String legacy = legacyNodeFor(node);
      return legacy != null && legacyEnabled() && target.hasPermission(legacy);
   }

   /**
    * Translates a current node to its pre-rename form.
    *
    * @return the legacy node, or null when {@code node} does not use the plugin's prefix
    */
   public static String legacyNodeFor(String node) {
      if (node == null || !node.regionMatches(true, 0, PREFIX, 0, PREFIX.length())) {
         return null;
      }
      return LEGACY_PREFIX + node.substring(PREFIX.length());
   }

   /**
    * Whether pre-rename nodes are still accepted.
    *
    * <p>Defaults to true so an upgrade never locks players out. Reading the flag defensively
    * keeps this usable before the config is loaded and in headless tests.</p>
    */
   private static boolean legacyEnabled() {
      try {
         return com.ErrorTown.Main.JavaPlugin == null
            || com.ErrorTown.Main.JavaPlugin.getConfig().getBoolean("LegacyPermissions", true);
      } catch (RuntimeException unavailable) {
         return true;
      }
   }

   /** Normalises a node for logging and comparison. */
   public static String normalize(String node) {
      return node == null ? "" : node.trim().toLowerCase(Locale.ROOT);
   }
}
