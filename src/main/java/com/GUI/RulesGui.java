package com.GUI;

import com.ErrorTown.Main;
import com.ErrorTown.Variable;
import com.Util.GuiSafe;
import com.Util.Home;
import com.Util.HomeAPI;
import com.Util.Util;
import java.util.Arrays;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;

public class RulesGui implements InventoryHolder {
   public static final String TITLE = "§8>>> §6庄园规则设置";
   public static final String BTN_EXPLOSION = "§6爆炸保护";
   public static final String BTN_FIRE = "§6火焰蔓延";
   public static final String BTN_MOB = "§6自然刷怪";
   public static final String BTN_HOSTILE = "§6敌对生物";
   public static final String BTN_PASSIVE = "§6被动生物";
   public static final String BTN_SPAWNER = "§6刷怪笼生效";
   public static final String BTN_BREED = "§6动物繁殖";
   public static final String BTN_MOBCAP = "§6刷怪数量上限";
   public static final String BTN_BACK = "§8[§a返回§8]";
   public Inventory MainGui = Bukkit.createInventory(this, com.Util.GuiSafe.size("RulesSize", 45), com.Util.GuiSafe.title("RulesTitle", TITLE));

   public RulesGui(final Player p) {
      (new BukkitRunnable() {
            public void run() {
               RulesGui.this.MainGui.clear();
               Material paneMat = Material.BLACK_STAINED_GLASS_PANE;

               paneMat = GuiSafe.material(Variable.GUI_YML.getString("PaneMaterial"), paneMat);

               ItemStack pane = RulesGui.makePane(paneMat);

               for (int i = 0; i < 9; i++) {
                  RulesGui.this.MainGui.setItem(i, pane);
               }

               for (int i = 36; i < 45; i++) {
                  RulesGui.this.MainGui.setItem(i, pane);
               }

               RulesGui.this.MainGui.setItem(9, pane);
               RulesGui.this.MainGui.setItem(17, pane);
               RulesGui.this.MainGui.setItem(18, pane);
               RulesGui.this.MainGui.setItem(26, pane);
               RulesGui.this.MainGui.setItem(27, pane);
               RulesGui.this.MainGui.setItem(35, pane);
               String baseName = Util.getBaseHomeName(p.getWorld().getName());
               Home home = HomeAPI.getHome(baseName);
               if (home == null) {
                  p.sendMessage("§c无法加载当前庄园规则，请在庄园世界内使用。");
                  p.closeInventory();
               } else {
                  RulesGui.this.MainGui.setItem(4, RulesGui.makeInfo(home));
                  RulesGui.this.MainGui
                     .setItem(
                        10,
                        RulesGui.makeToggle(
                           home.getRuleExplosionProtect() ? Material.SHIELD : Material.TNT, "§6爆炸保护", home.getRuleExplosionProtect(), "开启后爆炸不会破坏方块"
                        )
                     );
                  RulesGui.this.MainGui
                     .setItem(
                        12,
                        RulesGui.makeToggle(
                           home.getRuleNoFireSpread() ? Material.BARRIER : Material.CAMPFIRE, "§6火焰蔓延", home.getRuleNoFireSpread(), "控制火焰是否继续自然蔓延"
                        )
                     );
                  RulesGui.this.MainGui
                     .setItem(
                        14,
                        RulesGui.makeToggle(
                           home.getRuleNoMobSpawn() ? Material.BARRIER : Material.ZOMBIE_HEAD, "§6自然刷怪", !home.getRuleNoMobSpawn(), "一键控制庄园内自然刷怪总开关"
                        )
                     );
                  RulesGui.this.MainGui
                     .setItem(
                        16,
                        RulesGui.makeToggle(
                           home.getRuleAllowHostileMobs() ? Material.IRON_SWORD : Material.SKELETON_SKULL,
                           "§6敌对生物",
                           home.getRuleAllowHostileMobs(),
                           "控制敌对生物是否允许生成"
                        )
                     );
                  RulesGui.this.MainGui
                     .setItem(
                        19,
                        RulesGui.makeToggle(
                           home.getRuleAllowPassiveMobs() ? Material.WHEAT : Material.BARRIER, "§6被动生物", home.getRuleAllowPassiveMobs(), "控制牛羊鸡等被动生物是否生成"
                        )
                     );
                  RulesGui.this.MainGui
                     .setItem(
                        21,
                        RulesGui.makeToggle(
                           home.getRuleAllowSpawnerSpawn() ? Material.SPAWNER : Material.BARRIER, "§6刷怪笼生效", home.getRuleAllowSpawnerSpawn(), "控制刷怪笼刷出的生物是否生效"
                        )
                     );
                  RulesGui.this.MainGui
                     .setItem(
                        23,
                        RulesGui.makeToggle(
                           home.getRuleAllowAnimalBreed() ? Material.GOLDEN_CARROT : Material.BARRIER, "§6动物繁殖", home.getRuleAllowAnimalBreed(), "控制动物繁殖是否允许"
                        )
                     );
                  RulesGui.this.MainGui.setItem(25, RulesGui.makeMobCap(home.getRuleMaxMobCount()));
                  ItemStack back = new ItemStack(Material.FEATHER);
                  ItemMeta meta = back.getItemMeta();
                  meta.setDisplayName(BTN_BACK);
                  meta.setLore(Arrays.asList("§7返回上一页"));
                  back.setItemMeta(meta);
                  RulesGui.this.MainGui.setItem(40, back);
               }
            }
         })
         .runTask(Main.JavaPlugin);
   }

