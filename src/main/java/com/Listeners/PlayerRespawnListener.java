package com.Listeners;

import com.ErrorTown.Main;
import com.ErrorTown.Variable;
import com.Util.MySQL;
import com.Util.Util;
import java.io.File;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.scheduler.BukkitRunnable;

public class PlayerRespawnListener implements Listener {
   @EventHandler
   public void onSpawn(final PlayerRespawnEvent event) {
      if (Main.JavaPlugin.getConfig().getBoolean("EnableAutoRespawnInHome")) {
         if (Variable.wait_to_spawn_home.containsKey(event.getPlayer().getName())) {
            String worldname = Variable.wait_to_spawn_home.get(event.getPlayer().getName()).replace(Variable.world_prefix, "");
            String baseHomeName = Util.getBaseHomeName(worldname);
            World homeWorld = Bukkit.getWorld(Variable.world_prefix + baseHomeName);
            if (homeWorld == null) {
               Variable.wait_to_spawn_home.remove(event.getPlayer().getName());
            } else {
               final Location loc = homeWorld.getSpawnLocation();
               if (Variable.bungee) {
                  Double X = Double.valueOf(MySQL.getX(baseHomeName));
                  Double Y = Double.valueOf(MySQL.getY(baseHomeName));
                  Double Z = Double.valueOf(MySQL.getZ(baseHomeName));
                  loc.setX(X);
                  loc.setY(Y);
                  loc.setZ(Z);
                  event.setRespawnLocation(loc);
                  (new BukkitRunnable() {
                     public void run() {
                        event.getPlayer().teleport(loc);
                     }
                  }).runTaskLater(Main.JavaPlugin, 5L);
               } else {
                  File f2 = new File(Variable.Tempf, baseHomeName + ".yml");
                  YamlConfiguration yamlConfiguration = YamlConfiguration.loadConfiguration(f2);
                  loc.setX(yamlConfiguration.getDouble("X"));
                  loc.setY(yamlConfiguration.getDouble("Y"));
                  loc.setZ(yamlConfiguration.getDouble("Z"));
                  event.setRespawnLocation(loc);
                  (new BukkitRunnable() {
                     public void run() {
                        event.getPlayer().teleport(loc);
                     }
                  }).runTaskLater(Main.JavaPlugin, 5L);
               }

               Variable.wait_to_spawn_home.remove(event.getPlayer().getName());
            }
         }
      }
   }
}
