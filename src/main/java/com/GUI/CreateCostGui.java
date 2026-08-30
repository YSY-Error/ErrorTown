package com.GUI;

import com.ErrorTown.Main;
import com.ErrorTown.Variable;
import com.Util.GuiSafe;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class CreateCostGui implements InventoryHolder {
   public static final String BTN_MONEY = "§6§l金币创建";
   public static final String BTN_POINTS = "§b§l点券创建";
   public static final String BTN_BACK = "§8[§a返回§8]";
   private final String createType;
   private final String seedMode;
   public Inventory MainGui = Bukkit.createInventory(this, com.Util.GuiSafe.size("CreateCostSize", 27), com.Util.GuiSafe.title("CreateCostTitle", "§8>>> §6创建家园 - 选择付费方式"));

   public CreateCostGui(Player p, String createType, String seedMode) {
      this.createType = createType;
      this.seedMode = seedMode;
      this.fill(p);
   }

   public String getCreateType() {
      return this.createType;
   }

   public String getSeedMode() {
      return this.seedMode;
   }

   private void fill(Player p) {
      this.MainGui.clear();
      Material paneMat = Material.BLACK_STAINED_GLASS_PANE;

      paneMat = GuiSafe.material(Variable.GUI_YML.getString("PaneMaterial"), paneMat);

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
      String prefix = "CreateCost." + (this.seedMode.equals("custom") ? "CustomSeed" : "RandomSeed");
      double moneyCost = Main.JavaPlugin.getConfig().getDouble(prefix + ".Money", 0.0);
      int pointsCost = Main.JavaPlugin.getConfig().getInt(prefix + ".Points", 0);
      String seedLabel = this.seedMode.equals("custom") ? "§e指定种子" : "§a随机种子";
      double haveMoney = 0.0;

      if (Variable.econ != null) {
         haveMoney = Variable.econ.getBalance(p);
      }

      boolean canAffordMoney = haveMoney >= moneyCost;
      ItemStack moneyBtn = new ItemStack(canAffordMoney ? Material.GOLD_INGOT : Material.IRON_INGOT);
      ItemMeta moneyMeta = moneyBtn.getItemMeta();
      moneyMeta.setDisplayName(BTN_MONEY);
      List<String> moneyLore = new ArrayList<>();
      moneyLore.add("§7创建方式: " + seedLabel);
      moneyLore.add("§8§m-----------");
      moneyLore.add("§7需要金币: §e" + (long)moneyCost);
      moneyLore.add("§7拥有金币: " + (canAffordMoney ? "§a" : "§c") + (long)haveMoney);
      moneyLore.add("");
      moneyLore.add(canAffordMoney ? "§a点击使用金币创建" : "§c金币不足");
      moneyMeta.setLore(moneyLore);
      moneyBtn.setItemMeta(moneyMeta);
      this.MainGui.setItem(11, moneyBtn);
      int havePts = 0;

      if (Variable.PlyaerPointsModule && Variable.playerPoints != null) {
         havePts = Variable.playerPoints.getAPI().look(p.getUniqueId());
      }

      boolean canAffordPts = havePts >= pointsCost;
      ItemStack ptsBtn = new ItemStack(canAffordPts ? Material.DIAMOND : Material.LAPIS_LAZULI);
      ItemMeta ptsMeta = ptsBtn.getItemMeta();
      ptsMeta.setDisplayName(BTN_POINTS);
      List<String> ptsLore = new ArrayList<>();
      ptsLore.add("§7创建方式: " + seedLabel);
      ptsLore.add("§8§m-----------");
      ptsLore.add("§7需要点券: §e" + pointsCost);
      ptsLore.add("§7拥有点券: " + (canAffordPts ? "§a" : "§c") + havePts);
      if (!Variable.PlyaerPointsModule) {
         ptsLore.add("§c未安装 PlayerPoints");
      }

      ptsLore.add("");
      ptsLore.add(canAffordPts ? "§a点击使用点券创建" : "§c点券不足");
      ptsMeta.setLore(ptsLore);
      ptsBtn.setItemMeta(ptsMeta);
      this.MainGui.setItem(15, ptsBtn);
      this.setBackButton(22);
   }

   private void setBackButton(int slot) {
      ItemStack back = new ItemStack(Material.FEATHER);
      ItemMeta m = back.getItemMeta();
      m.setDisplayName(BTN_BACK);
      m.setLore(Arrays.asList("§7返回创建菜单"));
      back.setItemMeta(m);
      this.MainGui.setItem(slot, back);
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
