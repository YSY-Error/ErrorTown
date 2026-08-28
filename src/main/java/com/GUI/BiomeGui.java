package com.GUI;

import com.ErrorTown.Main;
import com.ErrorTown.Variable;
import com.Util.Home;
import com.Util.HomeAPI;
import com.Util.HomeTerrainPolicy;
import com.Util.Util;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;

public class BiomeGui implements InventoryHolder {
   public Inventory MainGui = Bukkit.createInventory(this, com.Util.GuiSafe.size("BiomeSize", 54), com.Util.GuiSafe.title("BiomeTitle", "§b>>> §d群系修改"));
   private static final String[] WARM_BIOMES = new String[]{
      "PLAINS", "SUNFLOWER_PLAINS", "FOREST", "FLOWER_FOREST", "BIRCH_FOREST", "DARK_FOREST", "SWAMP", "MANGROVE_SWAMP", "MEADOW", "CHERRY_GROVE"
   };
   private static final String[] DRY_BIOMES = new String[]{"DESERT", "SAVANNA", "BADLANDS", "ERODED_BADLANDS", "WOODED_BADLANDS", "SAVANNA_PLATEAU"};
   private static final String[] COLD_BIOMES = new String[]{
      "TAIGA",
      "SNOWY_TAIGA",
      "ICE_SPIKES",
      "FROZEN_OCEAN",
      "SNOWY_PLAINS",
      "SNOWY_BEACH",
      "FROZEN_RIVER",
      "GROVE",
      "SNOWY_SLOPES",
      "JAGGED_PEAKS",
      "FROZEN_PEAKS"
   };
   private static final String[] SPECIAL_BIOMES = new String[]{
      "MUSHROOM_FIELDS", "JUNGLE", "BAMBOO_JUNGLE", "SPARSE_JUNGLE", "STONY_PEAKS", "STONY_SHORE", "DRIPSTONE_CAVES", "LUSH_CAVES", "DEEP_DARK"
   };
   private static final String[] NETHER_BIOMES = new String[]{"NETHER_WASTES", "SOUL_SAND_VALLEY", "CRIMSON_FOREST", "WARPED_FOREST", "BASALT_DELTAS"};
   private static final String[] OCEAN_BIOMES = new String[]{
      "OCEAN",
      "DEEP_OCEAN",
      "WARM_OCEAN",
      "LUKEWARM_OCEAN",
      "COLD_OCEAN",
      "DEEP_LUKEWARM_OCEAN",
      "DEEP_COLD_OCEAN",
      "DEEP_FROZEN_OCEAN",
      "RIVER",
      "BEACH",
      "SNOWY_BEACH"
   };
   private static final String[] END_BIOMES = new String[]{"THE_END", "END_HIGHLANDS", "END_MIDLANDS", "END_BARRENS", "SMALL_END_ISLANDS"};
   private static final Map<String, String> BIOME_CN = new HashMap<>();
   private Player currentPlayer;
   private String selectedBiome;
   private String[] currentShownBiomes;
   private Material currentShownIcon;

   private static String getCN(String biome) {
      String cn = BIOME_CN.get(biome);
      return cn != null ? cn : biome;
   }

   public BiomeGui(final Player p) {
      this.currentPlayer = p;
      this.selectedBiome = null;
      this.currentShownBiomes = WARM_BIOMES;
      this.currentShownIcon = Material.GRASS_BLOCK;
      (new BukkitRunnable() {
         public void run() {
            BiomeGui.this.MainGui.clear();
            BiomeGui.this.buildGui(p);
         }
      }).runTask(Main.JavaPlugin);
   }

