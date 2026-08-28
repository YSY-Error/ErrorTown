package com.Listeners;

import com.ErrorTown.Variable;
import com.Util.Home;
import com.Util.HomeAPI;
import com.Util.Util;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;

public class HomeRulesListener implements Listener {
   @EventHandler(
      priority = EventPriority.HIGH,
      ignoreCancelled = true
   )
   public void onEntityExplode(EntityExplodeEvent event) {
      if (!event.blockList().isEmpty()) {
         World world = event.getLocation().getWorld();
         if (world != null) {
            String baseName = Util.getBaseHomeName(world.getName());
            if (Variable.list_home.contains(baseName)) {
               Home home = HomeAPI.getHome(baseName);
               if (home != null) {
                  if (home.getRuleExplosionProtect()) {
                     event.blockList().clear();
                  }
               }
            }
         }
      }
   }

   @EventHandler(
      priority = EventPriority.HIGH,
      ignoreCancelled = true
   )
   public void onBlockExplode(BlockExplodeEvent event) {
      if (!event.blockList().isEmpty()) {
         World world = event.getBlock().getWorld();
         if (world != null) {
            String baseName = Util.getBaseHomeName(world.getName());
            if (Variable.list_home.contains(baseName)) {
               Home home = HomeAPI.getHome(baseName);
               if (home != null) {
                  if (home.getRuleExplosionProtect()) {
                     event.blockList().clear();
                  }
               }
            }
         }
      }
   }
}
