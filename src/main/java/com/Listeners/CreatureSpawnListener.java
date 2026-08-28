package com.Listeners;

import com.ErrorTown.Main;
import com.ErrorTown.Variable;
import com.Util.Home;
import com.Util.HomeAPI;
import com.Util.R1_12_2;
import com.Util.R1_7_10;
import com.Util.Util;
import org.bukkit.Bukkit;
import org.bukkit.entity.Animals;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;

public class CreatureSpawnListener implements Listener {
   private boolean isHostileMob(LivingEntity entity) {
      if (entity instanceof Monster) {
         return true;
      } else {
         EntityType type = entity.getType();
         return type == EntityType.SLIME || type == EntityType.MAGMA_CUBE;
      }
   }

   private boolean isMobCountedForCap(LivingEntity entity, boolean countVillagers) {
      return this.isHostileMob(entity) || entity instanceof Animals || countVillagers && entity instanceof Villager;
   }

   @EventHandler
   public void onSpawm(CreatureSpawnEvent event) {
      if (event.getEntityType() == EntityType.PHANTOM && !Main.JavaPlugin.getConfig().getBoolean("EnablePhantomSpawn", false)) {
         event.setCancelled(true);
      } else if (Util.isManagedHomeWorld(event.getEntity().getWorld().getName())) {
         if (Main.JavaPlugin.getConfig().getBoolean("HomeTerrain.Enabled", false)) {
            event.setCancelled(true);
            return;
         }
         String worldname = event.getEntity().getWorld().getName().replace(Variable.world_prefix, "");
         String baseName = Util.getBaseHomeName(worldname);
         Home home = HomeAPI.getHome(baseName);
         if (home != null) {
            if (event.getEntity() instanceof Villager) {
               int villagerLimit = Math.max(0, Main.JavaPlugin.getConfig().getInt("HomeRulesDefaults.MaxVillagerCount", 12));
               if (villagerLimit > 0) {
                  int villagerCount = 0;

                  for (LivingEntity entity : event.getEntity().getWorld().getLivingEntities()) {
                     if (entity instanceof Villager) {
                        villagerCount++;
                     }
                  }

                  if (villagerCount >= villagerLimit) {
                     event.setCancelled(true);
                     return;
                  }
               }
            }

            if (this.isHostileMob(event.getEntity()) && !home.getRuleAllowHostileMobs()) {
               event.setCancelled(true);
               return;
            }

            if (event.getEntity() instanceof Animals && !home.getRuleAllowPassiveMobs()) {
               event.setCancelled(true);
               return;
            }

            if (event.getSpawnReason() == SpawnReason.SPAWNER && !home.getRuleAllowSpawnerSpawn()) {
               event.setCancelled(true);
               return;
            }

            int maxMobCount = Math.max(0, home.getRuleMaxMobCount());
            if (maxMobCount == 0) {
               if (this.isHostileMob(event.getEntity()) || event.getEntity() instanceof Animals || event.getSpawnReason() == SpawnReason.SPAWNER) {
                  event.setCancelled(true);
                  return;
               }
            } else {
               int count = 0;
               boolean countVillagers = Main.JavaPlugin.getConfig().getBoolean("HomeRulesDefaults.CountVillagersInMobCap", false);

               for (LivingEntity entityx : event.getEntity().getWorld().getLivingEntities()) {
                  if (this.isMobCountedForCap(entityx, countVillagers)) {
                     count++;
                  }
               }

               if (count >= maxMobCount) {
                  event.setCancelled(true);
                  return;
               }
            }
         }

         if (Main.JavaPlugin.getConfig().getBoolean("EnableBlackEntities")) {
            LivingEntity livingEntity = event.getEntity();
            String type = null;
            if (Bukkit.getBukkitVersion().toString().contains("1.12.2")) {
               type = R1_12_2.getName(livingEntity);
            } else if (Bukkit.getBukkitVersion().toString().contains("1.7.10")) {
               type = R1_7_10.getName(livingEntity);
            } else {
               type = event.getEntity().getType().toString().toUpperCase();
            }

            for (String temp : Main.JavaPlugin.getConfig().getStringList("BlackEntitiesList")) {
               if (type.toUpperCase().equalsIgnoreCase(temp.toUpperCase())) {
                  livingEntity.remove();
                  break;
               }
            }
         }
      }
   }
}
