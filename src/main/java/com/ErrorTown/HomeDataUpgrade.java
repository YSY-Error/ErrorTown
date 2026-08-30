package com.ErrorTown;

import com.Util.MySQL;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import org.bukkit.configuration.file.YamlConfiguration;

public class HomeDataUpgrade {
   public static void apply() {
      if (Variable.bungee) {
         MySQL.addFlowersColumn();
         MySQL.addPopularityColumn();
         MySQL.addGiftColumn();
         MySQL.addAdvertisementColumn();
         MySQL.addIconColumn();
         MySQL.addVisitColumn();
         MySQL.addLimitBlockColumn();
      } else {
         File folder = new File(Variable.Tempf);
         for (File temp : folder.listFiles()) {
            YamlConfiguration yamlConfiguration = YamlConfiguration.loadConfiguration(temp);
            boolean edit = false;
            if (!yamlConfiguration.isSet("flowers")) {
               yamlConfiguration.createSection("flowers");
               yamlConfiguration.set("flowers", 0);
               edit = true;
            }

            if (!yamlConfiguration.isSet("popularity")) {
               yamlConfiguration.createSection("popularity");
               yamlConfiguration.set("popularity", 0);
               edit = true;
            }

            if (!yamlConfiguration.isSet("gifts")) {
               yamlConfiguration.createSection("gifts");
               yamlConfiguration.set("gifts", new ArrayList<>());
               edit = true;
            }

            if (!yamlConfiguration.isSet("advertisement")) {
               yamlConfiguration.createSection("advertisement");
               yamlConfiguration.set("advertisement", new ArrayList<>());
               edit = true;
            }

            if (!yamlConfiguration.isSet("icon")) {
               yamlConfiguration.createSection("icon");
               yamlConfiguration.set("icon", "");
               edit = true;
            }

            if (edit) {
               try {
                  yamlConfiguration.save(temp);
               } catch (IOException ioFailure) {
                  ioFailure.printStackTrace();
               }
            }
         }
      }
   }
}
