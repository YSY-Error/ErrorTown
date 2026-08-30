package com.GUI;

import com.ErrorTown.Main;
import com.ErrorTown.Variable;
import com.Util.GuiSafe;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;

public class ManageGui implements InventoryHolder {
   public Inventory MainGui = Bukkit.createInventory(this, com.Util.GuiSafe.size("ManageSize", 45), com.Util.Text.format(Variable.GUI_YML.getString("ManageTitle")));

   public ManageGui(final Player p) {
      (new BukkitRunnable() {
            public void run() {
               ManageGui.this.MainGui.clear();
               if (Variable.GUI_YML.getBoolean("EnableManageGuiNormalPane")) {
                  Material paneMat = Material.matchMaterial(
                     Variable.GUI_YML.getString("PaneMaterial") != null ? Variable.GUI_YML.getString("PaneMaterial") : "BLACK_STAINED_GLASS_PANE"
                  );
                  if (paneMat == null) {
                     paneMat = Material.BLACK_STAINED_GLASS_PANE;
                  }

                  ItemStack blb1 = new ItemStack(paneMat);
                  ItemMeta i1 = blb1.getItemMeta();
                  i1.setDisplayName("§r");
                  blb1.setItemMeta(i1);

                  for (int i = 0; i < 9; i++) {
                     ManageGui.this.MainGui.setItem(i, blb1);
                  }

                  ManageGui.this.MainGui.setItem(9, blb1);
                  ManageGui.this.MainGui.setItem(18, blb1);
                  ManageGui.this.MainGui.setItem(27, blb1);
                  ManageGui.this.MainGui.setItem(17, blb1);
                  ManageGui.this.MainGui.setItem(26, blb1);
                  ManageGui.this.MainGui.setItem(35, blb1);

                  for (int i = 36; i < 45; i++) {
                     if (i != 40) {
                        ManageGui.this.MainGui.setItem(i, blb1);
                     }
                  }
               }

               ConfigurationSection cs = Variable.GUI_YML.getConfigurationSection("");

               for (String temp : cs.getKeys(false)) {
                  if (Variable.GUI_YML.getString(temp + ".InMenu") != null && Variable.GUI_YML.getString(temp + ".InMenu").equalsIgnoreCase("Manage") && com.Util.ItemSpec.visibleTo(temp, p)) {
                     Material itemMat = com.Util.ItemSpec.material(Variable.GUI_YML.getString(temp + ".Material"));
                     if (itemMat == null) {
                        String temp5 = Variable.Lang_YML.getString("MaterialNotFound");
                        if (temp5 != null && temp5.contains("<Material>")) {
                           temp5 = temp5.replace("<Material>", Variable.GUI_YML.getString(temp + ".Material"));
                        }

                        if (temp5 != null && temp5.contains("<ID>")) {
                           temp5 = temp5.replace("<ID>", temp);
                        }

                        if (temp5 != null) {
                           Bukkit.getConsoleSender().sendMessage(temp5);
                        }
                     } else {
                        ItemStack item = com.Util.ItemSpec.build(temp, p, itemMat);
                        ItemMeta meta = item.getItemMeta();
                        List<String> lores = new ArrayList<>();
                        meta.setDisplayName(com.Util.Text.format(Variable.GUI_YML.getString(temp + ".CustomName")));

                        for (String loreLine : Variable.GUI_YML.getStringList(temp + ".Lores")) {
                           String tempstr = GuiSafe.papi(p, loreLine);

                           lores.add(tempstr);
                        }

                        for (String enchantSpec : Variable.GUI_YML.getStringList(temp + ".Enchants")) {
                           String[] tempenc = enchantSpec.split("\\,");
                           Enchantment ench = Enchantment.getByName(tempenc[0]);
                           if (ench != null) {
                              meta.addEnchant(ench, Integer.valueOf(tempenc[1]), true);
                           }
                        }

                        meta.setLore(lores);
                        item.setItemMeta(meta);
                        ManageGui.this.MainGui.setItem(Variable.GUI_YML.getInt(temp + ".Index") - 1, item);
                     }
                  }
               }
            }
         })
         .runTask(Main.JavaPlugin);
   }

   public Inventory getInventory() {
      return this.MainGui;
   }
}
