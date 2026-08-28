package com.Util;

import org.bukkit.WorldCreator;
import org.bukkit.WorldType;

/**
 * Builds the only WorldCreator shape allowed for bounded natural homes.
 *
 * <p>The factory returns a configured creator instead of calling Bukkit
 * directly. That keeps world registration and error handling in the command
 * workflow while making the safety-critical generator settings independently
 * testable.</p>
 */
public final class NaturalHomeWorldFactory {
   private NaturalHomeWorldFactory() {
   }

   public static WorldCreator create(String worldName, String requestedSeed, long configuredSeed) {
      if (worldName == null || worldName.trim().isEmpty()) {
         throw new IllegalArgumentException("Natural home world name cannot be blank");
      }

      WorldCreator creator = new WorldCreator(worldName);
      creator.type(WorldType.NORMAL);
      creator.generateStructures(false);
      // Spigot spells this keepSpawnInMemory(boolean), Paper 26.2 keepSpawnLoaded(TriState),
      // and neither server has the other's method. See com.Util.Platform.
      Platform.keepSpawnLoaded(creator, false);
      applySeed(creator, requestedSeed, configuredSeed);
      return creator;
   }

   private static void applySeed(WorldCreator creator, String requestedSeed, long configuredSeed) {
      if (requestedSeed != null && !requestedSeed.trim().isEmpty()) {
         String seed = requestedSeed.trim();
         try {
            creator.seed(Long.parseLong(seed));
         } catch (NumberFormatException ignored) {
            // Preserve ErrorTown's established deterministic text-seed behavior.
            creator.seed(seed.hashCode());
         }
      } else if (configuredSeed != 0L) {
         creator.seed(configuredSeed);
      }
   }
}
