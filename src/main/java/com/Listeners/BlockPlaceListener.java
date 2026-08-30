package com.Listeners;

import com.ErrorTown.Main;
import com.ErrorTown.Variable;
import com.Util.Home;
import com.Util.HomeAPI;
import com.Util.HomeTerrainPolicy;
import com.Util.Util;
import com.Util.VipBorderRatchet;
import java.util.ArrayList;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;

public class BlockPlaceListener implements Listener {
   public static java.util.Map<String, Integer> border_redis = com.Util.Util.boundedCache(2048);

   /**
    * Applies the per-area limit to a CraftEngine custom block.
    *
    * <p>The historical limit counts {@code Chunk.getTileEntities()}, and most CraftEngine custom
    * blocks are not block entities, so they were exempt from {@code TileList} entirely. This closes
    * that hole for operators who opt in; see {@link com.Util.CraftEngineBlockLimit} for why it is
    * opt-in and area-bounded rather than per-chunk.</p>
    *
    * @return whether the placement was cancelled, in which case the remaining checks are pointless
    */
   private static boolean enforceCraftEngineBlockLimit(BlockPlaceEvent event, Player p) {
      if (!com.Util.CraftEngineBlockLimit.isActive()) {
         return false;
      }
      Block placed = event.getBlock();
      String blockId = com.Util.CraftEngineBridge.blockId(placed);
      if (blockId == null) {
         return false;
      }
      int max = com.Util.CraftEngineBlockLimit.limitFor(blockId);
      if (max < 0) {
         return false;
      }

      int existing = com.Util.CraftEngineBlockLimit.countNearby(placed, blockId);
      if (existing < max) {
         return false;
      }

      event.setCancelled(true);
      String message = com.Util.Lang.get("PlaceReachMaxTile", "§8[§6错误庄园§8] §c该方块已达上限 <Now>/<Max> (<NBT>)")
         .replace("<Now>", String.valueOf(existing))
         .replace("<Max>", String.valueOf(max))
         .replace("<NBT>", blockId);
      p.sendMessage(message);
      return true;
   }

