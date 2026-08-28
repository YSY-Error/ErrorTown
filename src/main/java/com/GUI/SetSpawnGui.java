package com.GUI;

import com.ErrorTown.Main;
import com.ErrorTown.Variable;
import com.Util.GuiSafe;
import java.util.Arrays;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class SetSpawnGui implements InventoryHolder {
   public static final String TITLE = "§8>>> §6移动家园中心";
   public static final String BTN_HERE = "§a设到脚下";
   public static final String BTN_COORDS = "§b输入坐标";
   public static final String BTN_BACK = "§8[§a返回§8]";
   private final Inventory inventory = Bukkit.createInventory(this, com.Util.GuiSafe.size("SetSpawnSize", 27), com.Util.GuiSafe.title("SetSpawnTitle", "§8>>> §6移动家园中心"));

   public SetSpawnGui(Player p) {
      this.fill(p);
   }

   public Inventory getInventory() {
      return this.inventory;
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
      double moneyCost = Main.JavaPlugin.getConfig().getDouble("SetSpawn.GoldFee", 0.0);
      int pointsCost = Main.JavaPlugin.getConfig().getInt("SetSpawn.PointFee", 0);
      long cooldownSec = Main.JavaPlugin.getConfig().getLong("SetSpawn.CooldownSeconds", 0L);
      boolean syncTp = Main.JavaPlugin.getConfig().getBoolean("SetSpawn.SyncTeleportAndRespawn", true);
      double haveMoney = 0.0;

      if (Variable.econ != null) {
         haveMoney = Variable.econ.getBalance(p);
      }

      int havePoints = 0;

      if (Variable.PlyaerPointsModule && Variable.playerPoints != null) {
         havePoints = Variable.playerPoints.getAPI().look(p.getUniqueId());
      }

      ItemStack hereBtn = new ItemStack(Material.LODESTONE);
      ItemMeta hereMeta = hereBtn.getItemMeta();
      hereMeta.setDisplayName("§a设到脚下");
      hereMeta.setLore(
         Arrays.asList(
            "§7把庄园中心移动到你当前脚下的位置",
            "§7会同步更新边界中心和世界出生点",
            syncTp ? "§7同时更新家园传送点/复活点" : "§7不会改动家园传送点/复活点",
            "§8-----------",
            "§7金币: §e" + (long)moneyCost + " §8| §7点券: §b" + pointsCost,
            "§7当前: §e" + (long)haveMoney + " §8| §b" + havePoints,
            cooldownSec > 0L ? "§7冷却: §c" + cooldownSec + " 秒" : "§7冷却: §a无",
            "§a点击后进入支付方式选择"
         )
      );
      hereBtn.setItemMeta(hereMeta);
      this.inventory.setItem(11, hereBtn);
      ItemStack coordsBtn = new ItemStack(Material.MAP);
      ItemMeta coordsMeta = coordsBtn.getItemMeta();
      coordsMeta.setDisplayName("§b输入坐标");
      coordsMeta.setLore(Arrays.asList("§7先输入目标坐标，再移动家园中心", "§7输入格式: §ex y z", "§7例如: §e128 75 -64", "§8-----------", "§7输入完成后再进入支付方式选择", "§b点击后进入聊天输入模式"));
      coordsBtn.setItemMeta(coordsMeta);
      this.inventory.setItem(15, coordsBtn);
      ItemStack back = new ItemStack(Material.FEATHER);
      ItemMeta backMeta = back.getItemMeta();
      backMeta.setDisplayName("§8[§a返回§8]");
      backMeta.setLore(Arrays.asList("§7返回上一页"));
      back.setItemMeta(backMeta);
      this.inventory.setItem(22, back);
   }

   private static ItemStack makePane(Material mat) {
      ItemStack pane = new ItemStack(mat);
      ItemMeta meta = pane.getItemMeta();
      meta.setDisplayName("§r");
      pane.setItemMeta(meta);
      return pane;
   }
}
