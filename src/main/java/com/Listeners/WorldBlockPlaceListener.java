package com.Listeners;

import com.ErrorTown.Main;
import com.ErrorTown.Variable;
import com.Util.Home;
import com.Util.HomeAPI;
import com.Util.MySQL;
import com.Util.Util;
import java.io.File;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;

public class WorldBlockPlaceListener implements Listener {
   public int getChunkAmount(String nbt, Chunk chunk) {
      int NowAmount = 0;

      BlockState[] arrayOfBlockState;
      for (BlockState state : arrayOfBlockState = chunk.getTileEntities()) {
         if (Util.getNBTString(state).toUpperCase().contains(nbt.toUpperCase())) {
            NowAmount++;
         }
      }

      return NowAmount;
   }

   @EventHandler(
      priority = EventPriority.HIGHEST
   )
   public void BlockCanBuildEvent2(BlockPlaceEvent event) {
      if (!event.getPlayer().getName().toUpperCase().contains("AS-FAKEPLAYER")
         && !event.getPlayer().getName().toUpperCase().contains("[MINECRAFT]")
         && !event.getPlayer().getName().toUpperCase().contains("[MEKANISM]")
         && !event.getPlayer().getName().toUpperCase().contains("[IF]")) {
         Player p = event.getPlayer();
         if (event.getBlock() != null) {
            int level = 0;
            if (Variable.bungee) {
               if (!MySQL.CheckIsAHome(p.getWorld().getName().replace(Variable.world_prefix, ""))) {
                  return;
               }

               level = Integer.valueOf(MySQL.getLevel(p.getWorld().getName().replace(Variable.world_prefix, "")));
            } else {
               File f2 = new File(Variable.Tempf, p.getWorld().getName().replace(Variable.world_prefix, "") + ".yml");
               if (!f2.exists()) {
                  return;
               }

               YamlConfiguration yamlConfiguration = YamlConfiguration.loadConfiguration(f2);
               level = yamlConfiguration.getInt("Level");
            }

            int addradius = Main.JavaPlugin.getConfig().getInt("UpdateRadius") / 2;
            if (Main.JavaPlugin.getConfig().getBoolean("CustomTileMax")) {
               Block block = event.getBlock();
               String nbt = Util.getNBTString(block.getState());
               boolean check_contain = false;
               String contain_nbt = "";
               int MaxThisTile = 0;

               var __cfg0 = Main.JavaPlugin.getConfig().getStringList("TileList");
               for (int d = 0; d < __cfg0.size(); d++) {
                  String[] temp = (__cfg0.get(d)).split("\\|");
                  if (temp[0].equalsIgnoreCase("world") && nbt.toUpperCase().contains(temp[1].toUpperCase())) {
                     check_contain = true;
                     contain_nbt = temp[1];
                     MaxThisTile = Integer.valueOf(temp[2]);
                     break;
                  }
               }

               if (check_contain) {
                  int NowAmount = 0;
                  Location topleft = p.getWorld().getSpawnLocation().clone();
                  Location topright = p.getWorld().getSpawnLocation().clone();
                  Location bottomleft = p.getWorld().getSpawnLocation().clone();
                  Location bottomright = p.getWorld().getSpawnLocation().clone();
                  topleft.setX(p.getLocation().getWorld().getSpawnLocation().getX() + (level + 1) * addradius);
                  topleft.setZ(p.getLocation().getWorld().getSpawnLocation().getZ() + (level + 1) * addradius);
                  topright.setX(p.getLocation().getWorld().getSpawnLocation().getX() + (level + 1) * addradius);
                  topright.setZ(p.getLocation().getWorld().getSpawnLocation().getZ() - (level + 1) * addradius);
                  bottomleft.setX(p.getLocation().getWorld().getSpawnLocation().getX() - (level + 1) * addradius);
                  bottomleft.setZ(p.getLocation().getWorld().getSpawnLocation().getZ() + (level + 1) * addradius);
                  bottomright.setX(p.getLocation().getWorld().getSpawnLocation().getX() - (level + 1) * addradius);
                  bottomright.setZ(p.getLocation().getWorld().getSpawnLocation().getZ() - (level + 1) * addradius);

                  for (Chunk temp : Util.getchunkmap(topleft, topright, bottomleft, bottomright)) {
                     NowAmount += this.getChunkAmount(contain_nbt, temp);
                  }

                  NowAmount--;
                  boolean extra_perm = false;
                  int extra_amount = MaxThisTile;

                  for (int i = 100; i > 0; i--) {
                     if (com.Util.Perm.has(p, "ErrorTown.WorldPlace." + contain_nbt + "." + i)) {
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

                     var __cfg1 = Main.JavaPlugin.getConfig().getStringList("TileList");
                     for (int e = 0; e < __cfg1.size(); e++) {
                        String[] temp = (__cfg1.get(e)).split("\\|");
                        if (temp[0].equalsIgnoreCase("world") && nbt.toUpperCase().contains(temp[1].toUpperCase())) {
                           start_amount = Integer.valueOf(temp[2]);
                           break;
                        }
                     }

                     if (args[0].equalsIgnoreCase("world") && nbt.toUpperCase().contains(args[1].toUpperCase())) {
                        int amount = Integer.valueOf(args[2]) + start_amount;
                        if (extra_amount < amount) {
                           extra_perm = true;
                           extra_amount = amount;
                        }
                     }
                  }

                  for (String se : Main.JavaPlugin.getConfig().getStringList("AnotherChunkLimit")) {
                     String[] args = se.split("\\|");
                     if (nbt.toUpperCase().contains(args[0].toUpperCase())
                        && home.getLevel() >= Integer.valueOf(args[1])
                        && Integer.valueOf(args[2]) >= extra_amount) {
                        extra_perm = true;
                        extra_amount = Integer.valueOf(args[2]);
                     }
                  }

                  if (extra_perm) {
                     MaxThisTile = extra_amount;
                  }

                  if (Main.JavaPlugin.getConfig().getBoolean("EnableClearExtraBlocks") && NowAmount + 1 > MaxThisTile) {
                     event.setCancelled(true);
                     int wait_to_delete = NowAmount + 1 - MaxThisTile - 1;

                     for (Chunk temp : Util.getchunkmap(topleft, topright, bottomleft, bottomright)) {
                        BlockState[] arrayOfBlockState;
                        for (BlockState state : arrayOfBlockState = temp.getTileEntities()) {
                           if (Util.getNBTString(state).toUpperCase().contains(contain_nbt.toUpperCase())) {
                              if (wait_to_delete == 0) {
                                 break;
                              }

                              state.getBlock().getLocation().getWorld().getBlockAt(state.getBlock().getLocation()).setType(Material.AIR);
                              p.sendMessage(Variable.Lang_YML.getString("ClearExtraBlocks"));
                              wait_to_delete--;
                           }
                        }
                     }
                  }

                  if (NowAmount + 1 <= MaxThisTile) {
                     String tempx = Variable.Lang_YML.getString("PlaceWorldMaxTile");
                     if (tempx.contains("<Now>")) {
                        tempx = tempx.replace("<Now>", String.valueOf(NowAmount + 1));
                     }

                     if (tempx.contains("<Max>")) {
                        tempx = tempx.replace("<Max>", String.valueOf(MaxThisTile));
                     }

                     if (tempx.contains("<NBT>")) {
                        tempx = tempx.replace("<NBT>", String.valueOf(contain_nbt));
                     }

                     p.sendMessage(tempx);
                  } else {
                     String tempxx = Variable.Lang_YML.getString("PlaceReachWorldMaxTile");
                     if (tempxx.contains("<Now>")) {
                        tempxx = tempxx.replace("<Now>", String.valueOf(NowAmount));
                     }

                     if (tempxx.contains("<Max>")) {
                        tempxx = tempxx.replace("<Max>", String.valueOf(MaxThisTile));
                     }

                     if (tempxx.contains("<NBT>")) {
                        tempxx = tempxx.replace("<NBT>", String.valueOf(contain_nbt));
                     }

                     event.setCancelled(true);
                     p.sendMessage(tempxx);
                  }
               }
            }
         }
      }
   }
}
