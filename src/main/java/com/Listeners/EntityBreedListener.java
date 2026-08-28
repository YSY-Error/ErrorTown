package com.Listeners;

import com.ErrorTown.Variable;
import com.Util.Home;
import com.Util.HomeAPI;
import com.Util.Util;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityBreedEvent;

public class EntityBreedListener implements Listener {
   @EventHandler(
      ignoreCancelled = true
   )
   public void onBreed(EntityBreedEvent event) {
      String worldName = event.getEntity().getWorld().getName();
      if (Util.CheckIsHome(worldName.replace(Variable.world_prefix, ""))) {
         String baseName = Util.getBaseHomeName(worldName);
         Home home = HomeAPI.getHome(baseName);
         if (home != null) {
            if (!home.getRuleAllowAnimalBreed()) {
               event.setCancelled(true);
            }
         }
      }
   }
}
