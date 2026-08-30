package com.Util;

import com.ErrorTown.Main;
import com.ErrorTown.Variable;
import de.tr7zw.nbtapi.NBTItem;
import de.tr7zw.nbtapi.NBTTileEntity;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Difficulty;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

public class Util {
   /**
    * The single shared high-water-mark cache for VIP border bonuses. Every border call site
    * (Util.refreshBorder, WBControl, ScheduledTasks, BlockPlaceListener, PlayerMoveListener,
    * PlayerTeleportListener) feeds the same map: a home's remembered bonus is server-wide state,
    * and six separate maps meant six separate ratchets that only saw part of the traffic.
    */
   public static final java.util.Map<String, Integer> border_redis = com.Util.Util.boundedCache(2048);


   public static void forceClearCache(String p, String papi_name) {
      Variable.cache.invalidate(p, papi_name);
   }

   public static boolean chargeMoneyOrPoints(Player p, double moneyCost, int pointsCost, String actionName) {
      if (moneyCost <= 0.0 && pointsCost <= 0) {
         return true;
      } else {
         boolean canUseMoney = false;
         if (moneyCost > 0.0 && Variable.econ != null) {
            canUseMoney = Variable.econ.getBalance(p) >= moneyCost;
         }

         boolean canUsePoints = false;
         if (pointsCost > 0 && Variable.PlyaerPointsModule && Variable.playerPoints != null) {
            canUsePoints = Variable.playerPoints.getAPI().look(p.getUniqueId()) >= pointsCost;
         }

         if (canUseMoney) {
            Variable.econ.withdrawPlayer(p, moneyCost);
            p.sendMessage("§8[§6错误庄园§8] §a已扣除 §e" + (long)moneyCost + " §a金币" + (actionName != null && !actionName.isEmpty() ? "，用于" + actionName : ""));
            return true;
         } else if (canUsePoints) {
            Variable.playerPoints.getAPI().take(p.getUniqueId(), pointsCost);
            p.sendMessage("§8[§6错误庄园§8] §a已扣除 §e" + pointsCost + " §a点券" + (actionName != null && !actionName.isEmpty() ? "，用于" + actionName : ""));
            return true;
         } else {
            if (moneyCost > 0.0 && pointsCost > 0) {
               p.sendMessage("§8[§6错误庄园§8] §c余额不足，需要 §e" + (long)moneyCost + " §c金币 或 §e" + pointsCost + " §c点券");
            } else if (moneyCost > 0.0) {
               p.sendMessage("§8[§6错误庄园§8] §c金币不足，需要 §e" + (long)moneyCost + " §c金币");
            } else {
               p.sendMessage("§8[§6错误庄园§8] §c点券不足，需要 §e" + pointsCost + " §c点券");
            }

            return false;
         }
      }
   }

   public static boolean chargeMoneyExact(Player p, double moneyCost, String actionName) {
      if (moneyCost <= 0.0) {
         return true;
      } else if (Variable.econ != null && !(Variable.econ.getBalance(p) < moneyCost)) {
         Variable.econ.withdrawPlayer(p, moneyCost);
         p.sendMessage("§8[§6错误庄园§8] §a已扣除 §e" + (long)moneyCost + " §a金币" + (actionName != null && !actionName.isEmpty() ? "，用于" + actionName : ""));
         return true;
      } else {
         p.sendMessage("§8[§6错误庄园§8] §c金币不足，需要 §e" + (long)moneyCost + " §c金币");
         return false;
      }
   }

   public static boolean chargePointsExact(Player p, int pointsCost, String actionName) {
      if (pointsCost <= 0) {
         return true;
      } else if (Variable.PlyaerPointsModule && Variable.playerPoints != null) {
         if (Variable.playerPoints.getAPI().look(p.getUniqueId()) < pointsCost) {
            p.sendMessage("§8[§6错误庄园§8] §c点券不足，需要 §e" + pointsCost + " §c点券");
            return false;
         } else {
            Variable.playerPoints.getAPI().take(p.getUniqueId(), pointsCost);
            p.sendMessage("§8[§6错误庄园§8] §a已扣除 §e" + pointsCost + " §a点券" + (actionName != null && !actionName.isEmpty() ? "，用于" + actionName : ""));
            return true;
         }
      } else {
         p.sendMessage("§8[§6错误庄园§8] §c点券系统未安装");
         return false;
      }
   }

