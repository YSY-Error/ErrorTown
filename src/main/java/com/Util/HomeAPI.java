package com.Util;

import com.ErrorTown.Main;
import com.ErrorTown.Variable;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

public class HomeAPI {
   public static List<Home> getHomes() {
      List<Home> list = new ArrayList<>();
      if (Variable.bungee) {
         for (String temp : MySQL.getAllWorlds()) {
            Home home = null;
            home = new Home(temp);
            list.add(home);
         }
      } else {
         File folder = new File(Variable.Tempf);
         for (File temp : folder.listFiles()) {
            String want_to = temp.getPath().replace(Variable.Tempf, "").replace(".yml", "").replace(Variable.file_loc_prefix, "");
            Home home = new Home(want_to);
            list.add(home);
         }
      }

      return list;
   }

   public static Home getHome(String name) {
      name = name.replace(Variable.world_prefix, "");
      if (!Util.CheckIsHome(name)) {
         boolean has_been_join = false;
         if (Variable.bungee) {
            if (MySQL.alreadyhastheplayerjoin(name)) {
               has_been_join = true;
               name = MySQL.getJoinHome(name);
            }
         } else {
            File folder = new File(Variable.Tempf);
            for (File temp : folder.listFiles()) {
               String want_to = temp.getPath().replace(Variable.Tempf, "").replace(".yml", "").replace(Variable.file_loc_prefix, "");
               YamlConfiguration yamlConfiguration1 = YamlConfiguration.loadConfiguration(temp);
               for (String listedName : yamlConfiguration1.getStringList("OP")) {
                  if (listedName.equalsIgnoreCase(name)) {
                     has_been_join = true;
                     name = want_to;
                     break;
                  }
               }
            }
         }

         if (!has_been_join) {
            return null;
         }
      }

      return new Home(name);
   }

   public static List<String> getOwnedHomes(final String playerName) {
      List<String> result = new ArrayList<>();
      if (Variable.bungee) {
         if (MySQL.alreadyhastheplayerhome(playerName)) {
            result.add(playerName);
         }

         return result;
      } else {
         File folder = new File(Variable.Tempf);
         File[] files = folder.listFiles();
         if (files == null) {
            return result;
         } else {
            for (File temp : files) {
               String wantTo = temp.getPath().replace(Variable.Tempf, "").replace(".yml", "").replace(Variable.file_loc_prefix, "");
               if (Util.getHomeOwner(wantTo).equalsIgnoreCase(playerName)) {
                  result.add(wantTo);
               }
            }

            Collections.sort(result, new Comparator<String>() {
               public int compare(String left, String right) {
                  if (left.equalsIgnoreCase(playerName)) {
                     return right.equalsIgnoreCase(playerName) ? 0 : -1;
                  } else if (right.equalsIgnoreCase(playerName)) {
                     return 1;
                  } else {
                     int leftIndex = HomeAPI.getOwnedHomeIndex(playerName, left);
                     int rightIndex = HomeAPI.getOwnedHomeIndex(playerName, right);
                     return leftIndex != rightIndex ? Integer.compare(leftIndex, rightIndex) : left.compareToIgnoreCase(right);
                  }
               }
            });
            return result;
         }
      }
   }

   private static int getOwnedHomeIndex(String playerName, String homeName) {
      if (homeName.equalsIgnoreCase(playerName)) {
         return 1;
      } else {
         String prefix = playerName + "_";
         if (homeName.regionMatches(true, 0, prefix, 0, prefix.length())) {
            try {
               return Integer.parseInt(homeName.substring(prefix.length()));
            } catch (Exception failure) {
               // Suffix is not numeric, so this is not an indexed home name.
            }
         }

         return Integer.MAX_VALUE;
      }
   }

   public static String getPrimaryOwnedHome(String playerName) {
      List<String> homes = getOwnedHomes(playerName);
      if (homes.isEmpty()) {
         return null;
      } else {
         for (String homeName : homes) {
            if (homeName.equalsIgnoreCase(playerName)) {
               return homeName;
            }
         }

         return homes.get(0);
      }
   }

   public static void delHome(final String name) {
      if (Variable.bungee) {
         MySQL.removePlayer(name);
      } else {
         File f2 = new File(Variable.Tempf, name + ".yml");
         f2.delete();
      }

      World world = Bukkit.getWorld(Variable.world_prefix + name);
      if (world != null) {
         for (Player p6 : Bukkit.getWorld(Variable.world_prefix + name).getPlayers()) {
            p6.teleport(Bukkit.getWorld("world").getSpawnLocation());
            p6.sendMessage(Variable.Lang_YML.getString("WorldHasBeenDeleted"));
         }
      }

      Bukkit.unloadWorld(Variable.world_prefix + name, true);
      (new BukkitRunnable() {
         public void run() {
            File f;
            if (Variable.world_prefix.equalsIgnoreCase("")) {
               if (Bukkit.getVersion().toString().toUpperCase().contains("ARCLIGHT")) {
                  f = new File(Variable.single_server_gen + Variable.world_prefix + name);
               } else {
                  f = new File(Variable.single_server_gen + "world" + Variable.file_loc_prefix + name);
               }
            } else {
               f = new File(Variable.single_server_gen + Variable.world_prefix + name);
            }

            Util.deleteFile(f);
         }
      }).runTaskLater(Main.JavaPlugin, 5L);
   }

   public boolean hasPermission(Player p, String name) {
      name = name.replace(Variable.world_prefix, "");
      return Util.Check(p, name);
   }
}
