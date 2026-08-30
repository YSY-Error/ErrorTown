package com.Util;

import com.ErrorTown.Main;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitRunnable;

public class TimeAsync {
   public static void asnycTime() {
      if (Main.JavaPlugin.getConfig().getBoolean("EnableAsnycTime")) {
         (new BukkitRunnable() {
            public void run() {
               if (!Main.JavaPlugin.getConfig().getBoolean("EnableAsnycTime")) {
                  this.cancel();
                  return;
               }

               String async_world_name = Main.JavaPlugin.getConfig().getString("AsyncTimeWorld");
               World async_world = async_world_name == null ? null : Bukkit.getWorld(async_world_name);
               if (async_world == null) {
                  return;
               }

               boolean useSeasons = Main.JavaPlugin.getConfig().getBoolean("RealisticSeasons");

               for (World world : Bukkit.getWorlds()) {
                  // The home key is the world name with the configured prefix removed.
                  // Looking the home up by the raw world name silently skipped every home
                  // whenever a non-empty world prefix was configured.
                  Home home = HomeAPI.getHome(Util.getBaseHomeName(world.getName()));
                  if (home == null || home.isLocktime()) {
                     continue;
                  }

                  if (useSeasons) {
                     SeasonsCompat.copySeasonAndDate(async_world, world);
                  }

                  world.setTime(async_world.getTime());
               }
            }
         }).runTaskTimer(Main.JavaPlugin, 0L, 20L);
      }
   }
}
