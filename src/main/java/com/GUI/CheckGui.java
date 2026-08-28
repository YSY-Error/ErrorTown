package com.GUI;

import com.Util.Platform;
import com.ErrorTown.Main;
import com.ErrorTown.Variable;
import com.Util.GuiSafe;
import com.Util.MySQL;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.scheduler.BukkitRunnable;

public class CheckGui implements InventoryHolder {
   public Inventory MainGui = Bukkit.createInventory(this, com.Util.GuiSafe.size("CheckSize", 45), com.Util.Text.format(Variable.GUI_YML.getString("CheckTitle")));

   public CheckGui(final Player p) {
      (new BukkitRunnable() {
            public void run() {
               CheckGui.this.MainGui.clear();
               if (Variable.GUI_YML.getBoolean("EnableCheckGuiNormalPane")) {
                  Material paneMat = Material.matchMaterial(
                     Variable.GUI_YML.getString("PaneMaterial") != null ? Variable.GUI_YML.getString("PaneMaterial") : "BLACK_STAINED_GLASS_PANE"
                  );
                  if (paneMat == null) {
                     paneMat = Material.BLACK_STAINED_GLASS_PANE;
                  }

                  ItemStack blb1 = new ItemStack(paneMat);
                  ItemMeta i1 = blb1.getItemMeta();
                  i1.setDisplayName("");
                  blb1.setItemMeta(i1);
                  for (int i = 0; i < 9; i++) {
                     CheckGui.this.MainGui.setItem(i, blb1);
                  }

                  CheckGui.this.MainGui.setItem(9, blb1);
                  CheckGui.this.MainGui.setItem(18, blb1);
                  CheckGui.this.MainGui.setItem(27, blb1);
                  CheckGui.this.MainGui.setItem(17, blb1);
                  CheckGui.this.MainGui.setItem(26, blb1);
                  CheckGui.this.MainGui.setItem(35, blb1);
                  for (int i = 36; i < 45; i++) {
                     if (i != 40) {
                        CheckGui.this.MainGui.setItem(i, blb1);
                     }
                  }
               }

               ConfigurationSection cs = Variable.GUI_YML.getConfigurationSection("");
               for (String temp : cs.getKeys(false)) {
                  if (Variable.GUI_YML.getString(temp + ".InMenu") != null && Variable.GUI_YML.getString(temp + ".InMenu").equalsIgnoreCase("Check") && com.Util.ItemSpec.visibleTo(temp, p)) {
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
                        CheckGui.this.MainGui.setItem(Variable.GUI_YML.getInt(temp + ".Index") - 1, item);
                     }
                  }
               }

               if (Variable.bungee) {
                  for (String worldname : MySQL.getAllWorlds()) {
                     int amount = 0;
                     boolean check_has = false;
                     if (worldname.equalsIgnoreCase(p.getName())) {
                        check_has = true;
                     }

                     if (!check_has) {
                        for (int ix = 0; ix < MySQL.getMembers(worldname).size(); ix++) {
                           String listedName = MySQL.getMembers(worldname).get(ix);
                           if (listedName.equalsIgnoreCase(p.getName()) || listedName.equals("*")) {
                              check_has = true;
                              break;
                           }
                        }
                     }

                     if (!check_has) {
                        for (int opIndex = 0; opIndex < MySQL.getOP(worldname).size(); opIndex++) {
                           String listedName = MySQL.getOP(worldname).get(opIndex);
                           if (listedName.equalsIgnoreCase(p.getName()) || listedName.equals("*")) {
                              check_has = true;
                              break;
                           }
                        }
                     }

                     if (check_has) {
                        if (!Variable.GUI_YML.getString("HeadMaterial").toUpperCase().contains("HEAD")
                           && !Variable.GUI_YML.getString("HeadMaterial").toUpperCase().contains("SKULL")) {
                           ItemStack item = new ItemStack(Platform.material(Variable.GUI_YML.getString("HeadMaterial"), Material.PLAYER_HEAD, "gui-head-material"));
                           ItemMeta i_meta = item.getItemMeta();
                           i_meta.setDisplayName(
                              Variable.Lang_YML.getString("CheckGuiHomePrefix") + worldname + Variable.Lang_YML.getString("CheckGuiHomeSuffix")
                           );
                           item.setItemMeta(i_meta);
                           CheckGui.this.MainGui.addItem(new ItemStack[]{item});
                        } else {
                           ItemStack skull = new ItemStack(Platform.material(Variable.GUI_YML.getString("HeadMaterial"), Material.PLAYER_HEAD, "gui-head-material"));
                           SkullMeta player_SKULL = (SkullMeta)skull.getItemMeta();
                           if (Variable.GUI_YML.getBoolean("EnableSkullSkin")) {
                              Player temp_p = Bukkit.getPlayer(worldname);
                              if (temp_p != null) {
                                 GuiSafe.setSkullOwner(player_SKULL, temp_p);
                              }
                           }

                           player_SKULL.setDisplayName(
                              Variable.Lang_YML.getString("CheckGuiHomePrefix") + worldname + Variable.Lang_YML.getString("CheckGuiHomeSuffix")
                           );
                           skull.setItemMeta(player_SKULL);
                           CheckGui.this.MainGui.addItem(new ItemStack[]{skull});
                        }

                        if (amount > 21) {
                           break;
                        }

                        amount++;
                     }
                  }
               } else {
                  int amountx = 0;
                  File folder = new File(Variable.Tempf);
                  for (File tempx : folder.listFiles()) {
                     boolean check_hasx = false;
                     String want_to = tempx.getPath().replace(Variable.Tempf, "").replace(".yml", "").replace(Variable.file_loc_prefix, "");
                     YamlConfiguration yamlConfiguration = YamlConfiguration.loadConfiguration(tempx);
                     for (String listedName : yamlConfiguration.getStringList("Members")) {
                        if (listedName.equalsIgnoreCase(p.getName()) || listedName.equals("*")) {
                           check_hasx = true;
                           break;
                        }
                     }

                     if (!check_hasx) {
                        for (String listedName : yamlConfiguration.getStringList("OP")) {
                           if (listedName.equalsIgnoreCase(p.getName()) || listedName.equals("*")) {
                              check_hasx = true;
                              break;
                           }
                        }
                     }

                     if (check_hasx) {
                        if (!Variable.GUI_YML.getString("HeadMaterial").toUpperCase().contains("HEAD")
                           && !Variable.GUI_YML.getString("HeadMaterial").toUpperCase().contains("SKULL")) {
                           ItemStack item = new ItemStack(Platform.material(Variable.GUI_YML.getString("HeadMaterial"), Material.PLAYER_HEAD, "gui-head-material"));
                           ItemMeta i_meta = item.getItemMeta();
                           i_meta.setDisplayName(
                              Variable.Lang_YML.getString("CheckGuiHomePrefix") + want_to + Variable.Lang_YML.getString("CheckGuiHomeSuffix")
                           );
                           item.setItemMeta(i_meta);
                           CheckGui.this.MainGui.addItem(new ItemStack[]{item});
                        } else {
                           ItemStack skull = new ItemStack(Platform.material(Variable.GUI_YML.getString("HeadMaterial"), Material.PLAYER_HEAD, "gui-head-material"));
                           SkullMeta player_SKULL = (SkullMeta)skull.getItemMeta();
                           if (Variable.GUI_YML.getBoolean("EnableSkullSkin")) {
                              Player temp_p = Bukkit.getPlayer(want_to);
                              if (temp_p != null) {
                                 GuiSafe.setSkullOwner(player_SKULL, temp_p);
                              }
                           }

                           player_SKULL.setDisplayName(
                              Variable.Lang_YML.getString("CheckGuiHomePrefix") + want_to + Variable.Lang_YML.getString("CheckGuiHomeSuffix")
                           );
                           skull.setItemMeta(player_SKULL);
                           CheckGui.this.MainGui.addItem(new ItemStack[]{skull});
                        }

                        if (amountx > 21) {
                           break;
                        }

                        amountx++;
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
