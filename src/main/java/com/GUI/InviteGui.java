package com.GUI;

import com.Util.Platform;
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
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.scheduler.BukkitRunnable;

public class InviteGui implements InventoryHolder {
   private Inventory MainGui = Bukkit.createInventory(this, com.Util.GuiSafe.size("InviteSize", 54), com.Util.Text.format(Variable.GUI_YML.getString("InviteTitle")));
   private int MaxPage = 0;
   private int NowPage = 0;
   private List<Player> players = new ArrayList<>();

   public InviteGui() {
      (new BukkitRunnable() {
            public void run() {
               InviteGui.this.MaxPage = 0;
               InviteGui.this.NowPage = 0;
               InviteGui.this.players.clear();
               boolean next_page = false;
               int amount = 0;

               for (Player p : Bukkit.getOnlinePlayers()) {
                  InviteGui.this.players.add(p);
               }

               InviteGui.this.MaxPage = (int)Math.ceil(Bukkit.getOnlinePlayers().size() / 28.0);
               InviteGui.this.MainGui.clear();
               if (Variable.GUI_YML.getBoolean("EnableInviteGuiNormalPane")) {
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
                     InviteGui.this.MainGui.setItem(i, blb1);
                  }

                  InviteGui.this.MainGui.setItem(9, blb1);
                  InviteGui.this.MainGui.setItem(18, blb1);
                  InviteGui.this.MainGui.setItem(27, blb1);
                  InviteGui.this.MainGui.setItem(17, blb1);
                  InviteGui.this.MainGui.setItem(26, blb1);
                  InviteGui.this.MainGui.setItem(35, blb1);
                  InviteGui.this.MainGui.setItem(36, blb1);
                  InviteGui.this.MainGui.setItem(44, blb1);

                  for (int i = 45; i < 54; i++) {
                     if (i != 49) {
                        InviteGui.this.MainGui.setItem(i, blb1);
                     }
                  }
               }

               ItemStack next = new ItemStack(Platform.material(Variable.GUI_YML.getString("NextMaterial"), Material.FEATHER, "gui-next-material"));
               ItemMeta next_meta = next.getItemMeta();
               next_meta.setDisplayName(Variable.GUI_YML.getString("Next"));
               next.setItemMeta(next_meta);
               InviteGui.this.MainGui.setItem(53, next);
               ItemStack prev = new ItemStack(Platform.material(Variable.GUI_YML.getString("PrevMaterial"), Material.FEATHER, "gui-prev-material"));
               ItemMeta prev_meta = next.getItemMeta();
               prev_meta.setDisplayName(Variable.GUI_YML.getString("Prev"));
               prev.setItemMeta(prev_meta);
               InviteGui.this.MainGui.setItem(45, prev);
               ConfigurationSection cs = Variable.GUI_YML.getConfigurationSection("");

               for (String temp : cs.getKeys(false)) {
                  if (Variable.GUI_YML.getString(temp + ".InMenu") != null && Variable.GUI_YML.getString(temp + ".InMenu").equalsIgnoreCase("Invite") && com.Util.ItemSpec.visibleTo(temp, null)) {
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
                        ItemStack item = com.Util.ItemSpec.build(temp, null, itemMat);
                        ItemMeta meta = item.getItemMeta();
                        List<String> lores = new ArrayList<>();
                        meta.setDisplayName(com.Util.Text.format(Variable.GUI_YML.getString(temp + ".CustomName")));

                        for (String loreLine : Variable.GUI_YML.getStringList(temp + ".Lores")) {
                           String tempstr = GuiSafe.papi(null, loreLine);

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
                        InviteGui.this.MainGui.setItem(Variable.GUI_YML.getInt(temp + ".Index") - 1, item);
                     }
                  }
               }

               for (int cx = InviteGui.this.NowPage * 28; cx < InviteGui.this.players.size() && cx < (InviteGui.this.NowPage + 1) * 28 && cx >= 0; cx++) {
                  Player tempx = InviteGui.this.players.get(cx);
                  if (!Variable.GUI_YML.getString("HeadMaterial").toUpperCase().contains("HEAD")
                     && !Variable.GUI_YML.getString("HeadMaterial").toUpperCase().contains("SKULL")) {
                     ItemStack item = new ItemStack(Platform.material(Variable.GUI_YML.getString("HeadMaterial"), Material.PLAYER_HEAD, "gui-head-material"));
                     ItemMeta i_meta = item.getItemMeta();
                     i_meta.setDisplayName(Variable.Lang_YML.getString("InviteGuiPrefix") + tempx.getName());
                     List<String> lores = new ArrayList<>();

                     for (String str : Variable.Lang_YML.getStringList("InviteGuiLores")) {
                        lores.add(str);
                     }

                     i_meta.setLore(lores);
                     item.setItemMeta(i_meta);
                     InviteGui.this.MainGui.addItem(new ItemStack[]{item});
                  } else {
                     ItemStack skull = new ItemStack(Platform.material(Variable.GUI_YML.getString("HeadMaterial"), Material.PLAYER_HEAD, "gui-head-material"));
                     SkullMeta player_SKULL = (SkullMeta)skull.getItemMeta();
                     if (Variable.GUI_YML.getBoolean("EnableSkullSkin") && tempx != null) {
                        GuiSafe.setSkullOwner(player_SKULL, tempx);
                     }

                     player_SKULL.setDisplayName(Variable.Lang_YML.getString("InviteGuiPrefix") + tempx.getName());
                     List<String> lores = new ArrayList<>();

                     for (String str : Variable.Lang_YML.getStringList("InviteGuiLores")) {
                        lores.add(str);
                     }

                     player_SKULL.setLore(lores);
                     skull.setItemMeta(player_SKULL);
                     InviteGui.this.MainGui.addItem(new ItemStack[]{skull});
                  }
               }
            }
         })
         .runTask(Main.JavaPlugin);
   }

   public void OpenNextInventory(final Player p) {
      (new BukkitRunnable() {
            public void run() {
               if (InviteGui.this.NowPage + 2 <= InviteGui.this.MaxPage) {
                  InviteGui.this.NowPage++;
                  InviteGui.this.MainGui.clear();
                  if (Variable.GUI_YML.getBoolean("EnableInviteGuiNormalPane")) {
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
                        InviteGui.this.MainGui.setItem(i, blb1);
                     }

                     InviteGui.this.MainGui.setItem(9, blb1);
                     InviteGui.this.MainGui.setItem(18, blb1);
                     InviteGui.this.MainGui.setItem(27, blb1);
                     InviteGui.this.MainGui.setItem(17, blb1);
                     InviteGui.this.MainGui.setItem(26, blb1);
                     InviteGui.this.MainGui.setItem(35, blb1);
                     InviteGui.this.MainGui.setItem(36, blb1);
                     InviteGui.this.MainGui.setItem(44, blb1);

                     for (int i = 45; i < 54; i++) {
                        if (i != 49) {
                           InviteGui.this.MainGui.setItem(i, blb1);
                        }
                     }
                  }

                  ItemStack next = new ItemStack(Platform.material(Variable.GUI_YML.getString("NextMaterial"), Material.FEATHER, "gui-next-material"));
                  ItemMeta next_meta = next.getItemMeta();
                  next_meta.setDisplayName(Variable.GUI_YML.getString("Next"));
                  next.setItemMeta(next_meta);
                  InviteGui.this.MainGui.setItem(53, next);
                  ItemStack prev = new ItemStack(Platform.material(Variable.GUI_YML.getString("PrevMaterial"), Material.FEATHER, "gui-prev-material"));
                  ItemMeta prev_meta = next.getItemMeta();
                  prev_meta.setDisplayName(Variable.GUI_YML.getString("Prev"));
                  prev.setItemMeta(prev_meta);
                  InviteGui.this.MainGui.setItem(45, prev);
                  ConfigurationSection cs = Variable.GUI_YML.getConfigurationSection("");

                  for (String temp : cs.getKeys(false)) {
                     if (Variable.GUI_YML.getString(temp + ".InMenu") != null && Variable.GUI_YML.getString(temp + ".InMenu").equalsIgnoreCase("Invite") && com.Util.ItemSpec.visibleTo(temp, p)) {
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
                           InviteGui.this.MainGui.setItem(Variable.GUI_YML.getInt(temp + ".Index") - 1, item);
                        }
                     }
                  }

                  for (int cx = InviteGui.this.NowPage * 28; cx < InviteGui.this.players.size() && cx < (InviteGui.this.NowPage + 1) * 28 && cx >= 0; cx++) {
                     Player px = InviteGui.this.players.get(cx);
                     if (!Variable.GUI_YML.getString("HeadMaterial").toUpperCase().contains("HEAD")
                        && !Variable.GUI_YML.getString("HeadMaterial").toUpperCase().contains("SKULL")) {
                        ItemStack item = new ItemStack(Platform.material(Variable.GUI_YML.getString("HeadMaterial"), Material.PLAYER_HEAD, "gui-head-material"));
                        ItemMeta i_meta = item.getItemMeta();
                        i_meta.setDisplayName(Variable.Lang_YML.getString("InviteGuiPrefix") + px.getName());
                        List<String> lores = new ArrayList<>();

                        for (String str : Variable.Lang_YML.getStringList("InviteGuiLores")) {
                           lores.add(str);
                        }

                        i_meta.setLore(lores);
                        item.setItemMeta(i_meta);
                        InviteGui.this.MainGui.addItem(new ItemStack[]{item});
                     } else {
                        ItemStack skull = new ItemStack(Platform.material(Variable.GUI_YML.getString("HeadMaterial"), Material.PLAYER_HEAD, "gui-head-material"));
                        SkullMeta player_SKULL = (SkullMeta)skull.getItemMeta();
                        if (Variable.GUI_YML.getBoolean("EnableSkullSkin") && px != null) {
                           GuiSafe.setSkullOwner(player_SKULL, px);
                        }

                        player_SKULL.setDisplayName(Variable.Lang_YML.getString("InviteGuiPrefix") + px.getName());
                        List<String> lores = new ArrayList<>();

                        for (String str : Variable.Lang_YML.getStringList("InviteGuiLores")) {
                           lores.add(str);
                        }

                        player_SKULL.setLore(lores);
                        skull.setItemMeta(player_SKULL);
                        InviteGui.this.MainGui.addItem(new ItemStack[]{skull});
                     }
                  }

                  p.openInventory(InviteGui.this.MainGui);
               }
            }
         })
         .runTask(Main.JavaPlugin);
   }

   public void OpenPrevInventory(final Player p) {
      (new BukkitRunnable() {
            public void run() {
               if (InviteGui.this.NowPage - 1 >= 0) {
                  InviteGui.this.NowPage--;
                  InviteGui.this.MainGui.clear();
                  if (Variable.GUI_YML.getBoolean("EnableInviteGuiNormalPane")) {
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
                        InviteGui.this.MainGui.setItem(i, blb1);
                     }

                     InviteGui.this.MainGui.setItem(9, blb1);
                     InviteGui.this.MainGui.setItem(18, blb1);
                     InviteGui.this.MainGui.setItem(27, blb1);
                     InviteGui.this.MainGui.setItem(17, blb1);
                     InviteGui.this.MainGui.setItem(26, blb1);
                     InviteGui.this.MainGui.setItem(35, blb1);
                     InviteGui.this.MainGui.setItem(36, blb1);
                     InviteGui.this.MainGui.setItem(44, blb1);

                     for (int i = 45; i < 54; i++) {
                        if (i != 49) {
                           InviteGui.this.MainGui.setItem(i, blb1);
                        }
                     }
                  }

                  ItemStack next = new ItemStack(Platform.material(Variable.GUI_YML.getString("NextMaterial"), Material.FEATHER, "gui-next-material"));
                  ItemMeta next_meta = next.getItemMeta();
                  next_meta.setDisplayName(Variable.GUI_YML.getString("Next"));
                  next.setItemMeta(next_meta);
                  InviteGui.this.MainGui.setItem(53, next);
                  ItemStack prev = new ItemStack(Platform.material(Variable.GUI_YML.getString("PrevMaterial"), Material.FEATHER, "gui-prev-material"));
                  ItemMeta prev_meta = next.getItemMeta();
                  prev_meta.setDisplayName(Variable.GUI_YML.getString("Prev"));
                  prev.setItemMeta(prev_meta);
                  InviteGui.this.MainGui.setItem(45, prev);
                  ConfigurationSection cs = Variable.GUI_YML.getConfigurationSection("");

                  for (String temp : cs.getKeys(false)) {
                     if (Variable.GUI_YML.getString(temp + ".InMenu") != null && Variable.GUI_YML.getString(temp + ".InMenu").equalsIgnoreCase("Invite") && com.Util.ItemSpec.visibleTo(temp, p)) {
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
                           InviteGui.this.MainGui.setItem(Variable.GUI_YML.getInt(temp + ".Index") - 1, item);
                        }
                     }
                  }

                  for (int cx = InviteGui.this.NowPage * 28; cx < InviteGui.this.players.size() && cx < (InviteGui.this.NowPage + 1) * 28 && cx >= 0; cx++) {
                     Player px = InviteGui.this.players.get(cx);
                     if (!Variable.GUI_YML.getString("HeadMaterial").toUpperCase().contains("HEAD")
                        && !Variable.GUI_YML.getString("HeadMaterial").toUpperCase().contains("SKULL")) {
                        ItemStack item = new ItemStack(Platform.material(Variable.GUI_YML.getString("HeadMaterial"), Material.PLAYER_HEAD, "gui-head-material"));
                        ItemMeta i_meta = item.getItemMeta();
                        i_meta.setDisplayName(Variable.Lang_YML.getString("InviteGuiPrefix") + px.getName());
                        List<String> lores = new ArrayList<>();

                        for (String str : Variable.Lang_YML.getStringList("InviteGuiLores")) {
                           lores.add(str);
                        }

                        i_meta.setLore(lores);
                        item.setItemMeta(i_meta);
                        InviteGui.this.MainGui.addItem(new ItemStack[]{item});
                     } else {
                        ItemStack skull = new ItemStack(Platform.material(Variable.GUI_YML.getString("HeadMaterial"), Material.PLAYER_HEAD, "gui-head-material"));
                        SkullMeta player_SKULL = (SkullMeta)skull.getItemMeta();
                        if (Variable.GUI_YML.getBoolean("EnableSkullSkin") && px != null) {
                           GuiSafe.setSkullOwner(player_SKULL, px);
                        }

                        player_SKULL.setDisplayName(Variable.Lang_YML.getString("InviteGuiPrefix") + px.getName());
                        List<String> lores = new ArrayList<>();

                        for (String str : Variable.Lang_YML.getStringList("InviteGuiLores")) {
                           lores.add(str);
                        }

                        player_SKULL.setLore(lores);
                        skull.setItemMeta(player_SKULL);
                        InviteGui.this.MainGui.addItem(new ItemStack[]{skull});
                     }
                  }

                  p.openInventory(InviteGui.this.MainGui);
               }
            }
         })
         .runTask(Main.JavaPlugin);
   }

   public int getMaxPage() {
      return this.MaxPage;
   }

   public int getNowPage() {
      return this.NowPage;
   }

   public Inventory getInventory() {
      return this.MainGui;
   }
}