   private void buildGui(Player p) {
      Material paneMat = Material.matchMaterial(
         Variable.GUI_YML.getString("PaneMaterial") != null ? Variable.GUI_YML.getString("PaneMaterial") : "BLACK_STAINED_GLASS_PANE"
      );
      if (paneMat == null) {
         paneMat = Material.BLACK_STAINED_GLASS_PANE;
      }

      ItemStack glass = new ItemStack(paneMat);
      ItemMeta gm = glass.getItemMeta();
      gm.setDisplayName("");
      glass.setItemMeta(gm);

      for (int i = 0; i < 9; i++) {
         this.MainGui.setItem(i, glass);
      }

      this.MainGui.setItem(9, glass);
      this.MainGui.setItem(17, glass);
      this.MainGui.setItem(18, glass);
      this.MainGui.setItem(26, glass);
      this.MainGui.setItem(27, glass);
      this.MainGui.setItem(35, glass);
      this.MainGui.setItem(36, glass);
      this.MainGui.setItem(44, glass);

      for (int i = 45; i < 54; i++) {
         this.MainGui.setItem(i, glass);
      }

      String currentBiome = "未知";

      try {
         currentBiome = getCN(p.getLocation().getBlock().getBiome().name());
      } catch (Exception var7) {
         com.Util.Diag.warnOnce("biome-current-name", "Could not resolve the current biome name", var7);
      }

      this.MainGui
         .setItem(
            4,
            this.makeItem(
               Material.OAK_SIGN,
               "§6§l当前区块群系: §e" + currentBiome,
               "§7当前已选: " + (this.selectedBiome != null ? "§a" + getCN(this.selectedBiome) : "§c未选择"),
               "§e左键选择群系后,点击底部确认按钮"
            )
         );
      this.MainGui.setItem(10, this.makeCategoryItem(Material.GRASS_BLOCK, "§a温和群系", "§7草原/森林/樱花/沼泽等"));
      this.MainGui.setItem(12, this.makeCategoryItem(Material.SAND, "§6干燥群系", "§7沙漠/热带草原/恶地等"));
      this.MainGui.setItem(14, this.makeCategoryItem(Material.SNOWBALL, "§b寒冷群系", "§7针叶林/雪原/冰刺等"));
      this.MainGui.setItem(16, this.makeCategoryItem(Material.PRISMARINE_SHARD, "§9海洋群系", "§7各类海洋/河流/海滩等"));
      this.MainGui.setItem(19, this.makeCategoryItem(Material.MYCELIUM, "§d特殊群系", "§7蘑菇岛/丛林/深暗之域等"));
      this.MainGui.setItem(21, this.makeCategoryItem(Material.NETHERRACK, "§c下界群系", "§7下界荒地/绯红森林/诡异森林等"));
      this.MainGui.setItem(23, this.makeCategoryItem(Material.END_STONE, "§5末地群系", "§7末地/末地高地等"));
      this.showBiomeList(this.currentShownBiomes, this.currentShownIcon);
      this.MainGui
         .setItem(
            48,
            this.makeItem(
               Material.LIME_DYE,
               "§a§l确认 - 修改当前区块",
               "§d费用: §6" + Main.JavaPlugin.getConfig().getInt("BiomeChange.SingleChunkCost") + " 金币",
               "§7将脚下所在的 §e1×1 区块§7 (16×16 格) 修改为所选群系"
            )
         );
      this.MainGui
         .setItem(
            49,
            this.makeItem(
               Material.ORANGE_DYE,
               "§6§l确认 - 修改边界内全部",
               "§d费用: §6" + Main.JavaPlugin.getConfig().getInt("BiomeChange.AllChunksCost") + " 金币",
               "§7将家园边界内所有区块修改为所选群系",
               "§8(默认等级约 §e7×7 区块§8, 随等级升级范围增大)"
            )
         );
      this.MainGui.setItem(50, this.makeItem(Material.ARROW, "§c返回管理菜单", "§7点击返回"));
   }

   private void showBiomeList(String[] biomes, Material icon) {
      int[] slots = new int[]{28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43};

      for (int s : slots) {
         this.MainGui.setItem(s, null);
      }

      for (int i = 0; i < biomes.length && i < slots.length; i++) {
         String biomeName = biomes[i];
         boolean selected = biomeName.equals(this.selectedBiome);
         Material mat = selected ? Material.LIME_STAINED_GLASS_PANE : icon;
         String prefix = selected ? "§a§l✓ " : "§e";
         this.MainGui.setItem(slots[i], this.makeItem(mat, prefix + getCN(biomeName), "§7ID: §8" + biomeName, selected ? "§a§l已选择" : "§e点击选择"));
      }
   }

