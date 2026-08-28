package com.ErrorTown;

import WorldBorder.WBControl;
import com.GUI.BiomeGui;
import com.GUI.CheckGui;
import com.GUI.CreateCostGui;
import com.GUI.CreateGui;
import com.GUI.DenyGui;
import com.GUI.GiftGui;
import com.GUI.InviteGui;
import com.GUI.MainGui;
import com.GUI.ManageGui;
import com.GUI.ManageGui2;
import com.GUI.ManageGui3;
import com.GUI.OwnedHomesGui;
import com.GUI.RulesGui;
import com.GUI.ServiceCostGui;
import com.GUI.SetSpawnGui;
import com.GUI.TrustGui;
import com.GUI.UpgradeGui;
import com.GUI.VisitGui;
import com.Util.Channel;
import com.Util.CustomChunkGenerator;
import com.Util.FirstBorderShaped;
import com.Util.Home;
import com.Util.HomeAPI;
import com.Util.BukkitCompat;
import com.Util.CsvUtil;
import com.Util.CreateCostLedger;
import com.Util.HomeAudit;
import com.Util.HomeCreationCoordinator;
import com.Util.HomeCreationQueue;
import com.Util.HomeTerrainPolicy;
import com.Util.NaturalHomeWorldFactory;
import com.Util.MySQL;
import com.Util.Platform;
import com.Util.R1_12_2;
import com.Util.R1_7_10;
import com.Util.StaticsTick;
import com.Util.Util;
import com.comphenix.protocol.utility.StreamSerializer;
import com.Util.HologramCompat;
import com.Util.MultiverseCompat;
import com.Util.MultiverseCompat.MVWorldManager;
import com.Util.MultiverseCompat.MultiverseCore;
import com.Util.MultiverseCompat.MultiverseWorld;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.TreeMap;
import me.clip.placeholderapi.PlaceholderAPI;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Difficulty;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.World.Environment;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

public class CommandListener implements CommandExecutor, TabExecutor {
   private static final SimpleDateFormat ADMIN_TS = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

   private String formatLastActive(long ts) {
      return ts <= 0L ? "从未记录" : ADMIN_TS.format(new Date(ts));
   }

   private List<String> getHomesForPlayer(String playerName) {
      List<String> result = new ArrayList<>();

      for (Home home : HomeAPI.getHomes()) {
         try {
            if (home.getOwner().equalsIgnoreCase(playerName)) {
               result.add(home.getName() + " (owner)");
            } else {
               for (String op : home.getOPs()) {
                  if (op.equalsIgnoreCase(playerName)) {
                     result.add(home.getName() + " (member)");
                     break;
                  }
               }
            }
         } catch (Exception var7) {
            com.Util.Diag.warnOnce("list-joined-homes", "Could not read a home while listing memberships", var7);
         }
      }

      return result;
   }

   private boolean canReceiveHomeInvite(String playerName) {
      return Util.canJoinMoreHomes(playerName);
   }

   private int getPermissionBasedLimit(Player player, String permissionPrefix, int fallback) {
      int result = fallback;
      int start = Math.max(1, fallback) * 1000;

      for (int i = start; i > Math.max(1, fallback); i--) {
         // Perm.has, not hasPermission: the node is built from a prefix at runtime, so the
         // pre-rename ErrorTown.* fallback has to be applied here too.
         if (com.Util.Perm.has(player, permissionPrefix + i)) {
            result = i;
            break;
         }
      }

      return result;
   }

   private int getExpandedHomeJoinLimit(String homeName) {
      int base = Main.JavaPlugin.getConfig().getInt("MaxJoin");
      Home home = HomeAPI.getHome(Util.getBaseHomeName(homeName));
      if (home != null) {
         base += Math.max(0, home.getExtraMemberSlots());
      }

      return base;
   }

   private int getExpandedHomeOpLimit(String homeName) {
      int base = Main.JavaPlugin.getConfig().getInt("MaxOP");
      Home home = HomeAPI.getHome(Util.getBaseHomeName(homeName));
      if (home != null) {
         base += Math.max(0, home.getExtraOpSlots());
      }

      return base;
   }

   private int getEffectiveHomeJoinLimit(Player player, String homeName) {
      return Math.max(
         this.getExpandedHomeJoinLimit(homeName), this.getPermissionBasedLimit(player, "ErrorTown.MaxJoin.", Main.JavaPlugin.getConfig().getInt("MaxJoin"))
      );
   }

   private int getEffectiveHomeOpLimit(Player player, String homeName) {
      return Math.max(
         this.getExpandedHomeOpLimit(homeName), this.getPermissionBasedLimit(player, "ErrorTown.MaxOP.", Main.JavaPlugin.getConfig().getInt("MaxOP"))
      );
   }

   private void sendInviteLimitReached(CommandSender sender, String playerName) {
      int maxTotal = Util.getInviteMaxTotalHomes();
      int currentTotal = Util.getTotalHomeParticipationCount(playerName);
      sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
      sender.sendMessage("§8[§6错误庄园§8] §c该玩家当前已拥有/加入 §e" + currentTotal + " §c个庄园，达到上限 §e" + maxTotal + "§c。");
      sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
   }

   private void sendAdminHomeInfo(CommandSender sender, String homeName) {
      Home home = HomeAPI.getHome(homeName);
      if (home == null) {
         sender.sendMessage("§c未找到家园: " + homeName);
      } else {
         sender.sendMessage("§8[§6错误庄园§8] §f家园: §e" + home.getName());
         sender.sendMessage("§7等级: §e" + home.getLevel() + " §7| 所在服: §b" + home.getServer());
         sender.sendMessage("§7成员: §a" + home.getMembers().size() + " §7| 管理: §a" + home.getOPs().size() + " §7| 黑名单: §c" + home.getDenys().size());
         sender.sendMessage(
            "§7PVP: "
               + (home.isPvp() ? "§c开" : "§a关")
               + " §7| 火焰蔓延: "
               + (home.getRuleNoFireSpread() ? "§c关" : "§a开")
               + " §7| 爆炸: "
               + (home.getRuleExplosionProtect() ? "§c关" : "§a开")
         );
         sender.sendMessage(
            "§7敌对生物: "
               + (home.getRuleAllowHostileMobs() ? "§a开" : "§c关")
               + " §7| 被动生物: "
               + (home.getRuleAllowPassiveMobs() ? "§a开" : "§c关")
               + " §7| 刷怪上限: §e"
               + home.getRuleMaxMobCount()
         );
         sender.sendMessage("§7最近活跃: §f" + this.formatLastActive(home.getLastActive()));
      }
   }

   private List<Home> getStaleHomes(long staleMillis) {
      List<Home> result = new ArrayList<>();
      long now = System.currentTimeMillis();

      for (Home home : HomeAPI.getHomes()) {
         try {
            long lastActive = home.getLastActive();
            if (lastActive <= 0L || now - lastActive >= staleMillis) {
               result.add(home);
            }
         } catch (Exception var10) {
            com.Util.Diag.warnOnce("stale-home-scan", "Could not read lastActive while scanning for stale homes", var10);
         }
      }

      return result;
   }

   private void sendAuditSummary(CommandSender sender, String homeName, int limit) {
      if (Variable.f_log != null && Variable.f_log.exists()) {
         YamlConfiguration yml = YamlConfiguration.loadConfiguration(Variable.f_log);
         if (!yml.contains("Audit")) {
            sender.sendMessage("§7暂无审计记录");
         } else {
            List<String> matched = new ArrayList<>();

            for (String key : yml.getConfigurationSection("Audit").getKeys(false)) {
               String auditHome = yml.getString("Audit." + key + ".home", "");
               if (auditHome.equalsIgnoreCase(homeName)) {
                  matched.add(key);
               }
            }

            Collections.sort(matched, Collections.reverseOrder());
            sender.sendMessage("§8[§6错误庄园§8] §f家园审计: §e" + homeName);
            if (matched.isEmpty()) {
               sender.sendMessage("§7暂无相关记录");
            } else {
               int shown = 0;

               for (String keyx : matched) {
                  String path = "Audit." + keyx;
                  sender.sendMessage(
                     "§7- §f"
                        + yml.getString(path + ".time", "?")
                        + " §8| §e"
                        + yml.getString(path + ".type", "?")
                        + " §8| §b"
                        + yml.getString(path + ".player", "?")
                        + " §8| §7"
                        + yml.getString(path + ".detail", "")
                  );
                  if (++shown >= limit) {
                     break;
                  }
               }
            }
         }
      } else {
         sender.sendMessage("§c未找到审计日志文件");
      }
   }

   private boolean hasRequiredHomeLevel(Player p, int needLevel, String featureName) {
      if (!p.isOp() && !com.Util.Perm.has(p, "ErrorTown.Admin")) {
         Home home = HomeAPI.getHome(Util.getBaseHomeName(p.getWorld().getName()));
         if (home == null) {
            p.sendMessage("§c未找到当前家园数据");
            return false;
         } else if (home.getLevel() < needLevel) {
            p.sendMessage("§8[§6错误庄园§8] §c" + featureName + " 需要家园等级达到 §e" + needLevel + " §c级");
            return false;
         } else {
            return true;
         }
      } else {
         return true;
      }
   }

   private File getHomeWorldRootDir() {
      if (Variable.world_prefix.equalsIgnoreCase("")) {
         return !Bukkit.getVersion().toString().toUpperCase().contains("ARCLIGHT") && !Bukkit.getVersion().toString().contains("1.20.1")
            ? new File(Variable.single_server_gen + "world" + Variable.file_loc_prefix)
            : new File(Variable.single_server_gen + Variable.world_prefix);
      } else {
         return new File(Variable.single_server_gen + Variable.world_prefix);
      }
   }

   private int getMaxOwnedHomes() {
      return Math.max(1, Main.JavaPlugin.getConfig().getInt("MaxOwnedHomes", 3));
   }

   private String getNextOwnedHomeName(Player p) {
      List<String> homes = HomeAPI.getOwnedHomes(p.getName());
      String base = p.getName();
      if (!homes.contains(base)) {
         return base;
      } else {
         for (int i = 2; i <= this.getMaxOwnedHomes(); i++) {
            String candidate = base + "_" + i;
            if (!homes.contains(candidate)) {
               return candidate;
            }
         }

         return null;
      }
   }

   private String getCreateHomeName(Player p) {
      String homeName = Variable.pendingCreateHomeName.remove(p.getName());
      return homeName != null && !homeName.trim().isEmpty() ? homeName : p.getName();
   }

   private String resolveOwnedHomeSelection(Player p, String input) {
      List<String> ownedHomes = HomeAPI.getOwnedHomes(p.getName());
      if (ownedHomes.isEmpty()) {
         return null;
      } else {
         try {
            int index = Integer.parseInt(input);
            if (index >= 1 && index <= ownedHomes.size()) {
               return ownedHomes.get(index - 1);
            }
         } catch (Exception var6) {
            // Not a number: the argument is treated as a home name instead of an index.
         }

         for (String homeName : ownedHomes) {
            if (homeName.equalsIgnoreCase(input)) {
               return homeName;
            }
         }

         return null;
      }
   }

   private void sendOwnedHomesList(Player p, CommandSender sender) {
      List<String> ownedHomes = HomeAPI.getOwnedHomes(p.getName());
      sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
      if (ownedHomes.isEmpty()) {
         sender.sendMessage(Variable.Lang_YML.getString("NoCreateOrJoin"));
         sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
      } else {
         sender.sendMessage("§8[§6错误庄园§8] §f您当前拥有 §e" + ownedHomes.size() + "§f/§e" + this.getMaxOwnedHomes() + " §f个庄园:");

         for (int i = 0; i < ownedHomes.size(); i++) {
            sender.sendMessage("§7" + (i + 1) + ". §e" + ownedHomes.get(i));
         }

         sender.sendMessage("§7使用 §f/sh home <序号> §7或 §f/sh home <庄园名> §7进入指定庄园");
         sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
      }
   }

   private String getCurrentOrPrimaryOwnedHome(Player p) {
      String currentHome = Util.getBaseHomeName(p.getWorld().getName());
      return Util.CheckIsHome(currentHome) && Util.CheckOwnerAndManagerAndOP(p, currentHome) ? currentHome : HomeAPI.getPrimaryOwnedHome(p.getName());
   }

   private boolean teleportToOwnedHomeLocal(Player p, CommandSender sender, String targetHome) {
      File f2 = new File(Variable.Tempf, targetHome + ".yml");
      if (!f2.exists()) {
         sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
         sender.sendMessage(Variable.Lang_YML.getString("TpNotExist"));
         sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
         return false;
      } else {
         World world = Bukkit.getWorld(Variable.world_prefix + targetHome);
         if (world == null) {
            WorldCreator creator = new WorldCreator(Variable.world_prefix + targetHome);
            Variable.create_list_home.add(Variable.world_prefix + targetHome);
            Bukkit.createWorld(creator);
         }

         world = Bukkit.getWorld(Variable.world_prefix + targetHome);
         Location loc = world.getSpawnLocation();
         loc = Util.getNotAir(loc);
         YamlConfiguration yamlConfiguration = YamlConfiguration.loadConfiguration(f2);
         loc.setX(yamlConfiguration.getDouble("X"));
         loc.setY(yamlConfiguration.getDouble("Y"));
         loc.setZ(yamlConfiguration.getDouble("Z"));
         loc = Util.getNotAir(loc);
         p.teleport(loc);
         return true;
      }
   }

   private boolean applyHomeCenter(String homeName, Location centerLocation) {
      if (homeName != null && centerLocation != null && centerLocation.getWorld() != null) {
         World world = Bukkit.getWorld(Variable.world_prefix + homeName);
         if (world == null) {
            return false;
         } else {
            Location center = centerLocation.clone();
            center.setWorld(world);
            if (!Bukkit.getVersion().contains("1.7.10") && !Bukkit.getVersion().contains("1.7.2")) {
               world.setSpawnLocation(center);
            } else {
               world.setSpawnLocation(center.getBlockX(), center.getBlockY(), center.getBlockZ());
            }

            if (Main.JavaPlugin.getConfig().getBoolean("SetSpawn.SyncTeleportAndRespawn", true)) {
               try {
                  Home home = HomeAPI.getHome(homeName);
                  if (home != null) {
                     home.setX(center.getX());
                     home.setY(center.getY());
                     home.setZ(center.getZ());
                  }
               } catch (IOException var10) {
                  var10.printStackTrace();
                  return false;
               }
            }

            if (Main.JavaPlugin.getConfig().getBoolean("BorderSwitch")) {
               try {
                  world.getWorldBorder().setCenter(center.getX(), center.getZ());
                  world.getWorldBorder().setSize(world.getWorldBorder().getSize());
               } catch (NoSuchMethodError var9) {
                  Bukkit.getConsoleSender().sendMessage(Variable.Lang_YML.getString("BorderException"));
               }
            }

            if (Variable.hook_multiverseCore) {
               try {
                  MultiverseCore mvcore = MultiverseCompat.plugin();
                  if (mvcore != null) {
                     MVWorldManager mvManager = mvcore.getMVWorldManager();
                     MultiverseWorld mvWorld = mvManager.getMVWorld(world.getName());
                     if (mvWorld != null) {
                        mvWorld.setSpawnLocation(center);
                     }
                  }
               } catch (Exception var8) {
                  com.Util.Diag.warnOnce("mv-set-spawn", "Multiverse rejected the new spawn location", var8);
               }
            }

            Util.refreshBorder(world);
            return true;
         }
      } else {
         return false;
      }
   }

   private boolean canUseSetSpawnFeature(Player p, CommandSender sender) {
      boolean adminBypass = p.isOp() || com.Util.Perm.has(p, "ErrorTown.Admin");
      if (!Util.CheckOwnerAndManagerAndOP(p, Util.getBaseHomeName(p.getWorld().getName())) && !adminBypass) {
         String temp = Variable.Lang_YML.getString("NoOwnerAndManagerPermission");
         sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
         sender.sendMessage(temp);
         sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
         return false;
      } else {
         String worldBase = p.getWorld().getName().replace(Variable.world_prefix, "");
         String netherSuffix = Main.JavaPlugin.getConfig().getString("HomeNetherSuffix");
         if (netherSuffix == null || netherSuffix.isEmpty()) {
            netherSuffix = "_nether";
         }

         if (worldBase.endsWith(netherSuffix)) {
            String tip = Variable.Lang_YML.getString("NetherSetSpawnNotSupported");
            p.sendMessage(tip != null ? tip : "§c地狱世界无法设置边界中心。");
            return false;
         } else {
            return true;
         }
      }
   }

   private Location getPendingSetSpawnLocation(Player p) {
      String raw = Variable.pendingSetSpawnTarget.get(p.getName());
      if (raw != null && !raw.trim().isEmpty()) {
         String[] parts = raw.split(",");
         if (parts.length != 3) {
            return null;
         } else {
            try {
               double x = Double.parseDouble(parts[0]);
               double y = Double.parseDouble(parts[1]);
               double z = Double.parseDouble(parts[2]);
               Location target = p.getLocation().clone();
               target.setX(x);
               target.setY(y);
               target.setZ(z);
               return target;
            } catch (Exception var11) {
               return null;
            }
         }
      } else {
         return null;
      }
   }

   private boolean executeSetSpawnChange(Player p, Location targetCenter, String payMode) {
      if (targetCenter == null) {
         p.sendMessage("§c未找到有效的目标坐标。");
         return false;
      } else {
         long cooldownSec = Main.JavaPlugin.getConfig().getLong("SetSpawn.CooldownSeconds", 60L);
         if (cooldownSec > 0L && Variable.setSpawnCooldown.containsKey(p.getName())) {
            long lastTime = Variable.setSpawnCooldown.get(p.getName());
            long now = System.currentTimeMillis();
            long elapsed = (now - lastTime) / 1000L;
            if (elapsed < cooldownSec) {
               long remaining = cooldownSec - elapsed;
               p.sendMessage("§8[§6错误庄园§8] §c设置边界中心冷却中，请等待 §e" + remaining + " §c秒。");
               return false;
            }
         }

         String actionName = "设置边界中心";
         double goldFee = Main.JavaPlugin.getConfig().getDouble("SetSpawn.GoldFee", 0.0);
         int pointsFee = Main.JavaPlugin.getConfig().getInt("SetSpawn.PointFee", 0);
         if ("money".equalsIgnoreCase(payMode)) {
            if (!Util.chargeMoneyExact(p, goldFee, actionName)) {
               return false;
            }
         } else if ("points".equalsIgnoreCase(payMode)) {
            if (!Util.chargePointsExact(p, pointsFee, actionName)) {
               return false;
            }
         } else if (!Util.chargeMoneyOrPoints(p, goldFee, pointsFee, actionName)) {
            return false;
         }

         String baseHomeName = Util.getBaseHomeName(p.getWorld().getName());
         if (!this.applyHomeCenter(baseHomeName, targetCenter)) {
            p.sendMessage("§c移动家园中心失败，请检查家园世界是否已正确加载。");
            return false;
         } else {
            if (cooldownSec > 0L) {
               Variable.setSpawnCooldown.put(p.getName(), System.currentTimeMillis());
            }

            Variable.pendingSetSpawnTarget.remove(p.getName());
            this.teleportPlayerAfterSetSpawn(p, targetCenter);
            String temp = Variable.Lang_YML.getString("SetSpawnSuccess");
            p.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
            p.sendMessage(temp);
            p.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
            return true;
         }
      }
   }

   private void teleportPlayerAfterSetSpawn(Player p, Location targetCenter) {
      if (p != null && targetCenter != null && targetCenter.getWorld() != null) {
         Location safe = this.findSafeSetSpawnLanding(targetCenter);
         if (safe == null) {
            p.sendMessage("§8[§6ErrorTown§8] §c未找到安全落点，中心已移动但没有传送你。");
         } else {
            p.setFallDistance(0.0F);
            p.teleport(safe);
            p.setFallDistance(0.0F);
            BukkitCompat.addResistance(p, 100, 4, false, false, false);
            p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 100, 0, false, false, false));
            p.sendMessage("§8[§6ErrorTown§8] §a已将你传送到新中心附近的安全位置。");
         }
      }
   }

   private Location findSafeSetSpawnLanding(Location center) {
      World world = center.getWorld();
      int baseX = center.getBlockX();
      int baseY = Math.max(world.getMinHeight() + 2, Math.min(center.getBlockY(), world.getMaxHeight() - 3));
      int baseZ = center.getBlockZ();

      for (int radius = 0; radius <= 6; radius++) {
         for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
               if (Math.max(Math.abs(dx), Math.abs(dz)) == radius) {
                  Location found = this.findSafeYAt(world, baseX + dx, baseY, baseZ + dz, center);
                  if (found != null) {
                     return found;
                  }
               }
            }
         }
      }

      int x = baseX;
      int z = baseZ;

      int y;
      try {
         y = world.getHighestBlockAt(x, z).getY() + 1;
      } catch (Throwable var10) {
         y = baseY;
      }

      y = Math.max(world.getMinHeight() + 2, Math.min(y, world.getMaxHeight() - 3));
      if (!this.isSafeStandingSpotForSetSpawn(world, baseX, y, baseZ)) {
         Block below = world.getBlockAt(baseX, y - 1, baseZ);
         if (below.isPassable()) {
            below.setType(Material.GLASS, false);
         }
      }

      if (!world.getBlockAt(baseX, y, baseZ).isPassable()) {
         world.getBlockAt(baseX, y, baseZ).setType(Material.AIR, false);
      }

      if (!world.getBlockAt(baseX, y + 1, baseZ).isPassable()) {
         world.getBlockAt(baseX, y + 1, baseZ).setType(Material.AIR, false);
      }

      return new Location(world, baseX + 0.5, y, baseZ + 0.5, center.getYaw(), center.getPitch());
   }

   private Location findSafeYAt(World world, int x, int baseY, int z, Location facing) {
      int minY = Math.max(world.getMinHeight() + 1, baseY - 12);
      int maxY = Math.min(world.getMaxHeight() - 2, baseY + 12);

      for (int offset = 0; offset <= 12; offset++) {
         int up = baseY + offset;
         if (up <= maxY && this.isSafeStandingSpotForSetSpawn(world, x, up, z)) {
            return new Location(world, x + 0.5, up, z + 0.5, facing.getYaw(), facing.getPitch());
         }

         if (offset != 0) {
            int down = baseY - offset;
            if (down >= minY && this.isSafeStandingSpotForSetSpawn(world, x, down, z)) {
               return new Location(world, x + 0.5, down, z + 0.5, facing.getYaw(), facing.getPitch());
            }
         }
      }

      return null;
   }

   private boolean isSafeStandingSpotForSetSpawn(World world, int x, int y, int z) {
      if (y > world.getMinHeight() && y + 1 < world.getMaxHeight()) {
         Block below = world.getBlockAt(x, y - 1, z);
         Block feet = world.getBlockAt(x, y, z);
         Block head = world.getBlockAt(x, y + 1, z);
         return !below.isPassable() && feet.isPassable() && head.isPassable();
      } else {
         return false;
      }
   }

   private File resolveCreateTemplateDir(String templateName) {
      if (templateName != null && !templateName.trim().isEmpty()) {
         File direct = new File(templateName);
         if (direct.exists() && direct.isDirectory()) {
            return direct;
         } else {
            File underRoot = new File(this.getHomeWorldRootDir(), templateName);
            if (underRoot.exists() && underRoot.isDirectory()) {
               return underRoot;
            } else {
               File underServer = new File(Variable.single_server_gen, templateName);
               return underServer.exists() && underServer.isDirectory() ? underServer : null;
            }
         }
      } else {
         return null;
      }
   }

   private World createHomeFromTemplate(Player p, String homeName, String templateName, WorldType fallbackType) {
      File templateDir = this.resolveCreateTemplateDir(templateName);
      if (templateDir == null) {
         return null;
      } else {
         File targetDir = new File(this.getHomeWorldRootDir(), homeName);
         if (targetDir.exists()) {
            return null;
         } else {
            Util.copyDir(templateDir.getPath(), targetDir.getPath());
            if (!targetDir.exists()) {
               return null;
            } else {
                WorldCreator creator = new WorldCreator(Variable.world_prefix + homeName);
                if (fallbackType == WorldType.FLAT) {
                   // A copied template still needs valid flat settings for any chunk generated
                   // beyond the template's own region, or vanilla logs "No key layers in MapLike[{}]".
                   com.Util.SuperflatPreset.apply(creator);
                } else {
                   creator.type(fallbackType);
                }
               if (Main.JavaPlugin.getConfig().getBoolean("generateStructures")) {
                  creator.generateStructures(true);
               } else {
                  creator.generateStructures(false);
               }

               Variable.create_list_home.add(Variable.world_prefix + homeName);
               return Bukkit.createWorld(creator);
            }
         }
      }
   }

   private Material findFirstMaterial(String... names) {
      if (names == null) {
         return null;
      } else {
         for (String name : names) {
            if (name != null && !name.trim().isEmpty()) {
               Material material = Material.matchMaterial(name);
               if (material != null) {
                  return material;
               }
            }
         }

         return null;
      }
   }

   private String getSkyIslandCreateKey() {
      String key = Main.JavaPlugin.getConfig().getString("SkyIsland.CreateKey", "airland");
      return key != null && !key.trim().isEmpty() ? key.trim() : "airland";
   }

   private String normalizeCreateMode(String createMode) {
      if (Main.JavaPlugin.getConfig().getBoolean("HomeTerrain.Enabled", false)) {
         return "1";
      }
      return CustomChunkGenerator.isSkyIslandType(createMode) ? this.getSkyIslandCreateKey() : createMode;
   }

   private int getNaturalHomeSize(int level) {
      return HomeTerrainPolicy.sizeForLevel(level, Main.JavaPlugin.getConfig().getIntegerList("HomeUpgrade.LevelSizes"));
   }

   private void configureCreatorSeed(Player p, WorldCreator creator) {
      if (creator != null) {
         if (Variable.pendingCreateSeed.containsKey(p.getName())) {
            String customSeed = Variable.pendingCreateSeed.remove(p.getName());

            try {
               creator.seed(Long.parseLong(customSeed));
            } catch (NumberFormatException var5) {
               creator.seed(customSeed.hashCode());
            }
         } else if (Main.JavaPlugin.getConfig().getLong("Seed") != 0L) {
            creator.seed(Main.JavaPlugin.getConfig().getLong("Seed"));
         }
      }
   }

   private void addChestItem(Chest chest, int slot, int amount, String... materialNames) {
      if (chest != null && slot >= 0) {
         Material material = this.findFirstMaterial(materialNames);
         if (material != null) {
            chest.getBlockInventory().setItem(slot, new ItemStack(material, amount));
         }
      }
   }

   private void setupSimpleSkyIsland(World world) {
      if (world != null) {
         Material air = this.findFirstMaterial("AIR");
         Material grass = this.findFirstMaterial("GRASS_BLOCK", "GRASS");
         Material dirt = this.findFirstMaterial("DIRT");
         Material bedrock = this.findFirstMaterial("BEDROCK");
         Material chestMaterial = this.findFirstMaterial("CHEST");
         if (grass != null && dirt != null && bedrock != null && chestMaterial != null) {
            int centerX = Main.JavaPlugin.getConfig().getInt("SkyIsland.CenterX", 0);
            int centerZ = Main.JavaPlugin.getConfig().getInt("SkyIsland.CenterZ", 0);
            int topY = Main.JavaPlugin.getConfig().getInt("SkyIsland.SpawnY", 65) - 1;
            int radius = Math.max(1, Main.JavaPlugin.getConfig().getInt("SkyIsland.PlatformRadius", 3));
            int clearHeight = Math.max(3, Main.JavaPlugin.getConfig().getInt("SkyIsland.ClearHeight", 6));
            int chestOffsetX = Main.JavaPlugin.getConfig().getInt("SkyIsland.ChestOffsetX", 2);
            int chestOffsetY = Main.JavaPlugin.getConfig().getInt("SkyIsland.ChestOffsetY", 1);
            int chestOffsetZ = Main.JavaPlugin.getConfig().getInt("SkyIsland.ChestOffsetZ", 0);
            boolean placeStarterChest = Main.JavaPlugin.getConfig().getBoolean("SkyIsland.StarterChest.Enable", true);

            for (int x = -radius - 2; x <= radius + 2; x++) {
               for (int z = -radius - 2; z <= radius + 2; z++) {
                  for (int y = topY; y <= topY + clearHeight; y++) {
                     if (air != null) {
                        world.getBlockAt(centerX + x, y, centerZ + z).setType(air, false);
                     }
                  }
               }
            }

            for (int x = -radius; x <= radius; x++) {
               for (int z = -radius; z <= radius; z++) {
                  world.getBlockAt(centerX + x, topY - 2, centerZ + z).setType(dirt, false);
                  world.getBlockAt(centerX + x, topY - 1, centerZ + z).setType(dirt, false);
                  world.getBlockAt(centerX + x, topY, centerZ + z).setType(grass, false);
               }
            }

            world.getBlockAt(centerX, topY - 3, centerZ).setType(bedrock, false);
            if (placeStarterChest) {
               Block chestBlock = world.getBlockAt(centerX + chestOffsetX, topY + chestOffsetY, centerZ + chestOffsetZ);
               chestBlock.setType(chestMaterial, false);
               if (chestBlock.getState() instanceof Chest chest) {
                  chest.getBlockInventory().clear();
                  List<String> starterItems = Main.JavaPlugin.getConfig().getStringList("SkyIsland.StarterChest.Items");

                  for (int i = 0; i < starterItems.size(); i++) {
                     String itemDef = starterItems.get(i);
                     if (itemDef != null && !itemDef.trim().isEmpty()) {
                        String[] parts = itemDef.split(",");
                        if (parts.length >= 2) {
                           Material itemMaterial = this.findFirstMaterial(parts[0].trim());
                           if (itemMaterial != null) {
                              int amount = 1;

                              try {
                                 amount = Math.max(1, Integer.parseInt(parts[1].trim()));
                              } catch (Exception var28) {
                                 com.Util.Diag.warnOnce("skyisland-item-amount", "SkyIsland.StarterChest.Items has a non-numeric amount in \"" + itemDef + "\"; using 1");
                              }

                              int slot = i;
                              if (parts.length >= 3) {
                                 try {
                                    slot = Math.max(0, Integer.parseInt(parts[2].trim()));
                                 } catch (Exception var27) {
                                    com.Util.Diag.warnOnce("skyisland-item-slot", "SkyIsland.StarterChest.Items has a non-numeric slot in \"" + itemDef + "\"; using the sequential slot");
                                 }
                              }

                              chest.getBlockInventory().setItem(slot, new ItemStack(itemMaterial, amount));
                           }
                        }
                     }
                  }

                  chest.update();
               }
            }

            world.setSpawnLocation(centerX, topY + 1, centerZ);
         }
      }
   }

   private boolean canStartCreate(Player p, CommandSender sender) {
      if (CreateCostLedger.hasCharge(p.getName())) {
         sender.sendMessage("§8[§6错误庄园§8] §c您已有一个尚未完成的家园创建请求，请等待其完成或退款后再试。");
         return false;
      }
      if (Main.JavaPlugin.getConfig().getBoolean("BungeeCord")) {
         if (MySQL.alreadyhastheplayerjoin(p.getName())) {
            String temp_BungeeCord = Variable.Lang_YML.getString("HasBeenJoin");
            if (temp_BungeeCord.contains("<ServerName>")) {
               temp_BungeeCord = temp_BungeeCord.replace("<ServerName>", MySQL.getJoinServer(p.getName()));
            }

            sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
            sender.sendMessage(temp_BungeeCord);
            sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
            return false;
         }

         if (MySQL.alreadyhastheplayerhome(p.getName())) {
            String temp_BungeeCord = Variable.Lang_YML.getString("HasBeenCreate");
            if (temp_BungeeCord.contains("<ServerName>")) {
               temp_BungeeCord = temp_BungeeCord.replace("<ServerName>", MySQL.getServer(p.getName()));
            }

            sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
            sender.sendMessage(temp_BungeeCord);
            sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
            return false;
         }
      } else {
         List<String> ownedHomes = HomeAPI.getOwnedHomes(p.getName());
         if (ownedHomes.size() >= this.getMaxOwnedHomes()) {
            sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
            sender.sendMessage("§8[§6错误庄园§8] §c您当前已拥有 §e" + ownedHomes.size() + " §c个庄园，最多只能拥有 §e" + this.getMaxOwnedHomes() + " §c个");
            sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
            return false;
         }

         String nextHomeName = this.getNextOwnedHomeName(p);
         if (nextHomeName == null) {
            sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
            sender.sendMessage("§8[§6错误庄园§8] §c未找到可用的庄园编号槽位");
            sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
            return false;
         }

         Variable.pendingCreateHomeName.put(p.getName(), nextHomeName);
      }

      YamlConfiguration yamlConfiguration = YamlConfiguration.loadConfiguration(Variable.f_log);
      int nowID = yamlConfiguration.getInt("NowID", 0);
      int MaxID = yamlConfiguration.getInt("MaxID", 1000);
      if (nowID >= MaxID) {
         String temp = Variable.Lang_YML.getString("ReachMaxCreate");
         sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
         sender.sendMessage(temp);
         sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
         return false;
      } else {
         return true;
      }
   }

   public void invite_guoqi(final Player p) {
      (new BukkitRunnable() {
         public void run() {
            if (Variable.invite_list.containsKey(p.getName())) {
               if (p != null) {
                  String temp2 = Variable.Lang_YML.getString("InviteOtherHasBeenOutDated");
                  if (temp2.contains("<Name>")) {
                     temp2 = temp2.replace("<Name>", Variable.invite_list.get(p.getName()));
                  }

                  p.sendMessage(temp2);
               }

               Player beinvite = Bukkit.getPlayer(Variable.invite_list.get(p.getName()));
               if (beinvite != null) {
                  String temp = Variable.Lang_YML.getString("InviteHasBeenOutDated");
                  if (temp.contains("<Name>")) {
                     temp = temp.replace("<Name>", p.getName());
                  }

                  beinvite.sendMessage(temp);
               }

               Variable.invite_list.remove(p.getName());
               Variable.inviteHomeName.remove(p.getName());
            }
         }
      }).runTaskLater(Main.JavaPlugin, 600L);
   }

   @EventHandler
   public boolean onCommand(final CommandSender sender, Command cmd, String Label, final String[] args) {
      if (!cmd.getName().equalsIgnoreCase("st")) {
         return false;
      } else if (Main.JavaPlugin.getConfig().getBoolean("DisableFunctionButTeleport") && Variable.bungee) {
         if (sender instanceof Player p) {
            if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
               if (sender instanceof Player temp && !com.Util.Perm.has(temp, "ErrorTown.Admin.Reload") && !com.Util.Perm.has(temp, "ErrorTown.Admin")) {
                  sender.sendMessage(Variable.Lang_YML.getString("PlayerIsNotOperator"));
                  return false;
               }

               for (Player ps : Bukkit.getOnlinePlayers()) {
                  if (ps.getOpenInventory() != null) {
                     InventoryHolder inv = ps.getOpenInventory().getTopInventory().getHolder();
                     if (inv instanceof CheckGui
                        || inv instanceof CreateGui
                        || inv instanceof DenyGui
                        || inv instanceof InviteGui
                        || inv instanceof MainGui
                        || inv instanceof ManageGui
                        || inv instanceof ManageGui2
                        || inv instanceof TrustGui
                        || inv instanceof VisitGui) {
                        sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                        sender.sendMessage(Variable.Lang_YML.getString("CloseGuiWhenPluginReload"));
                        sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
                        ps.closeInventory();
                     }
                  }
               }

               for (World temp : Bukkit.getWorlds()) {
                  if (Variable.hololist.containsKey(temp.getName())) {
                     for (HologramCompat.Handle temp2 : Variable.hololist.get(temp.getName())) {
                        temp2.delete();
                     }
                  }
               }

               Main.init();
               sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
               sender.sendMessage(Variable.Lang_YML.getString("ReloadSuccess"));
               sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
               return false;
            }

            if (args.length == 1 && (args[0].equalsIgnoreCase("home") || args[0].equalsIgnoreCase("h"))) {
               if (!MySQL.alreadyhastheplayerjoin(p.getName()) && !MySQL.alreadyhastheplayerhome(p.getName())) {
                  String tempx = Variable.Lang_YML.getString("NoCreateOrJoin");
                  sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                  sender.sendMessage(tempx);
                  sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
                  return false;
               }

               if (MySQL.alreadyhastheplayerjoin(p.getName())
                  && !MySQL.getJoinServer(p.getName()).equalsIgnoreCase(Main.JavaPlugin.getConfig().getString("Server"))) {
                  try {
                     if (Main.JavaPlugin.getConfig().getBoolean("Debug")) {
                        Main.JavaPlugin.getLogger().info("[调试]:跨服传送信号已发送给" + MySQL.getJoinServer(p.getName()) + "服务器");
                     }

                     Channel.waitDelayToSomeWhere(p, MySQL.getJoinServer(p.getName()), "sh h");
                  } catch (IOException var38) {
                     var38.printStackTrace();
                  }

                  (new BukkitRunnable() {
                     public void run() {
                        if (Main.JavaPlugin.getConfig().getBoolean("Debug")) {
                           Main.JavaPlugin.getLogger().info("[调试]:传送玩家" + p.getName() + "服务器" + MySQL.getJoinServer(p.getName()));
                        }

                        Channel.sendPlayerToServer(p, MySQL.getJoinServer(p.getName()));
                     }
                  }).runTaskLater(Main.JavaPlugin, 20L);
                  return false;
               }

               if (MySQL.alreadyhastheplayerhome(p.getName())
                  && !MySQL.getServer(p.getName()).equalsIgnoreCase(Main.JavaPlugin.getConfig().getString("Server"))) {
                  try {
                     if (Main.JavaPlugin.getConfig().getBoolean("Debug")) {
                        Main.JavaPlugin.getLogger().info("[调试]:跨服传送信号已发送给" + MySQL.getServer(p.getName()) + "服务器");
                     }

                     Channel.waitDelayToSomeWhere(p, MySQL.getServer(p.getName()), "sh h");
                  } catch (IOException var39) {
                     var39.printStackTrace();
                  }

                  (new BukkitRunnable() {
                     public void run() {
                        if (Main.JavaPlugin.getConfig().getBoolean("Debug")) {
                           Main.JavaPlugin.getLogger().info("[调试]:跨服传送信号已发送给" + MySQL.getServer(p.getName()) + "服务器");
                        }

                        Channel.sendPlayerToServer(p, MySQL.getServer(p.getName()));
                     }
                  }).runTaskLater(Main.JavaPlugin, 20L);
                  return false;
               }
            }

            if (args.length == 3 && args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("homeinfo")) {
               if (sender instanceof Player tempx && !com.Util.Perm.has(tempx, "ErrorTown.Admin") && !com.Util.Perm.has(tempx, "ErrorTown.Admin.Info")) {
                  sender.sendMessage(Variable.Lang_YML.getString("PlayerIsNotOperator"));
                  return false;
               }

               this.sendAdminHomeInfo(sender, args[2]);
               return false;
            }

            if (args.length == 3 && args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("playerhomes")) {
               if (sender instanceof Player tempx && !com.Util.Perm.has(tempx, "ErrorTown.Admin") && !com.Util.Perm.has(tempx, "ErrorTown.Admin.Info")) {
                  sender.sendMessage(Variable.Lang_YML.getString("PlayerIsNotOperator"));
                  return false;
               }

               List<String> homes = this.getHomesForPlayer(args[2]);
               sender.sendMessage("§8[§6错误庄园§8] §f玩家: §e" + args[2]);
               if (homes.isEmpty()) {
                  sender.sendMessage("§7未找到该玩家拥有或加入的家园");
               } else {
                  sender.sendMessage("§7家园列表: §f" + String.join("§7, §f", homes));
               }

               return false;
            }

            if (args.length == 3 && args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("audit")) {
               if (sender instanceof Player tempx && !com.Util.Perm.has(tempx, "ErrorTown.Admin") && !com.Util.Perm.has(tempx, "ErrorTown.Admin.Info")) {
                  sender.sendMessage(Variable.Lang_YML.getString("PlayerIsNotOperator"));
                  return false;
               }

               this.sendAuditSummary(sender, args[2], 10);
               return false;
            }

            if (args.length == 3 && args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("stalehomes")) {
               if (sender instanceof Player tempx && !com.Util.Perm.has(tempx, "ErrorTown.Admin") && !com.Util.Perm.has(tempx, "ErrorTown.Admin.Info")) {
                  sender.sendMessage(Variable.Lang_YML.getString("PlayerIsNotOperator"));
                  return false;
               }

               long days;
               try {
                  days = Long.parseLong(args[2]);
               } catch (Exception var40) {
                  sender.sendMessage("§c用法: /sh admin stalehomes <天数>");
                  return false;
               }

               List<Home> staleHomes = this.getStaleHomes(days * 24L * 60L * 60L * 1000L);
               sender.sendMessage("§8[§6错误庄园§8] §f长期未活跃家园预览: §e" + staleHomes.size() + " §f个");
               int shown = 0;

               for (Home home : staleHomes) {
                  sender.sendMessage("§7- §e" + home.getName() + " §8| §7最近活跃: §f" + this.formatLastActive(home.getLastActive()));
                  if (++shown >= 15) {
                     break;
                  }
               }

               if (staleHomes.size() > 15) {
                  sender.sendMessage("§8... 还有 §7" + (staleHomes.size() - 15) + " §8个未展示");
               }

               return false;
            }

            if (args.length == 4 && args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("setlevel")) {
               if (sender instanceof Player tempx && !com.Util.Perm.has(tempx, "ErrorTown.Admin.SetLevel") && !com.Util.Perm.has(tempx, "ErrorTown.Admin")) {
                  sender.sendMessage(Variable.Lang_YML.getString("PlayerIsNotOperator"));
                  return false;
               }

               if (!Util.CheckIsHome(args[2])) {
                  String tip = Variable.Lang_YML.getString("NowIsNotHome");
                  sender.sendMessage(tip);
                  return false;
               }

               if (Variable.bungee) {
                  MySQL.setLevel(args[2], String.valueOf(Integer.valueOf(args[3])));
               } else {
                  File f2 = new File(Variable.Tempf, args[2] + ".yml");
                  YamlConfiguration yamlConfiguration = YamlConfiguration.loadConfiguration(f2);
                  yamlConfiguration.set("Level", Integer.valueOf(args[3]));

                  try {
                     yamlConfiguration.save(f2);
                  } catch (IOException var41) {
                     var41.printStackTrace();
                  }
               }

               FirstBorderShaped.AddShapeBorder(Bukkit.getWorld(Variable.world_prefix + args[2]));
               sender.sendMessage(Variable.Lang_YML.getString("AdminSetLevelSuccess"));
               return false;
            }

            if (args.length == 4 && args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("addlevel")) {
               if (sender instanceof Player tempx && !com.Util.Perm.has(tempx, "ErrorTown.Admin.AddLevel") && !com.Util.Perm.has(tempx, "ErrorTown.Admin")) {
                  sender.sendMessage(Variable.Lang_YML.getString("PlayerIsNotOperator"));
                  return false;
               }

               if (!Util.CheckIsHome(args[2])) {
                  String tip = Variable.Lang_YML.getString("NowIsNotHome");
                  sender.sendMessage(tip);
                  return false;
               }

               if (Variable.bungee) {
                  MySQL.setLevel(args[2], String.valueOf(Integer.valueOf(MySQL.getLevel(args[2])) + Integer.valueOf(args[3])));
               } else {
                  File f2 = new File(Variable.Tempf, args[2] + ".yml");
                  YamlConfiguration yamlConfiguration = YamlConfiguration.loadConfiguration(f2);
                  yamlConfiguration.set("Level", yamlConfiguration.getInt("Level") + Integer.valueOf(args[3]));

                  try {
                     yamlConfiguration.save(f2);
                  } catch (IOException var42) {
                     var42.printStackTrace();
                  }
               }

               FirstBorderShaped.AddShapeBorder(Bukkit.getWorld(Variable.world_prefix + args[2]));
               sender.sendMessage(Variable.Lang_YML.getString("AdminAddLevelSuccess"));
               return false;
            }

            if (args.length == 2 && args[0].equalsIgnoreCase("create") && Main.JavaPlugin.getConfig().getBoolean("BungeeCord")) {
               if (MySQL.alreadyhastheplayerjoin(p.getName())) {
                  String temp_BungeeCord = Variable.Lang_YML.getString("HasBeenJoin");
                  if (temp_BungeeCord.contains("<ServerName>")) {
                     temp_BungeeCord = temp_BungeeCord.replace("<ServerName>", MySQL.getJoinServer(p.getName()));
                  }

                  sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                  sender.sendMessage(temp_BungeeCord);
                  sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
                  return false;
               }

               if (MySQL.alreadyhastheplayerhome(p.getName())) {
                  String temp_BungeeCord = Variable.Lang_YML.getString("HasBeenCreate");
                  if (temp_BungeeCord.contains("<ServerName>")) {
                     temp_BungeeCord = temp_BungeeCord.replace("<ServerName>", MySQL.getServer(p.getName()));
                  }

                  sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                  sender.sendMessage(temp_BungeeCord);
                  sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
                  return false;
               }

               if (Main.JavaPlugin.getConfig().getBoolean("AutoReCreateInLowerLagHome")
                  && !Variable.wait_to_command.containsKey(p.getName())
                  && Main.JavaPlugin.getConfig().getBoolean("BungeeCord")) {
                  if (Main.JavaPlugin.getConfig().getString("DecideBy").equalsIgnoreCase("Player")) {
                     if (!MySQL.getLowerstLagServer().equalsIgnoreCase(Main.JavaPlugin.getConfig().getString("Server"))) {
                        try {
                           Channel.waitToCommand(p, MySQL.getLowerstLagServer(), "sh create " + args[1]);
                        } catch (IOException var43) {
                           var43.printStackTrace();
                        }

                        p.sendMessage(Variable.Lang_YML.getString("StartLowestLagServer"));
                        Channel.sendPlayerToServer(p, MySQL.getLowerstLagServer());
                        return false;
                     }
                  } else if (!MySQL.getHighestTPSServer().equalsIgnoreCase(Main.JavaPlugin.getConfig().getString("Server"))) {
                     double now = 0.0;
                     if (Bukkit.getVersion().contains("1.7.10")) {
                        now = R1_7_10.getTps();
                     } else {
                        double se1 = Double.valueOf(PlaceholderAPI.setPlaceholders(null, "%server_tps_1%").replace("*", ""));
                        double se2 = Double.valueOf(PlaceholderAPI.setPlaceholders(null, "%server_tps_5%").replace("*", ""));
                        double se3 = Double.valueOf(PlaceholderAPI.setPlaceholders(null, "%server_tps_15%").replace("*", ""));
                        now = (se1 + se2 + se3) / 3.0;
                     }

                     if (MySQL.getServerAmount(MySQL.getLowerstLagServer()) != now) {
                        try {
                           Channel.waitToCommand(p, MySQL.getHighestTPSServer(), "sh create " + args[1]);
                        } catch (IOException var44) {
                           var44.printStackTrace();
                        }

                        p.sendMessage(Variable.Lang_YML.getString("StartLowestLagServer"));
                        Channel.sendPlayerToServer(p, MySQL.getHighestTPSServer());
                        return false;
                     }
                  }

                  return false;
               }
            }

            if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
               if (sender instanceof Player tempx && !com.Util.Perm.has(tempx, "ErrorTown.Admin.Reload") && !com.Util.Perm.has(tempx, "ErrorTown.Admin")) {
                  sender.sendMessage(Variable.Lang_YML.getString("PlayerIsNotOperator"));
                  return false;
               }

               for (Player pe : Bukkit.getOnlinePlayers()) {
                  if (pe.getOpenInventory() != null) {
                     InventoryHolder inv = pe.getOpenInventory().getTopInventory().getHolder();
                     if (inv instanceof CheckGui
                        || inv instanceof CreateGui
                        || inv instanceof DenyGui
                        || inv instanceof InviteGui
                        || inv instanceof MainGui
                        || inv instanceof ManageGui
                        || inv instanceof ManageGui2
                        || inv instanceof TrustGui
                        || inv instanceof VisitGui) {
                        sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                        sender.sendMessage(Variable.Lang_YML.getString("CloseGuiWhenPluginReload"));
                        sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
                        pe.closeInventory();
                     }
                  }
               }

               for (World tempx : Bukkit.getWorlds()) {
                  if (Variable.hololist.containsKey(tempx.getName())) {
                     for (HologramCompat.Handle temp2 : Variable.hololist.get(tempx.getName())) {
                        temp2.delete();
                     }
                  }
               }

               Main.init();
               sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
               sender.sendMessage(Variable.Lang_YML.getString("ReloadSuccess"));
               sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
               return false;
            }

            if (args.length == 2 && (args[0].equalsIgnoreCase("tp") || args[0].equalsIgnoreCase("visit") || args[0].equalsIgnoreCase("v"))) {
               if (!Main.JavaPlugin.getConfig().getBoolean("Permission.Visit") && !com.Util.Perm.has(p, "ErrorTown.Visit")) {
                  String tip = Variable.Lang_YML.getString("NoPermissionCheck");
                  if (tip.contains("<Permission>")) {
                     tip = tip.replace("<Permission>", "ErrorTown.Visit");
                  }

                  p.sendMessage(tip);
                  return false;
               }

               if (Util.CheckIsHome(args[1])) {
                  if (!Util.CanEnterHome(p, args[1]) && !com.Util.Perm.has(p, "ErrorTown.forcetp")) {
                     p.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                     String tempxx = Variable.Lang_YML.getString("TeleportStranger");
                     p.sendMessage(tempxx);
                     p.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
                     return false;
                  }

                  Home targetHome = HomeAPI.getHome(args[1]);
                  if (targetHome == null) {
                     sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                     sender.sendMessage(Variable.Lang_YML.getString("TpNotExist"));
                     sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
                     return false;
                  }

                  List<String> blacklist = targetHome.getDenys();
                  if (blacklist == null) {
                     blacklist = new ArrayList<>();
                  }

                  for (int i = 0; i < blacklist.size(); i++) {
                     if (blacklist.get(i).equalsIgnoreCase(p.getName())) {
                        String tempxx = Variable.Lang_YML.getString("TeleportInBlack");
                        sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                        sender.sendMessage(tempxx);
                        sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
                        return false;
                     }
                  }

                  if (Variable.bungee) {
                     if (Util.CheckIsHome(args[1])) {
                        if (!MySQL.getServer(args[1]).equalsIgnoreCase(Main.JavaPlugin.getConfig().getString("Server"))) {
                           try {
                              Channel.waitDelayToSomeWhere(p, MySQL.getServer(args[1]), "sh visit " + args[1]);
                           } catch (IOException var45) {
                              var45.printStackTrace();
                           }

                           Channel.sendPlayerToServer(p, MySQL.getServer(args[1]));
                           return false;
                        }
                     } else {
                        String tpNotExist = Variable.Lang_YML.getString("TpNotExist");
                        sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                        sender.sendMessage(tpNotExist);
                        sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
                     }
                  }

                  return false;
               }

               String tempxx = Variable.Lang_YML.getString("TpNotExist");
               sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
               sender.sendMessage(tempxx);
               sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
               return false;
            }

            if (args.length == 1 && (args[0].equalsIgnoreCase("Open") || args[0].equalsIgnoreCase("Menu"))) {
               MainGui gui = new MainGui(p);
               p.openInventory(gui.getInventory());
               return false;
            }

            if (args.length == 2 && args[0].equalsIgnoreCase("Open") && args[1].equalsIgnoreCase("Main")) {
               MainGui gui = new MainGui(p);
               p.openInventory(gui.getInventory());
               return false;
            }

            if (args.length == 2 && args[0].equalsIgnoreCase("Open") && args[1].equalsIgnoreCase("OwnedHomes")) {
               if (Variable.bungee) {
                  sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                  sender.sendMessage("§8[§6错误庄园§8] §c跨服模式暂未接入多庄园返回菜单");
                  sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
                  return false;
               }

               List<String> ownedHomes = HomeAPI.getOwnedHomes(p.getName());
               if (ownedHomes.isEmpty()) {
                  sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                  sender.sendMessage(Variable.Lang_YML.getString("NoCreateOrJoin"));
                  sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
                  return false;
               }

               if (ownedHomes.size() == 1) {
                  return this.teleportToOwnedHomeLocal(p, sender, ownedHomes.get(0));
               }

               OwnedHomesGui gui = new OwnedHomesGui(p);
               p.openInventory(gui.getInventory());
               return false;
            }

            if (args.length == 2 && args[0].equalsIgnoreCase("Open") && args[1].equalsIgnoreCase("Check")) {
               CheckGui gui = new CheckGui(p);
               p.openInventory(gui.getInventory());
               return false;
            }

            if (args.length == 2 && args[0].equalsIgnoreCase("Open") && args[1].equalsIgnoreCase("Create")) {
               CreateGui gui = new CreateGui(p);
               p.openInventory(gui.getInventory());
               return false;
            }

            if (args.length == 2 && args[0].equalsIgnoreCase("Open") && args[1].equalsIgnoreCase("Manage")) {
               ManageGui gui = new ManageGui(p);
               p.openInventory(gui.getInventory());
               return false;
            }

            if (args.length == 2 && args[0].equalsIgnoreCase("Open") && args[1].equalsIgnoreCase("Manage2")) {
               ManageGui2 gui = new ManageGui2(p);
               p.openInventory(gui.getInventory());
               return false;
            }

            if (args.length == 2 && args[0].equalsIgnoreCase("Open") && args[1].equalsIgnoreCase("Visit")) {
               VisitGui gui = new VisitGui();
               p.openInventory(gui.getInventory());
               return false;
            }

            if (args.length == 2 && args[0].equalsIgnoreCase("Open") && args[1].equalsIgnoreCase("Invite")) {
               InviteGui gui = new InviteGui();
               p.openInventory(gui.getInventory());
               return false;
            }

            if (args.length == 2 && args[0].equalsIgnoreCase("Open") && args[1].equalsIgnoreCase("Trust")) {
               TrustGui gui = new TrustGui();
               p.openInventory(gui.getInventory());
               return false;
            }

            if (args.length == 2 && args[0].equalsIgnoreCase("Open") && args[1].equalsIgnoreCase("Deny")) {
               DenyGui gui = new DenyGui();
               p.openInventory(gui.getInventory());
               return false;
            }

            if (args.length == 1 && args[0].equalsIgnoreCase("close")) {
               if (p.getOpenInventory() != null) {
                  p.closeInventory();
               }

               return false;
            }
         }

         sender.sendMessage(Variable.Lang_YML.getString("DisableFunctionTip"));
         return false;
      } else if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
         if (sender instanceof Player tempxx && !com.Util.Perm.has(tempxx, "ErrorTown.Admin.Reload") && !com.Util.Perm.has(tempxx, "ErrorTown.Admin")) {
            sender.sendMessage(Variable.Lang_YML.getString("PlayerIsNotOperator"));
            return false;
         } else {
            for (Player p : Bukkit.getOnlinePlayers()) {
               if (p.getOpenInventory() != null) {
                  InventoryHolder inv = p.getOpenInventory().getTopInventory().getHolder();
                  if (inv instanceof CheckGui
                     || inv instanceof CreateGui
                     || inv instanceof DenyGui
                     || inv instanceof InviteGui
                     || inv instanceof MainGui
                     || inv instanceof ManageGui
                     || inv instanceof ManageGui2
                     || inv instanceof TrustGui
                     || inv instanceof VisitGui) {
                     sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                     sender.sendMessage(Variable.Lang_YML.getString("CloseGuiWhenPluginReload"));
                     sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
                     p.closeInventory();
                  }
               }
            }

            for (World tempxx : Bukkit.getWorlds()) {
               if (Variable.hololist.containsKey(tempxx.getName())) {
                  for (HologramCompat.Handle temp2 : Variable.hololist.get(tempxx.getName())) {
                     temp2.delete();
                  }
               }
            }

            Main.init();
            sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
            sender.sendMessage(Variable.Lang_YML.getString("ReloadSuccess"));
            sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
            return false;
         }
      } else if (args.length == 2 && args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("setspawn")) {
         if (sender instanceof Player tempxxx) {
            if (!com.Util.Perm.has(tempxxx, "ErrorTown.Admin") && !com.Util.Perm.has(tempxxx, "ErrorTown.SetSpawn")) {
               sender.sendMessage(Variable.Lang_YML.getString("PlayerIsNotOperator"));
               return false;
            } else {
               Player px = (Player)sender;
               World world = px.getWorld();
               if (!Bukkit.getVersion().contains("1.7.10") && !Bukkit.getVersion().contains("1.7.2")) {
                  world.setSpawnLocation(px.getLocation());
               } else {
                  world.setSpawnLocation((int)px.getLocation().getX(), (int)px.getLocation().getY(), (int)px.getLocation().getZ());
               }

               if (Variable.hook_multiverseCore) {
                  MultiverseCore mvcore = MultiverseCompat.plugin();
                  MVWorldManager mv_m = mvcore.getMVWorldManager();
                  MultiverseWorld mv = mv_m.getMVWorld(px.getLocation().getWorld().getName());
                  mv.setSpawnLocation(px.getLocation());
               }

               sender.sendMessage(Variable.Lang_YML.getString("AdminSetSpawnSuccess"));
               return false;
            }
         } else {
            sender.sendMessage(Variable.Lang_YML.getString("CommandSenderIsNotAllowToUseTheCommand"));
            return false;
         }
      } else if (args.length == 3 && args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("clearout")) {
         if (sender instanceof Player tempxxxx && !com.Util.Perm.has(tempxxxx, "ErrorTown.Admin")) {
            sender.sendMessage(Variable.Lang_YML.getString("PlayerIsNotOperator"));
            return false;
         } else if (!Variable.wait_to_confirm_command.contains(sender.getName())) {
            Variable.wait_to_confirm_command.add(sender.getName());
            String message2 = Variable.Lang_YML.getString("OutdateWorldConfirm");
            if (message2.contains("<Day>")) {
               message2 = message2.replace("<Day>", args[2]);
            }

            sender.sendMessage(message2);
            (new BukkitRunnable() {
               public void run() {
                  if (Variable.wait_to_confirm_command.contains(sender.getName())) {
                     Variable.wait_to_confirm_command.remove(sender.getName());
                  }
               }
            }).runTaskLater(Main.JavaPlugin, 100L);
            return false;
         } else {
            Variable.wait_to_confirm_command.remove(sender.getName());
            long nowx = System.currentTimeMillis();
            int amount = 0;
            List<String> who_has_been_delete = new ArrayList<>();
            if (Variable.bungee) {
               for (String worldname : MySQL.getAllWorlds()) {
                  long before_time = Long.valueOf(MySQL.getVisitTime(worldname));
                  long distance = (nowx - before_time) / 86400000L;
                  if (distance > Long.valueOf(args[2])) {
                     HomeAPI.delHome(worldname);
                     who_has_been_delete.add(worldname);
                     amount++;
                  }
               }
            } else {
               File folder = new File(Variable.Tempf);

               File[] arrayOfFile;
               for (File tempxxxx : arrayOfFile = folder.listFiles()) {
                  long lastModified = tempxxxx.lastModified();
                  long distance = (nowx - lastModified) / 86400000L;
                  if (distance > Long.valueOf(args[2])) {
                     String want_to = tempxxxx.getPath().replace(Variable.Tempf, "").replace(".yml", "").replace(Variable.file_loc_prefix, "");
                     HomeAPI.delHome(want_to);
                     who_has_been_delete.add(want_to);
                     amount++;
                  }
               }
            }

            String message = Variable.Lang_YML.getString("OutdatedWorldHasBeenDeleted");
            if (message.contains("<Amount>")) {
               message = message.replace("<Amount>", String.valueOf(amount));
            }

            if (message.contains("<List>")) {
               message = message.replace("<List>", who_has_been_delete.toString());
            }

            sender.sendMessage(message);
            return false;
         }
      } else if (args.length == 3 && args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("load")) {
         if (sender instanceof Player p2 && !p2.isOp() && !com.Util.Perm.has(p2, "ErrorTown.Admin")) {
            sender.sendMessage(Variable.Lang_YML.getString("PlayerIsNotOperator"));
            return false;
         } else {
            File newf;
            if (Variable.world_prefix.equalsIgnoreCase("")) {
               if (Bukkit.getVersion().toString().toUpperCase().contains("ARCLIGHT")) {
                  newf = new File(Variable.single_server_gen + Variable.world_prefix + Variable.file_loc_prefix + args[2]);
               } else {
                  newf = new File(Variable.single_server_gen + "world" + Variable.file_loc_prefix + args[2]);
               }
            } else {
               newf = new File(Variable.single_server_gen + Variable.world_prefix + Variable.file_loc_prefix + args[2]);
            }

            if (!newf.exists() && !newf.isDirectory() && !args[2].equalsIgnoreCase("world")) {
               sender.sendMessage(Variable.Lang_YML.getString("WorldIsNotExist"));
               return false;
            } else {
               WorldCreator creator = null;
               creator = new WorldCreator(Variable.world_prefix + args[2]);
               Variable.create_list_home.add(Variable.world_prefix + args[2]);
               Bukkit.createWorld(creator);
               sender.sendMessage("Loaded the World: " + args[2]);
               World worldx = Bukkit.getWorld(args[2]);
               if (sender instanceof Player pxx) {
                  pxx.teleport(worldx.getSpawnLocation());
                  pxx.sendMessage(Variable.Lang_YML.getString("WorldTeleport"));
               }

               return false;
            }
         }
      } else if (args.length == 3 && args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("tp")) {
         if (sender instanceof Player p3 && !com.Util.Perm.has(p3, "ErrorTown.Admin.TP." + args[2]) && !com.Util.Perm.has(p3, "ErrorTown.Admin.TP.*")) {
            sender.sendMessage(Variable.Lang_YML.getString("PlayerIsNotOperator"));
            return false;
         } else {
            File newfx;
            if (Variable.world_prefix.equalsIgnoreCase("")) {
               if (Bukkit.getVersion().toString().toUpperCase().contains("ARCLIGHT")) {
                  newfx = new File(Variable.single_server_gen + Variable.world_prefix + Variable.file_loc_prefix + args[2]);
               } else {
                  newfx = new File(Variable.single_server_gen + "world" + Variable.file_loc_prefix + args[2]);
               }
            } else {
               newfx = new File(Variable.single_server_gen + Variable.world_prefix + Variable.file_loc_prefix + args[2]);
            }

            if (!newfx.exists() && !newfx.isDirectory() && !args[2].equalsIgnoreCase("world")) {
               sender.sendMessage(Variable.Lang_YML.getString("WorldIsNotExist"));
               return false;
            } else {
               WorldCreator creator = null;
               Variable.create_list_home.add(Variable.world_prefix + args[2]);
               creator = new WorldCreator(Variable.world_prefix + args[2]);
               Bukkit.createWorld(creator);
               if (sender instanceof Player pxx) {
                  World worldx = Bukkit.getWorld(args[2]);
                  pxx.teleport(worldx.getSpawnLocation());
                  pxx.sendMessage(Variable.Lang_YML.getString("WorldTeleport"));
               }

               return false;
            }
         }
      } else if (args.length == 4 && args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("addlevel")) {
         if (sender instanceof Player tempxxxxx && !com.Util.Perm.has(tempxxxxx, "ErrorTown.Admin.AddLevel") && !com.Util.Perm.has(tempxxxxx, "ErrorTown.Admin")) {
            sender.sendMessage(Variable.Lang_YML.getString("PlayerIsNotOperator"));
            return false;
         } else if (!Util.CheckIsHome(args[2])) {
            String tip = Variable.Lang_YML.getString("NowIsNotHome");
            sender.sendMessage(tip);
            return false;
         } else {
            if (Variable.bungee) {
               MySQL.setLevel(args[2], String.valueOf(Integer.valueOf(MySQL.getLevel(args[2])) + Integer.valueOf(args[3])));
            } else {
               File f2 = new File(Variable.Tempf, args[2] + ".yml");
               YamlConfiguration yamlConfiguration = YamlConfiguration.loadConfiguration(f2);
               yamlConfiguration.set("Level", yamlConfiguration.getInt("Level") + Integer.valueOf(args[3]));

               try {
                  yamlConfiguration.save(f2);
               } catch (IOException var46) {
                  var46.printStackTrace();
               }
            }

            FirstBorderShaped.AddShapeBorder(Bukkit.getWorld(Variable.world_prefix + args[2]));
            sender.sendMessage(Variable.Lang_YML.getString("AdminAddLevelSuccess"));
            return false;
         }
      } else if (args.length == 4 && args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("setlevel")) {
         if (sender instanceof Player tempxxxxx && !com.Util.Perm.has(tempxxxxx, "ErrorTown.Admin.SetLevel") && !com.Util.Perm.has(tempxxxxx, "ErrorTown.Admin")) {
            sender.sendMessage(Variable.Lang_YML.getString("PlayerIsNotOperator"));
            return false;
         } else if (!Util.CheckIsHome(args[2])) {
            String tip = Variable.Lang_YML.getString("NowIsNotHome");
            sender.sendMessage(tip);
            return false;
         } else {
            if (Variable.bungee) {
               MySQL.setLevel(args[2], String.valueOf(Integer.valueOf(args[3])));
            } else {
               File f2 = new File(Variable.Tempf, args[2] + ".yml");
               YamlConfiguration yamlConfiguration = YamlConfiguration.loadConfiguration(f2);
               yamlConfiguration.set("Level", Integer.valueOf(args[3]));

               try {
                  yamlConfiguration.save(f2);
               } catch (IOException var47) {
                  var47.printStackTrace();
               }
            }

            FirstBorderShaped.AddShapeBorder(Bukkit.getWorld(Variable.world_prefix + args[2]));
            sender.sendMessage(Variable.Lang_YML.getString("AdminSetLevelSuccess"));
            return false;
         }
      } else if (args.length == 2 && args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("export")) {
         if (sender instanceof Player tempxxxxx && !com.Util.Perm.has(tempxxxxx, "ErrorTown.Admin")) {
            sender.sendMessage(Variable.Lang_YML.getString("PlayerIsNotOperator"));
            return false;
         } else if (!Variable.bungee) {
            sender.sendMessage(Variable.Lang_YML.getString("ExportOrImportButBungeeCordHasBeenDisabled"));
            return false;
         } else {
            MySQL.data_export(sender);
            return false;
         }
      } else if (args.length == 2 && args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("import")) {
         if (sender instanceof Player tempxxxxx && !com.Util.Perm.has(tempxxxxx, "ErrorTown.Admin")) {
            sender.sendMessage(Variable.Lang_YML.getString("PlayerIsNotOperator"));
            return false;
         } else if (!Variable.bungee) {
            sender.sendMessage(Variable.Lang_YML.getString("ExportOrImportButBungeeCordHasBeenDisabled"));
            return false;
         } else {
            MySQL.data_import(sender);
            return false;
         }
      } else if (args.length == 2 && args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("pwp")) {
         if (sender instanceof Player tempxxxxx && !com.Util.Perm.has(tempxxxxx, "ErrorTown.Admin")) {
            sender.sendMessage(Variable.Lang_YML.getString("PlayerIsNotOperator"));
            return false;
         } else {
            String gen_mdk = "plugins\\PlayerWorldsPro";
            YamlConfiguration yamlConfiguration1 = YamlConfiguration.loadConfiguration(new File(gen_mdk, "config.yml"));
            String prefix = yamlConfiguration1.getString("Basic.World-Prefix");
            HashMap<String, String> map = new HashMap<>();
            YamlConfiguration yamlConfiguration2 = YamlConfiguration.loadConfiguration(new File(gen_mdk, "players.yml"));

            for (String key : yamlConfiguration2.getKeys(false)) {
               map.put(key, yamlConfiguration2.getString(key + ".Name"));
            }

            YamlConfiguration yamlConfiguration3 = YamlConfiguration.loadConfiguration(new File(gen_mdk, "data.yml"));

            for (String key : yamlConfiguration3.getKeys(true)) {
               if (key.split("\\.").length == 2) {
                  String uuid = key.split("\\.")[1];
                  boolean lockweather = !yamlConfiguration3.getBoolean("Worlds." + uuid + ".1.WeatherCycle");
                  boolean pvp = !yamlConfiguration3.getBoolean("Worlds." + uuid + ".1.PvP");
                  boolean pickup = !yamlConfiguration3.getBoolean("Worlds." + uuid + ".1.Item-Pickup");
                  boolean drop = !yamlConfiguration3.getBoolean("Worlds." + uuid + ".1.Drop-Item");
                  boolean publicAccess = false;
                  boolean has_set_spawn = false;
                  double X = 0.0;
                  double Y = 0.0;
                  double Z = 0.0;
                  boolean has_set_Members = false;
                  new ArrayList();
                  String publicswitch = yamlConfiguration3.getString("Worlds." + uuid + ".1.Access");
                  if (publicswitch.equalsIgnoreCase("Public")) {
                     publicAccess = true;
                  }

                  if (yamlConfiguration3.getString("Worlds." + uuid + ".1.Spawn") != null) {
                     has_set_spawn = true;
                     String[] tempxxxxx = yamlConfiguration3.getString("Worlds." + uuid + ".1.Spawn").split(";");
                     X = Double.valueOf(tempxxxxx[0]);
                     Y = Double.valueOf(tempxxxxx[1]);
                     Z = Double.valueOf(tempxxxxx[2]);
                  }

                  List<String> Trustlist = new ArrayList<>();
                  if (yamlConfiguration3.getStringList("Worlds." + uuid + ".1.Members") != null) {
                     List<String> list = yamlConfiguration3.getStringList("Worlds." + uuid + ".1.Members");

                     for (int ix = 0; ix < list.size(); ix++) {
                        if (map.get(list.get(ix)) != null && !map.get(list.get(ix)).contains("\\-")) {
                           Trustlist.add(map.get(list.get(ix)));
                           has_set_Members = true;
                        }
                     }
                  }

                  String name = map.get(uuid);
                  File f2 = new File(Variable.Tempf, name + ".yml");
                  if (f2.exists()) {
                     return false;
                  }

                  try {
                     f2.createNewFile();
                  } catch (IOException var50) {
                     var50.printStackTrace();
                  }

                  YamlConfiguration yamlConfiguration = YamlConfiguration.loadConfiguration(f2);
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
                  yamlConfiguration.createSection("icon");
                  yamlConfiguration.createSection("advertisement");
                  yamlConfiguration.createSection("limitblock");
                  yamlConfiguration.set("Public", publicAccess);
                  yamlConfiguration.set("pickup", pickup);
                  yamlConfiguration.set("drop", drop);
                  yamlConfiguration.set("pvp", pvp);
                  yamlConfiguration.set("locktime", false);
                  yamlConfiguration.set("time", 0);
                  yamlConfiguration.set("lockweather", lockweather);
                  int set_level = 1;
                  yamlConfiguration.set("Level", set_level);
                  yamlConfiguration.set("Server", Main.JavaPlugin.getConfig().getString("Server"));
                  yamlConfiguration.createSection("flowers");
                  yamlConfiguration.createSection("popularity");
                  yamlConfiguration.createSection("gifts");
                  yamlConfiguration.set("flowers", 0);
                  yamlConfiguration.set("popularity", 0);
                  yamlConfiguration.set("gifts", new ArrayList());
                  yamlConfiguration.set("advertisement", new ArrayList());
                  yamlConfiguration.set("limitblock", new ArrayList());
                  yamlConfiguration.set("icon", "");

                  try {
                     yamlConfiguration.save(f2);
                  } catch (IOException var49) {
                     var49.printStackTrace();
                  }

                  if (has_set_Members && Trustlist != null) {
                     yamlConfiguration.set("Members", Trustlist);
                  }

                  yamlConfiguration.createSection("X");
                  yamlConfiguration.createSection("Y");
                  yamlConfiguration.createSection("Z");
                  if (has_set_spawn) {
                     yamlConfiguration.set("X", X);
                     yamlConfiguration.set("Y", Y);
                     yamlConfiguration.set("Z", Z);
                  } else {
                     yamlConfiguration.set("X", 0.0);
                     yamlConfiguration.set("Y", 0.0);
                     yamlConfiguration.set("Z", 0.0);
                  }

                  try {
                     yamlConfiguration.save(f2);
                  } catch (IOException var48) {
                     var48.printStackTrace();
                  }

                  sender.sendMessage("成功导出" + name + ".yml到本插件的数据文件夹");
                  System.out.println("成功导出" + name + ".yml到本插件的数据文件夹");
                  File oldf;
                  if (Variable.world_prefix.equalsIgnoreCase("")) {
                     if (Bukkit.getVersion().toString().toUpperCase().contains("ARCLIGHT")) {
                        oldf = new File(Variable.single_server_gen + "PlayerWorldsPro" + Variable.file_loc_prefix);
                     } else {
                        oldf = new File(Variable.single_server_gen + "world" + Variable.file_loc_prefix);
                     }
                  } else {
                     oldf = new File(Variable.single_server_gen + "PlayerWorldsPro" + Variable.file_loc_prefix);
                  }

                  File newfxx;
                  if (Variable.world_prefix.equalsIgnoreCase("")) {
                     if (Bukkit.getVersion().toString().toUpperCase().contains("ARCLIGHT")) {
                        newfxx = new File(Variable.single_server_gen + Variable.world_prefix);
                     } else {
                        newfxx = new File(Variable.single_server_gen + "world" + Variable.file_loc_prefix);
                     }
                  } else {
                     newfxx = new File(Variable.single_server_gen + Variable.world_prefix);
                  }

                  File oldFile = new File(oldf.getPath().toString() + Variable.file_loc_prefix + prefix + uuid);
                  System.out.println(oldFile.getPath());
                  File newFile = new File(newfxx.getPath().toString() + Variable.file_loc_prefix + name);
                  if (oldFile.renameTo(newFile)) {
                     sender.sendMessage(name + "玩家的存档文件重命名成功");
                     System.out.println(name + "玩家的存档文件重命名成功");
                  } else {
                     sender.sendMessage(name + "玩家重命名失败！");
                     System.out.println(name + "玩家重命名失败！");
                  }
               }
            }

            return false;
         }
      } else if (args.length == 2 && args[0].equalsIgnoreCase("ForceDelete")) {
         if (sender instanceof Player tempxxxxx && !com.Util.Perm.has(tempxxxxx, "ErrorTown.Admin")) {
            sender.sendMessage(Variable.Lang_YML.getString("PlayerIsNotOperator"));
            return false;
         } else {
            World worldx = Bukkit.getWorld(Variable.world_prefix + args[1]);
            if (worldx != null) {
               for (Player p6 : Bukkit.getWorld(Variable.world_prefix + args[1]).getPlayers()) {
                  p6.teleport(Bukkit.getWorld(Main.JavaPlugin.getConfig().getString("Spawn")).getSpawnLocation());
                  p6.sendMessage(Variable.Lang_YML.getString("WorldHasBeenForceDelete"));
               }

               Bukkit.unloadWorld(Variable.world_prefix + args[1], true);
               sender.sendMessage(Variable.Lang_YML.getString("WorldHasBeenForceDeleteSuccess"));
            }

            if (Variable.hook_multiverseCore) {
               MultiverseCore mvcore = MultiverseCompat.plugin();
               MVWorldManager mv_m = mvcore.getMVWorldManager();
               mv_m.removeWorldFromConfig(Variable.world_prefix + args[1]);
            }

            YamlConfiguration yamlConfiguration = YamlConfiguration.loadConfiguration(Variable.f_log);
            yamlConfiguration.set("NowID", yamlConfiguration.getInt("NowID") - 1);

            try {
               yamlConfiguration.save(Variable.f_log);
            } catch (IOException var51) {
               var51.printStackTrace();
            }

            Object f;
            if (Variable.world_prefix.equalsIgnoreCase("")) {
               if (!Bukkit.getVersion().toString().toUpperCase().contains("ARCLIGHT") && !Bukkit.getVersion().toString().contains("1.20.1")) {
                  f = new File(Variable.single_server_gen + "world" + Variable.file_loc_prefix + args[1]);
               } else {
                  f = new File(Variable.single_server_gen + Variable.world_prefix + args[1]);
               }
            } else {
               f = new File(Variable.single_server_gen + Variable.world_prefix + args[1]);
            }

            Util.deleteFile((File)f);
            sender.sendMessage(Variable.Lang_YML.getString("WorldHasBeenDeleted"));
            if (Variable.bungee) {
               MySQL.removePlayer(args[1]);
               sender.sendMessage(Variable.Lang_YML.getString("WorldConfigHasBeenDeleted"));
            } else {
               File f2x = new File(Variable.Tempf, args[1] + ".yml");
               if (f2x.exists()) {
                  sender.sendMessage(Variable.Lang_YML.getString("WorldConfigHasBeenDeleted"));
                  f2x.delete();
               }
            }

            return false;
         }
      } else if (args.length == 2 && args[0].equalsIgnoreCase("UnLoad")) {
         if (sender instanceof Player tempxxxxx && !com.Util.Perm.has(tempxxxxx, "ErrorTown.Admin")) {
            sender.sendMessage(Variable.Lang_YML.getString("PlayerIsNotOperator"));
            return false;
         } else {
            for (Player p6 : Bukkit.getWorld(args[1]).getPlayers()) {
               p6.teleport(Bukkit.getWorld(Main.JavaPlugin.getConfig().getString("Spawn")).getSpawnLocation());
            }

            Bukkit.unloadWorld(args[1], true);
            sender.sendMessage(Variable.Lang_YML.getString("ForceUnLoadWorld"));
            return false;
         }
      } else if (args.length == 2 && args[0].equalsIgnoreCase("rank")) {
         if (sender instanceof Player tempxxxxx
            && !Main.JavaPlugin.getConfig().getBoolean("Permission.CommandUser")
            && !com.Util.Perm.has(tempxxxxx, "ErrorTown.Rank")
            && !com.Util.Perm.has(tempxxxxx, "ErrorTown.command.user")) {
            String tip = Variable.Lang_YML.getString("NoPermissionCheck");
            if (tip.contains("<Permission>")) {
               tip = tip.replace("<Permission>", "ErrorTown.Rank");
            }

            tempxxxxx.sendMessage(tip);
            return false;
         } else {
            (new BukkitRunnable() {
               public void run() {
                  int YS = Integer.valueOf(args[1]);
                  if (Variable.world_StaticsTick.size() == 0) {
                     init.refreshWorldStatics(false);
                  }

                  var __cfg0 = Main.JavaPlugin.getConfig().getStringList("StatisticsTop");
                  for (int c = 0; c < __cfg0.size(); c++) {
                     String a = __cfg0.get(c);
                     sender.sendMessage(a);
                  }

                  for (int i = 10 * YS - 10; i < YS * 10 && i < Variable.world_StaticsTick.size(); i++) {
                     StaticsTick s = Variable.world_StaticsTick.get(i);
                     String temp = Main.JavaPlugin.getConfig().getString("ShowFormat");
                     if (temp.contains("<index>")) {
                        temp = temp.replace("<index>", String.valueOf(i + 1));
                     }

                     if (temp.contains("<world>")) {
                        temp = temp.replace("<world>", s.name);
                     }

                     if (temp.contains("<tile>")) {
                        temp = temp.replace("<tile>", String.valueOf(s.tile));
                     }

                     if (temp.contains("<chunk>")) {
                        temp = temp.replace("<chunk>", String.valueOf(s.chunk));
                     }

                     if (temp.contains("<entity>")) {
                        temp = temp.replace("<entity>", String.valueOf(s.entity));
                     }

                     if (temp.contains("<drop>")) {
                        temp = temp.replace("<drop>", String.valueOf(s.drop));
                     }

                     if (temp.contains("<tps>")) {
                        temp = temp.replace("<tps>", String.format(Main.JavaPlugin.getConfig().getString("FormatInfo"), s.tps));
                     }

                     sender.sendMessage(temp);
                  }

                  var __cfg1 = Main.JavaPlugin.getConfig().getStringList("StatisticsEnd");
                  for (int c = 0; c < __cfg1.size(); c++) {
                     String a = __cfg1.get(c);
                     sender.sendMessage(a);
                  }
               }
            }).runTaskAsynchronously(Main.JavaPlugin);
            return false;
         }
      } else if (args.length == 1 && args[0].equalsIgnoreCase("dimension")) {
         if (sender instanceof Player tempxxxxx && !com.Util.Perm.has(tempxxxxx, "ErrorTown.Admin")) {
            sender.sendMessage(Variable.Lang_YML.getString("PlayerIsNotOperator"));
            return false;
         } else {
            if (Bukkit.getBukkitVersion().toString().toUpperCase().contains("1.12.2")) {
               for (World tempxxxxx : Bukkit.getWorlds()) {
                  sender.sendMessage("§dWorld:§b" + tempxxxxx.getName() + "§d,Dimension:§b" + R1_12_2.getID(tempxxxxx));
               }
            } else if (Bukkit.getBukkitVersion().toString().toUpperCase().contains("1.7.10")) {
               for (World tempxxxxx : Bukkit.getWorlds()) {
                  sender.sendMessage("§dWorld:§b" + tempxxxxx.getName() + "§d,Dimension:§b" + R1_7_10.getID(tempxxxxx));
               }
            } else {
               sender.sendMessage(Variable.Lang_YML.getString("DimensionNotAllow"));
            }

            return false;
         }
      } else if (args.length == 4 && args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("create")) {
         if (sender instanceof Player tempxxxxx && !com.Util.Perm.has(tempxxxxx, "ErrorTown.Admin")) {
            sender.sendMessage(Variable.Lang_YML.getString("PlayerIsNotOperator"));
            return false;
         } else {
            if (Main.JavaPlugin.getConfig().getBoolean("BungeeCord")) {
               if (MySQL.alreadyhastheplayerjoin(args[2])) {
                  String temp_BungeeCord = Variable.Lang_YML.getString("AdminCreateHasJoinButNotServer");
                  if (temp_BungeeCord.contains("<ServerName>")) {
                     temp_BungeeCord = temp_BungeeCord.replace("<ServerName>", MySQL.getJoinServer(args[2]));
                  }

                  sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                  sender.sendMessage(temp_BungeeCord);
                  sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
                  return false;
               }

               if (MySQL.alreadyhastheplayerhome(args[2])) {
                  String temp_BungeeCord = Variable.Lang_YML.getString("AdminCreateHasCreateButNotServer");
                  if (temp_BungeeCord.contains("<ServerName>")) {
                     temp_BungeeCord = temp_BungeeCord.replace("<ServerName>", MySQL.getServer(args[2]));
                  }

                  sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                  sender.sendMessage(temp_BungeeCord);
                  sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
                  return false;
               }
            } else {
               File f2x = new File(Variable.Tempf, args[2].replace(Variable.world_prefix, "") + ".yml");
               if (f2x.exists()) {
                  sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                  sender.sendMessage(Variable.Lang_YML.getString("HasBeenCreate"));
                  sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
                  return false;
               }

               boolean has_been_join = false;
               File folder = new File(Variable.Tempf);

               File[] arrayOfFile;
               for (File tempxxxxx : arrayOfFile = folder.listFiles()) {
                  String want_to = tempxxxxx.getPath().replace(Variable.Tempf, "").replace(".yml", "").replace(Variable.file_loc_prefix, "");
                  YamlConfiguration yamlConfiguration1 = YamlConfiguration.loadConfiguration(tempxxxxx);

                  var __cfg2 = yamlConfiguration1.getStringList("OP");
                  for (int ixx = 0; ixx < __cfg2.size(); ixx++) {
                     String temp_str = __cfg2.get(ixx);
                     if (temp_str.equalsIgnoreCase(args[2])) {
                        has_been_join = true;
                        break;
                     }
                  }
               }

               if (has_been_join) {
                  String tempxxxxx = Variable.Lang_YML.getString("HasAlreadyJoinOthers");
                  sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                  sender.sendMessage(tempxxxxx);
                  sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
                  return false;
               }
            }

            YamlConfiguration yamlConfiguration = YamlConfiguration.loadConfiguration(Variable.f_log);
            if (!yamlConfiguration.contains("NowID")) {
               yamlConfiguration.set("NowID", 0);
            }

            if (!yamlConfiguration.contains("MaxID")) {
               yamlConfiguration.set("MaxID", 1000);
            }

            try {
               yamlConfiguration.save(Variable.f_log);
            } catch (IOException var57) {
               var57.printStackTrace();
            }

            int nowID = yamlConfiguration.getInt("NowID");
            int MaxID = yamlConfiguration.getInt("MaxID");
            if (nowID >= MaxID) {
               String tempxxxxx = Variable.Lang_YML.getString("ReachMaxCreate");
               sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
               sender.sendMessage(tempxxxxx);
               sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
               return false;
            } else {
               String v = args[3];
               if (v.equalsIgnoreCase("1")) {
                  WorldCreator creator = null;
                  creator = new WorldCreator(Variable.world_prefix + args[2]);
                  if (Main.JavaPlugin.getConfig().getBoolean("generateStructures")) {
                     creator = creator.generateStructures(true);
                  } else {
                     creator = creator.generateStructures(false);
                  }

                  creator.type(WorldType.NORMAL);
                  creator = creator.type(WorldType.NORMAL);
                  Variable.create_list_home.add(Variable.world_prefix + args[2]);
                  Bukkit.createWorld(creator);
               } else if (v.equalsIgnoreCase("2")) {
                  WorldCreator creator = null;
                  creator = new WorldCreator(Variable.world_prefix + args[2]);
                  Main.JavaPlugin.getDefaultWorldGenerator(Variable.world_prefix + args[2], "");
                  if (Main.JavaPlugin.getConfig().getBoolean("generateStructures")) {
                     creator = creator.generateStructures(true);
                  } else {
                     creator = creator.generateStructures(false);
                  }

                  // type(FLAT) alone leaves generatorSettings empty, which makes vanilla log
                  // "No key layers in MapLike[{}]" and fall back to its built-in preset.
                  creator = com.Util.SuperflatPreset.apply(creator);
                  Variable.create_list_home.add(Variable.world_prefix + args[2]);
                  Bukkit.createWorld(creator);
               } else {
                  if (v.equalsIgnoreCase("random")) {
                     List<String> list = Main.JavaPlugin.getConfig().getStringList("Random");
                     int num = (int)(Math.random() * list.size());
                     Bukkit.dispatchCommand(sender, "sh admin create " + args[2] + " " + list.get(num));
                     return false;
                  }

                  String oldDir = Variable.worldFinal + v;
                  String newDir = "";
                  if (Variable.world_prefix.equalsIgnoreCase("")) {
                     if (!Bukkit.getVersion().toString().toUpperCase().contains("ARCLIGHT") && !Bukkit.getVersion().toString().contains("1.20.1")) {
                        newDir = Variable.single_server_gen + "world" + Variable.file_loc_prefix + args[2];
                     } else {
                        newDir = Variable.single_server_gen + Variable.world_prefix + args[2];
                     }
                  } else {
                     newDir = Variable.single_server_gen + Variable.world_prefix + args[2];
                  }

                  File exist_file = new File(oldDir);
                  if (!exist_file.exists()) {
                     String tempxxxxx = Variable.Lang_YML.getString("WorldFileNotExist");
                     if (tempxxxxx.contains("<name>")) {
                        tempxxxxx = tempxxxxx.replace("<name>", v);
                     }

                     sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                     sender.sendMessage(tempxxxxx);
                     sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
                     return false;
                  }

                  Util.copyDir(oldDir, newDir);
                  WorldCreator creator = null;
                  creator = new WorldCreator(Variable.world_prefix + args[2]);
                  if (Main.JavaPlugin.getConfig().getBoolean("generateStructures")) {
                     creator.generateStructures(true);
                  } else {
                     creator.generateStructures(false);
                  }

                  Variable.create_list_home.add(Variable.world_prefix + args[2]);
                  Bukkit.createWorld(creator);
               }

               if (Variable.hook_multiverseCore) {
                  String seed = Long.toString(Main.JavaPlugin.getConfig().getLong("Seed"));
                  if (seed.equalsIgnoreCase("0")) {
                     seed = "";
                  }

                  MultiverseCore mvcore = MultiverseCompat.plugin();
                  MVWorldManager mv_m = mvcore.getMVWorldManager();
                  if (mv_m.isMVWorld(Variable.world_prefix + args[2])) {
                     mv_m.removeWorldFromConfig(Variable.world_prefix + args[2]);
                  }

                  if (v.equalsIgnoreCase("1")) {
                     mv_m.addWorld(
                        Variable.world_prefix + args[2],
                        Environment.NORMAL,
                        seed,
                        WorldType.NORMAL,
                        Main.JavaPlugin.getConfig().getBoolean("generateStructures"),
                        ""
                     );
                  } else if (v.equalsIgnoreCase("2")) {
                     mv_m.addWorld(
                        Variable.world_prefix + args[2],
                        Environment.NORMAL,
                        seed,
                        WorldType.FLAT,
                        Main.JavaPlugin.getConfig().getBoolean("generateStructures"),
                        ""
                     );
                  } else {
                     mv_m.addWorld(
                        Variable.world_prefix + args[2],
                        Environment.NORMAL,
                        seed,
                        WorldType.NORMAL,
                        Main.JavaPlugin.getConfig().getBoolean("generateStructures"),
                        ""
                     );
                  }

                  if (Main.JavaPlugin.getConfig().getBoolean("EnableChatPrefix")) {
                     MultiverseWorld mv = mv_m.getMVWorld(Variable.world_prefix + args[2]);
                     World worldxx = Bukkit.getWorld(Variable.world_prefix + args[2]);
                     String tempxxxxx = Variable.Lang_YML.getString("PlaceHolders.WorldName");
                     if (tempxxxxx.contains("<PlayerName>")) {
                        tempxxxxx = tempxxxxx.replace("<PlayerName>", worldxx.getName().replace(Variable.world_prefix, ""));
                     }

                     if (tempxxxxx.contains("<WorldName>")) {
                        tempxxxxx = tempxxxxx.replace("<WorldName>", worldxx.getName().replace(Variable.world_prefix, ""));
                     }

                     mv.setAlias(tempxxxxx);
                  }

                  MultiverseWorld mvx = mv_m.getMVWorld(Variable.world_prefix + args[2]);
                  mvx.setAutoLoad(false);
               }

               World worldxxx = Bukkit.getWorld(Variable.world_prefix + args[2]);
               if (Variable.bungee) {
                  MySQL.insertvalue(
                     args[2],
                     "",
                     "",
                     "",
                     String.valueOf(Main.JavaPlugin.getConfig().getBoolean("NormalPublic")),
                     "1",
                     String.valueOf(Main.JavaPlugin.getConfig().getBoolean("NormalPVP")),
                     String.valueOf(Main.JavaPlugin.getConfig().getBoolean("NormalPickup")),
                     String.valueOf(Main.JavaPlugin.getConfig().getBoolean("NormalDrop")),
                     Main.JavaPlugin.getConfig().getString("Server"),
                     "false",
                     "false",
                     "0",
                     String.valueOf(worldxxx.getSpawnLocation().getX()),
                     String.valueOf(worldxxx.getSpawnLocation().getY()),
                     String.valueOf(worldxxx.getSpawnLocation().getZ()),
                     "0",
                     "0",
                     "",
                     "",
                     "",
                     "",
                     ""
                  );
               } else {
                  File f2xx = new File(Variable.Tempf, args[2] + ".yml");
                  if (f2xx.exists()) {
                     sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                     sender.sendMessage(Variable.Lang_YML.getString("AdminCreateHomeForPlayerFailed"));
                     sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
                     return false;
                  }

                  try {
                     f2xx.createNewFile();
                  } catch (IOException var56) {
                     var56.printStackTrace();
                  }

                  YamlConfiguration yamlConfiguration1 = YamlConfiguration.loadConfiguration(f2xx);
                  yamlConfiguration1.createSection("Members");
                  yamlConfiguration1.createSection("OP");
                  yamlConfiguration1.createSection("Denys");
                  yamlConfiguration1.createSection("Public");
                  yamlConfiguration1.createSection("Level");
                  yamlConfiguration1.createSection("pvp");
                  yamlConfiguration1.createSection("pickup");
                  yamlConfiguration1.createSection("drop");
                  yamlConfiguration1.createSection("Server");
                  yamlConfiguration1.createSection("locktime");
                  yamlConfiguration1.createSection("lockweather");
                  yamlConfiguration1.createSection("time");
                  if (!yamlConfiguration.contains("NowID")) {
                     yamlConfiguration.set("NowID", 0);
                  }

                  if (!yamlConfiguration.contains("MaxID")) {
                     yamlConfiguration.set("MaxID", 1000);
                  }

                  try {
                     yamlConfiguration.save(Variable.f_log);
                  } catch (IOException var55) {
                     var55.printStackTrace();
                  }

                  yamlConfiguration1.set("Public", Main.JavaPlugin.getConfig().getBoolean("NormalPublic"));
                  yamlConfiguration1.set("pickup", Main.JavaPlugin.getConfig().getBoolean("NormalPVP"));
                  yamlConfiguration1.set("drop", Main.JavaPlugin.getConfig().getBoolean("NormalPickup"));
                  yamlConfiguration1.set("pvp", Main.JavaPlugin.getConfig().getBoolean("NormalDrop"));
                  yamlConfiguration1.set("locktime", false);
                  yamlConfiguration1.set("time", 0);
                  yamlConfiguration1.set("lockweather", false);
                  int set_level = 1;
                  yamlConfiguration1.set("Level", set_level);
                  yamlConfiguration1.set("Server", Main.JavaPlugin.getConfig().getString("Server"));

                  try {
                     yamlConfiguration1.save(f2xx);
                  } catch (IOException var54) {
                     var54.printStackTrace();
                  }

                  yamlConfiguration.set("NowID", nowID + 1);

                  try {
                     yamlConfiguration.save(Variable.f_log);
                  } catch (IOException var53) {
                     var53.printStackTrace();
                  }

                  yamlConfiguration1.createSection("flowers");
                  yamlConfiguration1.createSection("popularity");
                  yamlConfiguration1.createSection("gifts");
                  yamlConfiguration1.createSection("icon");
                  yamlConfiguration1.createSection("advertisement");
                  yamlConfiguration1.createSection("limitblock");
                  yamlConfiguration1.set("flowers", 0);
                  yamlConfiguration1.set("popularity", 0);
                  yamlConfiguration1.set("gifts", new ArrayList());
                  yamlConfiguration1.set("icon", "");
                  yamlConfiguration1.set("advertisement", new ArrayList());
                  yamlConfiguration1.set("limitblock", new ArrayList());
                  yamlConfiguration1.createSection("X");
                  yamlConfiguration1.createSection("Y");
                  yamlConfiguration1.createSection("Z");
                  Location loc = worldxxx.getSpawnLocation();
                  yamlConfiguration1.set("X", loc.getX());
                  yamlConfiguration1.set("Y", loc.getY());
                  yamlConfiguration1.set("Z", loc.getZ());

                  try {
                     yamlConfiguration1.save(f2xx);
                  } catch (IOException var52) {
                     var52.printStackTrace();
                  }

                  if (Main.JavaPlugin.getConfig().getInt("MaxSpawnMonstersAmount") != -1) {
                     worldxxx.setMonsterSpawnLimit(Main.JavaPlugin.getConfig().getInt("MaxSpawnMonstersAmount"));
                  }

                  if (Main.JavaPlugin.getConfig().getInt("MaxSpawnAnimalsAmount") != -1) {
                     worldxxx.setAnimalSpawnLimit(Main.JavaPlugin.getConfig().getInt("MaxSpawnAnimalsAmount"));
                  }

                  FirstBorderShaped.ShapeBorder(worldxxx);
                  sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                  sender.sendMessage(Variable.Lang_YML.getString("AdminCreateHomeForPlayerSuccess"));
                  sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
               }

               return false;
            }
         }
      } else if (args.length == 1 && args[0].equalsIgnoreCase("Help")) {
         Bukkit.dispatchCommand(sender, "sh help 1");
         return false;
      } else if (args.length == 1 && args[0].equalsIgnoreCase("rank")) {
         Bukkit.dispatchCommand(sender, "sh rank 1");
         return false;
      } else if (!(sender instanceof Player pxx)) {
         sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
         sender.sendMessage(Variable.Lang_YML.getString("CommandSenderTip"));
         sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
         return false;
      } else if (Util.CheckIllegalName(pxx)) {
         sender.sendMessage(Variable.Lang_YML.getString("PlayerHasIllegalName"));
         return false;
      } else {
         return this.onCommandPlayer(sender, args, pxx);
       }
   }

   /** Number of {@code Help-N} pages the language files are expected to carry. */
   private static final int HELP_PAGES = 6;

   /**
    * Renders one page of {@code /sh help}.
    *
    * <p>Each language entry is a {@code "text,command"} pair; rendering and the click handling live
    * in {@link com.Util.ClickableText}. An absent or empty {@code Help-N} key used to print a bare
    * header and footer with nothing between them - the shipped language files carried no
    * {@code Help-N} keys at all - so it now says so instead of looking like an empty help screen.</p>
    *
    * @return whether {@code pageArgument} named a real page and was handled
    */
   private boolean sendHelpPage(final CommandSender sender, final Player target, final String pageArgument) {
      int page = -1;
      for (int candidate = 1; candidate <= HELP_PAGES; candidate++) {
         if (String.valueOf(candidate).equals(pageArgument)) {
            page = candidate;
            break;
         }
      }
      if (page < 0) {
         return false;
      }

      String key = "Help-" + page;
      java.util.List<String> pageLines = Variable.Lang_YML.getStringList(key);
      sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
      if (pageLines.isEmpty()) {
         com.Util.Diag.warnOnce(
            "help-page-" + page,
            "Language key " + key + " is missing or empty; /sh help " + page + " has nothing to show"
         );
         sender.sendMessage(
            com.Util.Lang.get("HelpPageMissing", "§8[§6错误庄园§8] §c帮助页未配置, 请检查语言文件的 <Key> 键").replace("<Key>", key)
         );
      } else {
         for (String line : pageLines) {
            com.Util.ClickableText.sendPairLine(target, line);
         }
      }

      sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
      return true;
   }

   private boolean onCommandPlayer(final CommandSender sender, final String[] args, final Player pxx) {
   if (args.length != 1 || !args[0].equalsIgnoreCase("Open") && !args[0].equalsIgnoreCase("Menu")) {
         if (args.length == 2 && args[0].equalsIgnoreCase("Open") && args[1].equalsIgnoreCase("Main")) {
            MainGui gui = new MainGui(pxx);
            pxx.openInventory(gui.getInventory());
            return false;
         } else if (args.length == 2 && args[0].equalsIgnoreCase("Open") && args[1].equalsIgnoreCase("Check")) {
            CheckGui gui = new CheckGui(pxx);
            pxx.openInventory(gui.getInventory());
            return false;
         } else if (args.length == 2 && args[0].equalsIgnoreCase("Open") && args[1].equalsIgnoreCase("Create")) {
            CreateGui gui = new CreateGui(pxx);
            pxx.openInventory(gui.getInventory());
            return false;
         } else if (args.length == 2 && args[0].equalsIgnoreCase("Open") && args[1].equalsIgnoreCase("Manage")) {
            ManageGui gui = new ManageGui(pxx);
            pxx.openInventory(gui.getInventory());
            return false;
         } else if (args.length == 2 && args[0].equalsIgnoreCase("Open") && args[1].equalsIgnoreCase("Manage2")) {
            ManageGui2 gui = new ManageGui2(pxx);
            pxx.openInventory(gui.getInventory());
            return false;
         } else if (args.length == 2 && args[0].equalsIgnoreCase("Open") && args[1].equalsIgnoreCase("Manage3")) {
            ManageGui3 gui = new ManageGui3(pxx);
            pxx.openInventory(gui.getInventory());
            return false;
         } else if (args.length == 2 && args[0].equalsIgnoreCase("Open") && args[1].equalsIgnoreCase("SetSpawn")) {
            SetSpawnGui gui = new SetSpawnGui(pxx);
            pxx.openInventory(gui.getInventory());
            return false;
         } else if (args.length == 2 && args[0].equalsIgnoreCase("Open") && args[1].equalsIgnoreCase("Biome")) {
            BiomeGui gui = new BiomeGui(pxx);
            pxx.openInventory(gui.getInventory());
            return false;
         } else if (args.length == 2 && args[0].equalsIgnoreCase("Open") && args[1].equalsIgnoreCase("Rules")) {
            RulesGui gui = new RulesGui(pxx);
            pxx.openInventory(gui.getInventory());
            return false;
         } else if (args.length == 2 && args[0].equalsIgnoreCase("Open") && args[1].equalsIgnoreCase("Upgrade")) {
            if (!(sender instanceof Player)) {
               return false;
            } else {
               UpgradeGui gui = new UpgradeGui(pxx);
               pxx.openInventory(gui.getInventory());
               return false;
            }
         } else if (args.length == 3 && args[0].equalsIgnoreCase("Open") && args[1].equalsIgnoreCase("CreateCost")) {
            if (!(sender instanceof Player)) {
               return false;
            } else if (!Main.JavaPlugin.getConfig().getBoolean("CreateCost.Enable", false)) {
               Bukkit.dispatchCommand(sender, "sh create " + Main.JavaPlugin.getConfig().getString("NormalType", "2"));
               return false;
            } else {
               String seedMode = args[2].toLowerCase();
               String defaultType = Main.JavaPlugin.getConfig().getString("NormalType", "2");
               CreateCostGui gui = new CreateCostGui(pxx, defaultType, seedMode);
               pxx.openInventory(gui.getInventory());
               return false;
            }
         } else if (args.length == 2 && args[0].equalsIgnoreCase("Open") && args[1].equalsIgnoreCase("CreateCost")) {
            if (!(sender instanceof Player)) {
               return false;
            } else if (!Main.JavaPlugin.getConfig().getBoolean("CreateCost.Enable", false)) {
               Bukkit.dispatchCommand(sender, "sh create " + Main.JavaPlugin.getConfig().getString("NormalType", "2"));
               return false;
            } else {
               String defaultType = Main.JavaPlugin.getConfig().getString("NormalType", "2");
               CreateCostGui gui = new CreateCostGui(pxx, defaultType, "random");
               pxx.openInventory(gui.getInventory());
               return false;
            }
         } else if (args.length == 3 && args[0].equalsIgnoreCase("Open") && args[1].equalsIgnoreCase("ServiceCost")) {
            if (!(sender instanceof Player)) {
               return false;
            } else {
               ServiceCostGui gui = new ServiceCostGui(pxx, args[2]);
               pxx.openInventory(gui.getInventory());
               return false;
            }
         } else if (args.length == 2 && args[0].equalsIgnoreCase("Open") && args[1].equalsIgnoreCase("Visit")) {
            VisitGui gui = new VisitGui();
            pxx.openInventory(gui.getInventory());
            return false;
         } else if (args.length == 2 && args[0].equalsIgnoreCase("Open") && args[1].equalsIgnoreCase("OwnedHomes")) {
            if (Variable.bungee) {
               sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
               sender.sendMessage("§8[§6错误庄园§8] §c跨服模式暂未接入多庄园返回菜单");
               sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
               return false;
            } else {
               List<String> ownedHomesx = HomeAPI.getOwnedHomes(pxx.getName());
               if (ownedHomesx.isEmpty()) {
                  sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                  sender.sendMessage(Variable.Lang_YML.getString("NoCreateOrJoin"));
                  sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
                  return false;
               } else if (ownedHomesx.size() == 1) {
                  return this.teleportToOwnedHomeLocal(pxx, sender, ownedHomesx.get(0));
               } else {
                  OwnedHomesGui gui = new OwnedHomesGui(pxx);
                  pxx.openInventory(gui.getInventory());
                  return false;
               }
            }
         } else if (args.length == 2 && args[0].equalsIgnoreCase("Open") && args[1].equalsIgnoreCase("Invite")) {
            InviteGui gui = new InviteGui();
            pxx.openInventory(gui.getInventory());
            return false;
         } else if (args.length == 2 && args[0].equalsIgnoreCase("Open") && args[1].equalsIgnoreCase("Trust")) {
            TrustGui gui = new TrustGui();
            pxx.openInventory(gui.getInventory());
            return false;
         } else if (args.length == 2 && args[0].equalsIgnoreCase("Open") && args[1].equalsIgnoreCase("Deny")) {
            DenyGui gui = new DenyGui();
            pxx.openInventory(gui.getInventory());
            return false;
         } else if (args.length == 1 && args[0].equalsIgnoreCase("close")) {
            if (pxx.getOpenInventory() != null) {
               pxx.closeInventory();
            }

            return false;
         } else {
            if (args.length == 2 && args[0].equalsIgnoreCase("Help")) {
               // Pages 1-6 used to be six copy-pasted blocks that each re-implemented the same
               // click handling, each indexing str[1] without checking it existed.
               if (this.sendHelpPage(sender, pxx, args[1])) {
                  return false;
               }
            }

            if (args.length == 1 && args[0].equalsIgnoreCase("check")) {
               if (!Main.JavaPlugin.getConfig().getBoolean("Permission.CommandUser")
                  && !com.Util.Perm.has(pxx, "ErrorTown.check")
                  && !com.Util.Perm.has(pxx, "ErrorTown.command.user")) {
                  String tip = Variable.Lang_YML.getString("NoPermissionCheck");
                  if (tip.contains("<Permission>")) {
                     tip = tip.replace("<Permission>", "ErrorTown.Check");
                  }

                  pxx.sendMessage(tip);
                   return false;
               } else {
                  (new BukkitRunnable() {
                     public void run() {
                        File folder = new File(Variable.Tempf);
                        sender.sendMessage(Variable.Lang_YML.getString("CheckListTitle"));
                        if (Variable.bungee) {
                           for (String e : MySQL.CheckHasPermission(pxx.getName())) {
                              com.Util.ClickableText.run(pxx, "§e" + e + Variable.Lang_YML.getString("CheckSuffix"), "/sh v " + e);
                           }
                        } else {
                           File[] arrayOfFile = folder.listFiles();
                           if (arrayOfFile == null) {
                              sender.sendMessage(Variable.Lang_YML.getString("CheckListEnd"));
                              return;
                           }

                           for (File temp : arrayOfFile) {
                              String want_to = temp.getPath().replace(Variable.Tempf, "").replace(".yml", "").replace(Variable.file_loc_prefix, "");
                              YamlConfiguration yamlConfiguration = YamlConfiguration.loadConfiguration(temp);

                              var __cfg9 = yamlConfiguration.getStringList("Members");
                              for (int i = 0; i < __cfg9.size(); i++) {
                                 String temp_str = __cfg9.get(i);
                                 if (temp_str.equalsIgnoreCase(pxx.getName()) || temp_str.equals("*")) {
                                     com.Util.ClickableText.run(pxx, "§e" + want_to + Variable.Lang_YML.getString("CheckSuffix"), "/sh v " + want_to);
                                 }
                              }
                           }
                        }

                        sender.sendMessage(Variable.Lang_YML.getString("CheckListEnd"));
                     }
                  }).runTask(Main.JavaPlugin);
                  return false;
               }
            } else if ((args.length == 1 || args.length == 4) && args[0].equalsIgnoreCase("setSpawn")) {
               if (!(sender instanceof Player)) {
                  sender.sendMessage("This command can only be used by players.");
                  return false;
               } else if (!this.canUseSetSpawnFeature(pxx, sender)) {
                  return false;
               } else if (args.length == 1) {
                  Variable.pendingSetSpawnTarget.remove(pxx.getName());
                  SetSpawnGui gui = new SetSpawnGui(pxx);
                  pxx.openInventory(gui.getInventory());
                  return false;
               } else {
                  double x;
                  double y;
                  double z;
                  try {
                     x = Double.parseDouble(args[1]);
                     y = Double.parseDouble(args[2]);
                     z = Double.parseDouble(args[3]);
                  } catch (NumberFormatException var58) {
                     pxx.sendMessage("§c用法: /sh setspawn [x] [y] [z]");
                     return false;
                  }

                  Variable.pendingSetSpawnTarget.put(pxx.getName(), x + "," + y + "," + z);
                  ServiceCostGui gui = new ServiceCostGui(pxx, "setspawn_coords");
                  pxx.openInventory(gui.getInventory());
                  pxx.sendMessage("§8[§6ErrorTown§8] §a已记录坐标，接下来请选择金币或点券确认修改。");
                  return false;
               }
            } else if ((args.length == 1 || args.length == 2) && args[0].equalsIgnoreCase("clearsetspawncd")) {
               if (!sender.isOp() && !com.Util.Perm.has(sender, "ErrorTown.Admin")) {
                  sender.sendMessage(Variable.Lang_YML.getString("PlayerIsNotOperator"));
                  return false;
               } else {
                  String targetName;
                  if (args.length == 2) {
                     targetName = args[1];
                  } else {
                     if (!(sender instanceof Player)) {
                        sender.sendMessage("§c用法: /sh clearsetspawncd <玩家名>");
                        return false;
                     }

                     targetName = ((Player)sender).getName();
                  }

                  Variable.setSpawnCooldown.remove(targetName);
                  Variable.pendingSetSpawnTarget.remove(targetName);
                  Player targetPlayer = Bukkit.getPlayerExact(targetName);
                  if (targetPlayer != null && targetPlayer.isOnline()) {
                     targetPlayer.sendMessage("§8[§6ErrorTown§8] §a你的边界中心冷却已被管理员清除。");
                  }

                  sender.sendMessage("§8[§6ErrorTown§8] §a已清除玩家 §e" + targetName + " §a的边界中心冷却。");
                  return false;
               }
            } else if (args.length == 1 && args[0].equalsIgnoreCase("mobs")) {
               Player p3 = (Player)sender;
               if (com.Util.Perm.has(p3, "ErrorTown.Admin")) {
                  String result = "";

                  for (Entity entity : p3.getWorld().getEntities()) {
                     if (entity instanceof LivingEntity) {
                        result = result + " " + entity.getType().toString();
                     }
                  }

                  p3.sendMessage(result);
               } else {
                  String tempxxxxxx = Variable.Lang_YML.getString("AdminCommand");
                  sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                  sender.sendMessage(tempxxxxxx);
                  sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
               }

               return false;
            } else if (args.length == 2 && args[0].equalsIgnoreCase("setBiome")) {
               if (sender instanceof Player p3) {
                  if (com.Util.Perm.has(p3, "ErrorTown.Biome")) {
                     World worldxxx = p3.getWorld();
                     Chunk chunk = p3.getWorld().getChunkAt(p3.getLocation());

                     try {
                        for (int xx = 0; xx < 16; xx++) {
                           for (int z = 0; z < 16; z++) {
                              for (int yx = 0; yx < 255; yx++) {
                                 Block block = chunk.getBlock(xx, yx, z);
                                 block.setBiome(Biome.valueOf(args[1].toUpperCase()));
                              }
                           }
                        }
                     } catch (IllegalArgumentException var140) {
                        pxx.sendMessage(Variable.Lang_YML.getString("BiomeError"));
                        return false;
                     }

                     World tworld = p3.getWorld();
                     String _biomeMsg2 = Variable.Lang_YML.getString("BiomeChangeReenter");

                     for (Player t : tworld.getPlayers()) {
                        t.sendMessage(_biomeMsg2 != null ? _biomeMsg2 : Variable.Lang_YML.getString("BiomeChangeTip"));
                     }
                  } else {
                     String Language = Variable.Lang_YML.getString("NoPermissionCheck");
                     if (Language.contains("<Permission>")) {
                        Language = Language.replace("<Permission>", "ErrorTown.Biome");
                     }

                     if (!com.Util.Perm.has(pxx, "ErrorTown.Biome")) {
                        sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                        sender.sendMessage(Language);
                        sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
                        return false;
                     }
                  }
               }

               return false;
            } else if (args.length == 1 && args[0].equalsIgnoreCase("cicdifficulty")) {
               String _diff_base = Util.getBaseHomeName(pxx.getWorld().getName());
               if (!Util.CheckOwnerAndManagerAndOP(pxx, _diff_base)) {
                  pxx.sendMessage(Variable.Lang_YML.getString("NoOwnerAndManagerPermission"));
                  return false;
               } else if (!Main.JavaPlugin.getConfig().getBoolean("DifficultyChange.Enable")) {
                  pxx.sendMessage("§c难度修改功能未开启");
                  return false;
               } else {
                  ServiceCostGui gui = new ServiceCostGui(pxx, "difficulty");
                  pxx.openInventory(gui.getInventory());
                  pxx.sendMessage("§8[§6错误庄园§8] §e请先选择支付方式，然后在聊天框输入难度数字");
                  return false;
               }
            } else {
               if (args.length == 3 && args[0].equalsIgnoreCase("servicecost")) {
                  String mode = args[1].toLowerCase();
                  String serviceKey = args[2].toLowerCase();
                  if (serviceKey.equals("setspawn_here") || serviceKey.equals("setspawn_coords")) {
                     if (!this.canUseSetSpawnFeature(pxx, sender)) {
                        return false;
                     } else if (!mode.equals("money") && !mode.equals("points")) {
                        pxx.sendMessage("§c无效的支付方式。");
                        return false;
                     } else {
                        Location targetCenter = serviceKey.equals("setspawn_here") ? pxx.getLocation().clone() : this.getPendingSetSpawnLocation(pxx);
                        if (targetCenter == null) {
                           pxx.sendMessage("§c未找到待设置的坐标，请重新输入后再试。");
                           Variable.pendingSetSpawnTarget.remove(pxx.getName());
                           return false;
                        } else {
                           this.executeSetSpawnChange(pxx, targetCenter, mode);
                           return false;
                        }
                     }
                  }

                  if (serviceKey.equals("difficulty")) {
                     String baseName = Util.getBaseHomeName(pxx.getWorld().getName());
                     if (!Util.CheckOwnerAndManagerAndOP(pxx, baseName)) {
                        pxx.sendMessage(Variable.Lang_YML.getString("NoOwnerAndManagerPermission"));
                        return false;
                     }

                     if (!Main.JavaPlugin.getConfig().getBoolean("DifficultyChange.Enable")) {
                        pxx.sendMessage("§c难度修改功能未开启");
                        return false;
                     }

                     int cost = Main.JavaPlugin.getConfig().getInt("DifficultyChange.Cost");
                     int points = Main.JavaPlugin.getConfig().getInt("DifficultyChange.Points", 0);
                     pxx.sendMessage("§a§l§m---------§8[§d家园难度设置§8]§a§l§m---------");
                     pxx.sendMessage("§e当前难度: §a" + pxx.getWorld().getDifficulty().name());
                     pxx.sendMessage("§7请在聊天框输入数字选择难度:");
                     pxx.sendMessage("§a1§7 = 和平(Peaceful)  §b2§7 = 简单(Easy)");
                     pxx.sendMessage("§63§7 = 普通(Normal)  §c4§7 = 困难(Hard)");
                     if (mode.equals("money")) {
                        pxx.sendMessage("§d本次将消耗: §6" + cost + " 金币");
                        Variable.wait_chat_input.put(pxx.getName(), "difficulty:money");
                     } else {
                        if (!mode.equals("points")) {
                           return false;
                        }

                        pxx.sendMessage("§d本次将消耗: §b" + points + " 点券");
                        Variable.wait_chat_input.put(pxx.getName(), "difficulty:points");
                     }

                     pxx.sendMessage("§a§l§m---------§8[§d输入 0 取消§8]§a§l§m---------");
                     return false;
                  }
               }

               if (args.length >= 2 && args[0].equalsIgnoreCase("upgrademember")) {
                  if (!Util.CheckOwnerAndManagerAndOP(pxx, Util.getBaseHomeName(pxx.getWorld().getName()))) {
                     pxx.sendMessage(Variable.Lang_YML.getString("NoOwnerAndManagerPermission"));
                     return false;
                  } else if (!Main.JavaPlugin.getConfig().getBoolean("MemberUpgrade.Enable")) {
                     pxx.sendMessage("§c成员扩容功能未开启");
                     return false;
                  } else if (args[1].equalsIgnoreCase("list")) {
                     List<String> plans = Main.JavaPlugin.getConfig().getStringList("MemberUpgrade.Plans");
                     pxx.sendMessage("§a§l§m--------------§8[§d扩容方案§8]§a§l§m--------------");

                     for (int ixxxxxxxxx = 0; ixxxxxxxxx < plans.size(); ixxxxxxxxx++) {
                        String[] parts = plans.get(ixxxxxxxxx).split(",");
                        int planMoney = Integer.parseInt(parts[0]);
                        int planPoints = parts.length >= 3 ? Integer.parseInt(parts[1]) : 0;
                        int planAddMembers = Integer.parseInt(parts.length >= 3 ? parts[2] : parts[1]);
                        pxx.sendMessage(
                           "§e方案" + (ixxxxxxxxx + 1) + "§7: §6" + planMoney + "§7金币 / §b" + planPoints + "§7点券, 增加 §a" + planAddMembers + "§7 个成员位"
                        );
                     }

                     pxx.sendMessage("§a§l§m--------------§8[§d输入 /sh upgrademember 方案编号§8]§a§l§m--------------");
                     return false;
                  } else {
                     int planIndex;
                     try {
                        planIndex = Integer.parseInt(args[1]) - 1;
                     } catch (NumberFormatException var60) {
                        pxx.sendMessage("§c请输入正确的方案编号");
                        return false;
                     }

                     List<String> plans = Main.JavaPlugin.getConfig().getStringList("MemberUpgrade.Plans");
                     if (planIndex >= 0 && planIndex < plans.size()) {
                        String[] parts = plans.get(planIndex).split(",");
                        int cost = Integer.parseInt(parts[0]);
                        int points = parts.length >= 3 ? Integer.parseInt(parts[1]) : 0;
                        int addMembers = Integer.parseInt(parts.length >= 3 ? parts[2] : parts[1]);
                        String baseNamex = Util.getBaseHomeName(pxx.getWorld().getName());
                        Home currentHome = HomeAPI.getHome(baseNamex);
                        if (currentHome == null) {
                           pxx.sendMessage("§c未找到当前庄园数据");
                           return false;
                        } else {
                           int maxAbs = Main.JavaPlugin.getConfig().getInt("MemberUpgrade.AbsoluteMax");
                           int currentJoinMax = this.getExpandedHomeJoinLimit(baseNamex);
                           int currentOpMax = this.getExpandedHomeOpLimit(baseNamex);
                           if (currentJoinMax + addMembers > maxAbs || currentOpMax + addMembers > maxAbs) {
                              pxx.sendMessage("§c扩容后将超过最大上限 " + maxAbs + " 人");
                              return false;
                           } else if (!Util.chargeMoneyOrPoints(pxx, cost, points, "扩容成员上限")) {
                              return false;
                           } else {
                              try {
                                 currentHome.setExtraMemberSlots(currentHome.getExtraMemberSlots() + addMembers);
                                 currentHome.setExtraOpSlots(currentHome.getExtraOpSlots() + addMembers);
                              } catch (IOException var59) {
                                 var59.printStackTrace();
                                 pxx.sendMessage("§c扩容数据保存失败，请联系管理员查看后台");
                                 return false;
                              }

                              pxx.sendMessage(
                                 "§a扩容成功! 信任成员上限从 §e"
                                    + currentJoinMax
                                    + " §a提升至 §e"
                                    + (currentJoinMax + addMembers)
                                    + "§a，管理员上限从 §e"
                                    + currentOpMax
                                    + " §a提升至 §e"
                                    + (currentOpMax + addMembers)
                              );
                              return false;
                           }
                        }
                     } else {
                        pxx.sendMessage("§c方案编号不存在");
                        return false;
                     }
                  }
               } else if (args.length == 1 && args[0].equalsIgnoreCase("createNether")) {
                  if (!Main.JavaPlugin.getConfig().getBoolean("EnableHomeNether")) {
                     pxx.sendMessage("§c当前服务器未开放家园下界创建");
                     return false;
                  } else {
                     String _cn_base = Util.getBaseHomeName(pxx.getWorld().getName());
                     if (!Util.CheckOwnerAndManagerAndOP(pxx, _cn_base)) {
                        pxx.sendMessage(Variable.Lang_YML.getString("NoOwnerAndManagerPermission"));
                        return false;
                     } else {
                        int cost = Main.JavaPlugin.getConfig().getInt("DimensionCreate.CreateNetherCost");
                        int points = Main.JavaPlugin.getConfig().getInt("DimensionCreate.CreateNetherPoints", 0);
                        if (!Util.chargeMoneyOrPoints(pxx, cost, points, "创建下界")) {
                           return false;
                        } else {
                           String netherSuffix2 = Main.JavaPlugin.getConfig().getString("HomeNetherSuffix");
                           if (netherSuffix2 == null || netherSuffix2.isEmpty()) {
                              netherSuffix2 = "_nether";
                           }

                           String _mainWorldName = Variable.world_prefix + _cn_base;
                           String netherName = _mainWorldName + netherSuffix2;
                           World existing = Bukkit.getWorld(netherName);
                           if (existing != null) {
                              pxx.sendMessage("§c您的家园下界已经存在了!");
                              return false;
                           } else {
                              if (!Variable.bungee) {
                                 File _nf = new File(Variable.Tempf, _cn_base + ".yml");
                                 if (_nf.exists()) {
                                    YamlConfiguration _ny = YamlConfiguration.loadConfiguration(_nf);
                                    _ny.set("NetherUnlocked", true);

                                    try {
                                       _ny.save(_nf);
                                    } catch (IOException var61) {
                                       com.Util.Diag.warn("Could not persist NetherUnlocked; the purchase may be lost on restart", var61);
                                    }
                                 }
                              }

                              WorldCreator wc = new WorldCreator(netherName);
                              wc.environment(Environment.NETHER);
                              Variable.create_list_home.add(netherName);
                              Bukkit.createWorld(wc);
                              pxx.sendMessage("§a家园专属下界创建成功! 通过下界传送门即可前往");
                              return false;
                           }
                        }
                     }
                  }
               } else if (args.length == 1 && args[0].equalsIgnoreCase("createEnd")) {
                  pxx.sendMessage("§c该功能已关闭,如需使用末地请联系管理员");
                  return false;
               } else if (args.length >= 1 && args[0].equalsIgnoreCase("resetOverworld")) {
                  String _ro_base = Util.getBaseHomeName(pxx.getWorld().getName());
                  if (!Util.CheckOwnerAndManagerAndOP(pxx, _ro_base)) {
                     pxx.sendMessage(Variable.Lang_YML.getString("NoOwnerAndManagerPermission"));
                     return false;
                  } else if (args.length >= 2 && args[1].equalsIgnoreCase("custom")) {
                     int _baseCost = Main.JavaPlugin.getConfig().getInt("DimensionCreate.ResetOverworldCost");
                     int _basePoints = Main.JavaPlugin.getConfig().getInt("DimensionCreate.ResetOverworldPoints", 0);
                     int _extraCost = Main.JavaPlugin.getConfig().getInt("DimensionCreate.CustomSeedExtraCost", 5000);
                     int _extraPoints = Main.JavaPlugin.getConfig().getInt("DimensionCreate.CustomSeedExtraPoints", 0);
                     int _totalCost = _baseCost + _extraCost;
                     int _totalPoints = _basePoints + _extraPoints;
                     pxx.sendMessage("§a§l§m---------§8[§d自定义种子重置§8]§a§l§m---------");
                     pxx.sendMessage("§7请在聊天框输入自定义种子(整数或字符串)");
                     pxx.sendMessage("§7输入 §c0 §7取消 | 费用: §6" + _totalCost + " 金币 §7/ §b" + _totalPoints + " 点券");
                     pxx.sendMessage("§a§l§m--------------------------------------");
                     Variable.wait_chat_input.put(pxx.getName(), "seed:" + Variable.world_prefix + _ro_base + ":" + _totalCost + ":" + _totalPoints);
                     return false;
                  } else {
                     int cost = Main.JavaPlugin.getConfig().getInt("DimensionCreate.ResetOverworldCost");
                     int points = Main.JavaPlugin.getConfig().getInt("DimensionCreate.ResetOverworldPoints", 0);
                     if (!Util.chargeMoneyOrPoints(pxx, cost, points, "重置主世界")) {
                        return false;
                     } else {
                        String worldName = Variable.world_prefix + _ro_base;
                        World _ro_world = Bukkit.getWorld(worldName);
                        World _spawn_world = Bukkit.getWorld(Main.JavaPlugin.getConfig().getString("Spawn", "world"));
                        if (_spawn_world == null) {
                           _spawn_world = (World)Bukkit.getWorlds().get(0);
                        }

                        if (_ro_world != null) {
                           for (Player pl : _ro_world.getPlayers()) {
                              pl.teleport(_spawn_world.getSpawnLocation());
                              pl.sendMessage("§c您的家园正在重置中...");
                           }

                           Bukkit.unloadWorld(worldName, false);
                        }

                        pxx.sendMessage("§e正在异步删除旧世界文件, 请稍候...");
                        Bukkit.getScheduler().runTaskAsynchronously(Main.JavaPlugin, () -> {
                           File worldDir2 = new File(worldName);
                           if (worldDir2.exists()) {
                              Util.deleteFile(worldDir2);
                           }

                           Bukkit.getScheduler().runTask(Main.JavaPlugin, () -> {
                              WorldCreator wc2 = new WorldCreator(worldName);
                              Variable.create_list_home.add(worldName);
                              final World _newWorld = Bukkit.createWorld(wc2);
                              if (!Variable.bungee) {
                                 File _rf = new File(Variable.Tempf, _ro_base + ".yml");
                                 if (_rf.exists()) {
                                    YamlConfiguration _ry = YamlConfiguration.loadConfiguration(_rf);
                                    _ry.set("TpSet", null);

                                    try {
                                       _ry.save(_rf);
                                    } catch (IOException var9) {
                                       com.Util.Diag.warn("Could not clear TpSet; the old spawn point stays on disk", var9);
                                    }
                                 }
                              }

                              if (_newWorld != null) {
                                 (new BukkitRunnable() {
                                    public void run() {
                                       if (pxx.isOnline()) {
                                          pxx.teleport(_newWorld.getSpawnLocation());
                                          pxx.sendMessage("§a家园主世界重置完成,已为您传送至新出生点!");
                                       }
                                    }
                                 }).runTaskLater(Main.JavaPlugin, 40L);
                              } else if (pxx.isOnline()) {
                                 pxx.sendMessage("§a家园主世界重置完成!");
                              }
                           });
                        });
                        return false;
                     }
                  }
               } else if (args.length == 1 && args[0].equalsIgnoreCase("resetNether")) {
                  String _rn_base = Util.getBaseHomeName(pxx.getWorld().getName());
                  if (!Util.CheckOwnerAndManagerAndOP(pxx, _rn_base)) {
                     pxx.sendMessage(Variable.Lang_YML.getString("NoOwnerAndManagerPermission"));
                     return false;
                  } else {
                     int cost = Main.JavaPlugin.getConfig().getInt("DimensionCreate.ResetNetherCost");
                     int points = Main.JavaPlugin.getConfig().getInt("DimensionCreate.ResetNetherPoints", 0);
                     if (!Util.chargeMoneyOrPoints(pxx, cost, points, "重置下界")) {
                        return false;
                     } else {
                        String netherSuffix3 = Main.JavaPlugin.getConfig().getString("HomeNetherSuffix");
                        if (netherSuffix3 == null || netherSuffix3.isEmpty()) {
                           netherSuffix3 = "_nether";
                        }

                        String netherName = Variable.world_prefix + _rn_base + netherSuffix3;
                        World nether = Bukkit.getWorld(netherName);
                        if (nether == null) {
                           pxx.sendMessage("§c您的家园下界不存在,请先创建");
                           return false;
                        } else {
                           World _overWorld = Bukkit.getWorld(Variable.world_prefix + _rn_base);
                           Location _over_spawn = _overWorld != null ? _overWorld.getSpawnLocation() : ((World)Bukkit.getWorlds().get(0)).getSpawnLocation();

                           for (Player pl : nether.getPlayers()) {
                              pl.teleport(_over_spawn);
                              pl.sendMessage("§c家园下界正在重置中...");
                           }

                           Bukkit.unloadWorld(netherName, false);
                           pxx.sendMessage("§e正在异步删除地狱文件, 请稍候...");
                           Bukkit.getScheduler().runTaskAsynchronously(Main.JavaPlugin, () -> {
                              File fn = new File(netherName);
                              if (fn.exists()) {
                                 Util.deleteFile(fn);
                              }

                              Bukkit.getScheduler().runTask(Main.JavaPlugin, () -> {
                                 WorldCreator wc2 = new WorldCreator(netherName);
                                 wc2.environment(Environment.NETHER);
                                 Variable.create_list_home.add(netherName);
                                 Bukkit.createWorld(wc2);
                                  if (pxx.isOnline()) {
                                     pxx.sendMessage("§a家园下界重置完成!");
                                 }
                              });
                           });
                           return false;
                        }
                     }
                  }
               } else if (args.length == 1 && args[0].equalsIgnoreCase("resetEnd")) {
                  pxx.sendMessage("§c该功能已关闭");
                  return false;
               } else if (args.length >= 2 && args[0].equalsIgnoreCase("title")) {
                  if (!Util.CheckOwnerAndManagerAndOP(pxx, Util.getBaseHomeName(pxx.getWorld().getName()))) {
                     pxx.sendMessage(Variable.Lang_YML.getString("NoOwnerAndManagerPermission"));
                     return false;
                  } else if (!Main.JavaPlugin.getConfig().getBoolean("HomeTitle.Enable")) {
                     pxx.sendMessage("§c标题功能未开启");
                     return false;
                  } else {
                     int maxLen = Main.JavaPlugin.getConfig().getInt("HomeTitle.MaxTitleLength");
                     String title = args[1];

                     for (int ixxxxxxxxx = 2; ixxxxxxxxx < args.length; ixxxxxxxxx++) {
                        title = title + " " + args[ixxxxxxxxx];
                     }

                     if (title.length() > maxLen) {
                        pxx.sendMessage("§c标题长度不能超过 " + maxLen + " 个字符");
                        return false;
                     } else {
                        String homeName = pxx.getWorld().getName().replace(Variable.world_prefix, "");
                        Home homex = HomeAPI.getHome(homeName);
                        if (homex == null) {
                           pxx.sendMessage("§c您不在家园中");
                           return false;
                        } else {
                           try {
                              homex.setTitle(title);
                           } catch (IOException var62) {
                              pxx.sendMessage("§c设置失败");
                              return false;
                           }

                           pxx.sendMessage("§a家园标题已设置为: §e" + title);
                           return false;
                        }
                     }
                  }
               } else if (args.length >= 2 && args[0].equalsIgnoreCase("desc")) {
                  if (!Util.CheckOwnerAndManagerAndOP(pxx, Util.getBaseHomeName(pxx.getWorld().getName()))) {
                     pxx.sendMessage(Variable.Lang_YML.getString("NoOwnerAndManagerPermission"));
                     return false;
                  } else if (!Main.JavaPlugin.getConfig().getBoolean("HomeTitle.Enable")) {
                     pxx.sendMessage("§c描述功能未开启");
                     return false;
                  } else {
                     int maxLines = Main.JavaPlugin.getConfig().getInt("HomeTitle.MaxDescriptionLines");
                     String desc = args[1];

                     for (int ixxxxxxxxx = 2; ixxxxxxxxx < args.length; ixxxxxxxxx++) {
                        desc = desc + " " + args[ixxxxxxxxx];
                     }

                     List<String> lines = new ArrayList<>(Arrays.asList(desc.split(",")));
                     if (lines.size() > maxLines) {
                        pxx.sendMessage("§c描述最多 " + maxLines + " 行(逗号分隔)");
                        return false;
                     } else {
                        String homeName = pxx.getWorld().getName().replace(Variable.world_prefix, "");
                        Home homex = HomeAPI.getHome(homeName);
                        if (homex == null) {
                           pxx.sendMessage("§c您不在家园中");
                           return false;
                        } else {
                           try {
                              homex.setDescription(lines);
                           } catch (IOException var63) {
                              pxx.sendMessage("§c设置失败");
                              return false;
                           }

                           pxx.sendMessage("§a家园描述已更新!");
                           return false;
                        }
                     }
                  }
               } else if (args.length == 1 && args[0].equalsIgnoreCase("titleinput")) {
                  if (!Util.CheckOwnerAndManagerAndOP(pxx, Util.getBaseHomeName(pxx.getWorld().getName()))) {
                     pxx.sendMessage(Variable.Lang_YML.getString("NoOwnerAndManagerPermission"));
                     return false;
                  } else if (!Main.JavaPlugin.getConfig().getBoolean("HomeTitle.Enable")) {
                     pxx.sendMessage("§c庄园标题功能未开启");
                     return false;
                  } else {
                     pxx.sendMessage("§8[§6错误庄园§8] §e请在聊天框输入新的庄园名");
                     pxx.sendMessage("§8[§6错误庄园§8] §7输入 §c0 §7取消");
                     Variable.wait_chat_input.put(pxx.getName(), "title_input");
                     return false;
                  }
               } else if (args.length == 1 && args[0].equalsIgnoreCase("descinput")) {
                  if (!Util.CheckOwnerAndManagerAndOP(pxx, Util.getBaseHomeName(pxx.getWorld().getName()))) {
                     pxx.sendMessage(Variable.Lang_YML.getString("NoOwnerAndManagerPermission"));
                     return false;
                  } else if (!Main.JavaPlugin.getConfig().getBoolean("HomeTitle.Enable")) {
                     pxx.sendMessage("§c庄园描述功能未开启");
                     return false;
                  } else {
                     pxx.sendMessage("§8[§6错误庄园§8] §e请在聊天框输入新的庄园描述");
                     pxx.sendMessage("§8[§6错误庄园§8] §7多行可用 §e| §7分隔, 输入 §c0 §7取消");
                     Variable.wait_chat_input.put(pxx.getName(), "desc_input");
                     return false;
                  }
               } else if (args.length == 2 && args[0].equalsIgnoreCase("update") && sender instanceof Player) {
                  String modex = args[1].toLowerCase();
                  if (!Main.JavaPlugin.getConfig().getBoolean("Permission.CommandUser")
                     && !com.Util.Perm.has(pxx, "ErrorTown.Update")
                     && !com.Util.Perm.has(pxx, "ErrorTown.command.user")) {
                     String tip = Variable.Lang_YML.getString("NoPermissionCheck");
                     if (tip != null && tip.contains("<Permission>")) {
                        tip = tip.replace("<Permission>", "ErrorTown.Update");
                     }

                     pxx.sendMessage(tip != null ? tip : "§c你没有升级权限");
                     return false;
                  } else if (!Util.CheckIsHome(pxx.getWorld().getName().replace(Variable.world_prefix, ""))) {
                     pxx.sendMessage(Variable.Lang_YML.getString("NowIsNotHome"));
                     return false;
                  } else if (!Util.CheckOwnerAndManagerAndOP(pxx, Util.getBaseHomeName(pxx.getWorld().getName()))) {
                     pxx.sendMessage(Variable.Lang_YML.getString("NoOwnerAndManagerPermission"));
                     return false;
                  } else {
                     File _f = new File(Variable.Tempf, pxx.getWorld().getName().replace(Variable.world_prefix, "") + ".yml");
                     if (Variable.bungee) {
                        Integer Now = Integer.valueOf(MySQL.getLevel(pxx.getWorld().getName().replace(Variable.world_prefix, "")));
                        if (Now >= Main.JavaPlugin.getConfig().getInt("MaxLevel")) {
                           String t = Variable.Lang_YML.getString("ReachMaxLevel");
                           if (t.contains("<Level>")) {
                              t = t.replace("<Level>", String.valueOf(Now));
                           }

                           pxx.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                           pxx.sendMessage(t);
                           pxx.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
                           return false;
                        }

                        int _bDiscount = Util.getUpgradeDiscount(pxx);
                        if (modex.equals("money")) {
                           double origMoney = (Double)Main.JavaPlugin.getConfig().getDoubleList("MoneyNeed").get(Now - 1);
                           double needMoney = _bDiscount < 100 ? Math.ceil(origMoney * _bDiscount / 100.0) : origMoney;
                           double haveMoney = Variable.econ.getBalance(pxx);
                           if (haveMoney < needMoney) {
                              String t = Variable.Lang_YML.getString("UpdateNoEnoughMoney");
                              if (t.contains("<NeedMoney>")) {
                                 t = t.replace("<NeedMoney>", String.valueOf((long)needMoney));
                              }

                              pxx.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                              pxx.sendMessage(t);
                              pxx.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
                              return false;
                           }

                           Variable.econ.withdrawPlayer(pxx, needMoney);
                           MySQL.setLevel(pxx.getWorld().getName().replace(Variable.world_prefix, ""), String.valueOf(Now + 1));
                        } else {
                           if (!modex.equals("points")) {
                              return false;
                           }

                           if (!Variable.PlyaerPointsModule) {
                              pxx.sendMessage("§c点券系统 (PlayerPoints) 未安装");
                              return false;
                           }

                           int origPts = (Integer)Main.JavaPlugin.getConfig().getIntegerList("PointsNeed").get(Now - 1);
                           int needPts = _bDiscount < 100 ? (int)Math.ceil(origPts * _bDiscount / 100.0) : origPts;
                           int havePts = Variable.playerPoints.getAPI().look(pxx.getUniqueId());
                           if (havePts < needPts) {
                              String t = Variable.Lang_YML.getString("UpdateNoPoints");
                              if (t.contains("<NeedPoints>")) {
                                 t = t.replace("<NeedPoints>", String.valueOf(needPts));
                              }

                              pxx.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                              pxx.sendMessage(t);
                              pxx.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
                              return false;
                           }

                           Variable.playerPoints.getAPI().take(pxx.getUniqueId(), needPts);
                           MySQL.setLevel(pxx.getWorld().getName().replace(Variable.world_prefix, ""), String.valueOf(Now + 1));
                        }

                        if (Variable.hook_FastAsyncWorldEdit
                           && Main.JavaPlugin.getConfig().getBoolean("FaweSwitch")
                           && Main.JavaPlugin.getConfig().getBoolean("UpdateClearOld")) {
                           FirstBorderShaped.AddShapeBorder(pxx.getWorld());
                        }

                        pxx.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                        String _t = Variable.Lang_YML.getString("UpdateToNewLevel");
                        if (_t.contains("<Level>")) {
                           _t = _t.replace("<Level>", MySQL.getLevel(pxx.getWorld().getName().replace(Variable.world_prefix, "")));
                        }

                        pxx.sendMessage(_t);
                        pxx.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
                        if (Main.JavaPlugin.getConfig().getBoolean("BorderSwitch")) {
                           try {
                              World _w = Bukkit.getWorld(Variable.world_prefix + pxx.getName());
                              _w.getWorldBorder().setCenter(_w.getSpawnLocation());
                              _w.getWorldBorder().setSize(HomeTerrainPolicy.upgradedBorderSize(
                                 (int)Math.round(_w.getWorldBorder().getSize()),
                                 Now,
                                 Now + 1,
                                 Main.JavaPlugin.getConfig().getBoolean("HomeTerrain.Enabled", false),
                                 Main.JavaPlugin.getConfig().getIntegerList("HomeUpgrade.LevelSizes"),
                                 Main.JavaPlugin.getConfig().getInt("WorldBoard"),
                                 Main.JavaPlugin.getConfig().getInt("UpdateRadius")
                              ));
                           } catch (Exception var66) {
                              com.Util.Diag.warnOnce("upgrade-border-1", "Could not resize the world border after an upgrade", var66);
                           }
                        }

                        Util.refreshBorder(pxx.getWorld());
                     } else {
                        YamlConfiguration _yml = YamlConfiguration.loadConfiguration(_f);
                        int Nowx = _yml.getInt("Level");
                        if (Nowx >= Main.JavaPlugin.getConfig().getInt("MaxLevel")) {
                           String t = Variable.Lang_YML.getString("ReachMaxLevel");
                           if (t.contains("<Level>")) {
                              t = t.replace("<Level>", String.valueOf(Nowx));
                           }

                           pxx.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                           pxx.sendMessage(t);
                           pxx.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
                           return false;
                        }

                        int _discount = Util.getUpgradeDiscount(pxx);
                        if (modex.equals("money")) {
                           double origMoney = (Double)Main.JavaPlugin.getConfig().getDoubleList("MoneyNeed").get(Nowx - 1);
                           double needMoney = _discount < 100 ? Math.ceil(origMoney * _discount / 100.0) : origMoney;
                           double haveMoney = Variable.econ.getBalance(pxx);
                           if (haveMoney < needMoney) {
                              String t = Variable.Lang_YML.getString("UpdateNoEnoughMoney");
                              if (t.contains("<NeedMoney>")) {
                                 t = t.replace("<NeedMoney>", String.valueOf((long)needMoney));
                              }

                              pxx.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                              pxx.sendMessage(t);
                              pxx.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
                              return false;
                           }

                           Variable.econ.withdrawPlayer(pxx, needMoney);
                        } else if (modex.equals("points")) {
                           if (!Variable.PlyaerPointsModule) {
                              pxx.sendMessage("§c点券系统 (PlayerPoints) 未安装");
                              return false;
                           }

                           int origPts = (Integer)Main.JavaPlugin.getConfig().getIntegerList("PointsNeed").get(Nowx - 1);
                           int needPts = _discount < 100 ? (int)Math.ceil(origPts * _discount / 100.0) : origPts;
                           int havePts = Variable.playerPoints.getAPI().look(pxx.getUniqueId());
                           if (havePts < needPts) {
                              String t = Variable.Lang_YML.getString("UpdateNoPoints");
                              if (t.contains("<NeedPoints>")) {
                                 t = t.replace("<NeedPoints>", String.valueOf(needPts));
                              }

                              pxx.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                              pxx.sendMessage(t);
                              pxx.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
                              return false;
                           }

                           Variable.playerPoints.getAPI().take(pxx.getUniqueId(), needPts);
                        } else {
                           if (!modex.equals("items")) {
                              return false;
                           }

                           String itemLine = (String)Main.JavaPlugin.getConfig().getStringList("ItemsNeed").get(Nowx - 1);
                           if (!itemLine.equalsIgnoreCase("")) {
                              String[] parts = itemLine.split(",");
                              ItemStack need_i = new ItemStack(Material.valueOf(parts[0]));
                              need_i.setAmount(Integer.parseInt(parts[1]));
                              if (!pxx.getInventory().containsAtLeast(need_i, need_i.getAmount())) {
                                 String lang = Variable.Lang_YML.getString("UpdateNoEnoughItems");
                                 if (lang.contains("<Amount>")) {
                                    lang = lang.replace("<Amount>", parts[1]);
                                 }

                                 if (lang.contains("<Item>")) {
                                    lang = lang.replace("<Item>", parts[0]);
                                 }

                                 pxx.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                                 pxx.sendMessage(lang);
                                 pxx.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
                                 return false;
                              }

                              int amt = need_i.getAmount();

                              for (int e2 = 0; e2 < pxx.getInventory().getSize(); e2++) {
                                 ItemStack slot_i = pxx.getInventory().getItem(e2);
                                 if (slot_i != null && slot_i.getType() == need_i.getType()) {
                                    if (slot_i.getAmount() > amt) {
                                       ItemStack clone = slot_i.clone();
                                       clone.setAmount(slot_i.getAmount() - amt);
                                       pxx.getInventory().setItem(e2, clone);
                                       break;
                                    }

                                    amt -= slot_i.getAmount();
                                    pxx.getInventory().setItem(e2, null);
                                 }
                              }
                           }
                        }

                        _yml.set("Level", Nowx + 1);

                        try {
                           _yml.save(_f);
                        } catch (IOException var65) {
                           var65.printStackTrace();
                        }

                        _yml = YamlConfiguration.loadConfiguration(_f);

                        var __cfg10 = Main.JavaPlugin.getConfig().getStringList("AfterUpdateDispathCommand");
                        for (int c = 0; c < __cfg10.size(); c++) {
                           String _cmd = (String)Main.JavaPlugin.getConfig().getStringList("DispathCommand").get(c);
                           if (_cmd.contains("<Name>")) {
                              _cmd = _cmd.replace("<Name>", pxx.getName());
                           }

                           if (_cmd.contains("[console]")) {
                              Bukkit.dispatchCommand(Bukkit.getConsoleSender(), _cmd.replace("[console]", ""));
                           } else if (_cmd.contains("[player]")) {
                              Bukkit.dispatchCommand(pxx, _cmd.replace("[player]", ""));
                           }
                        }

                        pxx.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                        String _tx = Variable.Lang_YML.getString("UpdateToNewLevel");
                        if (_tx.contains("<Level>")) {
                           _tx = _tx.replace("<Level>", String.valueOf(_yml.getInt("Level")));
                        }

                        pxx.sendMessage(_tx);
                        pxx.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
                        FirstBorderShaped.AddShapeBorder(pxx.getWorld());
                        if (Main.JavaPlugin.getConfig().getBoolean("BorderSwitch")) {
                           try {
                              World _w = Bukkit.getWorld(Variable.world_prefix + pxx.getName());
                              _w.getWorldBorder().setCenter(_w.getSpawnLocation());
                              _w.getWorldBorder().setSize(HomeTerrainPolicy.upgradedBorderSize(
                                 (int)Math.round(_w.getWorldBorder().getSize()),
                                 Nowx,
                                 Nowx + 1,
                                 Main.JavaPlugin.getConfig().getBoolean("HomeTerrain.Enabled", false),
                                 Main.JavaPlugin.getConfig().getIntegerList("HomeUpgrade.LevelSizes"),
                                 Main.JavaPlugin.getConfig().getInt("WorldBoard"),
                                 Main.JavaPlugin.getConfig().getInt("UpdateRadius")
                              ));
                           } catch (Exception var64) {
                              com.Util.Diag.warnOnce("upgrade-border-2", "Could not resize the world border after an upgrade", var64);
                           }
                        }

                        Util.refreshBorder(pxx.getWorld());
                     }

                     return false;
                  }
               } else if (args.length == 1 && args[0].equalsIgnoreCase("update")) {
                  if (!Main.JavaPlugin.getConfig().getBoolean("Permission.CommandUser")
                     && !com.Util.Perm.has(pxx, "ErrorTown.Update")
                     && !com.Util.Perm.has(pxx, "ErrorTown.command.user")) {
                     String tip = Variable.Lang_YML.getString("NoPermissionCheck");
                     if (tip.contains("<Permission>")) {
                        tip = tip.replace("<Permission>", "ErrorTown.Update");
                     }

                     pxx.sendMessage(tip);
                     return false;
                  } else if (!Util.CheckIsHome(pxx.getWorld().getName().replace(Variable.world_prefix, ""))) {
                     String tip = Variable.Lang_YML.getString("NowIsNotHome");
                     pxx.sendMessage(tip);
                     return false;
                  } else {
                     if (Util.CheckOwnerAndManagerAndOP(pxx, Util.getBaseHomeName(pxx.getWorld().getName()))) {
                        File fx = new File(Variable.Tempf, pxx.getWorld().getName().replace(Variable.world_prefix, "") + ".yml");
                        if (Variable.bungee) {
                           Integer Nowxx = Integer.valueOf(MySQL.getLevel(pxx.getWorld().getName().replace(Variable.world_prefix, "")));
                           if (Nowxx >= Main.JavaPlugin.getConfig().getInt("MaxLevel")) {
                              String tempxxxxxx = Variable.Lang_YML.getString("ReachMaxLevel");
                              if (tempxxxxxx.contains("<Level>")) {
                                 tempxxxxxx = tempxxxxxx.replace("<Level>", String.valueOf(Nowxx));
                              }

                              sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                              sender.sendMessage(tempxxxxxx);
                              sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
                           } else {
                              double GetMoney = Variable.econ.getBalance(pxx);
                              if (GetMoney >= (Double)Main.JavaPlugin.getConfig().getDoubleList("MoneyNeed").get(Nowxx - 1)) {
                                 if (Variable.PlyaerPointsModule) {
                                    Integer GetPoints = Variable.playerPoints.getAPI().look(pxx.getUniqueId());
                                    if (GetPoints < (Integer)Main.JavaPlugin.getConfig().getIntegerList("PointsNeed").get(Nowxx - 1)) {
                                       String tempxxxxxx = Variable.Lang_YML.getString("UpdateNoPoints");
                                       if (tempxxxxxx.contains("<NeedPoints>")) {
                                          tempxxxxxx = tempxxxxxx.replace(
                                             "<NeedPoints>", String.valueOf(Main.JavaPlugin.getConfig().getIntegerList("PointsNeed").get(Nowxx - 1))
                                          );
                                       }

                                       sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                                       sender.sendMessage(tempxxxxxx);
                                       sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
                                       return false;
                                    }
                                 }

                                 if (!((String)Main.JavaPlugin.getConfig().getStringList("ItemsNeed").get(Nowxx - 1)).equalsIgnoreCase("")) {
                                    String[] tempxxxxxx = ((String)Main.JavaPlugin.getConfig().getStringList("ItemsNeed").get(Nowxx - 1)).split(",");
                                    ItemStack ixxxxxxxxx = new ItemStack(Material.valueOf(tempxxxxxx[0]));
                                    ixxxxxxxxx.setAmount(Integer.valueOf(tempxxxxxx[1]));
                                    if (!pxx.getInventory().containsAtLeast(ixxxxxxxxx, ixxxxxxxxx.getAmount())) {
                                       String langx = Variable.Lang_YML.getString("UpdateNoEnoughItems");
                                       if (langx.contains("<Amount>")) {
                                          langx = langx.replace("<Amount>", String.valueOf(ixxxxxxxxx.getAmount()));
                                       }

                                       if (langx.contains("<Item>")) {
                                          langx = langx.replace("<Item>", String.valueOf(ixxxxxxxxx.getType().toString()));
                                       }

                                       sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                                       pxx.sendMessage(langx);
                                       sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
                                       return false;
                                    }

                                    int amountx = ixxxxxxxxx.getAmount();

                                    for (int e = 0; e < pxx.getInventory().getSize(); e++) {
                                       if (pxx.getInventory().getItem(e) != null) {
                                          ItemStack i_temp = pxx.getInventory().getItem(e);
                                          if (i_temp.getType() == ixxxxxxxxx.getType()) {
                                             if (i_temp.getAmount() > amountx) {
                                                ItemStack clone = i_temp.clone();
                                                clone.setAmount(i_temp.getAmount() - amountx);
                                                pxx.getInventory().setItem(e, clone);
                                                break;
                                             }

                                             amountx -= i_temp.getAmount();
                                             pxx.getInventory().setItem(e, null);
                                          }
                                       }
                                    }
                                 }

                                 if (Variable.PlyaerPointsModule) {
                                    Variable.playerPoints
                                       .getAPI()
                                       .take(pxx.getUniqueId(), (Integer)Main.JavaPlugin.getConfig().getIntegerList("PointsNeed").get(Nowxx - 1));
                                 }

                                 Variable.econ.withdrawPlayer(pxx, (Double)Main.JavaPlugin.getConfig().getDoubleList("MoneyNeed").get(Nowxx - 1));
                                 MySQL.setLevel(pxx.getWorld().getName().replace(Variable.world_prefix, ""), String.valueOf(Nowxx + 1));
                                 if (Variable.hook_FastAsyncWorldEdit
                                    && Main.JavaPlugin.getConfig().getBoolean("FaweSwitch")
                                    && Main.JavaPlugin.getConfig().getBoolean("UpdateClearOld")) {
                                    FirstBorderShaped.AddShapeBorder(pxx.getWorld());
                                 }

                                 sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                                 String tempxxxxxx = Variable.Lang_YML.getString("UpdateToNewLevel");
                                 if (tempxxxxxx.contains("<Level>")) {
                                    tempxxxxxx = tempxxxxxx.replace(
                                       "<Level>", String.valueOf(MySQL.getLevel(pxx.getWorld().getName().replace(Variable.world_prefix, "")))
                                    );
                                 }

                                 sender.sendMessage(tempxxxxxx);
                                 sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
                                 if (Main.JavaPlugin.getConfig().getBoolean("BorderSwitch")) {
                                    try {
                                       World worldxxx = Bukkit.getWorld(Variable.world_prefix + pxx.getName());
                                       worldxxx.getWorldBorder().setCenter(worldxxx.getSpawnLocation());
                                       worldxxx.getWorldBorder()
                                          .setSize(HomeTerrainPolicy.upgradedBorderSize(
                                             (int)Math.round(worldxxx.getWorldBorder().getSize()),
                                             Nowxx,
                                             Nowxx + 1,
                                             Main.JavaPlugin.getConfig().getBoolean("HomeTerrain.Enabled", false),
                                             Main.JavaPlugin.getConfig().getIntegerList("HomeUpgrade.LevelSizes"),
                                             Main.JavaPlugin.getConfig().getInt("WorldBoard"),
                                             Main.JavaPlugin.getConfig().getInt("UpdateRadius")
                                          ));
                                    } catch (Exception var69) {
                                       Bukkit.getConsoleSender().sendMessage(Variable.Lang_YML.getString("BorderException"));
                                    }
                                 }
                              } else {
                                 String tempxxxxxxx = Variable.Lang_YML.getString("UpdateNoEnoughMoney");
                                 if (tempxxxxxxx.contains("<NeedMoney>")) {
                                    tempxxxxxxx = tempxxxxxxx.replace(
                                       "<NeedMoney>", String.valueOf(Main.JavaPlugin.getConfig().getDoubleList("MoneyNeed").get(Nowxx - 1))
                                    );
                                 }

                                 sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                                 sender.sendMessage(tempxxxxxxx);
                                 sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
                              }
                           }

                           Util.refreshBorder(pxx.getWorld());
                        } else {
                           YamlConfiguration yamlConfigurationx = YamlConfiguration.loadConfiguration(fx);
                           Integer Nowxx = yamlConfigurationx.getInt("Level");
                           if (Nowxx >= Main.JavaPlugin.getConfig().getInt("MaxLevel")) {
                              String tempxxxxxxx = Variable.Lang_YML.getString("ReachMaxLevel");
                              if (tempxxxxxxx.contains("<Level>")) {
                                 tempxxxxxxx = tempxxxxxxx.replace("<Level>", String.valueOf(yamlConfigurationx.getInt("Level")));
                              }

                              sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                              sender.sendMessage(tempxxxxxxx);
                              sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
                           } else {
                              double GetMoney = Variable.econ.getBalance(pxx);
                              if (GetMoney >= (Double)Main.JavaPlugin.getConfig().getDoubleList("MoneyNeed").get(Nowxx - 1)) {
                                 if (Variable.PlyaerPointsModule) {
                                    Integer GetPoints = Variable.playerPoints.getAPI().look(pxx.getUniqueId());
                                    if (GetPoints < (Integer)Main.JavaPlugin.getConfig().getIntegerList("PointsNeed").get(Nowxx - 1)) {
                                       String tempxxxxxxx = Variable.Lang_YML.getString("UpdateNoPoints");
                                       if (tempxxxxxxx.contains("<NeedPoints>")) {
                                          tempxxxxxxx = tempxxxxxxx.replace(
                                             "<NeedPoints>", String.valueOf(Main.JavaPlugin.getConfig().getIntegerList("PointsNeed").get(Nowxx - 1))
                                          );
                                       }

                                       sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                                       sender.sendMessage(tempxxxxxxx);
                                       sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
                                       return false;
                                    }
                                 }

                                 if (!((String)Main.JavaPlugin.getConfig().getStringList("ItemsNeed").get(Nowxx - 1)).equalsIgnoreCase("")) {
                                    String[] tempxxxxxxx = ((String)Main.JavaPlugin.getConfig().getStringList("ItemsNeed").get(Nowxx - 1)).split(",");
                                    ItemStack ixxxxxxxxx = new ItemStack(Material.valueOf(tempxxxxxxx[0]));
                                    ixxxxxxxxx.setAmount(Integer.valueOf(tempxxxxxxx[1]));
                                    if (!pxx.getInventory().containsAtLeast(ixxxxxxxxx, ixxxxxxxxx.getAmount())) {
                                       String langxx = Variable.Lang_YML.getString("UpdateNoEnoughItems");
                                       if (langxx.contains("<Amount>")) {
                                          langxx = langxx.replace("<Amount>", String.valueOf(ixxxxxxxxx.getAmount()));
                                       }

                                       if (langxx.contains("<Item>")) {
                                          langxx = langxx.replace("<Item>", String.valueOf(ixxxxxxxxx.getType().toString()));
                                       }

                                       sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                                       pxx.sendMessage(langxx);
                                       sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
                                       return false;
                                    }

                                    int amountx = ixxxxxxxxx.getAmount();

                                    for (int ex = 0; ex < pxx.getInventory().getSize(); ex++) {
                                       if (pxx.getInventory().getItem(ex) != null) {
                                          ItemStack i_temp = pxx.getInventory().getItem(ex);
                                          if (i_temp.getType() == ixxxxxxxxx.getType()) {
                                             if (i_temp.getAmount() > amountx) {
                                                ItemStack clone = i_temp.clone();
                                                clone.setAmount(i_temp.getAmount() - amountx);
                                                pxx.getInventory().setItem(ex, clone);
                                                break;
                                             }

                                             amountx -= i_temp.getAmount();
                                             pxx.getInventory().setItem(ex, null);
                                          }
                                       }
                                    }
                                 }

                                 if (Variable.PlyaerPointsModule) {
                                    Variable.playerPoints
                                       .getAPI()
                                       .take(pxx.getUniqueId(), (Integer)Main.JavaPlugin.getConfig().getIntegerList("PointsNeed").get(Nowxx - 1));
                                 }

                                 Variable.econ.withdrawPlayer(pxx, (Double)Main.JavaPlugin.getConfig().getDoubleList("MoneyNeed").get(Nowxx - 1));
                                 yamlConfigurationx.set("Level", yamlConfigurationx.getInt("Level") + 1);

                                 try {
                                    yamlConfigurationx.save(fx);
                                 } catch (IOException var68) {
                                    var68.printStackTrace();
                                 }

                                 yamlConfigurationx = YamlConfiguration.loadConfiguration(fx);

                                 var __cfg11 = Main.JavaPlugin.getConfig().getStringList("AfterUpdateDispathCommand");
                                 for (int c = 0; c < __cfg11.size(); c++) {
                                    String temp1 = (String)Main.JavaPlugin.getConfig().getStringList("DispathCommand").get(c);
                                    if (temp1.contains("<Name>")) {
                                       temp1 = temp1.replace("<Name>", pxx.getName());
                                    }

                                    if (temp1.contains("[console]")) {
                                       temp1 = temp1.replace("[console]", "");
                                       Bukkit.dispatchCommand(Bukkit.getConsoleSender(), temp1);
                                    } else if (temp1.contains("[player]")) {
                                       temp1 = temp1.replace("[player]", "");
                                       Bukkit.dispatchCommand(pxx, temp1);
                                    }
                                 }

                                 sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                                 String tempxxxxxxx = Variable.Lang_YML.getString("UpdateToNewLevel");
                                 if (tempxxxxxxx.contains("<Level>")) {
                                    tempxxxxxxx = tempxxxxxxx.replace("<Level>", String.valueOf(yamlConfigurationx.getInt("Level")));
                                 }

                                 sender.sendMessage(tempxxxxxxx);
                                 sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
                                 FirstBorderShaped.AddShapeBorder(pxx.getWorld());
                                 if (Main.JavaPlugin.getConfig().getBoolean("BorderSwitch")) {
                                    try {
                                       World worldxxx = Bukkit.getWorld(Variable.world_prefix + pxx.getName());
                                       worldxxx.getWorldBorder().setCenter(worldxxx.getSpawnLocation());
                                       worldxxx.getWorldBorder()
                                          .setSize(HomeTerrainPolicy.upgradedBorderSize(
                                             (int)Math.round(worldxxx.getWorldBorder().getSize()),
                                             Nowxx,
                                             Nowxx + 1,
                                             Main.JavaPlugin.getConfig().getBoolean("HomeTerrain.Enabled", false),
                                             Main.JavaPlugin.getConfig().getIntegerList("HomeUpgrade.LevelSizes"),
                                             Main.JavaPlugin.getConfig().getInt("WorldBoard"),
                                             Main.JavaPlugin.getConfig().getInt("UpdateRadius")
                                          ));
                                    } catch (Exception var67) {
                                       Bukkit.getConsoleSender().sendMessage(Variable.Lang_YML.getString("BorderException"));
                                    }
                                 }
                              } else {
                                 String tempxxxxxxxx = Variable.Lang_YML.getString("UpdateNoEnoughMoney");
                                 if (tempxxxxxxxx.contains("<NeedMoney>")) {
                                    tempxxxxxxxx = tempxxxxxxxx.replace(
                                       "<NeedMoney>", String.valueOf(Main.JavaPlugin.getConfig().getDoubleList("MoneyNeed").get(Nowxx - 1))
                                    );
                                 }

                                 sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                                 sender.sendMessage(tempxxxxxxxx);
                                 sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
                              }
                           }

                           Util.refreshBorder(pxx.getWorld());
                        }
                     } else {
                        String tempxxxxxxxx = Variable.Lang_YML.getString("NoOwnerAndManagerPermission");
                        sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                        sender.sendMessage(tempxxxxxxxx);
                        sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
                     }

                     return false;
                  }
               } else if (args.length == 4 && args[0].equalsIgnoreCase("createcost") && sender instanceof Player) {
                  String modex = args[1].toLowerCase();
                  String type = args[2];
                  String seedMode = args[3].toLowerCase();
                  if (!Main.JavaPlugin.getConfig().getBoolean("CreateCost.Enable", false)) {
                     Bukkit.dispatchCommand(sender, "sh create " + type);
                     return false;
                  } else if (!this.canStartCreate(pxx, sender)) {
                     return false;
                  } else {
                     String prefix = "CreateCost." + (seedMode.equals("custom") ? "CustomSeed" : "RandomSeed");
                     if (modex.equals("money")) {
                        double cost = Main.JavaPlugin.getConfig().getDouble(prefix + ".Money", 0.0);
                        double have = Variable.econ != null ? Variable.econ.getBalance(pxx) : 0.0;
                        if (have < cost) {
                           String t = Variable.Lang_YML.getString("CreateCostNoEnoughMoney");
                           if (t == null) {
                              t = "§8[§6错误庄园§8] §c金币不够！创建家园需要 <NeedMoney> 金币";
                           }

                           if (t.contains("<NeedMoney>")) {
                              t = t.replace("<NeedMoney>", String.valueOf((long)cost));
                           }

                           pxx.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                           pxx.sendMessage(t);
                           pxx.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
                           return false;
                        }

                          EconomyResponse withdrawal = Variable.econ.withdrawPlayer(pxx, cost);
                          if (withdrawal == null || !withdrawal.transactionSuccess()) {
                             pxx.sendMessage("§8[§6错误庄园§8] §c金币扣费失败，请稍后重试。");
                             return false;
                          }
                         Variable.pendingCreateCostPaid.put(pxx.getName(), Boolean.TRUE);
                         // Record the charge so every creation failure path can refund it.
                         CreateCostLedger.recordMoney(pxx.getName(), cost);
                         HomeAudit.log("create.cost", pxx, pxx.getName(), "mode=money,type=" + type + ",seed=" + seedMode + ",cost=" + (long)cost);
                        Main.JavaPlugin.getLogger().info("[CreateCost] 金币扣费成功, pendingCreateCostPaid 已设置, player=" + pxx.getName() + ", type=" + type);
                        String paid = Variable.Lang_YML.getString("CreateCostPaidSuccess");
                        if (paid == null) {
                           paid = "§8[§6错误庄园§8] §a已扣除 <Cost>，正在创建家园...";
                        }

                        if (paid.contains("<Cost>")) {
                           paid = paid.replace("<Cost>", (long)cost + " 金币");
                        }

                        pxx.sendMessage(paid);
                     } else {
                        if (!modex.equals("points")) {
                           return false;
                        }

                        if (!Variable.PlyaerPointsModule || Variable.playerPoints == null) {
                           pxx.sendMessage("§c点券系统 (PlayerPoints) 未安装");
                           return false;
                        }

                        int costx = Main.JavaPlugin.getConfig().getInt(prefix + ".Points", 0);
                        int havex = Variable.playerPoints.getAPI().look(pxx.getUniqueId());
                        if (havex < costx) {
                           String tx = Variable.Lang_YML.getString("CreateCostNoEnoughPoints");
                           if (tx == null) {
                              tx = "§8[§6错误庄园§8] §c点券不够！创建家园需要 <NeedPoints> 点券";
                           }

                           if (tx.contains("<NeedPoints>")) {
                              tx = tx.replace("<NeedPoints>", String.valueOf(costx));
                           }

                           pxx.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                           pxx.sendMessage(tx);
                           pxx.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
                           return false;
                        }

                          if (!Variable.playerPoints.getAPI().take(pxx.getUniqueId(), costx)) {
                             pxx.sendMessage("§8[§6错误庄园§8] §c点券扣费失败，请稍后重试。");
                             return false;
                          }
                         Variable.pendingCreateCostPaid.put(pxx.getName(), Boolean.TRUE);
                         // Record the charge so every creation failure path can refund it.
                         CreateCostLedger.recordPoints(pxx.getName(), costx);
                         HomeAudit.log("create.cost", pxx, pxx.getName(), "mode=points,type=" + type + ",seed=" + seedMode + ",cost=" + costx);
                        String paidx = Variable.Lang_YML.getString("CreateCostPaidSuccess");
                        if (paidx == null) {
                           paidx = "§8[§6错误庄园§8] §a已扣除 <Cost>，正在创建家园...";
                        }

                        if (paidx.contains("<Cost>")) {
                           paidx = paidx.replace("<Cost>", costx + " 点券");
                        }

                        pxx.sendMessage(paidx);
                     }

                     if (seedMode.equals("custom")) {
                        Variable.wait_chat_input.put(pxx.getName(), "create_seed:" + type);
                        String tip = Variable.Lang_YML.getString("CreateCostInputSeed");
                        if (tip == null) {
                           tip = "§8[§6错误庄园§8] §e请输入你想要的种子(数字或文字)，输入 §c0 §e取消";
                        }

                        pxx.sendMessage(tip);
                     } else {
                        Bukkit.dispatchCommand(sender, "sh create " + type);
                     }

                     return false;
                  }
               } else if (args.length == 1 && args[0].equalsIgnoreCase("look")) {
                  if (!Main.JavaPlugin.getConfig().getBoolean("Permission.CommandUser")
                     && !com.Util.Perm.has(pxx, "ErrorTown.Look")
                     && !com.Util.Perm.has(pxx, "ErrorTown.command.user")) {
                     String tip = Variable.Lang_YML.getString("NoPermissionCheck");
                     if (tip.contains("<Permission>")) {
                        tip = tip.replace("<Permission>", "ErrorTown.Look");
                     }

                     pxx.sendMessage(tip);
                     return false;
                  } else if (!Util.CheckIsHome(pxx.getWorld().getName().replace(Variable.world_prefix, ""))) {
                     String tip = Variable.Lang_YML.getString("NowIsNotHome");
                     pxx.sendMessage(tip);
                     return false;
                  } else {
                     sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));

                     var __cfg12 = Variable.Lang_YML.getStringList("LookInfo");
                     for (int ixxxxxxxxx = 0; ixxxxxxxxx < __cfg12.size(); ixxxxxxxxx++) {
                        String tempxxxxxxxx = __cfg12.get(ixxxxxxxxx);
                        tempxxxxxxxx = PlaceholderAPI.setPlaceholders(pxx, tempxxxxxxxx);
                        sender.sendMessage(tempxxxxxxxx);
                     }

                     sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
                     return false;
                  }
               } else if (args.length == 1 && args[0].equalsIgnoreCase("calc")) {
                  if (!com.Util.Perm.has(pxx, "ErrorTown.Calc")) {
                     String tip = Variable.Lang_YML.getString("NoPermissionCheck");
                     if (tip.contains("<Permission>")) {
                        tip = tip.replace("<Permission>", "ErrorTown.Calc");
                     }

                     pxx.sendMessage(tip);
                     return false;
                  } else if (Variable.calc_cooldown.contains(pxx.getName())) {
                     pxx.sendMessage("Cooldown ing... waif for one minute!!!");
                     return false;
                  } else {
                     Variable.calc_cooldown.add(pxx.getName());
                     (new BukkitRunnable() {
                        public void run() {
                            if (Variable.calc_cooldown.contains(pxx.getName())) {
                               Variable.calc_cooldown.remove(pxx.getName());
                           }
                        }
                     }).runTaskLater(Main.JavaPlugin, 1200L);
                     if (!Util.CheckIsHome(pxx.getWorld().getName().replace(Variable.world_prefix, ""))) {
                        String tip = Variable.Lang_YML.getString("NowIsNotHome");
                        pxx.sendMessage(tip);
                        return false;
                     } else {
                        World worldxxx = pxx.getWorld();
                        TreeMap<String, Integer> sorted_map = new TreeMap<>(Collections.reverseOrder());

                        for (Chunk chunk : worldxxx.getLoadedChunks()) {
                           for (BlockState tile : chunk.getTileEntities()) {
                              String namex = tile.getBlock().getType().toString();
                              if (sorted_map.containsKey(namex)) {
                                 sorted_map.put(namex, sorted_map.get(namex) + 1);
                              } else {
                                 sorted_map.put(namex, 1);
                              }
                           }
                        }

                        String tempxxxxxxxx = "";

                        for (String keyx : sorted_map.keySet()) {
                           tempxxxxxxxx = tempxxxxxxxx + keyx + ":" + sorted_map.get(keyx) + " , ";
                        }

                        sender.sendMessage(tempxxxxxxxx);
                        return false;
                     }
                  }
               } else if (args.length == 1 && args[0].equalsIgnoreCase("nbt")) {
                  if (com.Util.Perm.has(pxx, "ErrorTown.Admin")) {
                     if (Variable.Debug.contains(pxx.getName())) {
                        Variable.Debug.remove(pxx.getName());
                        sender.sendMessage(Variable.Lang_YML.getString("DisableNBTDebug"));
                     } else {
                        Variable.Debug.add(pxx.getName());
                        sender.sendMessage(Variable.Lang_YML.getString("EnableNBTDebug"));
                     }

                     return false;
                  } else {
                     sender.sendMessage(Variable.Lang_YML.getString("PlayerIsNotOperator"));
                     return false;
                  }
               } else if (args.length == 1 && args[0].equalsIgnoreCase("item")) {
                  if (!(sender instanceof Player tempxxxxxxxx)) {
                     sender.sendMessage(Variable.Lang_YML.getString("CommandSenderIsNotAllowToUseTheCommand"));
                     return false;
                  } else if (!com.Util.Perm.has(tempxxxxxxxx, "ErrorTown.Admin")) {
                     sender.sendMessage(Variable.Lang_YML.getString("PlayerIsNotOperator"));
                     return false;
                  } else {
                     Player pl = (Player)sender;
                     if (pl.getItemInHand() == null) {
                        pl.sendMessage("§enull");
                     } else if (pl.getItemInHand().getType() == Material.AIR) {
                        pl.sendMessage("§eAIR");
                     } else {
                        String itemNbt = Util.getItemNBTString(pl.getItemInHand());
                        com.Util.ClickableText.suggest(pl, "§e" + itemNbt + " §b>> §dCopy", itemNbt);
                     }

                     return false;
                  }
               } else if (args.length == 1 && args[0].equalsIgnoreCase("item")) {
                  if (com.Util.Perm.has(pxx, "ErrorTown.Admin")) {
                     ItemStack ixxxxxxxxx = pxx.getItemInHand();
                     pxx.sendMessage("§e§l§m--------------§7[§eDeBug§7]§e§l§m--------------");
                     com.Util.ClickableText.suggest(
                        pxx,
                        "§eMaterial:§d" + ixxxxxxxxx.getType().toString() + "§e,SubID:§d" + ixxxxxxxxx.getDurability() + " §b>> §dCopy",
                        "Material:" + ixxxxxxxxx.getType().toString() + ",SubID:" + ixxxxxxxxx.getDurability()
                     );

                     pxx.sendMessage("§e§l§m--------------§7[§eDebug§7]§e§l§m--------------");
                  } else {
                     pxx.sendMessage(Variable.Lang_YML.getString("PlayerIsNotOperator"));
                  }

                  return false;
               } else if (args.length == 1 && args[0].equalsIgnoreCase("wholeDelete")) {
                  if (!Main.JavaPlugin.getConfig().getBoolean("Permission.CommandUser")
                     && !com.Util.Perm.has(pxx, "ErrorTown.WholeDelete")
                     && !com.Util.Perm.has(pxx, "ErrorTown.command.user")) {
                     String tip = Variable.Lang_YML.getString("NoPermissionCheck");
                     if (tip.contains("<Permission>")) {
                        tip = tip.replace("<Permission>", "ErrorTown.WholeDelete");
                     }

                     pxx.sendMessage(tip);
                     return false;
                  } else if (!Util.CheckIsHome(pxx.getLocation().getWorld().getName().replace(Variable.world_prefix, ""))) {
                     String tempxxxxxxxx = Variable.Lang_YML.getString("NowIsNotHome");
                     sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                     sender.sendMessage(tempxxxxxxxx);
                     sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
                     return false;
                  } else {
                     final String currentHomeName = Util.getBaseHomeName(pxx.getWorld().getName());
                     if (!Util.getHomeOwner(currentHomeName).equalsIgnoreCase(pxx.getName())) {
                        String tempxxxxxxxx = Variable.Lang_YML.getString("DeleteNotIsMyHome");
                        sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                        sender.sendMessage(tempxxxxxxxx);
                        sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
                        return false;
                     } else {
                        boolean real_delete = false;

                        for (int d = 0; d < Variable.waitDeleteconfirm.size(); d++) {
                           if (Variable.waitDeleteconfirm.get(d).equalsIgnoreCase(pxx.getName())) {
                              real_delete = true;
                           }
                        }

                        if (!real_delete && Main.JavaPlugin.getConfig().getBoolean("EnableConfirmDelete")) {
                           com.Util.ClickableText.run(pxx, Variable.Lang_YML.getString("ConfirmDelete"), "/sh wholedelete");

                           Variable.waitDeleteconfirm.add(pxx.getName());
                           (new BukkitRunnable() {
                              public void run() {
                                 for (int i = 0; i < Variable.waitDeleteconfirm.size(); i++) {
                                     if (Variable.waitDeleteconfirm.get(i).equalsIgnoreCase(pxx.getName())) {
                                        Variable.waitDeleteconfirm.remove(pxx.getName());
                                    }
                                 }
                              }
                           }).runTaskLater(Main.JavaPlugin, 100L);
                           return false;
                        } else {
                           Variable.waitDeleteconfirm.remove(pxx);
                           YamlConfiguration yamlConfigurationx = YamlConfiguration.loadConfiguration(Variable.f_log);
                           List<String> list = yamlConfigurationx.getStringList("DeleteTimes");
                           if (list == null) {
                              list = new ArrayList<>();
                              yamlConfigurationx.set("DeleteTimes", list);

                              try {
                                 yamlConfigurationx.save(Variable.f_log);
                              } catch (IOException var74) {
                                 var74.printStackTrace();
                              }
                           }

                           boolean check_contain = false;

                           for (int c = 0; c < list.size(); c++) {
                              String[] temp3 = list.get(c).split(",");
                              String namex = temp3[0];
                              if (namex.equalsIgnoreCase(pxx.getName())) {
                                 check_contain = true;
                                 int cs = Integer.valueOf(temp3[1]);
                                 if (cs >= Main.JavaPlugin.getConfig().getInt("MaxDelete")) {
                                    String temp5 = Variable.Lang_YML.getString("MaxDeleteLanguage");
                                    sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                                    sender.sendMessage(temp5);
                                    sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
                                    return false;
                                 }

                                 boolean cooldown_check = false;

                                 for (int ixxxxxxxxx = 0; ixxxxxxxxx < Variable.Deletecooldown.size(); ixxxxxxxxx++) {
                                    if (Variable.Deletecooldown.get(ixxxxxxxxx).equalsIgnoreCase(pxx.getName())) {
                                       cooldown_check = true;
                                    }
                                 }

                                 if (cooldown_check) {
                                    pxx.sendMessage(Variable.Lang_YML.getString("IsDeleteCooldown"));
                                    return false;
                                 }

                                 Variable.Deletecooldown.add(pxx.getName());
                                 (new BukkitRunnable() {
                                    public void run() {
                                        if (Variable.Deletecooldown.contains(pxx.getName())) {
                                           Variable.Deletecooldown.remove(pxx.getName());
                                          String temp = Variable.Lang_YML.getString("DeleteCooldownEnd");
                                          sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                                          sender.sendMessage(temp);
                                          sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
                                       }
                                    }
                                 }).runTaskLater(Main.JavaPlugin, 1200L);
                                 list.set(c, namex + "," + (cs + 1));
                                 yamlConfigurationx.set("DeleteTimes", list);

                                 try {
                                    yamlConfigurationx.save(Variable.f_log);
                                 } catch (IOException var73) {
                                    var73.printStackTrace();
                                 }
                                 break;
                              }
                           }

                           if (!check_contain) {
                              list.add(pxx.getName() + ",1");
                              yamlConfigurationx.set("DeleteTimes", list);

                              try {
                                 yamlConfigurationx.save(Variable.f_log);
                              } catch (IOException var72) {
                                 var72.printStackTrace();
                              }
                           }

                           yamlConfigurationx = YamlConfiguration.loadConfiguration(Variable.f_log);
                           if (!yamlConfigurationx.contains("NowID")) {
                              yamlConfigurationx.set("NowID", 0);
                           }

                           if (!yamlConfigurationx.contains("MaxID")) {
                              yamlConfigurationx.set("MaxID", 1000);
                           }

                           try {
                              yamlConfigurationx.save(Variable.f_log);
                           } catch (IOException var71) {
                              var71.printStackTrace();
                           }

                           yamlConfigurationx.set("NowID", yamlConfigurationx.getInt("NowID") - 1);

                           try {
                              yamlConfigurationx.save(Variable.f_log);
                           } catch (IOException var70) {
                              var70.printStackTrace();
                           }

                           String tempxxxxxxxx = Variable.Lang_YML.getString("WholeDeleteSuccess");
                           sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                           sender.sendMessage(tempxxxxxxxx);
                           sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
                           Variable.Deletecooldown.remove(pxx);
                           if (Variable.bungee) {
                              MySQL.removePlayer(currentHomeName);
                           } else {
                              File f2xxx = new File(Variable.Tempf, currentHomeName + ".yml");
                              f2xxx.delete();
                           }

                           World worldxxx = Bukkit.getWorld(Variable.world_prefix + currentHomeName);
                           if (worldxxx != null) {
                              for (Player p6 : Bukkit.getWorld(Variable.world_prefix + currentHomeName).getPlayers()) {
                                 p6.teleport(Bukkit.getWorld(Main.JavaPlugin.getConfig().getString("Spawn")).getSpawnLocation());
                                 p6.sendMessage(Variable.Lang_YML.getString("WorldHasBeenDeleted"));
                              }
                           }

                           Bukkit.unloadWorld(Variable.world_prefix + currentHomeName, true);
                           if (Main.JavaPlugin.getConfig().getBoolean("EnableHomeNether")) {
                              String nether_suffix = Main.JavaPlugin.getConfig().getString("HomeNetherSuffix");
                              if (nether_suffix == null || nether_suffix.isEmpty()) {
                                 nether_suffix = "_nether";
                              }

                              String nether_name = Variable.world_prefix + currentHomeName + nether_suffix;
                              World nether_world = Bukkit.getWorld(nether_name);
                              if (nether_world != null) {
                                 World spawn_world = Bukkit.getWorld(Main.JavaPlugin.getConfig().getString("Spawn"));

                                 for (Player np : nether_world.getPlayers()) {
                                    if (spawn_world != null) {
                                       np.teleport(spawn_world.getSpawnLocation());
                                    }

                                    np.sendMessage(Variable.Lang_YML.getString("WorldHasBeenDeleted"));
                                 }

                                 final File nether_folder = nether_world.getWorldFolder();
                                 Bukkit.unloadWorld(nether_name, false);
                                 (new BukkitRunnable() {
                                    public void run() {
                                       Util.deleteFile(nether_folder);
                                    }
                                 }).runTaskLater(Main.JavaPlugin, 10L);
                              }
                           }

                           var __cfg13 = Main.JavaPlugin.getConfig().getStringList("AfterDeleteDispathCommand");
                           for (int cx = 0; cx < __cfg13.size(); cx++) {
                              String temp1x = (String)Main.JavaPlugin.getConfig().getStringList("DispathCommand").get(cx);
                              if (temp1x.contains("<Name>")) {
                                 temp1x = temp1x.replace("<Name>", pxx.getName());
                              }

                              if (temp1x.contains("[console]")) {
                                 temp1x = temp1x.replace("[console]", "");
                                 Bukkit.dispatchCommand(Bukkit.getConsoleSender(), temp1x);
                              } else if (temp1x.contains("[player]")) {
                                 temp1x = temp1x.replace("[player]", "");
                                 Bukkit.dispatchCommand(pxx, temp1x);
                              }
                           }

                           if (Variable.hook_multiverseCore) {
                              MultiverseCore mvcorex = MultiverseCompat.plugin();
                              MVWorldManager mv_mx = mvcorex.getMVWorldManager();
                              mv_mx.removeWorldFromConfig(Variable.world_prefix + currentHomeName);
                           }

                           (new BukkitRunnable() {
                                 public void run() {
                                    if (Variable.world_prefix.equalsIgnoreCase("")) {
                                       if (!Bukkit.getVersion().toString().toUpperCase().contains("ARCLIGHT")
                                          && !Bukkit.getVersion().toString().contains("1.20.1")) {
                                          File f = new File(Variable.single_server_gen + "world" + Variable.file_loc_prefix + currentHomeName);
                                          Util.deleteFile(f);
                                       } else {
                                          File f = new File(Variable.single_server_gen + Variable.world_prefix + currentHomeName);
                                          Util.deleteFile(f);
                                       }
                                    } else {
                                       File f = new File(Variable.single_server_gen + Variable.world_prefix + currentHomeName);
                                       Util.deleteFile(f);
                                    }
                                 }
                              })
                              .runTaskLater(Main.JavaPlugin, 5L);
                           return false;
                        }
                     }
                  }
               } else if (args.length == 3 && args[0].equalsIgnoreCase("gift") && args[1].equalsIgnoreCase("send") && args[2].equalsIgnoreCase("all")) {
                  if (sender instanceof Player tempxxxxxxxxx && !com.Util.Perm.has(tempxxxxxxxxx, "ErrorTown.Admin")) {
                     sender.sendMessage(Variable.Lang_YML.getString("PlayerIsNotOperator"));
                     return false;
                  } else {
                     ItemStack cx = pxx.getItemInHand().clone();
                     if (cx == null) {
                        pxx.sendMessage(Variable.Lang_YML.getString("SendButTheHandIsAir"));
                        return false;
                     } else if (cx.getType() == Material.AIR) {
                        pxx.sendMessage(Variable.Lang_YML.getString("SendButTheHandIsAir"));
                        return false;
                     } else {
                        List<String> has_send_list = new ArrayList<>();
                        List<String> has_not_send_list = new ArrayList<>();

                        for (Home homex : HomeAPI.getHomes()) {
                           ItemStack ixxxxxxxxxx = cx.clone();
                           String home_name = homex.getName();
                           if (Variable.has_open_gifts_list.containsKey(home_name)) {
                              Player has_open = Bukkit.getPlayer(Variable.has_open_gifts_list.get(home_name));
                              if (has_open != null) {
                                 has_open.sendMessage(Variable.Lang_YML.getString("OperatorSendGiftButOpen"));
                                 has_open.closeInventory();
                              }
                           }

                           List<String> gifts = new ArrayList<>(homex.getGifts());
                           if (gifts.size() >= 45) {
                              has_not_send_list.add(homex.getName());
                           } else {
                              has_send_list.add(homex.getName());
                              StreamSerializer ss = new StreamSerializer();
                              if (!Variable.Lang_YML.getString("GiftLoreAddPrefix").equalsIgnoreCase("")) {
                                 String lore = Variable.Lang_YML.getString("GiftLoreAddPrefix") + pxx.getName();
                                 if (ixxxxxxxxxx.hasItemMeta()) {
                                    ItemMeta meta = ixxxxxxxxxx.getItemMeta();
                                    if (meta.hasLore()) {
                                       List<String> lores = meta.getLore();
                                       lores.add(lore);
                                       meta.setLore(lores);
                                       ixxxxxxxxxx.setItemMeta(meta);
                                    } else {
                                       List<String> lores = new ArrayList<>();
                                       lores.add(lore);
                                       meta.setLore(lores);
                                       ixxxxxxxxxx.setItemMeta(meta);
                                    }
                                 } else {
                                    ItemMeta meta = ixxxxxxxxxx.getItemMeta();
                                    List<String> lores = new ArrayList<>();
                                    lores.add(lore);
                                    meta.setLore(lores);
                                    ixxxxxxxxxx.setItemMeta(meta);
                                 }
                              }

                              try {
                                 gifts.add(ss.serializeItemStack(ixxxxxxxxxx));
                              } catch (Exception var76) {
                                 var76.printStackTrace();
                              }

                              try {
                                 homex.setGifts(gifts);
                              } catch (IOException var75) {
                                 var75.printStackTrace();
                              }

                              String tempxxxxxxxxx = Variable.Lang_YML.getString("GiftAdd");
                              if (tempxxxxxxxxx.contains("<Name>")) {
                                 tempxxxxxxxxx = tempxxxxxxxxx.replace("<Name>", pxx.getName());
                              }

                              if (Bukkit.getPlayer(homex.getName()) != null) {
                                 com.Util.ClickableText.run(Bukkit.getPlayer(homex.getName()), tempxxxxxxxxx, "/sh gift open");
                              }
                           }
                        }

                        String temp2 = Variable.Lang_YML.getString("SuccessedSendToAll");
                        if (temp2.contains("<List>")) {
                           temp2 = temp2.replace("<List>", has_send_list.toString());
                        }

                        pxx.sendMessage(temp2);
                        if (has_not_send_list.size() != 0) {
                           String temp3 = Variable.Lang_YML.getString("FailedSendToAll");
                           if (temp3.contains("<List>")) {
                              temp3 = temp3.replace("<List>", has_not_send_list.toString());
                           }

                           pxx.sendMessage(temp3);
                        }

                        return false;
                     }
                  }
               } else if (args.length == 3 && args[0].equalsIgnoreCase("gift") && args[1].equalsIgnoreCase("send")) {
                  if (!com.Util.Perm.has(pxx, "ErrorTown.Gift.Send")) {
                     pxx.sendMessage(Variable.Lang_YML.getString("NoPermissionSendTheItemToGift"));
                     return false;
                  } else {
                     String home_namex = args[2];
                     if (!Util.CheckIsHome(home_namex)) {
                        String tempxxxxxxxxxx = Variable.Lang_YML.getString("NowIsNotHome");
                        sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                        sender.sendMessage(tempxxxxxxxxxx);
                        sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
                        return false;
                     } else if (pxx.getName().equalsIgnoreCase(home_namex)) {
                        String tempxxxxxxxxxx = Variable.Lang_YML.getString("SendButTheMyHome");
                        sender.sendMessage(tempxxxxxxxxxx);
                        return false;
                     } else {
                        ItemStack ixxxxxxxxxxx = pxx.getItemInHand();
                        if (ixxxxxxxxxxx == null) {
                           pxx.sendMessage(Variable.Lang_YML.getString("SendButTheHandIsAir"));
                           return false;
                        } else if (ixxxxxxxxxxx.getType() == Material.AIR) {
                           pxx.sendMessage(Variable.Lang_YML.getString("SendButTheHandIsAir"));
                           return false;
                        } else if (Variable.has_open_gifts_list.containsKey(home_namex)) {
                           pxx.sendMessage(Variable.Lang_YML.getString("SendButTheInvHasBeenOpen"));
                           return false;
                        } else {
                           Home homex = HomeAPI.getHome(home_namex);
                           List<String> gifts = new ArrayList<>(homex.getGifts());
                           if (gifts.size() >= 45) {
                              pxx.sendMessage(Variable.Lang_YML.getString("GiftFail"));
                              return false;
                           } else {
                              StreamSerializer ssx = new StreamSerializer();
                              if (!Variable.Lang_YML.getString("GiftLoreAddPrefix").equalsIgnoreCase("")) {
                                 String lore = Variable.Lang_YML.getString("GiftLoreAddPrefix") + pxx.getName();
                                 if (ixxxxxxxxxxx.hasItemMeta()) {
                                    ItemMeta meta = ixxxxxxxxxxx.getItemMeta();
                                    if (meta.hasLore()) {
                                       List<String> lores = meta.getLore();
                                       lores.add(lore);
                                       meta.setLore(lores);
                                       ixxxxxxxxxxx.setItemMeta(meta);
                                    } else {
                                       List<String> lores = new ArrayList<>();
                                       lores.add(lore);
                                       meta.setLore(lores);
                                       ixxxxxxxxxxx.setItemMeta(meta);
                                    }
                                 } else {
                                    ItemMeta meta = ixxxxxxxxxxx.getItemMeta();
                                    List<String> lores = new ArrayList<>();
                                    lores.add(lore);
                                    meta.setLore(lores);
                                    ixxxxxxxxxxx.setItemMeta(meta);
                                 }
                              }

                              try {
                                 gifts.add(ssx.serializeItemStack(ixxxxxxxxxxx));
                                 pxx.getInventory().remove(ixxxxxxxxxxx);
                              } catch (Exception var78) {
                                 var78.printStackTrace();
                              }

                              try {
                                 homex.setGifts(gifts);
                              } catch (IOException var77) {
                                 var77.printStackTrace();
                              }

                              String temp2x = Variable.Lang_YML.getString("GiftSuccess");
                              if (temp2x.contains("<Name>")) {
                                 temp2x = temp2x.replace("<Name>", home_namex);
                              }

                              pxx.sendMessage(temp2x);
                              String tempxxxxxxxxxx = Variable.Lang_YML.getString("GiftAdd");
                              if (tempxxxxxxxxxx.contains("<Name>")) {
                                 tempxxxxxxxxxx = tempxxxxxxxxxx.replace("<Name>", pxx.getName());
                              }

                               if (Bukkit.getPlayer(homex.getName()) != null
                                  && Bukkit.getPlayer(homex.getName()).isOnline()
                                  && !homex.getName().equalsIgnoreCase(pxx.getName())) {
                                  com.Util.ClickableText.run(Bukkit.getPlayer(homex.getName()), tempxxxxxxxxxx, "/sh gift open");
                               }

                              return false;
                           }
                        }
                     }
                  }
               } else if (args.length == 4 && args[0].equalsIgnoreCase("popularity") && args[1].equalsIgnoreCase("add")) {
                  if (sender instanceof Player tempxxxxxxxxxxx && !com.Util.Perm.has(tempxxxxxxxxxxx, "ErrorTown.Admin")) {
                     sender.sendMessage(Variable.Lang_YML.getString("PlayerIsNotOperator"));
                     return false;
                  } else {
                     Home homex = HomeAPI.getHome(args[2]);
                     if (homex == null) {
                        sender.sendMessage(Variable.Lang_YML.getString("PopularityAddButHomeIsNotExist"));
                        return false;
                     } else {
                        try {
                           homex.setPopularity(homex.getPopularity() + Integer.valueOf(args[3]));
                        } catch (NumberFormatException var79) {
                           var79.printStackTrace();
                        } catch (IOException var80) {
                           var80.printStackTrace();
                        }

                        String temp2xx = Variable.Lang_YML.getString("PopularityAddSuccess");
                        if (temp2xx.contains("<Now>")) {
                           temp2xx = temp2xx.replace("<Now>", String.valueOf(homex.getPopularity()));
                        }

                        pxx.sendMessage(temp2xx);
                        return false;
                     }
                  }
               } else if (args.length == 4 && args[0].equalsIgnoreCase("flower") && args[1].equalsIgnoreCase("add")) {
                  if (sender instanceof Player tempxxxxxxxxxxx && !com.Util.Perm.has(tempxxxxxxxxxxx, "ErrorTown.Admin")) {
                     sender.sendMessage(Variable.Lang_YML.getString("PlayerIsNotOperator"));
                     return false;
                  } else {
                     Home homex = HomeAPI.getHome(args[2]);
                     if (homex == null) {
                        sender.sendMessage(Variable.Lang_YML.getString("FlowerAddButHomeIsNotExist"));
                        return false;
                     } else {
                        try {
                           homex.setFlowers(homex.getFlowers() + Integer.valueOf(args[3]));
                        } catch (NumberFormatException var81) {
                           var81.printStackTrace();
                        } catch (IOException var82) {
                           var82.printStackTrace();
                        }

                        String temp2xx = Variable.Lang_YML.getString("FlowerAddSuccess");
                        if (temp2xx.contains("<Now>")) {
                           temp2xx = temp2xx.replace("<Now>", String.valueOf(homex.getFlowers()));
                        }

                        pxx.sendMessage(temp2xx);
                        return false;
                     }
                  }
               } else if (args.length >= 3 && args[0].equalsIgnoreCase("flower") && args[1].equalsIgnoreCase("to") && sender instanceof Player) {
                  String targetNamex = args[2];
                  int amountx = 1;
                  if (args.length >= 4) {
                     try {
                        amountx = Math.max(1, Math.min(64, Integer.parseInt(args[3])));
                     } catch (NumberFormatException var84) {
                        // Not a number: fall back to the default amount of 1.
                     }
                  }

                  Home targetHome = HomeAPI.getHome(targetNamex);
                  if (targetHome == null) {
                     pxx.sendMessage(Variable.Lang_YML.getString("FlowerAddButHomeIsNotExist"));
                     return false;
                  } else {
                     boolean isSelf = targetNamex.equalsIgnoreCase(pxx.getName());
                     if (!isSelf) {
                        try {
                           for (String op : targetHome.getOPs()) {
                              if (op.equalsIgnoreCase(pxx.getName())) {
                                 isSelf = true;
                                 break;
                              }
                           }
                        } catch (Exception var141) {
                           com.Util.Diag.warnOnce("self-op-check", "Could not read the OP list while checking self-management", var141);
                        }
                     }

                     if (isSelf) {
                        pxx.sendMessage(Variable.Lang_YML.getString("FlowersMySelf"));
                        return false;
                     } else {
                        int set_flower = Main.JavaPlugin.getConfig().getInt("MaxFlowers");

                        for (int flowerI = Main.JavaPlugin.getConfig().getInt("MaxFlowers") + 100; flowerI > 0; flowerI--) {
                           if (com.Util.Perm.has(pxx, "ErrorTown.Flowers." + flowerI)) {
                              set_flower = flowerI;
                              break;
                           }
                        }

                        int has_given = Variable.flowers_list.containsKey(pxx.getName()) ? Variable.flowers_list.get(pxx.getName()) : 0;
                        int remaining = set_flower - has_given;
                        if (remaining <= 0) {
                           String tempMsg = Variable.Lang_YML.getString("FlowersMax");
                           if (tempMsg.contains("<Max>")) {
                              tempMsg = tempMsg.replace("<Max>", String.valueOf(set_flower));
                           }

                           pxx.sendMessage(tempMsg);
                           return false;
                        } else {
                           int toSend = Math.min(amountx, remaining);
                           Variable.flowers_list.put(pxx.getName(), has_given + toSend);

                           try {
                              targetHome.setFlowers(targetHome.getFlowers() + toSend);
                           } catch (IOException var83) {
                              var83.printStackTrace();
                           }

                           pxx.sendMessage(
                              "§8[§6错误庄园§8] §7您为 §e" + targetNamex + " §7的家园送上了 §e" + toSend + " §7束鲜花，今日已送 §a" + (has_given + toSend) + "§7/§c" + set_flower
                           );
                           String notifyMsg = Variable.Lang_YML.getString("FlowersAddToOwnerAndOP");
                           if (notifyMsg != null && notifyMsg.contains("<Player>")) {
                              notifyMsg = notifyMsg.replace("<Player>", pxx.getName());
                           }

                           if (notifyMsg != null && Bukkit.getPlayer(targetHome.getName()) != null) {
                              Bukkit.getPlayer(targetHome.getName()).sendMessage(notifyMsg);
                           }

                           return false;
                        }
                     }
                  }
               } else if (args.length == 3 && args[0].equalsIgnoreCase("gift") && args[1].equalsIgnoreCase("inv")) {
                  if (!com.Util.Perm.has(pxx, "ErrorTown.Gift.Inv")) {
                     pxx.sendMessage(Variable.Lang_YML.getString("InvPlayersGiftGuiButNoPermission"));
                     return false;
                  } else {
                     String home_namex = args[2];
                     if (!Util.CheckIsHome(home_namex)) {
                        String tempxxxxxxxxxxx = Variable.Lang_YML.getString("NowIsNotHome");
                        sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                        sender.sendMessage(tempxxxxxxxxxxx);
                        sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
                        return false;
                     } else if (Variable.has_open_gifts_list.containsKey(home_namex)) {
                        String str = Variable.Lang_YML.getString("HasAlreadyOpenByOthers");
                        if (str.contains("<Name>")) {
                           str = str.replace("<Name>", Variable.has_open_gifts_list.get(home_namex));
                        }

                        pxx.sendMessage(str);
                        return false;
                     } else {
                        Variable.has_open_gifts_list.put(home_namex, pxx.getName());
                        GiftGui giftgui = new GiftGui(pxx, home_namex);
                        pxx.openInventory(giftgui.getInventory());
                        return false;
                     }
                  }
               } else if (args.length == 1 && args[0].equalsIgnoreCase("Icon")) {
                  if (!com.Util.Perm.has(pxx, "ErrorTown.Icon")) {
                     pxx.sendMessage(Variable.Lang_YML.getString("NoPermissionSetIcon"));
                     return false;
                  } else if (!Util.CheckIsHome(pxx.getWorld().getName().replace(Variable.world_prefix, ""))) {
                     String tempxxxxxxxxxxx = Variable.Lang_YML.getString("NowIsNotHome");
                     sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                     sender.sendMessage(tempxxxxxxxxxxx);
                     sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
                     return false;
                  } else if (!Util.Check(pxx, pxx.getWorld().getName().replace(Variable.world_prefix, ""))) {
                     pxx.sendMessage(Variable.Lang_YML.getString("NoPermissionSetOthersIcon"));
                     return false;
                  } else {
                     ItemStack ixxxxxxxxxxx = pxx.getItemInHand();
                     if (ixxxxxxxxxxx == null) {
                        pxx.sendMessage(Variable.Lang_YML.getString("SetIconButHandIsAir"));
                        return false;
                     } else if (ixxxxxxxxxxx.getType() == Material.AIR) {
                        pxx.sendMessage(Variable.Lang_YML.getString("SetIconButHandIsAir"));
                        return false;
                     } else {
                        Home homex = HomeAPI.getHome(pxx.getWorld().getName().replace(Variable.world_prefix, ""));

                        try {
                           homex.setIcon(ixxxxxxxxxxx.getType().toString() + ":" + ixxxxxxxxxxx.getDurability());
                        } catch (IOException var85) {
                           var85.printStackTrace();
                        }

                        if (ixxxxxxxxxxx.getAmount() == 1) {
                           pxx.setItemInHand(null);
                        } else {
                           ixxxxxxxxxxx.setAmount(ixxxxxxxxxxx.getAmount() - 1);
                           pxx.setItemInHand(ixxxxxxxxxxx);
                        }

                        pxx.sendMessage(Variable.Lang_YML.getString("SetIconSuccess"));
                        return false;
                     }
                  }
               } else if (args.length == 5 && args[0].equalsIgnoreCase("AddBlockLimit")) {
                  if (sender instanceof Player te && !te.isOp()) {
                     te.sendMessage(Variable.Lang_YML.getString("NoPermissionSetCustomBlockLimit"));
                     return false;
                  } else {
                     Home homex = HomeAPI.getHome(args[1]);
                     if (homex == null) {
                        sender.sendMessage(Variable.Lang_YML.getString("SetCustomBlockButHomeIsNull"));
                        return false;
                     } else {
                        String str = args[2] + "|" + args[3].toUpperCase() + "|";
                        List<String> listx = homex.getLimitBlock();
                        List<String> list2 = new ArrayList<>();

                        for (String exx : listx) {
                           list2.add(exx);
                        }

                        boolean success = false;

                        for (int cx = 0; cx < list2.size(); cx++) {
                           String tempxxxxxxxxxxx = list2.get(cx);
                           if (tempxxxxxxxxxxx.contains(str)) {
                              success = true;
                              String[] args2 = tempxxxxxxxxxxx.split("\\|");
                              int amountxx = Integer.valueOf(args2[2]);
                              int nowxx = amountxx + Integer.valueOf(args[4]);
                              list2.set(cx, str + nowxx);

                              try {
                                 homex.setLimitBlock(list2);
                              } catch (IOException var87) {
                                 var87.printStackTrace();
                              }

                              String send_message = Variable.Lang_YML.getString("AddCustomBlockSuccess");
                              if (send_message.contains("<Name>")) {
                                 send_message = send_message.replace("<Name>", args[1]);
                              }

                              if (send_message.contains("<NBT>")) {
                                 send_message = send_message.replace("<NBT>", args[3].toUpperCase());
                              }

                              if (send_message.contains("<Amount>")) {
                                 send_message = send_message.replace("<Amount>", String.valueOf(amountxx));
                              }

                              if (send_message.contains("<Type>")) {
                                 send_message = send_message.replace("<Type>", args[2]);
                              }

                              if (send_message.contains("<NowAmount>")) {
                                 send_message = send_message.replace("<NowAmount>", String.valueOf(nowxx));
                              }

                              sender.sendMessage(send_message);
                              break;
                           }
                        }

                        if (!success) {
                           list2.add(str + args[4]);

                           try {
                              homex.setLimitBlock(list2);
                           } catch (IOException var86) {
                              var86.printStackTrace();
                           }

                           String send_messagex = Variable.Lang_YML.getString("SetCustomBlockSuccess");
                           if (send_messagex.contains("<Name>")) {
                              send_messagex = send_messagex.replace("<Name>", args[1]);
                           }

                           if (send_messagex.contains("<NBT>")) {
                              send_messagex = send_messagex.replace("<NBT>", args[3].toUpperCase());
                           }

                           if (send_messagex.contains("<Amount>")) {
                              send_messagex = send_messagex.replace("<Amount>", args[4]);
                           }

                           if (send_messagex.contains("<Type>")) {
                              send_messagex = send_messagex.replace("<Type>", args[2]);
                           }

                           sender.sendMessage(send_messagex);
                        }

                        return false;
                     }
                  }
               } else if (args.length == 5 && args[0].equalsIgnoreCase("SetBlockLimit")) {
                  if (sender instanceof Player te && !te.isOp()) {
                     te.sendMessage(Variable.Lang_YML.getString("NoPermissionSetCustomBlockLimit"));
                     return false;
                  } else {
                     Home homex = HomeAPI.getHome(args[1]);
                     if (homex == null) {
                        sender.sendMessage(Variable.Lang_YML.getString("SetCustomBlockButHomeIsNull"));
                        return false;
                     } else {
                        String str = args[2] + "|" + args[3].toUpperCase() + "|" + args[4];
                        List<String> listx = homex.getLimitBlock();
                        List<String> list2 = new ArrayList<>();

                        for (String exx : listx) {
                           list2.add(exx);
                        }

                        for (int cxx = 0; cxx < list2.size(); cxx++) {
                           String tem = list2.get(cxx);
                           if (tem.contains(args[2] + "|" + args[3].toUpperCase())) {
                              list2.remove(cxx);
                           }
                        }

                        list2.add(str);

                        try {
                           homex.setLimitBlock(list2);
                        } catch (IOException var88) {
                           var88.printStackTrace();
                        }

                        String send_messagexx = Variable.Lang_YML.getString("SetCustomBlockSuccess");
                        if (send_messagexx.contains("<Name>")) {
                           send_messagexx = send_messagexx.replace("<Name>", args[1]);
                        }

                        if (send_messagexx.contains("<NBT>")) {
                           send_messagexx = send_messagexx.replace("<NBT>", args[3].toUpperCase());
                        }

                        if (send_messagexx.contains("<Amount>")) {
                           send_messagexx = send_messagexx.replace("<Amount>", args[4]);
                        }

                        if (send_messagexx.contains("<Type>")) {
                           send_messagexx = send_messagexx.replace("<Type>", args[2]);
                        }

                        sender.sendMessage(send_messagexx);
                        return false;
                     }
                  }
               } else if (args.length == 4 && args[0].equalsIgnoreCase("DelBlockLimit")) {
                  if (sender instanceof Player te && !te.isOp()) {
                     te.sendMessage(Variable.Lang_YML.getString("NoPermissionSetCustomBlockLimit"));
                     return false;
                  } else {
                     Home homex = HomeAPI.getHome(args[1]);
                     if (homex == null) {
                        sender.sendMessage(Variable.Lang_YML.getString("SetCustomBlockButHomeIsNull"));
                        return false;
                     } else {
                        String str = args[2] + "|" + args[3].toUpperCase();
                        List<String> listx = homex.getLimitBlock();
                        List<String> list2 = new ArrayList<>();

                        for (String exx : listx) {
                           list2.add(exx);
                        }

                        boolean remove_success = false;

                        for (int cxxx = 0; cxxx < list2.size(); cxxx++) {
                           String str2 = list2.get(cxxx);
                           if (str2.contains(args[2] + "|" + args[3].toUpperCase())) {
                              list2.remove(cxxx);
                              remove_success = true;
                           }
                        }

                        if (!remove_success) {
                           sender.sendMessage(Variable.Lang_YML.getString("SetCustomBlockButHomeButNotContain"));
                           return false;
                        } else {
                           try {
                              homex.setLimitBlock(list2);
                           } catch (IOException var89) {
                              var89.printStackTrace();
                           }

                           String send_messagexxx = Variable.Lang_YML.getString("RemoveCustomBlockSuccess");
                           if (send_messagexxx.contains("<Name>")) {
                              send_messagexxx = send_messagexxx.replace("<Name>", args[1]);
                           }

                           if (send_messagexxx.contains("<NBT>")) {
                              send_messagexxx = send_messagexxx.replace("<NBT>", args[3].toUpperCase());
                           }

                           if (send_messagexxx.contains("<Type>")) {
                              send_messagexxx = send_messagexxx.replace("<Type>", args[2]);
                           }

                           sender.sendMessage(send_messagexxx);
                           return false;
                        }
                     }
                  }
               } else if (args.length >= 2 && args[0].equalsIgnoreCase("info")) {
                  String str = "";

                  for (int dx = 1; dx < args.length; dx++) {
                     str = str + " " + args[dx];
                  }

                  if (!com.Util.Perm.has(pxx, "ErrorTown.info")) {
                     pxx.sendMessage(Variable.Lang_YML.getString("NoPermissionSetInfo"));
                     return false;
                  } else if (!Util.CheckIsHome(pxx.getWorld().getName().replace(Variable.world_prefix, ""))) {
                     String tempxxxxxxxxxxx = Variable.Lang_YML.getString("NowIsNotHome");
                     sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                     sender.sendMessage(tempxxxxxxxxxxx);
                     sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
                     return false;
                  } else if (!Util.Check(pxx, pxx.getWorld().getName().replace(Variable.world_prefix, ""))) {
                     pxx.sendMessage(Variable.Lang_YML.getString("NoPermissionSetOthersInfo"));
                     return false;
                  } else {
                     List<String> adv = new ArrayList<>();
                     if (str.contains(",")) {
                        String[] content = str.split(",");

                        for (int ixxxxxxxxxxx = 0; ixxxxxxxxxxx < content.length; ixxxxxxxxxxx++) {
                           content[ixxxxxxxxxxx] = "§f" + content[ixxxxxxxxxxx];
                           if (content[ixxxxxxxxxxx].contains("&")) {
                              if (!com.Util.Perm.has(pxx, "ErrorTown.Info.Color")) {
                                 pxx.sendMessage(Variable.Lang_YML.getString("NoPermissionSetColorInfo"));
                                 return false;
                              }

                              content[ixxxxxxxxxxx] = content[ixxxxxxxxxxx].replace("&", "§");
                           }
                        }

                        adv = Arrays.asList(content);
                     } else {
                        if (str.contains("&")) {
                           if (!com.Util.Perm.has(pxx, "ErrorTown.Info.Color")) {
                              pxx.sendMessage(Variable.Lang_YML.getString("NoPermissionSetColorInfo"));
                              return false;
                           }

                           str = str.replace("&", "§");
                        }

                        adv.add("§f" + str);
                     }

                     Home homex = HomeAPI.getHome(pxx.getWorld().getName().replace(Variable.world_prefix, ""));

                     try {
                        homex.setAdvertisement(adv);
                     } catch (IOException var90) {
                        var90.printStackTrace();
                     }

                     pxx.sendMessage(Variable.Lang_YML.getString("SetInfoSuccess"));
                     return false;
                  }
               } else if (args.length == 2 && args[0].equalsIgnoreCase("gift") && args[1].equalsIgnoreCase("open")) {
                  if (!com.Util.Perm.has(pxx, "ErrorTown.Gift.Open")) {
                     pxx.sendMessage(Variable.Lang_YML.getString("NoPermissionOpenGift"));
                     return false;
                  } else if (!Util.CheckIsHome(pxx.getWorld().getName().replace(Variable.world_prefix, ""))) {
                     String tempxxxxxxxxxxx = Variable.Lang_YML.getString("NowIsNotHome");
                     sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                     sender.sendMessage(tempxxxxxxxxxxx);
                     sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
                     return false;
                  } else if (!Util.Check(pxx, pxx.getWorld().getName().replace(Variable.world_prefix, ""))) {
                     pxx.sendMessage(Variable.Lang_YML.getString("NoPermissionOpenOthersGift"));
                     return false;
                  } else if (Variable.has_open_gifts_list.containsKey(pxx.getWorld().getName().replace(Variable.world_prefix, ""))) {
                     String str = Variable.Lang_YML.getString("HasAlreadyOpenByOthers");
                     if (str.contains("<Name>")) {
                        str = str.replace("<Name>", Variable.has_open_gifts_list.get(pxx.getWorld().getName().replace(Variable.world_prefix, "")));
                     }

                     pxx.sendMessage(str);
                     return false;
                  } else {
                     Variable.has_open_gifts_list.put(pxx.getWorld().getName().replace(Variable.world_prefix, ""), pxx.getName());
                     GiftGui giftgui = new GiftGui(pxx, pxx.getWorld().getName().replace(Variable.world_prefix, ""));
                     pxx.openInventory(giftgui.getInventory());
                     return false;
                  }
               } else if (args.length == 1 && args[0].equalsIgnoreCase("homes")) {
                  if (Variable.bungee) {
                     sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                     sender.sendMessage("§8[§6错误庄园§8] §c当前版本暂未为跨服模式接入多庄园列表");
                     sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
                  } else {
                     this.sendOwnedHomesList(pxx, sender);
                  }

                  return false;
               } else if (args.length != 1 || !args[0].equalsIgnoreCase("home") && !args[0].equalsIgnoreCase("h")) {
                  if (args.length != 2 || !args[0].equalsIgnoreCase("home") && !args[0].equalsIgnoreCase("h")) {
                     if (args.length == 1) {
                        if (args[0].equalsIgnoreCase("public")) {
                           if (!Main.JavaPlugin.getConfig().getBoolean("Permission.CommandUser")
                              && !com.Util.Perm.has(pxx, "ErrorTown.Public")
                              && !com.Util.Perm.has(pxx, "ErrorTown.command.user")) {
                              String tip = Variable.Lang_YML.getString("NoPermissionCheck");
                              if (tip.contains("<Permission>")) {
                                 tip = tip.replace("<Permission>", "ErrorTown.Public");
                              }

                              pxx.sendMessage(tip);
                              return false;
                           }

                           String publicHome = Util.getBaseHomeName(pxx.getWorld().getName());
                           if (!Util.CheckIsHome(publicHome)) {
                              publicHome = pxx.getName();
                           }

                           if (Variable.bungee) {
                              if (!MySQL.CheckIsAHome(publicHome)) {
                                 String tip = Variable.Lang_YML.getString("NowIsNotHome");
                                 pxx.sendMessage(tip);
                                 return false;
                              }
                           } else {
                              File f2chk = new File(Variable.Tempf, publicHome + ".yml");
                              if (!f2chk.exists()) {
                                 String tip = Variable.Lang_YML.getString("NowIsNotHome");
                                 pxx.sendMessage(tip);
                                 return false;
                              }
                           }

                           File f2xxx = new File(Variable.Tempf, publicHome + ".yml");
                           if (Util.CheckOwnerAndManagerAndOP(pxx, publicHome)) {
                              if (Variable.bungee) {
                                 if (MySQL.getPublic(publicHome).equals("true")) {
                                    MySQL.setPublic(publicHome, "false");
                                    String tempxxxxxxxxxxx = Variable.Lang_YML.getString("DisablePublic");
                                    sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                                    sender.sendMessage(tempxxxxxxxxxxx);
                                    sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));

                                    for (Player pt : Bukkit.getOnlinePlayers()) {
                                       if (!Util.Check(pt, publicHome) && Util.getBaseHomeName(pt.getWorld().getName()).equalsIgnoreCase(publicHome)) {
                                          String bekicked = Main.JavaPlugin.getConfig().getString("BeKickedCommand");
                                          if (bekicked.contains("<Name>")) {
                                             bekicked = bekicked.replace("<Name>", pt.getName());
                                          }

                                          Bukkit.dispatchCommand(Bukkit.getConsoleSender(), bekicked);
                                          pt.sendMessage("§a§l§m--------------" + Variable.Prefix + "§a§l§m--------------");
                                          pt.sendMessage(Variable.Lang_YML.getString("BeKicked"));
                                          pt.sendMessage("§a§l§m--------------" + Variable.Prefix + "§a§l§m--------------");
                                       }
                                    }
                                 } else {
                                    MySQL.setPublic(publicHome, "true");
                                    String tempxxxxxxxxxxx = Variable.Lang_YML.getString("EnablePublic");
                                    sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                                    sender.sendMessage(tempxxxxxxxxxxx);
                                    sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
                                 }
                              } else {
                                 YamlConfiguration yamlConfigurationxx = YamlConfiguration.loadConfiguration(f2xxx);
                                 if (yamlConfigurationxx.getBoolean("Public")) {
                                    yamlConfigurationxx.set("Public", false);

                                    try {
                                       yamlConfigurationxx.save(f2xxx);
                                    } catch (IOException var98) {
                                       var98.printStackTrace();
                                    }

                                    String tempxxxxxxxxxxx = Variable.Lang_YML.getString("DisablePublic");
                                    sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                                    sender.sendMessage(tempxxxxxxxxxxx);
                                    sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));

                                    for (Player ptx : Bukkit.getOnlinePlayers()) {
                                       if (!Util.Check(ptx, publicHome) && Util.getBaseHomeName(ptx.getWorld().getName()).equalsIgnoreCase(publicHome)) {
                                          String bekicked = Variable.Lang_YML.getString("BeKickedCommand");
                                          if (bekicked.contains("<Name>")) {
                                             bekicked = bekicked.replace("<Name>", ptx.getName());
                                          }

                                          Bukkit.dispatchCommand(Bukkit.getConsoleSender(), bekicked);
                                          ptx.sendMessage("§a§l§m--------------" + Variable.Prefix + "§a§l§m--------------");
                                          ptx.sendMessage(Variable.Lang_YML.getString("BeKicked"));
                                          ptx.sendMessage("§a§l§m--------------" + Variable.Prefix + "§a§l§m--------------");
                                       }
                                    }
                                 } else {
                                    yamlConfigurationxx.set("Public", true);

                                    try {
                                       yamlConfigurationxx.save(f2xxx);
                                    } catch (IOException var97) {
                                       var97.printStackTrace();
                                    }

                                    String tempxxxxxxxxxxx = Variable.Lang_YML.getString("EnablePublic");
                                    sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                                    sender.sendMessage(tempxxxxxxxxxxx);
                                    sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
                                 }
                              }
                           } else {
                              String tempxxxxxxxxxxx = Variable.Lang_YML.getString("NoOwnerAndManagerPermission");
                              sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                              sender.sendMessage(tempxxxxxxxxxxx);
                              sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
                           }

                           Util.forceClearCache(pxx.getName(), "Public");
                           return false;
                        }

                        if (args[0].equalsIgnoreCase("tpset")) {
                           if (Variable.bungee) {
                              if (Util.CheckOwnerAndManagerAndOP(pxx, Util.getBaseHomeName(pxx.getWorld().getName()))) {
                                 MySQL.setX(pxx.getWorld().getName().replace(Variable.world_prefix, ""), String.valueOf(pxx.getLocation().getX()));
                                 MySQL.setY(pxx.getWorld().getName().replace(Variable.world_prefix, ""), String.valueOf(pxx.getLocation().getY()));
                                 MySQL.setZ(pxx.getWorld().getName().replace(Variable.world_prefix, ""), String.valueOf(pxx.getLocation().getZ()));
                                 String tempxxxxxxxxxxx = Variable.Lang_YML.getString("TpSetSuccess");
                                 sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                                 sender.sendMessage(tempxxxxxxxxxxx);
                                 sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
                                 return false;
                              }

                              String tempxxxxxxxxxxx = Variable.Lang_YML.getString("NoOwnerAndManagerPermission");
                              sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                              sender.sendMessage(tempxxxxxxxxxxx);
                              sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
                              return false;
                           }

                           File f2xxx = new File(Variable.Tempf, pxx.getWorld().getName().replace(Variable.world_prefix, "") + ".yml");
                           if (!Main.JavaPlugin.getConfig().getBoolean("Permission.CommandUser")
                              && !com.Util.Perm.has(pxx, "ErrorTown.tpset")
                              && !com.Util.Perm.has(pxx, "ErrorTown.command.user")) {
                              String tip = Variable.Lang_YML.getString("NoPermissionCheck");
                              if (tip.contains("<Permission>")) {
                                 tip = tip.replace("<Permission>", "ErrorTown.TpSet");
                              }

                              pxx.sendMessage(tip);
                              return false;
                           }

                           if (Util.CheckOwnerAndManagerAndOP(pxx, Util.getBaseHomeName(pxx.getWorld().getName()))) {
                              YamlConfiguration yamlConfigurationxx = YamlConfiguration.loadConfiguration(f2xxx);
                              yamlConfigurationxx.set("X", pxx.getLocation().getX());
                              yamlConfigurationxx.set("Y", pxx.getLocation().getY());
                              yamlConfigurationxx.set("Z", pxx.getLocation().getZ());

                              try {
                                 yamlConfigurationxx.save(f2xxx);
                              } catch (IOException var99) {
                                 var99.printStackTrace();
                              }

                              String tempxxxxxxxxxxx = Variable.Lang_YML.getString("TpSetSuccess");
                              sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                              sender.sendMessage(tempxxxxxxxxxxx);
                              sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
                              return false;
                           }

                           String tempxxxxxxxxxxx = Variable.Lang_YML.getString("NoOwnerAndManagerPermission");
                           sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                           sender.sendMessage(tempxxxxxxxxxxx);
                           sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
                           return false;
                        }

                        if (args[0].equalsIgnoreCase("flower")) {
                           if (sender instanceof Player) {
                              String baseHomeName = Util.getBaseHomeName(pxx.getWorld().getName());
                              if (pxx.getName().equalsIgnoreCase(baseHomeName)) {
                                 pxx.sendMessage(Variable.Lang_YML.getString("FlowersMySelf"));
                                 return false;
                              }

                              int set_flower = Main.JavaPlugin.getConfig().getInt("MaxFlowers");

                              for (int ixxxxxxxxxxxx = Main.JavaPlugin.getConfig().getInt("MaxFlowers") + 100; ixxxxxxxxxxxx > 0; ixxxxxxxxxxxx--) {
                                 if (com.Util.Perm.has(pxx, "ErrorTown.Flowers." + ixxxxxxxxxxxx)) {
                                    set_flower = ixxxxxxxxxxxx;
                                    break;
                                 }
                              }

                              Home home_check = HomeAPI.getHome(baseHomeName);
                              if (home_check == null) {
                                 sender.sendMessage(Variable.Lang_YML.getString("FlowerAddButHomeIsNotExist"));
                                 return false;
                              }

                              if (Variable.flowers_list.containsKey(pxx.getName())) {
                                 int has_give_amount = Variable.flowers_list.get(pxx.getName());
                                 if (has_give_amount >= set_flower) {
                                    String tempxxxxxxxxxxx = Variable.Lang_YML.getString("FlowersMax");
                                    if (tempxxxxxxxxxxx.contains("<Max>")) {
                                       tempxxxxxxxxxxx = tempxxxxxxxxxxx.replace("<Max>", String.valueOf(set_flower));
                                    }

                                    pxx.sendMessage(tempxxxxxxxxxxx);
                                    return false;
                                 }

                                 Variable.flowers_list.put(pxx.getName(), has_give_amount + 1);
                                 Home homex = HomeAPI.getHome(baseHomeName);

                                 try {
                                    homex.setFlowers(homex.getFlowers() + 1);
                                 } catch (IOException var101) {
                                    var101.printStackTrace();
                                 }

                                 String tempxxxxxxxxxxx = Variable.Lang_YML.getString("FlowersAdd");
                                 if (tempxxxxxxxxxxx.contains("<Name>")) {
                                    tempxxxxxxxxxxx = tempxxxxxxxxxxx.replace("<Name>", baseHomeName);
                                 }

                                 if (tempxxxxxxxxxxx.contains("<Now>")) {
                                    tempxxxxxxxxxxx = tempxxxxxxxxxxx.replace("<Now>", String.valueOf(Variable.flowers_list.get(pxx.getName())));
                                 }

                                 if (tempxxxxxxxxxxx.contains("<Max>")) {
                                    tempxxxxxxxxxxx = tempxxxxxxxxxxx.replace("<Max>", String.valueOf(Main.JavaPlugin.getConfig().getInt("MaxFlowers")));
                                 }

                                 pxx.sendMessage(tempxxxxxxxxxxx);
                                 String temp2xx = Variable.Lang_YML.getString("FlowersAddToOwnerAndOP");
                                 if (temp2xx.contains("<Player>")) {
                                    temp2xx = temp2xx.replace("<Player>", pxx.getName());
                                 }

                                 for (String s : homex.getOPs()) {
                                    if (Bukkit.getPlayer(s) != null) {
                                       Bukkit.getPlayer(temp2xx);
                                    }
                                 }

                                 if (Bukkit.getPlayer(homex.getName()) != null) {
                                    Bukkit.getPlayer(homex.getName()).sendMessage(temp2xx);
                                 }
                              } else {
                                 Variable.flowers_list.put(pxx.getName(), 1);
                                 Home homex = HomeAPI.getHome(baseHomeName);

                                 try {
                                    homex.setFlowers(1);
                                 } catch (IOException var100) {
                                    var100.printStackTrace();
                                 }

                                 String tempxxxxxxxxxxxx = Variable.Lang_YML.getString("FlowersAdd");
                                 if (tempxxxxxxxxxxxx.contains("<Name>")) {
                                    tempxxxxxxxxxxxx = tempxxxxxxxxxxxx.replace("<Name>", baseHomeName);
                                 }

                                 if (tempxxxxxxxxxxxx.contains("<Now>")) {
                                    tempxxxxxxxxxxxx = tempxxxxxxxxxxxx.replace("<Now>", "1");
                                 }

                                 if (tempxxxxxxxxxxxx.contains("<Max>")) {
                                    tempxxxxxxxxxxxx = tempxxxxxxxxxxxx.replace("<Max>", String.valueOf(Main.JavaPlugin.getConfig().getInt("MaxFlowers")));
                                 }

                                 pxx.sendMessage(tempxxxxxxxxxxxx);
                                 String temp2xxx = Variable.Lang_YML.getString("FlowersAddToOwnerAndOP");
                                 if (temp2xxx.contains("<Player>")) {
                                    temp2xxx = temp2xxx.replace("<Player>", pxx.getName());
                                 }

                                 for (String sx : homex.getOPs()) {
                                    if (Bukkit.getPlayer(sx) != null) {
                                       Bukkit.getPlayer(temp2xxx);
                                    }
                                 }

                                 if (Bukkit.getPlayer(homex.getName()) != null) {
                                    Bukkit.getPlayer(homex.getName()).sendMessage(temp2xxx);
                                 }
                              }
                           }

                           return false;
                        }

                        if (args[0].equalsIgnoreCase("MobSpawn")) {
                           String mobHome = Util.getBaseHomeName(pxx.getWorld().getName());
                           if (Util.CheckOwnerAndManagerAndOP(pxx, mobHome)) {
                              if (!com.Util.Perm.has(pxx, "ErrorTown.MobSpawn")) {
                                 String tip = Variable.Lang_YML.getString("NoPermissionCheck");
                                 if (tip.contains("<Permission>")) {
                                    tip = tip.replace("<Permission>", "ErrorTown.MobSpawn");
                                 }

                                 pxx.sendMessage(tip);
                                 return false;
                              }

                              try {
                                 Home homex = HomeAPI.getHome(mobHome);
                                 if (homex == null) {
                                    pxx.sendMessage(Variable.Lang_YML.getString("NowIsNotHome"));
                                    return false;
                                 }

                                 boolean nowNoMob = homex.getRuleNoMobSpawn();
                                 homex.setRuleNoMobSpawn(!nowNoMob);
                                 String mobVal = nowNoMob ? "true" : "false";
                                 World overWorld = Bukkit.getWorld(Variable.world_prefix + mobHome);
                                 World netherWorld = Bukkit.getWorld(Variable.world_prefix + mobHome + Util.getNetherSuffix());
                                 if (overWorld != null) {
                                    Platform.setGameRule(overWorld, "doMobSpawning", mobVal);
                                 }

                                 if (netherWorld != null) {
                                    Platform.setGameRule(netherWorld, "doMobSpawning", mobVal);
                                 }

                                 if (Variable.hook_multiverseCore) {
                                    MultiverseCore mvcorex = MultiverseCompat.plugin();
                                    MVWorldManager mv_mx = mvcorex.getMVWorldManager();
                                    MultiverseWorld mvOver = mv_mx.getMVWorld(Variable.world_prefix + mobHome);
                                    if (mvOver != null) {
                                       mvOver.setAllowMonsterSpawn(nowNoMob);
                                    }

                                    MultiverseWorld mvNether = mv_mx.getMVWorld(Variable.world_prefix + mobHome + Util.getNetherSuffix());
                                    if (mvNether != null) {
                                       mvNether.setAllowMonsterSpawn(nowNoMob);
                                    }
                                 }

                                 String tempxxxxxxxxxxxxx = Variable.Lang_YML.getString(nowNoMob ? "EnableMobSpawn" : "DisableMobSpawn");
                                 sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                                 sender.sendMessage(tempxxxxxxxxxxxxx);
                                 sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
                              } catch (Exception var102) {
                                 pxx.sendMessage("§c切换刷怪规则失败: " + var102.getMessage());
                                 var102.printStackTrace();
                              }

                              return false;
                           }

                           String tempxxxxxxxxxxxxx = Variable.Lang_YML.getString("NoOwnerAndManagerPermission");
                           sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                           sender.sendMessage(tempxxxxxxxxxxxxx);
                           sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
                           return false;
                        }

                        if (args[0].equalsIgnoreCase("pvp")) {
                           String pvpHome = Util.getBaseHomeName(pxx.getWorld().getName());
                           if (!Util.CheckIsHome(pvpHome)) {
                              pvpHome = pxx.getName();
                           }

                           File f2xxxx = new File(Variable.Tempf, pvpHome + ".yml");
                           if (!f2xxxx.exists()) {
                              pxx.sendMessage(Variable.Lang_YML.getString("NowIsNotHome"));
                              return false;
                           }

                           if (!Main.JavaPlugin.getConfig().getBoolean("Permission.CommandUser")
                              && !com.Util.Perm.has(pxx, "ErrorTown.PVP")
                              && !com.Util.Perm.has(pxx, "ErrorTown.command.user")) {
                              String tip = Variable.Lang_YML.getString("NoPermissionCheck");
                              if (tip.contains("<Permission>")) {
                                 tip = tip.replace("<Permission>", "ErrorTown.PVP");
                              }

                              pxx.sendMessage(tip);
                              return false;
                           }

                           if (Util.CheckOwnerAndManagerAndOP(pxx, pvpHome)) {
                              if (Variable.bungee) {
                                 if (MySQL.getPVP(pvpHome).equalsIgnoreCase("true")) {
                                    MySQL.setpvp(pvpHome, "false");
                                    if (Variable.hook_multiverseCore) {
                                       MultiverseCore mvcorexx = MultiverseCompat.plugin();
                                       MVWorldManager mv_mxx = mvcorexx.getMVWorldManager();
                                       MultiverseWorld mvx = mv_mxx.getMVWorld(pxx.getLocation().getWorld().getName());
                                       mvx.setPVPMode(false);
                                    }

                                    String tempxxxxxxxxxxxxx = Variable.Lang_YML.getString("DisablePVP");
                                    sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                                    sender.sendMessage(tempxxxxxxxxxxxxx);
                                    sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
                                 } else {
                                    MySQL.setpvp(pvpHome, "true");
                                    if (Variable.hook_multiverseCore) {
                                       MultiverseCore mvcorexx = MultiverseCompat.plugin();
                                       MVWorldManager mv_mxx = mvcorexx.getMVWorldManager();
                                       MultiverseWorld mvx = mv_mxx.getMVWorld(pxx.getLocation().getWorld().getName());
                                       mvx.setPVPMode(true);
                                    }

                                    String tempxxxxxxxxxxxxx = Variable.Lang_YML.getString("EnablePVP");
                                    sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                                    sender.sendMessage(tempxxxxxxxxxxxxx);
                                    sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
                                 }
                              } else {
                                 YamlConfiguration yamlConfigurationxx = YamlConfiguration.loadConfiguration(f2xxxx);
                                 if (yamlConfigurationxx.getBoolean("pvp")) {
                                    yamlConfigurationxx.set("pvp", false);

                                    try {
                                       yamlConfigurationxx.save(f2xxxx);
                                    } catch (IOException var104) {
                                       var104.printStackTrace();
                                    }

                                    if (Variable.hook_multiverseCore) {
                                       MultiverseCore mvcorexx = MultiverseCompat.plugin();
                                       MVWorldManager mv_mxx = mvcorexx.getMVWorldManager();
                                       MultiverseWorld mvx = mv_mxx.getMVWorld(pxx.getLocation().getWorld().getName());
                                       mvx.setPVPMode(false);
                                    }

                                    String tempxxxxxxxxxxxxx = Variable.Lang_YML.getString("DisablePVP");
                                    sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                                    sender.sendMessage(tempxxxxxxxxxxxxx);
                                    sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
                                 } else {
                                    yamlConfigurationxx.set("pvp", true);

                                    try {
                                       yamlConfigurationxx.save(f2xxxx);
                                    } catch (IOException var103) {
                                       var103.printStackTrace();
                                    }

                                    if (Variable.hook_multiverseCore) {
                                       MultiverseCore mvcorexx = MultiverseCompat.plugin();
                                       MVWorldManager mv_mxx = mvcorexx.getMVWorldManager();
                                       MultiverseWorld mvx = mv_mxx.getMVWorld(pxx.getLocation().getWorld().getName());
                                       mvx.setPVPMode(true);
                                    }

                                    String tempxxxxxxxxxxxxx = Variable.Lang_YML.getString("EnablePVP");
                                    sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                                    sender.sendMessage(tempxxxxxxxxxxxxx);
                                    sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
                                 }
                              }
                           } else {
                              String tempxxxxxxxxxxxxx = Variable.Lang_YML.getString("NoOwnerAndManagerPermission");
                              sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                              sender.sendMessage(tempxxxxxxxxxxxxx);
                              sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
                           }

                           Util.forceClearCache(pxx.getName(), "pvp");
                           return false;
                        }

                        if (args[0].equalsIgnoreCase("pickup")) {
                           String pickupHome = Util.getBaseHomeName(pxx.getWorld().getName());
                           if (!Util.CheckIsHome(pickupHome)) {
                              pickupHome = pxx.getName();
                           }

                           File f2xxxxx = new File(Variable.Tempf, pickupHome + ".yml");
                           if (!f2xxxxx.exists()) {
                              pxx.sendMessage(Variable.Lang_YML.getString("NowIsNotHome"));
                              return false;
                           }

                           if (!Main.JavaPlugin.getConfig().getBoolean("Permission.CommandUser")
                              && !com.Util.Perm.has(pxx, "ErrorTown.PickUp")
                              && !com.Util.Perm.has(pxx, "ErrorTown.command.user")) {
                              String tip = Variable.Lang_YML.getString("NoPermissionCheck");
                              if (tip.contains("<Permission>")) {
                                 tip = tip.replace("<Permission>", "ErrorTown.PickUp");
                              }

                              pxx.sendMessage(tip);
                              return false;
                           }

                           if (Util.CheckOwnerAndManagerAndOP(pxx, pickupHome)) {
                              if (Variable.bungee) {
                                 if (MySQL.getpickup(pickupHome).equalsIgnoreCase("true")) {
                                    MySQL.setpickup(pickupHome, "false");
                                    String tempxxxxxxxxxxxxx = Variable.Lang_YML.getString("DisablePickup");
                                    sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                                    sender.sendMessage(tempxxxxxxxxxxxxx);
                                    sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
                                 } else {
                                    MySQL.setpickup(pickupHome, "true");
                                    String tempxxxxxxxxxxxxx = Variable.Lang_YML.getString("EnablePickup");
                                    sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                                    sender.sendMessage(tempxxxxxxxxxxxxx);
                                    sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
                                 }
                              } else {
                                 YamlConfiguration yamlConfigurationxx = YamlConfiguration.loadConfiguration(f2xxxxx);
                                 if (yamlConfigurationxx.getBoolean("pickup")) {
                                    yamlConfigurationxx.set("pickup", false);

                                    try {
                                       yamlConfigurationxx.save(f2xxxxx);
                                    } catch (IOException var106) {
                                       var106.printStackTrace();
                                    }

                                    String tempxxxxxxxxxxxxx = Variable.Lang_YML.getString("DisablePickup");
                                    sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                                    sender.sendMessage(tempxxxxxxxxxxxxx);
                                    sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
                                 } else {
                                    yamlConfigurationxx.set("pickup", true);

                                    try {
                                       yamlConfigurationxx.save(f2xxxxx);
                                    } catch (IOException var105) {
                                       var105.printStackTrace();
                                    }

                                    String tempxxxxxxxxxxxxx = Variable.Lang_YML.getString("EnablePickup");
                                    sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                                    sender.sendMessage(tempxxxxxxxxxxxxx);
                                    sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
                                 }
                              }
                           } else {
                              String tempxxxxxxxxxxxxx = Variable.Lang_YML.getString("NoOwnerAndManagerPermission");
                              sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                              sender.sendMessage(tempxxxxxxxxxxxxx);
                              sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
                           }

                           Util.forceClearCache(pxx.getName(), "pickup");
                           return false;
                        }

                        if (args[0].equalsIgnoreCase("drop")) {
                           String dropHome = Util.getBaseHomeName(pxx.getWorld().getName());
                           if (!Util.CheckIsHome(dropHome)) {
                              dropHome = pxx.getName();
                           }

                           File f2xxxxxx = new File(Variable.Tempf, dropHome + ".yml");
                           if (!f2xxxxxx.exists()) {
                              pxx.sendMessage(Variable.Lang_YML.getString("NowIsNotHome"));
                              return false;
                           }

                           if (!Main.JavaPlugin.getConfig().getBoolean("Permission.CommandUser")
                              && !com.Util.Perm.has(pxx, "ErrorTown.Drop")
                              && !com.Util.Perm.has(pxx, "ErrorTown.command.user")) {
                              String tip = Variable.Lang_YML.getString("NoPermissionCheck");
                              if (tip.contains("<Permission>")) {
                                 tip = tip.replace("<Permission>", "ErrorTown.Drop");
                              }

                              pxx.sendMessage(tip);
                              return false;
                           }

                           if (Util.CheckOwnerAndManagerAndOP(pxx, dropHome)) {
                              if (Variable.bungee) {
                                 if (MySQL.getdropitem(dropHome).equalsIgnoreCase("true")) {
                                    MySQL.setdropitem(dropHome, "false");
                                    String tempxxxxxxxxxxxxx = Variable.Lang_YML.getString("DisableDrop");
                                    sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                                    sender.sendMessage(tempxxxxxxxxxxxxx);
                                    sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
                                 } else {
                                    MySQL.setdropitem(dropHome, "true");
                                    String tempxxxxxxxxxxxxx = Variable.Lang_YML.getString("EnableDrop");
                                    sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                                    sender.sendMessage(tempxxxxxxxxxxxxx);
                                    sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
                                 }
                              } else {
                                 YamlConfiguration yamlConfigurationxx = YamlConfiguration.loadConfiguration(f2xxxxxx);
                                 if (yamlConfigurationxx.getBoolean("drop")) {
                                    yamlConfigurationxx.set("drop", false);

                                    try {
                                       yamlConfigurationxx.save(f2xxxxxx);
                                    } catch (IOException var108) {
                                       var108.printStackTrace();
                                    }

                                    String tempxxxxxxxxxxxxx = Variable.Lang_YML.getString("DisableDrop");
                                    sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                                    sender.sendMessage(tempxxxxxxxxxxxxx);
                                    sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
                                 } else {
                                    yamlConfigurationxx.set("drop", true);

                                    try {
                                       yamlConfigurationxx.save(f2xxxxxx);
                                    } catch (IOException var107) {
                                       var107.printStackTrace();
                                    }

                                    String tempxxxxxxxxxxxxx = Variable.Lang_YML.getString("EnableDrop");
                                    sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                                    sender.sendMessage(tempxxxxxxxxxxxxx);
                                    sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
                                 }
                              }
                           } else {
                              String tempxxxxxxxxxxxxx = Variable.Lang_YML.getString("NoOwnerAndManagerPermission");
                              sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
                              sender.sendMessage(tempxxxxxxxxxxxxx);
                              sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
                           }

                           Util.forceClearCache(pxx.getName(), "drop");
                           return false;
                        }
                     }

                     if (args.length == 2 && args[0].equalsIgnoreCase("GAMEMODE")) {
                        if (!Util.CheckOwnerAndManagerAndOP(pxx, Util.getBaseHomeName(pxx.getWorld().getName()))) {
                           String tempxxxxxxxxxxxxx = Variable.Lang_YML.getString("NoOwnerAndManagerPermission");
                           sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                           sender.sendMessage(tempxxxxxxxxxxxxx);
                           sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
                           return false;
                        }

                        if (args[1].equalsIgnoreCase("EASY")) {
                           if (!com.Util.Perm.has(pxx, "ErrorTown.GAMEMODE.EASY")) {
                              String tip = Variable.Lang_YML.getString("NoPermissionCheck");
                              if (tip.contains("<Permission>")) {
                                 tip = tip.replace("<Permission>", "ErrorTown.GAMEMODE.EASY");
                              }

                              pxx.sendMessage(tip);
                              return false;
                           }

                           pxx.getWorld().setDifficulty(Difficulty.EASY);

                           try {
                              Home homexx = HomeAPI.getHome(Util.getBaseHomeName(pxx.getWorld().getName()));
                              if (homexx != null) {
                                 homexx.setRuleDifficulty(Difficulty.EASY);
                              }
                           } catch (IOException var109) {
                              var109.printStackTrace();
                           }

                           String tempxxxxxxxxxxxxx = Variable.Lang_YML.getString("DifficultyModify");
                           if (tempxxxxxxxxxxxxx.contains("<Mode>")) {
                              tempxxxxxxxxxxxxx = tempxxxxxxxxxxxxx.replace("<Mode>", "EASY");
                           }

                           pxx.sendMessage(tempxxxxxxxxxxxxx);
                           return false;
                        }

                        if (args[1].equalsIgnoreCase("NORMAL")) {
                           if (!com.Util.Perm.has(pxx, "ErrorTown.GAMEMODE.NORMAL")) {
                              String tip = Variable.Lang_YML.getString("NoPermissionCheck");
                              if (tip.contains("<Permission>")) {
                                 tip = tip.replace("<Permission>", "ErrorTown.GAMEMODE.NORMAL");
                              }

                              pxx.sendMessage(tip);
                              return false;
                           }

                           pxx.getWorld().setDifficulty(Difficulty.NORMAL);

                           try {
                              Home homexx = HomeAPI.getHome(Util.getBaseHomeName(pxx.getWorld().getName()));
                              if (homexx != null) {
                                 homexx.setRuleDifficulty(Difficulty.NORMAL);
                              }
                           } catch (IOException var110) {
                              var110.printStackTrace();
                           }

                           String tempxxxxxxxxxxxxx = Variable.Lang_YML.getString("DifficultyModify");
                           if (tempxxxxxxxxxxxxx.contains("<Mode>")) {
                              tempxxxxxxxxxxxxx = tempxxxxxxxxxxxxx.replace("<Mode>", "NORMAL");
                           }

                           pxx.sendMessage(tempxxxxxxxxxxxxx);
                           return false;
                        }

                        if (args[1].equalsIgnoreCase("HARD")) {
                           if (!com.Util.Perm.has(pxx, "ErrorTown.GAMEMODE.HARD")) {
                              String tip = Variable.Lang_YML.getString("NoPermissionCheck");
                              if (tip.contains("<Permission>")) {
                                 tip = tip.replace("<Permission>", "ErrorTown.GAMEMODE.HARD");
                              }

                              pxx.sendMessage(tip);
                              return false;
                           }

                           pxx.getWorld().setDifficulty(Difficulty.HARD);

                           try {
                              Home homexx = HomeAPI.getHome(Util.getBaseHomeName(pxx.getWorld().getName()));
                              if (homexx != null) {
                                 homexx.setRuleDifficulty(Difficulty.HARD);
                              }
                           } catch (IOException var111) {
                              var111.printStackTrace();
                           }

                           String tempxxxxxxxxxxxxx = Variable.Lang_YML.getString("DifficultyModify");
                           if (tempxxxxxxxxxxxxx.contains("<Mode>")) {
                              tempxxxxxxxxxxxxx = tempxxxxxxxxxxxxx.replace("<Mode>", "HARD");
                           }

                           pxx.sendMessage(tempxxxxxxxxxxxxx);
                           return false;
                        }

                        if (args[1].equalsIgnoreCase("PEACEFUL")) {
                           if (!com.Util.Perm.has(pxx, "ErrorTown.GAMEMODE.PEACEFUL")) {
                              String tip = Variable.Lang_YML.getString("NoPermissionCheck");
                              if (tip.contains("<Permission>")) {
                                 tip = tip.replace("<Permission>", "ErrorTown.GAMEMODE.PEACEFUL");
                              }

                              pxx.sendMessage(tip);
                              return false;
                           }

                           pxx.getWorld().setDifficulty(Difficulty.PEACEFUL);

                           try {
                              Home homexx = HomeAPI.getHome(Util.getBaseHomeName(pxx.getWorld().getName()));
                              if (homexx != null) {
                                 homexx.setRuleDifficulty(Difficulty.PEACEFUL);
                              }
                           } catch (IOException var112) {
                              var112.printStackTrace();
                           }

                           String tempxxxxxxxxxxxxx = Variable.Lang_YML.getString("DifficultyModify");
                           if (tempxxxxxxxxxxxxx.contains("<Mode>")) {
                              tempxxxxxxxxxxxxx = tempxxxxxxxxxxxxx.replace("<Mode>", "PEACEFUL");
                           }

                           pxx.sendMessage(tempxxxxxxxxxxxxx);
                           return false;
                        }
                     }

                     if (args.length != 2 || !args[0].equalsIgnoreCase("tp") && !args[0].equalsIgnoreCase("visit") && !args[0].equalsIgnoreCase("v")) {
                        if (args.length == 2 && (args[0].equalsIgnoreCase("invite") || args[0].equalsIgnoreCase("i"))) {
                           String Name = args[1];
                           if (Variable.bungee) {
                              if (!Main.JavaPlugin.getConfig().getBoolean("Permission.CommandUser")
                                 && !com.Util.Perm.has(pxx, "ErrorTown.Invite")
                                 && !com.Util.Perm.has(pxx, "ErrorTown.command.user")) {
                                 String tip = Variable.Lang_YML.getString("NoPermissionCheck");
                                 if (tip.contains("<Permission>")) {
                                    tip = tip.replace("<Permission>", "ErrorTown.Invite");
                                 }

                                 pxx.sendMessage(tip);
                                 return false;
                              }

                              if (!MySQL.alreadyhastheplayerhome(pxx.getName())) {
                                 String tempxxxxxxxxxxxxx = Variable.Lang_YML.getString("NoHome");
                                 sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                                 sender.sendMessage(tempxxxxxxxxxxxxx);
                                 sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
                                 return false;
                              }

                              if (args[1].equalsIgnoreCase(pxx.getName())) {
                                 String tempxxxxxxxxxxxxx = Variable.Lang_YML.getString("InviteMySelf");
                                 sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                                 sender.sendMessage(tempxxxxxxxxxxxxx);
                                 sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
                                 return false;
                              }

                              List<String> blacklistx = MySQL.getDenys(pxx.getName());
                              if (blacklistx == null) {
                                 blacklistx = new ArrayList<>();
                              }

                              for (int ixxxxxxxxxxxxx = 0; ixxxxxxxxxxxxx < blacklistx.size(); ixxxxxxxxxxxxx++) {
                                 if (blacklistx.get(ixxxxxxxxxxxxx).equalsIgnoreCase(args[1])) {
                                    String tempxxxxxxxxxxxxx = Variable.Lang_YML.getString("HasAlreadyInBlack");
                                    sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                                    sender.sendMessage(tempxxxxxxxxxxxxx);
                                    sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
                                    return false;
                                 }
                              }

                              List<String> memberlist = MySQL.getMembers(pxx.getName());
                              if (memberlist == null) {
                                 memberlist = new ArrayList<>();
                              }

                              for (int ixxxxxxxxxxxxxx = 0; ixxxxxxxxxxxxxx < memberlist.size(); ixxxxxxxxxxxxxx++) {
                                 if (memberlist.get(ixxxxxxxxxxxxxx).equalsIgnoreCase(args[1])) {
                                    String tempxxxxxxxxxxxxx = Variable.Lang_YML.getString("HasAlreadyTrust");
                                    sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                                    sender.sendMessage(tempxxxxxxxxxxxxx);
                                    sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
                                    return false;
                                 }
                              }

                              List<String> OP_List = MySQL.getOP(pxx.getName());
                              if (OP_List == null) {
                                 OP_List = new ArrayList<>();
                              }

                              Boolean CheckSame = false;

                              for (int ixxxxxxxxxxxxxxx = 0; ixxxxxxxxxxxxxxx < OP_List.size(); ixxxxxxxxxxxxxxx++) {
                                 if (OP_List.get(ixxxxxxxxxxxxxxx).equalsIgnoreCase(Name)) {
                                    CheckSame = true;
                                    String tempxxxxxxxxxxxxx = Variable.Lang_YML.getString("HasAlreadyOP");
                                    sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                                    sender.sendMessage(tempxxxxxxxxxxxxx);
                                    sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
                                    return false;
                                 }
                              }

                              if (!CheckSame) {
                                 if (!this.canReceiveHomeInvite(args[1])) {
                                    this.sendInviteLimitReached(sender, args[1]);
                                    return false;
                                 }

                                 if (Variable.invite_list.containsKey(pxx.getName())) {
                                    String tempxxxxxxxxxxxxx = Variable.Lang_YML.getString("HasInInviteCooldown");
                                    sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                                    sender.sendMessage(tempxxxxxxxxxxxxx);
                                    sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
                                    return false;
                                 }

                                 if (Variable.invite_list.containsValue(args[1])) {
                                    String tempxxxxxxxxxxxxx = Variable.Lang_YML.getString("InvitePlayerWhoHasBeenAlreadyInvited");
                                    sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                                    sender.sendMessage(tempxxxxxxxxxxxxx);
                                    sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
                                    return false;
                                 }

                                 int max_player = this.getEffectiveHomeOpLimit(pxx, pxx.getWorld().getName().replace(Variable.world_prefix, ""));
                                 if (OP_List.size() < max_player) {
                                    Player be_invite = Bukkit.getPlayer(args[1]);
                                    if (be_invite == null) {
                                       String tempxxxxxxxxxxxxx = Variable.Lang_YML.getString("NoPlayerExist");
                                       sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                                       sender.sendMessage(tempxxxxxxxxxxxxx);
                                       sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
                                       return false;
                                    }

                                    Variable.invite_list.put(pxx.getName(), args[1]);
                                    this.invite_guoqi(pxx);
                                    String tempxxxxxxxxxxxxx = Variable.Lang_YML.getString("SendInviteToPlayer");
                                    if (tempxxxxxxxxxxxxx.contains("<Name>")) {
                                       tempxxxxxxxxxxxxx = tempxxxxxxxxxxxxx.replace("<Name>", args[1]);
                                    }

                                    sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                                    sender.sendMessage(tempxxxxxxxxxxxxx);
                                    sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
                                    if (be_invite != null) {
                                       String temp2xxxx = Variable.Lang_YML.getString("InviteMessage");
                                       if (temp2xxxx.contains("<player>")) {
                                          temp2xxxx = temp2xxxx.replace("<player>", pxx.getName());
                                       }

                                       com.Util.ClickableText.run(be_invite, temp2xxxx, "/sh accept");
                                    }

                                    return false;
                                 }

                                 String tempxxxxxxxxxxxxxx = Variable.Lang_YML.getString("ReachMaxOP");
                                 if (tempxxxxxxxxxxxxxx.contains("<MaxAmount>")) {
                                    tempxxxxxxxxxxxxxx = tempxxxxxxxxxxxxxx.replace("<MaxAmount>", String.valueOf(max_player));
                                 }

                                 sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                                 sender.sendMessage(tempxxxxxxxxxxxxxx);
                                 sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
                                 Player be_invitex = Bukkit.getPlayer(args[1]);
                                 if (be_invitex == null) {
                                    String tempxxxxxxxxxxxxxxx = Variable.Lang_YML.getString("NoPlayerExist");
                                    sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                                    sender.sendMessage(tempxxxxxxxxxxxxxxx);
                                    sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
                                    return false;
                                 }

                                 Variable.invite_list.put(pxx.getName(), args[1]);
                                 this.invite_guoqi(pxx);
                                 String tempxxxxxxxxxxxxxxx = Variable.Lang_YML.getString("SendInviteToPlayer");
                                 if (tempxxxxxxxxxxxxxxx.contains("<Name>")) {
                                    tempxxxxxxxxxxxxxxx = tempxxxxxxxxxxxxxxx.replace("<Name>", args[1]);
                                 }

                                 sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                                 sender.sendMessage(tempxxxxxxxxxxxxxxx);
                                 sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
                                 if (be_invitex != null) {
                                    String temp2xxxxx = Variable.Lang_YML.getString("InviteMessage");
                                    if (temp2xxxxx.contains("<player>")) {
                                       temp2xxxxx = temp2xxxxx.replace("<player>", pxx.getName());
                                    }

                                    com.Util.ClickableText.run(be_invitex, temp2xxxxx, "/sh accept");
                                 }
                              }
                           } else {
                              String currentHomeName = this.getCurrentOrPrimaryOwnedHome(pxx);
                              File f2xxxxxxx = currentHomeName == null ? null : new File(Variable.Tempf, currentHomeName + ".yml");
                              if (f2xxxxxxx == null || !f2xxxxxxx.exists()) {
                                 String tempxxxxxxxxxxxxxxxx = Variable.Lang_YML.getString("NoHome");
                                 sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                                 sender.sendMessage(tempxxxxxxxxxxxxxxxx);
                                 sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
                                 return false;
                              }

                              if (!Main.JavaPlugin.getConfig().getBoolean("Permission.CommandUser")
                                 && !com.Util.Perm.has(pxx, "ErrorTown.Invite")
                                 && !com.Util.Perm.has(pxx, "ErrorTown.command.user")) {
                                 String tip = Variable.Lang_YML.getString("NoPermissionCheck");
                                 if (tip.contains("<Permission>")) {
                                    tip = tip.replace("<Permission>", "ErrorTown.Invite");
                                 }

                                 pxx.sendMessage(tip);
                                 return false;
                              }

                              YamlConfiguration yamlConfigurationxx = YamlConfiguration.loadConfiguration(f2xxxxxxx);
                              if (args[1].equalsIgnoreCase(pxx.getName())) {
                                 String tempxxxxxxxxxxxxxxxx = Variable.Lang_YML.getString("InviteMySelf");
                                 sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                                 sender.sendMessage(tempxxxxxxxxxxxxxxxx);
                                 sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
                                 return false;
                              }

                              List<String> blacklistxx = yamlConfigurationxx.getStringList("Denys");
                              if (blacklistxx == null) {
                                 blacklistxx = new ArrayList<>();
                              }

                              for (int ixxxxxxxxxxxxxxxx = 0; ixxxxxxxxxxxxxxxx < blacklistxx.size(); ixxxxxxxxxxxxxxxx++) {
                                 if (blacklistxx.get(ixxxxxxxxxxxxxxxx).equalsIgnoreCase(args[1])) {
                                    String tempxxxxxxxxxxxxxxxx = Variable.Lang_YML.getString("HasAlreadyInBlack");
                                    sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                                    sender.sendMessage(tempxxxxxxxxxxxxxxxx);
                                    sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
                                    return false;
                                 }
                              }

                              List<String> memberlistx = yamlConfigurationxx.getStringList("Members");
                              if (memberlistx == null) {
                                 memberlistx = new ArrayList<>();
                              }

                              for (int ixxxxxxxxxxxxxxxxx = 0; ixxxxxxxxxxxxxxxxx < memberlistx.size(); ixxxxxxxxxxxxxxxxx++) {
                                 if (memberlistx.get(ixxxxxxxxxxxxxxxxx).equalsIgnoreCase(args[1])) {
                                    String tempxxxxxxxxxxxxxxxx = Variable.Lang_YML.getString("HasAlreadyTrust");
                                    sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                                    sender.sendMessage(tempxxxxxxxxxxxxxxxx);
                                    sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
                                    return false;
                                 }
                              }

                              List<String> OP_Listx = yamlConfigurationxx.getStringList("OP");
                              if (OP_Listx == null) {
                                 OP_Listx = new ArrayList<>();
                              }

                              Boolean CheckSame = false;

                              for (int ixxxxxxxxxxxxxxxxxx = 0; ixxxxxxxxxxxxxxxxxx < OP_Listx.size(); ixxxxxxxxxxxxxxxxxx++) {
                                 if (OP_Listx.get(ixxxxxxxxxxxxxxxxxx).equalsIgnoreCase(Name)) {
                                    CheckSame = true;
                                    String tempxxxxxxxxxxxxxxxx = Variable.Lang_YML.getString("HasAlreadyOP");
                                    sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                                    sender.sendMessage(tempxxxxxxxxxxxxxxxx);
                                    sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
                                    return false;
                                 }
                              }

                              if (!CheckSame) {
                                 if (!this.canReceiveHomeInvite(args[1])) {
                                    this.sendInviteLimitReached(sender, args[1]);
                                    return false;
                                 }

                                 if (Variable.invite_list.containsKey(pxx.getName())) {
                                    String tempxxxxxxxxxxxxxxxx = Variable.Lang_YML.getString("HasInInviteCooldown");
                                    sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                                    sender.sendMessage(tempxxxxxxxxxxxxxxxx);
                                    sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
                                    return false;
                                 }

                                 if (Variable.invite_list.containsValue(args[1])) {
                                    String tempxxxxxxxxxxxxxxxx = Variable.Lang_YML.getString("InvitePlayerWhoHasBeenAlreadyInvited");
                                    sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                                    sender.sendMessage(tempxxxxxxxxxxxxxxxx);
                                    sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
                                    return false;
                                 }

                                 int max_playerx = this.getEffectiveHomeOpLimit(pxx, currentHomeName);
                                 if (OP_Listx.size() < max_playerx) {
                                    Player be_invitexx = Bukkit.getPlayer(args[1]);
                                    if (be_invitexx == null) {
                                       String tempxxxxxxxxxxxxxxxx = Variable.Lang_YML.getString("NoPlayerExist");
                                       sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                                       sender.sendMessage(tempxxxxxxxxxxxxxxxx);
                                       sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
                                       return false;
                                    }

                                    Variable.invite_list.put(pxx.getName(), args[1]);
                                    Variable.inviteHomeName.put(pxx.getName(), currentHomeName);
                                    this.invite_guoqi(pxx);
                                    String tempxxxxxxxxxxxxxxxx = Variable.Lang_YML.getString("SendInviteToPlayer");
                                    if (tempxxxxxxxxxxxxxxxx.contains("<Name>")) {
                                       tempxxxxxxxxxxxxxxxx = tempxxxxxxxxxxxxxxxx.replace("<Name>", args[1]);
                                    }

                                    sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                                    sender.sendMessage(tempxxxxxxxxxxxxxxxx);
                                    sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
                                    if (be_invitexx != null) {
                                       String temp2xxxxxx = Variable.Lang_YML.getString("InviteMessage");
                                       if (temp2xxxxxx.contains("<player>")) {
                                          temp2xxxxxx = temp2xxxxxx.replace("<player>", pxx.getName());
                                       }

                                       com.Util.ClickableText.run(be_invitexx, temp2xxxxxx, "/sh accept");
                                    }

                                    return false;
                                 }

                                 String tempxxxxxxxxxxxxxxxxx = Variable.Lang_YML.getString("ReachMaxOP");
                                 if (tempxxxxxxxxxxxxxxxxx.contains("<MaxAmount>")) {
                                    tempxxxxxxxxxxxxxxxxx = tempxxxxxxxxxxxxxxxxx.replace("<MaxAmount>", String.valueOf(max_playerx));
                                 }

                                 sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                                 sender.sendMessage(tempxxxxxxxxxxxxxxxxx);
                                 sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
                                 Player be_invitexxx = Bukkit.getPlayer(args[1]);
                                 if (be_invitexxx == null) {
                                    String tempxxxxxxxxxxxxxxxxxx = Variable.Lang_YML.getString("NoPlayerExist");
                                    sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                                    sender.sendMessage(tempxxxxxxxxxxxxxxxxxx);
                                    sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
                                    return false;
                                 }

                                 Variable.invite_list.put(pxx.getName(), args[1]);
                                 Variable.inviteHomeName.put(pxx.getName(), currentHomeName);
                                 this.invite_guoqi(pxx);
                                 String tempxxxxxxxxxxxxxxxxxx = Variable.Lang_YML.getString("SendInviteToPlayer");
                                 if (tempxxxxxxxxxxxxxxxxxx.contains("<Name>")) {
                                    tempxxxxxxxxxxxxxxxxxx = tempxxxxxxxxxxxxxxxxxx.replace("<Name>", args[1]);
                                 }

                                 sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                                 sender.sendMessage(tempxxxxxxxxxxxxxxxxxx);
                                 sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
                                 if (be_invitexxx != null) {
                                    String temp2xxxxxxx = Variable.Lang_YML.getString("InviteMessage");
                                    if (temp2xxxxxxx.contains("<player>")) {
                                       temp2xxxxxxx = temp2xxxxxxx.replace("<player>", pxx.getName());
                                    }

                                    com.Util.ClickableText.run(be_invitexxx, temp2xxxxxxx, "/sh accept");
                                 }
                              }
                           }

                           return false;
                        } else if (args.length == 1 && args[0].equalsIgnoreCase("accept")) {
                           if (Variable.bungee) {
                              if (!Variable.invite_list.containsValue(pxx.getName())) {
                                 String tempxxxxxxxxxxxxxxxxxxx = Variable.Lang_YML.getString("HasNoOthersInvite");
                                 sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                                 sender.sendMessage(tempxxxxxxxxxxxxxxxxxxx);
                                 sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
                                 return false;
                              }

                              String who_invite = "";

                              for (String Key : Variable.invite_list.keySet()) {
                                 if (Variable.invite_list.get(Key).equalsIgnoreCase(pxx.getName())) {
                                    who_invite = Key;
                                    break;
                                 }
                              }

                              if (!Util.CheckIsHome(who_invite)) {
                                 String tempxxxxxxxxxxxxxxxxxxx = Variable.Lang_YML.getString("InviteAcceptNoExistHome");
                                 sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                                 sender.sendMessage(tempxxxxxxxxxxxxxxxxxxx);
                                 sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
                                 return false;
                              }

                              if (!this.canReceiveHomeInvite(pxx.getName())) {
                                 this.sendInviteLimitReached(sender, pxx.getName());
                                 return false;
                              }

                              List<String> OP = MySQL.getOP(who_invite);
                              String result = MySQL.getListStringSpiltByDot(OP);
                              if (result != null && !result.equalsIgnoreCase("")) {
                                 result = result + "," + pxx.getName();
                              } else {
                                 result = pxx.getName();
                              }

                              MySQL.setOP(who_invite, result);
                              String temp2xxxxxxxx = Variable.Lang_YML.getString("SuccessJoinOthers");
                              if (temp2xxxxxxxx.contains("<Name>")) {
                                 temp2xxxxxxxx = temp2xxxxxxxx.replace("<Name>", who_invite);
                              }

                              sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                              sender.sendMessage(temp2xxxxxxxx);
                              sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
                              Player who_invite_player = Bukkit.getPlayer(who_invite);
                              if (who_invite_player != null) {
                                 String tempxxxxxxxxxxxxxxxxxxx = Variable.Lang_YML.getString("SuccessInviteOther");
                                 if (tempxxxxxxxxxxxxxxxxxxx.contains("<Name>")) {
                                    tempxxxxxxxxxxxxxxxxxxx = tempxxxxxxxxxxxxxxxxxxx.replace("<Name>", pxx.getName());
                                 }

                                 who_invite_player.sendMessage("§a§l§m--------------" + Variable.Prefix + "§a§l§m--------------");
                                 who_invite_player.sendMessage(tempxxxxxxxxxxxxxxxxxxx);
                                 who_invite_player.sendMessage("§a§l§m--------------" + Variable.Prefix + "§a§l§m--------------");
                              }

                              Variable.invite_list.remove(who_invite);
                           } else {
                              if (!Variable.invite_list.containsValue(pxx.getName())) {
                                 String tempxxxxxxxxxxxxxxxxxxx = Variable.Lang_YML.getString("HasNoOthersInvite");
                                 sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                                 sender.sendMessage(tempxxxxxxxxxxxxxxxxxxx);
                                 sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
                                 return false;
                              }

                              String who_invite = "";

                              for (String Keyx : Variable.invite_list.keySet()) {
                                 if (Variable.invite_list.get(Keyx).equalsIgnoreCase(pxx.getName())) {
                                    who_invite = Keyx;
                                    break;
                                 }
                              }

                              String inviteHomeName = Variable.inviteHomeName.containsKey(who_invite) ? Variable.inviteHomeName.get(who_invite) : who_invite;
                              File f2xxxxxxxx = new File(Variable.Tempf, inviteHomeName + ".yml");
                              if (!f2xxxxxxxx.exists()) {
                                 String tempxxxxxxxxxxxxxxxxxxx = Variable.Lang_YML.getString("InviteAcceptNoExistHome");
                                 sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                                 sender.sendMessage(tempxxxxxxxxxxxxxxxxxxx);
                                 sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
                                 return false;
                              }

                              if (!this.canReceiveHomeInvite(pxx.getName())) {
                                 this.sendInviteLimitReached(sender, pxx.getName());
                                 return false;
                              }

                              YamlConfiguration yamlConfigurationxxx = YamlConfiguration.loadConfiguration(f2xxxxxxxx);
                              List<String> OPx = yamlConfigurationxxx.getStringList("OP");
                              if (OPx == null) {
                                 OPx = new ArrayList<>();
                              }

                              int maxInviteOps = this.getExpandedHomeOpLimit(inviteHomeName);
                              Player inviteOwnerOnline = Bukkit.getPlayer(Util.getHomeOwner(inviteHomeName));
                              if (inviteOwnerOnline != null) {
                                 maxInviteOps = Math.max(maxInviteOps, this.getEffectiveHomeOpLimit(inviteOwnerOnline, inviteHomeName));
                              }

                              if (OPx.size() >= maxInviteOps) {
                                 String tempxxxxxxxxxxxxxxxxxxx = Variable.Lang_YML.getString("ReachMaxOP");
                                 if (tempxxxxxxxxxxxxxxxxxxx.contains("<MaxAmount>")) {
                                    tempxxxxxxxxxxxxxxxxxxx = tempxxxxxxxxxxxxxxxxxxx.replace("<MaxAmount>", String.valueOf(maxInviteOps));
                                 }

                                 sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                                 sender.sendMessage(tempxxxxxxxxxxxxxxxxxxx);
                                 sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
                                 Variable.invite_list.remove(who_invite);
                                 Variable.inviteHomeName.remove(who_invite);
                                 return false;
                              }

                              OPx.add(pxx.getName());
                              yamlConfigurationxxx.set("OP", OPx);

                              try {
                                 yamlConfigurationxxx.save(f2xxxxxxxx);
                              } catch (IOException var116) {
                                 var116.printStackTrace();
                              }

                              String temp2xxxxxxxxx = Variable.Lang_YML.getString("SuccessJoinOthers");
                              if (temp2xxxxxxxxx.contains("<Name>")) {
                                 temp2xxxxxxxxx = temp2xxxxxxxxx.replace("<Name>", inviteHomeName);
                              }

                              sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                              sender.sendMessage(temp2xxxxxxxxx);
                              sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
                              Player who_invite_player = Bukkit.getPlayer(Util.getHomeOwner(inviteHomeName));
                              if (who_invite_player != null) {
                                 String tempxxxxxxxxxxxxxxxxxxx = Variable.Lang_YML.getString("SuccessInviteOther");
                                 if (tempxxxxxxxxxxxxxxxxxxx.contains("<Name>")) {
                                    tempxxxxxxxxxxxxxxxxxxx = tempxxxxxxxxxxxxxxxxxxx.replace("<Name>", pxx.getName());
                                 }

                                 who_invite_player.sendMessage("§a§l§m--------------" + Variable.Prefix + "§a§l§m--------------");
                                 who_invite_player.sendMessage(tempxxxxxxxxxxxxxxxxxxx);
                                 who_invite_player.sendMessage("§a§l§m--------------" + Variable.Prefix + "§a§l§m--------------");
                              }

                              Variable.invite_list.remove(who_invite);
                              Variable.inviteHomeName.remove(who_invite);
                           }

                           return false;
                        } else if (args.length >= 2 && args[0].equalsIgnoreCase("rule") && sender instanceof Player) {
                           String baseNamex = Util.getBaseHomeName(pxx.getWorld().getName());
                           if (!Util.CheckIsHome(pxx.getWorld().getName().replace(Variable.world_prefix, "")) && !Util.CheckIsHome(baseNamex)) {
                              pxx.sendMessage("§c请在家园世界内使用此命令");
                              return false;
                           } else if (!Util.CheckOwnerAndManagerAndOP(pxx, baseNamex)) {
                              pxx.sendMessage(
                                 Variable.Lang_YML.getString("NoOwnerAndManagerPermission") != null
                                    ? Variable.Lang_YML.getString("NoOwnerAndManagerPermission")
                                    : "§c你没有管理此家园的权限"
                              );
                              return false;
                           } else {
                              Home ruleHome = HomeAPI.getHome(baseNamex);
                              if (ruleHome == null) {
                                 pxx.sendMessage("§c无法加载家园数据");
                                 return false;
                              } else {
                                 String ruleName = args[1].toLowerCase();

                                 try {
                                    if (ruleName.equals("explosion")) {
                                       boolean nowProtect = ruleHome.getRuleExplosionProtect();
                                       ruleHome.setRuleExplosionProtect(!nowProtect);
                                       HomeAudit.log("rule.explosion", pxx, baseNamex, "set=" + !nowProtect);
                                       pxx.sendMessage(!nowProtect ? "§a爆炸保护已开启 (爆炸不会破坏方块)" : "§c爆炸保护已关闭 (爆炸会破坏方块)");
                                    } else if (ruleName.equals("fire")) {
                                       boolean nowNoFire = ruleHome.getRuleNoFireSpread();
                                       ruleHome.setRuleNoFireSpread(!nowNoFire);
                                       HomeAudit.log("rule.fire", pxx, baseNamex, "set=" + !nowNoFire);
                                       String fireVal = nowNoFire ? "true" : "false";
                                       World ruleWorld = Bukkit.getWorld(Variable.world_prefix + baseNamex);
                                       if (ruleWorld != null) {
                                          Platform.setGameRule(ruleWorld, "doFireTick", fireVal);
                                       }

                                       World ruleNether = Bukkit.getWorld(Variable.world_prefix + baseNamex + Util.getNetherSuffix());
                                       if (ruleNether != null) {
                                          Platform.setGameRule(ruleNether, "doFireTick", fireVal);
                                       }

                                       pxx.sendMessage(!nowNoFire ? "§a火势蔓延已禁止 (主世界+地狱)" : "§c火势蔓延已启用 (主世界+地狱)");
                                    } else if (ruleName.equals("mob")) {
                                       boolean nowNoMobx = ruleHome.getRuleNoMobSpawn();
                                       ruleHome.setRuleNoMobSpawn(!nowNoMobx);
                                       HomeAudit.log("rule.mob", pxx, baseNamex, "set=" + !nowNoMobx);
                                       World ruleWorldx = Bukkit.getWorld(Variable.world_prefix + baseNamex);
                                       if (ruleWorldx != null) {
                                          Util.applyHomeWorldRules(ruleWorldx, ruleHome);
                                       }

                                       World ruleNether = Bukkit.getWorld(Variable.world_prefix + baseNamex + Util.getNetherSuffix());
                                       if (ruleNether != null) {
                                          Util.applyHomeWorldRules(ruleNether, ruleHome);
                                       }

                                       pxx.sendMessage(!nowNoMobx ? "§a怪物生成已禁止 (主世界+地狱)" : "§c怪物生成已启用 (主世界+地狱)");
                                    } else if (ruleName.equals("grief")) {
                                       boolean next = !ruleHome.getRuleMobGriefingEnabled();
                                       ruleHome.setRuleMobGriefingEnabled(next);
                                       HomeAudit.log("rule.grief", pxx, baseNamex, "set=" + next);
                                       String griefVal = next ? "true" : "false";
                                       World ruleWorldxx = Bukkit.getWorld(Variable.world_prefix + baseNamex);
                                       if (ruleWorldxx != null) {
                                          Platform.setGameRule(ruleWorldxx, "mobGriefing", griefVal);
                                       }

                                       World ruleNether = Bukkit.getWorld(Variable.world_prefix + baseNamex + Util.getNetherSuffix());
                                       if (ruleNether != null) {
                                          Platform.setGameRule(ruleNether, "mobGriefing", griefVal);
                                       }

                                       pxx.sendMessage(next ? "§a生物破坏已启用 (主世界+地狱)" : "§c生物破坏已关闭 (主世界+地狱)");
                                    } else if (ruleName.equals("hostile")) {
                                       boolean nextx = !ruleHome.getRuleAllowHostileMobs();
                                       ruleHome.setRuleAllowHostileMobs(nextx);
                                       HomeAudit.log("rule.hostile", pxx, baseNamex, "set=" + nextx);
                                       World ruleWorldxxx = Bukkit.getWorld(Variable.world_prefix + baseNamex);
                                       if (ruleWorldxxx != null) {
                                          Util.applyHomeWorldRules(ruleWorldxxx, ruleHome);
                                       }

                                       World ruleNether = Bukkit.getWorld(Variable.world_prefix + baseNamex + Util.getNetherSuffix());
                                       if (ruleNether != null) {
                                          Util.applyHomeWorldRules(ruleNether, ruleHome);
                                       }

                                       pxx.sendMessage(nextx ? "§a敌对生物已允许生成" : "§c敌对生物已禁止生成");
                                    } else if (ruleName.equals("passive")) {
                                       boolean nextxx = !ruleHome.getRuleAllowPassiveMobs();
                                       ruleHome.setRuleAllowPassiveMobs(nextxx);
                                       HomeAudit.log("rule.passive", pxx, baseNamex, "set=" + nextxx);
                                       World ruleWorldxxxx = Bukkit.getWorld(Variable.world_prefix + baseNamex);
                                       if (ruleWorldxxxx != null) {
                                          Util.applyHomeWorldRules(ruleWorldxxxx, ruleHome);
                                       }

                                       World ruleNether = Bukkit.getWorld(Variable.world_prefix + baseNamex + Util.getNetherSuffix());
                                       if (ruleNether != null) {
                                          Util.applyHomeWorldRules(ruleNether, ruleHome);
                                       }

                                       pxx.sendMessage(nextxx ? "§a被动生物已允许生成" : "§c被动生物已禁止生成");
                                    } else if (ruleName.equals("spawner")) {
                                       boolean nextxxx = !ruleHome.getRuleAllowSpawnerSpawn();
                                       ruleHome.setRuleAllowSpawnerSpawn(nextxxx);
                                       HomeAudit.log("rule.spawner", pxx, baseNamex, "set=" + nextxxx);
                                       pxx.sendMessage(nextxxx ? "§a刷怪笼已允许生效" : "§c刷怪笼已禁止生效");
                                    } else if (ruleName.equals("breed")) {
                                       boolean nextxxx = !ruleHome.getRuleAllowAnimalBreed();
                                       ruleHome.setRuleAllowAnimalBreed(nextxxx);
                                       HomeAudit.log("rule.breed", pxx, baseNamex, "set=" + nextxxx);
                                       pxx.sendMessage(nextxxx ? "§a动物繁殖已允许" : "§c动物繁殖已禁止");
                                    } else if (ruleName.equals("mobcap")) {
                                       if (!this.hasRequiredHomeLevel(
                                          pxx, Main.JavaPlugin.getConfig().getInt("FeatureUnlock.MobRuleAdvancedLevel", 3), "刷怪数量上限"
                                       )) {
                                          return false;
                                       }

                                       if (args.length < 3) {
                                          pxx.sendMessage("§c用法: /sh rule mobcap <add|sub>");
                                          return false;
                                       }

                                       int current = ruleHome.getRuleMaxMobCount();
                                       int step = Math.max(1, Main.JavaPlugin.getConfig().getInt("HomeRulesDefaults.MaxMobCountStep", 8));
                                       int min = Math.max(0, Main.JavaPlugin.getConfig().getInt("HomeRulesDefaults.MaxMobCountMin", 0));
                                       int max = Math.max(min, Main.JavaPlugin.getConfig().getInt("HomeRulesDefaults.MaxMobCountMax", 512));
                                       if (args[2].equalsIgnoreCase("add")) {
                                          current = Math.min(max, current + step);
                                       } else {
                                          if (!args[2].equalsIgnoreCase("sub")) {
                                             pxx.sendMessage("§c用法: /sh rule mobcap <add|sub>");
                                             return false;
                                          }

                                          current = Math.max(min, current - step);
                                       }

                                       ruleHome.setRuleMaxMobCount(current);
                                       HomeAudit.log("rule.mobcap", pxx, baseNamex, "set=" + current);
                                       pxx.sendMessage("§a刷怪数量上限已设置为 §e" + current);
                                    } else {
                                       pxx.sendMessage(
                                          "§c未知规则: " + ruleName + "  可用: explosion / fire / mob / grief / hostile / passive / spawner / breed / mobcap"
                                       );
                                    }
                                 } catch (Exception var117) {
                                    pxx.sendMessage("§c规则切换失败: " + var117.getMessage());
                                    var117.printStackTrace();
                                 }

                                 return false;
                              }
                           }
                        } else if (args.length == 1 && args[0].equalsIgnoreCase("locktime")) {
                           if (!Main.JavaPlugin.getConfig().getBoolean("EnableTimeLock")) {
                              sender.sendMessage("§c锁定时间功能未开启");
                              return false;
                           } else if (!this.hasRequiredHomeLevel(pxx, Main.JavaPlugin.getConfig().getInt("FeatureUnlock.LockTimeLevel", 3), "锁定时间")) {
                              return false;
                           } else {
                              File f2xxxxxxxxx = new File(Variable.Tempf, Util.getBaseHomeName(pxx.getWorld().getName()) + ".yml");
                              if (Util.CheckOwnerAndManagerAndOP(pxx, Util.getBaseHomeName(pxx.getWorld().getName()))) {
                                 if (Variable.bungee) {
                                    if (MySQL.getlocktime(pxx.getWorld().getName().replace(Variable.world_prefix, "")).equalsIgnoreCase("true")) {
                                       MySQL.setlocktime(pxx.getWorld().getName().replace(Variable.world_prefix, ""), "false");
                                       String tempxxxxxxxxxxxxxxxxxxx = Variable.Lang_YML.getString("TimeUnLocked");
                                       sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                                       sender.sendMessage(tempxxxxxxxxxxxxxxxxxxx);
                                       sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
                                    } else {
                                       MySQL.setlocktime(pxx.getWorld().getName().replace(Variable.world_prefix, ""), "true");
                                       MySQL.settime(pxx.getWorld().getName().replace(Variable.world_prefix, ""), String.valueOf(pxx.getWorld().getTime()));
                                       String tempxxxxxxxxxxxxxxxxxxx = Variable.Lang_YML.getString("TimeLocked");
                                       sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                                       sender.sendMessage(tempxxxxxxxxxxxxxxxxxxx);
                                       sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
                                    }
                                 } else {
                                    YamlConfiguration yamlConfigurationxxxx = YamlConfiguration.loadConfiguration(f2xxxxxxxxx);
                                    if (yamlConfigurationxxxx.getBoolean("locktime")) {
                                       yamlConfigurationxxxx.set("locktime", false);

                                       try {
                                          yamlConfigurationxxxx.save(f2xxxxxxxxx);
                                       } catch (IOException var119) {
                                          var119.printStackTrace();
                                       }

                                       String tempxxxxxxxxxxxxxxxxxxx = Variable.Lang_YML.getString("TimeUnLocked");
                                       sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                                       sender.sendMessage(tempxxxxxxxxxxxxxxxxxxx);
                                       sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
                                    } else {
                                       yamlConfigurationxxxx.set("locktime", true);
                                       yamlConfigurationxxxx.set("time", pxx.getWorld().getTime());

                                       try {
                                          yamlConfigurationxxxx.save(f2xxxxxxxxx);
                                       } catch (IOException var118) {
                                          var118.printStackTrace();
                                       }

                                       String tempxxxxxxxxxxxxxxxxxxx = Variable.Lang_YML.getString("TimeLocked");
                                       sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                                       sender.sendMessage(tempxxxxxxxxxxxxxxxxxxx);
                                       sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
                                    }
                                 }
                              } else {
                                 String tempxxxxxxxxxxxxxxxxxxx = Variable.Lang_YML.getString("NoOwnerAndManagerPermission");
                                 sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                                 sender.sendMessage(tempxxxxxxxxxxxxxxxxxxx);
                                 sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
                              }

                              return false;
                           }
                        } else if (args.length == 1 && args[0].equalsIgnoreCase("lockweather")) {
                           if (!this.hasRequiredHomeLevel(pxx, Main.JavaPlugin.getConfig().getInt("FeatureUnlock.LockWeatherLevel", 3), "锁定天气")) {
                              return false;
                           } else {
                              File f2xxxxxxxxx = new File(Variable.Tempf, Util.getBaseHomeName(pxx.getWorld().getName()) + ".yml");
                              if (Util.CheckOwnerAndManagerAndOP(pxx, Util.getBaseHomeName(pxx.getWorld().getName()))) {
                                 if (Variable.bungee) {
                                    if (MySQL.getlockweather(pxx.getWorld().getName().replace(Variable.world_prefix, "")).equalsIgnoreCase("true")) {
                                       MySQL.setlockweather(pxx.getWorld().getName().replace(Variable.world_prefix, ""), "false");
                                       String tempxxxxxxxxxxxxxxxxxxx = Variable.Lang_YML.getString("WeatherUnLocked");
                                       sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                                       sender.sendMessage(tempxxxxxxxxxxxxxxxxxxx);
                                       sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
                                    } else {
                                       MySQL.setlockweather(pxx.getWorld().getName().replace(Variable.world_prefix, ""), "true");
                                       String tempxxxxxxxxxxxxxxxxxxx = Variable.Lang_YML.getString("WeatherLocked");
                                       sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                                       sender.sendMessage(tempxxxxxxxxxxxxxxxxxxx);
                                       sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
                                    }
                                 } else {
                                    YamlConfiguration yamlConfigurationxxxx = YamlConfiguration.loadConfiguration(f2xxxxxxxxx);
                                    if (yamlConfigurationxxxx.getBoolean("lockweather")) {
                                       yamlConfigurationxxxx.set("lockweather", false);

                                       try {
                                          yamlConfigurationxxxx.save(f2xxxxxxxxx);
                                       } catch (IOException var121) {
                                          var121.printStackTrace();
                                       }

                                       String tempxxxxxxxxxxxxxxxxxxx = Variable.Lang_YML.getString("WeatherUnLocked");
                                       sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                                       sender.sendMessage(tempxxxxxxxxxxxxxxxxxxx);
                                       sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
                                    } else {
                                       yamlConfigurationxxxx.set("lockweather", true);

                                       try {
                                          yamlConfigurationxxxx.save(f2xxxxxxxxx);
                                       } catch (IOException var120) {
                                          var120.printStackTrace();
                                       }

                                       String tempxxxxxxxxxxxxxxxxxxx = Variable.Lang_YML.getString("WeatherLocked");
                                       sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                                       sender.sendMessage(tempxxxxxxxxxxxxxxxxxxx);
                                       sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
                                    }
                                 }
                              } else {
                                 String tempxxxxxxxxxxxxxxxxxxx = Variable.Lang_YML.getString("NoOwnerAndManagerPermission");
                                 sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                                 sender.sendMessage(tempxxxxxxxxxxxxxxxxxxx);
                                 sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
                              }

                              return false;
                           }
                        } else if (args.length == 1 && args[0].equalsIgnoreCase("day")) {
                           if (!this.hasRequiredHomeLevel(pxx, Main.JavaPlugin.getConfig().getInt("FeatureUnlock.LockTimeLevel", 3), "时间控制")) {
                              return false;
                           } else {
                              if (Util.CheckOwnerAndManagerAndOP(pxx, Util.getBaseHomeName(pxx.getWorld().getName()))) {
                                 if (HomeAPI.getHome(pxx.getWorld().getName()).isLocktime()) {
                                    try {
                                       HomeAPI.getHome(pxx.getWorld().getName()).setLocktime(false);
                                    } catch (IOException var122) {
                                       var122.printStackTrace();
                                    }
                                 }

                                 pxx.sendMessage(Variable.Lang_YML.getString("TimeDay"));
                                 pxx.getWorld().setTime(0L);
                              } else {
                                 String tempxxxxxxxxxxxxxxxxxxx = Variable.Lang_YML.getString("NoOwnerAndManagerPermission");
                                 sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                                 sender.sendMessage(tempxxxxxxxxxxxxxxxxxxx);
                                 sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
                              }

                              return false;
                           }
                        } else if (args.length == 1 && args[0].equalsIgnoreCase("sun")) {
                           if (!this.hasRequiredHomeLevel(pxx, Main.JavaPlugin.getConfig().getInt("FeatureUnlock.LockWeatherLevel", 3), "天气控制")) {
                              return false;
                           } else {
                              if (Util.CheckOwnerAndManagerAndOP(pxx, Util.getBaseHomeName(pxx.getWorld().getName()))) {
                                 if (HomeAPI.getHome(pxx.getWorld().getName()).isLockweather()) {
                                    try {
                                       HomeAPI.getHome(pxx.getWorld().getName()).setLockweather(false);
                                    } catch (IOException var123) {
                                       var123.printStackTrace();
                                    }
                                 }

                                 pxx.sendMessage(Variable.Lang_YML.getString("WeatherSun"));
                                 pxx.getWorld().setStorm(false);
                                 pxx.getWorld().setThundering(false);
                              } else {
                                 String tempxxxxxxxxxxxxxxxxxxx = Variable.Lang_YML.getString("NoOwnerAndManagerPermission");
                                 sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                                 sender.sendMessage(tempxxxxxxxxxxxxxxxxxxx);
                                 sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
                              }

                              return false;
                           }
                        } else if (args.length == 1 && args[0].equalsIgnoreCase("togglecc")) {
                           if (Util.CheckOwnerAndManagerAndOP(pxx, Util.getBaseHomeName(pxx.getWorld().getName()))) {
                              String Languagex = Variable.Lang_YML.getString("NoPermissionCheck");
                              if (Languagex.contains("<Permission>")) {
                                 Languagex = Languagex.replace("<Permission>", "ErrorTown.Togglecc");
                              }

                              if (!com.Util.Perm.has(pxx, "ErrorTown.Togglecc")) {
                                 sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                                 sender.sendMessage(Languagex);
                                 sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
                                 return false;
                              }

                              WBControl.togglecc(pxx);
                           } else {
                              String tempxxxxxxxxxxxxxxxxxxx = Variable.Lang_YML.getString("NoOwnerAndManagerPermission");
                              sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                              sender.sendMessage(tempxxxxxxxxxxxxxxxxxxx);
                              sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
                           }

                           return false;
                        } else if (args.length == 1 && args[0].equalsIgnoreCase("nether")) {
                           if (!Main.JavaPlugin.getConfig().getBoolean("EnableNetherTeleport")) {
                              pxx.sendMessage(Variable.Lang_YML.getString("NoOpenNetherTeleport"));
                              return false;
                           } else {
                              String Languagexx = Variable.Lang_YML.getString("NoPermissionCheck");
                              if (Languagexx.contains("<Permission>")) {
                                 Languagexx = Languagexx.replace("<Permission>", "ErrorTown.Nether");
                              }

                              if (!Main.JavaPlugin.getConfig().getBoolean("Permission.Nether") && !com.Util.Perm.has(pxx, "ErrorTown.Nether")) {
                                 sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                                 sender.sendMessage(Languagexx);
                                 sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
                                 return false;
                              } else {
                                 World worldxxxx = Bukkit.getWorld(Main.JavaPlugin.getConfig().getString("NetherWorldName"));
                                 if (worldxxxx == null) {
                                    WorldCreator creator = new WorldCreator(Main.JavaPlugin.getConfig().getString("NetherWorldName"));
                                    Variable.create_list_home.add(Main.JavaPlugin.getConfig().getString("NetherWorldName"));
                                    Bukkit.createWorld(creator);
                                 }

                                 worldxxxx = Bukkit.getWorld(Main.JavaPlugin.getConfig().getString("NetherWorldName"));

                                 for (int ixxxxxxxxxxxxxxxxxxx = 0;
                                    ixxxxxxxxxxxxxxxxxxx < Main.JavaPlugin.getConfig().getStringList("NeitherGameRules").size();
                                    ixxxxxxxxxxxxxxxxxxx++
                                 ) {
                                    String[] tempxxxxxxxxxxxxxxxxxxx = ((String)Main.JavaPlugin
                                          .getConfig()
                                          .getStringList("NeitherGameRules")
                                          .get(ixxxxxxxxxxxxxxxxxxx))
                                       .split(",");
                                    Platform.setGameRule(worldxxxx, tempxxxxxxxxxxxxxxxxxxx[0], tempxxxxxxxxxxxxxxxxxxx[1]);
                                 }

                                 pxx.teleport(worldxxxx.getSpawnLocation());
                                 return false;
                              }
                           }
                        } else if (args.length == 1 && args[0].equalsIgnoreCase("end")) {
                           if (!Main.JavaPlugin.getConfig().getBoolean("EnableEndTeleport")) {
                              pxx.sendMessage(Variable.Lang_YML.getString("NoOpenEndTeleport"));
                              return false;
                           } else {
                              String Languagexxx = Variable.Lang_YML.getString("NoPermissionCheck");
                              if (Languagexxx.contains("<Permission>")) {
                                 Languagexxx = Languagexxx.replace("<Permission>", "ErrorTown.End");
                              }

                              if (!Main.JavaPlugin.getConfig().getBoolean("Permission.End") && !com.Util.Perm.has(pxx, "ErrorTown.End")) {
                                 sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                                 sender.sendMessage(Languagexxx);
                                 sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
                                 return false;
                              } else {
                                 World worldxxxx = Bukkit.getWorld(Main.JavaPlugin.getConfig().getString("EndWorldName"));
                                 if (worldxxxx == null) {
                                    WorldCreator creator = new WorldCreator(Main.JavaPlugin.getConfig().getString("EndWorldName"));
                                    Variable.create_list_home.add(Main.JavaPlugin.getConfig().getString("EndWorldName"));
                                    Bukkit.createWorld(creator);
                                 }

                                 worldxxxx = Bukkit.getWorld(Main.JavaPlugin.getConfig().getString("EndWorldName"));

                                 for (int ixxxxxxxxxxxxxxxxxxx = 0;
                                    ixxxxxxxxxxxxxxxxxxx < Main.JavaPlugin.getConfig().getStringList("EndGameRules").size();
                                    ixxxxxxxxxxxxxxxxxxx++
                                 ) {
                                    String[] tempxxxxxxxxxxxxxxxxxxx = ((String)Main.JavaPlugin
                                          .getConfig()
                                          .getStringList("EndGameRules")
                                          .get(ixxxxxxxxxxxxxxxxxxx))
                                       .split(",");
                                    Platform.setGameRule(worldxxxx, tempxxxxxxxxxxxxxxxxxxx[0], tempxxxxxxxxxxxxxxxxxxx[1]);
                                 }

                                 pxx.teleport(worldxxxx.getSpawnLocation());
                                 return false;
                              }
                           }
                        } else if (args.length == 1 && args[0].equalsIgnoreCase("rain")) {
                           if (!this.hasRequiredHomeLevel(pxx, Main.JavaPlugin.getConfig().getInt("FeatureUnlock.LockWeatherLevel", 3), "天气控制")) {
                              return false;
                           } else {
                              if (Util.CheckOwnerAndManagerAndOP(pxx, Util.getBaseHomeName(pxx.getWorld().getName()))) {
                                 if (HomeAPI.getHome(pxx.getWorld().getName()).isLockweather()) {
                                    try {
                                       HomeAPI.getHome(pxx.getWorld().getName()).setLockweather(false);
                                    } catch (IOException var124) {
                                       var124.printStackTrace();
                                    }
                                 }

                                 pxx.sendMessage(Variable.Lang_YML.getString("WeatherRain"));
                                 pxx.getWorld().setStorm(true);
                              } else {
                                 String tempxxxxxxxxxxxxxxxxxxx = Variable.Lang_YML.getString("NoOwnerAndManagerPermission");
                                 sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                                 sender.sendMessage(tempxxxxxxxxxxxxxxxxxxx);
                                 sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
                              }

                              return false;
                           }
                        } else if (args.length == 1 && args[0].equalsIgnoreCase("seed")) {
                           if (Util.CheckOwnerAndManagerAndOP(pxx, Util.getBaseHomeName(pxx.getWorld().getName()))) {
                              String Languagexxxx = Variable.Lang_YML.getString("NoPermissionCheck");
                              if (Languagexxxx.contains("<Permission>")) {
                                 Languagexxxx = Languagexxxx.replace("<Permission>", "ErrorTown.Seed");
                              }

                              if (!com.Util.Perm.has(pxx, "ErrorTown.Seed")) {
                                 sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                                 sender.sendMessage(Languagexxxx);
                                 sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
                                 return false;
                              }

                              String messagex = Variable.Lang_YML.getString("LookSeed");
                              if (messagex.contains("<Seed>")) {
                                 messagex = messagex.replace("<Seed>", String.valueOf(pxx.getWorld().getSeed()));
                              }

                              pxx.sendMessage(messagex);
                           } else {
                              String tempxxxxxxxxxxxxxxxxxxx = Variable.Lang_YML.getString("NoOwnerAndManagerPermission");
                              sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                              sender.sendMessage(tempxxxxxxxxxxxxxxxxxxx);
                              sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
                           }

                           return false;
                        } else if (args.length == 2 && args[0].equalsIgnoreCase("fly")) {
                           if (Util.CheckOwnerAndManagerAndOP(pxx, Util.getBaseHomeName(pxx.getWorld().getName()))) {
                              String Languagexxxxx = Variable.Lang_YML.getString("NoPermissionCheck");
                              if (Languagexxxxx.contains("<Permission>")) {
                                 Languagexxxxx = Languagexxxxx.replace("<Permission>", "ErrorTown.Fly");
                              }

                              if (!com.Util.Perm.has(pxx, "ErrorTown.Fly")) {
                                 sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                                 sender.sendMessage(Languagexxxxx);
                                 sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
                                 return false;
                              }

                              if (args[1].equalsIgnoreCase("off")) {
                                 for (Player exx : pxx.getWorld().getPlayers()) {
                                    String messagex = Variable.Lang_YML.getString("DisableFly");
                                    exx.sendMessage(messagex);
                                    if (Variable.flying_list.containsKey(exx.getName())) {
                                       Variable.flying_list.remove(exx.getName());
                                    }

                                    if (exx.getAllowFlight()) {
                                       exx.setAllowFlight(false);
                                    }
                                 }
                              } else {
                                 for (Player exx : pxx.getWorld().getPlayers()) {
                                    String messagexx = Variable.Lang_YML.getString("EnableFly");
                                    exx.sendMessage(messagexx);
                                    if (!exx.getAllowFlight()) {
                                       if (!Variable.flying_list.containsKey(exx.getName())) {
                                          Variable.flying_list.put(exx.getName(), pxx.getWorld().getName());
                                       }

                                       exx.setAllowFlight(true);
                                    }
                                 }
                              }
                           } else {
                              String tempxxxxxxxxxxxxxxxxxxx = Variable.Lang_YML.getString("NoOwnerAndManagerPermission");
                              sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                              sender.sendMessage(tempxxxxxxxxxxxxxxxxxxx);
                              sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
                           }

                           return false;
                        } else if (args.length == 1 && args[0].equalsIgnoreCase("refresh")) {
                           Player psx = (Player)sender;
                           if (!com.Util.Perm.has(psx, "ErrorTown.Refresh")) {
                              psx.sendMessage("§8[§6家园§8] §c抱歉,缺乏权限无法使用>>>");
                              return false;
                           } else {
                              Util.refreshBorder(psx.getWorld());
                              psx.sendMessage("§8[§6家园§8] §c成功刷新当前家园边界>>>");
                              return false;
                           }
                        } else if (args.length == 1 && args[0].equalsIgnoreCase("night")) {
                           if (!this.hasRequiredHomeLevel(pxx, Main.JavaPlugin.getConfig().getInt("FeatureUnlock.LockTimeLevel", 3), "时间控制")) {
                              return false;
                           } else {
                              if (Util.CheckOwnerAndManagerAndOP(pxx, Util.getBaseHomeName(pxx.getWorld().getName()))) {
                                 if (HomeAPI.getHome(pxx.getWorld().getName()).isLocktime()) {
                                    try {
                                       HomeAPI.getHome(pxx.getWorld().getName()).setLocktime(false);
                                    } catch (IOException var125) {
                                       var125.printStackTrace();
                                    }
                                 }

                                 pxx.sendMessage(Variable.Lang_YML.getString("TimeNight"));
                                 pxx.getWorld().setTime(14000L);
                              } else {
                                 String tempxxxxxxxxxxxxxxxxxxx = Variable.Lang_YML.getString("NoOwnerAndManagerPermission");
                                 sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                                 sender.sendMessage(tempxxxxxxxxxxxxxxxxxxx);
                                 sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
                              }

                              return false;
                           }
                        } else if (args.length == 2 && (args[0].equalsIgnoreCase("trust") || args[0].equalsIgnoreCase("t") || args[0].equalsIgnoreCase("add"))) {
                           String Name = args[1];
                           if (!Main.JavaPlugin.getConfig().getBoolean("Permission.CommandUser")
                              && !com.Util.Perm.has(pxx, "ErrorTown.Trust")
                              && !com.Util.Perm.has(pxx, "ErrorTown.command.user")) {
                              String tip = Variable.Lang_YML.getString("NoPermissionCheck");
                              if (tip.contains("<Permission>")) {
                                 tip = tip.replace("<Permission>", "ErrorTown.Trust");
                              }

                              pxx.sendMessage(tip);
                              return false;
                           } else {
                              File f2xxxxxxxxx = new File(Variable.Tempf, Util.getBaseHomeName(pxx.getWorld().getName()) + ".yml");
                              if (!Util.CheckOwnerAndManagerAndOP(pxx, Util.getBaseHomeName(pxx.getWorld().getName()))) {
                                 String tempxxxxxxxxxxxxxxxxxxx = Variable.Lang_YML.getString("NoOwnerAndManagerPermission");
                                 sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                                 sender.sendMessage(tempxxxxxxxxxxxxxxxxxxx);
                                 sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
                                 return false;
                              } else if (args[1].equalsIgnoreCase(pxx.getName())) {
                                 String tempxxxxxxxxxxxxxxxxxxx = Variable.Lang_YML.getString("AddOwnerToTrust");
                                 sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                                 sender.sendMessage(tempxxxxxxxxxxxxxxxxxxx);
                                 sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
                                 return false;
                              } else {
                                 if (Variable.bungee) {
                                    List<String> blacklistxxx = MySQL.getDenys(pxx.getWorld().getName().replace(Variable.world_prefix, ""));
                                    if (blacklistxxx == null) {
                                       blacklistxxx = new ArrayList<>();
                                    }

                                    for (int ixxxxxxxxxxxxxxxxxxx = 0; ixxxxxxxxxxxxxxxxxxx < blacklistxxx.size(); ixxxxxxxxxxxxxxxxxxx++) {
                                       if (blacklistxxx.get(ixxxxxxxxxxxxxxxxxxx).equalsIgnoreCase(args[1])) {
                                          String tempxxxxxxxxxxxxxxxxxxx = Variable.Lang_YML.getString("HasAlreadyInBlack");
                                          sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                                          sender.sendMessage(tempxxxxxxxxxxxxxxxxxxx);
                                          sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
                                          return false;
                                       }
                                    }

                                    List<String> save = MySQL.getMembers(pxx.getWorld().getName().replace(Variable.world_prefix, ""));
                                    if (save == null) {
                                       save = new ArrayList<>();
                                    }

                                    Boolean CheckSame = false;

                                    for (int ixxxxxxxxxxxxxxxxxxxx = 0; ixxxxxxxxxxxxxxxxxxxx < save.size(); ixxxxxxxxxxxxxxxxxxxx++) {
                                       if (save.get(ixxxxxxxxxxxxxxxxxxxx).equalsIgnoreCase(Name)) {
                                          String tempxxxxxxxxxxxxxxxxxxx = Variable.Lang_YML.getString("HasAlreadyTrust");
                                          CheckSame = true;
                                          sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                                          sender.sendMessage(tempxxxxxxxxxxxxxxxxxxx);
                                          sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
                                       }
                                    }

                                    if (!CheckSame) {
                                       int max_playerxx = this.getEffectiveHomeJoinLimit(pxx, pxx.getWorld().getName().replace(Variable.world_prefix, ""));
                                       if (save.size() >= max_playerxx) {
                                          String tempxxxxxxxxxxxxxxxxxxx = Variable.Lang_YML.getString("MaxJoinMembers");
                                          if (tempxxxxxxxxxxxxxxxxxxx.contains("<MaxAmount>")) {
                                             tempxxxxxxxxxxxxxxxxxxx = tempxxxxxxxxxxxxxxxxxxx.replace("<MaxAmount>", String.valueOf(max_playerxx));
                                          }

                                          sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                                          sender.sendMessage(tempxxxxxxxxxxxxxxxxxxx);
                                          sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
                                          return false;
                                       }

                                       String resultx = MySQL.getListStringSpiltByDot(
                                          MySQL.getMembers(pxx.getWorld().getName().replace(Variable.world_prefix, ""))
                                       );
                                       if (resultx != null && !resultx.equalsIgnoreCase("")) {
                                          resultx = resultx + "," + Name;
                                       } else {
                                          resultx = Name;
                                       }

                                       MySQL.setMembers(pxx.getWorld().getName().replace(Variable.world_prefix, ""), resultx);
                                       String tempxxxxxxxxxxxxxxxxxxx = Variable.Lang_YML.getString("AddTrustSuccess");
                                       if (tempxxxxxxxxxxxxxxxxxxx.contains("<Name>")) {
                                          tempxxxxxxxxxxxxxxxxxxx = tempxxxxxxxxxxxxxxxxxxx.replace("<Name>", Name);
                                       }

                                       sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                                       sender.sendMessage(tempxxxxxxxxxxxxxxxxxxx);
                                       sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
                                    }
                                 } else {
                                    YamlConfiguration yamlConfigurationxxxx = YamlConfiguration.loadConfiguration(f2xxxxxxxxx);
                                    List<String> blacklistxxxx = yamlConfigurationxxxx.getStringList("Denys");
                                    if (blacklistxxxx == null) {
                                       blacklistxxxx = new ArrayList<>();
                                    }

                                    for (int ixxxxxxxxxxxxxxxxxxxxx = 0; ixxxxxxxxxxxxxxxxxxxxx < blacklistxxxx.size(); ixxxxxxxxxxxxxxxxxxxxx++) {
                                       if (blacklistxxxx.get(ixxxxxxxxxxxxxxxxxxxxx).equalsIgnoreCase(args[1])) {
                                          String tempxxxxxxxxxxxxxxxxxxx = Variable.Lang_YML.getString("HasAlreadyInBlack");
                                          sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                                          sender.sendMessage(tempxxxxxxxxxxxxxxxxxxx);
                                          sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
                                          return false;
                                       }
                                    }

                                    List<String> savex = yamlConfigurationxxxx.getStringList("Members");
                                    if (savex == null) {
                                       savex = new ArrayList<>();
                                    }

                                    Boolean CheckSame = false;

                                    for (int ixxxxxxxxxxxxxxxxxxxxxx = 0; ixxxxxxxxxxxxxxxxxxxxxx < savex.size(); ixxxxxxxxxxxxxxxxxxxxxx++) {
                                       if (savex.get(ixxxxxxxxxxxxxxxxxxxxxx).equalsIgnoreCase(Name)) {
                                          String tempxxxxxxxxxxxxxxxxxxx = Variable.Lang_YML.getString("HasAlreadyTrust");
                                          CheckSame = true;
                                          sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                                          sender.sendMessage(tempxxxxxxxxxxxxxxxxxxx);
                                          sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
                                       }
                                    }

                                    if (!CheckSame) {
                                       int max_playerxxx = this.getEffectiveHomeJoinLimit(pxx, Util.getBaseHomeName(pxx.getWorld().getName()));
                                       if (savex.size() >= max_playerxxx) {
                                          String tempxxxxxxxxxxxxxxxxxxx = Variable.Lang_YML.getString("MaxJoinMembers");
                                          if (tempxxxxxxxxxxxxxxxxxxx.contains("<MaxAmount>")) {
                                             tempxxxxxxxxxxxxxxxxxxx = tempxxxxxxxxxxxxxxxxxxx.replace("<MaxAmount>", String.valueOf(max_playerxxx));
                                          }

                                          sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                                          sender.sendMessage(tempxxxxxxxxxxxxxxxxxxx);
                                          sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
                                          return false;
                                       }

                                       savex.add(Name);
                                       yamlConfigurationxxxx.set("Members", savex);

                                       try {
                                          yamlConfigurationxxxx.save(f2xxxxxxxxx);
                                       } catch (IOException var126) {
                                          var126.printStackTrace();
                                       }

                                       String tempxxxxxxxxxxxxxxxxxxx = Variable.Lang_YML.getString("AddTrustSuccess");
                                       if (tempxxxxxxxxxxxxxxxxxxx.contains("<Name>")) {
                                          tempxxxxxxxxxxxxxxxxxxx = tempxxxxxxxxxxxxxxxxxxx.replace("<Name>", Name);
                                       }

                                       sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                                       sender.sendMessage(tempxxxxxxxxxxxxxxxxxxx);
                                       sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
                                    }
                                 }

                                 return false;
                              }
                           }
                        } else if (args.length == 2 && args[0].equalsIgnoreCase("Deny")) {
                           String Name = args[1];
                           if (!Main.JavaPlugin.getConfig().getBoolean("Permission.CommandUser")
                              && !com.Util.Perm.has(pxx, "ErrorTown.Deny")
                              && !com.Util.Perm.has(pxx, "ErrorTown.command.user")) {
                              String tip = Variable.Lang_YML.getString("NoPermissionCheck");
                              if (tip.contains("<Permission>")) {
                                 tip = tip.replace("<Permission>", "ErrorTown.Deny");
                              }

                              pxx.sendMessage(tip);
                              return false;
                           } else {
                              File f2xxxxxxxxx = new File(Variable.Tempf, Util.getBaseHomeName(pxx.getWorld().getName()) + ".yml");
                              if (!Util.CheckOwnerAndManagerAndOP(pxx, Util.getBaseHomeName(pxx.getWorld().getName()))) {
                                 String tempxxxxxxxxxxxxxxxxxxx = Variable.Lang_YML.getString("NoOwnerPermission");
                                 sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                                 sender.sendMessage(tempxxxxxxxxxxxxxxxxxxx);
                                 sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
                                 return false;
                              } else if (args[1].equalsIgnoreCase(pxx.getName())) {
                                 String tempxxxxxxxxxxxxxxxxxxx = Variable.Lang_YML.getString("AddOwnerToBlack");
                                 sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                                 sender.sendMessage(tempxxxxxxxxxxxxxxxxxxx);
                                 sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
                                 return false;
                              } else {
                                 if (Variable.bungee) {
                                    List<String> trustlist = MySQL.getMembers(pxx.getWorld().getName().replace(Variable.world_prefix, ""));
                                    if (trustlist == null) {
                                       trustlist = new ArrayList<>();
                                    }

                                    for (int ixxxxxxxxxxxxxxxxxxxxxxx = 0; ixxxxxxxxxxxxxxxxxxxxxxx < trustlist.size(); ixxxxxxxxxxxxxxxxxxxxxxx++) {
                                       if (trustlist.get(ixxxxxxxxxxxxxxxxxxxxxxx).equalsIgnoreCase(args[1])) {
                                          String tempxxxxxxxxxxxxxxxxxxx = Variable.Lang_YML.getString("HasInTrust");
                                          sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                                          sender.sendMessage(tempxxxxxxxxxxxxxxxxxxx);
                                          sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
                                          return false;
                                       }
                                    }

                                    List<String> oplist = MySQL.getOP(pxx.getWorld().getName().replace(Variable.world_prefix, ""));
                                    if (oplist == null) {
                                       oplist = new ArrayList<>();
                                    }

                                    for (int ixxxxxxxxxxxxxxxxxxxxxxxx = 0; ixxxxxxxxxxxxxxxxxxxxxxxx < oplist.size(); ixxxxxxxxxxxxxxxxxxxxxxxx++) {
                                       if (oplist.get(ixxxxxxxxxxxxxxxxxxxxxxxx).equalsIgnoreCase(args[1])) {
                                          String tempxxxxxxxxxxxxxxxxxxx = Variable.Lang_YML.getString("HasInManager");
                                          sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                                          sender.sendMessage(tempxxxxxxxxxxxxxxxxxxx);
                                          sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
                                          return false;
                                       }
                                    }

                                    List<String> savexx = MySQL.getDenys(pxx.getWorld().getName().replace(Variable.world_prefix, ""));
                                    if (savexx == null) {
                                       savexx = new ArrayList<>();
                                    }

                                    Boolean CheckSame = false;

                                    for (int ixxxxxxxxxxxxxxxxxxxxxxxxx = 0; ixxxxxxxxxxxxxxxxxxxxxxxxx < savexx.size(); ixxxxxxxxxxxxxxxxxxxxxxxxx++) {
                                       if (savexx.get(ixxxxxxxxxxxxxxxxxxxxxxxxx).equalsIgnoreCase(Name)) {
                                          CheckSame = true;
                                          String tempxxxxxxxxxxxxxxxxxxx = Variable.Lang_YML.getString("HasAlreadyExistBlack");
                                          sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                                          sender.sendMessage(tempxxxxxxxxxxxxxxxxxxx);
                                          sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
                                          return false;
                                       }
                                    }

                                    if (!CheckSame) {
                                       String resultxx = MySQL.getListStringSpiltByDot(
                                          MySQL.getDenys(pxx.getWorld().getName().replace(Variable.world_prefix, ""))
                                       );
                                       if (resultxx != null && !resultxx.equalsIgnoreCase("")) {
                                          resultxx = resultxx + "," + Name;
                                       } else {
                                          resultxx = Name;
                                       }

                                       MySQL.setDenys(pxx.getWorld().getName().replace(Variable.world_prefix, ""), resultxx);

                                       for (Player ptxx : Bukkit.getWorld(Variable.world_prefix + pxx.getName()).getPlayers()) {
                                          if (ptxx.getName().equalsIgnoreCase(Name)) {
                                             String tempxxxxxxxxxxxxxxxxxxx = Variable.Lang_YML.getString("BeKicked");
                                             ptxx.sendMessage("§a§l§m--------------" + Variable.Prefix + "§a§l§m--------------");
                                             ptxx.sendMessage(tempxxxxxxxxxxxxxxxxxxx);
                                             ptxx.sendMessage("§a§l§m--------------" + Variable.Prefix + "§a§l§m--------------");
                                             String bekicked = Main.JavaPlugin.getConfig().getString("BeKickedCommand");
                                             if (bekicked.contains("<Name>")) {
                                                bekicked = bekicked.replace("<Name>", ptxx.getName());
                                             }

                                             Bukkit.dispatchCommand(Bukkit.getConsoleSender(), bekicked);
                                          }
                                       }

                                       String tempxxxxxxxxxxxxxxxxxxx = Variable.Lang_YML.getString("AddBlackSuccess");
                                       if (tempxxxxxxxxxxxxxxxxxxx.contains("<Name>")) {
                                          tempxxxxxxxxxxxxxxxxxxx = tempxxxxxxxxxxxxxxxxxxx.replace("<Name>", Name);
                                       }

                                       sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                                       sender.sendMessage(tempxxxxxxxxxxxxxxxxxxx);
                                       sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
                                    }
                                 } else {
                                    YamlConfiguration yamlConfigurationxxxxx = YamlConfiguration.loadConfiguration(f2xxxxxxxxx);
                                    List<String> trustlistx = yamlConfigurationxxxxx.getStringList("Members");
                                    if (trustlistx == null) {
                                       trustlistx = new ArrayList<>();
                                    }

                                    for (int ixxxxxxxxxxxxxxxxxxxxxxxxxx = 0; ixxxxxxxxxxxxxxxxxxxxxxxxxx < trustlistx.size(); ixxxxxxxxxxxxxxxxxxxxxxxxxx++) {
                                       if (trustlistx.get(ixxxxxxxxxxxxxxxxxxxxxxxxxx).equalsIgnoreCase(args[1])) {
                                          String tempxxxxxxxxxxxxxxxxxxx = Variable.Lang_YML.getString("HasInTrust");
                                          sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                                          sender.sendMessage(tempxxxxxxxxxxxxxxxxxxx);
                                          sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
                                          return false;
                                       }
                                    }

                                    List<String> oplistx = yamlConfigurationxxxxx.getStringList("OP");
                                    if (oplistx == null) {
                                       oplistx = new ArrayList<>();
                                    }

                                    for (int ixxxxxxxxxxxxxxxxxxxxxxxxxxx = 0; ixxxxxxxxxxxxxxxxxxxxxxxxxxx < oplistx.size(); ixxxxxxxxxxxxxxxxxxxxxxxxxxx++) {
                                       if (oplistx.get(ixxxxxxxxxxxxxxxxxxxxxxxxxxx).equalsIgnoreCase(args[1])) {
                                          String tempxxxxxxxxxxxxxxxxxxx = Variable.Lang_YML.getString("HasInManager");
                                          sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                                          sender.sendMessage(tempxxxxxxxxxxxxxxxxxxx);
                                          sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
                                          return false;
                                       }
                                    }

                                    List<String> savexxx = yamlConfigurationxxxxx.getStringList("Denys");
                                    if (savexxx == null) {
                                       savexxx = new ArrayList<>();
                                    }

                                    Boolean CheckSame = false;

                                    for (int ixxxxxxxxxxxxxxxxxxxxxxxxxxxx = 0; ixxxxxxxxxxxxxxxxxxxxxxxxxxxx < savexxx.size(); ixxxxxxxxxxxxxxxxxxxxxxxxxxxx++) {
                                       if (savexxx.get(ixxxxxxxxxxxxxxxxxxxxxxxxxxxx).equalsIgnoreCase(Name)) {
                                          CheckSame = true;
                                          String tempxxxxxxxxxxxxxxxxxxx = Variable.Lang_YML.getString("HasAlreadyExistBlack");
                                          sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                                          sender.sendMessage(tempxxxxxxxxxxxxxxxxxxx);
                                          sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
                                          return false;
                                       }
                                    }

                                    if (!CheckSame) {
                                       savexxx.add(Name);
                                       yamlConfigurationxxxxx.set("Denys", savexxx);

                                       try {
                                          yamlConfigurationxxxxx.save(f2xxxxxxxxx);
                                       } catch (IOException var127) {
                                          var127.printStackTrace();
                                       }

                                       for (Player ptxxx : Bukkit.getWorld(Variable.world_prefix + pxx.getName()).getPlayers()) {
                                          if (ptxxx.getName().equalsIgnoreCase(Name)) {
                                             String tempxxxxxxxxxxxxxxxxxxx = Variable.Lang_YML.getString("BeKicked");
                                             ptxxx.sendMessage("§a§l§m--------------" + Variable.Prefix + "§a§l§m--------------");
                                             ptxxx.sendMessage(tempxxxxxxxxxxxxxxxxxxx);
                                             ptxxx.sendMessage("§a§l§m--------------" + Variable.Prefix + "§a§l§m--------------");
                                             String bekicked = Variable.Lang_YML.getString("BeKickedCommand");
                                             if (bekicked.contains("<Name>")) {
                                                bekicked = bekicked.replace("<Name>", ptxxx.getName());
                                             }

                                             Bukkit.dispatchCommand(Bukkit.getConsoleSender(), bekicked);
                                          }
                                       }

                                       String tempxxxxxxxxxxxxxxxxxxx = Variable.Lang_YML.getString("AddBlackSuccess");
                                       if (tempxxxxxxxxxxxxxxxxxxx.contains("<Name>")) {
                                          tempxxxxxxxxxxxxxxxxxxx = tempxxxxxxxxxxxxxxxxxxx.replace("<Name>", Name);
                                       }

                                       sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                                       sender.sendMessage(tempxxxxxxxxxxxxxxxxxxx);
                                       sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
                                    }
                                 }

                                 return false;
                              }
                           }
                        } else if (args.length == 2 && args[0].equalsIgnoreCase("UnDeny")) {
                           String Name = args[1];
                           File f2xxxxxxxxx = new File(Variable.Tempf, Util.getBaseHomeName(pxx.getWorld().getName()) + ".yml");
                           if (!Util.CheckOwnerAndManagerAndOP(pxx, Util.getBaseHomeName(pxx.getWorld().getName()))) {
                              String tempxxxxxxxxxxxxxxxxxxx = Variable.Lang_YML.getString("NoOwnerAndManagerPermission");
                              sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                              sender.sendMessage(tempxxxxxxxxxxxxxxxxxxx);
                              sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
                              return false;
                           } else {
                              if (Variable.bungee) {
                                 List<String> savexxxx = MySQL.getDenys(pxx.getWorld().getName().replace(Variable.world_prefix, ""));
                                 Boolean Check = false;
                                 if (savexxxx == null) {
                                    savexxxx = new ArrayList<>();
                                 }

                                 for (int ixxxxxxxxxxxxxxxxxxxxxxxxxxxxx = 0;
                                    ixxxxxxxxxxxxxxxxxxxxxxxxxxxxx < savexxxx.size();
                                    ixxxxxxxxxxxxxxxxxxxxxxxxxxxxx++
                                 ) {
                                    if (savexxxx.get(ixxxxxxxxxxxxxxxxxxxxxxxxxxxxx).equalsIgnoreCase(Name)) {
                                       Check = true;
                                    }
                                 }

                                 if (Check) {
                                    for (int ixxxxxxxxxxxxxxxxxxxxxxxxxxxxxx = 0;
                                       ixxxxxxxxxxxxxxxxxxxxxxxxxxxxxx < savexxxx.size();
                                       ixxxxxxxxxxxxxxxxxxxxxxxxxxxxxx++
                                    ) {
                                       if (savexxxx.get(ixxxxxxxxxxxxxxxxxxxxxxxxxxxxxx).equalsIgnoreCase(Name)) {
                                          String resultxxx = MySQL.getListStringSpiltByDot(
                                             MySQL.getDenys(pxx.getWorld().getName().replace(Variable.world_prefix, ""))
                                          );
                                           resultxxx = CsvUtil.remove(resultxxx, savexxxx.get(ixxxxxxxxxxxxxxxxxxxxxxxxxxxxxx));
                                           MySQL.setDenys(pxx.getWorld().getName().replace(Variable.world_prefix, ""), resultxxx);
                                          String tempxxxxxxxxxxxxxxxxxxx = Variable.Lang_YML.getString("RemoveBlackSuccess");
                                          if (tempxxxxxxxxxxxxxxxxxxx.contains("<Name>")) {
                                             tempxxxxxxxxxxxxxxxxxxx = tempxxxxxxxxxxxxxxxxxxx.replace("<Name>", Name);
                                          }

                                          sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                                          sender.sendMessage(tempxxxxxxxxxxxxxxxxxxx);
                                          sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
                                       }
                                    }
                                 } else {
                                    String tempxxxxxxxxxxxxxxxxxxx = Variable.Lang_YML.getString("NoBlackExist");
                                    sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                                    sender.sendMessage(tempxxxxxxxxxxxxxxxxxxx);
                                    sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
                                 }
                              } else {
                                 YamlConfiguration yamlConfigurationxxxxxx = YamlConfiguration.loadConfiguration(f2xxxxxxxxx);
                                 List<String> savexxxxx = yamlConfigurationxxxxxx.getStringList("Denys");
                                 Boolean Checkx = false;
                                 if (savexxxxx == null) {
                                    savexxxxx = new ArrayList<>();
                                 }

                                 for (int ixxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx = 0;
                                    ixxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx < savexxxxx.size();
                                    ixxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx++
                                 ) {
                                    if (savexxxxx.get(ixxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx).equalsIgnoreCase(Name)) {
                                       Checkx = true;
                                    }
                                 }

                                 if (Checkx) {
                                    for (int ixxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx = 0;
                                       ixxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx < savexxxxx.size();
                                       ixxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx++
                                    ) {
                                       if (savexxxxx.get(ixxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx).equalsIgnoreCase(Name)) {
                                          savexxxxx.remove(ixxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx);
                                          yamlConfigurationxxxxxx.set("Denys", savexxxxx);

                                          try {
                                             yamlConfigurationxxxxxx.save(f2xxxxxxxxx);
                                          } catch (IOException var128) {
                                             var128.printStackTrace();
                                          }

                                          String tempxxxxxxxxxxxxxxxxxxx = Variable.Lang_YML.getString("RemoveBlackSuccess");
                                          if (tempxxxxxxxxxxxxxxxxxxx.contains("<Name>")) {
                                             tempxxxxxxxxxxxxxxxxxxx = tempxxxxxxxxxxxxxxxxxxx.replace("<Name>", Name);
                                          }

                                          sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                                          sender.sendMessage(tempxxxxxxxxxxxxxxxxxxx);
                                          sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
                                       }
                                    }
                                 } else {
                                    String tempxxxxxxxxxxxxxxxxxxx = Variable.Lang_YML.getString("NoBlackExist");
                                    sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                                    sender.sendMessage(tempxxxxxxxxxxxxxxxxxxx);
                                    sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
                                 }
                              }

                              return false;
                           }
                        } else if (args.length == 2
                           && (args[0].equalsIgnoreCase("remove") || args[0].equalsIgnoreCase("delete") || args[0].equalsIgnoreCase("del"))) {
                           String Name = args[1];
                           File f2xxxxxxxxx = new File(Variable.Tempf, Util.getBaseHomeName(pxx.getWorld().getName()) + ".yml");
                           if (!Util.CheckOwnerAndManagerAndOP(pxx, Util.getBaseHomeName(pxx.getWorld().getName()))) {
                              String tempxxxxxxxxxxxxxxxxxxx = Variable.Lang_YML.getString("NoOwnerAndManagerPermission");
                              sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                              sender.sendMessage(tempxxxxxxxxxxxxxxxxxxx);
                              sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
                              return false;
                           } else {
                              if (Variable.bungee) {
                                 List<String> savexxxxxx = MySQL.getMembers(pxx.getWorld().getName().replace(Variable.world_prefix, ""));
                                 Boolean Checkxx = false;
                                 if (savexxxxxx == null) {
                                    savexxxxxx = new ArrayList<>();
                                 }

                                 for (int ixxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx = 0;
                                    ixxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx < savexxxxxx.size();
                                    ixxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx++
                                 ) {
                                    if (savexxxxxx.get(ixxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx).equalsIgnoreCase(Name)) {
                                       Checkxx = true;
                                    }
                                 }

                                 if (Checkxx) {
                                    for (int ixxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx = 0;
                                       ixxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx < savexxxxxx.size();
                                       ixxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx++
                                    ) {
                                       if (savexxxxxx.get(ixxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx).equalsIgnoreCase(Name)) {
                                          String resultxxx = MySQL.getListStringSpiltByDot(
                                             MySQL.getMembers(pxx.getWorld().getName().replace(Variable.world_prefix, ""))
                                          );
                                           resultxxx = CsvUtil.remove(resultxxx, savexxxxxx.get(ixxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx));
                                           MySQL.setMembers(pxx.getWorld().getName().replace(Variable.world_prefix, ""), resultxxx);
                                          String tempxxxxxxxxxxxxxxxxxxx = Variable.Lang_YML.getString("RemoveTrustPlayer");
                                          if (tempxxxxxxxxxxxxxxxxxxx.contains("<Name>")) {
                                             tempxxxxxxxxxxxxxxxxxxx = tempxxxxxxxxxxxxxxxxxxx.replace("<Name>", args[1]);
                                          }

                                          sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                                          sender.sendMessage(tempxxxxxxxxxxxxxxxxxxx);
                                          sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
                                       }
                                    }
                                 } else {
                                    String tempxxxxxxxxxxxxxxxxxxx = Variable.Lang_YML.getString("NoTrustExist");
                                    sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                                    sender.sendMessage(tempxxxxxxxxxxxxxxxxxxx);
                                    sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
                                 }
                              } else {
                                 YamlConfiguration yamlConfigurationxxxxxxx = YamlConfiguration.loadConfiguration(f2xxxxxxxxx);
                                 List<String> savexxxxxxx = yamlConfigurationxxxxxxx.getStringList("Members");
                                 Boolean Checkxxx = false;
                                 if (savexxxxxxx == null) {
                                    savexxxxxxx = new ArrayList<>();
                                 }

                                 for (int ixxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx = 0;
                                    ixxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx < savexxxxxxx.size();
                                    ixxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx++
                                 ) {
                                    if (savexxxxxxx.get(ixxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx).equalsIgnoreCase(Name)) {
                                       Checkxxx = true;
                                    }
                                 }

                                 if (Checkxxx) {
                                    for (int ixxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx = 0;
                                       ixxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx < savexxxxxxx.size();
                                       ixxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx++
                                    ) {
                                       if (savexxxxxxx.get(ixxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx).equalsIgnoreCase(Name)) {
                                          savexxxxxxx.remove(ixxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx);
                                          yamlConfigurationxxxxxxx.set("Members", savexxxxxxx);

                                          try {
                                             yamlConfigurationxxxxxxx.save(f2xxxxxxxxx);
                                          } catch (IOException var129) {
                                             var129.printStackTrace();
                                          }

                                          String tempxxxxxxxxxxxxxxxxxxx = Variable.Lang_YML.getString("RemoveTrustPlayer");
                                          if (tempxxxxxxxxxxxxxxxxxxx.contains("<Name>")) {
                                             tempxxxxxxxxxxxxxxxxxxx = tempxxxxxxxxxxxxxxxxxxx.replace("<Name>", args[1]);
                                          }

                                          sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                                          sender.sendMessage(tempxxxxxxxxxxxxxxxxxxx);
                                          sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
                                       }
                                    }
                                 } else {
                                    String tempxxxxxxxxxxxxxxxxxxx = Variable.Lang_YML.getString("NoTrustExist");
                                    sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                                    sender.sendMessage(tempxxxxxxxxxxxxxxxxxxx);
                                    sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
                                 }
                              }

                              return false;
                           }
                        } else if (args.length != 1 || !args[0].equalsIgnoreCase("quit") && !args[0].equalsIgnoreCase("q")) {
                           if (args.length == 2 && (args[0].equalsIgnoreCase("kick") || args[0].equalsIgnoreCase("k"))) {
                              String Name = args[1];
                               if (!pxx.getWorld()
                                  .getName()
                                  .replace(Variable.world_prefix, "")
                                  .equalsIgnoreCase(pxx.getName())) {
                                 String tempxxxxxxxxxxxxxxxxxxx = Variable.Lang_YML.getString("NoOwnerPermission");
                                 sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                                 sender.sendMessage(tempxxxxxxxxxxxxxxxxxxx);
                                 sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
                                 return false;
                              } else {
                                  if (Variable.bungee) {
                                     // The membership test must read the OP list of the home being
                                     // edited, not the executor's own home. Reading getOP(pxx.getName())
                                     // while writing setOP(currentWorld) let a visitor strip OPs from
                                     // somebody else's home.
                                     List<String> savexxxxxxxx = MySQL.getOP(pxx.getWorld().getName().replace(Variable.world_prefix, ""));
                                    Boolean Checkxxxx = false;
                                    if (savexxxxxxxx == null) {
                                       savexxxxxxxx = new ArrayList<>();
                                    }

                                    for (int ixxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx = 0;
                                       ixxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx < savexxxxxxxx.size();
                                       ixxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx++
                                    ) {
                                       if (savexxxxxxxx.get(ixxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx).equalsIgnoreCase(Name)) {
                                          Checkxxxx = true;
                                       }
                                    }

                                    if (Checkxxxx) {
                                       for (int ixxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx = 0;
                                          ixxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx < savexxxxxxxx.size();
                                          ixxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx++
                                       ) {
                                          if (savexxxxxxxx.get(ixxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx).equalsIgnoreCase(Name)) {
                                             String resultxxx = MySQL.getListStringSpiltByDot(
                                                MySQL.getOP(pxx.getWorld().getName().replace(Variable.world_prefix, ""))
                                             );
                                              resultxxx = CsvUtil.remove(resultxxx, Name);

                                              MySQL.setOP(pxx.getWorld().getName().replace(Variable.world_prefix, ""), resultxxx);
                                             Player ptxxxx = Bukkit.getPlayer(Name);
                                             if (ptxxxx != null
                                                && ptxxxx.getWorld().getName().replace(Variable.world_prefix, "").equalsIgnoreCase(pxx.getName())) {
                                                String bekicked = Main.JavaPlugin.getConfig().getString("BeKickedCommand");
                                                if (bekicked.contains("<Name>")) {
                                                   bekicked = bekicked.replace("<Name>", ptxxxx.getName());
                                                }

                                                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), bekicked);
                                                String tempxxxxxxxxxxxxxxxxxxx = Variable.Lang_YML.getString("BeKicked");
                                                ptxxxx.sendMessage("§a§l§m--------------" + Variable.Prefix + "§a§l§m--------------");
                                                ptxxxx.sendMessage(tempxxxxxxxxxxxxxxxxxxx);
                                                ptxxxx.sendMessage("§a§l§m--------------" + Variable.Prefix + "§a§l§m--------------");
                                             }

                                             String tempxxxxxxxxxxxxxxxxxxx = Variable.Lang_YML.getString("KickSuccess");
                                             if (tempxxxxxxxxxxxxxxxxxxx.contains("<Name>")) {
                                                tempxxxxxxxxxxxxxxxxxxx = tempxxxxxxxxxxxxxxxxxxx.replace("<Name>", Name);
                                             }

                                             sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                                             sender.sendMessage(tempxxxxxxxxxxxxxxxxxxx);
                                             sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
                                          }
                                       }
                                    } else {
                                       String tempxxxxxxxxxxxxxxxxxxx = Variable.Lang_YML.getString("KickNotExist");
                                       sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                                       sender.sendMessage(tempxxxxxxxxxxxxxxxxxxx);
                                       sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
                                    }
                                 } else {
                                    File f2xxxxxxxxx = new File(Variable.Tempf, pxx.getWorld().getName().replace(Variable.world_prefix, "") + ".yml");
                                    YamlConfiguration yamlConfigurationxxxxxxxx = YamlConfiguration.loadConfiguration(f2xxxxxxxxx);
                                    List<String> savexxxxxxxxx = yamlConfigurationxxxxxxxx.getStringList("OP");
                                    Boolean Checkxxxxx = false;
                                    if (savexxxxxxxxx == null) {
                                       savexxxxxxxxx = new ArrayList<>();
                                    }

                                    for (int ixxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx = 0;
                                       ixxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx < savexxxxxxxxx.size();
                                       ixxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx++
                                    ) {
                                       if (savexxxxxxxxx.get(ixxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx).equalsIgnoreCase(Name)) {
                                          Checkxxxxx = true;
                                       }
                                    }

                                    if (Checkxxxxx) {
                                       for (int ixxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx = 0;
                                          ixxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx < savexxxxxxxxx.size();
                                          ixxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx++
                                       ) {
                                          if (savexxxxxxxxx.get(ixxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx).equalsIgnoreCase(Name)) {
                                             savexxxxxxxxx.remove(ixxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx);
                                             yamlConfigurationxxxxxxxx.set("OP", savexxxxxxxxx);

                                             try {
                                                yamlConfigurationxxxxxxxx.save(f2xxxxxxxxx);
                                             } catch (IOException var131) {
                                                var131.printStackTrace();
                                             }

                                             Player ptxxxxx = Bukkit.getPlayer(Name);
                                             if (ptxxxxx != null
                                                && ptxxxxx.getWorld().getName().replace(Variable.world_prefix, "").equalsIgnoreCase(pxx.getName())) {
                                                String bekicked = Main.JavaPlugin.getConfig().getString("BeKickedCommand");
                                                if (bekicked.contains("<Name>")) {
                                                   bekicked = bekicked.replace("<Name>", ptxxxxx.getName());
                                                }

                                                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), bekicked);
                                                String tempxxxxxxxxxxxxxxxxxxx = Variable.Lang_YML.getString("BeKicked");
                                                ptxxxxx.sendMessage("§a§l§m--------------" + Variable.Prefix + "§a§l§m--------------");
                                                ptxxxxx.sendMessage(tempxxxxxxxxxxxxxxxxxxx);
                                                ptxxxxx.sendMessage("§a§l§m--------------" + Variable.Prefix + "§a§l§m--------------");
                                             }

                                             String tempxxxxxxxxxxxxxxxxxxx = Variable.Lang_YML.getString("KickSuccess");
                                             if (tempxxxxxxxxxxxxxxxxxxx.contains("<Name>")) {
                                                tempxxxxxxxxxxxxxxxxxxx = tempxxxxxxxxxxxxxxxxxxx.replace("<Name>", Name);
                                             }

                                             sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                                             sender.sendMessage(tempxxxxxxxxxxxxxxxxxxx);
                                             sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
                                          }
                                       }
                                    } else {
                                       String tempxxxxxxxxxxxxxxxxxxx = Variable.Lang_YML.getString("KickNotExist");
                                       sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                                       sender.sendMessage(tempxxxxxxxxxxxxxxxxxxx);
                                       sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
                                    }
                                 }

                                 return false;
                              }
                           } else if (args.length == 1 && args[0].equalsIgnoreCase("create")) {
                              if (args.length < 2 && Main.JavaPlugin.getConfig().getString("NormalType").equalsIgnoreCase("0")) {
                                 sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                                 String tempxxxxxxxxxxxxxxxxxxx = Variable.Lang_YML.getString("CreateHelp");
                                 sender.sendMessage(tempxxxxxxxxxxxxxxxxxxx);
                                 sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
                                 return false;
                              } else {
                                 if (args.length == 1 && !Main.JavaPlugin.getConfig().getString("NormalType").equalsIgnoreCase("0")) {
                                    Bukkit.dispatchCommand(sender, "sh create " + Main.JavaPlugin.getConfig().getString("NormalType"));
                                 }

                                 return false;
                              }
                           } else if (args.length == 2 && args[0].equalsIgnoreCase("create")) {
                              if (Main.JavaPlugin.getConfig().getBoolean("BungeeCord")) {
                                 if (MySQL.alreadyhastheplayerjoin(pxx.getName())) {
                                    String temp_BungeeCord = Variable.Lang_YML.getString("HasBeenJoin");
                                    if (temp_BungeeCord.contains("<ServerName>")) {
                                       temp_BungeeCord = temp_BungeeCord.replace("<ServerName>", MySQL.getJoinServer(pxx.getName()));
                                    }

                                    sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                                    sender.sendMessage(temp_BungeeCord);
                                    sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
                                    return false;
                                 }

                                 if (MySQL.alreadyhastheplayerhome(pxx.getName())) {
                                    String temp_BungeeCord = Variable.Lang_YML.getString("HasBeenCreate");
                                    if (temp_BungeeCord.contains("<ServerName>")) {
                                       temp_BungeeCord = temp_BungeeCord.replace("<ServerName>", MySQL.getServer(pxx.getName()));
                                    }

                                    sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                                    sender.sendMessage(temp_BungeeCord);
                                    sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
                                    return false;
                                 }
                              } else {
                                 String preparedHomeName = Variable.pendingCreateHomeName.get(pxx.getName());
                                 if (preparedHomeName == null || preparedHomeName.trim().isEmpty()) {
                                    List<String> ownedHomesx = HomeAPI.getOwnedHomes(pxx.getName());
                                    if (ownedHomesx.size() >= this.getMaxOwnedHomes()) {
                                       sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                                       sender.sendMessage(
                                          "§8[§6错误庄园§8] §c您当前已拥有 §e" + ownedHomesx.size() + " §c个庄园，最多只能拥有 §e" + this.getMaxOwnedHomes() + " §c个"
                                       );
                                       sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
                                       return false;
                                    }

                                    preparedHomeName = this.getNextOwnedHomeName(pxx);
                                    if (preparedHomeName == null) {
                                       sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                                       sender.sendMessage("§8[§6错误庄园§8] §c未找到可用的庄园编号槽位");
                                       sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
                                       return false;
                                    }

                                    Variable.pendingCreateHomeName.put(pxx.getName(), preparedHomeName);
                                 }
                              }

                               String createHomeName = Variable.bungee ? pxx.getName() : this.getCreateHomeName(pxx);
                               String createWorldName = Variable.world_prefix + createHomeName;
                               if (!Variable.bungee) {
                                 File f2xxxxxxxxxx = new File(Variable.Tempf, createHomeName + ".yml");
                                  if (f2xxxxxxxxxx.exists()) {
                                     if (Main.JavaPlugin.getConfig().getBoolean("HomeTerrain.Enabled", false)
                                         && HomeCreationCoordinator.isPending(createHomeName)) {
                                         HomeCreationQueue.CreationRequest request = HomeCreationCoordinator.currentRequest(createHomeName);
                                         if (request != null) {
                                            HomeCreationCoordinator.fail(Main.JavaPlugin, request, pxx, "家园数据已存在");
                                         }
                                     }
                                     sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                                    sender.sendMessage("§8[§6错误庄园§8] §c庄园数据已存在: §e" + createHomeName);
                                    sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
                                     return false;
                                  }
                               }
                               if (Main.JavaPlugin.getConfig().getBoolean("AutoReCreateInLowerLagHome")
                                 && !Variable.wait_to_command.containsKey(pxx.getName())
                                 && Main.JavaPlugin.getConfig().getBoolean("BungeeCord")) {
                                 if (Main.JavaPlugin.getConfig().getString("DecideBy").equalsIgnoreCase("Player")) {
                                    if (!MySQL.getLowerstLagServer().equalsIgnoreCase(Main.JavaPlugin.getConfig().getString("Server"))) {
                                       try {
                                          Channel.waitToCommand(pxx, MySQL.getLowerstLagServer(), "sh create " + args[1]);
                                       } catch (IOException var132) {
                                          var132.printStackTrace();
                                       }

                                       pxx.sendMessage(Variable.Lang_YML.getString("StartLowestLagServer"));
                                       Channel.sendPlayerToServer(pxx, MySQL.getLowerstLagServer());
                                       return false;
                                    }
                                 } else if (!MySQL.getHighestTPSServer().equalsIgnoreCase(Main.JavaPlugin.getConfig().getString("Server"))) {
                                    double nowxx = 0.0;
                                    if (Bukkit.getVersion().contains("1.7.10")) {
                                       nowxx = R1_7_10.getTps();
                                    } else {
                                       double se1 = Double.valueOf(PlaceholderAPI.setPlaceholders(null, "%server_tps_1%").replace("*", ""));
                                       double se2 = Double.valueOf(PlaceholderAPI.setPlaceholders(null, "%server_tps_5%").replace("*", ""));
                                       double se3 = Double.valueOf(PlaceholderAPI.setPlaceholders(null, "%server_tps_15%").replace("*", ""));
                                       nowxx = (se1 + se2 + se3) / 3.0;
                                    }

                                    if (MySQL.getServerAmount(MySQL.getLowerstLagServer()) != nowxx) {
                                       try {
                                          Channel.waitToCommand(pxx, MySQL.getHighestTPSServer(), "sh create " + args[1]);
                                       } catch (IOException var133) {
                                          var133.printStackTrace();
                                       }

                                       pxx.sendMessage(Variable.Lang_YML.getString("StartLowestLagServer"));
                                       Channel.sendPlayerToServer(pxx, MySQL.getHighestTPSServer());
                                       return false;
                                    }
                                 }
                              }

                              YamlConfiguration yamlConfigurationxxxxxxxxx = YamlConfiguration.loadConfiguration(Variable.f_log);
                              if (!yamlConfigurationxxxxxxxxx.contains("NowID")) {
                                 yamlConfigurationxxxxxxxxx.set("NowID", 0);
                              }

                              if (!yamlConfigurationxxxxxxxxx.contains("MaxID")) {
                                 yamlConfigurationxxxxxxxxx.set("MaxID", 1000);
                              }

                              try {
                                 yamlConfigurationxxxxxxxxx.save(Variable.f_log);
                              } catch (IOException var139) {
                                 var139.printStackTrace();
                              }

                              int nowID = yamlConfigurationxxxxxxxxx.getInt("NowID");
                              int MaxID = yamlConfigurationxxxxxxxxx.getInt("MaxID");
                              if (nowID >= MaxID) {
                                 String tempxxxxxxxxxxxxxxxxxxx = Variable.Lang_YML.getString("ReachMaxCreate");
                                 sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                                 sender.sendMessage(tempxxxxxxxxxxxxxxxxxxx);
                                 sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
                                 return false;
                              } else {
                                 String vx = args[1];
                                 boolean paidSkip = Variable.pendingCreateCostPaid.remove(pxx.getName()) == Boolean.TRUE;
                                  if (Main.JavaPlugin.getConfig().getBoolean("CreateCost.Enable", false) && !paidSkip) {
                                     pxx.closeInventory();
                                     pxx.openInventory(new CreateCostGui(pxx, vx, "random").getInventory());
                                     return false;
                                   } else {
                                      if (!Variable.bungee
                                         && Main.JavaPlugin.getConfig().getBoolean("HomeTerrain.Enabled", false)
                                          && !HomeCreationCoordinator.consumeAdmission(HomeCreationCoordinator.currentRequest(createHomeName))) {
                                         // This pass creates nothing, so give the prepaid token back
                                         // instead of relying on the coordinator to re-stamp it. The
                                         // re-dispatched command must also keep the requested type;
                                         // hardcoding "sh create 1" silently turned every queued
                                         // random/flat request into a plain type-1 home.
                                         if (paidSkip) {
                                            Variable.pendingCreateCostPaid.put(pxx.getName(), Boolean.TRUE);
                                         }

                                         int queuePosition = HomeCreationCoordinator.enqueue(
                                            Main.JavaPlugin,
                                            pxx,
                                            createHomeName,
                                            "sh create " + vx,
                                            Main.JavaPlugin.getConfig().getInt("HomeCreationQueue.MaxConcurrent", 2)
                                         );
                                         pxx.sendMessage(HomeCreationCoordinator.queuePositionMessage(queuePosition));
                                         pxx.sendMessage(HomeCreationCoordinator.queueWaitingMessage());
                                         return false;
                                      }
                                     vx = this.normalizeCreateMode(vx);
                                      if (vx.equalsIgnoreCase("1")) {
                                         if (Main.JavaPlugin.getConfig().getBoolean("HomeTerrain.Enabled", false)) {
                                            WorldCreator creator = NaturalHomeWorldFactory.create(
                                               createWorldName,
                                               Variable.pendingCreateSeed.remove(pxx.getName()),
                                               Main.JavaPlugin.getConfig().getLong("Seed")
                                            );
                                            Variable.create_list_home.add(createWorldName);
                                            Bukkit.createWorld(creator);
                                         } else {
                                            String normalTemplate = Main.JavaPlugin.getConfig().getString("CreateTemplate.NormalWorld", "");
                                            boolean useNormalTemplate = Main.JavaPlugin.getConfig().getBoolean("CreateTemplate.EnableNormal", false);
                                            World worldxxxx = useNormalTemplate
                                               ? this.createHomeFromTemplate(pxx, createHomeName, normalTemplate, WorldType.NORMAL)
                                               : null;
                                            if (useNormalTemplate && worldxxxx == null) {
                                               pxx.sendMessage("§e[错误庄园] 普通地形模板不可用，已回退到实时生成。");
                                            }
                                            if (worldxxxx == null) {
                                               if (Main.JavaPlugin.getConfig().getBoolean("EnableMultiverseCoreCreate")) {
                                                  Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "mv create " + createHomeName + " normal");
                                               } else {
                                                  WorldCreator creator = new WorldCreator(createWorldName);
                                                  this.configureCreatorSeed(pxx, creator);
                                                  creator.generateStructures(Main.JavaPlugin.getConfig().getBoolean("generateStructures"));
                                                  creator.type(WorldType.NORMAL);
                                                  Variable.create_list_home.add(createWorldName);
                                                  Bukkit.createWorld(creator);
                                               }
                                            }
                                         }
                                    } else if (vx.equalsIgnoreCase("2")) {
                                       String flatTemplate = Main.JavaPlugin.getConfig().getString("CreateTemplate.FlatWorld", "");
                                       boolean useFlatTemplate = Main.JavaPlugin.getConfig().getBoolean("CreateTemplate.EnableFlat", false);
                                       if (useFlatTemplate) {
                                          World worldxxxxx = this.createHomeFromTemplate(pxx, createHomeName, flatTemplate, WorldType.FLAT);
                                          if (worldxxxxx == null) {
                                             pxx.sendMessage("§e[错误庄园] 超平坦模板不可用，已回退到实时生成。");
                                          }

                                          if (worldxxxxx == null) {
                                             if (Main.JavaPlugin.getConfig().getBoolean("EnableMultiverseCoreCreate")) {
                                                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "mv create " + createHomeName + " normal -t flat");
                                             } else {
                                                WorldCreator creator = null;
                                                creator = new WorldCreator(createWorldName);
                                                this.configureCreatorSeed(pxx, creator);
                                                if (Main.JavaPlugin.getConfig().getBoolean("generateStructures")) {
                                                   creator.generateStructures(true);
                                                } else {
                                                   creator.generateStructures(false);
                                                }

                                                 com.Util.SuperflatPreset.apply(creator);
                                                 Variable.create_list_home.add(createWorldName);
                                                 World var1279 = Bukkit.createWorld(creator);
                                             }
                                          }
                                       } else if (Main.JavaPlugin.getConfig().getBoolean("EnableMultiverseCoreCreate")) {
                                          Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "mv create " + createHomeName + " normal -t flat");
                                       } else {
                                          WorldCreator creator = null;
                                          creator = new WorldCreator(createWorldName);
                                          this.configureCreatorSeed(pxx, creator);
                                          if (Main.JavaPlugin.getConfig().getBoolean("generateStructures")) {
                                             creator.generateStructures(true);
                                          } else {
                                             creator.generateStructures(false);
                                          }

                                           com.Util.SuperflatPreset.apply(creator);
                                           Variable.create_list_home.add(createWorldName);
                                           World var1251 = Bukkit.createWorld(creator);
                                       }
                                    } else if (vx.equalsIgnoreCase("airland")) {
                                       WorldCreator creator = new WorldCreator(createWorldName);
                                       creator.type(WorldType.NORMAL);
                                       creator.generateStructures(false);
                                       creator.generator(new CustomChunkGenerator());
                                       this.configureCreatorSeed(pxx, creator);
                                       Variable.create_list_home.add(createWorldName);
                                       Bukkit.createWorld(creator);
                                    } else {
                                       if (vx.equalsIgnoreCase("random")) {
                                          List<String> listx = Main.JavaPlugin.getConfig().getStringList("Random");
                                          int num = (int)(Math.random() * listx.size());
                                          Bukkit.dispatchCommand(sender, "sh create " + listx.get(num));
                                          return false;
                                       }

                                       if (Main.JavaPlugin.getConfig().getBoolean("EnableMultiverseCoreCreate")) {
                                          Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "mv create " + createHomeName + " normal -g VoidWorld");
                                       } else {
                                          if (!paidSkip
                                             && !Main.JavaPlugin.getConfig().getBoolean("Permission.Create-" + vx)
                                             && !com.Util.Perm.has(pxx, "ErrorTown.Create." + vx)
                                             && !com.Util.Perm.has(pxx, "ErrorTown.Create.*")) {
                                             String tempxxxxxxxxxxxxxxxxxxx = Variable.Lang_YML.getString("NoPermissionCreate");
                                             if (tempxxxxxxxxxxxxxxxxxxx.contains("<Permission>")) {
                                                tempxxxxxxxxxxxxxxxxxxx = tempxxxxxxxxxxxxxxxxxxx.replace("<Permission>", "ErrorTown.Create." + vx);
                                             }

                                             sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                                             sender.sendMessage(tempxxxxxxxxxxxxxxxxxxx);
                                             sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
                                             return false;
                                          }

                                          String oldDirx = Variable.worldFinal + vx;
                                          File newfxxx;
                                          if (Variable.world_prefix.equalsIgnoreCase("")) {
                                             if (!Bukkit.getVersion().toString().toUpperCase().contains("ARCLIGHT")
                                                && !Bukkit.getVersion().toString().contains("1.20.1")) {
                                                newfxxx = new File(Variable.single_server_gen + "world" + Variable.file_loc_prefix);
                                             } else {
                                                newfxxx = new File(Variable.single_server_gen + Variable.world_prefix);
                                             }
                                          } else {
                                             newfxxx = new File(Variable.single_server_gen + Variable.world_prefix);
                                          }

                                          String newDirx = newfxxx.getPath().toString() + Variable.file_loc_prefix + createHomeName;
                                          File exist_filex = new File(oldDirx);
                                          if (!exist_filex.exists()) {
                                             String tempxxxxxxxxxxxxxxxxxxx = Variable.Lang_YML.getString("WorldFileNotExist");
                                             if (tempxxxxxxxxxxxxxxxxxxx.contains("<name>")) {
                                                tempxxxxxxxxxxxxxxxxxxx = tempxxxxxxxxxxxxxxxxxxx.replace("<name>", vx);
                                             }

                                             sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                                             sender.sendMessage(tempxxxxxxxxxxxxxxxxxxx);
                                             sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
                                             return false;
                                          }

                                          Util.copyDir(oldDirx, newDirx);
                                          WorldCreator creator = null;
                                          creator = new WorldCreator(createWorldName);
                                          if (Main.JavaPlugin.getConfig().getBoolean("generateStructures")) {
                                             creator.generateStructures(true);
                                          } else {
                                             creator.generateStructures(false);
                                          }

                                          Variable.create_list_home.add(createWorldName);
                                          World var1299 = Bukkit.createWorld(creator);
                                       }
                                    }

                                    if (Variable.hook_multiverseCore) {
                                       String seedx = Long.toString(Main.JavaPlugin.getConfig().getLong("Seed"));
                                       if (seedx.equalsIgnoreCase("0")) {
                                          seedx = "";
                                       }

                                       MultiverseCore mvcorexx = MultiverseCompat.plugin();
                                       MVWorldManager mv_mxx = mvcorexx.getMVWorldManager();
                                       if (mv_mxx.isMVWorld(createWorldName)) {
                                          mv_mxx.removeWorldFromConfig(createWorldName);
                                       }

                                       if (vx.equalsIgnoreCase("1") && !Main.JavaPlugin.getConfig().getBoolean("EnableMultiverseCoreCreate")) {
                                          mv_mxx.addWorld(
                                             createWorldName,
                                             Environment.NORMAL,
                                             seedx,
                                             WorldType.NORMAL,
                                             Main.JavaPlugin.getConfig().getBoolean("generateStructures"),
                                             ""
                                          );
                                       } else if (vx.equalsIgnoreCase("2") && !Main.JavaPlugin.getConfig().getBoolean("EnableMultiverseCoreCreate")) {
                                          mv_mxx.addWorld(
                                             createWorldName,
                                             Environment.NORMAL,
                                             seedx,
                                             WorldType.FLAT,
                                             Main.JavaPlugin.getConfig().getBoolean("generateStructures"),
                                             ""
                                          );
                                       } else if (!Main.JavaPlugin.getConfig().getBoolean("EnableMultiverseCoreCreate")) {
                                          mv_mxx.addWorld(
                                             createWorldName,
                                             Environment.NORMAL,
                                             seedx,
                                             WorldType.NORMAL,
                                             Main.JavaPlugin.getConfig().getBoolean("generateStructures"),
                                             ""
                                          );
                                       }

                                       if (Main.JavaPlugin.getConfig().getBoolean("EnableChatPrefix")) {
                                          MultiverseWorld mvx = mv_mxx.getMVWorld(createWorldName);
                                          World worldxxxxxx = Bukkit.getWorld(createWorldName);
                                          if (mvx != null && worldxxxxxx != null) {
                                             String tempxxxxxxxxxxxxxxxxxxx = Variable.Lang_YML.getString("PlaceHolders.WorldName");
                                             if (tempxxxxxxxxxxxxxxxxxxx.contains("<PlayerName>")) {
                                                tempxxxxxxxxxxxxxxxxxxx = tempxxxxxxxxxxxxxxxxxxx.replace(
                                                   "<PlayerName>", worldxxxxxx.getName().replace(Variable.world_prefix, "")
                                                );
                                             }

                                             if (tempxxxxxxxxxxxxxxxxxxx.contains("<WorldName>")) {
                                                tempxxxxxxxxxxxxxxxxxxx = tempxxxxxxxxxxxxxxxxxxx.replace(
                                                   "<WorldName>", worldxxxxxx.getName().replace(Variable.world_prefix, "")
                                                );
                                             }

                                             mvx.setAlias(tempxxxxxxxxxxxxxxxxxxx);
                                          }
                                       }

                                       MultiverseWorld mvx = mv_mxx.getMVWorld(createWorldName);
                                       if (mvx != null) {
                                          mvx.setAutoLoad(false);
                                       }
                                    }

                                    World worldxxxxxx = Bukkit.getWorld(createWorldName);
                                    if (worldxxxxxx == null) {
                                       HomeCreationQueue.CreationRequest request = HomeCreationCoordinator.currentRequest(createHomeName);
                                       if (request != null) {
                                          HomeCreationCoordinator.fail(
                                             Main.JavaPlugin,
                                             request,
                                             pxx,
                                             HomeCreationCoordinator.worldCreationFailedMessage()
                                          );
                                       }
                                       return false;
                                    }
                                    if (Main.JavaPlugin.getConfig().getBoolean("HomeTerrain.Enabled", false)) {
                                       HomeCreationCoordinator.applyWorldPolicy(
                                          worldxxxxxx,
                                          this.getNaturalHomeSize(1),
                                          Main.JavaPlugin.getConfig().getBoolean("HomeTerrain.PermanentDay", true),
                                          Main.JavaPlugin.getConfig().getLong("HomeTerrain.DayTime", 6000L)
                                       );
                                    }
                                    if (vx.equalsIgnoreCase("airland")) {
                                       this.setupSimpleSkyIsland(worldxxxxxx);
                                    }

                                    if (!Main.JavaPlugin.getConfig().getBoolean("KeepInventory")) {
                                       Platform.setGameRule(worldxxxxxx, "keepInventory", "false");
                                    } else if (Main.JavaPlugin.getConfig().getBoolean("KeepInventory")) {
                                       Platform.setGameRule(worldxxxxxx, "keepInventory", "true");
                                    }

                                    if (!Main.JavaPlugin.getConfig().getBoolean("doMobSpawning")) {
                                       Platform.setGameRule(worldxxxxxx, "doMobSpawning", "false");
                                    } else if (Main.JavaPlugin.getConfig().getBoolean("doMobSpawning")) {
                                       Platform.setGameRule(worldxxxxxx, "doMobSpawning", "true");
                                    }

                                    if (!Main.JavaPlugin.getConfig().getBoolean("mobGriefing")) {
                                       Platform.setGameRule(worldxxxxxx, "mobGriefing", "false");
                                    } else if (Main.JavaPlugin.getConfig().getBoolean("mobGriefing")) {
                                       Platform.setGameRule(worldxxxxxx, "mobGriefing", "true");
                                    }

                                    if (!Main.JavaPlugin.getConfig().getBoolean("doFireTick")) {
                                       Platform.setGameRule(worldxxxxxx, "doFireTick", "false");
                                    } else if (Main.JavaPlugin.getConfig().getBoolean("doFireTick")) {
                                       Platform.setGameRule(worldxxxxxx, "doFireTick", "true");
                                    }

                                    if (Variable.bungee) {
                                       int set_level = 1;
                                       if (Main.JavaPlugin.getConfig().getBoolean("HomeTerrain.Enabled", false)) {
                                          set_level = 1;
                                       } else {

                                       for (int ixxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx = Main.JavaPlugin.getConfig().getInt("MaxLevel");
                                          ixxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx > 0;
                                          ixxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx--
                                       ) {
                                          if (com.Util.Perm.has(pxx, "ErrorTown.Level." + ixxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx)) {
                                             set_level = ixxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx;
                                             break;
                                          }
                                       }
                                       }

                                       MySQL.insertvalue(
                                          pxx.getName(),
                                          "",
                                          "",
                                          "",
                                          String.valueOf(Main.JavaPlugin.getConfig().getBoolean("NormalPublic")),
                                          String.valueOf(set_level),
                                          String.valueOf(Main.JavaPlugin.getConfig().getBoolean("NormalPVP")),
                                          String.valueOf(Main.JavaPlugin.getConfig().getBoolean("NormalPickup")),
                                          String.valueOf(Main.JavaPlugin.getConfig().getBoolean("NormalDrop")),
                                          Main.JavaPlugin.getConfig().getString("Server"),
                                          "false",
                                          "false",
                                          "0",
                                          String.valueOf(worldxxxxxx.getSpawnLocation().getX()),
                                          String.valueOf(worldxxxxxx.getSpawnLocation().getY()),
                                          String.valueOf(worldxxxxxx.getSpawnLocation().getZ()),
                                          "0",
                                          "0",
                                          "",
                                          "",
                                          "",
                                          "",
                                          ""
                                       );
                                       if (Main.JavaPlugin.getConfig().getBoolean("ClearInventoryBeforeCreate")) {
                                          pxx.getInventory().clear();
                                          pxx.sendMessage(Variable.Lang_YML.getString("ClearInventoryBeforeCreate"));
                                       }

                                       var __cfg14 = Main.JavaPlugin.getConfig().getStringList("DispathCommand");
                                       for (int cxxxx = 0; cxxxx < __cfg14.size(); cxxxx++) {
                                          String tempxxxxxxxxxxxxxxxxxxxx = __cfg14.get(cxxxx);
                                          if (tempxxxxxxxxxxxxxxxxxxxx.contains("<Name>")) {
                                             tempxxxxxxxxxxxxxxxxxxxx = tempxxxxxxxxxxxxxxxxxxxx.replace("<Name>", pxx.getName());
                                          }

                                          if (tempxxxxxxxxxxxxxxxxxxxx.contains("[console]")) {
                                             tempxxxxxxxxxxxxxxxxxxxx = tempxxxxxxxxxxxxxxxxxxxx.replace("[console]", "");
                                             Bukkit.dispatchCommand(Bukkit.getConsoleSender(), tempxxxxxxxxxxxxxxxxxxxx);
                                          } else if (tempxxxxxxxxxxxxxxxxxxxx.contains("[player]")) {
                                             tempxxxxxxxxxxxxxxxxxxxx = tempxxxxxxxxxxxxxxxxxxxx.replace("[player]", "");
                                             Bukkit.dispatchCommand(pxx, tempxxxxxxxxxxxxxxxxxxxx);
                                          }
                                       }
                                    } else {
                                       File f2xxxxxxxxxx = new File(Variable.Tempf, createHomeName + ".yml");
                                       if (f2xxxxxxxxxx.exists()) {
                                          sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                                          sender.sendMessage(Variable.Lang_YML.getString("AlreadyHome"));
                                          sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
                                          return false;
                                       }

                                       try {
                                          f2xxxxxxxxxx.createNewFile();
                                       } catch (IOException var138) {
                                          var138.printStackTrace();
                                       }

                                       YamlConfiguration yamlConfiguration1x = YamlConfiguration.loadConfiguration(f2xxxxxxxxxx);
                                       yamlConfiguration1x.createSection("Members");
                                       yamlConfiguration1x.createSection("OP");
                                       yamlConfiguration1x.createSection("Denys");
                                       yamlConfiguration1x.createSection("Public");
                                       yamlConfiguration1x.createSection("Level");
                                       yamlConfiguration1x.createSection("pvp");
                                       yamlConfiguration1x.createSection("pickup");
                                       yamlConfiguration1x.createSection("drop");
                                       yamlConfiguration1x.createSection("Server");
                                       yamlConfiguration1x.createSection("locktime");
                                       yamlConfiguration1x.createSection("lockweather");
                                       yamlConfiguration1x.createSection("time");
                                       yamlConfiguration1x.createSection("CreateMode");
                                       if (!yamlConfigurationxxxxxxxxx.contains("NowID")) {
                                          yamlConfigurationxxxxxxxxx.set("NowID", 0);
                                       }

                                       if (!yamlConfigurationxxxxxxxxx.contains("MaxID")) {
                                          yamlConfigurationxxxxxxxxx.set("MaxID", 1000);
                                       }

                                       try {
                                          yamlConfigurationxxxxxxxxx.save(Variable.f_log);
                                       } catch (IOException var137) {
                                          var137.printStackTrace();
                                       }

                                       yamlConfiguration1x.set("Public", Main.JavaPlugin.getConfig().getBoolean("NormalPublic"));
                                       yamlConfiguration1x.set("pickup", Main.JavaPlugin.getConfig().getBoolean("NormalPickup"));
                                       yamlConfiguration1x.set("drop", Main.JavaPlugin.getConfig().getBoolean("NormalDrop"));
                                       yamlConfiguration1x.set("pvp", Main.JavaPlugin.getConfig().getBoolean("NormalPVP"));
                                       boolean permanentDay = Main.JavaPlugin.getConfig().getBoolean("HomeTerrain.PermanentDay", true);
                                       yamlConfiguration1x.set("locktime", permanentDay);
                                       yamlConfiguration1x.set("time", Main.JavaPlugin.getConfig().getLong("HomeTerrain.DayTime", 6000L));
                                       yamlConfiguration1x.set("lockweather", permanentDay);
                                       yamlConfiguration1x.set("rules.noMobSpawn", true);
                                       yamlConfiguration1x.set("rules.allowHostileMobs", false);
                                       yamlConfiguration1x.set("rules.allowPassiveMobs", false);
                                       yamlConfiguration1x.set("rules.maxMobCount", 0);
                                       int set_level = 1;
                                       if (Main.JavaPlugin.getConfig().getBoolean("HomeTerrain.Enabled", false)) {
                                          set_level = 1;
                                       } else {

                                       for (int ixxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx = Main.JavaPlugin.getConfig().getInt("MaxLevel");
                                          ixxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx > 0;
                                          ixxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx--
                                       ) {
                                          if (com.Util.Perm.has(pxx, "ErrorTown.Level." + ixxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx)) {
                                             set_level = ixxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx;
                                             break;
                                          }
                                       }
                                       }

                                       yamlConfiguration1x.set("Level", set_level);
                                       yamlConfiguration1x.set("Owner", pxx.getName());
                                       yamlConfiguration1x.set("Server", Main.JavaPlugin.getConfig().getString("Server"));
                                       yamlConfiguration1x.set("CreateMode", vx);

                                       try {
                                          yamlConfiguration1x.save(f2xxxxxxxxxx);
                                       } catch (IOException var136) {
                                          var136.printStackTrace();
                                       }

                                       yamlConfigurationxxxxxxxxx.set("NowID", nowID + 1);

                                       try {
                                          yamlConfigurationxxxxxxxxx.save(Variable.f_log);
                                       } catch (IOException var135) {
                                          var135.printStackTrace();
                                       }

                                       if (Main.JavaPlugin.getConfig().getInt("MaxSpawnMonstersAmount") != -1) {
                                          worldxxxxxx.setMonsterSpawnLimit(Main.JavaPlugin.getConfig().getInt("MaxSpawnMonstersAmount"));
                                       }

                                       if (Main.JavaPlugin.getConfig().getInt("MaxSpawnAnimalsAmount") != -1) {
                                          worldxxxxxx.setAnimalSpawnLimit(Main.JavaPlugin.getConfig().getInt("MaxSpawnAnimalsAmount"));
                                       }

                                       if (Main.JavaPlugin.getConfig().getInt("MaxSpawnAnimalsAmount") == 0 && Variable.hook_multiverseCore) {
                                          MultiverseCore mvcorexxx = MultiverseCompat.plugin();
                                          MVWorldManager mv_mxxx = mvcorexxx.getMVWorldManager();
                                          MultiverseWorld mvx = mv_mxxx.getMVWorld(pxx.getLocation().getWorld().getName());
                                          mvx.setAllowAnimalSpawn(false);
                                       }

                                       if (Main.JavaPlugin.getConfig().getInt("MaxSpawnMonstersAmount") == 0 && Variable.hook_multiverseCore) {
                                          MultiverseCore mvcorexxx = MultiverseCompat.plugin();
                                          MVWorldManager mv_mxxx = mvcorexxx.getMVWorldManager();
                                          MultiverseWorld mvx = mv_mxxx.getMVWorld(pxx.getLocation().getWorld().getName());
                                          mvx.setAllowMonsterSpawn(false);
                                       }

                                       yamlConfiguration1x.createSection("X");
                                       yamlConfiguration1x.createSection("Y");
                                       yamlConfiguration1x.createSection("Z");
                                       Location loc = worldxxxxxx.getSpawnLocation();
                                       yamlConfiguration1x.set("X", loc.getX());
                                       yamlConfiguration1x.set("Y", loc.getY());
                                       yamlConfiguration1x.set("Z", loc.getZ());
                                       yamlConfiguration1x.createSection("flowers");
                                       yamlConfiguration1x.createSection("popularity");
                                       yamlConfiguration1x.createSection("gifts");
                                       yamlConfiguration1x.createSection("icon");
                                       yamlConfiguration1x.createSection("advertisement");
                                       yamlConfiguration1x.createSection("limitblock");
                                       yamlConfiguration1x.set("flowers", 0);
                                       yamlConfiguration1x.set("popularity", 0);
                                       yamlConfiguration1x.set("gifts", new ArrayList());
                                       yamlConfiguration1x.set("icon", "");
                                       yamlConfiguration1x.set("advertisement", new ArrayList());
                                       yamlConfiguration1x.set("limitblock", new ArrayList());

                                       try {
                                          yamlConfiguration1x.save(f2xxxxxxxxxx);
                                       } catch (IOException var134) {
                                          var134.printStackTrace();
                                       }

                                       final World createdWorld = worldxxxxxx;
                                       if (Main.JavaPlugin.getConfig().getBoolean("HomeTerrain.Enabled", false)) {
                                          HomeCreationCoordinator.prepareInitialArea(
                                             Main.JavaPlugin,
                                             createHomeName,
                                             createdWorld,
                                             pxx,
                                             this.getNaturalHomeSize(1),
                                             Main.JavaPlugin.getConfig().getBoolean("HomeTerrain.PermanentDay", true),
                                             Main.JavaPlugin.getConfig().getLong("HomeTerrain.DayTime", 6000L),
                                             null
                                          );
                                       } else {
                                          (new BukkitRunnable() {
                                             public void run() {
                                                if (pxx.isOnline() && createdWorld != null) {
                                                   pxx.teleport(createdWorld.getSpawnLocation());
                                                }
                                             }
                                          }).runTaskLater(Main.JavaPlugin, 5L);
                                       }
                                       FirstBorderShaped.ShapeBorder(worldxxxxxx);
                                       if (Main.JavaPlugin.getConfig().getBoolean("ClearInventoryBeforeCreate")) {
                                          pxx.getInventory().clear();
                                          pxx.sendMessage(Variable.Lang_YML.getString("ClearInventoryBeforeCreate"));
                                       }

                                       var __cfg15 = Main.JavaPlugin.getConfig().getStringList("DispathCommand");
                                       for (int cxxxx = 0; cxxxx < __cfg15.size(); cxxxx++) {
                                          String tempxxxxxxxxxxxxxxxxxxxxx = __cfg15.get(cxxxx);
                                          if (tempxxxxxxxxxxxxxxxxxxxxx.contains("<Name>")) {
                                             tempxxxxxxxxxxxxxxxxxxxxx = tempxxxxxxxxxxxxxxxxxxxxx.replace("<Name>", pxx.getName());
                                          }

                                          if (tempxxxxxxxxxxxxxxxxxxxxx.contains("[console]")) {
                                             tempxxxxxxxxxxxxxxxxxxxxx = tempxxxxxxxxxxxxxxxxxxxxx.replace("[console]", "");
                                             Bukkit.dispatchCommand(Bukkit.getConsoleSender(), tempxxxxxxxxxxxxxxxxxxxxx);
                                          } else if (tempxxxxxxxxxxxxxxxxxxxxx.contains("[player]")) {
                                             tempxxxxxxxxxxxxxxxxxxxxx = tempxxxxxxxxxxxxxxxxxxxxx.replace("[player]", "");
                                             Bukkit.dispatchCommand(pxx, tempxxxxxxxxxxxxxxxxxxxxx);
                                          }
                                       }
                                    }

                                    return false;
                                 }
                              }
                           } else {
                               sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                               com.Util.ClickableText.run(pxx, Variable.Lang_YML.getString("ErrorHelp"), "/sh Help 1");

                              sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
                              return false;
                           }
                        } else if (!Main.JavaPlugin.getConfig().getBoolean("Permission.CommandUser")
                           && !com.Util.Perm.has(pxx, "ErrorTown.Quit")
                           && !com.Util.Perm.has(pxx, "ErrorTown.command.user")) {
                           String tip = Variable.Lang_YML.getString("NoPermissionCheck");
                           if (tip.contains("<Permission>")) {
                              tip = tip.replace("<Permission>", "ErrorTown.Quit");
                           }

                           pxx.sendMessage(tip);
                           return false;
                        } else if (Variable.bungee) {
                           boolean has_been_quit = MySQL.PlayerQuitHome(pxx.getName());
                           if (!has_been_quit) {
                              String Message = Variable.Lang_YML.getString("QuitButNoJoin");
                              pxx.sendMessage(Message);
                              return false;
                           } else {
                              String Message = Variable.Lang_YML.getString("QuitSuccess");
                              pxx.sendMessage(Message);
                              return false;
                           }
                        } else {
                           File folder = new File(Variable.Tempf);
                           boolean has_been_quit = false;

                           File[] arrayOfFilex;
                           label6851:
                           for (File tempxxxxxxxxxxxxxxxxxxxxxx : arrayOfFilex = folder.listFiles()) {
                              String want_to = tempxxxxxxxxxxxxxxxxxxxxxx.getPath()
                                 .replace(Variable.Tempf, "")
                                 .replace(".yml", "")
                                 .replace(Variable.file_loc_prefix, "");
                              YamlConfiguration yamlConfigurationxxxxxxxxxx = YamlConfiguration.loadConfiguration(tempxxxxxxxxxxxxxxxxxxxxxx);
                              List<String> Ops = yamlConfigurationxxxxxxxxxx.getStringList("OP");

                              for (int ixxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx = 0;
                                 ixxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx < Ops.size();
                                 ixxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx++
                              ) {
                                 String temp_str = (String)yamlConfigurationxxxxxxxxxx.getStringList("OP").get(ixxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx);
                                 if (temp_str.equalsIgnoreCase(pxx.getName())) {
                                    has_been_quit = true;
                                    String Message = Variable.Lang_YML.getString("QuitSuccess");
                                    if (Message.contains("<Name>")) {
                                       Message = Message.replace("<Name>", want_to);
                                    }

                                    if (Bukkit.getPlayer(want_to) != null && Bukkit.getPlayer(want_to) != null) {
                                       String ManagerQuitTip = Variable.Lang_YML.getString("QuitManager");
                                       if (ManagerQuitTip.contains("<Name>")) {
                                          ManagerQuitTip = ManagerQuitTip.replace("<Name>", pxx.getName());
                                       }

                                       Bukkit.getPlayer(want_to).sendMessage(ManagerQuitTip);
                                    }

                                    pxx.sendMessage(Message);
                                    Ops.remove(ixxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx);
                                    yamlConfigurationxxxxxxxxxx.set("OP", Ops);

                                    try {
                                       yamlConfigurationxxxxxxxxxx.save(tempxxxxxxxxxxxxxxxxxxxxxx);
                                    } catch (IOException var130) {
                                       var130.printStackTrace();
                                    }
                                    break label6851;
                                 }
                              }
                           }

                           if (!has_been_quit) {
                              String Messagex = Variable.Lang_YML.getString("QuitButNoJoin");
                              pxx.sendMessage(Messagex);
                              return false;
                           } else {
                              return false;
                           }
                        }
                     } else if (!Main.JavaPlugin.getConfig().getBoolean("Permission.Visit") && !com.Util.Perm.has(pxx, "ErrorTown.Visit")) {
                        String tip = Variable.Lang_YML.getString("NoPermissionCheck");
                        if (tip.contains("<Permission>")) {
                           tip = tip.replace("<Permission>", "ErrorTown.Visit");
                        }

                        pxx.sendMessage(tip);
                        return false;
                     } else {
                        if (Variable.bungee) {
                           if (Util.CheckIsHome(args[1])) {
                              int amountxx = 0;

                              for (Player count_p : Bukkit.getOnlinePlayers()) {
                                 if (count_p.getWorld().getName().replace(Variable.world_prefix, "").equalsIgnoreCase(args[1])) {
                                    amountxx++;
                                 }
                              }

                              boolean has_been_load = false;
                              if (Main.JavaPlugin.getConfig().getBoolean("MoveWorldAfterUnLoad") && Bukkit.getWorld(Variable.world_prefix + args[1]) != null) {
                                 has_been_load = true;
                              }

                              if (Main.JavaPlugin.getConfig().getBoolean("AutoMoveWorldFilesToOther")
                                 && !has_been_load
                                 && !Variable.wait_to_command.containsKey(pxx.getName())
                                 && Main.JavaPlugin.getConfig().getBoolean("BungeeCord")
                                 && !Variable.has_already_move_world.contains(pxx.getName())
                                 && amountxx == 0) {
                                 if (Main.JavaPlugin.getConfig().getString("DecideBy").equalsIgnoreCase("Player")) {
                                    if (!MySQL.getLowerstLagServer().equalsIgnoreCase(MySQL.getServer(args[1]))
                                       && MySQL.getServerAmount(MySQL.getLowerstLagServer()) != Bukkit.getOnlinePlayers().size()) {
                                       try {
                                          Channel.waitToLoad(pxx, MySQL.getLowerstLagServer(), args[1]);
                                       } catch (IOException var113) {
                                          var113.printStackTrace();
                                       }

                                       pxx.sendMessage(Variable.Lang_YML.getString("StartLowestLagServer"));
                                       Channel.sendPlayerToServer(pxx, MySQL.getLowerstLagServer());
                                       return false;
                                    }
                                 } else if (!MySQL.getHighestTPSServer().equalsIgnoreCase(MySQL.getServer(args[1]))) {
                                    double nowxxx = 0.0;
                                    if (Bukkit.getVersion().contains("1.7.10")) {
                                       nowxxx = R1_7_10.getTps();
                                    } else {
                                       double se1 = Double.valueOf(PlaceholderAPI.setPlaceholders(null, "%server_tps_1%").replace("*", ""));
                                       double se2 = Double.valueOf(PlaceholderAPI.setPlaceholders(null, "%server_tps_5%").replace("*", ""));
                                       double se3 = Double.valueOf(PlaceholderAPI.setPlaceholders(null, "%server_tps_15%").replace("*", ""));
                                       nowxxx = (se1 + se2 + se3) / 3.0;
                                    }

                                    if (MySQL.getServerAmount(MySQL.getLowerstLagServer()) != nowxxx) {
                                       try {
                                          Channel.waitToLoad(pxx, MySQL.getHighestTPSServer(), args[1]);
                                       } catch (IOException var114) {
                                          var114.printStackTrace();
                                       }

                                       pxx.sendMessage(Variable.Lang_YML.getString("StartLowestLagServer"));
                                       Channel.sendPlayerToServer(pxx, MySQL.getHighestTPSServer());
                                       return false;
                                    }
                                 }
                              }

                              if (MySQL.getServer(args[1]).equalsIgnoreCase(Main.JavaPlugin.getConfig().getString("Server"))) {
                                 World worldxxxxxxx = Bukkit.getWorld(Variable.world_prefix + args[1]);
                                 WorldCreator creator = new WorldCreator(Variable.world_prefix + args[1]);
                                 Variable.create_list_home.add(Variable.world_prefix + args[1]);
                                 Bukkit.createWorld(creator);
                                 worldxxxxxxx = Bukkit.getWorld(Variable.world_prefix + args[1]);
                                 Location loc = worldxxxxxxx.getSpawnLocation();
                                 loc = Util.getNotAir(loc);
                                 loc.setX(Double.valueOf(MySQL.getX(args[1])));
                                 loc.setY(Double.valueOf(MySQL.getY(args[1])));
                                 loc.setZ(Double.valueOf(MySQL.getZ(args[1])));
                                 pxx.teleport(loc);
                              } else {
                                 try {
                                    Channel.waitDelayToSomeWhere(pxx, MySQL.getServer(args[1]), "sh v " + args[1]);
                                 } catch (IOException var115) {
                                    var115.printStackTrace();
                                 }

                                 Channel.sendPlayerToServer(pxx, MySQL.getServer(args[1]));
                              }
                           } else {
                              String tempxxxxxxxxxxxxxxxxxxxxxx = Variable.Lang_YML.getString("TpNotExist");
                              sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                              sender.sendMessage(tempxxxxxxxxxxxxxxxxxxxxxx);
                              sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
                           }
                        } else {
                           File fx = new File(Variable.Tempf, args[1] + ".yml");
                           if (fx.exists()) {
                              YamlConfiguration yamlConfigurationxxxxxxxxxx = YamlConfiguration.loadConfiguration(fx);
                              World worldxxxxxxx = Bukkit.getWorld(Variable.world_prefix + args[1]);
                              WorldCreator creator = new WorldCreator(Variable.world_prefix + args[1]);
                              Variable.create_list_home.add(Variable.world_prefix + args[1]);
                              Bukkit.createWorld(creator);
                              worldxxxxxxx = Bukkit.getWorld(Variable.world_prefix + args[1]);
                              Location loc = worldxxxxxxx.getSpawnLocation();
                              loc = Util.getNotAir(loc);
                              loc.setX(yamlConfigurationxxxxxxxxxx.getDouble("X"));
                              loc.setY(yamlConfigurationxxxxxxxxxx.getDouble("Y"));
                              loc.setZ(yamlConfigurationxxxxxxxxxx.getDouble("Z"));
                              pxx.teleport(loc);
                           } else {
                              String tempxxxxxxxxxxxxxxxxxxxxxx = Variable.Lang_YML.getString("TpNotExist");
                              sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                              sender.sendMessage(tempxxxxxxxxxxxxxxxxxxxxxx);
                              sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
                           }
                        }

                        return false;
                     }
                  } else if (Variable.bungee) {
                     sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                     sender.sendMessage("§8[§6错误庄园§8] §c当前版本暂未为跨服模式接入指定庄园进入");
                     sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
                     return false;
                  } else {
                     String targetHome = this.resolveOwnedHomeSelection(pxx, args[1]);
                     if (targetHome == null) {
                        sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                        sender.sendMessage("§8[§6错误庄园§8] §c未找到您指定的庄园: §e" + args[1]);
                        sender.sendMessage("§7可先使用 §f/sh homes §7查看自己的庄园列表");
                        sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
                        return false;
                     } else {
                        return this.teleportToOwnedHomeLocal(pxx, sender, targetHome);
                     }
                  }
               } else {
                  if (Variable.bungee) {
                     if (!MySQL.alreadyhastheplayerjoin(pxx.getName()) && !MySQL.alreadyhastheplayerhome(pxx.getName())) {
                        String tempxxxxxxxxxxxxxxxxxxxxxx = Variable.Lang_YML.getString("NoCreateOrJoin");
                        sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                        sender.sendMessage(tempxxxxxxxxxxxxxxxxxxxxxx);
                        sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
                        return false;
                     }

                     if (MySQL.alreadyhastheplayerjoin(pxx.getName())) {
                        int amountxx = 0;

                        for (Player count_px : Bukkit.getOnlinePlayers()) {
                           if (count_px.getWorld().getName().replace(Variable.world_prefix, "").equalsIgnoreCase(MySQL.getJoinHome(pxx.getName()))) {
                              amountxx++;
                           }
                        }

                        boolean has_been_loadx = false;
                        if (Main.JavaPlugin.getConfig().getBoolean("MoveWorldAfterUnLoad")
                           && Bukkit.getWorld(Variable.world_prefix + MySQL.getJoinHome(pxx.getName())) != null) {
                           has_been_loadx = true;
                        }

                        if (Main.JavaPlugin.getConfig().getBoolean("AutoMoveWorldFilesToOther")
                           && !has_been_loadx
                           && !Variable.wait_to_command.containsKey(pxx.getName())
                           && Main.JavaPlugin.getConfig().getBoolean("BungeeCord")
                           && !Variable.has_already_move_world.contains(pxx.getName())
                           && amountxx == 0) {
                           if (Main.JavaPlugin.getConfig().getString("DecideBy").equalsIgnoreCase("Player")) {
                              if (!MySQL.getLowerstLagServer().equalsIgnoreCase(MySQL.getServer(MySQL.getJoinHome(pxx.getName())))
                                 && MySQL.getServerAmount(MySQL.getLowerstLagServer()) != Bukkit.getOnlinePlayers().size()) {
                                 try {
                                    Channel.waitToLoad(pxx, MySQL.getLowerstLagServer(), MySQL.getJoinHome(pxx.getName()));
                                 } catch (IOException var91) {
                                    var91.printStackTrace();
                                 }

                                 pxx.sendMessage(Variable.Lang_YML.getString("StartLowestLagServer"));
                                 Channel.sendPlayerToServer(pxx, MySQL.getLowerstLagServer());
                                 return false;
                              }
                           } else if (!MySQL.getHighestTPSServer().equalsIgnoreCase(MySQL.getServer(MySQL.getJoinHome(pxx.getName())))) {
                              double nowxxxx = 0.0;
                              if (Bukkit.getVersion().contains("1.7.10")) {
                                 nowxxxx = R1_7_10.getTps();
                              } else {
                                 double se1 = Double.valueOf(PlaceholderAPI.setPlaceholders(null, "%server_tps_1%").replace("*", ""));
                                 double se2 = Double.valueOf(PlaceholderAPI.setPlaceholders(null, "%server_tps_5%").replace("*", ""));
                                 double se3 = Double.valueOf(PlaceholderAPI.setPlaceholders(null, "%server_tps_15%").replace("*", ""));
                                 nowxxxx = (se1 + se2 + se3) / 3.0;
                              }

                              if (MySQL.getServerAmount(MySQL.getLowerstLagServer()) != nowxxxx) {
                                 try {
                                    Channel.waitToLoad(pxx, MySQL.getHighestTPSServer(), MySQL.getJoinHome(pxx.getName()));
                                 } catch (IOException var92) {
                                    var92.printStackTrace();
                                 }

                                 pxx.sendMessage(Variable.Lang_YML.getString("StartLowestLagServer"));
                                 Channel.sendPlayerToServer(pxx, MySQL.getHighestTPSServer());
                                 return false;
                              }
                           }
                        }

                        if (!MySQL.getJoinServer(pxx.getName()).equalsIgnoreCase(Main.JavaPlugin.getConfig().getString("Server"))) {
                           try {
                              Channel.waitDelayToSomeWhere(pxx, MySQL.getJoinServer(pxx.getName()), "sh h");
                           } catch (IOException var93) {
                              var93.printStackTrace();
                           }

                           Channel.sendPlayerToServer(pxx, MySQL.getJoinServer(pxx.getName()));
                           return false;
                        }
                     }

                     if (MySQL.alreadyhastheplayerhome(pxx.getName())) {
                        int amountxx = 0;

                        for (Player count_pxx : Bukkit.getOnlinePlayers()) {
                           if (count_pxx.getWorld().getName().replace(Variable.world_prefix, "").equalsIgnoreCase(pxx.getName())) {
                              amountxx++;
                           }
                        }

                        boolean has_been_loadxx = false;
                        if (Main.JavaPlugin.getConfig().getBoolean("MoveWorldAfterUnLoad") && Bukkit.getWorld(Variable.world_prefix + pxx.getName()) != null) {
                           has_been_loadxx = true;
                        }

                        if (Main.JavaPlugin.getConfig().getBoolean("AutoMoveWorldFilesToOther")
                           && !has_been_loadxx
                           && !Variable.wait_to_command.containsKey(pxx.getName())
                           && Main.JavaPlugin.getConfig().getBoolean("BungeeCord")
                           && !Variable.has_already_move_world.contains(pxx.getName())
                           && amountxx == 0) {
                           if (Main.JavaPlugin.getConfig().getString("DecideBy").equalsIgnoreCase("Player")) {
                              if (!MySQL.getLowerstLagServer().equalsIgnoreCase(MySQL.getServer(pxx.getName()))
                                 && MySQL.getServerAmount(MySQL.getLowerstLagServer()) != Bukkit.getOnlinePlayers().size()) {
                                 try {
                                    Channel.waitToLoad(pxx, MySQL.getLowerstLagServer(), pxx.getName());
                                 } catch (IOException var94) {
                                    var94.printStackTrace();
                                 }

                                 pxx.sendMessage(Variable.Lang_YML.getString("StartLowestLagServer"));
                                 Channel.sendPlayerToServer(pxx, MySQL.getLowerstLagServer());
                                 return false;
                              }
                           } else if (!MySQL.getHighestTPSServer().equalsIgnoreCase(MySQL.getServer(pxx.getName()))) {
                              double nowxxxxx = 0.0;
                              if (Bukkit.getVersion().contains("1.7.10")) {
                                 nowxxxxx = R1_7_10.getTps();
                              } else {
                                 double se1 = Double.valueOf(PlaceholderAPI.setPlaceholders(null, "%server_tps_1%").replace("*", ""));
                                 double se2 = Double.valueOf(PlaceholderAPI.setPlaceholders(null, "%server_tps_5%").replace("*", ""));
                                 double se3 = Double.valueOf(PlaceholderAPI.setPlaceholders(null, "%server_tps_15%").replace("*", ""));
                                 nowxxxxx = (se1 + se2 + se3) / 3.0;
                              }

                              if (MySQL.getServerAmount(MySQL.getLowerstLagServer()) != nowxxxxx) {
                                 try {
                                    Channel.waitToLoad(pxx, MySQL.getHighestTPSServer(), pxx.getName());
                                 } catch (IOException var95) {
                                    var95.printStackTrace();
                                 }

                                 pxx.sendMessage(Variable.Lang_YML.getString("StartLowestLagServer"));
                                 Channel.sendPlayerToServer(pxx, MySQL.getHighestTPSServer());
                                 return false;
                              }
                           }
                        }

                        if (!MySQL.getServer(pxx.getName()).equalsIgnoreCase(Main.JavaPlugin.getConfig().getString("Server"))) {
                           try {
                              Channel.waitDelayToSomeWhere(pxx, MySQL.getServer(pxx.getName()), "sh h");
                           } catch (IOException var96) {
                              var96.printStackTrace();
                           }

                           Channel.sendPlayerToServer(pxx, MySQL.getServer(pxx.getName()));
                           return false;
                        }
                     }

                     if (MySQL.alreadyhastheplayerjoin(pxx.getName())) {
                        World worldxxxxxxx = Bukkit.getWorld(Variable.world_prefix + MySQL.getJoinHome(pxx.getName()));
                        if (worldxxxxxxx == null) {
                           WorldCreator creator = new WorldCreator(Variable.world_prefix + MySQL.getJoinHome(pxx.getName()));
                           Variable.create_list_home.add(Variable.world_prefix + MySQL.getJoinHome(pxx.getName()));
                           Bukkit.createWorld(creator);
                        }

                        worldxxxxxxx = Bukkit.getWorld(Variable.world_prefix + MySQL.getJoinHome(pxx.getName()));
                        Location loc = worldxxxxxxx.getSpawnLocation();
                        loc = Util.getNotAir(loc);
                        loc.setX(Double.valueOf(MySQL.getX(MySQL.getJoinHome(pxx.getName()))));
                        loc.setY(Double.valueOf(MySQL.getY(MySQL.getJoinHome(pxx.getName()))));
                        loc.setZ(Double.valueOf(MySQL.getZ(MySQL.getJoinHome(pxx.getName()))));
                        pxx.teleport(loc);
                     } else {
                        World worldxxxxxxx = Bukkit.getWorld(Variable.world_prefix + pxx.getName());
                        if (worldxxxxxxx == null) {
                           WorldCreator creator = new WorldCreator(Variable.world_prefix + pxx.getName());
                           Variable.create_list_home.add(Variable.world_prefix + pxx.getName());
                           Bukkit.createWorld(creator);
                        }

                        worldxxxxxxx = Bukkit.getWorld(Variable.world_prefix + pxx.getName());
                        Location loc = worldxxxxxxx.getSpawnLocation();
                        loc = Util.getNotAir(loc);
                        loc.setX(Double.valueOf(MySQL.getX(pxx.getName())));
                        loc.setY(Double.valueOf(MySQL.getY(pxx.getName())));
                        loc.setZ(Double.valueOf(MySQL.getZ(pxx.getName())));
                        pxx.teleport(loc);
                     }
                  } else {
                     String what_has_been_join = "";
                     boolean has_been_join = false;
                     File folder = new File(Variable.Tempf);

                     File[] arrayOfFilexx;
                     for (File tempxxxxxxxxxxxxxxxxxxxxxx : arrayOfFilexx = folder.listFiles()) {
                        String want_to = tempxxxxxxxxxxxxxxxxxxxxxx.getPath()
                           .replace(Variable.Tempf, "")
                           .replace(".yml", "")
                           .replace(Variable.file_loc_prefix, "");
                        YamlConfiguration yamlConfigurationxxxxxxxxxx = YamlConfiguration.loadConfiguration(tempxxxxxxxxxxxxxxxxxxxxxx);

                        for (int ixxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx = 0;
                           ixxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx < yamlConfigurationxxxxxxxxxx.getStringList("OP").size();
                           ixxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx++
                        ) {
                           String temp_str = (String)yamlConfigurationxxxxxxxxxx.getStringList("OP").get(ixxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx);
                           if (temp_str.equalsIgnoreCase(pxx.getName())) {
                              what_has_been_join = want_to;
                              has_been_join = true;
                              break;
                           }
                        }

                        if (!has_been_join) {
                           for (int ixxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx = 0;
                              ixxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx < yamlConfigurationxxxxxxxxxx.getStringList("Members").size();
                              ixxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx++
                           ) {
                              String temp_str = (String)yamlConfigurationxxxxxxxxxx.getStringList("Members")
                                 .get(ixxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx);
                              if (temp_str.equalsIgnoreCase(pxx.getName()) || temp_str.equals("*")) {
                                 what_has_been_join = want_to;
                                 has_been_join = true;
                                 break;
                              }
                           }
                        }
                     }

                     String primaryOwnedHome = HomeAPI.getPrimaryOwnedHome(pxx.getName());
                     File f2xxxxxxxxxxx = primaryOwnedHome != null ? new File(Variable.Tempf, primaryOwnedHome + ".yml") : null;
                     if ((f2xxxxxxxxxxx == null || !f2xxxxxxxxxxx.exists()) && !has_been_join) {
                        String tempxxxxxxxxxxxxxxxxxxxxxx = Variable.Lang_YML.getString("NoCreateOrJoin");
                        sender.sendMessage(Variable.Lang_YML.getString("HeadLineTtitle"));
                        sender.sendMessage(tempxxxxxxxxxxxxxxxxxxxxxx);
                        sender.sendMessage(Variable.Lang_YML.getString("BottomLineTtitle"));
                        return false;
                     }

                     if (!what_has_been_join.equalsIgnoreCase("")) {
                        World worldxxxxxxx = Bukkit.getWorld(Variable.world_prefix + what_has_been_join);
                        if (worldxxxxxxx == null) {
                           WorldCreator creator = new WorldCreator(Variable.world_prefix + what_has_been_join);
                           Variable.create_list_home.add(Variable.world_prefix + what_has_been_join);
                           Bukkit.createWorld(creator);
                        }

                        worldxxxxxxx = Bukkit.getWorld(Variable.world_prefix + what_has_been_join);
                        Location loc = worldxxxxxxx.getSpawnLocation();
                        loc = Util.getNotAir(loc);
                        File tp_set = new File(Variable.Tempf, worldxxxxxxx.getName().replace(Variable.world_prefix, "") + ".yml");
                        YamlConfiguration yamlConfigurationxxxxxxxxxx = YamlConfiguration.loadConfiguration(tp_set);
                        loc.setX(yamlConfigurationxxxxxxxxxx.getDouble("X"));
                        loc.setY(yamlConfigurationxxxxxxxxxx.getDouble("Y"));
                        loc.setZ(yamlConfigurationxxxxxxxxxx.getDouble("Z"));
                        loc = Util.getNotAir(loc);
                        pxx.teleport(loc);
                     } else {
                        String targetHome = primaryOwnedHome != null ? primaryOwnedHome : pxx.getName();
                        World worldxxxxxxx = Bukkit.getWorld(Variable.world_prefix + targetHome);
                        if (worldxxxxxxx == null) {
                           WorldCreator creator = new WorldCreator(Variable.world_prefix + targetHome);
                           Variable.create_list_home.add(Variable.world_prefix + targetHome);
                           Bukkit.createWorld(creator);
                        }

                        worldxxxxxxx = Bukkit.getWorld(Variable.world_prefix + targetHome);
                        Location loc = worldxxxxxxx.getSpawnLocation();
                        loc = Util.getNotAir(loc);
                        File tp_set = new File(Variable.Tempf, targetHome + ".yml");
                        YamlConfiguration yamlConfigurationxxxxxxxxxx = YamlConfiguration.loadConfiguration(tp_set);
                        loc.setX(yamlConfigurationxxxxxxxxxx.getDouble("X"));
                        loc.setY(yamlConfigurationxxxxxxxxxx.getDouble("Y"));
                        loc.setZ(yamlConfigurationxxxxxxxxxx.getDouble("Z"));
                        loc = Util.getNotAir(loc);
                        pxx.teleport(loc);
                     }
                  }

                  return false;
               }
            }
         }
      } else {
         MainGui gui = new MainGui(pxx);
         pxx.openInventory(gui.getInventory());
         return false;
      }
   }
   public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
      if (args.length == 1) {
         List<String> list = new ArrayList<>();
         list.add("open");
         list.add("home");
         list.add("homes");
         list.add("create");
         list.add("look");
         list.add("tpSet");
         list.add("invite");
         list.add("accept");
         list.add("deny");
         list.add("add");
         list.add("pvp");
         list.add("drop");
         list.add("pickup");
         list.add("public");
         list.add("setspawn");
         list.add("kick");
         list.add("remove");
         list.add("check");
         list.add("rank");
         list.add("sun");
         list.add("rain");
         list.add("night");
         list.add("day");
         list.add("lockTime");
         list.add("lockWeather");
         list.add("reload");
         list.add("mobs");
         list.add("nbt");
         list.add("admin");
         list.add("wholedelete");
         list.add("forceDelete");
         list.add("unLoad");
         list.add("MobSpawn");
         list.add("GameMode");
         list.add("flower");
         list.add("popularity");
         list.add("gift");
         list.add("icon");
         list.add("info");
         list.add("setBiome");
         list.add("setspawn");
         list.add("clearsetspawncd");
         return list;
      } else {
         if (args.length == 2) {
            if (args[0].equalsIgnoreCase("info")) {
               List<String> list = new ArrayList<>();
               list.add("第一,第二,第三行[逗号为分隔符]");
               return list;
            }

            if (args[0].equalsIgnoreCase("home") || args[0].equalsIgnoreCase("h")) {
               List<String> list = new ArrayList<>();
               if (sender instanceof Player && !Variable.bungee) {
                  Player player = (Player)sender;
                  List<String> ownedHomes = HomeAPI.getOwnedHomes(player.getName());

                  for (int i = 0; i < ownedHomes.size(); i++) {
                     list.add(String.valueOf(i + 1));
                  }

                  list.addAll(ownedHomes);
               }

               return list;
            }

            if (args[0].equalsIgnoreCase("create")) {
               List<String> list = new ArrayList<>();
               list.add("1");
               list.add("2");
               list.add("其他类型");
               return list;
            }

            if (args[0].equalsIgnoreCase("invite")) {
               List<String> list = new ArrayList<>();

               for (Player p : Bukkit.getOnlinePlayers()) {
                  list.add(p.getName());
               }

               return list;
            }

            if (args[0].equalsIgnoreCase("kick")) {
               List<String> list = new ArrayList<>();

               for (Player p : Bukkit.getOnlinePlayers()) {
                  list.add(p.getName());
               }

               return list;
            }

            if (args[0].equalsIgnoreCase("add")) {
               List<String> list = new ArrayList<>();

               for (Player p : Bukkit.getOnlinePlayers()) {
                  list.add(p.getName());
               }

               return list;
            }

            if (args[0].equalsIgnoreCase("remove")) {
               List<String> list = new ArrayList<>();

               for (Player p : Bukkit.getOnlinePlayers()) {
                  list.add(p.getName());
               }

               return list;
            }

            if (args[0].equalsIgnoreCase("deny")) {
               List<String> list = new ArrayList<>();

               for (Player p : Bukkit.getOnlinePlayers()) {
                  list.add(p.getName());
               }

               return list;
            }

            if (args[0].equalsIgnoreCase("SetBiome")) {
               List<String> list = new ArrayList<>();

               for (Biome b : Biome.values()) {
                  list.add(b.toString());
               }

               return list;
            }

            if (args[0].equalsIgnoreCase("setspawn")) {
               List<String> list = new ArrayList<>();
               list.add("<x>");
               list.add("<y>");
               list.add("<z>");
               return list;
            }

            if (args[0].equalsIgnoreCase("clearsetspawncd")) {
               List<String> list = new ArrayList<>();

               for (Player player : Bukkit.getOnlinePlayers()) {
                  list.add(player.getName());
               }

               return list;
            }

            if (args[0].equalsIgnoreCase("undeny")) {
               List<String> list = new ArrayList<>();

               for (Player p : Bukkit.getOnlinePlayers()) {
                  list.add(p.getName());
               }

               return list;
            }

            if (args[0].equalsIgnoreCase("rank")) {
               List<String> list = new ArrayList<>();
               list.add("1");
               list.add("2");
               list.add("3");
               list.add("4");
               list.add("5");
               list.add("6");
               return list;
            }

            if (args[0].equalsIgnoreCase("unLoad")) {
               List<String> list = new ArrayList<>();

               for (World p : Bukkit.getWorlds()) {
                  list.add(p.getName());
               }

               return list;
            }

            if (args[0].equalsIgnoreCase("forcedelete")) {
               List<String> list = new ArrayList<>();

               for (World p : Bukkit.getWorlds()) {
                  list.add(p.getName().replaceAll(Variable.world_prefix, ""));
               }

               return list;
            }

            if (args[0].equalsIgnoreCase("admin")) {
               List<String> list = new ArrayList<>();
               list.add("setSpawn");
               list.add("dimension");
               list.add("export");
               list.add("import");
               list.add("setlevel");
               list.add("pwp");
               return list;
            }

            if (args[0].equalsIgnoreCase("gift")) {
               List<String> list = new ArrayList<>();
               list.add("open");
               list.add("send");
               list.add("inv");
               return list;
            }

            if (args[0].equalsIgnoreCase("flower")) {
               List<String> list = new ArrayList<>();
               list.add("add");
               return list;
            }

            if (args[0].equalsIgnoreCase("popularity")) {
               List<String> list = new ArrayList<>();
               list.add("add");
               return list;
            }

            if (args[0].equalsIgnoreCase("GameMode")) {
               List<String> list = new ArrayList<>();
               list.add("EASY");
               list.add("HARD");
               list.add("PEACEFUL");
               return list;
            }
         }

         if (args.length == 3 && args[0].equalsIgnoreCase("gift") && args[1].equalsIgnoreCase("send")) {
            List<String> list = new ArrayList<>();

            for (Player p : Bukkit.getOnlinePlayers()) {
               list.add(p.getName());
            }

            return list;
         } else if (args.length == 3 && args[0].equalsIgnoreCase("gift") && args[1].equalsIgnoreCase("inv")) {
            List<String> list = new ArrayList<>();

            for (Player p : Bukkit.getOnlinePlayers()) {
               list.add(p.getName());
            }

            return list;
         } else if (args.length == 3 && args[0].equalsIgnoreCase("flower") && args[1].equalsIgnoreCase("add")) {
            List<String> list = new ArrayList<>();

            for (Player p : Bukkit.getOnlinePlayers()) {
               list.add(p.getName());
            }

            return list;
         } else if (args.length == 3 && args[0].equalsIgnoreCase("popularity") && args[1].equalsIgnoreCase("add")) {
            List<String> list = new ArrayList<>();

            for (Player p : Bukkit.getOnlinePlayers()) {
               list.add(p.getName());
            }

            return list;
         } else {
            return null;
         }
      }
   }
}

