package com.Util;

import org.bukkit.World;
import org.bukkit.entity.Entity;

public class R1_12_2 {
   public static long getID(World world) {
      return 0L;
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
