package com.GUI;

import com.ErrorTown.Main;
import com.ErrorTown.Variable;
import com.Util.Home;
import com.Util.HomeAPI;
import com.Util.HomeTerrainPolicy;
import com.Util.Util;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class UpgradeGui implements InventoryHolder {
   public static final String BTN_MONEY = "§6§l金币升级";
   public static final String BTN_POINTS = "§b§l点券升级";
   public static final String BTN_ITEMS = "§a§l物品升级";
   public static final String BTN_BACK = "§8[§a返回§8]";
   public Inventory MainGui = Bukkit.createInventory(this, com.Util.GuiSafe.size("UpgradeSize", 27), com.Util.GuiSafe.title("UpgradeTitle", "§8>>> §6升级方式选择"));

   public UpgradeGui(Player p) {
      this.fill(p);
   }

   private void fill(Player p) {
      this.MainGui.clear();
      Material paneMat = Material.BLACK_STAINED_GLASS_PANE;

      String cfg = Variable.GUI_YML == null ? null : Variable.GUI_YML.getString("PaneMaterial");
      if (cfg != null && !cfg.isEmpty()) {
         Material m = Material.matchMaterial(cfg);
         if (m != null) {
            paneMat = m;
         }
      }

      ItemStack pane = makePane(paneMat);

      for (int i = 0; i < 9; i++) {
         this.MainGui.setItem(i, pane);
      }

      for (int i = 18; i < 27; i++) {
         this.MainGui.setItem(i, pane);
      }

      this.MainGui.setItem(9, pane);
      this.MainGui.setItem(10, pane);
      this.MainGui.setItem(12, pane);
      this.MainGui.setItem(14, pane);
      this.MainGui.setItem(16, pane);
      this.MainGui.setItem(17, pane);

      // Fail closed: if the level cannot be determined we must not fall back to "1",
      // because level 1 is the cheapest upgrade tier. An unreadable home file used to
      // silently price a level-12 home as a level-1 upgrade.
      Integer resolvedLevel = resolveLevel(p);
      if (resolvedLevel == null) {
         this.MainGui.setItem(13, errorItem("§c§l无法读取家园等级", "§7请稍后重试，或联系管理员检查家园数据。"));
         this.setBackButton(22);
         return;
      }

      int now = resolvedLevel;
      int maxLv = Main.JavaPlugin.getConfig().getInt("MaxLevel", 12);
      boolean isMax = now >= maxLv;
      int currentSize = terrainSize(now);
      int nextSize = terrainSize(now + 1);
      int discount = Util.getUpgradeDiscount(p);
      boolean hasDiscount = discount < 100;
      if (isMax) {
         ItemStack it = new ItemStack(Material.BARRIER);
         ItemMeta m = it.getItemMeta();
         m.setDisplayName("§c§l已达最高等级");
         m.setLore(Arrays.asList("§7当前等级: §e" + now + " §7/ §e" + maxLv));
         it.setItemMeta(m);
         this.MainGui.setItem(13, it);
         this.setBackButton(22);
      } else {
         boolean enMoney = Main.JavaPlugin.getConfig().getBoolean("Upgrade.EnableMoney", true);
         boolean enPoints = Main.JavaPlugin.getConfig().getBoolean("Upgrade.EnablePoints", true);
         boolean enItems = Main.JavaPlugin.getConfig().getBoolean("Upgrade.EnableItems", false);
         if (enMoney) {
            Double origMoneyValue = configuredDouble("MoneyNeed", now - 1);
            if (origMoneyValue == null) {
               this.MainGui.setItem(11, errorItem("§c§l金币升级不可用", "§7MoneyNeed 缺少第 §e" + now + " §7级的价格。", "§7请补齐 config.yml 后重试。"));
            } else {
               double origMoney = origMoneyValue;
               double finalMoney = hasDiscount ? Math.ceil(origMoney * discount / 100.0) : origMoney;
               double haveMoney = Variable.econ == null ? 0.0 : Variable.econ.getBalance(p);
               boolean canAfford = haveMoney >= finalMoney;
               ItemStack btn = new ItemStack(canAfford ? Material.GOLD_INGOT : Material.IRON_INGOT);
               ItemMeta meta = btn.getItemMeta();
               meta.setDisplayName(BTN_MONEY);
               List<String> lore = new ArrayList<>();
               lore.add("§7当前等级: §e" + now + " §7→ §a" + (now + 1));
               lore.add("§7家园范围: §e" + currentSize + "×" + currentSize + " §7→ §a" + nextSize + "×" + nextSize);
               lore.add("§8§m-----------");
               if (hasDiscount) {
                  lore.add("§7原价: §8§m" + (long)origMoney + "§r §7→ §a" + (long)finalMoney + " §7金币");
                  lore.add("§7会员折扣: §6" + Util.discountZheStr(discount) + " §7(省 §a" + (long)(origMoney - finalMoney) + "§7)");
               } else {
                  lore.add("§7所需金币: §e" + (long)origMoney);
               }

               lore.add("§7拥有金币: " + (canAfford ? "§a" : "§c") + (long)haveMoney);
               lore.add("");
               lore.add(canAfford ? "§a▶ 点击升级" : "§c✗ 金币不足");
               meta.setLore(lore);
               btn.setItemMeta(meta);
               this.MainGui.setItem(11, btn);
            }
         }

         if (enPoints) {
            Integer origPtsValue = configuredInt("PointsNeed", now - 1);
            if (origPtsValue == null) {
               this.MainGui.setItem(15, errorItem("§c§l点券升级不可用", "§7PointsNeed 缺少第 §e" + now + " §7级的价格。", "§7请补齐 config.yml 后重试。"));
            } else {
               int origPts = origPtsValue;
               int finalPts = hasDiscount ? (int)Math.ceil(origPts * discount / 100.0) : origPts;
               int havePts = 0;
               if (Variable.PlyaerPointsModule && Variable.playerPoints != null) {
                  havePts = Variable.playerPoints.getAPI().look(p.getUniqueId());
               }

               boolean canAfford = havePts >= finalPts;
               ItemStack btn = new ItemStack(canAfford ? Material.DIAMOND : Material.LAPIS_LAZULI);
               ItemMeta meta = btn.getItemMeta();
               meta.setDisplayName(BTN_POINTS);
               List<String> lore = new ArrayList<>();
               lore.add("§7当前等级: §e" + now + " §7→ §a" + (now + 1));
               lore.add("§7家园范围: §e" + currentSize + "×" + currentSize + " §7→ §a" + nextSize + "×" + nextSize);
               lore.add("§8§m-----------");
               if (hasDiscount) {
                  lore.add("§7原价: §8§m" + origPts + "§r §7→ §b" + finalPts + " §7点券");
                  lore.add("§7会员折扣: §6" + Util.discountZheStr(discount) + " §7(省 §a" + (origPts - finalPts) + "§7)");
               } else {
                  lore.add("§7所需点券: §e" + origPts);
               }

               lore.add("§7拥有点券: " + (canAfford ? "§a" : "§c") + havePts);
               if (!Variable.PlyaerPointsModule) {
                  lore.add("§c(PlayerPoints 未安装)");
               }

               lore.add("");
               lore.add(canAfford ? "§a▶ 点击升级" : "§c✗ 点券不足");
               meta.setLore(lore);
               btn.setItemMeta(meta);
               this.MainGui.setItem(15, btn);
            }
         }

         if (enItems) {
            String itemLine = configuredString("ItemsNeed", now - 1);
            if (itemLine == null || itemLine.isEmpty()) {
               this.MainGui.setItem(13, errorItem("§c§l物品升级不可用", "§7ItemsNeed 缺少第 §e" + now + " §7级的配方。", "§7请补齐 config.yml 后重试。"));
            } else {
               ItemStack btnx = new ItemStack(Material.CHEST);
               ItemMeta metax = btnx.getItemMeta();
               metax.setDisplayName(BTN_ITEMS);
               List<String> lorex = new ArrayList<>();
               lorex.add("§7当前等级: §e" + now + " §7→ §a" + (now + 1));
               lorex.add("§7家园范围: §e" + currentSize + "×" + currentSize + " §7→ §a" + nextSize + "×" + nextSize);
               lorex.add("§8§m-----------");
               String[] parts = itemLine.split(",");
               lorex.add("§7所需物品: §e" + parts[0]);
               lorex.add("§7所需数量: §e" + (parts.length > 1 ? parts[1] : "1"));
               lorex.add("");
               lorex.add("§a▶ 点击升级");
               metax.setLore(lorex);
               btnx.setItemMeta(metax);
               this.MainGui.setItem(13, btnx);
            }
         }

         this.setBackButton(22);
      }
   }

   /**
    * Reads the current home level.
    *
    * @return the level, or null when it could not be determined - callers must then
    *         refuse to price an upgrade rather than assume level 1
    */
   private static Integer resolveLevel(Player p) {
      try {
         String homeName = Util.getBaseHomeName(p.getWorld().getName());
         if (!Util.CheckIsHome(homeName)) {
            homeName = HomeAPI.getPrimaryOwnedHome(p.getName());
         }
         if (homeName == null) {
            return null;
         }
         Home home = HomeAPI.getHome(homeName);
         if (home != null) {
            return Math.max(1, home.getLevel());
         }
         File f = new File(Variable.Tempf, homeName + ".yml");
         if (!f.exists()) {
            return null;
         }
         return Math.max(1, YamlConfiguration.loadConfiguration(f).getInt("Level", 1));
      } catch (RuntimeException failure) {
         Main.JavaPlugin.getLogger().log(Level.WARNING, "Could not resolve home level for " + p.getName(), failure);
         return null;
      }
   }

   private static Double configuredDouble(String key, int index) {
      List<Double> values = Main.JavaPlugin.getConfig().getDoubleList(key);
      return index >= 0 && index < values.size() ? values.get(index) : null;
   }

   private static Integer configuredInt(String key, int index) {
      List<Integer> values = Main.JavaPlugin.getConfig().getIntegerList(key);
      return index >= 0 && index < values.size() ? values.get(index) : null;
   }

   private static String configuredString(String key, int index) {
      List<String> values = Main.JavaPlugin.getConfig().getStringList(key);
      return index >= 0 && index < values.size() ? values.get(index) : null;
   }

   private static ItemStack errorItem(String name, String... lore) {
      ItemStack item = new ItemStack(Material.BARRIER);
      ItemMeta meta = item.getItemMeta();
      meta.setDisplayName(name);
      meta.setLore(Arrays.asList(lore));
      item.setItemMeta(meta);
      return item;
   }

   private void setBackButton(int slot) {
      ItemStack back = new ItemStack(Material.FEATHER);
      ItemMeta m = back.getItemMeta();
      m.setDisplayName("§8[§a返回§8]");
      back.setItemMeta(m);
      this.MainGui.setItem(slot, back);
   }

   private static int terrainSize(int level) {
      return HomeTerrainPolicy.sizeForLevel(level, Main.JavaPlugin.getConfig().getIntegerList("HomeUpgrade.LevelSizes"));
   }

   private static ItemStack makePane(Material mat) {
      ItemStack p = new ItemStack(mat);
      ItemMeta m = p.getItemMeta();
      m.setDisplayName("§r");
      p.setItemMeta(m);
      return p;
   }

   public Inventory getInventory() {
      return this.MainGui;
   }
}
