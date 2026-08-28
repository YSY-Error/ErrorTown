package com.Listeners;

import com.GUI.MainGui;
import com.ErrorTown.Main;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;

public class ShiftFMenuListener implements Listener {
   @EventHandler(
      priority = EventPriority.HIGHEST,
      ignoreCancelled = false
   )
   public void onShiftFOpenMenu(PlayerSwapHandItemsEvent event) {
      if (Main.JavaPlugin.getConfig().getBoolean("ShiftFOpenMainMenu.Enable", false)) {
         Player player = event.getPlayer();
         boolean requireSneaking = Main.JavaPlugin.getConfig().getBoolean("ShiftFOpenMainMenu.RequireSneaking", true);
         if (!requireSneaking || player.isSneaking()) {
            if (Main.JavaPlugin.getConfig().getBoolean("ShiftFOpenMainMenu.CancelOffhandSwap", true)) {
               event.setCancelled(true);
            }

            if (Main.JavaPlugin.getConfig().getBoolean("ShiftFOpenMainMenu.DebugMessage", false)) {
               player.sendMessage("§8[§6ErrorTown§8] §aShift+F 已触发，正在打开菜单。");
            }

            Main.JavaPlugin.getServer().getScheduler().runTask(Main.JavaPlugin, () -> {
               if (player.isOnline()) {
                  player.openInventory(new MainGui(player).getInventory());
               }
            });
         }
      }
   }
}
