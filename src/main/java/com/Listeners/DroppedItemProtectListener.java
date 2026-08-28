package com.Listeners;

import com.Util.Platform;
import com.Util.Util;
import java.util.EnumSet;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.entity.Item;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ItemDespawnEvent;
import org.bukkit.event.entity.ItemMergeEvent;
import org.bukkit.event.entity.ItemSpawnEvent;

public class DroppedItemProtectListener implements Listener {
   private static final Set<Material> FAST_DESPAWN_BLOCK_DROPS = EnumSet.of(
      Material.COBBLESTONE,
      Material.DIRT,
      Material.SAND,
      Material.GRAVEL,
      Material.STONE,
      Material.COBBLED_DEEPSLATE,
      Material.DEEPSLATE,
      Material.SANDSTONE,
      Material.RED_SAND
   );

   private static boolean isManagedHomeItem(Item item) {
      return item != null && item.getWorld() != null && Util.isManagedHomeWorld(item.getWorld().getName());
   }

   private static boolean isProtectedDrop(Item item) {
      return item != null && item.getItemStack() != null && FAST_DESPAWN_BLOCK_DROPS.contains(item.getItemStack().getType());
   }

   @EventHandler(
      priority = EventPriority.HIGHEST,
      ignoreCancelled = true
   )
   public void onItemSpawn(ItemSpawnEvent event) {
      Item item = event.getEntity();
      if (isManagedHomeItem(item) && isProtectedDrop(item)) {
         Platform.protectDroppedItem(item);
      }
   }

   @EventHandler(
      priority = EventPriority.HIGHEST,
      ignoreCancelled = true
   )
   public void onItemDespawn(ItemDespawnEvent event) {
      Item item = event.getEntity();
      if (isManagedHomeItem(item) && isProtectedDrop(item)) {
         event.setCancelled(true);
      }
   }

   @EventHandler(
      priority = EventPriority.HIGHEST,
      ignoreCancelled = true
   )
   public void onItemMerge(ItemMergeEvent event) {
      Item item = event.getEntity();
      Item target = event.getTarget();
      if (isManagedHomeItem(item) && target != null && target.getWorld() != null && Util.isManagedHomeWorld(target.getWorld().getName())) {
         if (isProtectedDrop(item) || isProtectedDrop(target)) {
            event.setCancelled(true);
         }
      }
   }
}
