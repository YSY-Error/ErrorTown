package com.PlaceHolder;

import com.ErrorTown.Main;
import com.ErrorTown.Variable;
import com.ErrorTown.init;
import com.Util.HomeAPI;
import com.Util.HomeTerrainPolicy;
import com.Util.BukkitCompat;
import com.Util.MySQL;
import com.Util.Platform;
import com.Util.StaticsTick;
import com.Util.Util;
import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import javax.annotation.Nonnull;
import me.clip.placeholderapi.PlaceholderAPI;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Difficulty;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

public class API extends PlaceholderExpansion {
   /** Placeholder prefix after the rename: {@code %ErrorTown_...%}. */
   public static final String IDENTIFIER = "ErrorTown";

   /** Prefix used before the rename, kept working by {@link LegacyAPI}. */
   public static final String LEGACY_IDENTIFIER = "SummerTown";

   public static List<StaticsTick> world_StaticsTick = new ArrayList<>();

   public boolean canRegister() {
      return true;
   }

   @Nonnull
   public String getIdentifier() {
      return IDENTIFIER;
   }

   @Nonnull
   public String getAuthor() {
      return "YSYError";
   }

   @Nonnull
   public String getVersion() {
      return "1.0.0";
   }

   /**
    * Registers this expansion plus a legacy alias.
    *
    * <p>Placeholder identifiers are an external contract: scoreboard, tab-list and chat
    * plugins hold {@code %ErrorTown_...%} strings in <em>their</em> configs, which this
    * plugin cannot rewrite. Registering {@link LegacyAPI} keeps those working after the
    * rename, so an operator can migrate their other plugins at their own pace.</p>
    */
   public void registerWithLegacyAlias() {
      this.register();
      new LegacyAPI().register();
   }

   /** Serves the pre-rename {@code %ErrorTown_...%} prefix by delegating to {@link API}. */
   public static final class LegacyAPI extends API {
      @Nonnull
      @Override
      public String getIdentifier() {
         return LEGACY_IDENTIFIER;
      }
   }

   public static String getRankName(int level) {
      init.refreshWorldStatics(false);
      return Variable.world_StaticsTick.size() > level - 1 ? Variable.world_StaticsTick.get(level - 1).name : "";
   }

   public static String getCache(String p, String papi_name) {
      return Variable.cache.get(p, papi_name);
   }

   public static void putCache(String p, String papi_name, String value) {
      long refreshSeconds = Variable.Lang_YML == null
         ? 5L
         : Math.max(1L, Variable.Lang_YML.getLong("PlaceHolders.RefreshTime", 5L));
      Variable.cache.put(p, papi_name, value, refreshSeconds * 1000L);
   }

   private String getCurrentHomeName(Player player) {
      if (player != null && player.getWorld() != null) {
         String worldName = player.getWorld().getName();
         String baseHomeName = Util.getBaseHomeName(worldName);
         return Util.CheckIsHome(baseHomeName) ? baseHomeName : null;
      } else {
         return null;
      }
   }

   private String getPrimaryHomeName(Player player) {
      if (player == null) {
         return null;
      } else if (Variable.bungee) {
         return MySQL.alreadyhastheplayerhome(player.getName()) ? player.getName() : null;
      } else {
         return HomeAPI.getPrimaryOwnedHome(player.getName());
      }
   }

   private String getHomeNameForSelfPlaceholders(Player player) {
      String currentHome = this.getCurrentHomeName(player);
      return currentHome != null ? currentHome : this.getPrimaryHomeName(player);
   }

   private YamlConfiguration loadHomeYml(String homeName) {
      if (homeName == null) {
         return null;
      } else {
         File f = new File(Variable.Tempf, homeName + ".yml");
         return !f.exists() ? null : YamlConfiguration.loadConfiguration(f);
      }
   }

