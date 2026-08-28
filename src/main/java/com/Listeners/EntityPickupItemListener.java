package com.Listeners;

import com.ErrorTown.Main;
import com.ErrorTown.Variable;
import com.Util.Util;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;

public class EntityPickupItemListener implements Listener {
   @EventHandler(
      ignoreCancelled = true
   )
   public void onPickup(EntityPickupItemEvent event) {
      if (event.getEntity() instanceof Villager) {
         String worldName = event.getEntity().getWorld().getName();
         if (Util.CheckIsHome(worldName.replace(Variable.world_prefix, ""))) {
            if (!Main.JavaPlugin.getConfig().getBoolean("HomeRulesDefaults.AllowVillagerPickupItems", false)) {
               event.setCancelled(true);
            }
         }
      }
   }
}