   @EventHandler(
      priority = EventPriority.HIGHEST
   )
   public void BlockCanBuildEvent(BlockPlaceEvent event) {
      if (!event.getPlayer().getName().toUpperCase().contains("AS-FAKEPLAYER")
         && !event.getPlayer().getName().toUpperCase().contains("[MINECRAFT]")
         && !event.getPlayer().getName().toUpperCase().contains("[MEKANISM]")
         && !event.getPlayer().getName().toUpperCase().contains("[IF]")
         && !event.getPlayer().getName().toUpperCase().contains("[depolyer]".toUpperCase())
         && !event.getPlayer().getName().toUpperCase().contains("[XU2FakePlayer]".toUpperCase())
         && !event.getPlayer().getName().toUpperCase().contains("[Modular Routers]".toUpperCase())) {
         Player p = event.getPlayer();
         if (event.getBlock() != null) {
            if (Variable.Debug.contains(p.getName())) {
               String blockNbt = Util.getNBTString(event.getBlock().getState());
               p.sendMessage("§e§l§m--------------§7[§eDeBug§7]§e§l§m--------------");
               com.Util.ClickableText.suggest(p, "§eGet-Returned:§d" + blockNbt + " §b>> §dCopy", blockNbt);
               p.sendMessage("§e§l§m--------------§7[§eDeBug§7]§e§l§m--------------");
            }

            if (Util.CheckIsHome(event.getPlayer().getWorld().getName().replace(Variable.world_prefix, ""))) {
               if (enforceCraftEngineBlockLimit(event, p)) {
                  return;
               }

               if (Main.JavaPlugin.getConfig().getBoolean("CustomTileMax")) {
                  Block block = event.getBlock();
                  String nbt = Util.getNBTString(block.getState());
                  boolean check_contain = false;
                  String contain_nbt = "";
                  int MaxThisTile = 0;

                  java.util.List<String> tileRules = Main.JavaPlugin.getConfig().getStringList("TileList");
                  for (int d = 0; d < tileRules.size(); d++) {
                     String[] temp = (tileRules.get(d)).split("\\|");
                     if (temp[0].equalsIgnoreCase("chunk") && nbt.toUpperCase().contains(temp[1].toUpperCase())) {
                        check_contain = true;
                        contain_nbt = temp[1];
                        MaxThisTile = Integer.valueOf(temp[2]);
                        break;
                     }
                  }

                  if (check_contain) {
                     int NowAmount = 0;
                     boolean extra_perm = false;
                     int extra_amount = MaxThisTile;

                     for (int i = 100; i > 0; i--) {
                        if (com.Util.Perm.has(p, "ErrorTown.ChunkPlace." + contain_nbt + "." + i)) {
                           extra_perm = true;
                           if (extra_amount < i) {
                              extra_amount = i;
                              break;
                           }
                        }
                     }

                     Home home = HomeAPI.getHome(event.getBlock().getWorld().getName().replace(Variable.world_prefix, ""));

                     for (String str : home.getLimitBlock()) {
                        String[] args = str.split("\\|");
                        int start_amount = 0;

                        java.util.List<String> tileRules2 = Main.JavaPlugin.getConfig().getStringList("TileList");
                        for (int e = 0; e < tileRules2.size(); e++) {
                           String[] temp = (tileRules2.get(e)).split("\\|");
                           if (temp[0].equalsIgnoreCase("chunk") && nbt.toUpperCase().contains(temp[1].toUpperCase())) {
                              start_amount = Integer.valueOf(args[2]);
                              break;
                           }
                        }

                        if (args[0].equalsIgnoreCase("chunk") && nbt.toUpperCase().contains(args[1].toUpperCase())) {
                           int amount = Integer.valueOf(args[2]) + start_amount;
                           if (extra_amount < amount) {
                              extra_perm = true;
                              extra_amount = amount;
                           }
                        }
                     }

                     for (BlockState state : event.getBlock().getChunk().getTileEntities()) {
                        if (Util.getNBTString(state).toUpperCase().contains(contain_nbt.toUpperCase())) {
                           if (Main.JavaPlugin.getConfig().getBoolean("EnableClearExtraBlocks") && ++NowAmount > MaxThisTile) {
                              event.setCancelled(true);
                              state.getBlock().getLocation().getWorld().getBlockAt(state.getBlock().getLocation()).setType(Material.AIR);
                              p.sendMessage(Variable.Lang_YML.getString("ClearExtraBlocks"));
                           }
                        }
                     }

                     NowAmount--;
                     if (extra_perm) {
                        MaxThisTile = extra_amount;
                     }

                     if (NowAmount + 1 <= MaxThisTile) {
                        String temp = Variable.Lang_YML.getString("PlaceMaxTile");
                        if (temp.contains("<Now>")) {
                           temp = temp.replace("<Now>", String.valueOf(NowAmount + 1));
                        }

                        if (temp.contains("<Max>")) {
                           temp = temp.replace("<Max>", String.valueOf(MaxThisTile));
                        }

                        if (temp.contains("<NBT>")) {
                           temp = temp.replace("<NBT>", String.valueOf(contain_nbt));
                        }

                        p.sendMessage(temp);
                     } else {
                        String tempx = Variable.Lang_YML.getString("PlaceReachMaxTile");
                        if (tempx.contains("<Now>")) {
                           tempx = tempx.replace("<Now>", String.valueOf(NowAmount));
                        }

                        if (tempx.contains("<Max>")) {
                           tempx = tempx.replace("<Max>", String.valueOf(MaxThisTile));
                        }

                        if (tempx.contains("<NBT>")) {
                           tempx = tempx.replace("<NBT>", String.valueOf(contain_nbt));
                        }

                        event.setCancelled(true);
                        p.sendMessage(tempx);
                     }
                  }
               }

               Home home = HomeAPI.getHome(p.getWorld().getName());
               int vip_add = 0;
               ArrayList<String> players = new ArrayList<>();
               players.add(home.getName());

               for (String op : home.getOPs()) {
                  if (Bukkit.getPlayer(op) != null) {
                     players.add(op);
                  }
               }

               for (String s : Main.JavaPlugin.getConfig().getStringList("VIPAdd")) {
                  String[] ss = s.split(",");

                  for (String p_name : players) {
                     Player pe = Bukkit.getPlayer(p_name);
                     if (pe != null && pe.hasPermission(ss[0])) {
                        int add = Integer.valueOf(ss[1]);
                        if (add > vip_add) {
                           vip_add = add;
                        }
                     }
                  }
               }

               vip_add = VipBorderRatchet.highWaterMark(border_redis, home.getName(), vip_add);

               double set_x = 0.0;
               double min_x = 0.0;
               double set_z = 0.0;
               double min_z = 0.0;
               int level = home.getLevel();
               double halfSize = HomeTerrainPolicy.configuredBorderSize(
                  level,
                  Main.JavaPlugin.getConfig().getBoolean("HomeTerrain.Enabled", false),
                  Main.JavaPlugin.getConfig().getIntegerList("HomeUpgrade.LevelSizes"),
                  Main.JavaPlugin.getConfig().getInt("WorldBoard"),
                  Main.JavaPlugin.getConfig().getInt("UpdateRadius"),
                  vip_add
               ) / 2.0;
               set_x = p.getWorld().getSpawnLocation().getX() + halfSize;
               min_x = p.getWorld().getSpawnLocation().getX() - halfSize;
               set_z = p.getWorld().getSpawnLocation().getZ() + halfSize;
               min_z = p.getWorld().getSpawnLocation().getZ() - halfSize;
               if (set_x < min_x) {
                  double borderX = set_x;
                  set_x = min_x;
                  min_x = borderX;
               }

               if (set_z < min_z) {
                  double borderX = set_z;
                  set_z = min_z;
                  min_z = borderX;
               }

               if (event.getBlock().getLocation().getX() < min_x
                  || event.getBlock().getLocation().getX() > set_x
                  || event.getBlock().getLocation().getZ() < min_z
                  || event.getBlock().getLocation().getZ() > set_z) {
                  event.setCancelled(true);
               }
            }
         }
      }
   }
}