   public static int getUpgradeDiscount(Player p) {
      int best = 100;

      try {
         for (String s : Main.JavaPlugin.getConfig().getStringList("VIPDiscount")) {
            String[] parts = s.split(",");
            if (parts.length >= 2) {
               int pct = Integer.parseInt(parts[1].trim());
               if (pct > 0 && pct < best && com.Util.Perm.has(p, "ErrorTown." + parts[0].trim())) {
                  best = pct;
               }
            }
         }
      } catch (Exception failure) {
         com.Util.Diag.warnOnce("vip-discount-parse", "A VIPDiscount entry is malformed (expected \"suffix,percent\"); it is ignored", failure);
      }

      return best;
   }

   public static String discountZheStr(int pct) {
      return pct % 10 == 0 ? "打" + pct / 10 + "折" : "打" + String.format("%.1f", pct / 10.0) + "折";
   }

   public static Location getAir(Location loc) {
      Location clo = loc.clone();

      double i;
      for (i = 255.0; i >= 0.0; i--) {
         Location temp = clo.clone();
         temp.setY(i + 20.0);
         if (loc.getWorld().getBlockAt(temp).getType() != Material.AIR) {
            break;
         }
      }

      if (i != 0.0) {
         return clo;
      } else {
         loc.setY(i);
         return loc;
      }
   }

   public static Location getNotAir(Location loc) {
      return loc;
   }

   public static List<Chunk> getchunkmap(Location loc1, Location loc2, Location loc3, Location loc4) {
      List<Chunk> chunkmap = new ArrayList<>();
      double Ax = loc1.getX();
      double Az = loc1.getZ();
      double Bx = loc2.getX();
      double Bz = loc2.getZ();
      double Dx = loc3.getX();
      double Dz = loc3.getZ();
      double Ex = loc4.getX();
      double Ez = loc4.getZ();
      double minX = Math.min(Ax, Math.min(Bx, Math.min(Dx, Ex)));
      double maxX = Math.max(Ax, Math.max(Bx, Math.max(Dx, Ex)));
      double minZ = Math.min(Az, Math.min(Bz, Math.min(Dz, Ez)));
      double maxZ = Math.max(Az, Math.max(Bz, Math.max(Dz, Ez)));

      for (double x = minX; x <= maxX; x += 16.0) {
         for (double z = minZ; z <= maxZ; z += 16.0) {
            Location temp = loc1.getWorld().getSpawnLocation();
            temp.setX(x);
            temp.setZ(z);
            Chunk chunk = loc1.getWorld().getBlockAt(temp).getChunk();
            chunkmap.add(chunk);
         }
      }

      return chunkmap;
   }

   public static String getNBTString(BlockState state) {
      NBTTileEntity tent = new NBTTileEntity(state);
      String name = "";

      try {
         name = "id:"
            + state.getType().toString().toUpperCase()
            + ":"
            + state.getData()
            + ",nbt:"
            + tent.asNBTString().toUpperCase().substring(0, Main.JavaPlugin.getConfig().getInt("SubStringNBT") + 1);
      } catch (Exception failure) {
         name = state.getType().toString().toUpperCase() + ":" + state.getData();
      }

      return name;
   }

   public static String getItemNBTString(ItemStack i) {
      if (i == null) {
         return "AIR";
      } else if (i.getType() == Material.AIR) {
         return "AIR";
      } else {
         NBTItem nbti = new NBTItem(i);
         String name = "";

         try {
            name = "id:" + i.getType().toString().toUpperCase() + ":" + i.getDurability() + ",nbt:" + nbti.asNBTString().toUpperCase();
         } catch (Exception failure) {
            name = i.getType().toString().toUpperCase() + ":" + i.getDurability();
         }

         return name;
      }
   }

