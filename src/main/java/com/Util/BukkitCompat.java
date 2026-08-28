package com.Util;

import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/** Modern Bukkit names kept out of the legacy home-management code. */
public final class BukkitCompat {
   private BukkitCompat() {
   }

   public static boolean isDroppedItem(Entity entity) {
      return entity != null && entity.getType() == EntityType.ITEM;
   }

   public static void addSlowness(LivingEntity entity, int duration, int amplifier) {
      if (entity != null) {
         entity.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, duration, amplifier));
      }
   }

   public static void addResistance(LivingEntity entity, int duration, int amplifier, boolean ambient, boolean particles, boolean icon) {
      if (entity != null) {
         entity.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, duration, amplifier, ambient, particles, icon));
      }
   }
}
