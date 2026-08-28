package com.Listeners;

import com.ErrorTown.Main;
import com.ErrorTown.Variable;
import com.Util.Home;
import com.Util.HomeAPI;
import com.Util.HomeTerrainPolicy;
import com.Util.MySQL;
import com.Util.Util;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

public class PlayerMoveListener implements Listener {
   int addExtra = 0;
   public static java.util.Map<String, Integer> border_redis = com.Util.Util.boundedCache(2048);

   @EventHandler
   public void onMove(final PlayerMoveEvent event) {
      (new BukkitRunnable() {
            public void run() {
               final Player p = event.getPlayer();
               if (!p.isOp()) {
                  if (Util.CheckIsHome(p.getWorld().getName().replace(Variable.world_prefix, ""))) {
                     PlayerMoveListener.this.addExtra = 0;
                     Home h = HomeAPI.getHome(event.getTo().getWorld().getName());
                     int vip_add = 0;
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
                              if (add > vip_add) {
                                 vip_add = add;
                              }
                           }
                        }
                     }

                     PlayerMoveListener.this.addExtra = vip_add;
                     if (PlayerMoveListener.border_redis.containsKey(h.getName())) {
                        if (PlayerMoveListener.this.addExtra < PlayerMoveListener.border_redis.get(h.getName())) {
                           PlayerMoveListener.this.addExtra = PlayerMoveListener.border_redis.get(h.getName());
                        } else {
                           PlayerMoveListener.border_redis.put(h.getName(), PlayerMoveListener.this.addExtra);
                        }
                     } else {
                        PlayerMoveListener.border_redis.put(h.getName(), PlayerMoveListener.this.addExtra);
                     }

                     double set_x = 0.0;
                     double min_x = 0.0;
                     double set_z = 0.0;
                     double min_z = 0.0;
                     int level;
                     if (Variable.bungee) {
                        level = Integer.valueOf(MySQL.getLevel(p.getWorld().getName().replace(Variable.world_prefix, "")));
                     } else {
                        File f2 = new File(Variable.Tempf, p.getWorld().getName().replace(Variable.world_prefix, "") + ".yml");
                        YamlConfiguration yamlConfiguration = YamlConfiguration.loadConfiguration(f2);
                        level = yamlConfiguration.getInt("Level");
                     }
                     double halfSize = HomeTerrainPolicy.configuredBorderSize(
                        level,
                        Main.JavaPlugin.getConfig().getBoolean("HomeTerrain.Enabled", false),
                        Main.JavaPlugin.getConfig().getIntegerList("HomeUpgrade.LevelSizes"),
                        Main.JavaPlugin.getConfig().getInt("WorldBoard"),
                        Main.JavaPlugin.getConfig().getInt("UpdateRadius"),
                        PlayerMoveListener.this.addExtra
                     ) / 2.0;
                     set_x = p.getWorld().getSpawnLocation().getX() + halfSize;
                     min_x = p.getWorld().getSpawnLocation().getX() - halfSize;
                     set_z = p.getWorld().getSpawnLocation().getZ() + halfSize;
                     min_z = p.getWorld().getSpawnLocation().getZ() - halfSize;

                     if (set_x < min_x) {
                        double temp = set_x;
                        set_x = min_x;
                        min_x = temp;
                     }

                     if (set_z < min_z) {
                        double temp = set_z;
                        set_z = min_z;
                        min_z = temp;
                     }

                     if (!(p.getLocation().getX() <= min_x)
                        && !(p.getLocation().getX() >= set_x)
                        && !(p.getLocation().getZ() <= min_z)
                        && !(p.getLocation().getZ() >= set_z)) {
                        if (!(p.getLocation().getX() + 15.0 <= min_x)
                           && !(p.getLocation().getX() - 15.0 >= set_x)
                           && !(p.getLocation().getZ() + 15.0 <= min_z)
                           && !(p.getLocation().getZ() - 15.0 >= set_z)) {
                           if (p.getGameMode() == GameMode.ADVENTURE) {
                              (new BukkitRunnable() {
                                 public void run() {
                                    p.setGameMode(GameMode.SURVIVAL);
                                 }
                              }).runTask(Main.JavaPlugin);
                           }
                        } else {
                           if (Main.JavaPlugin.getConfig().getBoolean("PlayerMoveOverBorderBuff") && !Variable.AddDebuff.contains(p.getName())) {
                              Variable.AddDebuff.add(p.getName());
                           }

                           if (Main.JavaPlugin.getConfig().getBoolean("PlayerMoveOverBorderHit")) {
                              p.setVelocity(new Vector(0, 0, -3));
                           }

                           this.cancel();
                        }
                     } else {
                        if (Main.JavaPlugin.getConfig().getBoolean("EnableAdventureMode") && p.getGameMode() != GameMode.ADVENTURE) {
                           final Player _p = p;
                           (new BukkitRunnable() {
                              public void run() {
                                 if (_p.isOnline()) {
                                    _p.setGameMode(GameMode.ADVENTURE);
                                    _p.sendMessage(Variable.Lang_YML.getString("PlayerMoveOverBorderButAdventure"));
                                 }
                              }
                           }).runTask(Main.JavaPlugin);
                        }

                        if (!Main.JavaPlugin.getConfig().getString("BorderCommand").equalsIgnoreCase("") && !Variable.DispathCommand.contains(p.getName())) {
                           Variable.DispathCommand.add(p.getName());
                        }

                        if (Main.JavaPlugin.getConfig().getBoolean("PlayerMoveOverBorderBuff") && !Variable.AddDebuff.contains(p.getName())) {
                           Variable.AddDebuff.add(p.getName());
                        }

                        if (Main.JavaPlugin.getConfig().getBoolean("PlayerMoveOverBorderHit")) {
                           p.setVelocity(new Vector(0, 0, -3));
                        }

                        this.cancel();
                     }
                  }
               }
            }
         })
         .runTaskAsynchronously(Main.JavaPlugin);
   }
}