   private static ItemStack makePane(Material mat) {
      ItemStack pane = new ItemStack(mat);
      ItemMeta m = pane.getItemMeta();
      m.setDisplayName("§r");
      pane.setItemMeta(m);
      return pane;
   }

   private static ItemStack makeToggle(Material mat, String name, boolean enabled, String desc) {
      ItemStack item = new ItemStack(mat);
      ItemMeta meta = item.getItemMeta();
      meta.setDisplayName(name);
      meta.setLore(Arrays.asList(enabled ? "§a§l当前: 开启" : "§c§l当前: 关闭", "§7" + desc, "§8点击切换"));
      item.setItemMeta(meta);
      return item;
   }

   private static ItemStack makeMobCap(int cap) {
      int step = Math.max(1, Main.JavaPlugin.getConfig().getInt("HomeRulesDefaults.MaxMobCountStep", 8));
      ItemStack item = new ItemStack(Material.COMPARATOR);
      ItemMeta meta = item.getItemMeta();
      meta.setDisplayName(BTN_MOBCAP);
      String capLine = "§7当前上限: §e" + cap;
      if (cap == 0) {
         capLine = capLine + " §c(禁刷怪)";
      }

      meta.setLore(Arrays.asList(capLine, "§7左键 +" + step, "§7右键 -" + step, "§80 表示直接禁止自然刷怪/刷怪笼刷怪", "§8该功能可随家园等级解锁"));
      item.setItemMeta(meta);
      return item;
   }

   private static ItemStack makeInfo(Home home) {
      ItemStack item = new ItemStack(Material.BOOK);
      ItemMeta meta = item.getItemMeta();
      meta.setDisplayName("§e当前庄园规则概览");
      String mobCapLine = "§7刷怪上限: §e" + home.getRuleMaxMobCount();
      if (home.getRuleMaxMobCount() == 0) {
         mobCapLine = mobCapLine + " §c(禁刷怪)";
      }

      meta.setLore(
         Arrays.asList(
            "§7家园等级: §e" + home.getLevel(),
            "§7敌对生物: " + boolText(home.getRuleAllowHostileMobs()),
            "§7被动生物: " + boolText(home.getRuleAllowPassiveMobs()),
            "§7刷怪笼: " + boolText(home.getRuleAllowSpawnerSpawn()),
            "§7动物繁殖: " + boolText(home.getRuleAllowAnimalBreed()),
            mobCapLine
         )
      );
      item.setItemMeta(meta);
      return item;
   }

   private static String boolText(boolean value) {
      return value ? "§a开启" : "§c关闭";
   }

   public Inventory getInventory() {
      return this.MainGui;
   }
}
