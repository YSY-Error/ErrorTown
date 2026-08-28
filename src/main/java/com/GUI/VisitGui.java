package com.GUI;

import com.Util.Platform;
import com.ErrorTown.Main;
import com.ErrorTown.Variable;
import com.Util.GuiSafe;
import com.Util.Home;
import com.Util.HomeAPI;
import com.Util.MySQL;
import com.Util.Util;
import com.Util.VisitStatistic;
import java.util.ArrayList;
import java.util.List;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.scheduler.BukkitRunnable;

public class VisitGui implements InventoryHolder {
   public Inventory MainGui = Bukkit.createInventory(this, com.Util.GuiSafe.size("VisitSize", 54), com.Util.Text.format(Variable.GUI_YML.getString("VisitTitle")));
   public int MaxPage = 0;
   public int NowPage = 0;
   public ArrayList<Home> players = new ArrayList<>();
   int item_add_amount = 0;

   private static boolean isMainHomeWorld(String rawName) {
      String name = rawName.replace(Variable.world_prefix, "");
      String netherSuffix = Util.getNetherSuffix();
      return name.endsWith(netherSuffix) ? false : !name.endsWith("_end") && !name.endsWith("_the_end");
   }

   private static void appendTitleDesc(List<String> lores, Home home) {
      try {
         String title = home.getTitle();
         if (title != null && !title.isEmpty()) {
            lores.add("§d家园标题: §f" + title);
         }
      } catch (Exception failure) {
         com.Util.Diag.warnOnce("visit-title", "Could not read a home title for the visit menu", failure);
      }

      try {
         List<String> desc = home.getDescription();
         if (desc != null && !desc.isEmpty()) {
            lores.add("§8§m-----------");

            for (String line : desc) {
               if (line != null && !line.isEmpty()) {
                  lores.add("§7" + line);
               }
            }
         }
      } catch (Exception failure) {
         com.Util.Diag.warnOnce("visit-desc", "Could not read a home description for the visit menu", failure);
      }
   }

   public VisitGui() {
      (new BukkitRunnable() {
            public void run() {
               ConfigurationSection dd = Variable.GUI_YML.getConfigurationSection("");

               for (String temp : dd.getKeys(false)) {
                  if (Variable.GUI_YML.getString(temp + ".InMenu") != null && Variable.GUI_YML.getString(temp + ".InMenu").equalsIgnoreCase("Visit") && com.Util.ItemSpec.visibleTo(temp, null)) {
                     VisitGui.this.item_add_amount++;
                  }
               }

               VisitGui.this.MaxPage = 0;
               VisitGui.this.NowPage = 0;
               VisitGui.this.players.clear();
               if (Main.JavaPlugin.getConfig().getBoolean("BungeeCord")) {
                  if (Main.JavaPlugin.getConfig().getString("VisitGuiShowAll").equalsIgnoreCase("Public")) {
                     for (String world : MySQL.getAllWorlds()) {
                        if (VisitGui.isMainHomeWorld(world)) {
                           Home home = HomeAPI.getHome(world);
                           if (home != null && home.isAllowStranger()) {
                              VisitGui.this.players.add(home);
                           }
                        }
                     }
                  } else if (Main.JavaPlugin.getConfig().getString("VisitGuiShowAll").equalsIgnoreCase("ALL")) {
                     for (String worldx : MySQL.getAllWorlds()) {
                        if (VisitGui.isMainHomeWorld(worldx)) {
                           Home home = HomeAPI.getHome(worldx);
                           if (home != null) {
                              VisitGui.this.players.add(home);
                           }
                        }
                     }
                  } else {
                     for (World loadedWorld0 : Bukkit.getWorlds()) {
                        if (VisitGui.isMainHomeWorld(loadedWorld0.getName()) && Util.CheckIsHome(loadedWorld0.getName())) {
                           Home home = HomeAPI.getHome(loadedWorld0.getName());
                           if (home != null) {
                              VisitGui.this.players.add(home);
                           }
                        }
                     }
                  }

                  VisitGui.this.MaxPage = (int)Math.ceil(Math.ceil(VisitGui.this.players.size()) / (29 - VisitGui.this.item_add_amount) * 1.0);
               } else {
                  for (World loadedWorld : Bukkit.getWorlds()) {
                     if (VisitGui.isMainHomeWorld(loadedWorld.getName()) && Util.CheckIsHome(loadedWorld.getName())) {
                        Home home = HomeAPI.getHome(loadedWorld.getName());
                        if (home != null && (!Main.JavaPlugin.getConfig().getString("VisitGuiShowAll").equalsIgnoreCase("Public") || home.isAllowStranger())) {
                           VisitGui.this.players.add(home);
                        }
                     }
                  }

                  VisitGui.this.MaxPage = (int)Math.ceil(Math.ceil(VisitGui.this.players.size()) / (29 - VisitGui.this.item_add_amount));
               }

               List<VisitStatistic> vst_list = new ArrayList<>();

               for (Home home : VisitGui.this.players) {
                  double value = home.getFlowers() * Main.JavaPlugin.getConfig().getDouble("FlowerAdd")
                     + home.getPopularity() * Main.JavaPlugin.getConfig().getDouble("PopularityAdd");
                  vst_list.add(new VisitStatistic(home, value));
               }

               for (int i = 0; i < vst_list.size() - 1; i++) {
                  for (int j = 0; j < vst_list.size() - 1 - i; j++) {
                     if (vst_list.get(j).value < vst_list.get(j + 1).value) {
                        VisitStatistic tempx = vst_list.get(j);
                        vst_list.set(j, vst_list.get(j + 1));
                        vst_list.set(j + 1, tempx);
                     }
                  }
               }

               VisitGui.this.players.clear();

               for (VisitStatistic statistic : vst_list) {
                  VisitGui.this.players.add(statistic.home);
               }

               VisitGui.this.MainGui.clear();
               VisitGui.buildPane(VisitGui.this.MainGui);
               VisitGui.buildNavButtons(VisitGui.this.MainGui, null);
               VisitGui.buildConfigItems(VisitGui.this.MainGui, null);
               VisitGui.buildHomeItems(VisitGui.this.MainGui, VisitGui.this.NowPage, VisitGui.this.item_add_amount, VisitGui.this.players);
            }
         })
         .runTask(Main.JavaPlugin);
   }

