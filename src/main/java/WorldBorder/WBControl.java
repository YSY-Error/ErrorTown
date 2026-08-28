package WorldBorder;

import com.ErrorTown.Main;
import com.ErrorTown.Variable;
import com.Util.Diag;
import com.Util.Home;
import com.Util.HomeAPI;
import com.Util.HomeTerrainPolicy;
import com.Util.Util;
import java.util.ArrayList;
import org.bukkit.Bukkit;
import org.bukkit.WorldBorder;
import org.bukkit.entity.Player;

/**
 * Per-player home border visibility, the {@code /sh togglecc} feature.
 *
 * <p><b>Why this was rewritten.</b> This class used to sniff the CraftBukkit package name for
 * {@code v1_12_R1} / {@code v1_16_R1..R3} and hand off to NMS border classes. Two things make that
 * dead code on every supported version: CraftBukkit stopped relocating its packages per Minecraft
 * version in 1.20.5, so the name no longer contains a version at all and no branch ever matched;
 * and the four {@code WorldBorder.R_*} classes it dispatched to were empty stubs anyway. The result
 * was a command that printed its toggle message and did nothing.</p>
 *
 * <p>Bukkit has had a per-player border since 1.18 — {@link Bukkit#createWorldBorder()} plus
 * {@link Player#setWorldBorder(WorldBorder)} — which is public API on Spigot, Paper, Purpur and
 * Leaves across 1.21 to 26.2. No reflection or NMS is involved any more.</p>
 */
public class WBControl {
   /**
    * Size of the "hidden" personal border. This is the vanilla maximum, so its wall sits far beyond
    * any reachable position and the player simply never sees one.
    */
   private static final double HIDDEN_BORDER_SIZE = 5.9999968E7;

   public static java.util.Map<String, Integer> border_redis = com.Util.Util.boundedCache(2048);

   /** Hides the home border for {@code p} by overriding it with a personal border of maximum size. */
   public static void setEnable(Player p) {
      if (Variable.not_adopt_nms || p == null || !Util.CheckIsHome(p.getWorld().getName())) {
         return;
      }
      applyPersonalBorder(p, HIDDEN_BORDER_SIZE, "border-hide");
   }

   /** Restores the visible home border for {@code p}, including any VIP size bonus. */
   public static void setDisable(Player p) {
      if (Variable.not_adopt_nms || p == null || !Util.CheckIsHome(p.getWorld().getName())) {
         return;
      }
      Home home = HomeAPI.getHome(p.getWorld().getName());
      if (home == null) {
         return;
      }

      int vip_add = highestVipBonus(home);
      if (border_redis.containsKey(home.getName())) {
         if (vip_add < border_redis.get(home.getName())) {
            vip_add = border_redis.get(home.getName());
         } else {
            border_redis.put(home.getName(), vip_add);
         }
      } else {
         border_redis.put(home.getName(), vip_add);
      }

      double size = HomeTerrainPolicy.configuredBorderSize(
         home.getLevel(),
         Main.JavaPlugin.getConfig().getBoolean("HomeTerrain.Enabled", false),
         Main.JavaPlugin.getConfig().getIntegerList("HomeUpgrade.LevelSizes"),
         Main.JavaPlugin.getConfig().getInt("WorldBoard"),
         Main.JavaPlugin.getConfig().getInt("UpdateRadius"),
         vip_add
      );
      applyPersonalBorder(p, size, "border-show");
   }

   public static void togglecc(Player p) {
      if (Variable.not_adopt_nms || p == null || !Util.CheckIsHome(p.getWorld().getName())) {
         return;
      }
      if (!Variable.has_already_hide_border.contains(p.getName())) {
         setEnable(p);
         p.sendMessage(Variable.Lang_YML.getString("ToggleccWorldDisable"));
         Variable.has_already_hide_border.add(p.getName());
      } else {
         setDisable(p);
         p.sendMessage(Variable.Lang_YML.getString("ToggleccWorldEnable"));
         Variable.has_already_hide_border.remove(p.getName());
      }
   }

   /**
    * The largest {@code VIPAdd} bonus held by the owner or any online co-owner.
    *
    * <p>Offline operators are skipped, matching the historical behaviour: their permissions cannot
    * be read reliably without a permission plugin lookup.</p>
    */
   private static int highestVipBonus(Home home) {
      ArrayList<String> players = new ArrayList<>();
      players.add(home.getName());
      for (String op : home.getOPs()) {
         if (Bukkit.getPlayer(op) != null) {
            players.add(op);
         }
      }

      int highest = 0;
      for (String entry : Main.JavaPlugin.getConfig().getStringList("VIPAdd")) {
         String[] parts = entry.split(",");
         if (parts.length < 2) {
            Diag.warnOnce("vipadd-malformed", "Config VIPAdd entry '" + entry + "' is not 'permission,size'; ignored");
            continue;
         }
         int add = Diag.parseInt(parts[1], 0, "vipadd-size", "VIPAdd size for '" + parts[0] + "'");
         if (add <= highest) {
            continue;
         }
         for (String name : players) {
            Player candidate = Bukkit.getPlayer(name);
            if (candidate != null && candidate.hasPermission(parts[0])) {
               highest = add;
               break;
            }
         }
      }
      return highest;
   }

   private static void applyPersonalBorder(Player p, double size, String signature) {
      try {
         WorldBorder border = Bukkit.createWorldBorder();
         border.setCenter(p.getWorld().getSpawnLocation());
         border.setSize(Math.max(1.0D, size));
         p.setWorldBorder(border);
      } catch (RuntimeException | LinkageError unsupported) {
         // Stop trying on a server that cannot do per-player borders, exactly as the old NMS path
         // gave up on NoSuchFieldError.
         Variable.not_adopt_nms = true;
         Diag.warnOnce(
            "platform-" + signature,
            "This server does not support per-player world borders; /sh togglecc is disabled",
            unsupported instanceof RuntimeException failure ? failure : null
         );
      }
   }
}