   private ItemStack makeCategoryItem(Material mat, String name, String... lores) {
      return this.makeItem(mat, name, lores);
   }

   private ItemStack makeItem(Material mat, String name, String... lores) {
      ItemStack item = new ItemStack(mat);
      ItemMeta meta = item.getItemMeta();
      meta.setDisplayName(name);
      List<String> loreList = new ArrayList<>();

      for (String l : lores) {
         loreList.add(l);
      }

      meta.setLore(loreList);
      item.setItemMeta(meta);
      return item;
   }

   public void handleClick(Player p, int slot, boolean isLeftClick) {
      if (slot == 10) {
         this.currentShownBiomes = WARM_BIOMES;
         this.currentShownIcon = Material.GRASS_BLOCK;
         this.buildGui(p);
      } else if (slot == 12) {
         this.currentShownBiomes = DRY_BIOMES;
         this.currentShownIcon = Material.SAND;
         this.buildGui(p);
      } else if (slot == 14) {
         this.currentShownBiomes = COLD_BIOMES;
         this.currentShownIcon = Material.SNOWBALL;
         this.buildGui(p);
      } else if (slot == 16) {
         this.currentShownBiomes = OCEAN_BIOMES;
         this.currentShownIcon = Material.PRISMARINE_SHARD;
         this.buildGui(p);
      } else if (slot == 19) {
         this.currentShownBiomes = SPECIAL_BIOMES;
         this.currentShownIcon = Material.MYCELIUM;
         this.buildGui(p);
      } else if (slot == 21) {
         this.currentShownBiomes = NETHER_BIOMES;
         this.currentShownIcon = Material.NETHERRACK;
         this.buildGui(p);
      } else if (slot == 23) {
         this.currentShownBiomes = END_BIOMES;
         this.currentShownIcon = Material.END_STONE;
         this.buildGui(p);
      } else {
         int[] slots = new int[]{28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43};

         for (int s : slots) {
            if (slot == s) {
               ItemStack item = this.MainGui.getItem(s);
               if (item != null && item.hasItemMeta()) {
                  List<String> lore = item.getItemMeta().getLore();
                  if (lore != null) {
                     for (String line : lore) {
                        if (line.contains("ID: ")) {
                           String biomeName = line.replace("§7ID: §8", "").replace("§7ID: ", "").trim();

                           try {
                              Biome.valueOf(biomeName);
                              this.selectedBiome = biomeName;
                              this.buildGui(p);
                           } catch (IllegalArgumentException var15) {
                              p.sendMessage("§c无效的群系: " + biomeName);
                           }

                           return;
                        }
                     }
                  }
               }

               return;
            }
         }

         if (slot == 48) {
            if (this.selectedBiome == null) {
               p.sendMessage("§c请先选择一个群系!");
            } else if (this.checkBiomePermission(p)) {
               if (!hasRequiredLevel(p)) {
                  sendLevelLocked(p);
               } else {
                  // Charge first, then apply, and refund if the terrain edit fails.
                  // The previous order applied the biome change and only afterwards
                  // checked the balance, returning early when the player could not
                  // pay - which made every biome change free for a broke player.
                  int cost = Main.JavaPlugin.getConfig().getInt("BiomeChange.SingleChunkCost");
                  if (!withdraw(p, cost)) {
                     return;
                  }

                  boolean success = this.applyBiomeToChunk(p, p.getWorld().getChunkAt(p.getLocation()), this.selectedBiome);
                  if (!success) {
                     deposit(p, cost);
                     return;
                  }

                  String reenterMsg = Variable.Lang_YML.getString("BiomeChangeReenter");
                  p.sendMessage(reenterMsg != null ? reenterMsg : "§a已将当前区块群系修改为: §e" + getCN(this.selectedBiome) + " §7(请重新进入世界查看效果)");
                  p.closeInventory();
               }
            }
         } else if (slot == 49) {
            if (this.selectedBiome == null) {
               p.sendMessage("§c请先选择一个群系!");
            } else if (this.checkBiomePermission(p)) {
               // The all-chunks option costs far more than the single-chunk one yet
               // was the only branch without the FeatureUnlock level gate.
               if (!hasRequiredLevel(p)) {
                  sendLevelLocked(p);
               } else {
                  int cost = Main.JavaPlugin.getConfig().getInt("BiomeChange.AllChunksCost");
                  if (!withdraw(p, cost)) {
                     return;
                  }

                  int radius = this.getActualRadius(p);
                  boolean success = this.applyBiomeToAllChunks(p, this.selectedBiome, radius);
                  if (!success) {
                     deposit(p, cost);
                     return;
                  }

                  String reenterMsg = Variable.Lang_YML.getString("BiomeChangeReenter");
                  p.sendMessage(reenterMsg != null ? reenterMsg : "§a已将边界内所有区块群系修改为: §e" + getCN(this.selectedBiome) + " §7(请重新进入世界查看效果)");
                  p.closeInventory();
               }
            }
         } else {
            if (slot == 50) {
               ManageGui3 gui = new ManageGui3(p);
               p.openInventory(gui.getInventory());
            }
         }
      }
   }

