package com.ErrorTown;

import WorldBorder.WBControl;
import com.Util.Home;
import com.Util.BukkitCompat;
import com.Util.HomeAPI;
import com.Util.HomeTerrainPolicy;
import com.Util.MySQL;
import com.Util.R1_12_2;
import com.Util.R1_7_10;
import com.Util.StaticsTick;
import com.Util.Util;
import com.Util.VipBorderRatchet;
import com.Util.ZIP;
import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

public class ScheduledTasks {
   public static java.util.Map<String, Integer> border_redis = com.Util.Util.boundedCache(2048);
   public static HashMap<String, List<String>> OPS_redis = new HashMap<>();
   public static HashMap<String, List<String>> MEMBERS_redis = new HashMap<>();

   public static void refreshWorldStatics(boolean broad) {
      Variable.list_home.clear();
      if (Variable.bungee) {
         for (String str : MySQL.getAllWorlds()) {
            Variable.list_home.add(str);
         }
      } else {
         File folder = new File(Main.JavaPlugin.getDataFolder().getPath().toString() + Variable.file_loc_prefix + "playerdata");
         for (File temp : folder.listFiles()) {
            String want_to = temp.getPath()
               .replace(Main.JavaPlugin.getDataFolder().getPath().toString() + Variable.file_loc_prefix + "playerdata", "")
               .replace(Variable.file_loc_prefix, "")
               .replace(".yml", "");
            Variable.list_home.add(want_to);
         }
      }

      Variable.world_StaticsTick.clear();
      boolean check_has = false;
      for (World world : Bukkit.getWorlds()) {
         if (Util.CheckIsHome(world.getName())) {
            check_has = true;
            int chunks = 0;
            int tiles = 0;
            int entity = 0;
            int dropitem = 0;
            for (Chunk chunk : world.getLoadedChunks()) {
               chunks++;
               for (BlockState bs : chunk.getTileEntities()) {
                  tiles++;
               }
               for (Entity et : chunk.getEntities()) {
                  if (!BukkitCompat.isDroppedItem(et)) {
                     entity++;
                  } else {
                     Item i = (Item)et;
                     dropitem += i.getItemStack().getAmount();
                  }
               }
            }

            double calc_tps = tiles * Main.JavaPlugin.getConfig().getDouble("OneTileTick")
               + entity * Main.JavaPlugin.getConfig().getDouble("OneEntityTick")
               + dropitem * Main.JavaPlugin.getConfig().getDouble("OneDropTick")
               + chunks * Main.JavaPlugin.getConfig().getDouble("OneChunkTick");
            StaticsTick temp = new StaticsTick(world.getName().replaceAll(Variable.world_prefix, ""), tiles, chunks, entity, dropitem, calc_tps);
            Variable.world_StaticsTick.add(temp);
         }
      }

      if (check_has) {
         if (broad) {
            for (String a : Main.JavaPlugin.getConfig().getStringList("StatisticsTop")) {
               Bukkit.broadcastMessage(a);
            }

            // Was a hand-written bubble sort: O(n^2) comparisons plus two List.set calls
            // per swap on a synchronized list. sort() is O(n log n) and takes the lock once.
            Variable.world_StaticsTick.sort((left, right) -> Double.compare(right.tps, left.tps));

            int showAmount = Main.JavaPlugin.getConfig().getInt("ShowAmount");
            for (int i = 0; i < Variable.world_StaticsTick.size() && i < showAmount; i++) {
               StaticsTick s = Variable.world_StaticsTick.get(i);
               if (s.tps != 0.0) {
                  String temp = Main.JavaPlugin.getConfig().getString("ShowFormat");
                  if (temp.contains("<index>")) {
                     temp = temp.replace("<index>", String.valueOf(i + 1));
                  }

                  if (temp.contains("<world>")) {
                     temp = temp.replace("<world>", s.name);
                  }

                  if (temp.contains("<tile>")) {
                     temp = temp.replace("<tile>", String.valueOf(s.tile));
                  }

                  if (temp.contains("<chunk>")) {
                     temp = temp.replace("<chunk>", String.valueOf(s.chunk));
                  }

                  if (temp.contains("<entity>")) {
                     temp = temp.replace("<entity>", String.valueOf(s.entity));
                  }

                  if (temp.contains("<drop>")) {
                     temp = temp.replace("<drop>", String.valueOf(s.drop));
                  }

                  if (temp.contains("<tps>")) {
                     temp = temp.replace("<tps>", String.format(Main.JavaPlugin.getConfig().getString("FormatInfo"), s.tps));
                  }

                  Bukkit.broadcastMessage(temp);
               }
            }
            for (String a : Main.JavaPlugin.getConfig().getStringList("StatisticsEnd")) {
               Bukkit.broadcastMessage(a);
            }
         }
      }
   }

