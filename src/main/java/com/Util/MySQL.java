package com.Util;

import com.ErrorTown.Main;
import com.ErrorTown.Variable;
import com.ErrorTown.ScheduledTasks;
import com.zaxxer.hikari.HikariDataSource;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.scheduler.BukkitRunnable;

public class MySQL {
   private static final String CREATE_Users_TABLE = "CREATE TABLE IF NOT EXISTS ErrorTown_Users (Name VARCHAR(255),Members VARCHAR(255),OP VARCHAR(255),Denys VARCHAR(255),Public VARCHAR(255),Level VARCHAR(255),pvp VARCHAR(255),pickup VARCHAR(255),dropitem VARCHAR(255),Server VARCHAR(255),locktime VARCHAR(255),lockweather VARCHAR(255),time VARCHAR(255),X VARCHAR(255),Y VARCHAR(255),Z VARCHAR(255),flowers VARCHAR(100),popularity VARCHAR(100),gifts TEXT default null,advertisement VARCHAR(255),icon VARCHAR(255),visittime VARCHAR(255),limitblock varchar(255))";
   private static final String CREATE_Servers_TABLE = "CREATE TABLE IF NOT EXISTS ErrorTown_Servers (Server VarChar(100),Amount double)";
   private static final String Find_the_Lowest_Server = "SELECT * From ErrorTown_Servers Order by Amount ASC";
   private static final String Find_the_Highest_Server = "SELECT * From ErrorTown_Servers Order by Amount DESC";
   private static final String Update_Server_Statistic = "UPDATE ErrorTown_Servers Set Amount = ? Where Server = ?";
   private static final String Insert_Server = "INSERT INTO ErrorTown_Servers VALUES(?,?)";
   private static final String Has_Already_Contain_The_Server = "Select * From ErrorTown_Servers Where Server = ?";
   private static final String Get_Amount = "SELECT Amount FROM ErrorTown_Servers WHERE Server = ?";
   private static final String Already_has_the_player_home = "SELECT * FROM ErrorTown_Users Where Name = ?";
   private static final String Search_Home = "SELECT * FROM ErrorTown_Users WHERE Name = ?";
   private static final String Get_Flowers = "SELECT flowers FROM ErrorTown_Users WHERE Name = ?";
   private static final String Get_Popularity = "SELECT popularity FROM ErrorTown_Users WHERE Name = ?";
   private static final String Get_Gifts = "SELECT gifts FROM ErrorTown_Users WHERE Name = ?";
   private static final String Get_LimitBlock = "SELECT limitblock FROM ErrorTown_Users WHERE Name = ?";
   private static final String Get_advertisement = "SELECT advertisement FROM ErrorTown_Users WHERE Name = ?";
   private static final String Get_icon = "SELECT icon FROM ErrorTown_Users WHERE Name = ?";
   private static final String Get_visittime = "SELECT visittime FROM ErrorTown_Users WHERE Name = ?";
   private static final String Get_X = "SELECT X FROM ErrorTown_Users WHERE Name = ?";
   private static final String Get_Y = "SELECT Y FROM ErrorTown_Users WHERE Name = ?";
   private static final String Get_Z = "SELECT Z FROM ErrorTown_Users WHERE Name = ?";
   private static final String Get_Members = "SELECT Members FROM ErrorTown_Users WHERE Name = ?";
   private static final String Get_OP = "SELECT OP FROM ErrorTown_Users WHERE Name = ?";
   private static final String Get_Denys = "SELECT Denys FROM ErrorTown_Users WHERE Name = ?";
   private static final String Get_Level = "SELECT Level FROM ErrorTown_Users WHERE Name = ?";
   private static final String Get_Public = "SELECT Public FROM ErrorTown_Users WHERE Name = ?";
   private static final String Get_pvp = "SELECT pvp FROM ErrorTown_Users WHERE Name = ?";
   private static final String Get_pickup = "SELECT pickup FROM ErrorTown_Users WHERE Name = ?";
   private static final String Get_dropitem = "SELECT dropitem FROM ErrorTown_Users WHERE Name = ?";
   private static final String Get_locktime = "SELECT locktime FROM ErrorTown_Users WHERE Name = ?";
   private static final String Get_lockweather = "SELECT lockweather FROM ErrorTown_Users WHERE Name = ?";
   private static final String Get_time = "SELECT time FROM ErrorTown_Users WHERE Name = ?";
   private static final String Update_Flowers = "UPDATE ErrorTown_Users Set flowers = ? Where Name = ?";
   private static final String Update_Popularity = "UPDATE ErrorTown_Users Set popularity = ? Where Name = ?";
   private static final String Update_Gift = "UPDATE ErrorTown_Users Set gifts = ? Where Name = ?";
   private static final String Update_icon = "UPDATE ErrorTown_Users Set icon = ? Where Name = ?";
   private static final String Update_LimitBlock = "UPDATE ErrorTown_Users Set limitblock = ? Where Name = ?";
   private static final String Update_advertisement = "UPDATE ErrorTown_Users Set advertisement = ? Where Name = ?";
   private static final String Update_visittime = "UPDATE ErrorTown_Users Set visittime = ? Where Name = ?";
   private static final String Update_X = "UPDATE ErrorTown_Users Set X = ? Where Name = ?";
   private static final String Update_Y = "UPDATE ErrorTown_Users Set Y = ? Where Name = ?";
   private static final String Update_Z = "UPDATE ErrorTown_Users Set Z = ? Where Name = ?";
   private static final String Update_Members = "UPDATE ErrorTown_Users Set Members = ? Where Name = ?";
   private static final String Update_OP = "UPDATE ErrorTown_Users Set OP = ? Where Name = ?";
   private static final String Update_Denys = "UPDATE ErrorTown_Users Set Denys = ? Where Name = ?";
   private static final String Update_Level = "UPDATE ErrorTown_Users Set Level = ? Where Name = ?";
   private static final String Update_Public = "UPDATE ErrorTown_Users Set Public = ? Where Name = ?";
   private static final String Update_pvp = "UPDATE ErrorTown_Users Set pvp = ? Where Name = ?";
   private static final String Update_pickup = "UPDATE ErrorTown_Users Set pickup = ? Where Name = ?";
   private static final String Update_dropitem = "UPDATE ErrorTown_Users Set dropitem = ? Where Name = ?";
   private static final String Update_locktime = "UPDATE ErrorTown_Users Set locktime = ? Where Name = ?";
   private static final String Update_lockweather = "UPDATE ErrorTown_Users Set lockweather = ? Where Name = ?";
   private static final String Update_time = "UPDATE ErrorTown_Users Set time = ? Where Name = ?";
   private static final String Update_Server = "UPDATE ErrorTown_Users Set Server = ? Where Name = ?";
   private static final String Insert_Value = "INSERT INTO ErrorTown_Users VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
   private static final String Remove_Player = "DELETE From ErrorTown_Users Where Name = ?";
   private static final String Get_ALL = "SELECT * FROM ErrorTown_Users";
   private static final String Import_Check = "SELECT * FROM ErrorTown_Users WHERE Name = ?";
   private static final String DESC_LEVEL = "SELECT * FROM ErrorTown_Users Order by Level DESC";
   private static final String DESC_FLOWER = "SELECT * FROM ErrorTown_Users Order by flowers DESC";
   private static final String DESC_POPULARITY = "SELECT * FROM ErrorTown_Users Order by popularity DESC";
   private static final String Find_Homes_By_OP = "SELECT * FROM ErrorTown_Users WHERE OP LIKE ?";
   private static final String Find_Homes_By_Members = "SELECT * FROM ErrorTown_Users WHERE Members LIKE ?";
   private static final String ALTER_Add_flowers = "alter table ErrorTown_Users add column flowers VARCHAR(100) default '0'";
   private static final String ALTER_Add_popularity = "alter table ErrorTown_Users add column popularity VARCHAR(100) default '0'";
   private static final String ALTER_Add_gifts = "alter table ErrorTown_Users add column gifts TEXT default NULL";
   private static final String ALTER_Add_advertisement = "alter table ErrorTown_Users add column advertisement VARCHAR(255)";
   private static final String ALTER_Add_icon = "alter table ErrorTown_Users add column icon VARCHAR(255)";
   private static final String ALTER_Add_visittime = "alter table ErrorTown_Users add column visittime VARCHAR(255)";
   private static final String ALTER_Add_limitblock = "alter table ErrorTown_Users add column limitblock VARCHAR(255)";
   static ConsoleCommandSender console = Bukkit.getConsoleSender();