   /** True when the home is at or above {@code FeatureUnlock.BiomeChangeLevel}. */
   private static boolean hasRequiredLevel(Player p) {
      Home levelHome = HomeAPI.getHome(Util.getBaseHomeName(p.getWorld().getName()));
      int required = Main.JavaPlugin.getConfig().getInt("FeatureUnlock.BiomeChangeLevel", 3);
      return levelHome == null || levelHome.getLevel() >= required;
   }

   private static void sendLevelLocked(Player p) {
      int required = Main.JavaPlugin.getConfig().getInt("FeatureUnlock.BiomeChangeLevel", 3);
      String message = Variable.Lang_YML == null ? null : Variable.Lang_YML.getString("BiomeChangeLevelLocked");
      p.sendMessage(
         message != null
            ? message.replace("<Level>", String.valueOf(required))
            : "§8[§6错误庄园§8] §c群系修改需要家园等级 §e" + required + " §c以上"
      );
   }

   /** Takes {@code cost} up front. Returns false and messages the player when unaffordable. */
   private static boolean withdraw(Player p, int cost) {
      if (cost <= 0 || Variable.econ == null) {
         return true;
      }
      if (Variable.econ.getBalance(p) < cost) {
         p.sendMessage("§c金币不足! 需要 " + cost + " 金币");
         return false;
      }
      Variable.econ.withdrawPlayer(p, cost);
      return true;
   }

   /** Returns a charge that was taken for an operation which then failed. */
   private static void deposit(Player p, int cost) {
      if (cost > 0 && Variable.econ != null) {
         Variable.econ.depositPlayer(p, cost);
         p.sendMessage("§c群系修改失败，已退还 " + cost + " 金币。");
      }
   }

   private boolean checkBiomePermission(Player p) {      String worldName = p.getWorld().getName();
      String baseName = Util.getBaseHomeName(worldName);
      if (!Util.CheckIsHome(baseName)) {
         p.sendMessage("§c您当前不在任何家园世界中, 无法修改群系!");
         return false;
      } else if (!Util.CheckOwnerAndManagerAndOP(p, baseName)) {
         String msg = Variable.Lang_YML.getString("NoOwnerAndManagerPermission");
         p.sendMessage(msg != null ? msg : "§c您没有权限在此家园修改群系!");
         return false;
      } else {
         return true;
      }
   }

