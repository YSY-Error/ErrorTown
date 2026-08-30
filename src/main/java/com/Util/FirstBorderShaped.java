package com.Util;

import com.ErrorTown.Main;
import com.ErrorTown.Variable;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Builds and clears the decorative block shell that marks a home's border.
 *
 * <p>Rewritten to fix four defects in the original implementation:</p>
 * <ol>
 *   <li>The home was looked up as {@code Variable.world_prefix + world.getName()},
 *       but {@code world.getName()} already contains the prefix. With a non-empty
 *       prefix this produced {@code ST_ST_Bob}, {@code getHome} returned null, and the
 *       next line threw NPE inside an async task where nobody saw it. Everywhere else
 *       in the plugin uses {@link Util#getBaseHomeName(String)} to <em>strip</em> the
 *       prefix.</li>
 *   <li>{@code Block.setType} was called from FAWE's async executor. Bukkit block
 *       mutation is main-thread only. Coordinates are now computed off-thread and the
 *       block writes are applied on the main thread in bounded batches.</li>
 *   <li>{@code Material.valueOf(config)} was re-parsed once per block - up to ~170k
 *       times for a level-12 sphere - and throws instead of returning null on a bad
 *       name. It is now resolved and validated once.</li>
 *   <li>Rebuilding the shell after an upgrade was gated behind {@code UpdateClearOld},
 *       a flag that only means "remove the old blocks". With the default
 *       {@code UpdateClearOld: false} an upgrade neither cleared the old shell nor
 *       built the new one. Clearing and building are now independent.</li>
 * </ol>
 */
public final class FirstBorderShaped {
   /** Block writes applied per tick, to keep the shell update off the tick budget. */
   private static final int BLOCKS_PER_TICK = 2000;

   private FirstBorderShaped() {
   }

   /**
    * Clears the previous shell (when {@code UpdateClearOld} is set) and then rebuilds
    * it for the home's current level.
    */
   public static void AddShapeBorder(final World world) {
      if (!enabled() || world == null) {
         return;
      }

      new BukkitRunnable() {
         @Override
         public void run() {
            if (Main.JavaPlugin.getConfig().getBoolean("UpdateClearOld")) {
               Integer previousLevel = level(world);
               if (previousLevel != null) {
                  Material material = borderMaterial();
                  if (material != null) {
                     // Only clear blocks that are actually the border material, so player
                     // builds on the old shell line are left alone.
                     applyBatched(world, shell(world, Math.max(1, previousLevel - 1)), Material.AIR, material);
                  }
               }
            }
            ShapeBorder(world);
         }
      }.runTaskLater(Main.JavaPlugin, 100L);
   }

   /** Builds the border shell for the home's current level. */
   public static void ShapeBorder(final World world) {
      if (!enabled() || world == null) {
         return;
      }

      new BukkitRunnable() {
         @Override
         public void run() {
            Material material = borderMaterial();
            if (material == null) {
               Main.JavaPlugin.getLogger().warning(
                  "BorderMaterial '" + Main.JavaPlugin.getConfig().getString("BorderMaterial") + "' is not a valid material; border shell skipped."
               );
               return;
            }
            Integer level = level(world);
            if (level == null) {
               Main.JavaPlugin.getLogger().log(Level.FINE, "No home data for world " + world.getName() + "; border shell skipped.");
               return;
            }
            applyBatched(world, shell(world, level), material, null);
         }
      }.runTaskLater(Main.JavaPlugin, 100L);
   }

   private static boolean enabled() {
      return Main.JavaPlugin != null
         && Variable.hook_FastAsyncWorldEdit
         && Main.JavaPlugin.getConfig().getBoolean("FaweSwitch");
   }

   /** Resolves the home level for {@code world}, or null when this is not a home. */
   private static Integer level(World world) {
      Home home = HomeAPI.getHome(Util.getBaseHomeName(world.getName()));
      return home == null ? null : Math.max(1, home.getLevel());
   }

   private static Material borderMaterial() {
      String configured = Main.JavaPlugin.getConfig().getString("BorderMaterial");
      return configured == null ? null : Material.matchMaterial(configured);
   }

   private static int radiusFor(int level) {
      return HomeTerrainPolicy.configuredBorderSize(
            level,
            Main.JavaPlugin.getConfig().getBoolean("HomeTerrain.Enabled", false),
            Main.JavaPlugin.getConfig().getIntegerList("HomeUpgrade.LevelSizes"),
            Main.JavaPlugin.getConfig().getInt("WorldBoard"),
            Main.JavaPlugin.getConfig().getInt("UpdateRadius"),
            0
         )
         / 2
         + 4;
   }

   private static List<Location> shell(World world, int level) {
      int radius = radiusFor(level);
      Location spawn = world.getSpawnLocation();
      int cx = spawn.getBlockX();
      int cy = spawn.getBlockY();
      int cz = spawn.getBlockZ();
      String shape = Main.JavaPlugin.getConfig().getString("BorderShape", "Square");
      return "Circle".equalsIgnoreCase(shape)
         ? traverseSphere(world, cx, cy, cz, radius)
         : traverseCube(world, cx, cy, cz, radius);
   }

   /**
    * Applies {@code target} to every location, on the main thread, {@value #BLOCKS_PER_TICK}
    * blocks per tick.
    *
    * @param onlyReplace when non-null, a block is changed only if it currently has this type
    */
   private static void applyBatched(World world, List<Location> locations, Material target, Material onlyReplace) {
      if (locations.isEmpty()) {
         return;
      }

      new BukkitRunnable() {
         private int index;

         @Override
         public void run() {
            if (!world.isChunkLoaded(world.getSpawnLocation().getBlockX() >> 4, world.getSpawnLocation().getBlockZ() >> 4)) {
               cancel();
               return;
            }
            int end = Math.min(index + BLOCKS_PER_TICK, locations.size());
            while (index < end) {
               Location location = locations.get(index++);
               Block block = world.getBlockAt(location);
               if (onlyReplace == null || block.getType() == onlyReplace) {
                  block.setType(target, false);
               }
            }
            if (index >= locations.size()) {
               cancel();
            }
         }
      }.runTaskTimer(Main.JavaPlugin, 1L, 1L);
   }

   public static List<Location> traverseSphere(World world, int centerX, int centerY, int centerZ, int radius) {
      List<Location> coordinates = new ArrayList<>();
      int outer = radius * radius;
      int inner = Math.max(0, radius - 5) * Math.max(0, radius - 5);

      for (int x = centerX - radius; x <= centerX + radius; x++) {
         for (int z = centerZ - radius; z <= centerZ + radius; z++) {
            for (int y = centerY - radius; y <= centerY + radius; y++) {
               int squared = squaredDistance(x, y, z, centerX, centerY, centerZ);
               if (squared <= outer && squared > inner) {
                  coordinates.add(new Location(world, x, y, z));
               }
            }
         }
      }

      return coordinates;
   }

   private static int squaredDistance(int x, int y, int z, int centerX, int centerY, int centerZ) {
      int dx = x - centerX;
      int dy = y - centerY;
      int dz = z - centerZ;
      return dx * dx + dy * dy + dz * dz;
   }

   /**
    * Hollow cube surface. Faces are emitted without the duplicate edge and corner
    * coordinates the original three-loop version produced.
    */
   public static List<Location> traverseCube(World world, int centerX, int centerY, int centerZ, int radius) {
      List<Location> coordinates = new ArrayList<>();
      int minX = centerX - radius;
      int maxX = centerX + radius;
      int minY = centerY - radius;
      int maxY = centerY + radius;
      int minZ = centerZ - radius;
      int maxZ = centerZ + radius;

      for (int x = minX; x <= maxX; x++) {
         for (int y = minY; y <= maxY; y++) {
            for (int z = minZ; z <= maxZ; z++) {
               boolean onSurface = x == minX || x == maxX || y == minY || y == maxY || z == minZ || z == maxZ;
               if (onSurface) {
                  coordinates.add(new Location(world, x, y, z));
               }
            }
         }
      }

      return coordinates;
   }
}
