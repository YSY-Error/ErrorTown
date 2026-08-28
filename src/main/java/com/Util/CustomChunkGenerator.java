package com.Util;

import com.ErrorTown.Main;
import com.ErrorTown.Variable;
import java.io.File;
import java.util.Random;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.ChunkGenerator.BiomeGrid;
import org.bukkit.generator.ChunkGenerator.ChunkData;

public class CustomChunkGenerator extends ChunkGenerator {
   public ChunkData generateChunkData(World world, Random random, int chunkX, int chunkZ, BiomeGrid biome) {
      ChunkData data = this.createChunkData(world);
      if (isSkyIslandEnabled() && isSkyIslandWorld(world)) {
         int radius = Math.max(1, Main.JavaPlugin.getConfig().getInt("SkyIsland.PlatformRadius", 3));
         int spawnY = Main.JavaPlugin.getConfig().getInt("SkyIsland.SpawnY", 65);
         Material top = this.matchMaterial(Main.JavaPlugin.getConfig().getString("SkyIsland.PlatformTop", "GRASS_BLOCK"), "GRASS_BLOCK", "GRASS");
         Material middle = this.matchMaterial(Main.JavaPlugin.getConfig().getString("SkyIsland.PlatformMiddle", "DIRT"), "DIRT");
         Material bottom = this.matchMaterial(Main.JavaPlugin.getConfig().getString("SkyIsland.PlatformBottom", "BEDROCK"), "BEDROCK", "STONE");
         if (top != null && middle != null && bottom != null) {
            for (int worldX = -radius; worldX <= radius; worldX++) {
               for (int worldZ = -radius; worldZ <= radius; worldZ++) {
                  if (worldX >> 4 == chunkX && worldZ >> 4 == chunkZ) {
                     int localX = worldX & 15;
                     int localZ = worldZ & 15;
                     data.setBlock(localX, spawnY - 2, localZ, middle);
                     data.setBlock(localX, spawnY - 1, localZ, middle);
                     data.setBlock(localX, spawnY, localZ, top);
                  }
               }
            }

            if (0 == chunkX && 0 == chunkZ) {
               data.setBlock(0, spawnY - 3, 0, bottom);
            }

            return data;
         } else {
            return data;
         }
      } else {
         return data;
      }
   }

   public static boolean isSkyIslandEnabled() {
      return Main.JavaPlugin != null && Main.JavaPlugin.getConfig().getBoolean("SkyIsland.Enable", true);
   }

   public static boolean isSkyIslandType(String createType) {
      String key = Main.JavaPlugin.getConfig().getString("SkyIsland.CreateKey", "airland");
      if (key == null || key.trim().isEmpty()) {
         key = "airland";
      }

      return createType != null && createType.equalsIgnoreCase(key);
   }

   public static boolean isSkyIslandWorld(World world) {
      return world == null ? false : isSkyIslandWorldName(world.getName());
   }

   public static boolean isSkyIslandWorldName(String worldName) {
      if (isSkyIslandEnabled() && worldName != null) {
         String baseName = Util.getBaseHomeName(worldName);
         File homeFile = new File(Variable.Tempf, baseName + ".yml");
         if (!homeFile.exists()) {
            return false;
         } else {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(homeFile);
            return isSkyIslandType(yaml.getString("CreateMode", ""));
         }
      } else {
         return false;
      }
   }

   private Material matchMaterial(String preferred, String... fallbacks) {
      Material material = Material.matchMaterial(preferred);
      if (material != null) {
         return material;
      } else if (fallbacks == null) {
         return null;
      } else {
         for (String fallback : fallbacks) {
            material = Material.matchMaterial(fallback);
            if (material != null) {
               return material;
            }
         }

         return null;
      }
   }
}
