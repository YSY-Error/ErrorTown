package com.Util;

import com.ErrorTown.Main;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.World.Environment;

public class HomeSpawnUtil {
   private static final int VANILLA_SPAWN_CHUNK_AREA = 289;

   public static void applyHomeSpawnCompensation(World world) {
      if (world != null && Main.JavaPlugin != null) {
         if (Main.JavaPlugin.getConfig().getBoolean("HomeSpawnCompensation.Enable", true)) {
            if (Main.JavaPlugin.getConfig().getInt("MaxSpawnMonstersAmount", -1) == -1) {
               if (world.getEnvironment() != Environment.NETHER || Main.JavaPlugin.getConfig().getBoolean("HomeSpawnCompensation.ApplyToNether", true)) {
                  int targetCap = Math.max(1, Main.JavaPlugin.getConfig().getInt("HomeSpawnCompensation.TargetMonsterCap", 70));
                  int minLimit = Math.max(1, Main.JavaPlugin.getConfig().getInt("HomeSpawnCompensation.MinMonsterSpawnLimit", 70));
                  int maxLimit = Math.max(minLimit, Main.JavaPlugin.getConfig().getInt("HomeSpawnCompensation.MaxMonsterSpawnLimit", 1024));
                  int borderChunks = estimateBorderChunkArea(world);
                  int compensatedLimit = (int)Math.ceil(targetCap * (double)VANILLA_SPAWN_CHUNK_AREA / Math.max(1, borderChunks));
                  compensatedLimit = Math.max(minLimit, Math.min(maxLimit, compensatedLimit));
                  world.setMonsterSpawnLimit(compensatedLimit);
               }
            }
         }
      }
   }

   private static int estimateBorderChunkArea(World world) {
      try {
         WorldBorder border = world.getWorldBorder();
         int chunksWide = Math.max(1, (int)Math.ceil(border.getSize() / 16.0));
         return chunksWide * chunksWide;
      } catch (Throwable failure) {
         return VANILLA_SPAWN_CHUNK_AREA;
      }
   }
}
