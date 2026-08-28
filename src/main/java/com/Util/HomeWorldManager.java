package com.Util;

import com.ErrorTown.Main;
import com.ErrorTown.Variable;
import java.io.IOException;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

public class HomeWorldManager {
   public static boolean isHomeWorld(World world) {
      return world == null ? false : Util.CheckIsHome(Util.getBaseHomeName(world.getName()));
   }

   public static void markActive(String baseName) {
      Home home = HomeAPI.getHome(baseName);
      if (home != null) {
         try {
            home.setLastActive(System.currentTimeMillis());
         } catch (IOException ioFailure) {
            // lastActive drives NoBackup and the idle-unload decision; a silent failure
            // makes both operate on a stale timestamp.
            com.Util.Diag.warn("Could not persist lastActive for home " + baseName, ioFailure);
         }
      }
   }

   public static void cancelUnload(String baseName) {
      Integer taskId = Variable.homeUnloadTaskIds.remove(baseName);
      if (taskId != null) {
         Bukkit.getScheduler().cancelTask(taskId);
      }
   }

   public static void scheduleUnload(final String baseName) {
      if (Main.JavaPlugin.getConfig().getBoolean("HomeWorldLifecycle.EnableAutoUnload", true)) {
         cancelUnload(baseName);
         long delayTicks = Math.max(20L, Main.JavaPlugin.getConfig().getLong("HomeWorldLifecycle.UnloadDelaySeconds", 180L) * 20L);
         BukkitRunnable task = new BukkitRunnable() {
            public void run() {
               Variable.homeUnloadTaskIds.remove(baseName);
               HomeWorldManager.unloadIfIdle(baseName);
            }
         };
         task.runTaskLater(Main.JavaPlugin, delayTicks);
         Variable.homeUnloadTaskIds.put(baseName, task.getTaskId());
      }
   }

   public static void unloadIfIdle(String baseName) {
      unloadOne(Variable.world_prefix + baseName);
      unloadOne(Variable.world_prefix + baseName + Util.getNetherSuffix());
   }

   private static void unloadOne(String worldName) {
      World world = Bukkit.getWorld(worldName);
      if (world != null) {
         if (world.getPlayers().isEmpty()) {
            Bukkit.unloadWorld(world, true);
         }
      }
   }

   public static void showHomeInfo(Player player, Home home, World world) {
      if (player != null && home != null && world != null) {
         if (Main.JavaPlugin.getConfig().getBoolean("HomeInfoPanel.Enable", true)) {
            // Use the same VIP radius bonus the world border was built with. Hardcoding
            // 0 made the panel disagree with the real border for every VIP player.
            int vipAdd = Util.getPermissionBorderBonus(home);
            int radius = HomeTerrainPolicy.configuredBorderSize(
                  home.getLevel(),
                  Main.JavaPlugin.getConfig().getBoolean("HomeTerrain.Enabled", false),
                  Main.JavaPlugin.getConfig().getIntegerList("HomeUpgrade.LevelSizes"),
                  Main.JavaPlugin.getConfig().getInt("WorldBoard"),
                  Main.JavaPlugin.getConfig().getInt("UpdateRadius"),
                  vipAdd
               ) / 2;
            player.sendMessage("§8[§6错误庄园§8] §f等级: §e" + home.getLevel() + " §7| 边界半径: §b" + radius + " §7| 成员: §a" + home.getMembers().size());
            player.sendMessage(
               "§8[§6错误庄园§8] §f怪物: "
                  + (home.getRuleAllowHostileMobs() ? "§a开" : "§c关")
                  + " §7| 被动生物: "
                  + (home.getRuleAllowPassiveMobs() ? "§a开" : "§c关")
                  + " §7| 刷怪上限: §e"
                  + home.getRuleMaxMobCount()
            );
            player.sendMessage(
               "§8[§6错误庄园§8] §fPVP: "
                  + (home.isPvp() ? "§c开" : "§a关")
                  + " §7| 火焰蔓延: "
                  + (home.getRuleNoFireSpread() ? "§c关" : "§a开")
                  + " §7| 爆炸: "
                  + (home.getRuleExplosionProtect() ? "§c关" : "§a开")
            );
         }
      }
   }
}
