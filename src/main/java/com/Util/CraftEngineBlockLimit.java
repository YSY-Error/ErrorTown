package com.Util;

import com.ErrorTown.Main;
import java.util.List;
import java.util.Locale;
import org.bukkit.Location;
import org.bukkit.block.Block;

/**
 * Applies the per-chunk block limit to CraftEngine custom blocks.
 *
 * <p><b>Why this needs its own path.</b> The existing limit in {@code BlockPlaceListener} matches a
 * fragment of a block's NBT and counts hits in {@code Chunk.getTileEntities()}. CraftEngine's custom
 * blocks are real registered blocks, but most of them are not block entities at all, so they never
 * appear in that list — a home could hold any number of them regardless of {@code TileList}.</p>
 *
 * <p><b>Why it is off by default.</b> Because they are not block entities, there is no cheap list to
 * count: the only exact answer is to look at blocks. A whole chunk is ~98k blocks per placement, which
 * is not acceptable, so this counts a bounded cube around the placement instead. That makes the limit
 * <i>local</i> rather than per-chunk, which is a deliberate trade and the reason an operator has to
 * opt in.</p>
 *
 * <p><b>Configuration.</b> Entries in {@code TileList} keep their {@code chunk|<match>|<max>} shape;
 * a CraftEngine rule is written with a {@code ce:} prefix on the match, for example
 * {@code chunk|ce:myfurniture:oak_chair|4}. Anything without that prefix is left to the NBT matcher
 * exactly as before.</p>
 */
public final class CraftEngineBlockLimit {
   /** Master switch. Off by default: the scan costs CPU on every placement. */
   public static final String ENABLED_KEY = "CraftEngineBlockLimit.Enabled";
   /** Half-width of the counted cube, in blocks. */
   public static final String RADIUS_KEY = "CraftEngineBlockLimit.ScanRadius";

   /** Prefix that marks a {@code TileList} match as a CraftEngine block id. */
   private static final String ID_PREFIX = "ce:";
   private static final int DEFAULT_RADIUS = 6;
   /** A cube of this half-width is 35^3 blocks; beyond it the scan stops being defensible. */
   private static final int MAX_RADIUS = 17;

   private CraftEngineBlockLimit() {
   }

   /** @return whether an operator turned the CraftEngine block limit on and CraftEngine is present */
   public static boolean isActive() {
      return CraftEngineBridge.isAvailable()
         && Main.JavaPlugin != null
         && Main.JavaPlugin.getConfig().getBoolean(ENABLED_KEY, false);
   }

   /**
    * The configured limit for the block being placed.
    *
    * @return the maximum allowed within the scan cube, or {@code -1} when this block is not limited
    */
   public static int limitFor(String blockId) {
      if (blockId == null) {
         return -1;
      }
      String id = blockId.toLowerCase(Locale.ROOT);
      List<String> rules = Main.JavaPlugin.getConfig().getStringList("TileList");
      for (String rule : rules) {
         String[] parts = rule.split("\\|");
         if (parts.length < 3 || !parts[0].equalsIgnoreCase("chunk")) {
            continue;
         }
         String match = parts[1].trim().toLowerCase(Locale.ROOT);
         if (!match.startsWith(ID_PREFIX)) {
            continue;
         }
         if (!match.substring(ID_PREFIX.length()).equals(id)) {
            continue;
         }
         int max = Diag.parseInt(parts[2], -1, "ce-tilelist-" + id, "TileList limit for '" + blockId + "'");
         return max;
      }
      return -1;
   }

   /**
    * Counts custom blocks with {@code blockId} around {@code centre}, excluding {@code centre} itself.
    *
    * <p>Only loaded chunks are consulted: a scan must never pull chunks in, which would turn a block
    * placement into disk I/O and, on 1.21.x, could deadlock inside the placement event.</p>
    */
   public static int countNearby(Block centre, String blockId) {
      if (centre == null || blockId == null) {
         return 0;
      }
      int radius = radius();
      Location origin = centre.getLocation();
      int minY = Math.max(centre.getWorld().getMinHeight(), origin.getBlockY() - radius);
      int maxY = Math.min(centre.getWorld().getMaxHeight() - 1, origin.getBlockY() + radius);
      int found = 0;

      for (int x = origin.getBlockX() - radius; x <= origin.getBlockX() + radius; x++) {
         for (int z = origin.getBlockZ() - radius; z <= origin.getBlockZ() + radius; z++) {
            if (!centre.getWorld().isChunkLoaded(x >> 4, z >> 4)) {
               continue;
            }
            for (int y = minY; y <= maxY; y++) {
               if (x == origin.getBlockX() && y == origin.getBlockY() && z == origin.getBlockZ()) {
                  continue;
               }
               Block candidate = centre.getWorld().getBlockAt(x, y, z);
               if (candidate.getType().isAir() || !CraftEngineBridge.isCustomBlock(candidate)) {
                  continue;
               }
               if (blockId.equalsIgnoreCase(CraftEngineBridge.blockId(candidate))) {
                  found++;
               }
            }
         }
      }
      return found;
   }

   private static int radius() {
      int configured = Main.JavaPlugin == null ? DEFAULT_RADIUS : Main.JavaPlugin.getConfig().getInt(RADIUS_KEY, DEFAULT_RADIUS);
      if (configured < 1 || configured > MAX_RADIUS) {
         Diag.warnOnce(
            RADIUS_KEY,
            RADIUS_KEY + " must be between 1 and " + MAX_RADIUS + " but is " + configured + "; using " + DEFAULT_RADIUS
         );
         return DEFAULT_RADIUS;
      }
      return configured;
   }
}
