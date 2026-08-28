package com.Listeners;

import com.ErrorTown.Main;
import com.ErrorTown.Variable;
import com.Util.CustomChunkGenerator;
import com.Util.Home;
import com.Util.HomeAPI;
import com.Util.Platform;
import com.Util.Util;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldInitEvent;
import org.bukkit.scheduler.BukkitRunnable;

public class WorldInitListener implements Listener {
   @EventHandler
   public void onInit(WorldInitEvent event) {
      boolean check_false_core = false;
      if (Bukkit.getVersion().toString().contains("1.7.10")
         || Bukkit.getVersion().toString().contains("Paper")
         || Bukkit.getVersion().toString().contains("Purpur")) {
         check_false_core = true;
      }

      boolean check_is_home = false;

      for (Player p : Bukkit.getOnlinePlayers()) {
         if (event.getWorld().getName().replace(Variable.world_prefix, "").equalsIgnoreCase(p.getName())) {
            check_is_home = true;
         }
      }

      if (Variable.create_list_home.contains(event.getWorld().getName())) {
         event.getWorld().setAutoSave(false);
         event.getWorld().setKeepSpawnInMemory(false);
      }

      if (Util.CheckIsHome(event.getWorld().getName()) || check_is_home || Variable.create_list_home.contains(event.getWorld().getName())) {
         if (Variable.create_list_home.contains(event.getWorld().getName())) {
            Variable.create_list_home.remove(event.getWorld().getName());
         }

         event.getWorld().setKeepSpawnInMemory(Main.JavaPlugin.getConfig().getBoolean("KeepSpawnInMemory"));
         if (!Bukkit.getVersion().contains("Paper") && !Bukkit.getVersion().contains("Purpur")) {
            Location loc = event.getWorld().getHighestBlockAt(event.getWorld().getSpawnLocation()).getLocation();
            if (!Bukkit.getVersion().contains("1.7.10") && !Bukkit.getVersion().contains("1.7.2")) {
               event.getWorld().setSpawnLocation(loc);
            } else {
               event.getWorld().setSpawnLocation((int)loc.getX(), (int)loc.getY(), (int)loc.getZ());
            }
         }

         final World initWorld = event.getWorld();
         (new BukkitRunnable() {
            public void run() {
               try {
                  String baseName = Util.getBaseHomeName(initWorld.getName());
                  if (Util.hasHomeData(baseName)) {
                     Home home = HomeAPI.getHome(baseName);
                     if (home != null) {
                        Util.applyHomeWorldRules(initWorld, home);
                        return;
                     }
                  }

                  String diff = Main.JavaPlugin.getConfig().getString("Difficulty");
                  if (diff == null || diff.isEmpty()) {
                     diff = "Easy";
                  }

                  switch (diff.toLowerCase()) {
                     case "peaceful":
                        initWorld.setDifficulty(Difficulty.PEACEFUL);
                        break;
                     case "normal":
                        initWorld.setDifficulty(Difficulty.NORMAL);
                        break;
                     case "hard":
                        initWorld.setDifficulty(Difficulty.HARD);
                        break;
                     default:
                        initWorld.setDifficulty(Difficulty.EASY);
                  }
               } catch (Exception failure) {
                  com.Util.Diag.warnOnce("world-init-difficulty", "Could not apply the configured Difficulty to a new world", failure);
               }

               try {
                  Platform.setGameRule(initWorld, "doMobSpawning", "true");
               } catch (Exception failure) {
                  com.Util.Diag.warnOnce("world-init-mobspawning", "Could not set doMobSpawning on a new world", failure);
               }
            }
         }).runTaskLater(Main.JavaPlugin, 5L);
         if (!check_false_core && CustomChunkGenerator.isSkyIslandWorld(event.getWorld())) {
            WorldCreator worldCreator = new WorldCreator(event.getWorld().getName());
            worldCreator = worldCreator.generator(new CustomChunkGenerator());
            Bukkit.getServer().createWorld(worldCreator);
         }
      }
   }
}