   private int getActualRadius(Player p) {
      int worldBoard = Main.JavaPlugin.getConfig().getInt("WorldBoard");
      int updateRadius = Main.JavaPlugin.getConfig().getInt("UpdateRadius");
      int level = 1;

      Home home = HomeAPI.getHome(Util.getBaseHomeName(p.getWorld().getName()));
      if (home != null) {
         level = home.getLevel();
      }

      return HomeTerrainPolicy.configuredBorderSize(
         level,
         Main.JavaPlugin.getConfig().getBoolean("HomeTerrain.Enabled", false),
         Main.JavaPlugin.getConfig().getIntegerList("HomeUpgrade.LevelSizes"),
         worldBoard,
         updateRadius,
         0
      ) / 2;
   }

   /** Vertical span written per column, matching the vanilla overworld build limits. */
   private static final int MIN_BIOME_Y = -64;
   private static final int MAX_BIOME_Y = 320;

   private boolean applyBiomeToChunk(Player p, Chunk chunk, String biomeName) {
      Biome biome = resolveBiome(p, biomeName);
      if (biome == null) {
         return false;
      }
      return writeChunk(p, chunk, biome);
   }

   private boolean applyBiomeToAllChunks(Player p, String biomeName, int radius) {
      World world = p.getWorld();
      Location center = world.getSpawnLocation();
      int cx = center.getBlockX() >> 4;
      int cz = center.getBlockZ() >> 4;
      int chunkRadius = radius / 16 + 1;

      Biome biome = resolveBiome(p, biomeName);
      if (biome == null) {
         return false;
      }

      boolean allWritten = true;
      for (int x = cx - chunkRadius; x <= cx + chunkRadius; x++) {
         for (int z = cz - chunkRadius; z <= cz + chunkRadius; z++) {
            Chunk chunk = world.getChunkAt(x, z);
            if (!chunk.isLoaded()) {
               chunk.load();
            }
            allWritten &= writeChunk(p, chunk, biome);
         }
      }
      return allWritten;
   }

   @SuppressWarnings("removal") // Biome.valueOf is scheduled for removal; no stable replacement on 1.21
   private static Biome resolveBiome(Player p, String biomeName) {
      if (biomeName == null || biomeName.trim().isEmpty()) {
         return null;
      }
      try {
         return Biome.valueOf(biomeName.trim().toUpperCase(java.util.Locale.ROOT));
      } catch (IllegalArgumentException unknown) {
         p.sendMessage("§c群系 '" + biomeName + "' 不存在。");
         com.Util.Diag.warnOnce("biome-unknown:" + biomeName, "Unknown biome requested from the biome menu: " + biomeName);
         return null;
      }
   }

