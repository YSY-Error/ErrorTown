package com.Util;

import com.ErrorTown.Main;
import com.ErrorTown.Variable;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import org.bukkit.Bukkit;
import org.bukkit.configuration.Configuration;
import org.bukkit.configuration.file.FileConfiguration;

public class ConfigUpdate {
   private static boolean migrateLegacyHomeMobCapDefaults() {
      FileConfiguration config = Main.JavaPlugin.getConfig();
      String markerPath = "InternalMigrations.HomeRulesMobCapDefaults20260618";
      if (config.getBoolean(markerPath, false)) {
         return false;
      } else {
         boolean changed = false;
         if (config.getInt("HomeRulesDefaults.MaxMobCount", 48) == 48) {
            config.set("HomeRulesDefaults.MaxMobCount", 0);
            changed = true;
         }

         if (config.getBoolean("HomeRulesDefaults.CountVillagersInMobCap", true)) {
            config.set("HomeRulesDefaults.CountVillagersInMobCap", false);
            changed = true;
         }

         config.set(markerPath, true);
         return true;
      }
   }

   private static boolean migrateNaturalHomeDefaults() {
      FileConfiguration config = Main.JavaPlugin.getConfig();
      if (!config.getBoolean("HomeTerrain.Enabled", false)
         || config.getBoolean("InternalMigrations.NaturalHomeTerrain20260826", false)) {
         return false;
      }
      config.set("NormalType", 1);
      config.set("WorldBoard", 8);
      config.set("UpdateRadius", 8);
      config.set("MaxLevel", 12);
      config.set("generateStructures", false);
      config.set("MaxSpawnMonstersAmount", 0);
      config.set("MaxSpawnAnimalsAmount", 0);
      config.set("DisablePortalCreate", true);
      config.set("InternalMigrations.NaturalHomeTerrain20260826", true);
      return true;
   }

   private static boolean setConfigDefault(String path, Object value) {
      if (Main.JavaPlugin.getConfig().contains(path)) {
         return false;
      } else {
         Main.JavaPlugin.getConfig().set(path, value);
         return true;
      }
   }

   private static boolean mergeMissingDefaultConfigKeys() {
      boolean changed = false;
      FileConfiguration current = Main.JavaPlugin.getConfig();
      Configuration defaults = Main.JavaPlugin.getConfig().getDefaults();
      if (defaults == null) {
         return false;
      } else {
         for (String key : defaults.getKeys(true)) {
            if (!defaults.isConfigurationSection(key) && !current.contains(key)) {
               current.set(key, defaults.get(key));
               changed = true;
            }
         }

         return changed;
      }
   }

   private static boolean isBrokenText(String text) {
      return text != null
         && (
            text.contains("搂")
               || text.contains("璁")
               || text.contains("涓")
               || text.contains("鍐")
               || text.contains("绉")
               || text.contains("�")
               || text.contains(">>> Next")
               || text.contains(">>> Prev")
               || text.endsWith("已自动更")
         );
   }

   private static boolean setGuiStringIfMissingOrBroken(String path, String value) {
      String current = Variable.GUI_YML.getString(path);
      if (current != null && !isBrokenText(current)) {
         return false;
      } else {
         Variable.GUI_YML.set(path, value);
         return true;
      }
   }

   private static boolean setLangStringIfMissingOrBroken(String path, String value) {
      String current = Variable.Lang_YML.getString(path);
      if (current != null && !isBrokenText(current)) {
         return false;
      } else {
         Variable.Lang_YML.set(path, value);
         return true;
      }
   }

   private static boolean setLangListIfMissingOrBroken(String path, List<String> value) {
      List<String> current = Variable.Lang_YML.getStringList(path);
      if (current != null && !current.isEmpty()) {
         for (String line : current) {
            if (isBrokenText(line)) {
               Variable.Lang_YML.set(path, value);
               return true;
            }
         }

         return false;
      } else {
         Variable.Lang_YML.set(path, value);
         return true;
      }
   }

   private static boolean setGuiListIfMissingOrBroken(String path, List<String> value) {
      List<String> current = Variable.GUI_YML.getStringList(path);
      if (current != null && !current.isEmpty()) {
         for (String line : current) {
            if (isBrokenText(line)) {
               Variable.GUI_YML.set(path, value);
               return true;
            }
         }

         return false;
      } else {
         Variable.GUI_YML.set(path, value);
         return true;
      }
   }

   private static boolean repairGuiAndLanguageTexts() {
      boolean changed = false;
      Map<String, String> guiStrings = new LinkedHashMap<>();
      guiStrings.put("MainTitle", "§b>>> §d错误庄园");
      guiStrings.put("CheckTitle", "§b>>> §d返回伙伴的庄园");
      guiStrings.put("CreateTitle", "§b>>> §d创建你的专属庄园");
      guiStrings.put("ManageTitle", "§b>>> §d庄园管理 · 一");
      guiStrings.put("Manage2Title", "§b>>> §d庄园管理 · 二");
      guiStrings.put("Manage3Title", "§b>>> §d庄园管理 · 三");
      guiStrings.put("VisitTitle", "§b>>> §d选择要前往的庄园");
      guiStrings.put("InviteTitle", "§b>>> §d邀请伙伴加入庄园");
      guiStrings.put("TrustTitle", "§b>>> §d信任伙伴协助管理");
      guiStrings.put("DenyTitle", "§b>>> §d设置庄园黑名单");
      guiStrings.put("Next", "§a>>> 下一页");
      guiStrings.put("Prev", "§a<<< 上一页");
      guiStrings.put("Button20.CustomName", "§8[§a下一页§8]");
      guiStrings.put("Button21.CustomName", "§8[§a返回§8]");
      guiStrings.put("Button22.CustomName", "§8[§a锁定天气§8]");
      guiStrings.put("Button23.CustomName", "§8[§a锁定时间§8]");
      guiStrings.put("Button28.CustomName", "§8[§a设置边界中心§8]");
      guiStrings.put("Button29.CustomName", "§8[§a退出加入的家园§8]");
      guiStrings.put("Button30.CustomName", "§8[§a删除家园§8]");
      guiStrings.put("Button31.CustomName", "§8[§a返回§8]");
      guiStrings.put("Button32.CustomName", "§8[§a上一页§8]");
      guiStrings.put("Button33.CustomName", "§8[§a返回§8]");
      guiStrings.put("Button34.CustomName", "§8[§a返回§8]");
      guiStrings.put("Button35.CustomName", "§8[§a返回§8]");
      guiStrings.put("Button36.CustomName", "§8[§a返回§8]");
      guiStrings.put("Button37.CustomName", "§8[§c公共地狱§8]");
      guiStrings.put("Button38.CustomName", "§8[§c公共末地§8]");
      guiStrings.put("Button50.CustomName", "§8[§a下一页§8]");
      guiStrings.put("Button51.CustomName", "§8[§a群系修改§8]");
      guiStrings.put("Button52.CustomName", "§8[§a难度设置§8]");
      guiStrings.put("Button53.CustomName", "§8[§a扩容成员§8]");
      guiStrings.put("Button54.CustomName", "§8[§a创建下界§8]");
      guiStrings.put("Button56.CustomName", "§8[§a重置主世界§8]");
      guiStrings.put("Button57.CustomName", "§8[§a重置下界§8]");
      guiStrings.put("Button59.CustomName", "§8[§a设置标题§8]");
      guiStrings.put("Button60.CustomName", "§8[§a天气控制§8]");
      guiStrings.put("Button61.CustomName", "§8[§a时间控制§8]");
      guiStrings.put("Button63.CustomName", "§8[§a上一页§8]");
      guiStrings.put("Button64.CustomName", "§8[§a返回§8]");
      guiStrings.put("Button65.CustomName", "§8[§a返回主城§8]");
      guiStrings.put("Button66.CustomName", "§8[§a规则设置§8]");
      guiStrings.put("Button67.CustomName", "§8[§a仓库§8]");

      for (Entry<String, String> entry : guiStrings.entrySet()) {
         if (setGuiStringIfMissingOrBroken(entry.getKey(), entry.getValue())) {
            changed = true;
         }
      }

      if (setGuiListIfMissingOrBroken(
         "VisitGuiLores",
         Arrays.asList(
            "§d§l§m---------------%ErrorTown_World_Name_<Name>%§d§l§m---------------",
            "§8鲜花数: §6%ErrorTown_World_Flower_<Name>%",
            "§8人气值: §6%ErrorTown_World_Popularity_<Name>%",
            "§8热度指数: §6%ErrorTown_World_Calc_<Name>%",
            "",
            "§8管理员: §6%ErrorTown_World_ManageList_<Name>%",
            "§8信任列表: §6%ErrorTown_World_TrustList_<Name>%",
            "§8黑名单: §6%ErrorTown_World_BlackList_<Name>%",
            "",
            "§8家园等级: §6%ErrorTown_World_Level_<Name>%",
            "§8家园类型: §6%ErrorTown_World_Type_<Name>%",
            "§7点击即可前往",
            "",
            "§d§l§m---------------%ErrorTown_World_Name_<Name>%§d§l§m---------------"
         )
      )) {
         changed = true;
      }

      if (setGuiListIfMissingOrBroken("Button28.Lores", Arrays.asList("§7打开边界中心迁移菜单", "§e可选择脚下坐标 或 手动输入坐标", "§d费用: §62000 金币", "§724小时冷却"))) {
         changed = true;
      }

      if (setGuiListIfMissingOrBroken("Button37.Lores", Arrays.asList("§7传送到服务器公共地狱"))) {
         changed = true;
      }

      if (setGuiListIfMissingOrBroken("Button38.Lores", Arrays.asList("§7传送到服务器公共末地"))) {
         changed = true;
      }

      if (setGuiListIfMissingOrBroken("Button66.Lores", Arrays.asList("§7爆炸保护 / 火势蔓延 / 怪物生成上限", "§e点击打开详细规则菜单"))) {
         changed = true;
      }

      if (setGuiListIfMissingOrBroken("Button67.Lores", Arrays.asList("§7打开你的个人仓库"))) {
         changed = true;
      }

      if (setLangStringIfMissingOrBroken("UpdateConfigMessage", "§8[§6错误庄园§8] §7配置文件Config.yml已自动更新")) {
         changed = true;
      }

      if (setLangStringIfMissingOrBroken("UpdateGuiMessage", "§8[§6错误庄园§8] §7配置文件Gui.yml已自动更新")) {
         changed = true;
      }

      if (setLangStringIfMissingOrBroken("UpdateLanguageMessage", "§8[§6错误庄园§8] §7配置文件Language.yml已自动更新")) {
         changed = true;
      }

      if (setLangListIfMissingOrBroken("DenyGuiLores", Arrays.asList("§6[左键] -> §d加入黑名单", "§6[右键] -> §d移出黑名单"))) {
         changed = true;
      }

      if (setLangListIfMissingOrBroken("InviteGuiLores", Arrays.asList("§6[左键] -> §d邀请成为庄园管理员", "§6[右键] -> §d移除该庄园管理员"))) {
         changed = true;
      }

      if (setLangListIfMissingOrBroken("TrustGuiLores", Arrays.asList("§6[左键] -> §d加入信任名单", "§6[右键] -> §d移出信任名单"))) {
         changed = true;
      }

      return changed;
   }

