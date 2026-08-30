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

public class ServiceCostGui implements InventoryHolder {
   public static final String BTN_MONEY = "§6§l金币支付";
   public static final String BTN_POINTS = "§b§l点券支付";
   public static final String BTN_BACK = "§8[§a返回§8]";
   private final String serviceKey;
   private final Inventory inventory;

   public ServiceCostGui(Player p, String serviceKey) {
      this.serviceKey = serviceKey.toLowerCase();
      this.inventory = Bukkit.createInventory(this, com.Util.GuiSafe.size("ServiceCostSize", 27), this.getTitle());
      this.fill(p);
   }

   public String getServiceKey() {
      return this.serviceKey;
   }

   public Inventory getInventory() {
      return this.inventory;
   }

   private String getTitle() {
      if (this.serviceKey.equals("difficulty")) {
         return com.Util.GuiSafe.title("ServiceCostDifficultyTitle", "§8>>> §6难度设置 - 选择支付方式");
      } else if (this.serviceKey.equals("setspawn_here")) {
         return com.Util.GuiSafe.title("ServiceCostSetSpawnHereTitle", "§8>>> §6移动中心(脚下) - 选择支付方式");
      } else {
         return this.serviceKey.equals("setspawn_coords") ? com.Util.GuiSafe.title("ServiceCostSetSpawnCoordsTitle", "§8>>> §6移动中心(坐标) - 选择支付方式") : com.Util.GuiSafe.title("ServiceCostTitle", "§8>>> §6选择支付方式");
      }
   }

   private double getMoneyCost() {
      if (this.serviceKey.equals("difficulty")) {
         return Main.JavaPlugin.getConfig().getDouble("DifficultyChange.Cost", 0.0);
      } else {
         return !this.serviceKey.equals("setspawn_here") && !this.serviceKey.equals("setspawn_coords")
            ? 0.0
            : Main.JavaPlugin.getConfig().getDouble("SetSpawn.GoldFee", 0.0);
      }
   }

   private int getPointsCost() {
      if (this.serviceKey.equals("difficulty")) {
         return Main.JavaPlugin.getConfig().getInt("DifficultyChange.Points", 0);
      } else {
         return !this.serviceKey.equals("setspawn_here") && !this.serviceKey.equals("setspawn_coords")
            ? 0
            : Main.JavaPlugin.getConfig().getInt("SetSpawn.PointFee", 0);
      }
   }

   private String getServiceName() {
      if (this.serviceKey.equals("difficulty")) {
         return com.Util.GuiSafe.title("ServiceCostDifficultyTitle", "设置家园难度");
      } else if (this.serviceKey.equals("setspawn_here")) {
         return com.Util.GuiSafe.title("ServiceCostSetSpawnHereTitle", "把家园中心移动到脚下");
      } else {
         return this.serviceKey.equals("setspawn_coords") ? com.Util.GuiSafe.title("ServiceCostSetSpawnCoordsTitle", "把家园中心移动到指定坐标") : com.Util.GuiSafe.title("ServiceCostTitle", "执行服务");
      }
   }

   private String getBackCommand() {
      return !this.serviceKey.equals("setspawn_here") && !this.serviceKey.equals("setspawn_coords") ? com.Util.GuiSafe.title("ServiceCostSetSpawnCoordsTitle", "sh open manage3") : com.Util.GuiSafe.title("ServiceCostTitle", "sh open setspawn");
   }

   private void fill(Player p) {
      this.inventory.clear();
      Material paneMat = Material.BLACK_STAINED_GLASS_PANE;

      paneMat = GuiSafe.material(Variable.GUI_YML.getString("PaneMaterial"), paneMat);

      ItemStack pane = makePane(paneMat);

      for (int i = 0; i < 9; i++) {
         this.inventory.setItem(i, pane);
      }

      for (int i = 18; i < 27; i++) {
         this.inventory.setItem(i, pane);
      }

      this.inventory.setItem(9, pane);
      this.inventory.setItem(10, pane);
      this.inventory.setItem(12, pane);
      this.inventory.setItem(14, pane);
      this.inventory.setItem(16, pane);
      this.inventory.setItem(17, pane);
      double moneyCost = this.getMoneyCost();
      int pointsCost = this.getPointsCost();
      String serviceName = this.getServiceName();
      double haveMoney = 0.0;

      if (Variable.econ != null) {
         haveMoney = Variable.econ.getBalance(p);
      }

      boolean canAffordMoney = haveMoney >= moneyCost;
      ItemStack moneyBtn = new ItemStack(canAffordMoney ? Material.GOLD_INGOT : Material.IRON_INGOT);
      ItemMeta moneyMeta = moneyBtn.getItemMeta();
      moneyMeta.setDisplayName(BTN_MONEY);
      List<String> moneyLore = new ArrayList<>();
      moneyLore.add("§7功能: " + serviceName);
      moneyLore.add("§8§m-----------");
      moneyLore.add("§7需要金币: §e" + (long)moneyCost);
      moneyLore.add("§7拥有金币: " + (canAffordMoney ? "§a" : "§c") + (long)haveMoney);
      moneyLore.add("");
      moneyLore.add(canAffordMoney ? "§a点击使用金币继续" : "§c金币不足");
      moneyMeta.setLore(moneyLore);
      moneyBtn.setItemMeta(moneyMeta);
      this.inventory.setItem(11, moneyBtn);
      int havePoints = 0;

      if (Variable.PlyaerPointsModule && Variable.playerPoints != null) {
         havePoints = Variable.playerPoints.getAPI().look(p.getUniqueId());
      }

      boolean canAffordPoints = havePoints >= pointsCost;
      ItemStack pointsBtn = new ItemStack(canAffordPoints ? Material.DIAMOND : Material.LAPIS_LAZULI);
      ItemMeta pointsMeta = pointsBtn.getItemMeta();
      pointsMeta.setDisplayName(BTN_POINTS);
      List<String> pointsLore = new ArrayList<>();
      pointsLore.add("§7功能: " + serviceName);
      pointsLore.add("§8§m-----------");
      pointsLore.add("§7需要点券: §e" + pointsCost);
      pointsLore.add("§7拥有点券: " + (canAffordPoints ? "§a" : "§c") + havePoints);
      if (!Variable.PlyaerPointsModule) {
         pointsLore.add("§c未安装 PlayerPoints");
      }

      pointsLore.add("");
      pointsLore.add(canAffordPoints ? "§a点击使用点券继续" : "§c点券不足");
      pointsMeta.setLore(pointsLore);
      pointsBtn.setItemMeta(pointsMeta);
      this.inventory.setItem(15, pointsBtn);
      ItemStack back = new ItemStack(Material.FEATHER);
      ItemMeta backMeta = back.getItemMeta();
      backMeta.setDisplayName(BTN_BACK);
      backMeta.setLore(Arrays.asList("§7返回上一步"));
      back.setItemMeta(backMeta);
      this.inventory.setItem(22, back);
   }

   public String getBackCommandForClick() {
      return this.getBackCommand();
   }

   private static ItemStack makePane(Material mat) {
      ItemStack pane = new ItemStack(mat);
      ItemMeta meta = pane.getItemMeta();
      meta.setDisplayName("§r");
      pane.setItemMeta(meta);
      return pane;
   }
}