   public static String getAliasName(String name) {
      String result = null;
      if (Variable.Lang_YML.getStringList("PlaceHolders.OtherWorldAlias") == null) {
         result = name;
      } else {
         java.util.List<String> worldAliases = Variable.Lang_YML.getStringList("PlaceHolders.OtherWorldAlias");
         for (int e = 0; e < worldAliases.size(); e++) {
            String[] temp = (worldAliases.get(e)).split(",");
            if (temp[0].equalsIgnoreCase(name)) {
               result = temp[1];
            }
         }
      }

      return result;
   }

   public static String getNetherSuffix() {
      try {
         String s = Main.JavaPlugin.getConfig().getString("HomeNetherSuffix");
         if (s != null && !s.isEmpty()) {
            return s;
         }
      } catch (Exception failure) {
         com.Util.Diag.warnOnce("nether-suffix", "Could not read HomeNetherSuffix; using the built-in default", failure);
      }

      return "_nether";
   }

   public static String getBaseHomeName(String worldName) {
      String base = worldName.replace(Variable.world_prefix, "");
      String suffix = getNetherSuffix();
      if (base.endsWith(suffix)) {
         base = base.substring(0, base.length() - suffix.length());
      }

      return base;
   }

   public static String getHomeDataName(String worldName) {
      return getBaseHomeName(worldName);
   }

   public static String getHomeOwner(String worldName) {
      String homeName = getBaseHomeName(worldName);
      if (Variable.bungee) {
         return homeName;
      } else {
         File f = new File(Variable.Tempf, homeName + ".yml");
         if (!f.exists()) {
            return homeName;
         } else {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(f);
            String owner = yaml.getString("Owner", "");
            return owner != null && !owner.trim().isEmpty() ? owner.trim() : homeName;
         }
      }
   }

   public static boolean hasHomeData(String homeName) {
      String baseName = getBaseHomeName(homeName);
      if (Variable.bungee) {
         return MySQL.CheckIsAHome(baseName);
      } else {
         File f = new File(Variable.Tempf, baseName + ".yml");
         return f.exists();
      }
   }

   public static boolean isManagedHomeWorld(String worldName) {
      String stripped = worldName.replace(Variable.world_prefix, "");
      String baseName = getBaseHomeName(stripped);
      return !hasHomeData(baseName) ? false : stripped.equalsIgnoreCase(baseName) || stripped.equalsIgnoreCase(baseName + getNetherSuffix());
   }