   public static void autoUpdateServer() {
      (new BukkitRunnable() {
         public void run() {
            Connection con = MySQL.getConnection();
            PreparedStatement ps = null;
            ResultSet res = null;
            YamlConfiguration yamlConfiguration = YamlConfiguration.loadConfiguration(Variable.f_log);
            if (!yamlConfiguration.contains("NowID")) {
               yamlConfiguration.set("NowID", 0);
            }

            if (!yamlConfiguration.contains("MaxID")) {
               yamlConfiguration.set("MaxID", 255);
            }

            try {
               yamlConfiguration.save(Variable.f_log);
            } catch (IOException ioFailure) {
               ioFailure.printStackTrace();
            }

            int nowID = yamlConfiguration.getInt("NowID");
            int MaxID = yamlConfiguration.getInt("MaxID");
            if (nowID < MaxID) {
               try {
                  ps = con.prepareStatement(Has_Already_Contain_The_Server, ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
                  ps.setString(1, Main.JavaPlugin.getConfig().getString("Server"));
                  res = ps.executeQuery();
                  res.last();
                  int amount = res.getRow();
                  if (amount == 0) {
                     ps = con.prepareStatement(Insert_Server);
                     ps.setString(1, Main.JavaPlugin.getConfig().getString("Server"));
                     if (Main.JavaPlugin.getConfig().getString("DecideBy").equalsIgnoreCase("Player")) {
                        ps.setDouble(2, Bukkit.getOnlinePlayers().size());
                     } else if (Bukkit.getVersion().contains("1.7.10")) {
                        ps.setDouble(2, R1_7_10.getTps());
                     } else {
                        double se1 = Double.valueOf(PlaceholderAPI.setPlaceholders(null, "%server_tps_1%").replace("*", ""));
                        double se2 = Double.valueOf(PlaceholderAPI.setPlaceholders(null, "%server_tps_5%").replace("*", ""));
                        double se3 = Double.valueOf(PlaceholderAPI.setPlaceholders(null, "%server_tps_15%").replace("*", ""));
                        ps.setDouble(2, (se1 + se2 + se3) / 3.0);
                     }

                     ps.executeUpdate();
                  } else {
                     ps = con.prepareStatement(Update_Server_Statistic);
                     if (Main.JavaPlugin.getConfig().getString("DecideBy").equalsIgnoreCase("Player")) {
                        ps.setDouble(1, Bukkit.getOnlinePlayers().size());
                     } else if (Bukkit.getVersion().contains("1.7.10")) {
                        ps.setDouble(1, R1_7_10.getTps());
                     } else {
                        double se1 = Double.valueOf(PlaceholderAPI.setPlaceholders(null, "%server_tps_1%").replace("*", ""));
                        double se2 = Double.valueOf(PlaceholderAPI.setPlaceholders(null, "%server_tps_5%").replace("*", ""));
                        double se3 = Double.valueOf(PlaceholderAPI.setPlaceholders(null, "%server_tps_15%").replace("*", ""));
                        ps.setDouble(1, (se1 + se2 + se3) / 3.0);
                     }

                     ps.setString(2, Main.JavaPlugin.getConfig().getString("Server"));
                     ps.executeUpdate();
                  }
               } catch (SQLException sqlFailure) {
                  sqlFailure.printStackTrace();
               } finally {
                  try {
                     if (res != null) {
                        res.close();
                     }

                     if (ps != null) {
                        ps.close();
                     }

                     if (con != null) {
                        con.close();
                     }
                  } catch (SQLException closeFailure) {
                     closeFailure.printStackTrace();
                  }
               }
            }
         }
      }).runTaskTimerAsynchronously(Main.JavaPlugin, 0L, 60L);
   }

   public static String getLowerstLagServer() {
      Connection con = getConnection();
      PreparedStatement ps = null;
      ResultSet res = null;
      String result = null;

      try {
         ps = con.prepareStatement(Find_the_Lowest_Server);
         res = ps.executeQuery();
         if (res.next()) {
            result = res.getString("Server");
         }
      } catch (SQLException sqlFailure) {
         sqlFailure.printStackTrace();
      } finally {
         try {
            if (res != null) {
               res.close();
            }

            if (ps != null) {
               ps.close();
            }

            if (con != null) {
               con.close();
            }
         } catch (SQLException closeFailure) {
            closeFailure.printStackTrace();
         }
      }

      return result;
   }

   public static String getHighestTPSServer() {
      Connection con = getConnection();
      PreparedStatement ps = null;
      ResultSet res = null;
      String result = null;

      try {
         ps = con.prepareStatement(Find_the_Highest_Server);
         res = ps.executeQuery();
         if (res.next()) {
            result = res.getString("Server");
         }
      } catch (SQLException sqlFailure) {
         sqlFailure.printStackTrace();
      } finally {
         try {
            if (res != null) {
               res.close();
            }

            if (ps != null) {
               ps.close();
            }

            if (con != null) {
               con.close();
            }
         } catch (SQLException closeFailure) {
            closeFailure.printStackTrace();
         }
      }

      return result;
   }

   public static double getServerAmount(String server) {
      Connection con = getConnection();
      PreparedStatement ps = null;
      ResultSet res = null;
      double amount = 0.0;

      try {
         ps = con.prepareStatement(Get_Amount);
         ps.setString(1, server);
         res = ps.executeQuery();
         if (res.next()) {
            amount = res.getDouble("Amount");
         }
      } catch (SQLException sqlFailure) {
         sqlFailure.printStackTrace();
      } finally {
         try {
            if (res != null) {
               res.close();
            }

            if (ps != null) {
               ps.close();
            }

            if (con != null) {
               con.close();
            }
         } catch (SQLException closeFailure) {
            closeFailure.printStackTrace();
         }
      }

      return amount;
   }

   public static boolean alreadyhastheplayerhome(String name) {
      Connection con = getConnection();
      PreparedStatement ps = null;
      ResultSet res = null;
      int ss = 0;

      try {
         ps = con.prepareStatement(Already_has_the_player_home);
         ps.setString(1, name);
         res = ps.executeQuery();
         if (res.next()) {
            ss++;
         }
      } catch (SQLException sqlFailure) {
         sqlFailure.printStackTrace();
      } finally {
         try {
            if (res != null) {
               res.close();
            }

            if (ps != null) {
               ps.close();
            }

            if (con != null) {
               con.close();
            }
         } catch (SQLException closeFailure) {
            closeFailure.printStackTrace();
         }
      }

      return ss != 0;
   }

   public static boolean CheckIsAHome(String name) {
      Connection con = getConnection();
      PreparedStatement ps = null;
      ResultSet res = null;
      boolean check = false;

      try {
         ps = con.prepareStatement(Search_Home);
         ps.setString(1, name.replace(Variable.world_prefix, ""));
         res = ps.executeQuery();
         if (res.next()) {
            check = true;
         }
      } catch (SQLException sqlFailure) {
         sqlFailure.printStackTrace();
      } finally {
         try {
            if (res != null) {
               res.close();
            }

            if (ps != null) {
               ps.close();
            }

            if (con != null) {
               con.close();
            }
         } catch (SQLException closeFailure) {
            closeFailure.printStackTrace();
         }
      }

      return check;
   }

   public static boolean alreadyhastheplayerjoin(String name) {
      Connection con = getConnection();
      PreparedStatement ps = null;
      ResultSet res = null;
      boolean check = false;

      try {
         ps = con.prepareStatement(Find_Homes_By_OP);
         ps.setString(1, "%" + name + "%");
         res = ps.executeQuery();
         if (res.next()) {
            check = true;
         }
      } catch (SQLException sqlFailure) {
         sqlFailure.printStackTrace();
      } finally {
         try {
            if (res != null) {
               res.close();
            }

            if (ps != null) {
               ps.close();
            }

            if (con != null) {
               con.close();
            }
         } catch (SQLException closeFailure) {
            closeFailure.printStackTrace();
         }
      }

      return check;
   }

   public static String getJoinHome(String name) {
      Connection con = getConnection();
      PreparedStatement ps = null;
      ResultSet res = null;
      String check = null;

      try {
         ps = con.prepareStatement(Find_Homes_By_OP);
         ps.setString(1, "%" + name + "%");
         res = ps.executeQuery();

         while (res.next()) {
            List<String> list = new ArrayList<>();
            if (res.getString("OP").contains(",")) {
               list = Arrays.asList(res.getString("OP").split(","));
            } else {
               list.add(res.getString("OP"));
            }

            for (String str : list) {
               if (str.equalsIgnoreCase(name)) {
                  check = res.getString("Name");
                  break;
               }
            }

            if (check != null) {
               break;
            }
         }
      } catch (SQLException sqlFailure) {
         sqlFailure.printStackTrace();
      } finally {
         try {
            if (res != null) {
               res.close();
            }

            if (ps != null) {
               ps.close();
            }

            if (con != null) {
               con.close();
            }
         } catch (SQLException closeFailure) {
            closeFailure.printStackTrace();
         }
      }

      return check;
   }

   public static String getJoinServer(String name) {
      Connection con = getConnection();
      PreparedStatement ps = null;
      ResultSet res = null;
      String Server = null;

      try {
         ps = con.prepareStatement(Find_Homes_By_OP);
         ps.setString(1, "%" + name + "%");
         res = ps.executeQuery();
         if (res.next()) {
            Server = res.getString("Server");
         }
      } catch (SQLException sqlFailure) {
         sqlFailure.printStackTrace();
      } finally {
         try {
            if (res != null) {
               res.close();
            }

            if (ps != null) {
               ps.close();
            }

            if (con != null) {
               con.close();
            }
         } catch (SQLException closeFailure) {
            closeFailure.printStackTrace();
         }
      }

      return Server;
   }

   public static String getServer(String name) {
      Connection con = getConnection();
      PreparedStatement ps = null;
      ResultSet res = null;
      String Server = null;

      try {
         ps = con.prepareStatement(Search_Home);
         ps.setString(1, name);
         res = ps.executeQuery();
         if (res.next()) {
            Server = res.getString("Server");
         }
      } catch (SQLException sqlFailure) {
         sqlFailure.printStackTrace();
      } finally {
         try {
            if (res != null) {
               res.close();
            }

            if (ps != null) {
               ps.close();
            }

            if (con != null) {
               con.close();
            }
         } catch (SQLException closeFailure) {
            closeFailure.printStackTrace();
         }
      }

      return Server;
   }

   public static void addFlowersColumn() {
      Connection con = getConnection();
      PreparedStatement ps = null;
      ResultSet res = null;

      try {
         ps = con.prepareStatement(ALTER_Add_flowers);
         ps.executeUpdate();
      } catch (SQLException sqlFailure) {
         try {
            if (res != null) {
               res.close();
            }

            if (ps != null) {
               ps.close();
            }

            if (con != null) {
               con.close();
            }
         } catch (SQLException closeFailure) {
            closeFailure.printStackTrace();
         }
      } finally {
         try {
            if (res != null) {
               res.close();
            }

            if (ps != null) {
               ps.close();
            }

            if (con != null) {
               con.close();
            }
         } catch (SQLException closeFailure) {
            closeFailure.printStackTrace();
         }
      }
   }

   public static void addAdvertisementColumn() {
      Connection con = getConnection();
      PreparedStatement ps = null;
      ResultSet res = null;

      try {
         ps = con.prepareStatement(ALTER_Add_advertisement);
         ps.executeUpdate();
      } catch (SQLException sqlFailure) {
         try {
            if (res != null) {
               res.close();
            }

            if (ps != null) {
               ps.close();
            }

            if (con != null) {
               con.close();
            }
         } catch (SQLException closeFailure) {
            closeFailure.printStackTrace();
         }
      } finally {
         try {
            if (res != null) {
               res.close();
            }

            if (ps != null) {
               ps.close();
            }

            if (con != null) {
               con.close();
            }
         } catch (SQLException closeFailure) {
            closeFailure.printStackTrace();
         }
      }
   }

   public static void addLimitBlockColumn() {
      Connection con = getConnection();
      PreparedStatement ps = null;
      ResultSet res = null;

      try {
         ps = con.prepareStatement(ALTER_Add_limitblock);
         ps.executeUpdate();
      } catch (SQLException sqlFailure) {
         try {
            if (res != null) {
               res.close();
            }

            if (ps != null) {
               ps.close();
            }

            if (con != null) {
               con.close();
            }
         } catch (SQLException closeFailure) {
            closeFailure.printStackTrace();
         }
      } finally {
         try {
            if (res != null) {
               res.close();
            }

            if (ps != null) {
               ps.close();
            }

            if (con != null) {
               con.close();
            }
         } catch (SQLException closeFailure) {
            closeFailure.printStackTrace();
         }
      }
   }

   public static void addIconColumn() {
      Connection con = getConnection();
      PreparedStatement ps = null;
      ResultSet res = null;

      try {
         ps = con.prepareStatement(ALTER_Add_icon);
         ps.executeUpdate();
      } catch (SQLException sqlFailure) {
         try {
            if (res != null) {
               res.close();
            }

            if (ps != null) {
               ps.close();
            }

            if (con != null) {
               con.close();
            }
         } catch (SQLException closeFailure) {
            closeFailure.printStackTrace();
         }
      } finally {
         try {
            if (res != null) {
               res.close();
            }

            if (ps != null) {
               ps.close();
            }

            if (con != null) {
               con.close();
            }
         } catch (SQLException closeFailure) {
            closeFailure.printStackTrace();
         }
      }
   }

   public static void addVisitColumn() {
      Connection con = getConnection();
      PreparedStatement ps = null;
      ResultSet res = null;

      try {
         ps = con.prepareStatement(ALTER_Add_visittime);
         ps.executeUpdate();
      } catch (SQLException sqlFailure) {
         try {
            if (res != null) {
               res.close();
            }

            if (ps != null) {
               ps.close();
            }

            if (con != null) {
               con.close();
            }
         } catch (SQLException closeFailure) {
            closeFailure.printStackTrace();
         }
      } finally {
         try {
            if (res != null) {
               res.close();
            }

            if (ps != null) {
               ps.close();
            }

            if (con != null) {
               con.close();
            }
         } catch (SQLException closeFailure) {
            closeFailure.printStackTrace();
         }
      }
   }

   public static void addPopularityColumn() {
      Connection con = getConnection();
      PreparedStatement ps = null;
      ResultSet res = null;

      try {
         ps = con.prepareStatement(ALTER_Add_popularity);
         ps.executeUpdate();
      } catch (SQLException sqlFailure) {
         try {
            if (res != null) {
               res.close();
            }

            if (ps != null) {
               ps.close();
            }

            if (con != null) {
               con.close();
            }
         } catch (SQLException closeFailure) {
            closeFailure.printStackTrace();
         }
      } finally {
         try {
            if (res != null) {
               res.close();
            }

            if (ps != null) {
               ps.close();
            }

            if (con != null) {
               con.close();
            }
         } catch (SQLException closeFailure) {
            closeFailure.printStackTrace();
         }
      }
   }

   public static void addGiftColumn() {
      Connection con = getConnection();
      PreparedStatement ps = null;
      ResultSet res = null;

      try {
         ps = con.prepareStatement(ALTER_Add_gifts);
         ps.executeUpdate();
      } catch (SQLException sqlFailure) {
         try {
            if (res != null) {
               res.close();
            }

            if (ps != null) {
               ps.close();
            }

            if (con != null) {
               con.close();
            }
         } catch (SQLException closeFailure) {
            closeFailure.printStackTrace();
         }
      } finally {
         try {
            if (res != null) {
               res.close();
            }

            if (ps != null) {
               ps.close();
            }

            if (con != null) {
               con.close();
            }
         } catch (SQLException closeFailure) {
            closeFailure.printStackTrace();
         }
      }
   }

   public static void setFlowers(String name, String value) {
      Connection con = getConnection();
      PreparedStatement ps = null;
      ResultSet res = null;

      try {
         ps = con.prepareStatement(Update_Flowers);
         ps.setString(1, value);
         ps.setString(2, name);
         ps.executeUpdate();
      } catch (SQLException sqlFailure) {
         sqlFailure.printStackTrace();
      } finally {
         try {
            if (res != null) {
               res.close();
            }

            if (ps != null) {
               ps.close();
            }

            if (con != null) {
               con.close();
            }
         } catch (SQLException closeFailure) {
            closeFailure.printStackTrace();
         }
      }
   }

   public static void setPopularity(String name, String value) {
      Connection con = getConnection();
      PreparedStatement ps = null;
      ResultSet res = null;

      try {
         ps = con.prepareStatement(Update_Popularity);
         ps.setString(1, value);
         ps.setString(2, name);
         ps.executeUpdate();
      } catch (SQLException sqlFailure) {
         sqlFailure.printStackTrace();
      } finally {
         try {
            if (res != null) {
               res.close();
            }

            if (ps != null) {
               ps.close();
            }

            if (con != null) {
               con.close();
            }
         } catch (SQLException closeFailure) {
            closeFailure.printStackTrace();
         }
      }
   }

   public static void setGifts(String name, String value) throws IOException {
      Connection con = getConnection();
      PreparedStatement ps = null;
      ResultSet res = null;
      Reader reader = new StringReader(value);

      try {
         ps = con.prepareStatement(Update_Gift);
         ps.setCharacterStream(1, reader);
         ps.setString(2, name);
         ps.executeUpdate();
      } catch (SQLException sqlFailure) {
         sqlFailure.printStackTrace();
      } finally {
         try {
            if (reader != null) {
               reader.close();
            }

            if (res != null) {
               res.close();
            }

            if (ps != null) {
               ps.close();
            }

            if (con != null) {
               con.close();
            }
         } catch (SQLException closeFailure) {
            closeFailure.printStackTrace();
         }
      }
   }

   public static void setIcon(String name, String value) {
      Connection con = getConnection();
      PreparedStatement ps = null;
      ResultSet res = null;

      try {
         ps = con.prepareStatement(Update_icon);
         ps.setString(1, value);
         ps.setString(2, name);
         ps.executeUpdate();
      } catch (SQLException sqlFailure) {
         sqlFailure.printStackTrace();
      } finally {
         try {
            if (res != null) {
               res.close();
            }

            if (ps != null) {
               ps.close();
            }

            if (con != null) {
               con.close();
            }
         } catch (SQLException closeFailure) {
            closeFailure.printStackTrace();
         }
      }
   }

   public static String getTitle(String name) {
      return "";
   }

   public static void setTitle(String name, String title) {
   }

   public static List<String> getDescription(String name) {
      return new ArrayList<>();
   }

   public static void setDescription(String name, String description) {
   }

   public static void setVisitTime(String name, String value) {
      Connection con = getConnection();
      PreparedStatement ps = null;
      ResultSet res = null;

      try {
         ps = con.prepareStatement(Update_visittime);
         ps.setString(1, value);
         ps.setString(2, name);
         ps.executeUpdate();
      } catch (SQLException sqlFailure) {
         sqlFailure.printStackTrace();
      } finally {
         try {
            if (res != null) {
               res.close();
            }

            if (ps != null) {
               ps.close();
            }

            if (con != null) {
               con.close();
            }
         } catch (SQLException closeFailure) {
            closeFailure.printStackTrace();
         }
      }
   }

   public static void setAdvertisement(String name, String value) {
      Connection con = getConnection();
      PreparedStatement ps = null;
      ResultSet res = null;

      try {
         ps = con.prepareStatement(Update_advertisement);
         ps.setString(1, value);
         ps.setString(2, name);
         ps.executeUpdate();
      } catch (SQLException sqlFailure) {
         sqlFailure.printStackTrace();
      } finally {
         try {
            if (res != null) {
               res.close();
            }

            if (ps != null) {
               ps.close();
            }

            if (con != null) {
               con.close();
            }
         } catch (SQLException closeFailure) {
            closeFailure.printStackTrace();
         }
      }
   }

   public static void setLimitBlock(String name, String value) {
      Connection con = getConnection();
      PreparedStatement ps = null;
      ResultSet res = null;

      try {
         ps = con.prepareStatement(Update_LimitBlock);
         if (value != null && value.length() != 0 && value.substring(0, 1).equals(",")) {
            value = value.substring(1, value.length());
         }

         ps.setString(1, value);
         ps.setString(2, name);
         ps.executeUpdate();
      } catch (SQLException sqlFailure) {
         sqlFailure.printStackTrace();
      } finally {
         try {
            if (res != null) {
               res.close();
            }

            if (ps != null) {
               ps.close();
            }

            if (con != null) {
               con.close();
            }
         } catch (SQLException closeFailure) {
            closeFailure.printStackTrace();
         }
      }
   }

   public static void setX(String name, String value) {
      Connection con = getConnection();
      PreparedStatement ps = null;
      ResultSet res = null;

      try {
         ps = con.prepareStatement(Update_X);
         ps.setString(1, value);
         ps.setString(2, name);
         ps.executeUpdate();
      } catch (SQLException sqlFailure) {
         sqlFailure.printStackTrace();
      } finally {
         try {
            if (res != null) {
               res.close();
            }

            if (ps != null) {
               ps.close();
            }

            if (con != null) {
               con.close();
            }
         } catch (SQLException closeFailure) {
            closeFailure.printStackTrace();
         }
      }
   }

   public static void setY(String name, String value) {
      Connection con = getConnection();
      PreparedStatement ps = null;
      ResultSet res = null;

      try {
         ps = con.prepareStatement(Update_Y);
         ps.setString(1, value);
         ps.setString(2, name);
         ps.executeUpdate();
      } catch (SQLException sqlFailure) {
         sqlFailure.printStackTrace();
      } finally {
         try {
            if (res != null) {
               res.close();
            }

            if (ps != null) {
               ps.close();
            }

            if (con != null) {
               con.close();
            }
         } catch (SQLException closeFailure) {
            closeFailure.printStackTrace();
         }
      }
   }

   public static void setZ(String name, String value) {
      Connection con = getConnection();
      PreparedStatement ps = null;
      ResultSet res = null;

      try {
         ps = con.prepareStatement(Update_Z);
         ps.setString(1, value);
         ps.setString(2, name);
         ps.executeUpdate();
      } catch (SQLException sqlFailure) {
         sqlFailure.printStackTrace();
      } finally {
         try {
            if (res != null) {
               res.close();
            }

            if (ps != null) {
               ps.close();
            }

            if (con != null) {
               con.close();
            }
         } catch (SQLException closeFailure) {
            closeFailure.printStackTrace();
         }
      }
   }

   public static void settime(String name, String value) {
      Connection con = getConnection();
      PreparedStatement ps = null;
      ResultSet res = null;

      try {
         ps = con.prepareStatement(Update_time);
         ps.setString(1, value);
         ps.setString(2, name);
         ps.executeUpdate();
      } catch (SQLException sqlFailure) {
         sqlFailure.printStackTrace();
      } finally {
         try {
            if (res != null) {
               res.close();
            }

            if (ps != null) {
               ps.close();
            }

            if (con != null) {
               con.close();
            }
         } catch (SQLException closeFailure) {
            closeFailure.printStackTrace();
         }
      }
   }

   public static void setlockweather(String name, String value) {
      Connection con = getConnection();
      PreparedStatement ps = null;
      ResultSet res = null;

      try {
         ps = con.prepareStatement(Update_lockweather);
         ps.setString(1, value);
         ps.setString(2, name);
         ps.executeUpdate();
      } catch (SQLException sqlFailure) {
         sqlFailure.printStackTrace();
      } finally {
         try {
            if (res != null) {
               res.close();
            }

            if (ps != null) {
               ps.close();
            }

            if (con != null) {
               con.close();
            }
         } catch (SQLException closeFailure) {
            closeFailure.printStackTrace();
         }
      }
   }

   public static void setlocktime(String name, String value) {
      Connection con = getConnection();
      PreparedStatement ps = null;
      ResultSet res = null;

      try {
         ps = con.prepareStatement(Update_locktime);
         ps.setString(1, value);
         ps.setString(2, name);
         ps.executeUpdate();
      } catch (SQLException sqlFailure) {
         sqlFailure.printStackTrace();
      } finally {
         try {
            if (res != null) {
               res.close();
            }

            if (ps != null) {
               ps.close();
            }

            if (con != null) {
               con.close();
            }
         } catch (SQLException closeFailure) {
            closeFailure.printStackTrace();
         }
      }
   }

   public static void setdropitem(String name, String value) {
      Connection con = getConnection();
      PreparedStatement ps = null;
      ResultSet res = null;

      try {
         ps = con.prepareStatement(Update_dropitem);
         ps.setString(1, value);
         ps.setString(2, name);
         ps.executeUpdate();
      } catch (SQLException sqlFailure) {
         sqlFailure.printStackTrace();
      } finally {
         try {
            if (res != null) {
               res.close();
            }

            if (ps != null) {
               ps.close();
            }

            if (con != null) {
               con.close();
            }
         } catch (SQLException closeFailure) {
            closeFailure.printStackTrace();
         }
      }
   }

   public static void setpickup(String name, String value) {
      Connection con = getConnection();
      PreparedStatement ps = null;
      ResultSet res = null;

      try {
         ps = con.prepareStatement(Update_pickup);
         ps.setString(1, value);
         ps.setString(2, name);
         ps.executeUpdate();
      } catch (SQLException sqlFailure) {
         sqlFailure.printStackTrace();
      } finally {
         try {
            if (res != null) {
               res.close();
            }

            if (ps != null) {
               ps.close();
            }

            if (con != null) {
               con.close();
            }
         } catch (SQLException closeFailure) {
            closeFailure.printStackTrace();
         }
      }
   }

   public static void setpvp(String name, String value) {
      Connection con = getConnection();
      PreparedStatement ps = null;
      ResultSet res = null;

      try {
         ps = con.prepareStatement(Update_pvp);
         ps.setString(1, value);
         ps.setString(2, name);
         ps.executeUpdate();
      } catch (SQLException sqlFailure) {
         sqlFailure.printStackTrace();
      } finally {
         try {
            if (res != null) {
               res.close();
            }

            if (ps != null) {
               ps.close();
            }

            if (con != null) {
               con.close();
            }
         } catch (SQLException closeFailure) {
            closeFailure.printStackTrace();
         }
      }
   }

   public static void setLevel(String name, String value) {
      Connection con = getConnection();
      PreparedStatement ps = null;
      ResultSet res = null;

      try {
         ps = con.prepareStatement(Update_Level);
         ps.setString(1, value);
         ps.setString(2, name);
         ps.executeUpdate();
      } catch (SQLException sqlFailure) {
         sqlFailure.printStackTrace();
      } finally {
         try {
            if (res != null) {
               res.close();
            }

            if (ps != null) {
               ps.close();
            }

            if (con != null) {
               con.close();
            }
         } catch (SQLException closeFailure) {
            closeFailure.printStackTrace();
         }
      }
   }

   public static void setServer(String name, String value) {
      Connection con = getConnection();
      PreparedStatement ps = null;
      ResultSet res = null;

      try {
         ps = con.prepareStatement(Update_Server);
         ps.setString(1, value);
         ps.setString(2, name);
         ps.executeUpdate();
      } catch (SQLException sqlFailure) {
         sqlFailure.printStackTrace();
      } finally {
         try {
            if (res != null) {
               res.close();
            }

            if (ps != null) {
               ps.close();
            }

            if (con != null) {
               con.close();
            }
         } catch (SQLException closeFailure) {
            closeFailure.printStackTrace();
         }
      }
   }

   public static void setPublic(String name, String value) {
      Connection con = getConnection();
      PreparedStatement ps = null;
      ResultSet res = null;

      try {
         ps = con.prepareStatement(Update_Public);
         ps.setString(1, value);
         ps.setString(2, name);
         ps.executeUpdate();
      } catch (SQLException sqlFailure) {
         sqlFailure.printStackTrace();
      } finally {
         try {
            if (res != null) {
               res.close();
            }

            if (ps != null) {
               ps.close();
            }

            if (con != null) {
               con.close();
            }
         } catch (SQLException closeFailure) {
            closeFailure.printStackTrace();
         }
      }
   }

   public static void setDenys(String name, String value) {
      Connection con = getConnection();
      PreparedStatement ps = null;
      ResultSet res = null;

      try {
         ps = con.prepareStatement(Update_Denys);
         ps.setString(1, value);
         ps.setString(2, name);
         ps.executeUpdate();
      } catch (SQLException sqlFailure) {
         sqlFailure.printStackTrace();
      } finally {
         try {
            if (res != null) {
               res.close();
            }

            if (ps != null) {
               ps.close();
            }

            if (con != null) {
               con.close();
            }
         } catch (SQLException closeFailure) {
            closeFailure.printStackTrace();
         }
      }
   }

   public static void setOP(String name, String value) {
      Connection con = getConnection();
      PreparedStatement ps = null;
      ResultSet res = null;

      try {
         ps = con.prepareStatement(Update_OP);
         ps.setString(1, value);
         ps.setString(2, name);
         ps.executeUpdate();
      } catch (SQLException sqlFailure) {
         sqlFailure.printStackTrace();
      } finally {
         try {
            if (res != null) {
               res.close();
            }

            if (ps != null) {
               ps.close();
            }

            if (con != null) {
               con.close();
            }
         } catch (SQLException closeFailure) {
            closeFailure.printStackTrace();
         }
      }
   }

   public static void setMembers(String name, String value) {
      Connection con = getConnection();
      PreparedStatement ps = null;
      ResultSet res = null;

      try {
         ps = con.prepareStatement(Update_Members);
         ps.setString(1, value);
         ps.setString(2, name);
         ps.executeUpdate();
      } catch (SQLException sqlFailure) {
         sqlFailure.printStackTrace();
      } finally {
         try {
            if (res != null) {
               res.close();
            }

            if (ps != null) {
               ps.close();
            }

            if (con != null) {
               con.close();
            }
         } catch (SQLException closeFailure) {
            closeFailure.printStackTrace();
         }
      }
   }

   public static String getFlowers(String name) {
      Connection con = getConnection();
      PreparedStatement ps = null;
      ResultSet res = null;
      String result = "";

      try {
         ps = con.prepareStatement(Get_Flowers);
         ps.setString(1, name);
         res = ps.executeQuery();
         if (res.next()) {
            result = res.getString("flowers");
         }

         if (result.contains(".")) {
            result = result.split("\\.")[0];
         }
      } catch (SQLException sqlFailure) {
         sqlFailure.printStackTrace();
      } finally {
         try {
            if (res != null) {
               res.close();
            }

            if (ps != null) {
               ps.close();
            }

            if (con != null) {
               con.close();
            }
         } catch (SQLException closeFailure) {
            closeFailure.printStackTrace();
         }
      }

      return result;
   }

   public static String getPopularity(String name) {
      Connection con = getConnection();
      PreparedStatement ps = null;
      ResultSet res = null;
      String result = "";

      try {
         ps = con.prepareStatement(Get_Popularity);
         ps.setString(1, name);
         res = ps.executeQuery();
         if (res.next()) {
            result = res.getString("Popularity");
         }

         if (result.contains(".")) {
            result = result.split("\\.")[0];
         }
      } catch (SQLException sqlFailure) {
         sqlFailure.printStackTrace();
      } finally {
         try {
            if (res != null) {
               res.close();
            }

            if (ps != null) {
               ps.close();
            }

            if (con != null) {
               con.close();
            }
         } catch (SQLException closeFailure) {
            closeFailure.printStackTrace();
         }
      }

      return result;
   }

   public static List<String> getGift(String name) throws IOException {
      Connection con = getConnection();
      PreparedStatement ps = null;
      ResultSet res = null;
      List<String> list = new ArrayList<>();

      try {
         ps = con.prepareStatement(Get_Gifts);
         ps.setString(1, name);
         res = ps.executeQuery();
         if (res.next()) {
            String str = "";
            Reader in = res.getCharacterStream("gifts");
            if (in != null) {
               BufferedReader r = new BufferedReader(in);
               StringBuffer sb = new StringBuffer();

               String line;
               try {
                  while ((line = r.readLine()) != null) {
                     sb.append(line);
                  }
               } catch (IOException ioFailure) {
                  ioFailure.printStackTrace();
               }

               str = sb.toString();
               r.close();
               in.close();
            }

            if (str.contains(",")) {
               list = Arrays.asList(str.split(","));
            } else {
               list.add(str);
            }
         }
      } catch (SQLException sqlFailure) {
         sqlFailure.printStackTrace();
      } finally {
         try {
            if (res != null) {
               res.close();
            }

            if (ps != null) {
               ps.close();
            }

            if (con != null) {
               con.close();
            }
         } catch (SQLException closeFailure) {
            closeFailure.printStackTrace();
         }
      }

      return list;
   }

   public static String getIcon(String name) {
      Connection con = getConnection();
      PreparedStatement ps = null;
      ResultSet res = null;
      String result = "";

      try {
         ps = con.prepareStatement(Get_icon);
         ps.setString(1, name);
         res = ps.executeQuery();
         if (res.next()) {
            String value = res.getString("icon");
            if (value != null) {
               result = value;
            } else {
               result = "";
            }
         }
      } catch (SQLException sqlFailure) {
         sqlFailure.printStackTrace();
      } finally {
         try {
            if (res != null) {
               res.close();
            }

            if (ps != null) {
               ps.close();
            }

            if (con != null) {
               con.close();
            }
         } catch (SQLException closeFailure) {
            closeFailure.printStackTrace();
         }
      }

      return result;
   }

   public static String getVisitTime(String name) {
      Connection con = getConnection();
      PreparedStatement ps = null;
      ResultSet res = null;
      String result = "";

      try {
         ps = con.prepareStatement(Get_visittime);
         ps.setString(1, name);
         res = ps.executeQuery();
         if (res.next()) {
            String value = res.getString("visittime");
            if (value != null) {
               result = value;
            } else {
               result = "";
            }
         }
      } catch (SQLException sqlFailure) {
         sqlFailure.printStackTrace();
      } finally {
         try {
            if (res != null) {
               res.close();
            }

            if (ps != null) {
               ps.close();
            }

            if (con != null) {
               con.close();
            }
         } catch (SQLException closeFailure) {
            closeFailure.printStackTrace();
         }
      }

      return result;
   }

   public static List<String> getAdvertisement(String name) {
      Connection con = getConnection();
      PreparedStatement ps = null;
      ResultSet res = null;
      List<String> list = new ArrayList<>();

      try {
         ps = con.prepareStatement(Get_advertisement);
         ps.setString(1, name);
         res = ps.executeQuery();
         if (res.next()) {
            if (res.getString("advertisement") == null) {
               list.add("");
            } else if (res.getString("advertisement").contains(",")) {
               list = Arrays.asList(res.getString("advertisement").split(","));
            } else {
               list.add(res.getString("advertisement"));
            }
         }
      } catch (SQLException sqlFailure) {
         sqlFailure.printStackTrace();
      } finally {
         try {
            if (res != null) {
               res.close();
            }

            if (ps != null) {
               ps.close();
            }

            if (con != null) {
               con.close();
            }
         } catch (SQLException closeFailure) {
            closeFailure.printStackTrace();
         }
      }

      return list;
   }

   public static List<String> getLimitBlock(String name) {
      Connection con = getConnection();
      PreparedStatement ps = null;
      ResultSet res = null;
      List<String> list = new ArrayList<>();

      try {
         ps = con.prepareStatement(Get_LimitBlock);
         ps.setString(1, name);
         res = ps.executeQuery();
         if (!res.next()) {
            return list;
         }

         String value = res.getString("limitblock");
         if (value == null) {
            return list;
         }
         // Historical rows carry a leading separator.
         if (value.startsWith(",")) {
            value = value.substring(1);
         }
         if (value.contains(",")) {
            return Arrays.asList(value.split(","));
         }
         list.add(value);
         return list;
      } catch (SQLException sqlFailure) {
         sqlFailure.printStackTrace();
         return list;
      } finally {
         try {
            if (res != null) {
               res.close();
            }

            if (ps != null) {
               ps.close();
            }

            if (con != null) {
               con.close();
            }
         } catch (SQLException closeFailure) {
            closeFailure.printStackTrace();
         }
      }
   }

   public static String getX(String name) {
      Connection con = getConnection();
      PreparedStatement ps = null;
      ResultSet res = null;
      String result = "";

      try {
         ps = con.prepareStatement(Get_X);
         ps.setString(1, name);
         res = ps.executeQuery();
         if (res.next()) {
            result = res.getString("X");
         }
      } catch (SQLException sqlFailure) {
         sqlFailure.printStackTrace();
      } finally {
         try {
            if (res != null) {
               res.close();
            }

            if (ps != null) {
               ps.close();
            }

            if (con != null) {
               con.close();
            }
         } catch (SQLException closeFailure) {
            closeFailure.printStackTrace();
         }
      }

      return result;
   }

   public static String getY(String name) {
      Connection con = getConnection();
      PreparedStatement ps = null;
      ResultSet res = null;
      String result = "";

      try {
         ps = con.prepareStatement(Get_Y);
         ps.setString(1, name);
         res = ps.executeQuery();
         if (res.next()) {
            result = res.getString("Y");
         }
      } catch (SQLException sqlFailure) {
         sqlFailure.printStackTrace();
      } finally {
         try {
            if (res != null) {
               res.close();
            }

            if (ps != null) {
               ps.close();
            }

            if (con != null) {
               con.close();
            }
         } catch (SQLException closeFailure) {
            closeFailure.printStackTrace();
         }
      }

      return result;
   }

   public static String getZ(String name) {
      Connection con = getConnection();
      PreparedStatement ps = null;
      ResultSet res = null;
      String result = "";

      try {
         ps = con.prepareStatement(Get_Z);
         ps.setString(1, name);
         res = ps.executeQuery();
         if (res.next()) {
            result = res.getString("Z");
         }
      } catch (SQLException sqlFailure) {
         sqlFailure.printStackTrace();
      } finally {
         try {
            if (res != null) {
               res.close();
            }

            if (ps != null) {
               ps.close();
            }

            if (con != null) {
               con.close();
            }
         } catch (SQLException closeFailure) {
            closeFailure.printStackTrace();
         }
      }

      return result;
   }

   public static List<String> getMembers(String name) {
      if (ScheduledTasks.MEMBERS_redis.containsKey(name)) {
         return ScheduledTasks.MEMBERS_redis.get(name);
      } else {
         Connection con = getConnection();
         PreparedStatement ps = null;
         ResultSet res = null;
         List<String> list = new ArrayList<>();

         try {
            ps = con.prepareStatement(Get_Members);
            ps.setString(1, name);
            res = ps.executeQuery();
            if (res.next()) {
               if (res.getString("Members").contains(",")) {
                  list = Arrays.asList(res.getString("Members").split(","));
               } else {
                  list.add(res.getString("Members"));
               }
            }
         } catch (SQLException sqlFailure) {
            sqlFailure.printStackTrace();
         } finally {
            try {
               if (res != null) {
                  res.close();
               }

               if (ps != null) {
                  ps.close();
               }

               if (con != null) {
                  con.close();
               }
            } catch (SQLException closeFailure) {
               closeFailure.printStackTrace();
            }
         }

         return list;
      }
   }

   public static List<String> getOP(String name) {
      if (ScheduledTasks.OPS_redis.containsKey(name)) {
         return ScheduledTasks.OPS_redis.get(name);
      } else {
         Connection con = getConnection();
         PreparedStatement ps = null;
         ResultSet res = null;
         List<String> list = new ArrayList<>();

         try {
            ps = con.prepareStatement(Get_OP);
            ps.setString(1, name);
            res = ps.executeQuery();
            if (res.next()) {
               if (res.getString("OP").contains(",")) {
                  list = Arrays.asList(res.getString("OP").split(","));
               } else {
                  list.add(res.getString("OP"));
               }
            }
         } catch (SQLException sqlFailure) {
            sqlFailure.printStackTrace();
         } finally {
            try {
               if (res != null) {
                  res.close();
               }

               if (ps != null) {
                  ps.close();
               }

               if (con != null) {
                  con.close();
               }
            } catch (SQLException closeFailure) {
               closeFailure.printStackTrace();
            }
         }

         return list;
      }
   }

   public static List<String> getDenys(String name) {
      Connection con = getConnection();
      PreparedStatement ps = null;
      ResultSet res = null;
      List<String> list = new ArrayList<>();

      try {
         ps = con.prepareStatement(Get_Denys);
         ps.setString(1, name);
         res = ps.executeQuery();
         if (res.next()) {
            if (res.getString("Denys").contains(",")) {
               list = Arrays.asList(res.getString("Denys").split(","));
            } else {
               list.add(res.getString("Denys"));
            }
         }
      } catch (SQLException sqlFailure) {
         sqlFailure.printStackTrace();
      } finally {
         try {
            if (res != null) {
               res.close();
            }

            if (ps != null) {
               ps.close();
            }

            if (con != null) {
               con.close();
            }
         } catch (SQLException closeFailure) {
            closeFailure.printStackTrace();
         }
      }

      return list;
   }

   public static String getPublic(String name) {
      Connection con = getConnection();
      PreparedStatement ps = null;
      ResultSet res = null;
      String result = "";

      try {
         ps = con.prepareStatement(Get_Public);
         ps.setString(1, name);
         res = ps.executeQuery();
         if (res.next()) {
            result = res.getString("Public");
         }
      } catch (SQLException sqlFailure) {
         sqlFailure.printStackTrace();
      } finally {
         try {
            if (res != null) {
               res.close();
            }

            if (ps != null) {
               ps.close();
            }

            if (con != null) {
               con.close();
            }
         } catch (SQLException closeFailure) {
            closeFailure.printStackTrace();
         }
      }

      return result;
   }

   public static String getLevel(String name) {
      Connection con = getConnection();
      PreparedStatement ps = null;
      ResultSet res = null;
      String result = "";

      try {
         ps = con.prepareStatement(Get_Level);
         ps.setString(1, name);
         res = ps.executeQuery();
         if (res.next()) {
            result = res.getString("Level");
         }
      } catch (SQLException sqlFailure) {
         sqlFailure.printStackTrace();
      } finally {
         try {
            if (res != null) {
               res.close();
            }

            if (ps != null) {
               ps.close();
            }

            if (con != null) {
               con.close();
            }
         } catch (SQLException closeFailure) {
            closeFailure.printStackTrace();
         }
      }

      return result;
   }

   public static String getPVP(String name) {
      Connection con = getConnection();
      PreparedStatement ps = null;
      ResultSet res = null;
      String result = "";

      try {
         ps = con.prepareStatement(Get_pvp);
         ps.setString(1, name);
         res = ps.executeQuery();
         if (res.next()) {
            result = res.getString("pvp");
         }
      } catch (SQLException sqlFailure) {
         sqlFailure.printStackTrace();
      } finally {
         try {
            if (res != null) {
               res.close();
            }

            if (ps != null) {
               ps.close();
            }

            if (con != null) {
               con.close();
            }
         } catch (SQLException closeFailure) {
            closeFailure.printStackTrace();
         }
      }

      return result;
   }

   public static String getpickup(String name) {
      Connection con = getConnection();
      PreparedStatement ps = null;
      ResultSet res = null;
      String result = "";

      try {
         ps = con.prepareStatement(Get_pickup);
         ps.setString(1, name);
         res = ps.executeQuery();
         if (res.next()) {
            result = res.getString("pickup");
         }
      } catch (SQLException sqlFailure) {
         sqlFailure.printStackTrace();
      } finally {
         try {
            if (res != null) {
               res.close();
            }

            if (ps != null) {
               ps.close();
            }

            if (con != null) {
               con.close();
            }
         } catch (SQLException closeFailure) {
            closeFailure.printStackTrace();
         }
      }

      return result;
   }

   public static String getdropitem(String name) {
      Connection con = getConnection();
      PreparedStatement ps = null;
      ResultSet res = null;
      String result = "";

      try {
         ps = con.prepareStatement(Get_dropitem);
         ps.setString(1, name);
         res = ps.executeQuery();
         if (res.next()) {
            result = res.getString("dropitem");
         }
      } catch (SQLException sqlFailure) {
         sqlFailure.printStackTrace();
      } finally {
         try {
            if (res != null) {
               res.close();
            }

            if (ps != null) {
               ps.close();
            }

            if (con != null) {
               con.close();
            }
         } catch (SQLException closeFailure) {
            closeFailure.printStackTrace();
         }
      }

      return result;
   }

   public static String getlocktime(String name) {
      Connection con = getConnection();
      PreparedStatement ps = null;
      ResultSet res = null;
      String result = "";

      try {
         ps = con.prepareStatement(Get_locktime);
         ps.setString(1, name);
         res = ps.executeQuery();
         if (res == null) {
            return result;
         }

         if (res.next()) {
            result = res.getString("locktime");
         }
      } catch (SQLException sqlFailure) {
         sqlFailure.printStackTrace();
      } finally {
         try {
            if (res != null) {
               res.close();
            }

            if (ps != null) {
               ps.close();
            }

            if (con != null) {
               con.close();
            }
         } catch (SQLException closeFailure) {
            closeFailure.printStackTrace();
         }
      }

      try {
         if (res != null) {
            res.close();
         }

         if (ps != null) {
            ps.close();
         }

         if (con != null) {
            con.close();
         }
      } catch (SQLException sqlFailure) {
         sqlFailure.printStackTrace();
      }

      return result;
   }

   public static String getlockweather(String name) {
      Connection con = getConnection();
      PreparedStatement ps = null;
      ResultSet res = null;
      String result = "";

      try {
         ps = con.prepareStatement(Get_lockweather);
         ps.setString(1, name);
         res = ps.executeQuery();
         if (res.next()) {
            result = res.getString("lockweather");
         }
      } catch (SQLException sqlFailure) {
         sqlFailure.printStackTrace();
      } finally {
         try {
            if (res != null) {
               res.close();
            }

            if (ps != null) {
               ps.close();
            }

            if (con != null) {
               con.close();
            }
         } catch (SQLException closeFailure) {
            closeFailure.printStackTrace();
         }
      }

      return result;
   }

   public static String gettime(String name) {
      Connection con = getConnection();
      PreparedStatement ps = null;
      ResultSet res = null;
      String result = "";

      try {
         ps = con.prepareStatement(Get_time);
         ps.setString(1, name);
         res = ps.executeQuery();
         if (res.next()) {
            result = res.getString("time");
         }
      } catch (SQLException sqlFailure) {
         sqlFailure.printStackTrace();
      } finally {
         try {
            if (res != null) {
               res.close();
            }

            if (ps != null) {
               ps.close();
            }

            if (con != null) {
               con.close();
            }
         } catch (SQLException closeFailure) {
            closeFailure.printStackTrace();
         }
      }

      return result;
   }

   public static List<String> getAllWorlds() {
      Connection con = getConnection();
      PreparedStatement ps = null;
      ResultSet res = null;
      List<String> list = new ArrayList<>();

      try {
         ps = con.prepareStatement(Get_ALL);
         res = ps.executeQuery();

         while (res.next()) {
            list.add(res.getString("Name"));
         }
      } catch (SQLException sqlFailure) {
         sqlFailure.printStackTrace();
      } finally {
         try {
            if (res != null) {
               res.close();
            }

            if (ps != null) {
               ps.close();
            }

            if (con != null) {
               con.close();
            }
         } catch (SQLException closeFailure) {
            closeFailure.printStackTrace();
         }
      }

      return list;
   }

   public static int getAllWorldsAmount() {
      Connection con = getConnection();
      PreparedStatement ps = null;
      ResultSet res = null;
      int amount = 0;

      try {
         ps = con.prepareStatement(Get_ALL, ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
         res = ps.executeQuery();
         res.last();
         amount = res.getRow();
      } catch (SQLException sqlFailure) {
         sqlFailure.printStackTrace();
      } finally {
         try {
            if (res != null) {
               res.close();
            }

            if (ps != null) {
               ps.close();
            }

            if (con != null) {
               con.close();
            }
         } catch (SQLException closeFailure) {
            closeFailure.printStackTrace();
         }
      }

      return amount;
   }

   public static String getListStringSpiltByDot(List<String> list) {
      Connection con = getConnection();
      PreparedStatement ps = null;
      ResultSet res = null;
      String result = "";
      if (list.size() > 0) {
         result = list.get(0);
      }

      for (int c = 1; c < list.size(); c++) {
         String temp = list.get(c);
         result = result + "," + temp;
      }

      try {
         if (res != null) {
            res.close();
         }

         if (ps != null) {
            ps.close();
         }

         if (con != null) {
            con.close();
         }
      } catch (SQLException sqlFailure) {
         sqlFailure.printStackTrace();
      }

      return result;
   }

   public static boolean PlayerQuitHome(String name) {
      Connection con = getConnection();
      PreparedStatement ps = null;
      ResultSet res = null;
      boolean success = false;

      try {
         ps = con.prepareStatement(Find_Homes_By_OP);
         ps.setString(1, "%" + name + "%");
         res = ps.executeQuery();
         if (res.next()) {
            String OP = res.getString("OP");
            // Element-wise removal. `OP.replace(name, "")` was substring replacement and
            // corrupted neighbouring names (removing "Bo" from "Alice,Bob" gave "Alice,b").
            if (CsvUtil.contains(OP, name)) {
               setOP(res.getString("Name"), CsvUtil.remove(OP, name));
               success = true;
            }
         }
      } catch (SQLException sqlFailure) {
         sqlFailure.printStackTrace();
      } finally {
         try {
            if (res != null) {
               res.close();
            }

            if (ps != null) {
               ps.close();
            }

            if (con != null) {
               con.close();
            }
         } catch (SQLException closeFailure) {
            closeFailure.printStackTrace();
         }
      }

      return success;
   }

   public static void insertvalue(
      String s1,
      String s2,
      String s3,
      String s4,
      String s5,
      String s6,
      String s7,
      String s8,
      String s9,
      String s10,
      String s11,
      String s12,
      String s13,
      String s14,
      String s15,
      String s16,
      String s17,
      String s18,
      String s19,
      String s20,
      String s21,
      String s22,
      String s23
   ) {
      Connection con = getConnection();
      PreparedStatement ps = null;
      ResultSet res = null;

      try {
         ps = con.prepareStatement(Insert_Value);
         ps.setString(1, s1);
         ps.setString(2, s2);
         ps.setString(3, s3);
         ps.setString(4, s4);
         ps.setString(5, s5);
         ps.setString(6, s6);
         ps.setString(7, s7);
         ps.setString(8, s8);
         ps.setString(9, s9);
         ps.setString(10, s10);
         ps.setString(11, s11);
         ps.setString(12, s12);
         ps.setString(13, s13);
         ps.setString(14, s14);
         ps.setString(15, s15);
         ps.setString(16, s16);
         ps.setString(17, s17);
         ps.setString(18, s18);
         ps.setCharacterStream(19, null);
         ps.setString(20, s20);
         ps.setString(21, s21);
         ps.setString(22, s22);
         ps.setString(23, s23);
         ps.executeUpdate();
      } catch (SQLException sqlFailure) {
         sqlFailure.printStackTrace();
      } finally {
         try {
            if (res != null) {
               res.close();
            }

            if (ps != null) {
               ps.close();
            }

            if (con != null) {
               con.close();
            }
         } catch (SQLException closeFailure) {
            closeFailure.printStackTrace();
         }
      }
   }

   public static void removePlayer(String name) {
      Connection con = getConnection();
      PreparedStatement ps = null;
      ResultSet res = null;

      try {
         ps = con.prepareStatement(Remove_Player);
         ps.setString(1, name);
         ps.executeUpdate();
      } catch (SQLException sqlFailure) {
         sqlFailure.printStackTrace();
      } finally {
         try {
            if (res != null) {
               res.close();
            }

            if (ps != null) {
               ps.close();
            }

            if (con != null) {
               con.close();
            }
         } catch (SQLException closeFailure) {
            closeFailure.printStackTrace();
         }
      }
   }

   public static String getFlowerTop(String top) {
      Connection con = getConnection();
      PreparedStatement ps = null;
      ResultSet res = null;
      String result = "";

      try {
         ps = con.prepareStatement(DESC_FLOWER);
         res = ps.executeQuery();

         for (int amount = 1; res.next(); amount++) {
            if (amount == Integer.valueOf(top)) {
               result = res.getString("Name");
               break;
            }
         }
      } catch (SQLException sqlFailure) {
         sqlFailure.printStackTrace();
      } finally {
         try {
            if (res != null) {
               res.close();
            }

            if (ps != null) {
               ps.close();
            }

            if (con != null) {
               con.close();
            }
         } catch (SQLException closeFailure) {
            closeFailure.printStackTrace();
         }
      }

      return result;
   }

   public static String getPopularityTop(String top) {
      Connection con = getConnection();
      PreparedStatement ps = null;
      ResultSet res = null;
      String result = "";

      try {
         ps = con.prepareStatement(DESC_POPULARITY);
         res = ps.executeQuery();

         for (int amount = 1; res.next(); amount++) {
            if (amount == Integer.valueOf(top)) {
               result = res.getString("Name");
               break;
            }
         }
      } catch (SQLException sqlFailure) {
         sqlFailure.printStackTrace();
      } finally {
         try {
            if (res != null) {
               res.close();
            }

            if (ps != null) {
               ps.close();
            }

            if (con != null) {
               con.close();
            }
         } catch (SQLException closeFailure) {
            closeFailure.printStackTrace();
         }
      }

      return result;
   }

   public static String getLevelTop(String top) {
      Connection con = getConnection();
      PreparedStatement ps = null;
      ResultSet res = null;
      String result = "";

      try {
         ps = con.prepareStatement(DESC_LEVEL);
         res = ps.executeQuery();

         for (int amount = 1; res.next(); amount++) {
            if (amount == Integer.valueOf(top)) {
               result = res.getString("Name");
               break;
            }
         }
      } catch (SQLException sqlFailure) {
         sqlFailure.printStackTrace();
      } finally {
         try {
            if (res != null) {
               res.close();
            }

            if (ps != null) {
               ps.close();
            }

            if (con != null) {
               con.close();
            }
         } catch (SQLException closeFailure) {
            closeFailure.printStackTrace();
         }
      }

      return result;
   }

   public static int getMyLevelTop(String name) {
      Connection con = getConnection();
      PreparedStatement ps = null;
      ResultSet res = null;
      int amount = 1;

      try {
         ps = con.prepareStatement(DESC_LEVEL);
         res = ps.executeQuery();

         while (res.next() && !res.getString("Name").equalsIgnoreCase(name)) {
            amount++;
         }
      } catch (SQLException sqlFailure) {
         sqlFailure.printStackTrace();
      } finally {
         try {
            if (res != null) {
               res.close();
            }

            if (ps != null) {
               ps.close();
            }

            if (con != null) {
               con.close();
            }
         } catch (SQLException closeFailure) {
            closeFailure.printStackTrace();
         }
      }

      return amount;
   }

   public static List<String> CheckHasPermission(String name) {
      Connection con = getConnection();
      PreparedStatement ps = null;
      ResultSet res = null;
      List<String> members = new ArrayList<>();

      try {
         ps = con.prepareStatement(Find_Homes_By_Members);
         ps.setString(1, "%" + name + "%");
         res = ps.executeQuery();

         while (res.next()) {
            for (String e : Arrays.asList(res.getString("Members").split(","))) {
               if (e.equalsIgnoreCase(name)) {
                  members.add(res.getString("Name"));
               }
            }
         }
      } catch (SQLException sqlFailure) {
         sqlFailure.printStackTrace();
      } finally {
         try {
            if (res != null) {
               res.close();
            }

            if (ps != null) {
               ps.close();
            }

            if (con != null) {
               con.close();
            }
         } catch (SQLException closeFailure) {
            closeFailure.printStackTrace();
         }
      }

      return members;
   }

   public static void data_import(CommandSender p) {
      Connection con = getConnection();
      PreparedStatement ps = null;
      ResultSet res = null;
      int amount = 0;
      File folder = new File(Main.JavaPlugin.getDataFolder().getPath().toString() + Variable.file_loc_prefix + "playerdata");
      File[] list_files = folder.listFiles();

      for (int c = 0; c < list_files.length; c++) {
         File temp = list_files[c];
         String want_to = temp.getPath()
            .replace(Main.JavaPlugin.getDataFolder().getPath().toString() + Variable.file_loc_prefix + "playerdata", "")
            .replace(".yml", "")
            .replace(Variable.file_loc_prefix, "");
         YamlConfiguration yamlConfiguration = YamlConfiguration.loadConfiguration(temp);

         try {
            ps = con.prepareStatement(Import_Check);
            ps.setString(1, want_to);
            res = ps.executeQuery();
            if (res.next()) {
               String msg = Variable.Lang_YML.getString("ImportButHasAlreadyExist");
               if (msg.contains("<Name>")) {
                  msg = msg.replace("<Name>", want_to);
               }

               p.sendMessage(msg);
               continue;
            }
         } catch (SQLException sqlFailure) {
            sqlFailure.printStackTrace();
         }

         List<String> Members = new ArrayList<>();
         List<String> OPs = new ArrayList<>();
         List<String> Denys = new ArrayList<>();
         List<String> Gifts = new ArrayList<>();
         List<String> Advertisement = new ArrayList<>();
         List<String> LimitBlock = new ArrayList<>();

         for (String a : yamlConfiguration.getStringList("Members")) {
            Members.add(a);
         }

         for (String a : yamlConfiguration.getStringList("OP")) {
            OPs.add(a);
         }

         for (String a : yamlConfiguration.getStringList("Denys")) {
            Denys.add(a);
         }

         for (String a : yamlConfiguration.getStringList("gifts")) {
            Gifts.add(a);
         }

         for (String a : yamlConfiguration.getStringList("limitblock")) {
            LimitBlock.add(a);
         }

         insertvalue(
            want_to,
            getListStringSpiltByDot(Members),
            getListStringSpiltByDot(OPs),
            getListStringSpiltByDot(Denys),
            String.valueOf(yamlConfiguration.getBoolean("Public")),
            String.valueOf(yamlConfiguration.getInt("Level")),
            String.valueOf(yamlConfiguration.getBoolean("pvp")),
            String.valueOf(yamlConfiguration.getBoolean("pickup")),
            String.valueOf(yamlConfiguration.getBoolean("drop")),
            String.valueOf(yamlConfiguration.getString("Server")),
            String.valueOf(yamlConfiguration.getBoolean("locktime")),
            String.valueOf(yamlConfiguration.getBoolean("lockweather")),
            String.valueOf(yamlConfiguration.getLong("time")),
            String.valueOf(yamlConfiguration.getDouble("X")),
            String.valueOf(yamlConfiguration.getDouble("Y")),
            String.valueOf(yamlConfiguration.getDouble("Z")),
            String.valueOf(yamlConfiguration.getDouble("flowers")),
            String.valueOf(yamlConfiguration.getDouble("popularity")),
            getListStringSpiltByDot(Gifts),
            getListStringSpiltByDot(Advertisement),
            yamlConfiguration.getString("icon"),
            "",
            getListStringSpiltByDot(LimitBlock)
         );
         amount++;
         String msg = Variable.Lang_YML.getString("ImportSuccess");
         if (msg.contains("<Name>")) {
            msg = msg.replace("<Name>", want_to);
         }

         p.sendMessage(msg);
      }

      String msg = Variable.Lang_YML.getString("ImportFinal");
      if (msg.contains("<Amount>")) {
         msg = msg.replace("<Amount>", String.valueOf(amount));
      }

      p.sendMessage(msg);

      try {
         if (res != null) {
            res.close();
         }

         if (ps != null) {
            ps.close();
         }

         if (con != null) {
            con.close();
         }
      } catch (SQLException ioFailure) {
         ioFailure.printStackTrace();
      }
   }

   public static void data_export(CommandSender p) {
      Connection con = getConnection();
      PreparedStatement ps = null;
      ResultSet res = null;
      int amount = 0;

      try {
         ps = con.prepareStatement(Get_ALL);
         ResultSet rs = ps.executeQuery();

         while (rs.next()) {
            String name = rs.getString("Name");
            String Public = rs.getString("Public");
            String Level = rs.getString("Level");
            String PVP = rs.getString("pvp");
            String Pickup = rs.getString("pickup");
            String drop = rs.getString("dropitem");
            String Server = rs.getString("Server");
            String locktime = rs.getString("locktime");
            String lockweather = rs.getString("lockweather");
            String time = rs.getString("time");
            String X = rs.getString("X");
            String Y = rs.getString("Y");
            String Z = rs.getString("Z");
            String icon = rs.getString("icon");
            String flowers = rs.getString("flowers");
            String popularity = rs.getString("popularity");
            if (!Server.equalsIgnoreCase(Main.JavaPlugin.getConfig().getString("Server"))) {
               String temp = Variable.Lang_YML.getString("ExportButServerNotEqual");
               if (temp.contains("<Name>")) {
                  temp = temp.replace("<Name>", name);
               }

               if (temp.contains("<Server>")) {
                  temp = temp.replace("<Server>", Server);
               }

               p.sendMessage(temp);
            } else {
               File playerdata = new File(
                  Main.JavaPlugin.getDataFolder().getPath().toString() + Variable.file_loc_prefix + "playerdata" + Variable.file_loc_prefix + name + ".yml"
               );
               if (playerdata.exists()) {
                  String tempx = Variable.Lang_YML.getString("ExportButHasAlreadyExist");
                  if (tempx.contains("<Name>")) {
                     tempx = tempx.replace("<Name>", name);
                  }

                  p.sendMessage(tempx);
               } else {
                  try {
                     playerdata.createNewFile();
                  } catch (IOException ioFailure) {
                     ioFailure.printStackTrace();
                  }

                  try {
                     playerdata.createNewFile();
                  } catch (IOException ioFailure) {
                     ioFailure.printStackTrace();
                  }

                  YamlConfiguration yamlConfiguration = YamlConfiguration.loadConfiguration(playerdata);
                  yamlConfiguration.createSection("Members");
                  yamlConfiguration.createSection("OP");
                  yamlConfiguration.createSection("Denys");
                  yamlConfiguration.createSection("Public");
                  yamlConfiguration.createSection("Level");
                  yamlConfiguration.createSection("pvp");
                  yamlConfiguration.createSection("pickup");
                  yamlConfiguration.createSection("drop");
                  yamlConfiguration.createSection("Server");
                  yamlConfiguration.createSection("locktime");
                  yamlConfiguration.createSection("lockweather");
                  yamlConfiguration.createSection("time");
                  yamlConfiguration.createSection("X");
                  yamlConfiguration.createSection("Y");
                  yamlConfiguration.createSection("Z");
                  yamlConfiguration.createSection("icon");
                  yamlConfiguration.set("Members", getMembers(name));
                  yamlConfiguration.set("OP", getOP(name));
                  yamlConfiguration.set("Denys", getDenys(name));
                  yamlConfiguration.set("Public", Boolean.valueOf(Public));
                  yamlConfiguration.set("Level", Integer.valueOf(Level));
                  yamlConfiguration.set("pvp", Boolean.valueOf(PVP));
                  yamlConfiguration.set("pickup", Boolean.valueOf(Pickup));
                  yamlConfiguration.set("drop", Boolean.valueOf(drop));
                  yamlConfiguration.set("Server", Server);
                  yamlConfiguration.set("locktime", Boolean.valueOf(locktime));
                  yamlConfiguration.set("lockweather", Boolean.valueOf(lockweather));
                  yamlConfiguration.set("time", Long.valueOf(time));
                  yamlConfiguration.set("X", Double.valueOf(X));
                  yamlConfiguration.set("Y", Double.valueOf(Y));
                  yamlConfiguration.set("Z", Double.valueOf(Z));

                  try {
                     yamlConfiguration.set("flowers", Integer.valueOf(flowers));
                  } catch (NumberFormatException malformed) {
                     yamlConfiguration.set("flowers", 0);
                  }

                  try {
                     yamlConfiguration.set("popularity", Integer.valueOf(popularity));
                  } catch (NumberFormatException malformed) {
                     yamlConfiguration.set("popularity", popularity);
                  }

                  try {
                     yamlConfiguration.set("gifts", getGift(name));
                  } catch (IOException ioFailure) {
                     ioFailure.printStackTrace();
                  }

                  yamlConfiguration.set("advertisement", getAdvertisement(name));
                  yamlConfiguration.set("limitblock", getLimitBlock(name));
                  yamlConfiguration.set("icon", icon);

                  try {
                     yamlConfiguration.save(playerdata);
                  } catch (IOException ioFailure) {
                     ioFailure.printStackTrace();
                  }

                  String tempx = Variable.Lang_YML.getString("ExportSuccess");
                  if (tempx.contains("<Name>")) {
                     tempx = tempx.replace("<Name>", name);
                  }

                  p.sendMessage(tempx);
                  amount++;
               }
            }
         }

         String tempx = Variable.Lang_YML.getString("ExportFinal");
         if (tempx.contains("<Amount>")) {
            tempx = tempx.replace("<Amount>", String.valueOf(amount));
         }

         p.sendMessage(tempx);
      } catch (SQLException sqlFailure) {
         sqlFailure.printStackTrace();
      } finally {
         try {
            if (res != null) {
               res.close();
            }

            if (ps != null) {
               ps.close();
            }

            if (con != null) {
               con.close();
            }
         } catch (SQLException closeFailure) {
            closeFailure.printStackTrace();
         }
      }
   }

   public static void init() {
      Connection con = getConnection();
      PreparedStatement ps = null;

      try {
         ps = con.prepareStatement(CREATE_Users_TABLE);
         int ss = ps.executeUpdate();
         if (ss != 0) {
            Bukkit.getConsoleSender().sendMessage(Variable.Lang_YML.getString("CreateDataBaseTableSuccess"));
         }

         PreparedStatement ps2 = getConnection().prepareStatement(CREATE_Servers_TABLE);
         ps2.executeUpdate();
         return;
      } catch (SQLException sqlFailure) {
         Bukkit.getConsoleSender().sendMessage(Variable.Lang_YML.getString("CreateDataBaseTableError"));
         sqlFailure.printStackTrace();
      } finally {
         try {
            if (ps != null) {
               ps.close();
            }

            if (con != null) {
               con.close();
            }
         } catch (SQLException closeFailure) {
            closeFailure.printStackTrace();
         }
      }
   }

   public static Connection getConnection() {
      // Null before the pool is built, in single-server mode where it never is, and between
      // HikariCPUtils.shutdown() and the next setSqlConnectionPool(). Callers already handle a
      // null Connection, so keep returning that instead of throwing from a field dereference.
      HikariDataSource pool = HikariCPUtils.sqlConnectionPool;
      if (pool == null) {
         return null;
      }

      try {
         return pool.getConnection();
      } catch (SQLException sqlFailure) {
         Diag.warnOnce("mysql-get-connection", "Could not borrow a database connection from the pool", sqlFailure);
         return null;
      }
   }
}