   public static void update() {
      boolean config_check = false;
      boolean lang_check = false;
      boolean gui_check = false;
      if (repairGuiAndLanguageTexts()) {
         gui_check = true;
         lang_check = true;
      }

      if (mergeMissingDefaultConfigKeys()) {
         config_check = true;
      }

      if (setConfigDefault("InviteAccess.MaxTotalHomes", 3)) {
         config_check = true;
      }

      if (migrateLegacyHomeMobCapDefaults()) {
         config_check = true;
      }

      if (migrateNaturalHomeDefaults()) {
         config_check = true;
      }

      if (Main.JavaPlugin.getConfig().isConfigurationSection("Version")) {
         Main.JavaPlugin.getConfig().set("Version", 2.1);
         Main.JavaPlugin.saveConfig();
      }

      if (Main.JavaPlugin.getConfig().getDouble("Version") < 1.97) {
         if (!Main.JavaPlugin.getConfig().isConfigurationSection("ClearInventoryBeforeCreate")) {
            Main.JavaPlugin.getConfig().set("ClearInventoryBeforeCreate", false);
            config_check = true;
         }

         if (Main.JavaPlugin.getConfig().getString("DispathCommand") == null) {
            List<String> list = new ArrayList<>();
            Main.JavaPlugin.getConfig().set("DispathCommand", list);
            config_check = true;
         }

         if (Main.JavaPlugin.getConfig().getString("ArmorStand") == null) {
            Main.JavaPlugin.getConfig().set("ArmorStand", -1);
            config_check = true;
         }

         if (Variable.Lang_YML.getString("AdminSetLevelSuccess") == null) {
            Variable.Lang_YML.set("AdminSetLevelSuccess", "§7[ErrorTown] §c成功设置该家园的等级!");
            lang_check = true;
         }

         if (Variable.Lang_YML.getString("ClearInventoryBeforeCreate") == null) {
            Variable.Lang_YML.set("ClearInventoryBeforeCreate", "§7[ErrorTown] §d创建家园时背包已清空完毕!");
            lang_check = true;
         }

         config_check = true;
      }

      if (Main.JavaPlugin.getConfig().getDouble("Version") < 1.98 && !Main.JavaPlugin.getConfig().isConfigurationSection("EnableAutoRespawnInHome")) {
         Main.JavaPlugin.getConfig().set("EnableAutoRespawnInHome", false);
         config_check = true;
      }

      if (Main.JavaPlugin.getConfig().getDouble("Version") < 1.99) {
         if (Variable.GUI_YML.getString("Next") == null) {
            Variable.GUI_YML.set("Next", "§a>>> 下一页");
            gui_check = true;
         }

         if (Variable.GUI_YML.getString("NextMaterial") == null) {
            Variable.GUI_YML.set("NextMaterial", "FEATHER");
            gui_check = true;
         }

         if (Variable.GUI_YML.getString("Prev") == null) {
            Variable.GUI_YML.set("Prev", "§a<<< 上一页");
            gui_check = true;
         }

         if (Variable.GUI_YML.getString("PrevMaterial") == null) {
            Variable.GUI_YML.set("PrevMaterial", "FEATHER");
            gui_check = true;
         }

         if (Variable.Lang_YML.getString("UpdateLanguageMessage") == null) {
            Variable.Lang_YML.set("UpdateLanguageMessage", "§8[§6错误庄园§8] §7配置文件Language.yml已自动更新");
            lang_check = true;
         }

         if (Variable.Lang_YML.getString("UpdateGuiMessage") == null) {
            Variable.Lang_YML.set("UpdateGuiMessage", "§8[§6错误庄园§8] §7配置文件Gui.yml已自动更新");
            lang_check = true;
         }

         if (Variable.Lang_YML.getString("UpdateConfigMessage") == null) {
            Variable.Lang_YML.set("UpdateConfigMessage", "§8[§6错误庄园§8] §7配置文件Config.yml已自动更新");
            lang_check = true;
         }

         config_check = true;
      }

      if (Main.JavaPlugin.getConfig().getDouble("Version") < 1.992) {
         if (!Main.JavaPlugin.getConfig().isConfigurationSection("DisableFunctionButTeleport")) {
            Main.JavaPlugin.getConfig().set("DisableFunctionButTeleport", false);
            config_check = true;
         }

         if (Variable.Lang_YML.getString("DisableFunctionButTeleport") == null) {
            Variable.Lang_YML.set("DisableFunctionButTeleport", "§7[ErrorTown] §d已为您关闭除了GUI和传送指令以外其他所有功");
            lang_check = true;
         }

         if (Variable.Lang_YML.getString("DisableFunctionTip") == null) {
            Variable.Lang_YML.set("DisableFunctionTip", "§7[ErrorTown] §d本插件除了传送以外的其他功能已被服主关闭,请前家园服务器使用该命令");
            lang_check = true;
         }

         config_check = true;
      }

      if (Main.JavaPlugin.getConfig().getDouble("Version") < 1.993) {
         if (Variable.Lang_YML.getString("EnableMobSpawn") == null) {
            Variable.Lang_YML.set("EnableMobSpawn", "§7[ErrorTown] §d成功启家园的刷�功");
            lang_check = true;
         }

         if (Variable.Lang_YML.getString("DisableMobSpawn") == null) {
            Variable.Lang_YML.set("DisableMobSpawn", "§7[ErrorTown] §d成功关闭家园的刷怪功");
            lang_check = true;
         }

         if (Variable.Lang_YML.getString("AutoUpdateHomeLevel") == null) {
            Variable.Lang_YML.set("AutoUpdateHomeLevel", "§7[ErrorTown] §d测到您有更高级别的家园等级权,为您自动提升家园等级!");
            lang_check = true;
         }

         config_check = true;
      }

      if (Main.JavaPlugin.getConfig().getDouble("Version") < 1.994) {
         if (!Main.JavaPlugin.getConfig().isConfigurationSection("EnableTilesAndChunksAndDropItemsStatisticsTop")) {
            Main.JavaPlugin.getConfig().set("EnableTilesAndChunksAndDropItemsStatisticsTop", true);
            config_check = true;
         }

         if (!Main.JavaPlugin.getConfig().isConfigurationSection("ShowAmount")) {
            Main.JavaPlugin.getConfig().set("ShowAmount", 8);
            config_check = true;
         }

         if (!Main.JavaPlugin.getConfig().isConfigurationSection("ShowTimes")) {
            Main.JavaPlugin.getConfig().set("ShowTimes", 300);
            config_check = true;
         }

         if (!Main.JavaPlugin.getConfig().isConfigurationSection("OneTileTick")) {
            Main.JavaPlugin.getConfig().set("OneTileTick", 0.005);
            config_check = true;
         }

         if (!Main.JavaPlugin.getConfig().isConfigurationSection("OneEntityTick")) {
            Main.JavaPlugin.getConfig().set("OneEntityTick", 0.005);
            config_check = true;
         }

         if (!Main.JavaPlugin.getConfig().isConfigurationSection("OneChunkTick")) {
            Main.JavaPlugin.getConfig().set("OneChunkTick", 0.0);
            config_check = true;
         }

         if (!Main.JavaPlugin.getConfig().isConfigurationSection("OneDropTick")) {
            Main.JavaPlugin.getConfig().set("OneDropTick", 0.001);
            config_check = true;
         }

         if (!Main.JavaPlugin.getConfig().isConfigurationSection("FormatInfo")) {
            Main.JavaPlugin.getConfig().set("FormatInfo", "%.2f");
            config_check = true;
         }

         if (!Main.JavaPlugin.getConfig().isConfigurationSection("StatisticsTop")) {
            List<String> list = new ArrayList<>();
            list.add("§7当前服务器玩家世界占用情况：");
            list.add("§8§m————————————————————————————————————————————————————————");
            Main.JavaPlugin.getConfig().set("StatisticsTop", list);
            config_check = true;
         }

         if (!Main.JavaPlugin.getConfig().isConfigurationSection("ShowFormat")) {
            Main.JavaPlugin
               .getConfig()
               .set("ShowFormat", "§6§l#<index> §a<world> §8的世 §7<tile> §8方块实体 §7<chunk> §8区块  §7<entity> §8实体  §7<drop> §8掉落  §8每Tick耗时 §7<tps> §8Ms");
            config_check = true;
         }

         if (!Main.JavaPlugin.getConfig().isConfigurationSection("StatisticsEnd")) {
            List<String> list = new ArrayList<>();
            list.add("§8§m————————————————————————————————————————————————————————");
            list.add("§a上方世界仅供参�，如果如果存在你的谁请及时清理掉落物和生物");
            Main.JavaPlugin.getConfig().set("StatisticsEnd", list);
            config_check = true;
         }

         config_check = true;
      }

      if (Main.JavaPlugin.getConfig().getDouble("Version") < 1.996) {
         if (Variable.Lang_YML.getString("DifficultyModify") == null) {
            Variable.Lang_YML.set("DifficultyModify", "§7[ErrorTown] §d为您当前家园调整<Mode>模式!");
            lang_check = true;
         }

         config_check = true;
      }

      if (Main.JavaPlugin.getConfig().getDouble("Version") < 1.997) {
         if (Main.JavaPlugin.getConfig().isConfigurationSection("AutoReCreateInLowerLagHome")) {
            Main.JavaPlugin.getConfig().set("AutoReCreateInLowerLagHome", false);
            config_check = true;
         }

         if (Variable.Lang_YML.getString("StartLowestLagServer") == null) {
            Variable.Lang_YML.set("StartLowestLagServer", "§7[ErrorTown] §d正在为您进行均衡负载模式进行创建家园,请等待~");
            lang_check = true;
         }

         config_check = true;
      }

      if (Main.JavaPlugin.getConfig().getDouble("Version") < 1.998) {
         if (Variable.Lang_YML.getString("WorldIsNotExist") == null) {
            Variable.Lang_YML.set("WorldIsNotExist", "§7[ErrorTown] §d世界不存,请检查世界存档是否存放在地图目录/本插件地图目录下~");
            lang_check = true;
         }

         if (Variable.Lang_YML.getString("WorldTeleport") == null) {
            Variable.Lang_YML.set("WorldTeleport", "§7[ErrorTown] §d世界传�成功~");
            lang_check = true;
         }

         config_check = true;
      }

      if (Main.JavaPlugin.getConfig().getDouble("Version") < 1.999 && Main.JavaPlugin.getConfig().isConfigurationSection("DecideBy")) {
         Main.JavaPlugin.getConfig().set("DecideBy", "TPS");
         config_check = true;
      }

      if (Main.JavaPlugin.getConfig().getDouble("Version") < 2.0) {
         if (Variable.Lang_YML.getString("ProtectFarm") == null) {
            Variable.Lang_YML.set("ProtectFarm", "§7[ErrorTown] §d当前耕地被保护着,您没有权,请勿践踏!");
            lang_check = true;
         }

         if (Variable.Lang_YML.getString("ProtectEntity") == null) {
            Variable.Lang_YML.set("ProtectEntity", "§7[ErrorTown] §d当前实体被保护着,您没有权,请勿触碰!");
            lang_check = true;
         }

         config_check = true;
      }

      if (Main.JavaPlugin.getConfig().getDouble("Version") < 2.001) {
         if (Variable.Lang_YML.getString("SoilTypeError") == null) {
            Variable.Lang_YML.set("SoilTypeError", "§7[ErrorTown] §dConfig.yml配置的�地类型SoilType错误,类型可以选择:SOIL/FARMLAND,1.16.5-Cat端使用FARMLAND");
            lang_check = true;
         }

         if (Main.JavaPlugin.getConfig().getString("SoilType") == null) {
            // FARMLAND, not the historical SOIL: SOIL stopped existing in 1.13, and an unresolvable
            // value used to disable the whole interact-protection listener silently.
            Main.JavaPlugin.getConfig().set("SoilType", "FARMLAND");
            config_check = true;
         }

         config_check = true;
      }

      if (Main.JavaPlugin.getConfig().getDouble("Version") < 2.002) {
         if (Variable.Lang_YML.getString("PlaceInMaxHeight") == null) {
            Variable.Lang_YML.set("PlaceInMaxHeight", "§7[ErrorTown] §d抱歉,当前您放置的方块超过了家园高度上,请勿高处放置方块");
            lang_check = true;
         }

         if (Variable.Lang_YML.getString("EnableHeightLimit") == null) {
            Variable.Lang_YML.set("EnableHeightLimit", "§7[ErrorTown] §a您开启了家园放置高高度的监听");
            lang_check = true;
         }

         if (!Main.JavaPlugin.getConfig().isConfigurationSection("MaxHeight")) {
            Main.JavaPlugin.getConfig().set("MaxHeight", 255);
            config_check = true;
         }

         if (!Main.JavaPlugin.getConfig().getBoolean("EnableHeightLimit")) {
            Main.JavaPlugin.getConfig().set("EnableHeightLimit", true);
            config_check = true;
         }

         config_check = true;
      }

      if (Main.JavaPlugin.getConfig().getDouble("Version") < 2.005) {
         if (!Main.JavaPlugin.getConfig().getBoolean("EnableClearExtraBlocks")) {
            Main.JavaPlugin.getConfig().set("EnableClearExtraBlocks", false);
            config_check = true;
         }

         if (Variable.Lang_YML.getString("ClearExtraBlocks") == null) {
            Variable.Lang_YML.set("ClearExtraBlocks", "§7[ErrorTown] §c测到区块/世界放置方块过多,为您自动清空掉~");
            lang_check = true;
         }

         config_check = true;
      }

      if (Main.JavaPlugin.getConfig().getDouble("Version") < 2.006) {
         if (!Main.JavaPlugin.getConfig().isConfigurationSection("HikariCP.connectionTimeout")) {
            Main.JavaPlugin.getConfig().set("HikariCP.connectionTimeout", 30000);
            config_check = true;
         }

         if (!Main.JavaPlugin.getConfig().isConfigurationSection("HikariCP.minimumIdle")) {
            Main.JavaPlugin.getConfig().set("HikariCP.minimumIdle", 10);
            config_check = true;
         }

         if (!Main.JavaPlugin.getConfig().isConfigurationSection("HikariCP.maximumPoolSize")) {
            Main.JavaPlugin.getConfig().set("HikariCP.maximumPoolSize", 50);
            config_check = true;
         }

         if (Variable.Lang_YML.getString("UpdateNoPoints") == null) {
            Variable.Lang_YML.set("UpdateNoPoints", "§7[ErrorTown] §c点券不足<NeedPoints>");
            lang_check = true;
         }
      }

      if (Main.JavaPlugin.getConfig().getDouble("Version") < 2.007 && !Main.JavaPlugin.getConfig().getBoolean("AutoMoveWorldFilesToOther")) {
         Main.JavaPlugin.getConfig().set("AutoMoveWorldFilesToOther", false);
         config_check = true;
      }

      if (Main.JavaPlugin.getConfig().getDouble("Version") < 2.008) {
         if (!Main.JavaPlugin.getConfig().getBoolean("CustomEntityMax")) {
            Main.JavaPlugin.getConfig().set("CustomEntityMax", false);
            config_check = true;
         }

         if (Main.JavaPlugin.getConfig().getStringList("EntityList") == null) {
            Main.JavaPlugin.getConfig().set("EntityList", new ArrayList<>());
            config_check = true;
         }

         if (!Main.JavaPlugin.getConfig().getBoolean("CheckEntityInterval")) {
            Main.JavaPlugin.getConfig().set("CheckEntityInterval", 60);
            config_check = true;
         }
      }

      if (Main.JavaPlugin.getConfig().getDouble("Version") < 2.009) {
         if (!Main.JavaPlugin.getConfig().getBoolean("EnableBlackItemsUseInNoPermission")) {
            Main.JavaPlugin.getConfig().set("EnableBlackItemsUseInNoPermission", false);
            config_check = true;
         }

         if (Main.JavaPlugin.getConfig().getStringList("BlackItems") == null) {
            Main.JavaPlugin.getConfig().set("BlackItems", new ArrayList<>());
            config_check = true;
         }

         if (!Main.JavaPlugin.getConfig().isConfigurationSection("Permission.CommandUser")) {
            Main.JavaPlugin.getConfig().set("Permission.CommandUser", true);
            config_check = true;
         }

         if (!Main.JavaPlugin.getConfig().isConfigurationSection("Permission.Visit")) {
            Main.JavaPlugin.getConfig().set("Permission.Visit", true);
            config_check = true;
         }

         if (!Main.JavaPlugin.getConfig().isConfigurationSection("Permission.SetSpawn")) {
            Main.JavaPlugin.getConfig().set("Permission.SetSpawn", false);
            config_check = true;
         }

         if (!Main.JavaPlugin.getConfig().isConfigurationSection("Permission.Nether")) {
            Main.JavaPlugin.getConfig().set("Permission.Nether", false);
            config_check = true;
         }

         if (!Main.JavaPlugin.getConfig().isConfigurationSection("Permission.End")) {
            Main.JavaPlugin.getConfig().set("Permission.End", false);
            config_check = true;
         }

         if (!Main.JavaPlugin.getConfig().isConfigurationSection("Permission.Rain")) {
            Main.JavaPlugin.getConfig().set("Permission.Rain", false);
            config_check = true;
         }

         if (!Main.JavaPlugin.getConfig().isConfigurationSection("Permission.Sun")) {
            Main.JavaPlugin.getConfig().set("Permission.Sun", false);
            config_check = true;
         }

         if (!Main.JavaPlugin.getConfig().isConfigurationSection("Permission.Night")) {
            Main.JavaPlugin.getConfig().set("Permission.Night", false);
            config_check = true;
         }

         if (!Main.JavaPlugin.getConfig().isConfigurationSection("Permission.Day")) {
            Main.JavaPlugin.getConfig().set("Permission.Day", false);
            config_check = true;
         }

         if (!Main.JavaPlugin.getConfig().isConfigurationSection("Permission.Create-1")) {
            Main.JavaPlugin.getConfig().set("Permission.Create-1", true);
            config_check = true;
         }

         if (!Main.JavaPlugin.getConfig().isConfigurationSection("Permission.Create-2")) {
            Main.JavaPlugin.getConfig().set("Permission.Create-2", true);
            config_check = true;
         }

         if (!Main.JavaPlugin.getConfig().isConfigurationSection("Permission.Create-airland")) {
            Main.JavaPlugin.getConfig().set("Permission.Create-airland", true);
            config_check = true;
         }

         if (!Main.JavaPlugin.getConfig().isConfigurationSection("Permission.LockTime")) {
            Main.JavaPlugin.getConfig().set("Permission.LockTime", false);
            config_check = true;
         }

         if (!Main.JavaPlugin.getConfig().isConfigurationSection("Permission.LockWeather")) {
            Main.JavaPlugin.getConfig().set("Permission.LockWeather", false);
            config_check = true;
         }

         if (Variable.Lang_YML.getString("TakeBlackItemsInNoPermissionHome") == null) {
            Variable.Lang_YML.set("TakeBlackItemsInNoPermissionHome", "§7[ErrorTown] §c抱歉,禁止携带违禁品进入无权限的家,关键:<type>");
            lang_check = true;
         }
      }

      if (Main.JavaPlugin.getConfig().getDouble("Version") < 2.012) {
         if (!Main.JavaPlugin.getConfig().isConfigurationSection("UnOptimizeWorlds")) {
            List<String> list = new ArrayList<>();
            list.add("ZC");
            Main.JavaPlugin.getConfig().set("UnOptimizeWorlds", list);
            config_check = true;
         }

         if (!Main.JavaPlugin.getConfig().isConfigurationSection("FlowerAdd")) {
            Main.JavaPlugin.getConfig().set("FlowerAdd", 0.3);
            config_check = true;
         }

         if (!Main.JavaPlugin.getConfig().isConfigurationSection("PopularityAdd")) {
            Main.JavaPlugin.getConfig().set("PopularityAdd", 0.1);
            config_check = true;
         }

         if (Variable.Lang_YML.getString("PopularityAdd") == null) {
            Variable.Lang_YML.set("PopularityAdd", "§7[ErrorTown] §d您为<Name>的家园增加了1点人");
            lang_check = true;
         }

         if (Variable.Lang_YML.getString("PopularityAddToOwnerAndOP") == null) {
            Variable.Lang_YML.set("PopularityAddToOwnerAndOP", "§7[ErrorTown] §d您的家园<Player>访问,增加1点人");
            lang_check = true;
         }

         if (Variable.Lang_YML.getString("FlowersAdd") == null) {
            Variable.Lang_YML.set("FlowersAdd", "§7[ErrorTown] §d您为<Name>的家园�上了一束鲜,<Now>/<Max>");
            lang_check = true;
         }

         if (Variable.Lang_YML.getString("FlowersAddToOwnerAndOP") == null) {
            Variable.Lang_YML.set("FlowersAddToOwnerAndOP", "§7[ErrorTown] §d您的家园<Player>投�了束鲜");
            lang_check = true;
         }

         if (Variable.Lang_YML.getString("FlowersMax") == null) {
            Variable.Lang_YML.set("FlowersMax", "§7[ErrorTown] §d今日鲜花已经用完,<Max>/<Max>");
            lang_check = true;
         }

         if (Variable.Lang_YML.getString("FlowersMySelf") == null) {
            Variable.Lang_YML.set("FlowersMySelf", "§7[ErrorTown] §d抱歉,鲜花无法赠�给自己拥有权限的家");
            lang_check = true;
         }

         if (Variable.Lang_YML.getString("GiftAdd") == null) {
            Variable.Lang_YML.set("GiftAdd", "§7[ErrorTown] §d家园的礼物盒<Name>赠�了物品,快去看看,点我打开~");
            lang_check = true;
         }

         if (Variable.Lang_YML.getString("GiftSuccess") == null) {
            Variable.Lang_YML.set("GiftSuccess", "§7[ErrorTown] §d您赠送了手上的物品给<Name>,感谢馈赠~");
            lang_check = true;
         }

         if (Variable.Lang_YML.getString("GiftFail") == null) {
            Variable.Lang_YML.set("GiftFail", "§7[ErrorTown] §d该家园的礼物盒已,无法赠�~");
            lang_check = true;
         }

         if (Variable.Lang_YML.getString("NoPermissionOpenGift") == null) {
            Variable.Lang_YML.set("NoPermissionOpenGift", "§7[ErrorTown] §d缺乏权限:ErrorTown.Gift.Open,无法打开礼物盒~");
            lang_check = true;
         }

         if (Variable.Lang_YML.getString("NoPermissionOpenOthersGift") == null) {
            Variable.Lang_YML.set("NoPermissionOpenOthersGift", "§7[ErrorTown] §d您没有权限打他人家园的礼物盒~");
            lang_check = true;
         }

         if (Variable.Lang_YML.getString("NoPermissionSendTheItemToGift") == null) {
            Variable.Lang_YML.set("NoPermissionSendTheItemToGift", "§7[ErrorTown] §d缺乏权限:ErrorTown.Gift.Send,无法打开礼物盒~");
            lang_check = true;
         }

         if (Variable.Lang_YML.getString("HasAlreadyOpenByOthers") == null) {
            Variable.Lang_YML.set("HasAlreadyOpenByOthers", "§7[ErrorTown] §d礼物盒正在被玩家<Name>打开,目前无法查看~");
            lang_check = true;
         }

         if (Variable.Lang_YML.getString("SendButTheHandIsAir") == null) {
            Variable.Lang_YML.set("SendButTheHandIsAir", "§7[ErrorTown] §d抱歉,当前您手上的物品为空,无法发�过去哦~");
            lang_check = true;
         }

         if (Variable.Lang_YML.getString("SendButTheInvHasBeenOpen") == null) {
            Variable.Lang_YML.set("SendButTheInvHasBeenOpen", "§7[ErrorTown] §d抱歉,该家园的礼物盒已被打,请等待该玩家关闭后再发?");
            lang_check = true;
         }

         if (Variable.Lang_YML.getString("SendButTheMyHome") == null) {
            Variable.Lang_YML.set("SendButTheMyHome", "§7[ErrorTown] §d抱歉,您不能给自己送礼物哦~");
            lang_check = true;
         }

         if (Variable.Lang_YML.getString("InvPlayersGiftGuiButNoPermission") == null) {
            Variable.Lang_YML.set("InvPlayersGiftGuiButNoPermission", "§7[ErrorTown] §d抱歉,您没有权限打别人的家园收件箱,缺乏权限:ErrorTown.Open.Inv");
            lang_check = true;
         }

         if (Variable.Lang_YML.getString("GiftGuiTitle") == null) {
            Variable.Lang_YML.set("GiftGuiTitle", "§8家园礼物");
            lang_check = true;
         }

         if (Variable.Lang_YML.getString("GiftLoreAddPrefix") == null) {
            Variable.Lang_YML.set("GiftLoreAddPrefix", "§a来自:");
            lang_check = true;
         }

         if (Variable.Lang_YML.getString("NotHookProtocolLib") == null) {
            Variable.Lang_YML.set("NotHookProtocolLib", "§7[ErrorTown] §a没有发现Vault，家园礼物盒相关功能无法正常使用");
            lang_check = true;
         }

         if (Variable.Lang_YML.getString("HookProtocolLib") == null) {
            Variable.Lang_YML.set("HookProtocolLib", "§7[ErrorTown] §a挂载:ProtocolLib 家园礼物盒相关功能可正常使用");
            lang_check = true;
         }

         if (Variable.Lang_YML.getString("TodayRefreshPopularityAndFlowerData") == null) {
            Variable.Lang_YML.set("TodayRefreshPopularityAndFlowerData", "§7[ErrorTown] §a今日的玩 - 鲜花和人 - 数据已自动刷新重");
            lang_check = true;
         }
      }

      if (Main.JavaPlugin.getConfig().getDouble("Version") < 2.013) {
         if (!Main.JavaPlugin.getConfig().isConfigurationSection("NormalJoinWorld")) {
            Main.JavaPlugin.getConfig().set("NormalJoinWorld", "");
            config_check = true;
         }

         if (Variable.Lang_YML.getString("NoPermissionSetIcon") == null) {
            Variable.Lang_YML.set("NoPermissionSetIcon", "§7[ErrorTown] §d缺乏权限:ErrorTown.ICON,无法设置图标~");
            lang_check = true;
         }

         if (Variable.Lang_YML.getString("NoPermissionSetOthersIcon") == null) {
            Variable.Lang_YML.set("NoPermissionSetOthersIcon", "§7[ErrorTown] §c没有权限设置他人家园的传送ICON图标");
            lang_check = true;
         }

         if (Variable.Lang_YML.getString("SetIconButHandIsAir") == null) {
            Variable.Lang_YML.set("SetIconButHandIsAir", "§7[ErrorTown] §c抱歉,您不能设置ICON图标为手上的空气~");
            lang_check = true;
         }

         if (Variable.Lang_YML.getString("SetIconSuccess") == null) {
            Variable.Lang_YML.set("SetIconSuccess", "§7[ErrorTown] §a设置成功!");
            lang_check = true;
         }

         if (Variable.Lang_YML.getString("NoPermissionSetInfo") == null) {
            Variable.Lang_YML.set("NoPermissionSetInfo", "§7[ErrorTown] §d缺乏权限:ErrorTown.Info,无法设置家园标语~");
            lang_check = true;
         }

         if (Variable.Lang_YML.getString("NoPermissionSetColorInfo") == null) {
            Variable.Lang_YML.set("NoPermissionSetColorInfo", "§7[ErrorTown] §d缺乏权限:ErrorTown.Info.Color,无法设置带颜色的标语~");
            lang_check = true;
         }

         if (Variable.Lang_YML.getString("NoPermissionSetOthersInfo") == null) {
            Variable.Lang_YML.set("NoPermissionSetOthersInfo", "§7[ErrorTown] §c没有权限设置他人家园的家园标语~");
            lang_check = true;
         }

         if (Variable.Lang_YML.getString("SetInfoSuccess") == null) {
            Variable.Lang_YML.set("SetInfoSuccess", "§7[ErrorTown] §a设置成功!换行符为逗号,");
            lang_check = true;
         }

         if (!Variable.GUI_YML.isConfigurationSection("VisitGuiLores")) {
            List<String> list = new ArrayList<>();
            list.add("§d§l§m---------------%ErrorTown_World_Name_<Name>%§d§l§m---------------");
            list.add("§8家园鲜花: §6%ErrorTown_World_Flower_<Name>%");
            list.add("§8家园人气: §6%ErrorTown_World_Popularity_<Name>%");
            list.add("§8家园热度指数: §6%ErrorTown_World_Calc_<Name>%");
            list.add("");
            list.add("§8家园管理: §6%ErrorTown_World_ManageList_<Name>%");
            list.add("§8家园信任: §6%ErrorTown_World_TrustList_<Name>%");
            list.add("§8家园黑名: §6%ErrorTown_World_BlackList_<Name>%");
            list.add("");
            list.add("§8家园等级: §6%ErrorTown_World_Level_<Name>%");
            list.add("§8家园类型: §6%ErrorTown_World_Type_<Name>%");
            list.add("");
            list.add("§d§l§m---------------%ErrorTown_World_Name_<Name>%§d§l§m---------------");
            Variable.GUI_YML.set("VisitGuiLores", list);
            gui_check = true;
         }

         if (!Variable.GUI_YML.getBoolean("VisitSlogan")) {
            Variable.GUI_YML.set("VisitSlogan", true);
            gui_check = true;
         }
      }

      if (Main.JavaPlugin.getConfig().getDouble("Version") < 2.014 && Variable.Lang_YML.getString("WantToPutItemIntoGiftChest") == null) {
         Variable.Lang_YML.set("WantToPutItemIntoGiftChest", "§7[ErrorTown] §d抱歉,礼物盒仓库并不是给你存放你的物品的哦~");
         lang_check = true;
      }

      if (Main.JavaPlugin.getConfig().getDouble("Version") < 2.017) {
         if (Variable.Lang_YML.getString("OperatorSendGiftButOpen") == null) {
            Variable.Lang_YML.set("OperatorSendGiftButOpen", "§7[ErrorTown] §d抱歉,管理员正在派发福利礼包到您的收件,已为您自动关闭界,请重新打~");
            lang_check = true;
         }

         if (Variable.Lang_YML.getString("SuccessedSendToAll") == null) {
            Variable.Lang_YML.set("SuccessedSendToAll", "§7[ErrorTown] §d您已经成功发送手上的物品到礼物盒的家园列:<List>");
            lang_check = true;
         }

         if (Variable.Lang_YML.getString("FailedSendToAll") == null) {
            Variable.Lang_YML.set("FailedSendToAll", "§7[ErrorTown] §d以下家园列表发�失,礼物盒已:<List>");
            lang_check = true;
         }

         if (Variable.Lang_YML.getString("PopularityAddButHomeIsNotExist") == null) {
            Variable.Lang_YML.set("PopularityAddButHomeIsNotExist", "§7[ErrorTown] §d人气设置失败,该家园不存在");
            lang_check = true;
         }

         if (Variable.Lang_YML.getString("FlowerAddButHomeIsNotExist") == null) {
            Variable.Lang_YML.set("FlowerAddButHomeIsNotExist", "§7[ErrorTown] §d鲜花设置失败,该家园不存在");
            lang_check = true;
         }

         if (Variable.Lang_YML.getString("PopularityAddSuccess") == null) {
            Variable.Lang_YML.set("PopularityAddSuccess", "§7[ErrorTown] §d人气设置成功,目前的人气�为<Now>");
            lang_check = true;
         }

         if (Variable.Lang_YML.getString("FlowerAddSuccess") == null) {
            Variable.Lang_YML.set("FlowerAddSuccess", "§7[ErrorTown] §d鲜花设置成功,目前的鲜花�为<Now>");
            lang_check = true;
         }
      }

      if (Main.JavaPlugin.getConfig().getDouble("Version") < 2.018) {
         if (!Main.JavaPlugin.getConfig().isConfigurationSection("EnableAsnycTime")) {
            Main.JavaPlugin.getConfig().set("EnableAsnycTime", false);
            config_check = true;
         }

         if (!Main.JavaPlugin.getConfig().isConfigurationSection("AsyncTimeWorld")) {
            Main.JavaPlugin.getConfig().set("AsyncTimeWorld", "world");
            config_check = true;
         }

         if (!Main.JavaPlugin.getConfig().isConfigurationSection("RealisticSeasons")) {
            Main.JavaPlugin.getConfig().set("RealisticSeasons", false);
            config_check = true;
         }
      }

      if (Main.JavaPlugin.getConfig().getDouble("Version") < 2.019 && Variable.Lang_YML.getString("DisableAutoSaveWorld") == null) {
         Variable.Lang_YML.set("DisableAutoSaveWorld", "§7[ErrorTown] §a家园的自动保存功 已关");
         lang_check = true;
      }

      if (Main.JavaPlugin.getConfig().getDouble("Version") < 2.02) {
         if (!Main.JavaPlugin.getConfig().isConfigurationSection("VisitGuiShowAll")) {
            Main.JavaPlugin.getConfig().set("VisitGuiShowAll", false);
            config_check = true;
         }

         if (!Variable.Lang_YML.isConfigurationSection("DenyGuiLores")) {
            List<String> list = new ArrayList<>();
            list.add("§6[左键]§d  -> 添加黑名");
            list.add("§6[右键]§d  -> 移除黑名");
            Variable.Lang_YML.set("DenyGuiLores", list);
            lang_check = true;
         }

         if (!Variable.Lang_YML.isConfigurationSection("InviteGuiLores")) {
            List<String> list = new ArrayList<>();
            list.add("§6[左键]§d  -> 请成为家园管理员");
            list.add("§6[右键]§d  -> 移除该家园管理员");
            Variable.Lang_YML.set("InviteGuiLores", list);
            lang_check = true;
         }

         if (!Variable.Lang_YML.isConfigurationSection("TrustGuiLores")) {
            List<String> list = new ArrayList<>();
            list.add("§6[左键]§d  -> 增加信任名单");
            list.add("§6[右键]§d  -> 移除信任名单");
            Variable.Lang_YML.set("TrustGuiLores", list);
            lang_check = true;
         }

         if (!Variable.GUI_YML.getBoolean("EnableCheckGuiNormalPane")) {
            Variable.GUI_YML.set("EnableCheckGuiNormalPane", true);
            gui_check = true;
         }

         if (!Variable.GUI_YML.getBoolean("EnableCreateGuiNormalPane")) {
            Variable.GUI_YML.set("EnableCreateGuiNormalPane", true);
            gui_check = true;
         }

         if (!Variable.GUI_YML.getBoolean("EnableDenyGuiNormalPane")) {
            Variable.GUI_YML.set("EnableDenyGuiNormalPane", true);
            gui_check = true;
         }

         if (!Variable.GUI_YML.getBoolean("EnableInviteGuiNormalPane")) {
            Variable.GUI_YML.set("EnableInviteGuiNormalPane", true);
            gui_check = true;
         }

         if (!Variable.GUI_YML.getBoolean("EnableMainGuiNormalPane")) {
            Variable.GUI_YML.set("EnableMainGuiNormalPane", true);
            gui_check = true;
         }

         if (!Variable.GUI_YML.getBoolean("EnableManageGuiNormalPane")) {
            Variable.GUI_YML.set("EnableManageGuiNormalPane", true);
            gui_check = true;
         }

         if (!Variable.GUI_YML.getBoolean("EnableManageGui2NormalPane")) {
            Variable.GUI_YML.set("EnableManageGui2NormalPane", true);
            gui_check = true;
         }

         if (!Variable.GUI_YML.getBoolean("EnableTrustGuiNormalPane")) {
            Variable.GUI_YML.set("EnableTrustGuiNormalPane", true);
            gui_check = true;
         }

         if (!Variable.GUI_YML.getBoolean("EnableInviteGuiNormalPane")) {
            Variable.GUI_YML.set("EnableInviteGuiNormalPane", true);
            gui_check = true;
         }

         if (!Variable.GUI_YML.getBoolean("EnableVisitGuiNormalPane")) {
            Variable.GUI_YML.set("EnableVisitGuiNormalPane", true);
            gui_check = true;
         }
      }

      if (Main.JavaPlugin.getConfig().getDouble("Version") < 2.021 && !Main.JavaPlugin.getConfig().isConfigurationSection("EnableAutoRespawnInHome")) {
         Main.JavaPlugin.getConfig().set("EnableAutoRespawnInHome", false);
         config_check = true;
      }

      if (Main.JavaPlugin.getConfig().getDouble("Version") < 2.022 && !Main.JavaPlugin.getConfig().isConfigurationSection("CustomBackupLocation")) {
         Main.JavaPlugin.getConfig().set("CustomBackupLocation", "");
         config_check = true;
      }

      if (Main.JavaPlugin.getConfig().getDouble("Version") < 2.023) {
         if (!Main.JavaPlugin.getConfig().isConfigurationSection("EnableChatPrefix")) {
            Main.JavaPlugin.getConfig().set("EnableChatPrefix", true);
            config_check = true;
         }

         if (!Main.JavaPlugin.getConfig().isConfigurationSection("EnableAdventureMode")) {
            Main.JavaPlugin.getConfig().set("EnableAdventureMode", true);
            config_check = true;
         }

         if (!Main.JavaPlugin.getConfig().isConfigurationSection("PlayerMoveOverBorderHit")) {
            Main.JavaPlugin.getConfig().set("PlayerMoveOverBorderHit", true);
            config_check = true;
         }

         if (!Main.JavaPlugin.getConfig().isConfigurationSection("PlayerMoveOverBorderBuff")) {
            Main.JavaPlugin.getConfig().set("PlayerMoveOverBorderBuff", true);
            config_check = true;
         }

         if (Variable.Lang_YML.getString("PlayerMoveOverBorderButAdventure") == null) {
            Variable.Lang_YML.set("PlayerMoveOverBorderButAdventure", "§8[§6ErrorTown§8] §7测到您走出边界过,自动切换为冒险模.");
            lang_check = true;
         }

         if (Variable.Lang_YML.getString("OutdatedWorldHasBeenDeleted") == null) {
            Variable.Lang_YML.set("OutdatedWorldHasBeenDeleted", "§8[§6ErrorTown§8] §7本次清空家园<Amount>,清空的列:<List>");
            lang_check = true;
         }

         if (Variable.Lang_YML.getString("OutdateWorldConfirm") == null) {
            Variable.Lang_YML.set("OutdateWorldConfirm", "§8[§6ErrorTown§8] §7您确定要删除<Day>天未被他人或自己访问过的有符合条件的家园?,5秒冷,请再次输>>>");
            lang_check = true;
         }
      }

      if (Main.JavaPlugin.getConfig().getDouble("Version") < 2.024) {
         if (!Main.JavaPlugin.getConfig().isConfigurationSection("EnableEntityInteract")) {
            Main.JavaPlugin.getConfig().set("EnableEntityInteract", true);
            config_check = true;
         }

         if (Variable.Lang_YML.getString("GivePopularityButOP") == null) {
            Variable.Lang_YML.set("GivePopularityButOP", "§8[§6ErrorTown§8] §7您是OP,访问他人家园为隐身状,不给予人气以及提示家园主人~");
            lang_check = true;
         }
      }

      if (Main.JavaPlugin.getConfig().getDouble("Version") < 2.025 && !Main.JavaPlugin.getConfig().isConfigurationSection("MoveWorldAfterUnLoad")) {
         Main.JavaPlugin.getConfig().set("MoveWorldAfterUnLoad", false);
         config_check = true;
      }

      if (Main.JavaPlugin.getConfig().getDouble("Version") < 2.026) {
         if (Variable.Lang_YML.getString("NoPermissionSetCustomBlockLimit") == null) {
            Variable.Lang_YML.set("NoPermissionSetCustomBlockLimit", "§8[§6ErrorTown§8] §7抱歉,您无权设置方块的自定义放置功能指令,非OP身份~");
            lang_check = true;
         }

         if (Variable.Lang_YML.getString("SetCustomBlockButHomeIsNull") == null) {
            Variable.Lang_YML.set("SetCustomBlockButHomeIsNull", "§8[§6ErrorTown§8] §7抱歉,参数里的家园不存在~");
            lang_check = true;
         }

         if (Variable.Lang_YML.getString("SetCustomBlockButHomeButHasAlreadyContain") == null) {
            Variable.Lang_YML.set("SetCustomBlockButHomeButHasAlreadyContain", "§8[§6ErrorTown§8] §7抱歉,该家园的该限制已存在~");
            lang_check = true;
         }

         if (Variable.Lang_YML.getString("SetCustomBlockButHomeButNotContain") == null) {
            Variable.Lang_YML.set("SetCustomBlockButHomeButNotContain", "§8[§6ErrorTown§8] §7抱歉,该家园的该限制不存在~");
            lang_check = true;
         }

         if (Variable.Lang_YML.getString("SetCustomBlockSuccess") == null) {
            Variable.Lang_YML.set("SetCustomBlockSuccess", "§8[§6ErrorTown§8] §7成功设置<Name>家园的<NBT>方块放置数量为:<Amount>,限制类型为:<Type>");
            lang_check = true;
         }

         if (Variable.Lang_YML.getString("RemoveCustomBlockSuccess") == null) {
            Variable.Lang_YML.set("RemoveCustomBlockSuccess", "§8[§6ErrorTown§8] §7成功移除<Name>家园的<NBT>方块放置的限制,限制类型为:<Type>");
            lang_check = true;
         }

         if (Variable.Lang_YML.getString("AddCustomBlockSuccess") == null) {
            Variable.Lang_YML.set("AddCustomBlockSuccess", "§8[§6ErrorTown§8] §7成功添加<Name>家园的<NBT>方块放置数量<Amount>个,目前新的限制数量为:<NowAmount>,限制类型为:<Type>");
            lang_check = true;
         }

         config_check = true;
      }

      if (Main.JavaPlugin.getConfig().getDouble("Version") < 2.027) {
         if (Main.JavaPlugin.getConfig().isConfigurationSection("MultiverseCoreCompability")) {
            Main.JavaPlugin.getConfig().set("MultiverseCoreCompability", true);
            config_check = true;
         }

         if (Variable.Lang_YML.getString("MultiverseCoreCompability") == null) {
            Variable.Lang_YML.set("MultiverseCoreCompability", "§8[§6ErrorTown§8] §a挂载到:Multiverse-Core 兼容多世界插件已开启~");
            lang_check = true;
         }

         if (Main.JavaPlugin.getConfig().getString("UnAutoSaveWorlds") == null) {
            List<String> list = new ArrayList<>();
            list.add("DIM34676");
            Main.JavaPlugin.getConfig().set("UnAutoSaveWorlds", list);
            config_check = true;
         }

         if (!Main.JavaPlugin.getConfig().isConfigurationSection("CheckUpdate")) {
            Main.JavaPlugin.getConfig().set("CheckUpdate", true);
            config_check = true;
         }

         if (!Main.JavaPlugin.getConfig().isConfigurationSection("CheckUpdate")) {
            Main.JavaPlugin.getConfig().set("CheckUpdate", true);
            config_check = true;
         }

         if (!Variable.Lang_YML.isConfigurationSection("CheckHasNewPlugin")) {
            List<String> list = new ArrayList<>();
            list.add("§8[§6ErrorTown§8] §d当前运行的插件版本为:§a<Now>");
            list.add("§8[§6ErrorTown§8] §d找到一个更新的版本:§a<New>");
            list.add("§8[§6ErrorTown§8] §d下载地址: §e<Link>");
            Variable.Lang_YML.set("CheckHasNewPlugin", list);
            lang_check = true;
         }

         if (!Variable.Lang_YML.isConfigurationSection("NowIsTheLatestPlugin")) {
            Variable.Lang_YML.set("NowIsTheLatestPlugin", "§8[§6ErrorTown§8] §d当前运行的插件版本为最新版:§a<Now>");
            lang_check = true;
         }

         if (!Variable.Lang_YML.isConfigurationSection("CheckUpdateFailed")) {
            Variable.Lang_YML.set("CheckUpdateFailed", "§8[§6ErrorTown§8] §c版本更新检查失败!");
            lang_check = true;
         }

         if (!Main.JavaPlugin.getConfig().isConfigurationSection("DisablePortalCreate")) {
            Main.JavaPlugin.getConfig().set("DisablePortalCreate", true);
            config_check = true;
         }

         config_check = true;
      }

      if (Main.JavaPlugin.getConfig().getDouble("Version") < 2.0283) {
         if (Main.JavaPlugin.getConfig().isConfigurationSection("FaweSwitch")) {
            Main.JavaPlugin.getConfig().set("FaweSwitch", false);
            config_check = true;
         }

         if (Main.JavaPlugin.getConfig().isConfigurationSection("BorderMaterial")) {
            Main.JavaPlugin.getConfig().set("BorderMaterial", "BEDROCK");
            config_check = true;
         }

         if (Main.JavaPlugin.getConfig().isConfigurationSection("BorderShape")) {
            Main.JavaPlugin.getConfig().set("BorderShape", "Square");
            config_check = true;
         }

         if (Main.JavaPlugin.getConfig().isConfigurationSection("UpdateClearOld")) {
            Main.JavaPlugin.getConfig().set("UpdateClearOld", false);
            config_check = true;
         }

         if (Main.JavaPlugin.getConfig().isConfigurationSection("KeepSpawnInMemory")) {
            Main.JavaPlugin.getConfig().set("KeepSpawnInMemory", true);
            config_check = true;
         }

         if (!Variable.Lang_YML.isConfigurationSection("FaweAndWorldEditCompability")) {
            Variable.Lang_YML.set("FaweAndWorldEditCompability", "§8[§6ErrorTown§8] §a挂载到:Multiverse-FastAsyncWorldEdit 异步形状边界功能已开启~");
            lang_check = true;
         }

         if (!Variable.Lang_YML.isConfigurationSection("ToggleccWorldEnable")) {
            Variable.Lang_YML.set("ToggleccWorldEnable", "§8[§6ErrorTown§8] §a成功切换边界显示情况为: 显示");
            lang_check = true;
         }

         if (!Variable.Lang_YML.isConfigurationSection("ToggleccWorldDisable")) {
            Variable.Lang_YML.set("ToggleccWorldDisable", "§8[§6ErrorTown§8] §a成功切换边界显示情况为: 隐藏");
            lang_check = true;
         }

         config_check = true;
      }

      if (Main.JavaPlugin.getConfig().getDouble("Version") < 2.0285) {
         if (!Variable.Lang_YML.isConfigurationSection("BiomeChangeTip")) {
            Variable.Lang_YML.set("BiomeChangeTip", "§8[§6ErrorTown§8] §a已为您更改当前的区块群系,请重新上线查看生效!");
            lang_check = true;
         }

         config_check = true;
      }

      if (Main.JavaPlugin.getConfig().getDouble("Version") < 2.0296) {
         if (!Variable.Lang_YML.isConfigurationSection("WeatherRain")) {
            Variable.Lang_YML.set("WeatherRain", "§8[§6ErrorTown§8] §a为您切换为雨天");
            lang_check = true;
         }

         if (!Variable.Lang_YML.isConfigurationSection("WeatherSun")) {
            Variable.Lang_YML.set("WeatherSun", "§8[§6ErrorTown§8] §a为您切换为晴天");
            lang_check = true;
         }

         if (!Variable.Lang_YML.isConfigurationSection("TimeDay")) {
            Variable.Lang_YML.set("TimeDay", "§8[§6ErrorTown§8] §a为您切换为白天");
            lang_check = true;
         }

         if (!Variable.Lang_YML.isConfigurationSection("TimeNight")) {
            Variable.Lang_YML.set("TimeNight", "§8[§6ErrorTown§8] §a为您切换为黑天");
            lang_check = true;
         }

         if (!Variable.Lang_YML.isConfigurationSection("LookSeed")) {
            Variable.Lang_YML.set("LookSeed", "§8[§6ErrorTown§8] §a当前世界的种子为:<Seed>");
            lang_check = true;
         }

         if (!Variable.Lang_YML.isConfigurationSection("BiomeError")) {
            Variable.Lang_YML.set("BiomeError", "§8[§6ErrorTown§8] §a群系类型错误,请检查");
            lang_check = true;
         }

         if (!Variable.Lang_YML.isConfigurationSection("EnableFly")) {
            Variable.Lang_YML.set("EnableFly", "§8[§6ErrorTown§8] §a成功为您开启飞行模式");
            lang_check = true;
         }

         if (!Variable.Lang_YML.isConfigurationSection("DisableFly")) {
            Variable.Lang_YML.set("DisableFly", "§8[§6ErrorTown§8] §a成功为您关闭飞行模式");
            lang_check = true;
         }

         if (!Variable.Lang_YML.isConfigurationSection("ToggleccWorldDisableFlying")) {
            Variable.Lang_YML.set("ToggleccWorldDisableFlying", "§8[§6ErrorTown§8] §a您离开了飞行的世界,为您自动关闭飞行功能.");
            lang_check = true;
         }

         config_check = true;
      }

      if (config_check) {
         Main.JavaPlugin.getConfig().set("Version", 2.1);
         Main.JavaPlugin.saveConfig();
         Bukkit.getConsoleSender().sendMessage(Variable.Lang_YML.getString("UpdateConfigMessage"));
      }

      if (lang_check) {
         try {
            Variable.Lang_YML
               .save(
                  new File(
                     Main.JavaPlugin.getDataFolder()
                        + Variable.file_loc_prefix
                        + "Language"
                        + Variable.file_loc_prefix
                        + Main.JavaPlugin.getConfig().getString("Language")
                        + ".yml"
                  )
               );
         } catch (IOException ioFailure) {
            Diag.warnOnce("configupdate-update", "File I/O failed in ConfigUpdate.update", ioFailure);
         }

         Bukkit.getConsoleSender().sendMessage(Variable.Lang_YML.getString("UpdateLanguageMessage"));
      }

      if (gui_check) {
         try {
            Variable.GUI_YML.save(new File(Main.JavaPlugin.getDataFolder() + Variable.file_loc_prefix + "GUI.yml"));
         } catch (IOException ioFailure) {
            Diag.warnOnce("configupdate-update-2", "File I/O failed in ConfigUpdate.update", ioFailure);
         }

         Bukkit.getConsoleSender().sendMessage(Variable.Lang_YML.getString("UpdateGuiMessage"));
      }

      if (setConfigDefault("SkyIsland.Enable", true)) {
         config_check = true;
      }

      if (setConfigDefault("SkyIsland.CreateKey", "airland")) {
         config_check = true;
      }

      if (setConfigDefault("SkyIsland.CenterX", 0)) {
         config_check = true;
      }

      if (setConfigDefault("SkyIsland.CenterZ", 0)) {
         config_check = true;
      }

      if (setConfigDefault("SkyIsland.SpawnY", 65)) {
         config_check = true;
      }

      if (setConfigDefault("SkyIsland.ClearHeight", 6)) {
         config_check = true;
      }

      if (setConfigDefault("SkyIsland.PlatformRadius", 3)) {
         config_check = true;
      }

      if (setConfigDefault("SkyIsland.PlatformTop", "GRASS_BLOCK")) {
         config_check = true;
      }

      if (setConfigDefault("SkyIsland.PlatformMiddle", "DIRT")) {
         config_check = true;
      }

      if (setConfigDefault("SkyIsland.PlatformBottom", "BEDROCK")) {
         config_check = true;
      }

      if (setConfigDefault("SkyIsland.ChestOffsetX", 2)) {
         config_check = true;
      }

      if (setConfigDefault("SkyIsland.ChestOffsetY", 1)) {
         config_check = true;
      }

      if (setConfigDefault("SkyIsland.ChestOffsetZ", 0)) {
         config_check = true;
      }

      if (setConfigDefault("SkyIsland.StarterChest.Enable", true)) {
         config_check = true;
      }

      List<String> starterItems = new ArrayList<>();
      starterItems.add("LAVA_BUCKET,1,0");
      starterItems.add("ICE,2,1");
      starterItems.add("OAK_SAPLING,1,2");
      starterItems.add("DIRT,8,3");
      if (setConfigDefault("SkyIsland.StarterChest.Items", starterItems)) {
         config_check = true;
      }

      if (setConfigDefault("ShiftFOpenMainMenu.Enable", true)) {
         config_check = true;
      }

      if (setConfigDefault("ShiftFOpenMainMenu.RequireSneaking", true)) {
         config_check = true;
      }

      if (setConfigDefault("ShiftFOpenMainMenu.CancelOffhandSwap", true)) {
         config_check = true;
      }

      if (setConfigDefault("ShiftFOpenMainMenu.DebugMessage", false)) {
         config_check = true;
      }

      if (setConfigDefault("ShiftFOpenMainMenu.Command", "sh open")) {
         config_check = true;
      }

      if (setConfigDefault("HomeNetherPortal.SearchRadius", 16)) {
         config_check = true;
      }

      if (setConfigDefault("HomeNetherPortal.CreationRadius", 16)) {
         config_check = true;
      }

      if (setConfigDefault("HomeNetherPortal.UseVanillaResolver", true)) {
         config_check = true;
      }

      if (setConfigDefault("EnablePhantomSpawn", false)) {
         config_check = true;
      }

      if (setConfigDefault("SetSpawn.SyncTeleportAndRespawn", true)) {
         config_check = true;
      }

      if (setConfigDefault("LegacyPermissions", true)) {
         config_check = true;
      }

      if (setConfigDefault("HomeSpawnCompensation.Enable", true)) {
         config_check = true;
      }

      if (setConfigDefault("HomeSpawnCompensation.TargetMonsterCap", 70)) {
         config_check = true;
      }

      if (setConfigDefault("HomeSpawnCompensation.MinMonsterSpawnLimit", 70)) {
         config_check = true;
      }

      if (setConfigDefault("HomeSpawnCompensation.MaxMonsterSpawnLimit", 1024)) {
         config_check = true;
      }

      if (setConfigDefault("HomeSpawnCompensation.ApplyToNether", true)) {
         config_check = true;
      }

      // Empty on purpose: SuperflatPreset falls back to its built-in layers, so an operator's config
      // does not gain a long JSON blob they never asked for.
      if (setConfigDefault(SuperflatPreset.SETTINGS_KEY, "")) {
         config_check = true;
      }

      if (setConfigDefault(Text.MODE_KEY, "auto")) {
         config_check = true;
      }

      if (setConfigDefault(Text.AMPERSAND_KEY, true)) {
         config_check = true;
      }

      if (setConfigDefault(CraftEngineBlockLimit.ENABLED_KEY, false)) {
         config_check = true;
      }

      if (setConfigDefault(CraftEngineBlockLimit.RADIUS_KEY, 6)) {
         config_check = true;
      }

      if (config_check) {
         Main.JavaPlugin.saveConfig();
      }
   }
}
