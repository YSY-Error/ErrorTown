package com.Listeners;

import com.ErrorTown.Variable;
import com.Util.Platform;
import com.Util.Util;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEvent;

public class HomeProtectInteractListener implements Listener {
   /**
    * Physical-interact protection skips the configured farmland type so trampling crops still
    * fires the normal farm rules instead of the generic interact message.
    *
    * <p>This used to be {@code Material.valueOf(Variable.Soil)} inside a {@code try} whose
    * {@code catch} returned from the handler. With the historical default {@code SoilType: SOIL} —
    * a material that stopped existing in 1.13 — that meant the entire interact protection was
    * silently disabled on every supported version. Resolution is now tolerant, and an unresolvable
    * value only costs the farmland exemption.</p>
    */
   private static Material configuredSoil() {
      return Platform.material(Variable.Soil, Material.FARMLAND, "config-soil-type");
   }

   @EventHandler(
      priority = EventPriority.HIGHEST,
      ignoreCancelled = true
   )
   public void onInteract(PlayerInteractEvent event) {
      if (event.getClickedBlock() == null) {
         return;
      }

      Material clicked = event.getClickedBlock().getType();
      if (clicked.toString().toUpperCase().contains("SIGN")) {
         return;
      }
      if (clicked == configuredSoil() && event.getAction() == Action.PHYSICAL) {
         return;
      }
      if (Util.CheckIsHome(event.getPlayer().getWorld().getName().replaceAll(Variable.world_prefix, ""))) {
         if (!Util.Check(event.getPlayer(), event.getPlayer().getLocation().getWorld().getName().replaceAll(Variable.world_prefix, ""))) {
            String temp = Variable.Lang_YML.getString("NoPermissionInteract");
            event.getPlayer().sendMessage(temp);
            event.setCancelled(true);
         }
      }
   }

   @EventHandler
   public void onPlayerBucketEmptyEvent(PlayerBucketEmptyEvent event) {
      Player p = event.getPlayer();
      if (Util.CheckIsHome(event.getPlayer().getWorld().getName())) {
         if (!Util.Check(event.getPlayer(), event.getPlayer().getWorld().getName())) {
            String temp = Variable.Lang_YML.getString("NoPermissionInteract");
            event.getPlayer().sendMessage(temp);
            event.setCancelled(true);
         }
      }
   }

   @EventHandler
   public void onPlayerBucketFillEvent(PlayerBucketFillEvent event) {
      Player p = event.getPlayer();
      if (Util.CheckIsHome(event.getPlayer().getWorld().getName())) {
         if (!Util.Check(event.getPlayer(), event.getPlayer().getWorld().getName())) {
            String temp = Variable.Lang_YML.getString("NoPermissionInteract");
            event.getPlayer().sendMessage(temp);
            event.setCancelled(true);
         }
      }
   }
}
