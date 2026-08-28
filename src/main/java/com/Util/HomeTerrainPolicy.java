package com.Util;

import java.util.Arrays;
import java.util.List;

public final class HomeTerrainPolicy {
   public static final int MINIMUM_SIZE = 8;
   public static final int MAXIMUM_SIZE = 96;
   public static final int DEFAULT_MAX_CONCURRENT_CREATIONS = 2;
   private static final int[] DEFAULT_LEVEL_SIZES = new int[]{8, 16, 24, 32, 40, 48, 56, 64, 72, 80, 88, 96};

   private HomeTerrainPolicy() {
   }

   public static int sizeForLevel(int level) {
      return sizeForLevel(level, DEFAULT_LEVEL_SIZES);
   }

   public static int sizeForLevel(int level, int[] configuredSizes) {
      int[] sizes = validConfiguredSizes(configuredSizes);
      int index = Math.max(0, Math.min(level - 1, sizes.length - 1));
      return clampSize(sizes[index]);
   }

   /** Convenience overload for Bukkit's YAML-backed integer list. */
   public static int sizeForLevel(int level, List<Integer> configuredSizes) {
      return sizeForLevel(level, toArray(configuredSizes));
   }

   /**
    * Returns the number of usable upgrade levels in a configuration.
    * Values outside the supported 8..96 block range are ignored instead of
    * silently becoming a different size through clamping.
    */
   public static int levelCount(int[] configuredSizes) {
      return validConfiguredSizes(configuredSizes).length;
   }

   /**
    * Returns the size after an upgrade. Once the final configured level is
    * reached, the current size is returned so callers cannot grow past the
    * configured table or the global 96-block limit.
    */
   public static int sizeForNextLevel(int currentLevel, int[] configuredSizes) {
      int nextLevel = Math.max(1, currentLevel) + 1;
      return sizeForLevel(nextLevel, configuredSizes);
   }

   /**
    * Converts the legacy WorldBoard/UpdateRadius settings to one bounded
    * square size. Keeping this calculation here prevents protection,
    * displays, and world-border callers from drifting apart.
    */
   public static int borderSizeForLevel(int level, int worldBoard, int updateRadius) {
      int initialSize = isWithinMaximumSize(worldBoard) ? worldBoard : MINIMUM_SIZE;
      int growth = Math.max(0, updateRadius);
      int normalizedLevel = Math.max(1, level);
      long requested = (long)initialSize + (long)(normalizedLevel - 1) * growth;
      return clampSize(requested > Integer.MAX_VALUE ? MAXIMUM_SIZE : (int)requested);
   }

   /**
    * Chooses the natural level table when enabled, otherwise preserves the
    * legacy linear border formula. Permission additions are applied last and
    * remain bounded by the global maximum.
    */
   public static int configuredBorderSize(
      int level,
      boolean naturalTerrainEnabled,
      List<Integer> configuredSizes,
      int worldBoard,
      int updateRadius,
      int permissionExtra
   ) {
      int base = naturalTerrainEnabled
         ? sizeForLevel(level, configuredSizes)
         : borderSizeForLevel(level, worldBoard, updateRadius);
      // VIPAdd is documented as an extra radius, so it expands both sides
      // of the square and contributes twice to the full border diameter.
      long requested = (long)base + (long)Math.max(0, permissionExtra) * 2L;
      return clampSize(requested > Integer.MAX_VALUE ? MAXIMUM_SIZE : (int)requested);
   }

   /**
    * Calculates the full border after one upgrade. Natural homes follow the
    * configured level table; legacy homes retain the historical increment
    * from the currently applied border size.
    */
   public static int upgradedBorderSize(
      int currentSize,
      int currentLevel,
      int nextLevel,
      boolean naturalTerrainEnabled,
      List<Integer> configuredSizes,
      int worldBoard,
      int updateRadius
   ) {
      int safeNextLevel = Math.max(Math.max(1, currentLevel + 1), nextLevel);
      long requested;
      if (naturalTerrainEnabled) {
         int currentBaseSize = sizeForLevel(currentLevel, configuredSizes);
         int preservedExtraDiameter = currentSize >= MINIMUM_SIZE && currentSize <= MAXIMUM_SIZE
            ? Math.max(0, currentSize - currentBaseSize)
            : 0;
         requested = (long)sizeForLevel(safeNextLevel, configuredSizes) + preservedExtraDiameter;
      } else {
         requested = (long)Math.max(MINIMUM_SIZE, currentSize) + Math.max(0, updateRadius);
      }
      return clampSize(requested > Integer.MAX_VALUE ? MAXIMUM_SIZE : (int)requested);
   }

