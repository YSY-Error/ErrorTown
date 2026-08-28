package com.Listeners;

import com.ErrorTown.Variable;
import com.Util.Util;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

public class PlayerDeathListener implements Listener {
   @EventHandler
   public void onDeath(PlayerDeathEvent event) {
      String baseHomeName = Util.getBaseHomeName(event.getEntity().getWorld().getName());
      if (Util.CheckIsHome(baseHomeName)) {
         if (!Variable.wait_to_spawn_home.containsKey(event.getEntity().getName())) {
            Variable.wait_to_spawn_home.put(event.getEntity().getName(), event.getEntity().getLocation().getWorld().getName());
         }
      }
   }
}