   public static List<String> getJoinedHomes(String playerName) {
      List<String> result = new ArrayList<>();
      if (playerName != null && !playerName.trim().isEmpty()) {
         if (Variable.bungee) {
            for (String worldName : MySQL.getAllWorlds()) {
               try {
                  for (String op : MySQL.getOP(worldName)) {
                     if (op.equalsIgnoreCase(playerName)) {
                        result.add(worldName);
                        break;
                     }
                  }
               } catch (Exception failure) {
                  com.Util.Diag.warnOnce("joined-homes-scan", "Could not read a home file while collecting joined homes", failure);
               }
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
                  YamlConfiguration yamlConfiguration = YamlConfiguration.loadConfiguration(temp);

                  for (String opx : yamlConfiguration.getStringList("OP")) {
                     if (opx.equalsIgnoreCase(playerName)) {
                        result.add(wantTo);
                        break;
                     }
                  }
               }

               return result;
            }
         }
      } else {
         return result;
      }
   }

   public static int getTotalHomeParticipationCount(String playerName) {
      return HomeAPI.getOwnedHomes(playerName).size() + getJoinedHomes(playerName).size();
   }

   public static int getInviteMaxTotalHomes() {
      return Math.max(1, Main.JavaPlugin.getConfig().getInt("InviteAccess.MaxTotalHomes", 3));
   }

   public static boolean canJoinMoreHomes(String playerName) {
      return getTotalHomeParticipationCount(playerName) < getInviteMaxTotalHomes();
   }

   public static void applyHomeWorldRules(World world, Home home) {
      if (world != null && home != null) {
         if (Main.JavaPlugin.getConfig().getBoolean("HomeTerrain.Enabled", false)) {
            HomeCreationCoordinator.applyWorldPolicy(
               world,
               HomeTerrainPolicy.sizeForLevel(home.getLevel(), Main.JavaPlugin.getConfig().getIntegerList("HomeUpgrade.LevelSizes")),
               Main.JavaPlugin.getConfig().getBoolean("HomeTerrain.PermanentDay", true),
               Main.JavaPlugin.getConfig().getLong("HomeTerrain.DayTime", 6000L)
            );
         }
         Platform.setGameRule(world, "doFireTick", home.getRuleNoFireSpread() ? "false" : "true");
         if (!Main.JavaPlugin.getConfig().getBoolean("HomeTerrain.Enabled", false)) {
            Platform.setGameRule(world, "doMobSpawning", home.isNaturalMobSpawningEnabled() ? "true" : "false");
         }
         Platform.setGameRule(world, "mobGriefing", home.getRuleMobGriefingEnabled() ? "true" : "false");
         Difficulty difficulty = home.getRuleDifficulty();
         if (difficulty != null) {
            world.setDifficulty(difficulty);
         }
      }
   }

   public static boolean CheckIsHome(String name) {
      String stripped = name.replace(Variable.world_prefix, "");
      String netherSuffix = getNetherSuffix();
      if (stripped.endsWith(netherSuffix)) {
         return false;
      } else {
         return !stripped.endsWith("_end") && !stripped.endsWith("_the_end") ? Variable.list_home.contains(getBaseHomeName(name)) : false;
      }
   }

   public static void deleteFile(File file) {
      if (file.exists()) {
         try {
            if (file.isDirectory()) {
               File[] files = file.listFiles();
               if (files.length > 0) {
                  for (File aFile : files) {
                     deleteFile(aFile);
                  }
               }
            }

            file.delete();
         } catch (Exception failure) {
            Diag.warnOnce("util-delete-file", "Deleting a file or directory failed in Util.deleteFile", failure);
         }
      }
   }

   public static void copyDir(String oldDir, String newDir) {
      File srcDir = new File(oldDir);
      if (srcDir.exists() && srcDir.isDirectory()) {
         File destDir = new File(newDir);
         if (!destDir.exists() && destDir.mkdirs()) {
            File[] files = srcDir.listFiles();
            File[] arrayOfFile = files;
            int i = files.length;
            int b = 0;

            while (b < i) {
               File f = arrayOfFile[b];
               if (!f.getName().equalsIgnoreCase("uid.dat") && !f.getName().equalsIgnoreCase("session.lock")) {
                  if (f.isFile()) {
                     copyFile(f, new File(newDir, f.getName()));
                  } else if (f.isDirectory()) {
                     copyDir(oldDir + File.separator + f.getName(), newDir + File.separator + f.getName());
                  }

                  b++;
               } else {
                  b++;
               }
            }
         }
      }
   }

   public static void copyFile(File oldDir, File newDir) {
      BufferedInputStream bufferedInputStream = null;
      BufferedOutputStream bufferedOutputStream = null;
      byte[] b = new byte[1024];

      try {
         bufferedInputStream = new BufferedInputStream(new FileInputStream(oldDir));
         bufferedOutputStream = new BufferedOutputStream(new FileOutputStream(newDir));

         int len;
         while ((len = bufferedInputStream.read(b)) > -1) {
            bufferedOutputStream.write(b, 0, len);
         }

         bufferedOutputStream.flush();
      } catch (IOException ioFailure) {
         com.Util.Diag.warn("File copy failed; the destination may be truncated", ioFailure);
      } finally {
         if (bufferedInputStream != null) {
            try {
               bufferedInputStream.close();
            } catch (IOException closeFailure) {
               Diag.warnOnce("util-copyFile-close", "Closing resources failed in Util.copyFile", closeFailure);
            }
         }

         if (bufferedOutputStream != null) {
            try {
               bufferedOutputStream.close();
            } catch (IOException closeFailure) {
               Diag.warnOnce("util-copyFile-close-2", "Closing resources failed in Util.copyFile", closeFailure);
            }
         }
      }
   }

   /**
    * Highest {@code VIPAdd} radius bonus granted by any currently online owner or manager
    * of {@code home}.
    *
    * <p>This same block was copy-pasted into seven places (Util, WBControl, ScheduledTasks,
    * BlockPlaceListener, PlayerMoveListener, PlayerTeleportListener, HomeWorldManager),
    * each with an unguarded {@code Integer.valueOf(ss[1])} that throws on a malformed
    * {@code VIPAdd} entry and an unguarded {@code ss[1]} that throws on a missing comma.
    * New call sites must use this method.</p>
    */
   public static int getPermissionBorderBonus(Home home) {
      if (home == null || Main.JavaPlugin == null) {
         return 0;
      }

      List<String> candidates = new ArrayList<>();
      candidates.add(home.getName());
      List<String> ops = home.getOPs();
      if (ops != null) {
         for (String op : ops) {
            if (op != null && Bukkit.getPlayer(op) != null) {
               candidates.add(op);
            }
         }
      }

      int bonus = 0;
      for (String entry : Main.JavaPlugin.getConfig().getStringList("VIPAdd")) {
         if (entry == null) {
            continue;
         }
         String[] parts = entry.split(",");
         if (parts.length < 2) {
            Main.JavaPlugin.getLogger().warning("Ignoring malformed VIPAdd entry '" + entry + "' (expected 'permission,radius').");
            continue;
         }
         int add;
         try {
            add = Integer.parseInt(parts[1].trim());
         } catch (NumberFormatException invalid) {
            Main.JavaPlugin.getLogger().warning("Ignoring VIPAdd entry '" + entry + "': '" + parts[1] + "' is not a number.");
            continue;
         }
         if (add <= bonus) {
            continue;
         }
         for (String name : candidates) {
            Player online = Bukkit.getPlayer(name);
            if (online != null && online.hasPermission(parts[0].trim())) {
               bonus = add;
               break;
            }
         }
      }
      return bonus;
   }

   /**
    * Creates a bounded, least-recently-used map for caches keyed by home or player name.
    *
    * <p>The VIP border ratchet used to be held in plain {@code HashMap}s with no eviction, one
    * per call-site class, so they retained an entry for every home ever visited for the lifetime
    * of the server. Bounding the single shared map stops that growth without changing how any
    * call site reads or writes it.</p>
    */
   public static <K, V> java.util.Map<K, V> boundedCache(final int maxEntries) {
      return java.util.Collections.synchronizedMap(new java.util.LinkedHashMap<K, V>(16, 0.75F, true) {
         @Override
         protected boolean removeEldestEntry(java.util.Map.Entry<K, V> eldest) {
            return size() > Math.max(16, maxEntries);
         }
      });
   }

   public static Boolean CheckOwnerAndManagerAndOP(Player p, String name) {
      boolean return_boolean = false;
      String suffix = getNetherSuffix();
      if (name.endsWith(suffix)) {
         name = name.substring(0, name.length() - suffix.length());
      }

      if (p.getName().equalsIgnoreCase(getHomeOwner(name))) {
         return_boolean = true;
      }

      if (p.isOp()) {
         return_boolean = true;
      }

      if (Variable.bungee) {
         List<String> ops = MySQL.getOP(name);

         for (int e = 0; e < ops.size(); e++) {
            if (ops.get(e).equalsIgnoreCase(p.getName())) {
               return_boolean = true;
               break;
            }
         }
      } else {
         File f = new File(Variable.Tempf, name + ".yml");
         if (f.exists()) {
            YamlConfiguration yml = YamlConfiguration.loadConfiguration(f);
            List<String> OP = yml.getStringList("OP");
            Boolean CheckOP = false;
            if (OP == null) {
               OP = new ArrayList<>();
            }

            for (int i = 0; i < OP.size(); i++) {
               if (OP.get(i).equalsIgnoreCase(p.getName())) {
                  CheckOP = true;
               }
            }

            if (CheckOP) {
               return_boolean = true;
            }
         } else {
            return_boolean = false;
         }
      }

      return return_boolean;
   }

   public static Boolean Check(Player p, String name) {
      name = name.replace(Variable.world_prefix, "");
      boolean result = false;
      File f = new File(Variable.Tempf, name + ".yml");
      if (CheckOwnerAndManagerAndOP(p, name)) {
         return true;
      } else {
         if (Variable.bungee) {
            List<String> ops = MySQL.getMembers(name);

            for (int e = 0; e < ops.size(); e++) {
               if (ops.get(e).equalsIgnoreCase(p.getName()) || ops.get(e).equals("*")) {
                  result = true;
                  break;
               }
            }
         } else if (f.exists()) {
            YamlConfiguration yamlConfiguration = YamlConfiguration.loadConfiguration(f);
            List<String> Members = yamlConfiguration.getStringList("Members");
            Boolean CheckMembers = false;
            if (Members == null) {
               Members = new ArrayList<>();
            }

            for (int i = 0; i < Members.size(); i++) {
               if (Members.get(i).equalsIgnoreCase(p.getName()) || Members.get(i).equals("*")) {
                  result = true;
                  break;
               }
            }
         } else {
            result = false;
         }

         return result;
      }
   }

   public static boolean isHomeOwnerOnline(String name) {
      String owner = getHomeOwner(name);
      if (owner != null && !owner.trim().isEmpty()) {
         Player player = Bukkit.getPlayerExact(owner);
         return player != null && player.isOnline();
      } else {
         return false;
      }
   }

   public static boolean CanEnterHome(Player p, String name) {
      name = getBaseHomeName(name.replace(Variable.world_prefix, ""));
      if (CheckOwnerAndManagerAndOP(p, name)) {
         return true;
      } else if (Check(p, name)) {
         return isHomeOwnerOnline(name);
      } else {
         Home home = HomeAPI.getHome(name);
         return home != null && home.isAllowStranger();
      }
   }

   public static Boolean CheckBlack(Player p, String name) {
      boolean check = false;
      if (Variable.bungee) {
         List<String> ops = MySQL.getDenys(name);

         for (int e = 0; e < ops.size(); e++) {
            if (ops.get(e).equalsIgnoreCase(p.getName())) {
               check = true;
               break;
            }
         }
      } else {
         File f = new File(Variable.Tempf, name + ".yml");
         if (f.exists()) {
            YamlConfiguration yamlConfiguration = YamlConfiguration.loadConfiguration(f);
            List<String> Members = yamlConfiguration.getStringList("Denys");
            Boolean CheckMembers = false;
            if (Members == null) {
               Members = new ArrayList<>();
            }

            for (int i = 0; i < Members.size(); i++) {
               if (Members.get(i).equalsIgnoreCase(p.getName())) {
                  check = true;
                  break;
               }
            }
         } else {
            check = false;
         }
      }

      return check;
   }

   public static Boolean CheckIllegalName(Player p) {
      String name = p.getName();
      if (!name.contains("|")
         && !name.contains("&")
         && !name.contains("!")
         && !name.contains("@")
         && !name.contains("^")
         && !name.contains("*")
         && !name.toUpperCase().contains("DIM")) {
         for (String temp : Main.JavaPlugin.getConfig().getStringList("IlleagalName")) {
            if (p.getName().equalsIgnoreCase(temp)) {
               return true;
            }
         }

         return false;
      } else {
         return true;
      }
   }

   public static void refreshBorder(final World world) {
      (new BukkitRunnable() {
         public void run() {
            if (Util.CheckIsHome(world.getName().replace(Variable.world_prefix, ""))) {
               if (Main.JavaPlugin.getConfig().getBoolean("HDSwitch") && Variable.Hologram_switch) {
                  int level = 1;
                  if (Variable.bungee) {
                     level = Integer.valueOf(MySQL.getLevel(world.getName().replace(Variable.world_prefix, "")));
                  } else {
                     File f = new File(Variable.Tempf, world.getName().replace(Variable.world_prefix, "") + ".yml");
                     YamlConfiguration yamlConfiguration = YamlConfiguration.loadConfiguration(f);
                     level = yamlConfiguration.getInt("Level");
                  }

                  Home h = HomeAPI.getHome(world.getName());
                  int vip_add = 0;
                  ArrayList<String> players = new ArrayList<>();
                  players.add(h.getName());

                  for (String op : h.getOPs()) {
                     if (Bukkit.getPlayer(op) != null) {
                        players.add(op);
                     }
                  }

                  for (String s : Main.JavaPlugin.getConfig().getStringList("VIPAdd")) {
                     String[] ss = s.split(",");

                     for (String p_name : players) {
                        Player p = Bukkit.getPlayer(p_name);
                        if (p != null && p.hasPermission(ss[0])) {
                           int add = Integer.valueOf(ss[1]);
                           if (add > vip_add) {
                              vip_add = add;
                           }
                        }
                     }
                  }

                  int addExtra = VipBorderRatchet.highWaterMark(Util.border_redis, h.getName(), vip_add);

                  double halfSize = HomeTerrainPolicy.configuredBorderSize(
                     level,
                     Main.JavaPlugin.getConfig().getBoolean("HomeTerrain.Enabled", false),
                     Main.JavaPlugin.getConfig().getIntegerList("HomeUpgrade.LevelSizes"),
                     Main.JavaPlugin.getConfig().getInt("WorldBoard"),
                     Main.JavaPlugin.getConfig().getInt("UpdateRadius"),
                     addExtra
                  ) / 2.0;
                  Location loc = world.getSpawnLocation();
                  Location loc1 = loc.clone();
                  loc1.setX(loc1.getX() + halfSize + 5.0);
                  loc1.setY(loc1.getY() + 5.0);
                  Location loc5 = loc1.clone();
                  loc5.setZ(loc5.getZ() + halfSize + 5.0);
                  Location loc6 = loc1.clone();
                  loc6.setZ(loc6.getZ() - halfSize - 5.0);
                  Location loc2 = loc.clone();
                  loc2.setX(loc2.getX() - halfSize - 5.0);
                  loc2.setY(loc2.getY() + 5.0);
                  Location loc7 = loc2.clone();
                  loc7.setZ(loc7.getZ() + halfSize + 5.0);
                  Location loc8 = loc2.clone();
                  loc8.setZ(loc8.getZ() - halfSize - 5.0);
                  Location loc3 = loc.clone();
                  loc3.setZ(loc3.getZ() + halfSize + 5.0);
                  loc3.setY(loc3.getY() + 5.0);
                  Location loc4 = loc.clone();
                  loc4.setZ(loc4.getZ() - halfSize - 5.0);
                  loc4.setY(loc4.getY() + 5.0);
                  List<HologramCompat.Handle> hololist = new ArrayList<>();
                  loc1 = Util.getAir(loc1);
                  loc2 = Util.getAir(loc2);
                  loc3 = Util.getAir(loc3);
                  loc4 = Util.getAir(loc4);
                  loc5 = Util.getAir(loc5);
                  loc6 = Util.getAir(loc6);
                  loc7 = Util.getAir(loc7);
                  loc8 = Util.getAir(loc8);
                  HologramCompat.Handle holo1 = HologramCompat.create(Main.JavaPlugin, loc1);

                  java.util.List<String> tagsNorth = Variable.Lang_YML.getStringList("HDTagsNorth");
                  for (int line = 0; line < tagsNorth.size(); line++) {
                     String TempLine = tagsNorth.get(line);
                     TempLine = PlaceholderAPI.setPlaceholders(null, TempLine);
                     holo1.insertTextLine(line, TempLine);
                  }

                  holo1.teleport(loc1);
                  HologramCompat.Handle holo2 = HologramCompat.create(Main.JavaPlugin, loc2);

                  java.util.List<String> tagsSouth = Variable.Lang_YML.getStringList("HDTagsSouth");
                  for (int line = 0; line < tagsSouth.size(); line++) {
                     String TempLine = tagsSouth.get(line);
                     TempLine = PlaceholderAPI.setPlaceholders(null, TempLine);
                     holo2.insertTextLine(line, TempLine);
                  }

                  holo2.teleport(loc2);
                  HologramCompat.Handle holo3 = HologramCompat.create(Main.JavaPlugin, loc4);

                  java.util.List<String> tagsEast = Variable.Lang_YML.getStringList("HDTagsEast");
                  for (int line = 0; line < tagsEast.size(); line++) {
                     String TempLine = tagsEast.get(line);
                     TempLine = PlaceholderAPI.setPlaceholders(null, TempLine);
                     holo3.insertTextLine(line, TempLine);
                  }

                  holo3.teleport(loc4);
                  HologramCompat.Handle holo4 = HologramCompat.create(Main.JavaPlugin, loc3);

                  java.util.List<String> tagsWest = Variable.Lang_YML.getStringList("HDTagsWest");
                  for (int line = 0; line < tagsWest.size(); line++) {
                     String TempLine = tagsWest.get(line);
                     TempLine = PlaceholderAPI.setPlaceholders(null, TempLine);
                     holo4.insertTextLine(line, TempLine);
                  }

                  holo4.teleport(loc3);
                  HologramCompat.Handle holo5 = HologramCompat.create(Main.JavaPlugin, loc5);

                  java.util.List<String> tagsNorthWest = Variable.Lang_YML.getStringList("HDTagsNorthWest");
                  for (int line = 0; line < tagsNorthWest.size(); line++) {
                     String TempLine = tagsNorthWest.get(line);
                     TempLine = PlaceholderAPI.setPlaceholders(null, TempLine);
                     holo5.insertTextLine(line, TempLine);
                  }

                  holo5.teleport(loc5);
                  HologramCompat.Handle holo6 = HologramCompat.create(Main.JavaPlugin, loc6);

                  java.util.List<String> tagsNorthEast = Variable.Lang_YML.getStringList("HDTagsNorthEast");
                  for (int line = 0; line < tagsNorthEast.size(); line++) {
                     String TempLine = tagsNorthEast.get(line);
                     TempLine = PlaceholderAPI.setPlaceholders(null, TempLine);
                     holo6.insertTextLine(line, TempLine);
                  }

                  holo6.teleport(loc6);
                  HologramCompat.Handle holo7 = HologramCompat.create(Main.JavaPlugin, loc7);

                  java.util.List<String> tagsWestSouth = Variable.Lang_YML.getStringList("HDTagsWestSouth");
                  for (int line = 0; line < tagsWestSouth.size(); line++) {
                     String TempLine = tagsWestSouth.get(line);
                     TempLine = PlaceholderAPI.setPlaceholders(null, TempLine);
                     holo7.insertTextLine(line, TempLine);
                  }

                  holo7.teleport(loc7);
                  HologramCompat.Handle holo8 = HologramCompat.create(Main.JavaPlugin, loc8);

                  java.util.List<String> tagsEastSouth = Variable.Lang_YML.getStringList("HDTagsEastSouth");
                  for (int line = 0; line < tagsEastSouth.size(); line++) {
                     String TempLine = tagsEastSouth.get(line);
                     TempLine = PlaceholderAPI.setPlaceholders(null, TempLine);
                     holo8.insertTextLine(line, TempLine);
                  }

                  holo8.teleport(loc8);
                  hololist.add(holo1);
                  hololist.add(holo2);
                  hololist.add(holo3);
                  hololist.add(holo4);
                  hololist.add(holo5);
                  hololist.add(holo6);
                  hololist.add(holo7);
                  hololist.add(holo8);
                  if (Variable.hololist.containsKey(world.getName())) {
                     for (HologramCompat.Handle temp : Variable.hololist.get(world.getName())) {
                        temp.delete();
                     }
                  }

                  Variable.hololist.put(world.getName(), hololist);
               }
            }
         }
      }).runTask(Main.JavaPlugin);
   }
}