   @Nonnull
   public String onRequest(OfflinePlayer player, String check) {
      String result_check = null;
      if (player == null) {
         if (check.contains("World_Name_")) {
            String home_name = check.replace("World_Name_", "");
            if (Util.CheckIsHome(home_name)) {
               String temp = Variable.Lang_YML.getString("PlaceHolders.WorldName");
               if (temp.contains("<PlayerName>")) {
                  temp = temp.replace("<PlayerName>", home_name);
               }

               if (temp.contains("<WorldName>")) {
                  temp = temp.replace("<WorldName>", home_name);
               }

               home_name = temp;
            } else if (Util.getAliasName(home_name) != null) {
               home_name = Util.getAliasName(home_name);
            }

            result_check = String.valueOf(home_name);
         }

         if (check.contains("World_Popularity_")) {
            String tempx = check.replace("World_Popularity_", "");
            if (!Util.CheckIsHome(tempx)) {
               return Variable.Lang_YML.getString("PlaceHolders.NoHome");
            } else {
               com.Util.Home home = HomeAPI.getHome(tempx);
               return String.valueOf(home.getPopularity());
            }
         } else {
            if (check.contains("World_Calc_")) {
               String tempx = check.replace("World_Calc_", "");
               if (!Util.CheckIsHome(tempx)) {
                  return Variable.Lang_YML.getString("PlaceHolders.NoHome");
               }

               com.Util.Home home = HomeAPI.getHome(tempx);
               result_check = String.valueOf(
                  home.getPopularity() * Main.JavaPlugin.getConfig().getDouble("PopularityAdd")
                     + home.getFlowers() * Main.JavaPlugin.getConfig().getDouble("FlowerAdd")
               );
            }

            if (check.contains("World_Flower_")) {
               String tempx = check.replace("World_Flower_", "");
               if (!Util.CheckIsHome(tempx)) {
                  return Variable.Lang_YML.getString("PlaceHolders.NoHome");
               } else {
                  com.Util.Home home = HomeAPI.getHome(tempx);
                  return String.valueOf(home.getFlowers());
               }
            } else {
               if (check.contains("World_TrustList_")) {
                  String tempx = check.replace("World_TrustList_", "");
                  if (!Util.CheckIsHome(tempx)) {
                     return Variable.Lang_YML.getString("PlaceHolders.NotIsHome");
                  }

                  if (Variable.bungee) {
                     result_check = MySQL.getMembers(tempx).toString();
                  } else {
                     File f = new File(Variable.Tempf, tempx + ".yml");
                     YamlConfiguration yml = YamlConfiguration.loadConfiguration(f);
                     List<String> list = yml.getStringList("Members");
                     result_check = list.toString();
                  }
               }

               if (check.contains("World_ManageList_")) {
                  String tempxx = check.replace("World_ManageList_", "");
                  if (!Util.CheckIsHome(tempxx)) {
                     return Variable.Lang_YML.getString("PlaceHolders.NotIsHome");
                  }

                  if (Variable.bungee) {
                     result_check = MySQL.getOP(tempxx).toString();
                  } else {
                     File f = new File(Variable.Tempf, tempxx + ".yml");
                     YamlConfiguration yml = YamlConfiguration.loadConfiguration(f);
                     List<String> list = yml.getStringList("OP");
                     result_check = list.toString();
                  }
               }

               if (check.contains("World_BlackList_")) {
                  String tempxxx = check.replace("World_BlackList_", "");
                  if (!Util.CheckIsHome(tempxxx)) {
                     return Variable.Lang_YML.getString("PlaceHolders.NotIsHome");
                  }

                  if (Variable.bungee) {
                     result_check = MySQL.getDenys(tempxxx).toString();
                  } else {
                     File f = new File(Variable.Tempf, tempxxx + ".yml");
                     YamlConfiguration yml = YamlConfiguration.loadConfiguration(f);
                     List<String> list = yml.getStringList("Denys");
                     result_check = list.toString();
                  }
               }

               if (check.contains("World_Level_")) {
                  String tempxxxx = check.replace("World_Level_", "");
                  if (!Util.CheckIsHome(tempxxxx)) {
                     return Variable.Lang_YML.getString("PlaceHolders.NotIsHome");
                  }

                  if (Variable.bungee) {
                     result_check = MySQL.getLevel(tempxxxx);
                  } else {
                     File f = new File(Variable.Tempf, tempxxxx + ".yml");
                     YamlConfiguration yml = YamlConfiguration.loadConfiguration(f);
                     result_check = String.valueOf(yml.getInt("Level"));
                  }
               }

               if (check.contains("World_Type_")) {
                  String tempxxxxx = check.replace("World_Type_", "");
                  if (!Util.CheckIsHome(tempxxxxx)) {
                     return Variable.Lang_YML.getString("PlaceHolders.NotIsHome");
                  }

                  result_check = Variable.Lang_YML.getString("PlaceHolders.Error");
                  if (Bukkit.getWorld(Variable.world_prefix + tempxxxxx) != null) {
                     World world = Bukkit.getWorld(Variable.world_prefix + tempxxxxx);
                     result_check = world.getWorldType().toString();
                  }
               }

               return result_check;
            }
         }
      } else {
         Player onlinePlayer = player.getPlayer();
         String currentHomeName = this.getCurrentHomeName(onlinePlayer);
         String primaryHomeName = this.getPrimaryHomeName(onlinePlayer);
         String selfHomeName = this.getHomeNameForSelfPlaceholders(onlinePlayer);
         String cache = getCache(player.getPlayer().getName(), check);
         if (cache != null) {
            return cache;
         } else if (check.equalsIgnoreCase("GetHomeName")) {
            Player p = player.getPlayer();
            String primaryHome = HomeAPI.getPrimaryOwnedHome(p.getName());
            com.Util.Home home = primaryHome != null ? HomeAPI.getHome(primaryHome) : null;
            if (home != null) {
               result_check = home.getName();
            } else {
               result_check = Variable.Lang_YML.getString("PlaceHolders.NoHome");
            }

            return result_check;
         } else {
            if (check.equalsIgnoreCase("Name")) {
               String tempxxxxxx = player.getPlayer().getWorld().getName().replace(Variable.world_prefix, "");
               if (Util.CheckIsHome(player.getPlayer().getWorld().getName().replace(Variable.world_prefix, ""))) {
                  tempxxxxxx = Variable.Lang_YML.getString("PlaceHolders.WorldName");
                  if (tempxxxxxx.contains("<PlayerName>")) {
                     tempxxxxxx = tempxxxxxx.replace("<PlayerName>", player.getPlayer().getWorld().getName().replace(Variable.world_prefix, ""));
                  }

                  if (tempxxxxxx.contains("<WorldName>")) {
                     tempxxxxxx = tempxxxxxx.replace("<WorldName>", player.getPlayer().getWorld().getName().replace(Variable.world_prefix, ""));
                  }
               } else if (Util.getAliasName(player.getPlayer().getWorld().getName().replace(Variable.world_prefix, "")) != null) {
                  tempxxxxxx = Util.getAliasName(player.getPlayer().getWorld().getName().replace(Variable.world_prefix, ""));
               } else if (!PlaceholderAPI.setPlaceholders(player.getPlayer(), "%multiverse_world_alias%").equalsIgnoreCase("%multiverse_world_alias%")) {
                  tempxxxxxx = PlaceholderAPI.setPlaceholders(player.getPlayer(), "%multiverse_world_alias%");
               }

               result_check = String.valueOf(tempxxxxxx);
            }

            if (check.equalsIgnoreCase("World_Alias")) {
               String tempxxxxxx = player.getPlayer().getWorld().getName().replace(Variable.world_prefix, "");
               if (Util.getAliasName(player.getPlayer().getWorld().getName().replace(Variable.world_prefix, "")) != null) {
                  tempxxxxxx = Util.getAliasName(player.getPlayer().getWorld().getName().replace(Variable.world_prefix, ""));
               } else if (!PlaceholderAPI.setPlaceholders(player, "%multiverse_world_alias%").equalsIgnoreCase("%multiverse_world_alias%")) {
                  tempxxxxxx = PlaceholderAPI.setPlaceholders(player, "%multiverse_world_alias%");
               }

               result_check = String.valueOf(tempxxxxxx);
            }

            if (check.equalsIgnoreCase("Tile")) {
               if (selfHomeName == null) {
                  return Variable.Lang_YML.getString("PlaceHolders.NoHome");
               }

               World world = Bukkit.getWorld(Variable.world_prefix + selfHomeName);
               if (world == null) {
                  return Variable.Lang_YML.getString("PlaceHolders.Error");
               }

               int amount = 0;

               try {
                  for (Chunk chunk : world.getLoadedChunks()) {
                     for (BlockState bs : chunk.getTileEntities()) {
                        amount++;
                     }
                  }
               } catch (ExceptionInInitializerError var20) {
                  return Variable.Lang_YML.getString("PlaceHolders.Error");
               }

               result_check = String.valueOf(amount);
            }

            if (check.equalsIgnoreCase("World_Tile")) {
               World world = Bukkit.getWorld(Variable.world_prefix + player.getPlayer().getWorld().getName().replace(Variable.world_prefix, ""));
               if (world == null) {
                  return Variable.Lang_YML.getString("PlaceHolders.Error");
               }

               int amount = 0;

               try {
                  for (Chunk chunk : world.getLoadedChunks()) {
                     for (BlockState bs : chunk.getTileEntities()) {
                        amount++;
                     }
                  }
               } catch (ExceptionInInitializerError var19) {
                  return Variable.Lang_YML.getString("PlaceHolders.Error");
               }

               result_check = String.valueOf(amount);
            }

            if (check.equalsIgnoreCase("Entity")) {
               if (selfHomeName == null) {
                  return Variable.Lang_YML.getString("PlaceHolders.NoHome");
               }

               World world = Bukkit.getWorld(Variable.world_prefix + selfHomeName);
               if (world == null) {
                  return Variable.Lang_YML.getString("PlaceHolders.Error");
               }

               int amount = 0;

               for (Chunk chunk : world.getLoadedChunks()) {
                  amount += chunk.getEntities().length;
               }

               result_check = String.valueOf(amount);
            }

            if (check.equalsIgnoreCase("World_Entity")) {
               World world = Bukkit.getWorld(Variable.world_prefix + player.getPlayer().getWorld().getName().replace(Variable.world_prefix, ""));
               if (world == null) {
                  return Variable.Lang_YML.getString("PlaceHolders.Error");
               }

               int amount = 0;

               for (Chunk chunk : world.getLoadedChunks()) {
                  amount += chunk.getEntities().length;
               }

               result_check = String.valueOf(amount);
            }

            if (check.equalsIgnoreCase("DropItem")) {
               if (selfHomeName == null) {
                  return Variable.Lang_YML.getString("PlaceHolders.NoHome");
               }

               World world = Bukkit.getWorld(Variable.world_prefix + selfHomeName);
               if (world == null) {
                  return Variable.Lang_YML.getString("PlaceHolders.Error");
               }

               int amount = 0;

               for (Chunk chunk : world.getLoadedChunks()) {
                  for (Entity entity : chunk.getEntities()) {
                     if (BukkitCompat.isDroppedItem(entity)) {
                        amount++;
                     }
                  }
               }

               result_check = String.valueOf(amount);
            }

            if (check.equalsIgnoreCase("World_DropItem")) {
               World world = Bukkit.getWorld(Variable.world_prefix + player.getPlayer().getWorld().getName().replace(Variable.world_prefix, ""));
               if (world == null) {
                  return Variable.Lang_YML.getString("PlaceHolders.Error");
               }

               int amount = 0;

               for (Chunk chunk : world.getLoadedChunks()) {
                  for (Entity entityx : chunk.getEntities()) {
                     if (BukkitCompat.isDroppedItem(entityx)) {
                        amount++;
                     }
                  }
               }

               result_check = String.valueOf(amount);
            }

            if (check.equalsIgnoreCase("World")) {
               result_check = String.valueOf(player.getPlayer().getWorld().getName().replace(Variable.world_prefix, ""));
            }

            if (check.equalsIgnoreCase("World_Flower")) {
               if (currentHomeName != null && Util.CheckIsHome(currentHomeName)) {
                  com.Util.Home home = HomeAPI.getHome(currentHomeName);
                  return String.valueOf(home.getFlowers());
               } else {
                  return Variable.Lang_YML.getString("PlaceHolders.NotIsHome");
               }
            } else if (check.equalsIgnoreCase("Flower")) {
               if (selfHomeName != null && Util.CheckIsHome(selfHomeName)) {
                  com.Util.Home home = HomeAPI.getHome(selfHomeName);
                  return String.valueOf(home.getFlowers());
               } else {
                  return Variable.Lang_YML.getString("PlaceHolders.NoHome");
               }
            } else if (check.equalsIgnoreCase("World_Popularity")) {
               if (currentHomeName != null && Util.CheckIsHome(currentHomeName)) {
                  com.Util.Home home = HomeAPI.getHome(currentHomeName);
                  return String.valueOf(home.getPopularity());
               } else {
                  return Variable.Lang_YML.getString("PlaceHolders.NotIsHome");
               }
            } else if (check.equalsIgnoreCase("Popularity")) {
               if (selfHomeName != null && Util.CheckIsHome(selfHomeName)) {
                  com.Util.Home home = HomeAPI.getHome(selfHomeName);
                  return String.valueOf(home.getPopularity());
               } else {
                  return Variable.Lang_YML.getString("PlaceHolders.NoHome");
               }
            } else {
               if (check.equalsIgnoreCase("World_Level")) {
                  if (currentHomeName == null || !Util.CheckIsHome(currentHomeName)) {
                     return Variable.Lang_YML.getString("PlaceHolders.NotIsHome");
                  }

                  if (Variable.bungee) {
                     result_check = MySQL.getLevel(currentHomeName);
                  } else {
                     File f = new File(Variable.Tempf, currentHomeName + ".yml");
                     YamlConfiguration yml = YamlConfiguration.loadConfiguration(f);
                     result_check = String.valueOf(yml.getInt("Level"));
                  }
               }

               if (check.equalsIgnoreCase("Level")) {
                  if (selfHomeName == null || !Util.CheckIsHome(selfHomeName)) {
                     return Variable.Lang_YML.getString("PlaceHolders.NoHome");
                  }

                  if (Variable.bungee) {
                     result_check = MySQL.getLevel(selfHomeName);
                  } else {
                     YamlConfiguration yml = this.loadHomeYml(selfHomeName);
                     if (yml == null) {
                        return Variable.Lang_YML.getString("PlaceHolders.NoHome");
                     }

                     result_check = String.valueOf(yml.getInt("Level"));
                  }
               }

               if (check.equalsIgnoreCase("pvp")) {
                  if (selfHomeName == null || !Util.CheckIsHome(selfHomeName)) {
                     return Variable.Lang_YML.getString("PlaceHolders.NoHome");
                  }

                  if (Variable.bungee) {
                     if (MySQL.getPVP(selfHomeName).equalsIgnoreCase("true")) {
                        result_check = Variable.Lang_YML.getString("PlaceHolders.Enable");
                     } else {
                        result_check = Variable.Lang_YML.getString("PlaceHolders.Disable");
                     }
                  } else {
                     YamlConfiguration yml = this.loadHomeYml(selfHomeName);
                     if (yml == null) {
                        return Variable.Lang_YML.getString("PlaceHolders.NoHome");
                     }

                     if (yml.getBoolean("pvp")) {
                        result_check = Variable.Lang_YML.getString("PlaceHolders.Enable");
                     } else {
                        result_check = Variable.Lang_YML.getString("PlaceHolders.Disable");
                     }
                  }
               }

               if (check.equalsIgnoreCase("World_pvp")) {
                  if (currentHomeName == null || !Util.CheckIsHome(currentHomeName)) {
                     return Variable.Lang_YML.getString("PlaceHolders.NotIsHome");
                  }

                  if (Variable.bungee) {
                     if (MySQL.getPVP(currentHomeName).equalsIgnoreCase("true")) {
                        result_check = Variable.Lang_YML.getString("PlaceHolders.Enable");
                     } else {
                        result_check = Variable.Lang_YML.getString("PlaceHolders.Disable");
                     }
                  } else {
                     File f = new File(Variable.Tempf, currentHomeName + ".yml");
                     YamlConfiguration ymlx = YamlConfiguration.loadConfiguration(f);
                     if (ymlx.getBoolean("pvp")) {
                        result_check = Variable.Lang_YML.getString("PlaceHolders.Enable");
                     } else {
                        result_check = Variable.Lang_YML.getString("PlaceHolders.Disable");
                     }
                  }
               }

               if (check.equalsIgnoreCase("pickup")) {
                  if (selfHomeName == null || !Util.CheckIsHome(selfHomeName)) {
                     return Variable.Lang_YML.getString("PlaceHolders.NoHome");
                  }

                  if (Variable.bungee) {
                     if (MySQL.getpickup(selfHomeName).equalsIgnoreCase("true")) {
                        result_check = Variable.Lang_YML.getString("PlaceHolders.Enable");
                     } else {
                        result_check = Variable.Lang_YML.getString("PlaceHolders.Disable");
                     }
                  } else {
                     YamlConfiguration ymlx = this.loadHomeYml(selfHomeName);
                     if (ymlx == null) {
                        return Variable.Lang_YML.getString("PlaceHolders.NoHome");
                     }

                     if (ymlx.getBoolean("pickup")) {
                        result_check = Variable.Lang_YML.getString("PlaceHolders.Enable");
                     } else {
                        result_check = Variable.Lang_YML.getString("PlaceHolders.Disable");
                     }
                  }
               }

               if (check.equalsIgnoreCase("World_pickup")) {
                  if (!Util.CheckIsHome(player.getPlayer().getWorld().getName().replace(Variable.world_prefix, ""))) {
                     return Variable.Lang_YML.getString("PlaceHolders.NotIsHome");
                  }

                  if (Variable.bungee) {
                     if (MySQL.getpickup(player.getPlayer().getWorld().getName().replace(Variable.world_prefix, "")).equalsIgnoreCase("true")) {
                        result_check = Variable.Lang_YML.getString("PlaceHolders.Enable");
                     } else {
                        result_check = Variable.Lang_YML.getString("PlaceHolders.Disable");
                     }
                  } else {
                     File f = new File(Variable.Tempf, player.getPlayer().getWorld().getName().replace(Variable.world_prefix, "") + ".yml");
                     YamlConfiguration ymlxx = YamlConfiguration.loadConfiguration(f);
                     if (ymlxx.getBoolean("pickup")) {
                        result_check = Variable.Lang_YML.getString("PlaceHolders.Enable");
                     } else {
                        result_check = Variable.Lang_YML.getString("PlaceHolders.Disable");
                     }
                  }
               }

               if (check.equalsIgnoreCase("drop")) {
                  if (selfHomeName == null || !Util.CheckIsHome(selfHomeName)) {
                     return Variable.Lang_YML.getString("PlaceHolders.NoHome");
                  }

                  if (Variable.bungee) {
                     if (MySQL.getdropitem(selfHomeName).equalsIgnoreCase("true")) {
                        result_check = Variable.Lang_YML.getString("PlaceHolders.Enable");
                     } else {
                        result_check = Variable.Lang_YML.getString("PlaceHolders.Disable");
                     }
                  } else {
                     YamlConfiguration ymlxx = this.loadHomeYml(selfHomeName);
                     if (ymlxx == null) {
                        return Variable.Lang_YML.getString("PlaceHolders.NoHome");
                     }

                     if (ymlxx.getBoolean("drop")) {
                        result_check = Variable.Lang_YML.getString("PlaceHolders.Enable");
                     } else {
                        result_check = Variable.Lang_YML.getString("PlaceHolders.Disable");
                     }
                  }
               }

               if (check.equalsIgnoreCase("World_drop")) {
                  if (!Util.CheckIsHome(player.getPlayer().getWorld().getName().replace(Variable.world_prefix, ""))) {
                     return Variable.Lang_YML.getString("PlaceHolders.NotIsHome");
                  }

                  if (Variable.bungee) {
                     if (MySQL.getdropitem(player.getPlayer().getWorld().getName().replace(Variable.world_prefix, "")).equalsIgnoreCase("true")) {
                        result_check = Variable.Lang_YML.getString("PlaceHolders.Enable");
                     } else {
                        result_check = Variable.Lang_YML.getString("PlaceHolders.Disable");
                     }
                  } else {
                     File f = new File(Variable.Tempf, player.getPlayer().getWorld().getName().replace(Variable.world_prefix, "") + ".yml");
                     YamlConfiguration ymlxxx = YamlConfiguration.loadConfiguration(f);
                     if (ymlxxx.getBoolean("drop")) {
                        result_check = Variable.Lang_YML.getString("PlaceHolders.Enable");
                     } else {
                        result_check = Variable.Lang_YML.getString("PlaceHolders.Disable");
                     }
                  }
               }

               if (check.equalsIgnoreCase("trustList")) {
                  if (selfHomeName == null || !Util.CheckIsHome(selfHomeName)) {
                     return Variable.Lang_YML.getString("PlaceHolders.NoHome");
                  }

                  if (Variable.bungee) {
                     result_check = MySQL.getMembers(selfHomeName).toString();
                  } else {
                     YamlConfiguration ymlxxx = this.loadHomeYml(selfHomeName);
                     if (ymlxxx == null) {
                        return Variable.Lang_YML.getString("PlaceHolders.NoHome");
                     }

                     List<String> list = ymlxxx.getStringList("Members");
                     result_check = list.toString();
                  }
               }

               if (check.equalsIgnoreCase("World_trustList")) {
                  if (!Util.CheckIsHome(player.getPlayer().getWorld().getName().replace(Variable.world_prefix, ""))) {
                     return Variable.Lang_YML.getString("PlaceHolders.NotIsHome");
                  }

                  if (Variable.bungee) {
                     result_check = MySQL.getMembers(player.getPlayer().getWorld().getName().replace(Variable.world_prefix, "")).toString();
                  } else {
                     File f = new File(Variable.Tempf, player.getPlayer().getWorld().getName().replace(Variable.world_prefix, "") + ".yml");
                     YamlConfiguration ymlxxx = YamlConfiguration.loadConfiguration(f);
                     List<String> list = ymlxxx.getStringList("Members");
                     result_check = list.toString();
                  }
               }

               if (check.equalsIgnoreCase("ManagerList")) {
                  if (selfHomeName == null || !Util.CheckIsHome(selfHomeName)) {
                     return Variable.Lang_YML.getString("PlaceHolders.NoHome");
                  }

                  if (Variable.bungee) {
                     result_check = MySQL.getOP(selfHomeName).toString();
                  } else {
                     YamlConfiguration ymlxxx = this.loadHomeYml(selfHomeName);
                     if (ymlxxx == null) {
                        return Variable.Lang_YML.getString("PlaceHolders.NoHome");
                     }

                     List<String> list = ymlxxx.getStringList("OP");
                     result_check = list.toString();
                  }
               }

               if (check.equalsIgnoreCase("World_ManagerList")) {
                  if (!Util.CheckIsHome(player.getPlayer().getWorld().getName().replace(Variable.world_prefix, ""))) {
                     return Variable.Lang_YML.getString("PlaceHolders.NotIsHome");
                  }

                  if (Variable.bungee) {
                     result_check = MySQL.getOP(player.getPlayer().getWorld().getName().replace(Variable.world_prefix, "")).toString();
                  } else {
                     File f = new File(Variable.Tempf, player.getPlayer().getWorld().getName().replace(Variable.world_prefix, "") + ".yml");
                     YamlConfiguration ymlxxx = YamlConfiguration.loadConfiguration(f);
                     List<String> list = ymlxxx.getStringList("OP");
                     result_check = list.toString();
                  }
               }

               if (check.equalsIgnoreCase("BlackList")) {
                  if (selfHomeName == null || !Util.CheckIsHome(selfHomeName)) {
                     return Variable.Lang_YML.getString("PlaceHolders.NoHome");
                  }

                  if (Variable.bungee) {
                     result_check = MySQL.getDenys(selfHomeName).toString();
                  } else {
                     YamlConfiguration ymlxxx = this.loadHomeYml(selfHomeName);
                     if (ymlxxx == null) {
                        return Variable.Lang_YML.getString("PlaceHolders.NoHome");
                     }

                     List<String> list = ymlxxx.getStringList("Denys");
                     result_check = list.toString();
                  }
               }

               if (check.equalsIgnoreCase("World_BlackList")) {
                  if (!Util.CheckIsHome(player.getPlayer().getWorld().getName().replace(Variable.world_prefix, ""))) {
                     return Variable.Lang_YML.getString("PlaceHolders.NotIsHome");
                  }

                  if (Variable.bungee) {
                     result_check = MySQL.getDenys(player.getPlayer().getWorld().getName().replace(Variable.world_prefix, "")).toString();
                  } else {
                     File f = new File(Variable.Tempf, player.getPlayer().getWorld().getName().replace(Variable.world_prefix, "") + ".yml");
                     YamlConfiguration ymlxxx = YamlConfiguration.loadConfiguration(f);
                     List<String> list = ymlxxx.getStringList("Denys");
                     result_check = list.toString();
                  }
               }

               if (check.equalsIgnoreCase("TrustAmount")) {
                  if (selfHomeName == null || !Util.CheckIsHome(selfHomeName)) {
                     return Variable.Lang_YML.getString("PlaceHolders.NoHome");
                  }

                  if (Variable.bungee) {
                     result_check = String.valueOf(MySQL.getMembers(selfHomeName).size());
                  } else {
                     YamlConfiguration ymlxxx = this.loadHomeYml(selfHomeName);
                     if (ymlxxx == null) {
                        return Variable.Lang_YML.getString("PlaceHolders.NoHome");
                     }

                     result_check = String.valueOf(ymlxxx.getStringList("Members").size());
                  }
               }

               if (check.equalsIgnoreCase("ManagerAmount")) {
                  if (selfHomeName == null || !Util.CheckIsHome(selfHomeName)) {
                     return Variable.Lang_YML.getString("PlaceHolders.NoHome");
                  }

                  if (Variable.bungee) {
                     result_check = String.valueOf(MySQL.getOP(selfHomeName).size());
                  } else {
                     YamlConfiguration ymlxxx = this.loadHomeYml(selfHomeName);
                     if (ymlxxx == null) {
                        return Variable.Lang_YML.getString("PlaceHolders.NoHome");
                     }

                     result_check = String.valueOf(ymlxxx.getStringList("OP").size());
                  }
               }

               if (check.equalsIgnoreCase("Radius")) {
                  if (selfHomeName == null || !Util.CheckIsHome(selfHomeName)) {
                     return Variable.Lang_YML.getString("PlaceHolders.NoHome");
                  }

                  if (Variable.bungee) {
                     result_check = String.valueOf(HomeTerrainPolicy.configuredBorderSize(
                        Integer.valueOf(MySQL.getLevel(selfHomeName)),
                        Main.JavaPlugin.getConfig().getBoolean("HomeTerrain.Enabled", false),
                        Main.JavaPlugin.getConfig().getIntegerList("HomeUpgrade.LevelSizes"),
                        Main.JavaPlugin.getConfig().getInt("WorldBoard"),
                        Main.JavaPlugin.getConfig().getInt("UpdateRadius"),
                        0
                     ));
                  } else {
                     YamlConfiguration ymlxxx = this.loadHomeYml(selfHomeName);
                     if (ymlxxx == null) {
                        return Variable.Lang_YML.getString("PlaceHolders.NoHome");
                     }

                     result_check = String.valueOf(HomeTerrainPolicy.configuredBorderSize(
                        ymlxxx.getInt("Level"),
                        Main.JavaPlugin.getConfig().getBoolean("HomeTerrain.Enabled", false),
                        Main.JavaPlugin.getConfig().getIntegerList("HomeUpgrade.LevelSizes"),
                        Main.JavaPlugin.getConfig().getInt("WorldBoard"),
                        Main.JavaPlugin.getConfig().getInt("UpdateRadius"),
                        0
                     ));
                  }
               }

               if (check.equalsIgnoreCase("World_Radius")) {
                  if (currentHomeName == null || !Util.CheckIsHome(currentHomeName)) {
                     return Variable.Lang_YML.getString("PlaceHolders.NotIsHome");
                  }

                  if (Variable.bungee) {
                     result_check = String.valueOf(HomeTerrainPolicy.configuredBorderSize(
                        Integer.valueOf(MySQL.getLevel(currentHomeName)),
                        Main.JavaPlugin.getConfig().getBoolean("HomeTerrain.Enabled", false),
                        Main.JavaPlugin.getConfig().getIntegerList("HomeUpgrade.LevelSizes"),
                        Main.JavaPlugin.getConfig().getInt("WorldBoard"),
                        Main.JavaPlugin.getConfig().getInt("UpdateRadius"),
                        0
                     ));
                  } else {
                     File f = new File(Variable.Tempf, currentHomeName + ".yml");
                     YamlConfiguration ymlxxx = YamlConfiguration.loadConfiguration(f);
                     result_check = String.valueOf(HomeTerrainPolicy.configuredBorderSize(
                        ymlxxx.getInt("Level"),
                        Main.JavaPlugin.getConfig().getBoolean("HomeTerrain.Enabled", false),
                        Main.JavaPlugin.getConfig().getIntegerList("HomeUpgrade.LevelSizes"),
                        Main.JavaPlugin.getConfig().getInt("WorldBoard"),
                        Main.JavaPlugin.getConfig().getInt("UpdateRadius"),
                        0
                     ));
                  }
               }

               if (check.equalsIgnoreCase("World_NextRadius")) {
                  if (currentHomeName == null || !Util.CheckIsHome(currentHomeName)) {
                     return Variable.Lang_YML.getString("PlaceHolders.NotIsHome");
                  }

                  if (Variable.bungee) {
                     result_check = String.valueOf(HomeTerrainPolicy.configuredBorderSize(
                        Integer.valueOf(MySQL.getLevel(currentHomeName)) + 1,
                        Main.JavaPlugin.getConfig().getBoolean("HomeTerrain.Enabled", false),
                        Main.JavaPlugin.getConfig().getIntegerList("HomeUpgrade.LevelSizes"),
                        Main.JavaPlugin.getConfig().getInt("WorldBoard"),
                        Main.JavaPlugin.getConfig().getInt("UpdateRadius"),
                        0
                     ));
                  } else {
                     File f = new File(Variable.Tempf, currentHomeName + ".yml");
                     YamlConfiguration ymlxxx = YamlConfiguration.loadConfiguration(f);
                     result_check = String.valueOf(HomeTerrainPolicy.configuredBorderSize(
                        ymlxxx.getInt("Level") + 1,
                        Main.JavaPlugin.getConfig().getBoolean("HomeTerrain.Enabled", false),
                        Main.JavaPlugin.getConfig().getIntegerList("HomeUpgrade.LevelSizes"),
                        Main.JavaPlugin.getConfig().getInt("WorldBoard"),
                        Main.JavaPlugin.getConfig().getInt("UpdateRadius"),
                        0
                     ));
                  }
               }

               if (check.equalsIgnoreCase("World_OwnerName")) {
                  if (currentHomeName == null) {
                     return Variable.Lang_YML.getString("PlaceHolders.NotIsHome");
                  }

                  result_check = Util.getHomeOwner(currentHomeName);
               }

               if (check.equalsIgnoreCase("World_OwnerDisplayName")) {
                  if (currentHomeName == null) {
                     return Variable.Lang_YML.getString("PlaceHolders.NotIsHome");
                  }

                  String ownerName = Util.getHomeOwner(currentHomeName);
                  OfflinePlayer owner = Bukkit.getOfflinePlayer(ownerName);
                  result_check = owner != null && owner.getName() != null ? owner.getName() : ownerName;
               }

               if (check.equalsIgnoreCase("World_TeleportLocation")) {
                  if (!Util.CheckIsHome(player.getPlayer().getWorld().getName().replace(Variable.world_prefix, ""))) {
                     return Variable.Lang_YML.getString("PlaceHolders.NotIsHome");
                  }

                  if (Variable.bungee) {
                     result_check = String.valueOf(
                        "§aX: §d"
                           + String.format("%.2f", Double.valueOf(MySQL.getX(player.getPlayer().getWorld().getName())))
                           + "§a,Y: §d"
                           + String.format("%.2f", Double.valueOf(MySQL.getY(player.getPlayer().getWorld().getName())))
                           + "§a,Z: §d"
                           + String.format("%.2f", Double.valueOf(MySQL.getZ(player.getPlayer().getWorld().getName())))
                     );
                  } else {
                     File f = new File(Variable.Tempf, player.getPlayer().getWorld().getName().replace(Variable.world_prefix, "") + ".yml");
                     YamlConfiguration ymlxxx = YamlConfiguration.loadConfiguration(f);
                     result_check = String.valueOf(
                        "§aX: §d"
                           + String.format("%.2f", ymlxxx.getDouble("X"))
                           + "§a,Y: §d"
                           + String.format("%.2f", ymlxxx.getDouble("Y"))
                           + "§a,Z: §d"
                           + String.format("%.2f", ymlxxx.getDouble("Z"))
                     );
                  }
               }

               if (check.equalsIgnoreCase("locktime")) {
                  if (selfHomeName == null || !Util.CheckIsHome(selfHomeName)) {
                     return Variable.Lang_YML.getString("PlaceHolders.NoHome");
                  }

                  if (Variable.bungee) {
                     if (MySQL.getlocktime(selfHomeName).equalsIgnoreCase("true")) {
                        result_check = Variable.Lang_YML.getString("PlaceHolders.Enable");
                     } else {
                        result_check = Variable.Lang_YML.getString("PlaceHolders.Disable");
                     }
                  } else {
                     YamlConfiguration ymlxxx = this.loadHomeYml(selfHomeName);
                     if (ymlxxx == null) {
                        return Variable.Lang_YML.getString("PlaceHolders.NoHome");
                     }

                     if (ymlxxx.getBoolean("locktime")) {
                        result_check = Variable.Lang_YML.getString("PlaceHolders.Enable");
                     } else {
                        result_check = Variable.Lang_YML.getString("PlaceHolders.Disable");
                     }
                  }
               }

               if (check.equalsIgnoreCase("World_locktime")) {
                  if (!Util.CheckIsHome(player.getPlayer().getWorld().getName().replace(Variable.world_prefix, ""))) {
                     return Variable.Lang_YML.getString("PlaceHolders.NotIsHome");
                  }

                  if (Variable.bungee) {
                     if (MySQL.getlocktime(player.getPlayer().getWorld().getName().replace(Variable.world_prefix, "")).equalsIgnoreCase("true")) {
                        result_check = Variable.Lang_YML.getString("PlaceHolders.Enable");
                     } else {
                        result_check = Variable.Lang_YML.getString("PlaceHolders.Disable");
                     }
                  } else {
                     File f = new File(Variable.Tempf, player.getPlayer().getWorld().getName().replace(Variable.world_prefix, "") + ".yml");
                     YamlConfiguration ymlxxxx = YamlConfiguration.loadConfiguration(f);
                     if (ymlxxxx.getBoolean("locktime")) {
                        result_check = Variable.Lang_YML.getString("PlaceHolders.Enable");
                     } else {
                        result_check = Variable.Lang_YML.getString("PlaceHolders.Disable");
                     }
                  }
               }

               if (check.equalsIgnoreCase("lockweather")) {
                  if (selfHomeName == null || !Util.CheckIsHome(selfHomeName)) {
                     return Variable.Lang_YML.getString("PlaceHolders.NoHome");
                  }

                  if (Variable.bungee) {
                     if (MySQL.getlockweather(selfHomeName).equalsIgnoreCase("true")) {
                        result_check = Variable.Lang_YML.getString("PlaceHolders.Enable");
                     } else {
                        result_check = Variable.Lang_YML.getString("PlaceHolders.Disable");
                     }
                  } else {
                     YamlConfiguration ymlxxxx = this.loadHomeYml(selfHomeName);
                     if (ymlxxxx == null) {
                        return Variable.Lang_YML.getString("PlaceHolders.NoHome");
                     }

                     if (ymlxxxx.getBoolean("lockweather")) {
                        result_check = Variable.Lang_YML.getString("PlaceHolders.Enable");
                     } else {
                        result_check = Variable.Lang_YML.getString("PlaceHolders.Disable");
                     }
                  }
               }

               if (check.equalsIgnoreCase("World_lockweather")) {
                  if (!Util.CheckIsHome(player.getPlayer().getWorld().getName().replace(Variable.world_prefix, ""))) {
                     return Variable.Lang_YML.getString("PlaceHolders.NotIsHome");
                  }

                  if (Variable.bungee) {
                     if (MySQL.getlockweather(player.getPlayer().getWorld().getName().replace(Variable.world_prefix, "")).equalsIgnoreCase("true")) {
                        result_check = Variable.Lang_YML.getString("PlaceHolders.Enable");
                     } else {
                        result_check = Variable.Lang_YML.getString("PlaceHolders.Disable");
                     }
                  } else {
                     File f = new File(Variable.Tempf, player.getPlayer().getWorld().getName().replace(Variable.world_prefix, "") + ".yml");
                     YamlConfiguration ymlxxxxx = YamlConfiguration.loadConfiguration(f);
                     if (ymlxxxxx.getBoolean("lockweather")) {
                        result_check = Variable.Lang_YML.getString("PlaceHolders.Enable");
                     } else {
                        result_check = Variable.Lang_YML.getString("PlaceHolders.Disable");
                     }
                  }
               }

               if (check.equalsIgnoreCase("Public")) {
                  if (selfHomeName == null || !Util.CheckIsHome(selfHomeName)) {
                     return Variable.Lang_YML.getString("PlaceHolders.NoHome");
                  }

                  if (Variable.bungee) {
                     if (MySQL.getPublic(selfHomeName).equalsIgnoreCase("true")) {
                        result_check = Variable.Lang_YML.getString("PlaceHolders.Public");
                     } else {
                        result_check = Variable.Lang_YML.getString("PlaceHolders.NoPublic");
                     }
                  } else {
                     YamlConfiguration ymlxxxxx = this.loadHomeYml(selfHomeName);
                     if (ymlxxxxx == null) {
                        return Variable.Lang_YML.getString("PlaceHolders.NoHome");
                     }

                     if (ymlxxxxx.getBoolean("Public")) {
                        result_check = Variable.Lang_YML.getString("PlaceHolders.Public");
                     } else {
                        result_check = Variable.Lang_YML.getString("PlaceHolders.NoPublic");
                     }
                  }
               }

               if (check.equalsIgnoreCase("World_Public")) {
                  if (!Util.CheckIsHome(player.getPlayer().getWorld().getName().replace(Variable.world_prefix, ""))) {
                     return Variable.Lang_YML.getString("PlaceHolders.NotIsHome");
                  }

                  if (Variable.bungee) {
                     if (MySQL.getPublic(player.getPlayer().getWorld().getName().replace(Variable.world_prefix, "")).equalsIgnoreCase("true")) {
                        result_check = Variable.Lang_YML.getString("PlaceHolders.Public");
                     } else {
                        result_check = Variable.Lang_YML.getString("PlaceHolders.NoPublic");
                     }
                  } else {
                     File f = new File(Variable.Tempf, player.getPlayer().getWorld().getName().replace(Variable.world_prefix, "") + ".yml");
                     YamlConfiguration ymlxxxxxx = YamlConfiguration.loadConfiguration(f);
                     if (ymlxxxxxx.getBoolean("Public")) {
                        result_check = Variable.Lang_YML.getString("PlaceHolders.Public");
                     } else {
                        result_check = Variable.Lang_YML.getString("PlaceHolders.NoPublic");
                     }
                  }
               }

               if (check.equalsIgnoreCase("time")) {
                  Long time = player.getPlayer().getWorld().getTime();
                  Date date = new Date(time);
                  result_check = String.valueOf(date.getHours() + ":" + date.getMinutes() + ":" + date.getSeconds());
               }

               if (check.equalsIgnoreCase("UpdateMoney")) {
                  if (selfHomeName == null || !Util.CheckIsHome(selfHomeName)) {
                     return Variable.Lang_YML.getString("PlaceHolders.NoHome");
                  }

                  if (Variable.bungee) {
                     if (Main.JavaPlugin.getConfig().getInt("MaxLevel") != Integer.valueOf(MySQL.getLevel(selfHomeName))) {
                        result_check = String.valueOf(
                           Main.JavaPlugin.getConfig().getDoubleList("MoneyNeed").get(Integer.valueOf(MySQL.getLevel(selfHomeName)) - 1)
                        );
                     } else {
                        result_check = Variable.Lang_YML.getString("PlaceHolders.HasAlreadyReachLevelMax");
                     }
                  } else {
                     YamlConfiguration ymlxxxxxx = this.loadHomeYml(selfHomeName);
                     if (ymlxxxxxx == null) {
                        return Variable.Lang_YML.getString("PlaceHolders.NoHome");
                     }

                     if (Main.JavaPlugin.getConfig().getInt("MaxLevel") != ymlxxxxxx.getInt("Level")) {
                        result_check = String.valueOf(Main.JavaPlugin.getConfig().getDoubleList("MoneyNeed").get(ymlxxxxxx.getInt("Level") - 1));
                     } else {
                        result_check = Variable.Lang_YML.getString("PlaceHolders.HasAlreadyReachLevelMax");
                     }
                  }
               }

               if (check.equalsIgnoreCase("UpdatePoints")) {
                  if (selfHomeName == null || !Util.CheckIsHome(selfHomeName)) {
                     return Variable.Lang_YML.getString("PlaceHolders.NoHome");
                  }

                  if (Variable.bungee) {
                     if (Main.JavaPlugin.getConfig().getInt("MaxLevel") != Integer.valueOf(MySQL.getLevel(selfHomeName))) {
                        result_check = String.valueOf(
                           Main.JavaPlugin.getConfig().getDoubleList("PointsNeed").get(Integer.valueOf(MySQL.getLevel(selfHomeName)) - 1)
                        );
                     } else {
                        result_check = Variable.Lang_YML.getString("PlaceHolders.HasAlreadyReachLevelMax");
                     }
                  } else {
                     YamlConfiguration ymlxxxxxxx = this.loadHomeYml(selfHomeName);
                     if (ymlxxxxxxx == null) {
                        return Variable.Lang_YML.getString("PlaceHolders.NoHome");
                     }

                     if (Main.JavaPlugin.getConfig().getInt("MaxLevel") != ymlxxxxxxx.getInt("Level")) {
                        result_check = String.valueOf(Main.JavaPlugin.getConfig().getDoubleList("PointsNeed").get(ymlxxxxxxx.getInt("Level") - 1));
                     } else {
                        result_check = Variable.Lang_YML.getString("PlaceHolders.HasAlreadyReachLevelMax");
                     }
                  }
               }

               if (check.equalsIgnoreCase("UpdateItems")) {
                  if (selfHomeName == null || !Util.CheckIsHome(selfHomeName)) {
                     return Variable.Lang_YML.getString("PlaceHolders.NoHome");
                  }

                  if (Variable.bungee) {
                     if (Main.JavaPlugin.getConfig().getInt("MaxLevel") != Integer.valueOf(MySQL.getLevel(selfHomeName))) {
                        result_check = String.valueOf(
                           ((String)Main.JavaPlugin.getConfig().getStringList("ItemsNeed").get(Integer.valueOf(MySQL.getLevel(selfHomeName)) - 1)).split(",")[0]
                        );
                     } else {
                        result_check = Variable.Lang_YML.getString("PlaceHolders.HasAlreadyReachLevelMax");
                     }
                  } else {
                     YamlConfiguration ymlxxxxxxxx = this.loadHomeYml(selfHomeName);
                     if (ymlxxxxxxxx == null) {
                        return Variable.Lang_YML.getString("PlaceHolders.NoHome");
                     }

                     if (Main.JavaPlugin.getConfig().getInt("MaxLevel") != ymlxxxxxxxx.getInt("Level")) {
                        result_check = String.valueOf(
                           ((String)Main.JavaPlugin.getConfig().getStringList("ItemsNeed").get(ymlxxxxxxxx.getInt("Level") - 1)).split(",")[0]
                        );
                     } else {
                        result_check = Variable.Lang_YML.getString("PlaceHolders.HasAlreadyReachLevelMax");
                     }
                  }
               }

               if (check.equalsIgnoreCase("UpdateItemsChineseName")) {
                  if (selfHomeName == null || !Util.CheckIsHome(selfHomeName)) {
                     return Variable.Lang_YML.getString("PlaceHolders.NoHome");
                  }

                  if (Variable.bungee) {
                     if (Main.JavaPlugin.getConfig().getInt("MaxLevel") != Integer.valueOf(MySQL.getLevel(selfHomeName))) {
                        result_check = String.valueOf(
                           ((String)Main.JavaPlugin.getConfig().getStringList("ItemsChineseName").get(Integer.valueOf(MySQL.getLevel(selfHomeName)) - 1))
                              .split(",")[0]
                        );
                     } else {
                        result_check = Variable.Lang_YML.getString("PlaceHolders.HasAlreadyReachLevelMax");
                     }
                  } else {
                     YamlConfiguration ymlxxxxxxxxx = this.loadHomeYml(selfHomeName);
                     if (ymlxxxxxxxxx == null) {
                        return Variable.Lang_YML.getString("PlaceHolders.NoHome");
                     }

                     if (Main.JavaPlugin.getConfig().getInt("MaxLevel") != ymlxxxxxxxxx.getInt("Level")) {
                        result_check = String.valueOf(
                           ((String)Main.JavaPlugin.getConfig().getStringList("ItemsChineseName").get(ymlxxxxxxxxx.getInt("Level") - 1)).split(",")[0]
                        );
                     } else {
                        result_check = Variable.Lang_YML.getString("PlaceHolders.HasAlreadyReachLevelMax");
                     }
                  }
               }

               if (check.equalsIgnoreCase("UpdateItemsAmount")) {
                  if (selfHomeName == null || !Util.CheckIsHome(selfHomeName)) {
                     return Variable.Lang_YML.getString("PlaceHolders.NoHome");
                  }

                  if (Variable.bungee) {
                     if (Main.JavaPlugin.getConfig().getInt("MaxLevel") != Integer.valueOf(MySQL.getLevel(selfHomeName))) {
                        result_check = String.valueOf(
                           ((String)Main.JavaPlugin.getConfig().getStringList("ItemsNeed").get(Integer.valueOf(MySQL.getLevel(selfHomeName)) - 1)).split(",")[1]
                        );
                     } else {
                        result_check = Variable.Lang_YML.getString("PlaceHolders.HasAlreadyReachLevelMax");
                     }
                  } else {
                     YamlConfiguration ymlxxxxxxxxxx = this.loadHomeYml(selfHomeName);
                     if (ymlxxxxxxxxxx == null) {
                        return Variable.Lang_YML.getString("PlaceHolders.NoHome");
                     }

                     if (Main.JavaPlugin.getConfig().getInt("MaxLevel") != ymlxxxxxxxxxx.getInt("Level")) {
                        result_check = String.valueOf(
                           ((String)Main.JavaPlugin.getConfig().getStringList("ItemsNeed").get(ymlxxxxxxxxxx.getInt("Level") - 1)).split(",")[1]
                        );
                     } else {
                        result_check = Variable.Lang_YML.getString("PlaceHolders.HasAlreadyReachLevelMax");
                     }
                  }
               }

               if (check.equalsIgnoreCase("UpdateRadius")) {
                  result_check = String.valueOf(Main.JavaPlugin.getConfig().getInt("UpdateRadius"));
               }

               if (check.equalsIgnoreCase("MaxTiles")) {
                  result_check = String.valueOf(Main.JavaPlugin.getConfig().getInt("MaxTiles"));
               }

               if (check.equalsIgnoreCase("UnLoadTiles")) {
                  result_check = String.valueOf(Main.JavaPlugin.getConfig().getInt("UnLoadTiles"));
               }

               if (check.equalsIgnoreCase("MaxOP")) {
                  if (selfHomeName != null && Util.CheckIsHome(selfHomeName)) {
                     com.Util.Home home = HomeAPI.getHome(selfHomeName);
                     int value = Main.JavaPlugin.getConfig().getInt("MaxOP");
                     if (home != null) {
                        value += Math.max(0, home.getExtraOpSlots());
                     }

                     result_check = String.valueOf(value);
                  } else {
                     result_check = String.valueOf(Main.JavaPlugin.getConfig().getInt("MaxOP"));
                  }
               }

               if (check.equalsIgnoreCase("MaxJoin")) {
                  if (selfHomeName != null && Util.CheckIsHome(selfHomeName)) {
                     com.Util.Home home = HomeAPI.getHome(selfHomeName);
                     int value = Main.JavaPlugin.getConfig().getInt("MaxJoin");
                     if (home != null) {
                        value += Math.max(0, home.getExtraMemberSlots());
                     }

                     result_check = String.valueOf(value);
                  } else {
                     result_check = String.valueOf(Main.JavaPlugin.getConfig().getInt("MaxJoin"));
                  }
               }

               if (check.equalsIgnoreCase("MaxLevel")) {
                  result_check = String.valueOf(Main.JavaPlugin.getConfig().getInt("MaxLevel"));
               }

               if (check.equalsIgnoreCase("MaxDelete")) {
                  result_check = String.valueOf(Main.JavaPlugin.getConfig().getInt("MaxDelete"));
               }

               if (check.equalsIgnoreCase("BiomeSingleCost")) {
                  result_check = String.valueOf(Main.JavaPlugin.getConfig().getInt("BiomeChange.SingleChunkCost"));
               }

               if (check.equalsIgnoreCase("BiomeAllCost")) {
                  result_check = String.valueOf(Main.JavaPlugin.getConfig().getInt("BiomeChange.AllChunksCost"));
               }

               if (check.equalsIgnoreCase("DeleteItems")) {
                  result_check = String.valueOf(Main.JavaPlugin.getConfig().getInt("DeleteItems"));
               }

               if (check.equalsIgnoreCase("DeleteEntities")) {
                  result_check = String.valueOf(Main.JavaPlugin.getConfig().getInt("DeleteEntities"));
               }

               if (check.equalsIgnoreCase("Server")) {
                  result_check = String.valueOf(Main.JavaPlugin.getConfig().getString("Server"));
               }

               if (check.equalsIgnoreCase("Prefix")) {
                  result_check = String.valueOf(Main.JavaPlugin.getConfig().getString("Prefix"));
               }

               if (check.equalsIgnoreCase("Normal_WorldBoard")) {
                  result_check = String.valueOf(Main.JavaPlugin.getConfig().getInt("WorldBoard"));
               }

               if (check.equalsIgnoreCase("KeepInventory")) {
                   if (Platform.gameRuleValue(player.getPlayer().getWorld(), "keepInventory").equalsIgnoreCase("true")) {
                     result_check = Variable.Lang_YML.getString("PlaceHolders.Enable");
                  } else {
                     result_check = Variable.Lang_YML.getString("PlaceHolders.Disable");
                  }
               }

               if (check.equalsIgnoreCase("doMobSpawning")) {
                   if (Platform.gameRuleValue(player.getPlayer().getWorld(), "doMobSpawning").equalsIgnoreCase("true")) {
                     result_check = Variable.Lang_YML.getString("PlaceHolders.Enable");
                  } else {
                     result_check = Variable.Lang_YML.getString("PlaceHolders.Disable");
                  }
               }

               if (check.equalsIgnoreCase("mobGriefing")) {
                   if (Platform.gameRuleValue(player.getPlayer().getWorld(), "mobGriefing").equalsIgnoreCase("false")) {
                     result_check = Variable.Lang_YML.getString("PlaceHolders.Enable");
                  } else {
                     result_check = Variable.Lang_YML.getString("PlaceHolders.Disable");
                  }
               }

               if (check.equalsIgnoreCase("doFireTick")) {
                   if (Platform.gameRuleValue(player.getPlayer().getWorld(), "doFireTick").equalsIgnoreCase("false")) {
                     result_check = Variable.Lang_YML.getString("PlaceHolders.Enable");
                  } else {
                     result_check = Variable.Lang_YML.getString("PlaceHolders.Disable");
                  }
               }

               if (check.equalsIgnoreCase("World_Difficulty")) {
                  if (player.getPlayer().getWorld().getDifficulty() == Difficulty.EASY) {
                     result_check = "简单";
                  } else if (player.getPlayer().getWorld().getDifficulty() == Difficulty.NORMAL) {
                     result_check = "普通";
                  } else if (player.getPlayer().getWorld().getDifficulty() == Difficulty.HARD) {
                     result_check = "困难";
                  } else if (player.getPlayer().getWorld().getDifficulty() == Difficulty.PEACEFUL) {
                     result_check = "和平";
                  }
               }

               if (check.equalsIgnoreCase("World_generateStructures")) {
                  if (player.getPlayer().getWorld().canGenerateStructures()) {
                     result_check = Variable.Lang_YML.getString("PlaceHolders.Enable");
                  } else {
                     result_check = Variable.Lang_YML.getString("PlaceHolders.Disable");
                  }
               }

               if (check.equalsIgnoreCase("WorldList")) {
                  List<String> list = new ArrayList<>();
                  if (Variable.bungee) {
                     List<String> lis = MySQL.getAllWorlds();
                     list = lis;
                  } else {
                     File folder = new File(Variable.Tempf);

                     File[] arrayOfFile;
                     for (File tempxxxxxx : arrayOfFile = folder.listFiles()) {
                        String want_to = tempxxxxxx.getPath().replace(Variable.Tempf, "").replace(".yml", "").replace(Variable.file_loc_prefix, "");
                        list.add(want_to);
                     }
                  }

                  result_check = list.toString();
               }

               if (check.equalsIgnoreCase("WorldAmount")) {
                  if (Variable.bungee) {
                     result_check = String.valueOf(MySQL.getAllWorldsAmount());
                  } else {
                     File folder = new File(Variable.Tempf);
                     result_check = String.valueOf(folder.listFiles().length);
                  }
               }

               if (check.equalsIgnoreCase("WholeMaxDelete")) {
                  result_check = String.valueOf(Math.max(1, Main.JavaPlugin.getConfig().getInt("MaxOwnedHomes", 3)));
               }

               if (check.equalsIgnoreCase("WholeDelete")) {
                  result_check = String.valueOf(HomeAPI.getOwnedHomes(player.getPlayer().getName()).size());
               }

               if (check.equalsIgnoreCase("LoadWorlds")) {
                  int amount = 0;

                  for (World world : Bukkit.getWorlds()) {
                     if (Util.CheckIsHome(world.getName().replace(Variable.world_prefix, ""))) {
                        amount++;
                     }
                  }

                  result_check = String.valueOf(amount);
               }

               if (check.toLowerCase().contains("HasPermission_")) {
                  String tempxxxxxx = check.replace("HasPermission_", "");
                  if (com.Util.Perm.has(player.getPlayer(), "ErrorTown." + tempxxxxxx)) {
                     result_check = String.valueOf(Variable.Lang_YML.getString("PlaceHolders.HasPermision"));
                  } else {
                     result_check = String.valueOf(Variable.Lang_YML.getString("PlaceHolders.NoPermission"));
                  }
               }

               if (check.equalsIgnoreCase("hasPermission")) {
                  if (!Util.CheckIsHome(player.getPlayer().getWorld().getName().replace(Variable.world_prefix, ""))) {
                     return Variable.Lang_YML.getString("PlaceHolders.NotIsHome");
                  }

                  if (Util.Check(player.getPlayer(), player.getPlayer().getWorld().getName().replace(Variable.world_prefix, ""))) {
                     result_check = "true";
                  } else {
                     result_check = "false";
                  }
               }

               if (check.toUpperCase().contains("LEVELTOP_")) {
                  String level = check.toUpperCase().replace("LEVELTOP_", "");
                  if (Variable.bungee) {
                     result_check = MySQL.getLevelTop(level);
                  } else {
                     File folder = new File(Variable.Tempf);
                     List<Home> homelist = new ArrayList<>();

                     File[] arrayOfFile;
                     for (File tempxxxxxx : arrayOfFile = folder.listFiles()) {
                        String want_to = tempxxxxxx.getPath().replace(Variable.Tempf, "").replace(".yml", "").replace(Variable.file_loc_prefix, "");
                        YamlConfiguration ymlxxxxxxxxxxx = YamlConfiguration.loadConfiguration(tempxxxxxx);
                        Home home = new Home(want_to, ymlxxxxxxxxxxx.getInt("Level"), ymlxxxxxxxxxxx.getInt("flowers"), ymlxxxxxxxxxxx.getInt("popularity"));
                        homelist.add(home);
                     }

                     for (int i = 0; i < homelist.size() - 1; i++) {
                        for (int j = 0; j < homelist.size() - 1 - i; j++) {
                           if (homelist.get(j).level < homelist.get(j + 1).level) {
                              Home tempxxxxxx = homelist.get(j);
                              homelist.set(j, homelist.get(j + 1));
                              homelist.set(j + 1, tempxxxxxx);
                           }
                        }
                     }

                     if (homelist.get(Integer.valueOf(level) - 1) == null) {
                        return Variable.Lang_YML.getString("PlaceHolders.NoHome");
                     }

                     result_check = homelist.get(Integer.valueOf(level) - 1).name;
                  }
               }

               if (check.equalsIgnoreCase("MyLevelTop")) {
                  if (Variable.bungee) {
                     if (!MySQL.alreadyhastheplayerhome(player.getPlayer().getName())) {
                        return Variable.Lang_YML.getString("PlaceHolders.NoHome");
                     }

                     result_check = String.valueOf(MySQL.getMyLevelTop(player.getPlayer().getName()));
                  } else {
                     File folder = new File(Variable.Tempf);
                     List<Home> homelist = new ArrayList<>();

                     File[] arrayOfFilex;
                     for (File tempxxxxxx : arrayOfFilex = folder.listFiles()) {
                        String want_to = tempxxxxxx.getPath().replace(Variable.Tempf, "").replace(".yml", "").replace(Variable.file_loc_prefix, "");
                        YamlConfiguration ymlxxxxxxxxxxx = YamlConfiguration.loadConfiguration(tempxxxxxx);
                        Home home = new Home(want_to, ymlxxxxxxxxxxx.getInt("Level"), ymlxxxxxxxxxxx.getInt("flowers"), ymlxxxxxxxxxxx.getInt("popularity"));
                        homelist.add(home);
                     }

                     for (int i = 0; i < homelist.size() - 1; i++) {
                        for (int jx = 0; jx < homelist.size() - 1 - i; jx++) {
                           if (homelist.get(jx).level < homelist.get(jx + 1).level) {
                              Home tempxxxxxx = homelist.get(jx);
                              homelist.set(jx, homelist.get(jx + 1));
                              homelist.set(jx + 1, tempxxxxxx);
                           }
                        }
                     }

                     int i = 0;
                     boolean check_contain = false;

                     for (i = 0; i < homelist.size(); i++) {
                        if (primaryHomeName != null && homelist.get(i).name.equalsIgnoreCase(primaryHomeName)) {
                           check_contain = true;
                           break;
                        }
                     }

                     if (!check_contain) {
                        return Variable.Lang_YML.getString("PlaceHolders.NoHome");
                     }

                     result_check = String.valueOf(i + 1);
                  }
               }

               if (check.toUpperCase().contains("FlowerTOP_".toUpperCase())) {
                  String level = check.toUpperCase().replace("FlowerTOP_".toUpperCase(), "");
                  if (Variable.bungee) {
                     result_check = MySQL.getFlowerTop(level);
                  } else {
                     File folder = new File(Variable.Tempf);
                     List<Home> homelist = new ArrayList<>();

                     File[] arrayOfFilexx;
                     for (File tempxxxxxx : arrayOfFilexx = folder.listFiles()) {
                        String want_to = tempxxxxxx.getPath().replace(Variable.Tempf, "").replace(".yml", "").replace(Variable.file_loc_prefix, "");
                        YamlConfiguration ymlxxxxxxxxxxx = YamlConfiguration.loadConfiguration(tempxxxxxx);
                        Home home = new Home(want_to, ymlxxxxxxxxxxx.getInt("Level"), ymlxxxxxxxxxxx.getInt("flowers"), ymlxxxxxxxxxxx.getInt("popularity"));
                        homelist.add(home);
                     }

                     for (int i = 0; i < homelist.size() - 1; i++) {
                        for (int jxx = 0; jxx < homelist.size() - 1 - i; jxx++) {
                           if (homelist.get(jxx).flowers < homelist.get(jxx + 1).flowers) {
                              Home tempxxxxxx = homelist.get(jxx);
                              homelist.set(jxx, homelist.get(jxx + 1));
                              homelist.set(jxx + 1, tempxxxxxx);
                           }
                        }
                     }

                     if (homelist.get(Integer.valueOf(level) - 1) == null) {
                        return Variable.Lang_YML.getString("PlaceHolders.NoHome");
                     }

                     result_check = homelist.get(Integer.valueOf(level) - 1).name;
                  }
               }

               if (check.toUpperCase().contains("PopularityTOP_".toUpperCase())) {
                  String level = check.toUpperCase().replace("PopularityTOP_".toUpperCase(), "");
                  if (Variable.bungee) {
                     result_check = MySQL.getPopularity(level);
                  } else {
                     File folder = new File(Variable.Tempf);
                     List<Home> homelist = new ArrayList<>();

                     File[] arrayOfFilexxx;
                     for (File tempxxxxxx : arrayOfFilexxx = folder.listFiles()) {
                        String want_to = tempxxxxxx.getPath().replace(Variable.Tempf, "").replace(".yml", "").replace(Variable.file_loc_prefix, "");
                        YamlConfiguration ymlxxxxxxxxxxx = YamlConfiguration.loadConfiguration(tempxxxxxx);
                        Home home = new Home(want_to, ymlxxxxxxxxxxx.getInt("Level"), ymlxxxxxxxxxxx.getInt("flowers"), ymlxxxxxxxxxxx.getInt("popularity"));
                        homelist.add(home);
                     }

                     for (int i = 0; i < homelist.size() - 1; i++) {
                        for (int jxxx = 0; jxxx < homelist.size() - 1 - i; jxxx++) {
                           if (homelist.get(jxxx).popularity < homelist.get(jxxx + 1).popularity) {
                              Home tempxxxxxx = homelist.get(jxxx);
                              homelist.set(jxxx, homelist.get(jxxx + 1));
                              homelist.set(jxxx + 1, tempxxxxxx);
                           }
                        }
                     }

                     if (homelist.get(Integer.valueOf(level) - 1) == null) {
                        return Variable.Lang_YML.getString("PlaceHolders.NoHome");
                     }

                     result_check = homelist.get(Integer.valueOf(level) - 1).name;
                  }
               }

               if (check.equalsIgnoreCase("MyFlowerTop")) {
                  if (Variable.bungee) {
                     if (!MySQL.alreadyhastheplayerhome(player.getPlayer().getName())) {
                        return Variable.Lang_YML.getString("PlaceHolders.NoHome");
                     }

                     result_check = String.valueOf(MySQL.getMyLevelTop(player.getPlayer().getName()));
                  } else {
                     File folder = new File(Variable.Tempf);
                     List<Home> homelist = new ArrayList<>();

                     File[] arrayOfFilexxxx;
                     for (File tempxxxxxx : arrayOfFilexxxx = folder.listFiles()) {
                        String want_to = tempxxxxxx.getPath().replace(Variable.Tempf, "").replace(".yml", "").replace(Variable.file_loc_prefix, "");
                        YamlConfiguration ymlxxxxxxxxxxx = YamlConfiguration.loadConfiguration(tempxxxxxx);
                        Home home = new Home(want_to, ymlxxxxxxxxxxx.getInt("Level"), ymlxxxxxxxxxxx.getInt("flowers"), ymlxxxxxxxxxxx.getInt("popularity"));
                        homelist.add(home);
                     }

                     for (int i = 0; i < homelist.size() - 1; i++) {
                        for (int jxxxx = 0; jxxxx < homelist.size() - 1 - i; jxxxx++) {
                           if (homelist.get(jxxxx).flowers < homelist.get(jxxxx + 1).flowers) {
                              Home tempxxxxxx = homelist.get(jxxxx);
                              homelist.set(jxxxx, homelist.get(jxxxx + 1));
                              homelist.set(jxxxx + 1, tempxxxxxx);
                           }
                        }
                     }

                     int i = 0;
                     boolean check_contain = false;

                     for (i = 0; i < homelist.size(); i++) {
                        if (primaryHomeName != null && homelist.get(i).name.equalsIgnoreCase(primaryHomeName)) {
                           check_contain = true;
                           break;
                        }
                     }

                     if (!check_contain) {
                        return Variable.Lang_YML.getString("PlaceHolders.NoHome");
                     }

                     result_check = String.valueOf(i + 1);
                  }
               }

               if (check.equalsIgnoreCase("MyPopularityTop")) {
                  if (Variable.bungee) {
                     if (!MySQL.alreadyhastheplayerhome(player.getPlayer().getName())) {
                        return Variable.Lang_YML.getString("PlaceHolders.NoHome");
                     }

                     result_check = String.valueOf(MySQL.getMyLevelTop(player.getPlayer().getName()));
                  } else {
                     File folder = new File(Variable.Tempf);
                     List<Home> homelist = new ArrayList<>();

                     File[] arrayOfFilexxxxx;
                     for (File tempxxxxxx : arrayOfFilexxxxx = folder.listFiles()) {
                        String want_to = tempxxxxxx.getPath().replace(Variable.Tempf, "").replace(".yml", "").replace(Variable.file_loc_prefix, "");
                        YamlConfiguration ymlxxxxxxxxxxx = YamlConfiguration.loadConfiguration(tempxxxxxx);
                        Home home = new Home(want_to, ymlxxxxxxxxxxx.getInt("Level"), ymlxxxxxxxxxxx.getInt("flowers"), ymlxxxxxxxxxxx.getInt("popularity"));
                        homelist.add(home);
                     }

                     for (int i = 0; i < homelist.size() - 1; i++) {
                        for (int jxxxxx = 0; jxxxxx < homelist.size() - 1 - i; jxxxxx++) {
                           if (homelist.get(jxxxxx).popularity < homelist.get(jxxxxx + 1).popularity) {
                              Home tempxxxxxx = homelist.get(jxxxxx);
                              homelist.set(jxxxxx, homelist.get(jxxxxx + 1));
                              homelist.set(jxxxxx + 1, tempxxxxxx);
                           }
                        }
                     }

                     int i = 0;
                     boolean check_contain = false;

                     for (i = 0; i < homelist.size(); i++) {
                        if (primaryHomeName != null && homelist.get(i).name.equalsIgnoreCase(primaryHomeName)) {
                           check_contain = true;
                           break;
                        }
                     }

                     if (!check_contain) {
                        return Variable.Lang_YML.getString("PlaceHolders.NoHome");
                     }

                     result_check = String.valueOf(i + 1);
                  }
               }

               if (check.equalsIgnoreCase("Flower")) {
                  if (selfHomeName == null || !Util.CheckIsHome(selfHomeName)) {
                     return Variable.Lang_YML.getString("PlaceHolders.NoHome");
                  }

                  com.Util.Home home = HomeAPI.getHome(selfHomeName);
                  result_check = String.valueOf(home.getFlowers());
               }

               if (check.equalsIgnoreCase("World_Flower")) {
                  if (!Util.CheckIsHome(player.getPlayer().getWorld().getName().replace(Variable.world_prefix, ""))) {
                     return Variable.Lang_YML.getString("PlaceHolders.NoHome");
                  }

                  com.Util.Home home = HomeAPI.getHome(player.getPlayer().getWorld().getName().replace(Variable.world_prefix, ""));
                  result_check = String.valueOf(home.getFlowers());
               }

               if (check.equalsIgnoreCase("Popularity")) {
                  if (selfHomeName == null || !Util.CheckIsHome(selfHomeName)) {
                     return Variable.Lang_YML.getString("PlaceHolders.NoHome");
                  }

                  com.Util.Home home = HomeAPI.getHome(selfHomeName);
                  result_check = String.valueOf(home.getPopularity());
               }

               if (check.equalsIgnoreCase("World_Popularity")) {
                  if (!Util.CheckIsHome(player.getPlayer().getWorld().getName().replace(Variable.world_prefix, ""))) {
                     return Variable.Lang_YML.getString("PlaceHolders.NoHome");
                  }

                  com.Util.Home home = HomeAPI.getHome(player.getPlayer().getWorld().getName().replace(Variable.world_prefix, ""));
                  result_check = String.valueOf(home.getPopularity());
               }

               if (check.equalsIgnoreCase("Calc")) {
                  if (selfHomeName == null || !Util.CheckIsHome(selfHomeName)) {
                     return Variable.Lang_YML.getString("PlaceHolders.NoHome");
                  }

                  com.Util.Home home = HomeAPI.getHome(selfHomeName);
                  result_check = String.valueOf(
                     home.getPopularity() * Main.JavaPlugin.getConfig().getDouble("PopularityAdd")
                        + home.getFlowers() * Main.JavaPlugin.getConfig().getDouble("FlowerAdd")
                  );
               }

               if (check.equalsIgnoreCase("World_Calc")) {
                  if (!Util.CheckIsHome(player.getPlayer().getWorld().getName().replace(Variable.world_prefix, ""))) {
                     return Variable.Lang_YML.getString("PlaceHolders.NoHome");
                  }

                  com.Util.Home home = HomeAPI.getHome(player.getPlayer().getWorld().getName().replace(Variable.world_prefix, ""));
                  result_check = String.valueOf(
                     home.getPopularity() * Main.JavaPlugin.getConfig().getDouble("PopularityAdd")
                        + home.getFlowers() * Main.JavaPlugin.getConfig().getDouble("FlowerAdd")
                  );
               }

               if (check.toUpperCase().contains("RankTOP_".toUpperCase())) {
                  int level = Integer.valueOf(check.toUpperCase().replace("RankTOP_".toUpperCase(), ""));
                  result_check = getRankName(level);
               }

               if (player != null) {
                  putCache(player.getPlayer().getName(), check, result_check);
               }

               return result_check != null ? result_check : "";
            }
         }
      }
   }
}
