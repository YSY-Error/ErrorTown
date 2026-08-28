package com.GUI;

import com.ErrorTown.Main;
import com.ErrorTown.Variable;
import com.Util.GuiSafe;
import com.Util.HomeAPI;
import java.util.ArrayList;
import java.util.List;
import me.clip.placeholderapi.PlaceholderAPI;
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

public class OwnedHomesGui implements InventoryHolder {
   public Inventory MainGui = Bukkit.createInventory(this, com.Util.GuiSafe.size("OwnedHomesSize", 27), com.Util.GuiSafe.title("OwnedHomesTitle", "§8>>> §6选择要返回的庄园"));

   public OwnedHomesGui(final Player player) {
      (new BukkitRunnable() {
         public void run() {
            OwnedHomesGui.this.MainGui.clear();
            OwnedHomesGui.this.buildPane();
            OwnedHomesGui.this.buildHomes(player);
            OwnedHomesGui.this.buildBackButton(player);
         }
      }).runTask(Main.JavaPlugin);
   }

   private void buildPane() {
      Material paneMat = Material.matchMaterial(
         Variable.GUI_YML.getString("PaneMaterial") != null ? Variable.GUI_YML.getString("PaneMaterial") : "BLACK_STAINED_GLASS_PANE"
      );
      if (paneMat == null) {
         paneMat = Material.BLACK_STAINED_GLASS_PANE;
      }

      ItemStack pane = new ItemStack(paneMat);
      ItemMeta meta = pane.getItemMeta();
      meta.setDisplayName("");
      pane.setItemMeta(meta);

      for (int i = 0; i < 9; i++) {
         this.MainGui.setItem(i, pane);
      }

      for (int i = 18; i < 27; i++) {
         this.MainGui.setItem(i, pane);
      }
   }

   private void buildHomes(Player player) {
      List<String> homes = HomeAPI.getOwnedHomes(player.getName());
      int[] slots = new int[]{10, 11, 12, 13, 14, 15, 16};

      for (int i = 0; i < homes.size() && i < slots.length; i++) {
         String homeName = homes.get(i);
         ItemStack item = new ItemStack(Material.GRASS_BLOCK);
         ItemMeta meta = item.getItemMeta();
         meta.setDisplayName("§a" + homeName);
         List<String> lore = new ArrayList<>();
         lore.add("§7序号: §e" + (i + 1));
         lore.add("§7左键返回这个庄园");

         lore = GuiSafe.papi(player, lore);

         meta.setLore(lore);
         meta.addEnchant(Enchantment.LURE, 1, true);
         item.setItemMeta(meta);
         this.MainGui.setItem(slots[i], item);
      }
   }

   private void buildBackButton(Player player) {
      ConfigurationSection cs = Variable.GUI_YML.getConfigurationSection("");
      if (cs != null) {
         for (String key : cs.getKeys(false)) {
            if ("MAIN".equalsIgnoreCase(Variable.GUI_YML.getString(key + ".InMenu"))
               && "sh open main".equalsIgnoreCase(Variable.GUI_YML.getString(key + ".LeftInTo"))) {
               Material itemMat = com.Util.ItemSpec.material(Variable.GUI_YML.getString(key + ".Material"));
               if (itemMat == null) {
                  itemMat = Material.ARROW;
               }

               ItemStack item = com.Util.ItemSpec.build(key, player, itemMat);
               ItemMeta meta = item.getItemMeta();
               meta.setDisplayName(com.Util.Text.format(Variable.GUI_YML.getString(key + ".CustomName")));
               List<String> lores = new ArrayList<>();

               for (String line : Variable.GUI_YML.getStringList(key + ".Lores")) {
                  line = GuiSafe.papi(player, line);

                  lores.add(line);
               }

               meta.setLore(lores);
               item.setItemMeta(meta);
               this.MainGui.setItem(22, item);
               return;
            }
         }
      }
   }

   public Inventory getInventory() {
      return this.MainGui;
   }
}