   /**
    * Highest reachable level for a maximum size, honouring a configured level table.
    *
    * <p>The no-argument form still exists for callers that have no configuration at
    * hand, but it reported the default table's level count even when the operator had
    * configured a different {@code HomeUpgrade.LevelSizes}.</p>
    */
   public static int maxLevelForSize(int maximumSize) {
      return maxLevelForSize(maximumSize, DEFAULT_LEVEL_SIZES);
   }

   public static int maxLevelForSize(int maximumSize, int[] configuredSizes) {
      int maximum = Math.max(0, Math.min(maximumSize, MAXIMUM_SIZE));
      if (maximum < MINIMUM_SIZE) {
         return 1;
      }
      int[] sizes = validConfiguredSizes(configuredSizes);
      int level = 0;
      for (int size : sizes) {
         if (size > maximum) {
            break;
         }
         level++;
      }
      return Math.max(1, level);
   }

   public static int maxLevelForSize(int maximumSize, List<Integer> configuredSizes) {
      return maxLevelForSize(maximumSize, toArray(configuredSizes));
   }

   public static int activeCreationSlots(int queuedHomes, int configuredLimit) {
      int limit = normalizeCreationLimit(configuredLimit);
      return Math.max(0, Math.min(queuedHomes, limit));
   }

   /** Returns a safe queue limit while preserving the hard two-task cap. */
   public static int normalizeCreationLimit(int configuredLimit) {
      return Math.max(1, Math.min(configuredLimit, DEFAULT_MAX_CONCURRENT_CREATIONS));
   }

   public static int chunkCountForSquare(int sizeBlocks) {
      int size = Math.max(0, Math.min(sizeBlocks, MAXIMUM_SIZE));
      int chunksWide = (size + 15) / 16;
      return chunksWide * chunksWide;
   }

   public static boolean isWithinMaximumSize(int sizeBlocks) {
      return sizeBlocks >= MINIMUM_SIZE && sizeBlocks <= MAXIMUM_SIZE;
   }

   public static long estimatedBytesForHomes(int homes, int chunksPerHome, long bytesPerChunk) {
      if (homes <= 0 || chunksPerHome <= 0 || bytesPerChunk <= 0L) {
         return 0L;
      }
      return Math.multiplyExact(Math.multiplyExact((long)homes, (long)chunksPerHome), bytesPerChunk);
   }

   public static int clampSize(int requestedSize) {
      return Math.max(MINIMUM_SIZE, Math.min(requestedSize, MAXIMUM_SIZE));
   }

   /**
    * Filters a configured table down to usable sizes.
    *
    * <p>Out-of-range values are dropped rather than clamped, so a typo cannot silently
    * become a different size. The result is also sorted ascending: an unsorted table
    * such as {@code [96, 8, 40]} would otherwise make an "upgrade" shrink the home,
    * and {@link #sizeForNextLevel} would return a smaller size than the current one.</p>
    */
   private static int[] validConfiguredSizes(int[] configuredSizes) {
      if (configuredSizes == null || configuredSizes.length == 0) {
         return DEFAULT_LEVEL_SIZES;
      }

      int validCount = 0;
      for (int size : configuredSizes) {
         if (isWithinMaximumSize(size)) {
            validCount++;
         }
      }
      if (validCount == 0) {
         return DEFAULT_LEVEL_SIZES;
      }

      int[] validSizes = new int[validCount];
      int index = 0;
      for (int size : configuredSizes) {
         if (isWithinMaximumSize(size)) {
            validSizes[index++] = size;
         }
      }
      Arrays.sort(validSizes);
      int uniqueCount = 1;
      for (int i = 1; i < validSizes.length; i++) {
         if (validSizes[i] != validSizes[uniqueCount - 1]) {
            validSizes[uniqueCount++] = validSizes[i];
         }
      }
      return Arrays.copyOf(validSizes, uniqueCount);
   }

   private static int[] toArray(List<Integer> configuredSizes) {
      if (configuredSizes == null || configuredSizes.isEmpty()) {
         return null;
      }
      int[] values = new int[configuredSizes.size()];
      for (int i = 0; i < configuredSizes.size(); i++) {
         Integer value = configuredSizes.get(i);
         values[i] = value == null ? 0 : value;
      }
      return values;
   }
}