   private static void buildPane(Inventory inv) {
      if (Variable.GUI_YML.getBoolean("EnableVisitGuiNormalPane")) {
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
            inv.setItem(i, blb1);
         }

         inv.setItem(9, blb1);
         inv.setItem(18, blb1);
         inv.setItem(27, blb1);
         inv.setItem(17, blb1);
         inv.setItem(26, blb1);
         inv.setItem(35, blb1);
         inv.setItem(36, blb1);
         inv.setItem(44, blb1);

         for (int i = 45; i < 54; i++) {
            if (i != 49) {
               inv.setItem(i, blb1);
            }
         }
      }
   }

   private static void buildNavButtons(Inventory inv, Player p) {
      ItemStack next = new ItemStack(Platform.material(Variable.GUI_YML.getString("NextMaterial"), Material.FEATHER, "gui-next-material"));
      ItemMeta next_meta = next.getItemMeta();
      next_meta.setDisplayName(Variable.GUI_YML.getString("Next"));
      next.setItemMeta(next_meta);
      inv.setItem(53, next);
      ItemStack prev = new ItemStack(Platform.material(Variable.GUI_YML.getString("PrevMaterial"), Material.FEATHER, "gui-prev-material"));
      ItemMeta prev_meta = prev.getItemMeta();
      prev_meta.setDisplayName(Variable.GUI_YML.getString("Prev"));
      prev.setItemMeta(prev_meta);
      inv.setItem(45, prev);
   }

   private static void buildConfigItems(Inventory inv, Player p) {
      ConfigurationSection cs = Variable.GUI_YML.getConfigurationSection("");

      for (String temp : cs.getKeys(false)) {
         if (Variable.GUI_YML.getString(temp + ".InMenu") != null && Variable.GUI_YML.getString(temp + ".InMenu").equalsIgnoreCase("Visit") && com.Util.ItemSpec.visibleTo(temp, p)) {
            Material itemMat = com.Util.ItemSpec.material(Variable.GUI_YML.getString(temp + ".Material"));
            if (itemMat != null) {
               ItemStack item = com.Util.ItemSpec.build(temp, p, itemMat);
               ItemMeta meta = item.getItemMeta();
               List<String> lores = new ArrayList<>();
               meta.setDisplayName(com.Util.Text.format(Variable.GUI_YML.getString(temp + ".CustomName")));

               for (String tempstr0 : Variable.GUI_YML.getStringList(temp + ".Lores")) {
                  String tempstr = tempstr0;

                  tempstr = GuiSafe.papi(p, tempstr);

                  lores.add(tempstr);
               }

               for (String enc : Variable.GUI_YML.getStringList(temp + ".Enchants")) {
                  String[] tempenc = enc.split("\\,");
                  Enchantment ench = Enchantment.getByName(tempenc[0]);
                  if (ench != null) {
                     meta.addEnchant(ench, Integer.parseInt(tempenc[1]), true);
                  }
               }

               meta.setLore(lores);
               item.setItemMeta(meta);
               inv.setItem(Variable.GUI_YML.getInt(temp + ".Index") - 1, item);
            }
         }
      }
   }

   private static void buildHomeItems(Inventory inv, int page, int itemAddAmount, ArrayList<Home> players) {
      int start = page * (29 - itemAddAmount);
      int end = Math.min(players.size(), (page + 1) * (29 - itemAddAmount));
      boolean useHead = Variable.GUI_YML.getString("HeadMaterial").toUpperCase().contains("HEAD")
         || Variable.GUI_YML.getString("HeadMaterial").toUpperCase().contains("SKULL");

      for (int c = start; c < end; c++) {
         Home home = players.get(c);
         String homeName = home.getName().replace(Variable.world_prefix, "");
         if (Util.CheckIsHome(homeName)) {
            String displayName = Variable.Lang_YML.getString("VisitGuiHomePrefix") + homeName + Variable.Lang_YML.getString("VisitGuiHomeSuffix");
            List<String> lores = new ArrayList<>();
            List<String> loreTemplate = Variable.GUI_YML.getStringList("VisitGuiLores");

            for (int i = 0; i < loreTemplate.size() - 1; i++) {
               String loreLine = loreTemplate.get(i).replace("<Name>", homeName);

               loreLine = GuiSafe.papi(null, loreLine);

               lores.add(loreLine);
            }

            if (Variable.GUI_YML.getBoolean("VisitSlogan")) {
               try {
                  for (String str : home.getAdvertisement()) {
                     lores.add(str);
                  }
               } catch (Exception failure) {
                  com.Util.Diag.warnOnce("visit-slogan", "Could not read a home advertisement for the visit menu", failure);
               }
            }

            if (!loreTemplate.isEmpty()) {
               String lastLine = loreTemplate.get(loreTemplate.size() - 1).replace("<Name>", homeName);

               lastLine = GuiSafe.papi(null, lastLine);

               lores.add(lastLine);
            }

            appendTitleDesc(lores, home);
            lores.add("§8§m----------");
            lores.add("§e左键 §7参观  §8|  §e右键 §7赠送鲜花");
            ItemStack item;
            if (useHead) {
               item = new ItemStack(Platform.material(Variable.GUI_YML.getString("HeadMaterial"), Material.PLAYER_HEAD, "gui-head-material"));
               SkullMeta player_SKULL = (SkullMeta)item.getItemMeta();
               Player temp_p = Bukkit.getPlayer(homeName);
               if (Variable.GUI_YML.getBoolean("EnableSkullSkin") && temp_p != null) {
                  GuiSafe.setSkullOwner(player_SKULL, temp_p);
               }

               player_SKULL.setDisplayName(displayName);
               player_SKULL.setLore(lores);
               item.setItemMeta(player_SKULL);
            } else {
               item = new ItemStack(Platform.material(Variable.GUI_YML.getString("HeadMaterial"), Material.PLAYER_HEAD, "gui-head-material"));
               ItemMeta i_meta = item.getItemMeta();
               i_meta.setDisplayName(displayName);
               i_meta.setLore(lores);
               item.setItemMeta(i_meta);
            }

            if (!home.getIcon().equalsIgnoreCase("")) {
               GuiSafe.applyIcon(item, home.getIcon());
            }

            item.setAmount(1);
            inv.addItem(new ItemStack[]{item});
         }
      }
   }

   public void OpenNextInventory(final Player p) {
      (new BukkitRunnable() {
         public void run() {
            if (VisitGui.this.NowPage + 2 <= VisitGui.this.MaxPage) {
               VisitGui.this.NowPage++;
               VisitGui.this.MainGui.clear();
               VisitGui.buildPane(VisitGui.this.MainGui);
               VisitGui.buildNavButtons(VisitGui.this.MainGui, p);
               VisitGui.buildConfigItems(VisitGui.this.MainGui, p);
               VisitGui.buildHomeItems(VisitGui.this.MainGui, VisitGui.this.NowPage, VisitGui.this.item_add_amount, VisitGui.this.players);
               (new BukkitRunnable() {
                  public void run() {
                     p.openInventory(VisitGui.this.MainGui);
                  }
               }).runTask(Main.JavaPlugin);
            }
         }
      }).runTask(Main.JavaPlugin);
   }

   public void OpenPrevInventory(final Player p) {
      (new BukkitRunnable() {
         public void run() {
            if (VisitGui.this.NowPage - 1 >= 0) {
               VisitGui.this.NowPage--;
               VisitGui.this.MainGui.clear();
               VisitGui.buildPane(VisitGui.this.MainGui);
               VisitGui.buildNavButtons(VisitGui.this.MainGui, p);
               VisitGui.buildConfigItems(VisitGui.this.MainGui, p);
               VisitGui.buildHomeItems(VisitGui.this.MainGui, VisitGui.this.NowPage, VisitGui.this.item_add_amount, VisitGui.this.players);
               (new BukkitRunnable() {
                  public void run() {
                     p.openInventory(VisitGui.this.MainGui);
                  }
               }).runTask(Main.JavaPlugin);
            }
         }
      }).runTask(Main.JavaPlugin);
   }

   public Inventory getInventory() {
      return this.MainGui;
   }
}
