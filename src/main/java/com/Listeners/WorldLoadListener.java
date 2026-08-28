package com.Listeners;

import com.ErrorTown.Main;
import com.ErrorTown.Variable;
import com.Util.Home;
import com.Util.HomeAPI;
import com.Util.HomeSpawnUtil;
import com.Util.MySQL;
import com.Util.Util;
import java.io.File;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldLoadEvent;

public class WorldLoadListener implements Listener {
   @EventHandler(
      priority = EventPriority.HIGHEST,
      ignoreCancelled = true
   )
   public void onLoad(WorldLoadEvent event) {
      String worldname = event.getWorld().getName().replace(Variable.world_prefix, "");
      World world = event.getWorld();
      if (Variable.bungee) {
         if (MySQL.CheckIsAHome(worldname) && Main.JavaPlugin.getConfig().getBoolean("HDSwitch")) {
            Util.refreshBorder(world);
         }
      } else {
         File f2 = new File(Variable.Tempf, worldname + ".yml");
         if (f2.exists() && Main.JavaPlugin.getConfig().getBoolean("HDSwitch")) {
            Util.refreshBorder(world);
         }
      }

      String baseName = Util.getBaseHomeName(worldname);
      if (Util.hasHomeData(baseName)) {
         try {
            Home home = HomeAPI.getHome(baseName);
            if (home == null) {
               return;
            }

            Util.applyHomeWorldRules(world, home);
            HomeSpawnUtil.applyHomeSpawnCompensation(world);
         } catch (Exception var6) {
            com.Util.Diag.warnOnce("world-load-rules", "Could not apply home rules to a loading world", var6);
         }
      }
   }
}