   /**
    * Writes {@code biome} to every column of {@code chunk}.
    *
    * <p>The try/catch is outside the loop on purpose. It used to wrap the single
    * {@code setBiome} call, so a chunk that could not accept the biome executed and
    * swallowed 98304 exceptions (16 x 16 x 384) instead of one, and reported success.</p>
    */
   private static boolean writeChunk(Player p, Chunk chunk, Biome biome) {
      try {
         for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
               for (int y = MIN_BIOME_Y; y < MAX_BIOME_Y; y++) {
                  chunk.getBlock(x, y, z).setBiome(biome);
               }
            }
         }
         return true;
      } catch (RuntimeException failure) {
         p.sendMessage("§c群系修改失败: " + failure.getMessage());
         com.Util.Diag.warnOnce(
            "biome-write",
            "Could not write biome " + biome + " to chunk " + chunk.getX() + "," + chunk.getZ() + " in " + chunk.getWorld().getName(),
            failure
         );
         return false;
      }
   }

   public Inventory getInventory() {
      return this.MainGui;
   }


   public String getSelectedBiome() {
      return this.selectedBiome;
   }

   public void setSelectedBiome(String biome) {
      this.selectedBiome = biome;
   }

   static {
      BIOME_CN.put("PLAINS", "平原");
      BIOME_CN.put("SUNFLOWER_PLAINS", "向日葵平原");
      BIOME_CN.put("FOREST", "森林");
      BIOME_CN.put("FLOWER_FOREST", "繁花森林");
      BIOME_CN.put("BIRCH_FOREST", "白桦林");
      BIOME_CN.put("DARK_FOREST", "黑森林");
      BIOME_CN.put("SWAMP", "沼泽");
      BIOME_CN.put("MANGROVE_SWAMP", "红树林沼泽");
      BIOME_CN.put("MEADOW", "草甸");
      BIOME_CN.put("CHERRY_GROVE", "樱花园");
      BIOME_CN.put("DESERT", "沙漠");
      BIOME_CN.put("SAVANNA", "热带草原");
      BIOME_CN.put("BADLANDS", "恶地");
      BIOME_CN.put("ERODED_BADLANDS", "侵蚀恶地");
      BIOME_CN.put("WOODED_BADLANDS", "繁茂恶地");
      BIOME_CN.put("SAVANNA_PLATEAU", "热带草原高地");
      BIOME_CN.put("TAIGA", "针叶林");
      BIOME_CN.put("SNOWY_TAIGA", "积雪针叶林");
      BIOME_CN.put("ICE_SPIKES", "冰刺平原");
      BIOME_CN.put("FROZEN_OCEAN", "冻洋");
      BIOME_CN.put("SNOWY_PLAINS", "积雪平原");
      BIOME_CN.put("SNOWY_BEACH", "积雪海滩");
      BIOME_CN.put("FROZEN_RIVER", "冻河");
      BIOME_CN.put("GROVE", "雪林");
      BIOME_CN.put("SNOWY_SLOPES", "积雪山坡");
      BIOME_CN.put("JAGGED_PEAKS", "尖峭山峰");
      BIOME_CN.put("FROZEN_PEAKS", "冰封山峰");
      BIOME_CN.put("MUSHROOM_FIELDS", "蘑菇岛");
      BIOME_CN.put("JUNGLE", "丛林");
      BIOME_CN.put("BAMBOO_JUNGLE", "竹林");
      BIOME_CN.put("SPARSE_JUNGLE", "稀疏丛林");
      BIOME_CN.put("STONY_PEAKS", "裸岩山峰");
      BIOME_CN.put("STONY_SHORE", "石质海岸");
      BIOME_CN.put("DRIPSTONE_CAVES", "溶洞");
      BIOME_CN.put("LUSH_CAVES", "繁茂洞穴");
      BIOME_CN.put("DEEP_DARK", "幽暗深地");
      BIOME_CN.put("NETHER_WASTES", "下界荒地");
      BIOME_CN.put("SOUL_SAND_VALLEY", "灵魂沙峡谷");
      BIOME_CN.put("CRIMSON_FOREST", "绯红森林");
      BIOME_CN.put("WARPED_FOREST", "诡异森林");
      BIOME_CN.put("BASALT_DELTAS", "玄武岩三角洲");
      BIOME_CN.put("OCEAN", "海洋");
      BIOME_CN.put("DEEP_OCEAN", "深海");
      BIOME_CN.put("WARM_OCEAN", "温暖海洋");
      BIOME_CN.put("LUKEWARM_OCEAN", "温和海洋");
      BIOME_CN.put("COLD_OCEAN", "寒冷海洋");
      BIOME_CN.put("DEEP_LUKEWARM_OCEAN", "深温和海洋");
      BIOME_CN.put("DEEP_COLD_OCEAN", "深寒冷海洋");
      BIOME_CN.put("DEEP_FROZEN_OCEAN", "深冻洋");
      BIOME_CN.put("RIVER", "河流");
      BIOME_CN.put("BEACH", "海滩");
      BIOME_CN.put("THE_END", "末地");
      BIOME_CN.put("END_HIGHLANDS", "末地高地");
      BIOME_CN.put("END_MIDLANDS", "末地中地");
      BIOME_CN.put("END_BARRENS", "末地荒地");
      BIOME_CN.put("SMALL_END_ISLANDS", "末地小岛");
   }
}
