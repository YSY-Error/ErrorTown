package com.Listeners;

import com.ErrorTown.Main;
import com.ErrorTown.Variable;
import com.Util.Home;
import com.Util.HomeAPI;
import com.Util.HomeSpawnUtil;
import com.Util.HomeWorldManager;
import com.Util.HomeCreationCoordinator;
import com.Util.HomeTerrainPolicy;
import com.Util.MySQL;
import com.Util.Platform;
import com.Util.Util;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.scheduler.BukkitRunnable;

public class PlayerTeleportListener implements Listener {
   int vip_add = 0;
   public static java.util.Map<String, Integer> border_redis = com.Util.Util.boundedCache(2048);

   @EventHandler(
      priority = EventPriority.HIGHEST,
      ignoreCancelled = false
   )
   public void onTeleport(PlayerTeleportEvent event) throws IOException {
      final World world = event.getTo().getWorld();
      String targetBase = Util.getBaseHomeName(world.getName());
      if (Main.JavaPlugin.getConfig().getBoolean("HomeTerrain.Enabled", false)
         && Util.isManagedHomeWorld(world.getName())
         && HomeCreationCoordinator.isPending(targetBase)) {
         event.setCancelled(true);
         event.getPlayer().sendMessage("§8[§6错误庄园§8] §e该家园仍在生成自然地形，请等待创建完成。");
         return;
      }
      if (Main.JavaPlugin.getConfig().getBoolean("HomeTravel.AllowNetherEnd", false) == false
         && Util.isManagedHomeWorld(event.getFrom().getWorld().getName())
         && (world.getEnvironment() == World.Environment.NETHER || world.getEnvironment() == World.Environment.THE_END)) {
         event.setCancelled(true);
         event.getPlayer().sendMessage("§8[§6错误庄园§8] §c家园世界禁止前往下界和末地。");
         return;
      }
      if (Variable.flying_list.containsKey(event.getPlayer().getName())) {
         String world_to = event.getTo().getWorld().getName();
         if (!Variable.flying_list.get(event.getPlayer().getName()).equalsIgnoreCase(world_to)) {
            if (event.getPlayer().getAllowFlight()) {
               event.getPlayer().setAllowFlight(false);
               event.getPlayer().sendMessage(Variable.Lang_YML.getString("ToggleccWorldDisableFlying"));
            }

            Variable.flying_list.remove(event.getPlayer().getName());
         }
      }

      if (Util.isManagedHomeWorld(event.getTo().getWorld().getName())) {
         String targetBaseHomeName = Util.getBaseHomeName(event.getTo().getWorld().getName());
         Home h = HomeAPI.getHome(targetBaseHomeName);
         if (h != null) {
            this.vip_add = 0;
            ArrayList<String> players = new ArrayList<>();
            players.add(h.getName());

            for (String op : h.getOPs()) {
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
                     if (add > this.vip_add) {
                        this.vip_add = add;
                     }
                  }
               }
            }

            if (border_redis.containsKey(h.getName())) {
               if (this.vip_add < border_redis.get(h.getName())) {
                  this.vip_add = border_redis.get(h.getName());
               } else {
                  border_redis.put(h.getName(), this.vip_add);
               }
            } else {
               border_redis.put(h.getName(), this.vip_add);
            }

            if (!Main.JavaPlugin.getConfig().getBoolean("KeepInventory")) {
               Platform.setGameRule(event.getTo().getWorld(), "keepInventory", "false");
            } else if (Main.JavaPlugin.getConfig().getBoolean("KeepInventory")) {
               Platform.setGameRule(event.getTo().getWorld(), "keepInventory", "true");
            }

            try {
               HomeWorldManager.markActive(targetBaseHomeName);
               HomeWorldManager.cancelUnload(targetBaseHomeName);
               Util.applyHomeWorldRules(event.getTo().getWorld(), h);
            } catch (Exception failure) {
               com.Util.Diag.warnOnce("tp-apply-rules", "Could not apply home rules on teleport into " + targetBaseHomeName, failure);
            }

            final String name = world.getName().replace(Variable.world_prefix, "");
            Player p = event.getPlayer();
            if (name.equalsIgnoreCase(p.getName())) {
               int set_level = 1;

               for (int i = Main.JavaPlugin.getConfig().getInt("MaxLevel"); i > 0; i--) {
                  if (com.Util.Perm.has(p, "ErrorTown.Level." + i)) {
                     set_level = i;
                     break;
                  }
               }

               if (Variable.bungee) {
                  if (Integer.valueOf(MySQL.getLevel(name)) < set_level) {
                     MySQL.setLevel(name, String.valueOf(set_level));
                     String temp = Variable.Lang_YML.getString("AutoUpdateHomeLevel");
                     p.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                     p.sendMessage(temp);
                     p.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
                  }
               } else {
                  File f2 = new File(Variable.Tempf, name + ".yml");
                  YamlConfiguration yml = YamlConfiguration.loadConfiguration(f2);
                  if (yml.getInt("Level") < set_level) {
                     yml.set("Level", set_level);

                     try {
                        yml.save(f2);
                     } catch (IOException ioFailure) {
                        ioFailure.printStackTrace();
                     }

                     String temp = Variable.Lang_YML.getString("AutoUpdateHomeLevel");
                     p.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                     p.sendMessage(temp);
                     p.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
                  }
               }
            }

            if (Variable.bungee) {
               if (MySQL.CheckIsAHome(name)) {
                  Util.refreshBorder(event.getTo().getWorld());
                  if (!Variable.KeepWorlds.contains(name)) {
                     Variable.KeepWorlds.add(name);
                  }

                  if (!Util.CanEnterHome(event.getPlayer(), event.getTo().getWorld().getName().replace(Variable.world_prefix, ""))
                     && !com.Util.Perm.has(event.getPlayer(), "ErrorTown.forcetp")) {
                     p.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                     String temp = Variable.Lang_YML.getString("TeleportStranger");
                     p.sendMessage(temp);
                     p.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
                     event.setCancelled(true);
                     return;
                  }

                  if (Util.CheckBlack(event.getPlayer(), event.getTo().getWorld().getName().replace(Variable.world_prefix, ""))
                     && !event.getPlayer().isOp()
                     && !com.Util.Perm.has(event.getPlayer(), "ErrorTown.forcetp")) {
                     String temp = Variable.Lang_YML.getString("TeleportInBlack");
                     p.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                     p.sendMessage(temp);
                     p.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
                     event.setCancelled(true);
                     return;
                  }

                  if (!event.getTo()
                     .getWorld()
                     .getName()
                     .replace(Variable.world_prefix, "")
                     .equalsIgnoreCase(event.getFrom().getWorld().getName().replace(Variable.world_prefix, ""))) {
                     String _toWorldBase = Util.getBaseHomeName(event.getTo().getWorld().getName());
                     boolean _isOwnerOrMember = Util.CheckOwnerAndManagerAndOP(p, _toWorldBase)
                        || Util.Check(p, _toWorldBase) && Util.isHomeOwnerOnline(_toWorldBase);
                     if (_isOwnerOrMember) {
                        String _welcomeMsg = Variable.Lang_YML.getString("OwnerEnterHome");
                        p.sendMessage(_welcomeMsg != null ? _welcomeMsg : "§8[§6错误庄园§8] §a少爷，欢迎回家！");
                     } else {
                        String temp = Variable.Lang_YML.getString("TeleportTip");
                        if (temp != null && temp.contains("<Name>")) {
                           temp = temp.replace("<Name>", event.getTo().getWorld().getName().replace(Variable.world_prefix, ""));
                        }

                        if (temp != null) {
                           p.sendMessage(temp);
                        }
                     }

                     try {
                        Home infoHome = HomeAPI.getHome(_toWorldBase);
                        if (infoHome != null) {
                           HomeWorldManager.showHomeInfo(p, infoHome, event.getTo().getWorld());
                        }
                     } catch (Exception failure) {
                        com.Util.Diag.warnOnce("tp-info-panel", "Could not show the home info panel", failure);
                     }

                     if (!event.getPlayer().getName().equalsIgnoreCase(event.getTo().getWorld().getName().replace(Variable.world_prefix, ""))
                        && com.Util.Perm.has(event.getPlayer(), "ErrorTown.Popularity")) {
                        if (event.getPlayer().isOp()) {
                           event.getPlayer().sendMessage(Variable.Lang_YML.getString("GivePopularityButOP"));
                        } else if (Variable.popularity_list.containsKey(event.getPlayer().getName())) {
                           List<String> list = Variable.popularity_list.get(event.getPlayer().getName());
                           boolean has_vote = false;

                           for (int ix = 0; ix < list.size(); ix++) {
                              if (list.get(ix).equalsIgnoreCase(event.getTo().getWorld().getName().replace(Variable.world_prefix, ""))) {
                                 has_vote = true;
                                 break;
                              }
                           }

                           if (!has_vote) {
                              list.add(event.getTo().getWorld().getName().replace(Variable.world_prefix, ""));
                              Variable.popularity_list.put(event.getPlayer().getName(), list);
                              Home home = HomeAPI.getHome(event.getTo().getWorld().getName().replace(Variable.world_prefix, ""));
                              home.setPopularity(home.getPopularity() + 1);
                              String temp4 = Variable.Lang_YML.getString("PopularityAdd");
                              if (temp4.contains("<Name>")) {
                                 temp4 = temp4.replace("<Name>", event.getTo().getWorld().getName().replace(Variable.world_prefix, ""));
                              }

                              event.getPlayer().sendMessage(temp4);
                              String temp2 = Variable.Lang_YML.getString("PopularityAddToOwnerAndOP");
                              if (temp2.contains("<Player>")) {
                                 temp2 = temp2.replace("<Player>", event.getPlayer().getName());
                              }

                              for (String s : home.getOPs()) {
                                 if (Bukkit.getPlayer(s) != null) {
                                    Bukkit.getPlayer(temp2);
                                 }
                              }

                              if (Bukkit.getPlayer(home.getName()) != null) {
                                 Bukkit.getPlayer(home.getName()).sendMessage(temp2);
                              }
                           }
                        } else {
                           List<String> list = new ArrayList<>();
                           list.add(event.getTo().getWorld().getName().replace(Variable.world_prefix, ""));
                           Variable.popularity_list.put(event.getPlayer().getName(), list);
                           Home homex = HomeAPI.getHome(event.getTo().getWorld().getName().replace(Variable.world_prefix, ""));
                           homex.setPopularity(1);
                           String temp3 = Variable.Lang_YML.getString("PopularityAdd");
                           if (temp3.contains("<Name>")) {
                              temp3 = temp3.replace("<Name>", event.getTo().getWorld().getName().replace(Variable.world_prefix, ""));
                           }

                           event.getPlayer().sendMessage(temp3);
                           String temp2x = Variable.Lang_YML.getString("PopularityAddToOwnerAndOP");
                           if (temp2x.contains("<Player>")) {
                              temp2x = temp2x.replace("<Player>", event.getPlayer().getName());
                           }

                           for (String sx : homex.getOPs()) {
                              if (Bukkit.getPlayer(sx) != null) {
                                 Bukkit.getPlayer(temp2x);
                              }
                           }

                           if (Bukkit.getPlayer(homex.getName()) != null) {
                              Bukkit.getPlayer(homex.getName()).sendMessage(temp2x);
                           }
                        }
                     }
                  }

                  MySQL.setVisitTime(event.getTo().getWorld().getName().replace(Variable.world_prefix, ""), String.valueOf(System.currentTimeMillis()));
                  if (Main.JavaPlugin.getConfig().getInt("MaxSpawnMonstersAmount") != -1) {
                     world.setMonsterSpawnLimit(Main.JavaPlugin.getConfig().getInt("MaxSpawnMonstersAmount"));
                  }

                  if (Main.JavaPlugin.getConfig().getInt("MaxSpawnAnimalsAmount") != -1) {
                     world.setAnimalSpawnLimit(Main.JavaPlugin.getConfig().getInt("MaxSpawnAnimalsAmount"));
                  }

                  HomeSpawnUtil.applyHomeSpawnCompensation(world);
                  boolean openborder = false;
                  if (Main.JavaPlugin.getConfig().getString("CustomBorder") != null) {
                     String tempx = Main.JavaPlugin.getConfig().getString("CustomBorder");
                     if (!tempx.equalsIgnoreCase("")) {
                        if (tempx.contains("<Radius>")) {
                           tempx = tempx.replace(
                              "<Radius>",
                              String.valueOf(
                                 HomeTerrainPolicy.configuredBorderSize(
                                       Integer.valueOf(MySQL.getLevel(name)),
                                       Main.JavaPlugin.getConfig().getBoolean("HomeTerrain.Enabled", false),
                                       Main.JavaPlugin.getConfig().getIntegerList("HomeUpgrade.LevelSizes"),
                                       Main.JavaPlugin.getConfig().getInt("WorldBoard"),
                                       Main.JavaPlugin.getConfig().getInt("UpdateRadius"),
                                       PlayerTeleportListener.this.vip_add
                                    ) / 2
                              )
                           );
                        }

                        if (tempx.contains("<Player>")) {
                           tempx = tempx.replace("<Player>", event.getTo().getWorld().getName().replace(Variable.world_prefix, ""));
                        }

                        if (tempx.contains("<X>")) {
                           tempx = tempx.replace("<X>", String.valueOf(world.getSpawnLocation().getX()));
                        }

                        if (tempx.contains("<Y>")) {
                           tempx = tempx.replace("<Y>", String.valueOf(world.getSpawnLocation().getY()));
                        }

                        if (tempx.contains("<Z>")) {
                           tempx = tempx.replace("<Z>", String.valueOf(world.getSpawnLocation().getZ()));
                        }

                        openborder = true;
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), tempx);
                     }
                  }

                  if (!openborder) {
                     (new BukkitRunnable() {
                           public void run() {
                              if (Main.JavaPlugin.getConfig().getBoolean("BorderSwitch")) {
                                 try {
                                    world.getWorldBorder().setCenter(world.getSpawnLocation());
                                    world.getWorldBorder()
                                        .setSize(HomeTerrainPolicy.configuredBorderSize(
                                           Integer.valueOf(MySQL.getLevel(name)),
                                           Main.JavaPlugin.getConfig().getBoolean("HomeTerrain.Enabled", false),
                                           Main.JavaPlugin.getConfig().getIntegerList("HomeUpgrade.LevelSizes"),
                                           Main.JavaPlugin.getConfig().getInt("WorldBoard"),
                                           Main.JavaPlugin.getConfig().getInt("UpdateRadius"),
                                           PlayerTeleportListener.this.vip_add
                                        ));
                                 } catch (NoSuchMethodError unsupported) {
                                    Bukkit.getConsoleSender().sendMessage(Variable.Lang_YML.getString("BorderException"));
                                 }
                              }
                           }
                        })
                        .runTaskLater(Main.JavaPlugin, 5L);
                  }
               }
            } else {
               File f2 = new File(Variable.Tempf, name + ".yml");
               if (f2.exists()) {
                  Util.refreshBorder(event.getTo().getWorld());
                  if (!Variable.KeepWorlds.contains(name)) {
                     Variable.KeepWorlds.add(name);
                  }

                  final YamlConfiguration yml = YamlConfiguration.loadConfiguration(f2);
                  if (yml.getDouble("X") == 0.0) {
                     yml.set("X", event.getTo().getWorld().getSpawnLocation().getX());
                     yml.save(f2);
                  }

                  if (yml.getDouble("Y") == 0.0) {
                     yml.set("Y", event.getTo().getWorld().getSpawnLocation().getY());
                     yml.save(f2);
                  }

                  if (yml.getDouble("Z") == 0.0) {
                     yml.set("Z", event.getTo().getWorld().getSpawnLocation().getZ());
                     yml.save(f2);
                  }

                  if (!Util.CanEnterHome(event.getPlayer(), event.getTo().getWorld().getName().replace(Variable.world_prefix, ""))
                     && !com.Util.Perm.has(event.getPlayer(), "ErrorTown.forcetp")
                     && !event.getPlayer().isOp()) {
                     p.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                     String tempx = Variable.Lang_YML.getString("TeleportStranger");
                     p.sendMessage(tempx);
                     p.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
                     event.setCancelled(true);
                     return;
                  }

                  if (Util.CheckBlack(event.getPlayer(), event.getTo().getWorld().getName().replace(Variable.world_prefix, ""))
                     && !event.getPlayer().isOp()
                     && !com.Util.Perm.has(event.getPlayer(), "ErrorTown.forcetp")) {
                     String tempx = Variable.Lang_YML.getString("TeleportInBlack");
                     p.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                     p.sendMessage(tempx);
                     p.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
                     event.setCancelled(true);
                     return;
                  }

                  if (!event.getTo()
                     .getWorld()
                     .getName()
                     .replace(Variable.world_prefix, "")
                     .equalsIgnoreCase(event.getFrom().getWorld().getName().replace(Variable.world_prefix, ""))) {
                     String _toWorldBase2 = Util.getBaseHomeName(event.getTo().getWorld().getName());
                     boolean _isOwnerOrMember2 = Util.CheckOwnerAndManagerAndOP(p, _toWorldBase2)
                        || Util.Check(p, _toWorldBase2) && Util.isHomeOwnerOnline(_toWorldBase2);
                     if (_isOwnerOrMember2) {
                        String _welcomeMsg2 = Variable.Lang_YML.getString("OwnerEnterHome");
                        p.sendMessage(_welcomeMsg2 != null ? _welcomeMsg2 : "§8[§6错误庄园§8] §a少爷，欢迎回家！");
                     } else {
                        String tempx = Variable.Lang_YML.getString("TeleportTip");
                        if (tempx != null && tempx.contains("<Name>")) {
                           tempx = tempx.replace("<Name>", event.getTo().getWorld().getName().replace(Variable.world_prefix, ""));
                        }

                        if (tempx != null) {
                           p.sendMessage(tempx);
                        }
                     }
                  }

                  if (Main.JavaPlugin.getConfig().getInt("MaxSpawnMonstersAmount") != -1) {
                     world.setMonsterSpawnLimit(Main.JavaPlugin.getConfig().getInt("MaxSpawnMonstersAmount"));
                  }

                  if (Main.JavaPlugin.getConfig().getInt("MaxSpawnAnimalsAmount") != -1) {
                     world.setAnimalSpawnLimit(Main.JavaPlugin.getConfig().getInt("MaxSpawnAnimalsAmount"));
                  }

                  HomeSpawnUtil.applyHomeSpawnCompensation(world);
                  boolean openborderx = false;
                  if (Main.JavaPlugin.getConfig().getString("CustomBorder") != null) {
                     String customBorderSpec = Main.JavaPlugin.getConfig().getString("CustomBorder");
                     if (!customBorderSpec.equalsIgnoreCase("")) {
                        if (customBorderSpec.contains("<Radius>")) {
                           customBorderSpec = customBorderSpec.replace(
                              "<Radius>",
                              String.valueOf(
                                 HomeTerrainPolicy.configuredBorderSize(
                                       yml.getInt("Level"),
                                       Main.JavaPlugin.getConfig().getBoolean("HomeTerrain.Enabled", false),
                                       Main.JavaPlugin.getConfig().getIntegerList("HomeUpgrade.LevelSizes"),
                                       Main.JavaPlugin.getConfig().getInt("WorldBoard"),
                                       Main.JavaPlugin.getConfig().getInt("UpdateRadius"),
                                       this.vip_add
                                    ) / 2
                              )
                           );
                        }

                        if (customBorderSpec.contains("<Player>")) {
                           customBorderSpec = customBorderSpec.replace("<Player>", event.getTo().getWorld().getName().replace(Variable.world_prefix, ""));
                        }

                        if (customBorderSpec.contains("<X>")) {
                           customBorderSpec = customBorderSpec.replace("<X>", String.valueOf(world.getSpawnLocation().getX()));
                        }

                        if (customBorderSpec.contains("<Y>")) {
                           customBorderSpec = customBorderSpec.replace("<Y>", String.valueOf(world.getSpawnLocation().getY()));
                        }

                        if (customBorderSpec.contains("<Z>")) {
                           customBorderSpec = customBorderSpec.replace("<Z>", String.valueOf(world.getSpawnLocation().getZ()));
                        }

                        openborderx = true;
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), customBorderSpec);
                     }
                  }

                  try {
                     yml.save(f2);
                  } catch (IOException ioFailure) {
                     ioFailure.printStackTrace();
                  }

                  if (!openborderx) {
                     (new BukkitRunnable() {
                           public void run() {
                              if (Main.JavaPlugin.getConfig().getBoolean("BorderSwitch")) {
                                 try {
                                    world.getWorldBorder().setCenter(world.getSpawnLocation());
                                    world.getWorldBorder()
                                        .setSize(HomeTerrainPolicy.configuredBorderSize(
                                           yml.getInt("Level"),
                                           Main.JavaPlugin.getConfig().getBoolean("HomeTerrain.Enabled", false),
                                           Main.JavaPlugin.getConfig().getIntegerList("HomeUpgrade.LevelSizes"),
                                           Main.JavaPlugin.getConfig().getInt("WorldBoard"),
                                           Main.JavaPlugin.getConfig().getInt("UpdateRadius"),
                                           PlayerTeleportListener.this.vip_add
                                        ));
                                 } catch (NoSuchMethodError unsupported) {
                                    Bukkit.getConsoleSender().sendMessage(Variable.Lang_YML.getString("BorderException"));
                                 }
                              }
                           }
                        })
                        .runTaskLater(Main.JavaPlugin, 5L);
                  }
               }
            }
         }
      }
   }
}
