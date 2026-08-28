package com.Util;

import org.bukkit.World;
import org.bukkit.entity.Entity;

public class R1_7_10 {
   public static long getID(World world) {
      return 0L;
   }

   public static double getTps() {
      return 20.0;
   }

   public static String getName(Entity entity) {
      String customName = entity.getType().name();
      if (customName.toUpperCase().contains("HYDRA")) {
         customName = "Hydra";
      }

      if (customName.contains(" ")) {
         customName = customName.replace(" ", "_");
      }

      if (customName.contains("'")) {
         customName = customName.replace("'", "_");
      }

      return customName.toUpperCase();
   }
}