   public static void start() {
      if (Main.JavaPlugin.getConfig().getBoolean("BorderSwitch")) {
         (new BukkitRunnable() {
               public void run() {
                  for (World world : Bukkit.getWorlds()) {
                     if (Util.CheckIsHome(world.getName().replace(Variable.world_prefix, ""))) {
                        Home home = HomeAPI.getHome(world.getName().replace(Variable.world_prefix, ""));
                        if (Main.JavaPlugin.getConfig().getBoolean("BorderSwitch")) {
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

                           vip_add = VipBorderRatchet.highWaterMark(ScheduledTasks.border_redis, home.getName(), vip_add);

                           try {
                              world.getWorldBorder().setCenter(world.getSpawnLocation());
                              world.getWorldBorder()
                                 .setSize(
                                    HomeTerrainPolicy.configuredBorderSize(
                                       home.getLevel(),
                                       Main.JavaPlugin.getConfig().getBoolean("HomeTerrain.Enabled", false),
                                       Main.JavaPlugin.getConfig().getIntegerList("HomeUpgrade.LevelSizes"),
                                       Main.JavaPlugin.getConfig().getInt("WorldBoard"),
                                       Main.JavaPlugin.getConfig().getInt("UpdateRadius"),
                                       vip_add
                                    )
                                 );
                              for (Player p : world.getPlayers()) {
                                 if (Variable.has_already_hide_border.contains(p.getName())) {
                                    WBControl.setEnable(p);
                                 } else {
                                    WBControl.setDisable(p);
                                 }
                              }
                           } catch (NoSuchMethodError unsupported) {
                              Bukkit.getConsoleSender().sendMessage(Variable.Lang_YML.getString("BorderException"));
                           }
                        }
                     }
                  }
               }
            })
            .runTaskTimer(Main.JavaPlugin, 0L, 60L);
      }

      (new BukkitRunnable() {
         public void run() {
            Calendar cal = Calendar.getInstance();
            int hour = cal.getTime().getHours();
            int minute = cal.getTime().getMinutes();
            int seconds = cal.getTime().getSeconds();
            if (hour == 0 && minute == 0 && seconds == 0) {
               Variable.popularity_list.clear();
               Variable.flowers_list.clear();
            }
         }
      }).runTaskTimerAsynchronously(Main.JavaPlugin, 0L, 20L);
      (new BukkitRunnable() {
            public void run() {
               Variable.list_home.clear();
               if (Variable.bungee) {
                  for (String str : MySQL.getAllWorlds()) {
                     Variable.list_home.add(str);
                  }
               } else {
                  File folder = new File(Main.JavaPlugin.getDataFolder().getPath().toString() + Variable.file_loc_prefix + "playerdata");
                  for (File temp : folder.listFiles()) {
                     String want_to = temp.getPath()
                        .replace(Main.JavaPlugin.getDataFolder().getPath().toString() + Variable.file_loc_prefix + "playerdata", "")
                        .replace(Variable.file_loc_prefix, "")
                        .replace(".yml", "");
                     Variable.list_home.add(want_to);
                  }
               }
            }
         })
         .runTaskTimer(Main.JavaPlugin, 0L, 100L);
      if (!Main.JavaPlugin.getConfig().getBoolean("DisableFunctionButTeleport") && Main.JavaPlugin.getConfig().getBoolean("EnableBlackItemsUseInNoPermission")) {
         (new BukkitRunnable() {
               public void run() {
                  for (World world : Bukkit.getWorlds()) {
                     if (Util.CheckIsHome(world.getName().replace(Variable.world_prefix, ""))) {
                        label59:
                        for (Player p : world.getPlayers()) {
                           if (!Util.Check(p, world.getName().replace(Variable.world_prefix, ""))) {
                              for (ItemStack i : p.getInventory().getContents()) {
                                 String nbt = Util.getItemNBTString(i);

                                 java.util.List<String> blackItems = Main.JavaPlugin.getConfig().getStringList("BlackItems");
                                 for (int j = 0; j < blackItems.size(); j++) {
                                    if (nbt.toUpperCase().contains((blackItems.get(j)).toUpperCase())) {
                                       String command = Main.JavaPlugin.getConfig().getString("BeKickedCommand");
                                       if (command.contains("<Name>")) {
                                          command = command.replace("<Name>", p.getName());
                                       }

                                       Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
                                       String message = Variable.Lang_YML.getString("TakeBlackItemsInNoPermissionHome");
                                       if (message.contains("<type>")) {
                                          message = message.replace(
                                             "<type>", (blackItems.get(j)).toUpperCase()
                                          );
                                       }

                                       p.sendMessage(message);
                                       continue label59;
                                    }
                                 }
                              }
                           }
                        }
                     }
                  }
               }
            })
            .runTaskTimerAsynchronously(Main.JavaPlugin, 0L, 20L);
      }

      if (!Main.JavaPlugin.getConfig().getBoolean("DisableFunctionButTeleport") && Main.JavaPlugin.getConfig().getBoolean("CustomEntityMax")) {
         (new BukkitRunnable() {
            public void run() {
               for (World world : Bukkit.getWorlds()) {
                  if (Util.CheckIsHome(world.getName().replace(Variable.world_prefix, ""))) {
                     HashMap<String, Integer> entity_map = new HashMap<>();
                     for (Entity entity : world.getEntities()) {
                        String type = null;
                        if (Bukkit.getBukkitVersion().toString().contains("1.12.2")) {
                           type = R1_12_2.getName(entity);
                        } else if (Bukkit.getBukkitVersion().toString().contains("1.7.10")) {
                           type = R1_7_10.getName(entity);
                        } else {
                           type = entity.getType().toString().toUpperCase();
                        }

                        if (entity instanceof Animals) {
                           type = "Animals";
                        }

                        if (!entity_map.containsKey(type)) {
                           entity_map.put(type, 1);
                        } else {
                           int now_amount = entity_map.get(type);

                           java.util.List<String> entityRules = Main.JavaPlugin.getConfig().getStringList("EntityList");
                           for (int c = 0; c < entityRules.size(); c++) {
                              String[] args = (entityRules.get(c)).split("\\|");
                              if (args[0].toUpperCase().contains(type.toUpperCase())) {
                                 int Max_Amount = Integer.valueOf(args[1]);
                                 if (now_amount > Max_Amount) {
                                    entity.remove();
                                 } else {
                                    entity_map.put(type, now_amount + 1);
                                 }
                              }
                           }
                        }
                     }
                  }
               }
            }
         }).runTaskTimer(Main.JavaPlugin, 0L, Main.JavaPlugin.getConfig().getLong("CheckEntityInterval") * 20L);
      }

      if (!Main.JavaPlugin.getConfig().getBoolean("DisableFunctionButTeleport")
         && Main.JavaPlugin.getConfig().getBoolean("EnableTilesAndChunksAndDropItemsStatisticsTop")) {
         (new BukkitRunnable() {
            public void run() {
               ScheduledTasks.refreshWorldStatics(true);
            }
         }).runTaskTimer(Main.JavaPlugin, 0L, Main.JavaPlugin.getConfig().getLong("ShowTimes") * 20L);
      }

      if (!Main.JavaPlugin.getConfig().getBoolean("DisableFunctionButTeleport")) {
         if (Main.JavaPlugin.getConfig().getLong("SaveTime") != 0L) {
            Bukkit.getConsoleSender().sendMessage(Variable.Lang_YML.getString("EnableAutoSaveWorld"));
            (new BukkitRunnable() {
               public void run() {
                  for (World temp : Bukkit.getWorlds()) {
                     boolean is_jump = false;
                     for (String str : Main.JavaPlugin.getConfig().getStringList("UnAutoSaveWorlds")) {
                        if (str.equalsIgnoreCase(temp.getName().replace(Variable.world_prefix, ""))) {
                           is_jump = true;
                           break;
                        }
                     }

                     if (!is_jump) {
                        temp.save();
                     }
                  }

                  Bukkit.getConsoleSender().sendMessage(Variable.Lang_YML.getString("AutoSaveSuccess"));
               }
            }).runTaskTimer(Main.JavaPlugin, 0L, Main.JavaPlugin.getConfig().getLong("SaveTime") * 20L);
         } else {
            Bukkit.getConsoleSender().sendMessage(Variable.Lang_YML.getString("DisableAutoSaveWorld"));
         }
      }

      if (!Main.JavaPlugin.getConfig().getBoolean("DisableFunctionButTeleport")) {
         (new BukkitRunnable() {
            public void run() {
               if (Main.JavaPlugin.getConfig().getInt("ArmorStand") != -1) {
                  for (World world : Bukkit.getWorlds()) {
                     if (Util.CheckIsHome(world.getName().replaceAll(Variable.world_prefix, ""))) {
                        int amount = 0;
                        for (Entity entity : world.getEntities()) {
                           if (entity.getType() == EntityType.ARMOR_STAND && ++amount > Main.JavaPlugin.getConfig().getInt("ArmorStand")) {
                              entity.remove();
                              amount--;
                           }
                        }
                     }
                  }
               }
            }
         }).runTaskTimer(Main.JavaPlugin, 0L, 100L);
      }

      if (!Main.JavaPlugin.getConfig().getBoolean("DisableFunctionButTeleport")) {
         if (Main.JavaPlugin.getConfig().getLong("AutoBackup") != 0L) {
            (new BukkitRunnable() {
               public void run() {
                  if (Variable.check_first_start) {
                     Bukkit.getConsoleSender().sendMessage(Variable.Lang_YML.getString("EnableAutoBackup"));
                     Bukkit.getConsoleSender().sendMessage(Variable.Lang_YML.getString("EnableAutoBackupButFirstTime"));
                     Variable.check_first_start = false;
                  } else {
                     LocalDateTime now = LocalDateTime.now();
                     String time = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm"));
                     if (Variable.bungee) {
                        File f = null;
                        String OriginalBackup_location = Variable.custom_autobackup_location + Variable.file_loc_prefix + time;
                        if (!Main.JavaPlugin.getConfig().getString("CustomBackupLocation").equalsIgnoreCase("")) {
                           OriginalBackup_location = Main.JavaPlugin.getConfig().getString("CustomBackupLocation") + time;
                        }

                        boolean check_has_copy = true;
                        String folderToCompress = "";
                        for (String worldname : MySQL.getAllWorlds()) {
                           if (MySQL.getServer(worldname).equalsIgnoreCase(Main.JavaPlugin.getConfig().getString("Server"))) {
                              if (MySQL.getVisitTime(worldname).equalsIgnoreCase("")) {
                                 MySQL.setVisitTime(worldname, String.valueOf(System.currentTimeMillis()));
                              }

                              long before_time = Long.valueOf(MySQL.getVisitTime(worldname));
                              long distance = (System.currentTimeMillis() - before_time) / 86400000L;
                              if (distance <= Main.JavaPlugin.getConfig().getLong("NoBackup")) {
                                 if (Variable.world_prefix.equalsIgnoreCase("")) {
                                    if (Bukkit.getVersion().toString().toUpperCase().contains("ARCLIGHT")) {
                                       f = new File(Variable.single_server_gen + Variable.world_prefix + worldname);
                                    } else {
                                       f = new File(Variable.single_server_gen + "world" + Variable.file_loc_prefix + worldname);
                                    }
                                 } else {
                                    f = new File(Variable.single_server_gen + Variable.world_prefix + worldname);
                                 }

                                 String oldDir = OriginalBackup_location + Variable.file_loc_prefix + worldname;

                                 try {
                                    Util.copyDir(f.getPath(), oldDir);
                                    folderToCompress = Variable.custom_autobackup_location + Variable.file_loc_prefix + time;
                                 } catch (Exception failure) {
                                    check_has_copy = false;
                                 }
                              }
                           }
                        }

                        if (check_has_copy) {
                           String zipFileName = OriginalBackup_location + ".zip";

                           try {
                              ZIP.zipFolder(OriginalBackup_location, zipFileName);
                           } catch (IOException ioFailure) {
                              com.Util.Diag.warnOnce("scheduledtasks-start", "File I/O failed in ScheduledTasks.start", ioFailure);
                           }

                           Util.deleteFile(new File(OriginalBackup_location));
                           Bukkit.getConsoleSender().sendMessage(Variable.Lang_YML.getString("BungeeCordModuleAutoBackupSuccess"));
                        }
                     } else {
                        File folder = new File(Variable.Tempf);
                        String OriginalBackup_locationx = Variable.custom_autobackup_location + Variable.file_loc_prefix + time;
                        if (!Main.JavaPlugin.getConfig().getString("CustomBackupLocation").equalsIgnoreCase("")) {
                           OriginalBackup_locationx = Main.JavaPlugin.getConfig().getString("CustomBackupLocation") + time;
                        }

                        String folderToCompress = null;
                        boolean check_has_copy = true;
                        for (File temp : folder.listFiles()) {
                           long lastModified = temp.lastModified();
                           long nowlong = System.currentTimeMillis();
                           long distance = (nowlong - lastModified) / 86400000L;
                           if (distance <= Main.JavaPlugin.getConfig().getLong("NoBackup")) {
                              String want_to = temp.getPath().replace(Variable.Tempf, "").replace(".yml", "").replace(Variable.file_loc_prefix, "");
                              if (Bukkit.getWorld(Variable.world_prefix + want_to) != null) {
                                 Bukkit.getWorld(Variable.world_prefix + want_to).save();
                              }

                              String oldDir = OriginalBackup_locationx + Variable.file_loc_prefix + want_to;
                              File fx = null;
                              if (Variable.world_prefix.equalsIgnoreCase("")) {
                                 if (Bukkit.getVersion().toString().toUpperCase().contains("ARCLIGHT")) {
                                    fx = new File(Variable.single_server_gen + Variable.world_prefix + want_to);
                                 } else {
                                    fx = new File(Variable.single_server_gen + "world" + Variable.file_loc_prefix + want_to);
                                 }
                              } else {
                                 fx = new File(Variable.single_server_gen + Variable.world_prefix + want_to);
                              }

                              try {
                                 Util.copyDir(fx.getPath(), oldDir);
                                 folderToCompress = Variable.custom_autobackup_location + Variable.file_loc_prefix + time;
                              } catch (Exception failure) {
                                 check_has_copy = false;
                              }
                           }
                        }

                        if (check_has_copy) {
                           String zipFileName = OriginalBackup_locationx + ".zip";

                           try {
                              ZIP.zipFolder(OriginalBackup_locationx, zipFileName);
                           } catch (IOException ioFailure) {
                              com.Util.Diag.warnOnce("scheduledtasks-start-2", "File I/O failed in ScheduledTasks.start", ioFailure);
                           }

                           Util.deleteFile(new File(OriginalBackup_locationx));
                           Bukkit.getConsoleSender().sendMessage(Variable.Lang_YML.getString("SingleServerModuleAutoBackupSuccess"));
                        }
                     }
                  }
               }
            }).runTaskTimer(Main.JavaPlugin, 0L, Main.JavaPlugin.getConfig().getLong("AutoBackup") * 20L);
         } else {
            Bukkit.getConsoleSender().sendMessage(Variable.Lang_YML.getString("DisableAutoBackup"));
         }
      }

      if (!Main.JavaPlugin.getConfig().getBoolean("DisableFunctionButTeleport") && Main.JavaPlugin.getConfig().getLong("OptimizeTime") != 0L) {
         (new BukkitRunnable() {
               public void run() {
                  boolean has_been_solve = false;
                  for (World world : Bukkit.getWorlds()) {
                     if (Util.CheckIsHome(world.getName().replace(Variable.world_prefix, ""))) {
                        boolean in_whitelist = false;

                        java.util.List<String> unoptimizedWorlds = Main.JavaPlugin.getConfig().getStringList("UnOptimizeWorlds");
                        for (int i = 0; i < unoptimizedWorlds.size(); i++) {
                           if ((unoptimizedWorlds.get(i))
                              .equalsIgnoreCase(world.getName().replace(Variable.world_prefix, ""))) {
                              in_whitelist = true;
                              break;
                           }
                        }

                        if (!in_whitelist) {
                           if (Main.JavaPlugin.getConfig().getInt("OptimizeType") == 1) {
                              if (world.getPlayers().size() == 0) {
                                 has_been_solve = true;
                                 Bukkit.unloadWorld(world, true);
                              }
                           } else if (Main.JavaPlugin.getConfig().getInt("OptimizeType") == 2) {
                              for (Chunk temp_chunk : world.getLoadedChunks()) {
                                 boolean check_player = false;
                                 boolean check_cable = false;
                                 for (BlockState bs : temp_chunk.getTileEntities()) {
                                    try {
                                       if (Util.getNBTString(bs).toUpperCase().contains("IC2:CABLE")) {
                                          check_cable = true;
                                          break;
                                       }
                                    } catch (NoClassDefFoundError absent) {
                                       check_cable = false;
                                    }
                                 }

                                 Entity[] arrayOfEntity;
                                 int ke = (arrayOfEntity = temp_chunk.getEntities()).length;

                                  int b1 = 0;
                                 while (b1 < ke) {
                                    Entity ee = arrayOfEntity[b1];
                                    if (ee instanceof Player) {
                                       check_player = true;
                                       break;
                                    }

                                    b1++;
                                 }

                                 if (!check_player && !check_cable) {
                                    has_been_solve = true;
                                    temp_chunk.unload(true);
                                 }
                              }
                           }
                        }
                     }
                  }

                  if (has_been_solve) {
                     if (Main.JavaPlugin.getConfig().getInt("OptimizeType") == 1) {
                        Bukkit.getConsoleSender().sendMessage(Variable.Lang_YML.getString("OptimizeTypeOne"));
                     } else if (Main.JavaPlugin.getConfig().getInt("OptimizeType") == 2) {
                        Bukkit.getConsoleSender().sendMessage(Variable.Lang_YML.getString("OptimizeTypeTwo"));
                     }
                  }
               }
            })
            .runTaskTimer(Main.JavaPlugin, 0L, Main.JavaPlugin.getConfig().getLong("OptimizeTime") * 20L);
      }

      if (!Main.JavaPlugin.getConfig().getBoolean("DisableFunctionButTeleport")) {
         if (Main.JavaPlugin.getConfig().getLong("CheckTime") != 0L) {
            Bukkit.getConsoleSender().sendMessage(Variable.Lang_YML.getString("EnableHomeTileCheck"));
            (new BukkitRunnable() {
               int i = 0;

               public void run() {
                  List<String> WarnList = new ArrayList<>();
                  List<String> UnLoadList = new ArrayList<>();
                  String WarnStr = "";
                  String UnLoadStr = "";
                  for (StaticsTick st : Variable.world_StaticsTick) {
                     World world = Bukkit.getWorld(Variable.world_prefix + st.name);
                     int tiles = st.tile;
                     if (tiles >= Main.JavaPlugin.getConfig().getInt("UnLoadTiles")) {
                        this.i++;
                        UnLoadList.add(world.getName().replace(Variable.world_prefix, ""));
                        if (world.getName() != null) {
                           for (Player p : world.getPlayers()) {
                              p.teleport(Bukkit.getWorld("world").getSpawnLocation());
                              Bukkit.getConsoleSender().sendMessage(Variable.Lang_YML.getString("PlayerBeKickedByBanHome"));
                           }

                           Bukkit.unloadWorld(world.getName().replace(Variable.world_prefix, ""), true);
                        }
                     } else if (tiles >= Main.JavaPlugin.getConfig().getInt("MaxTiles")) {
                        this.i++;
                        WarnList.add(world.getName().replace(Variable.world_prefix, ""));
                     }
                  }

                  if (WarnList.size() != 0 || UnLoadList.size() != 0) {
                     if (Main.JavaPlugin.getConfig().getBoolean("CheckTipToAllPlayers")) {
                        Bukkit.broadcastMessage("§a§l§m--------------" + Variable.Prefix + "§a§l§m--------------");
                     } else {
                        Bukkit.getConsoleSender().sendMessage("§a§l§m--------------" + Variable.Prefix + "§a§l§m--------------");
                     }

                     if (Main.JavaPlugin.getConfig().getBoolean("CheckTipToAllPlayers")) {
                        if (WarnList.size() != 0) {
                           for (String message : Variable.Lang_YML.getStringList("WarnLanguage")) {
                              if (message.contains("<WarnList>")) {
                                 message = message.replace("<WarnList>", WarnList.toString());
                              }

                              Bukkit.broadcastMessage(message);
                           }
                        }

                        if (UnLoadList.size() != 0) {
                           for (String message : Variable.Lang_YML.getStringList("UnLoadLanguage")) {
                              if (message.contains("<UnLoadList>")) {
                                 message = message.replace("<UnLoadList>", UnLoadList.toString());
                              }

                              Bukkit.broadcastMessage(message);
                           }
                        }
                     } else {
                        if (WarnList.size() != 0) {
                           for (String message : Variable.Lang_YML.getStringList("WarnLanguage")) {
                              if (message.contains("<WarnList>")) {
                                 message = message.replace("<WarnList>", WarnList.toString());
                              }

                              Bukkit.getConsoleSender().sendMessage(message);
                           }
                        }

                        if (UnLoadList.size() != 0) {
                           for (String message : Variable.Lang_YML.getStringList("UnLoadLanguage")) {
                              if (message.contains("<UnLoadList>")) {
                                 message = message.replace("<UnLoadList>", UnLoadList.toString());
                              }

                              Bukkit.getConsoleSender().sendMessage(message);
                           }
                        }
                     }

                     if (Main.JavaPlugin.getConfig().getBoolean("CheckTipToAllPlayers")) {
                        Bukkit.broadcastMessage("§a§l§m--------------" + Variable.Prefix + "§a§l§m--------------");
                     } else {
                        Bukkit.getConsoleSender().sendMessage("§a§l§m--------------" + Variable.Prefix + "§a§l§m--------------");
                     }
                  }
               }
            }).runTaskTimer(Main.JavaPlugin, 0L, Main.JavaPlugin.getConfig().getLong("CheckTime") * 20L);
         }

         if (!Main.JavaPlugin.getConfig().getBoolean("DisableFunctionButTeleport")) {
            (new BukkitRunnable() {
               public void run() {
                  for (World temp : Bukkit.getWorlds()) {
                     String check_world_is_home = temp.getName().replace(Variable.world_prefix, "");
                     if (Util.CheckIsHome(check_world_is_home)) {
                        Integer Amount = 0;
                        Boolean Check = false;
                        Integer Del = 0;
                        for (Entity entity : temp.getEntities()) {
                           if (entity instanceof LivingEntity) {
                              boolean check_white = false;
                              for (String white : Main.JavaPlugin.getConfig().getStringList("WhiteEntities")) {
                                 if (white.equalsIgnoreCase(entity.getType().toString())) {
                                    check_white = true;
                                    break;
                                 }
                              }

                              if (!check_white) {
                                 Amount = Amount + 1;
                                 if (Amount > Main.JavaPlugin.getConfig().getInt("DeleteEntities") && !(entity instanceof Player)) {
                                    entity.remove();
                                    Check = true;
                                    Del = Del + 1;
                                 }
                              }
                           }
                        }

                        if (Check) {
                           String temp5 = Variable.Lang_YML.getString("ClearEntity");
                           if (temp5.contains("<Name>")) {
                              temp5 = temp5.replace("<Name>", temp.getName());
                           }

                           if (temp5.contains("<Amount>")) {
                              temp5 = temp5.replace("<Amount>", String.valueOf(Del));
                           }

                           Bukkit.broadcastMessage(temp5);
                        }
                     }
                  }
               }
            }).runTaskTimer(Main.JavaPlugin, 0L, Main.JavaPlugin.getConfig().getLong("CheckTime") * 20L);
         }

         if (!Main.JavaPlugin.getConfig().getBoolean("DisableFunctionButTeleport")) {
            (new BukkitRunnable() {
               public void run() {
                  for (World temp : Bukkit.getWorlds()) {
                     String check_world_is_home = temp.getName().replace(Variable.world_prefix, "");
                     if (Util.CheckIsHome(check_world_is_home)) {
                        Integer Amount = 0;
                        Boolean Check = false;
                        Integer Del = 0;
                        for (Entity entity : temp.getEntities()) {
                           if (BukkitCompat.isDroppedItem(entity)) {
                              Amount = Amount + 1;
                              if (Amount > Main.JavaPlugin.getConfig().getInt("DeleteItems")) {
                                 Check = true;
                                 Del = Del + 1;
                              }
                           }
                        }

                        if (Check) {
                           String temp5 = Variable.Lang_YML.getString("ClearDropItems");
                           if (temp5.contains("<Name>")) {
                              temp5 = temp5.replace("<Name>", temp.getName());
                           }

                           if (temp5.contains("<Amount>")) {
                              temp5 = temp5.replace("<Amount>", String.valueOf(Del));
                           }

                           Bukkit.broadcastMessage(temp5);
                        }
                     }
                  }
               }
            }).runTaskTimer(Main.JavaPlugin, 0L, Main.JavaPlugin.getConfig().getLong("CheckTime") * 20L);
         } else {
            Bukkit.getConsoleSender().sendMessage(Variable.Lang_YML.getString("DisableHomeTileCheck"));
         }
      }

      if (!Main.JavaPlugin.getConfig().getBoolean("DisableFunctionButTeleport")) {
         (new BukkitRunnable() {
            public void run() {
               if (!Main.JavaPlugin.getConfig().getBoolean("EnableTimeLock")) {
                  this.cancel();
               } else {
                  if (Variable.bungee) {
                     for (String worldname : MySQL.getAllWorlds()) {
                        if (Bukkit.getWorld(Variable.world_prefix + worldname) != null) {
                           World world = Bukkit.getWorld(Variable.world_prefix + worldname);
                           if (MySQL.getlocktime(worldname).equalsIgnoreCase("true")) {
                              world.setTime(Long.valueOf(MySQL.gettime(worldname)));
                           }
                        }
                     }
                  } else {
                     File folder = new File(Variable.Tempf);
                     if (folder.listFiles() == null) {
                        return;
                     }
                     for (File temp : folder.listFiles()) {
                        String want_to = temp.getPath().replace(Variable.Tempf, "").replace(".yml", "").replace(Variable.file_loc_prefix, "");
                        if (Bukkit.getWorld(Variable.world_prefix + want_to) != null) {
                           World world = Bukkit.getWorld(Variable.world_prefix + want_to);
                           YamlConfiguration yamlConfiguration = YamlConfiguration.loadConfiguration(temp);
                           if (yamlConfiguration.getBoolean("locktime")) {
                              world.setTime(yamlConfiguration.getLong("time"));
                           }
                        }
                     }
                  }
               }
            }
         }).runTaskTimer(Main.JavaPlugin, 0L, 60L);
      }

      if (!Main.JavaPlugin.getConfig().getBoolean("DisableFunctionButTeleport")) {
         (new BukkitRunnable() {
            public void run() {
               for (Player p : Bukkit.getOnlinePlayers()) {
                  if (Variable.DispathCommand.contains(p.getName())) {
                     String temp5 = Variable.Lang_YML.getString("OverSomeBorderTip");
                     if (!temp5.equalsIgnoreCase("")) {
                        p.sendMessage("§a§l§m--------------" + Variable.Prefix + "§a§l§m--------------");
                        p.sendMessage(temp5);
                        p.sendMessage("§a§l§m--------------" + Variable.Prefix + "§a§l§m--------------");
                        Bukkit.dispatchCommand(p, Main.JavaPlugin.getConfig().getString("BorderCommand"));
                        Variable.DispathCommand.remove(p.getName());
                     }
                  }
               }
            }
         }).runTaskTimer(Main.JavaPlugin, 0L, 20L);
      }

      if (!Main.JavaPlugin.getConfig().getBoolean("DisableFunctionButTeleport")) {
         (new BukkitRunnable() {
            public void run() {
               for (Player p : Bukkit.getOnlinePlayers()) {
                  if (Variable.AddDebuff.contains(p.getName())) {
                     BukkitCompat.addSlowness(p, 100, 10);
                     String temp5 = Variable.Lang_YML.getString("OverBorderTip");
                     p.sendMessage("§a§l§m--------------" + Variable.Prefix + "§a§l§m--------------");
                     p.sendMessage(temp5);
                     p.sendMessage("§a§l§m--------------" + Variable.Prefix + "§a§l§m--------------");
                     Variable.AddDebuff.remove(p.getName());
                  }
               }
            }
         }).runTaskTimer(Main.JavaPlugin, 0L, 20L);
      }

      if (!Main.JavaPlugin.getConfig().getBoolean("DisableFunctionButTeleport")) {
         (new BukkitRunnable() {
            public void run() {
               ScheduledTasks.OPS_redis.clear();
               ScheduledTasks.MEMBERS_redis.clear();
               for (World world : Bukkit.getWorlds()) {
                  Home temp_home = HomeAPI.getHome(world.getName());
                  if (temp_home != null) {
                     ScheduledTasks.OPS_redis.put(world.getName().replace(Variable.world_prefix, ""), temp_home.getOPs());
                     ScheduledTasks.MEMBERS_redis.put(world.getName().replace(Variable.world_prefix, ""), temp_home.getMembers());
                  }
               }
            }
         }).runTaskTimer(Main.JavaPlugin, 0L, 60L);
      }

      if (!Main.JavaPlugin.getConfig().getBoolean("DisableFunctionButTeleport")) {
         if (Main.JavaPlugin.getConfig().getBoolean("CustomTileMax")) {
            Bukkit.getConsoleSender().sendMessage(Variable.Lang_YML.getString("EnableCustomTileMaxFunction"));
         } else {
            Bukkit.getConsoleSender().sendMessage(Variable.Lang_YML.getString("DisableCustomTileMaxFunction"));
         }

         if (Main.JavaPlugin.getConfig().getBoolean("EnableBlackEntities")) {
            Bukkit.getConsoleSender().sendMessage(Variable.Lang_YML.getString("EnableBlackEntitiesFunction"));
         } else {
            Bukkit.getConsoleSender().sendMessage(Variable.Lang_YML.getString("DisableBlackEntitiesFunction"));
         }

         if (!Main.JavaPlugin.getConfig().getString("CustomBorder").equalsIgnoreCase("")) {
            Bukkit.getConsoleSender().sendMessage(Variable.Lang_YML.getString("EnableCustomBorder"));
         }

         if (!Main.JavaPlugin.getConfig().getBoolean("KeepInventory")) {
            Bukkit.getConsoleSender().sendMessage(Variable.Lang_YML.getString("DisableWholeKeepInventory"));
         } else if (Main.JavaPlugin.getConfig().getBoolean("KeepInventory")) {
            Bukkit.getConsoleSender().sendMessage(Variable.Lang_YML.getString("EnableWholeKeepInventory"));
         }

         if (!Main.JavaPlugin.getConfig().getBoolean("doMobSpawning")) {
            Bukkit.getConsoleSender().sendMessage(Variable.Lang_YML.getString("DisableMobSpawning"));
         } else if (Main.JavaPlugin.getConfig().getBoolean("doMobSpawning")) {
            Bukkit.getConsoleSender().sendMessage(Variable.Lang_YML.getString("EnabledoMobSpawning"));
         }

         if (!Main.JavaPlugin.getConfig().getBoolean("mobGriefing")) {
            Bukkit.getConsoleSender().sendMessage(Variable.Lang_YML.getString("DisablemobGriefing"));
         } else if (Main.JavaPlugin.getConfig().getBoolean("mobGriefing")) {
            Bukkit.getConsoleSender().sendMessage(Variable.Lang_YML.getString("EnablemobGriefing"));
         }

         if (!Main.JavaPlugin.getConfig().getBoolean("doFireTick")) {
            Bukkit.getConsoleSender().sendMessage(Variable.Lang_YML.getString("DisabledoFireTick"));
         } else if (Main.JavaPlugin.getConfig().getBoolean("doFireTick")) {
            Bukkit.getConsoleSender().sendMessage(Variable.Lang_YML.getString("EnabledoFireTick"));
         }
      }
   }
}
