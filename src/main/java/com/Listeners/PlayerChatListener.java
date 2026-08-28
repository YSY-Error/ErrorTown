package com.Listeners;

import com.GUI.ServiceCostGui;
import com.ErrorTown.Main;
import com.ErrorTown.Variable;
import com.Util.Home;
import com.Util.HomeAPI;
import com.Util.CreateCostLedger;
import com.Util.Util;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.scheduler.BukkitRunnable;
import net.milkbowl.vault.economy.EconomyResponse;

public class PlayerChatListener implements Listener {
   @EventHandler(
      priority = EventPriority.HIGHEST,
      ignoreCancelled = false
   )
   public void onChat(AsyncPlayerChatEvent event) {
      final Player p = event.getPlayer();
      if (Variable.wait_chat_input.containsKey(p.getName())) {
         final String inputType = Variable.wait_chat_input.get(p.getName());
         String message = event.getMessage().trim();
         event.setCancelled(true);
         Variable.wait_chat_input.remove(p.getName());
         if (inputType.startsWith("flower:")) {
            final String targetName = inputType.substring("flower:".length());
                  if (message.equals("0")) {
               p.sendMessage("§8[§6错误庄园§8] §c已取消送花");
            } else {
               int amount;
               try {
                  amount = Integer.parseInt(message);
               } catch (NumberFormatException var13) {
                  p.sendMessage("§8[§6错误庄园§8] §c请输入有效的数字");
                  Variable.wait_chat_input.put(p.getName(), inputType);
                  return;
               }

               if (amount <= 0) {
                  p.sendMessage("§8[§6错误庄园§8] §c已取消送花");
               } else {
                  final int finalAmount = amount;
                  (new BukkitRunnable() {
                     public void run() {
                        if (p.isOnline()) {
                           Bukkit.dispatchCommand(p, "sh flower to " + targetName + " " + finalAmount);
                        }
                     }
                  }).runTask(Main.JavaPlugin);
               }
            }
         } else if (!inputType.equals("difficulty") && !inputType.startsWith("difficulty:")) {
            if (inputType.equals("setspawn_coords")) {
               if (message.equals("0")) {
                  Variable.pendingSetSpawnTarget.remove(p.getName());
                  p.sendMessage("§c已取消移动家园中心。");
               } else {
                  String[] parts = message.split("\\s+");
                  if (parts.length != 3) {
                     p.sendMessage("§c请输入三个坐标，格式: X Y Z");
                     Variable.wait_chat_input.put(p.getName(), inputType);
                  } else {
                     double z;
                     double x;
                     double y;
                     try {
                        x = Double.parseDouble(parts[0]);
                        y = Double.parseDouble(parts[1]);
                        z = Double.parseDouble(parts[2]);
                     } catch (NumberFormatException var14) {
                        p.sendMessage("§c坐标格式不正确，请输入数字，例如: 128 75 -64");
                        Variable.wait_chat_input.put(p.getName(), inputType);
                        return;
                     }

                     Variable.pendingSetSpawnTarget.put(p.getName(), x + "," + y + "," + z);
                     (new BukkitRunnable() {
                        public void run() {
                           if (p.isOnline()) {
                              p.openInventory(new ServiceCostGui(p, "setspawn_coords").getInventory());
                              p.sendMessage("§8[§6错误庄园§8] §a坐标已记录，接下来请选择支付方式。");
                           }
                        }
                     }).runTask(Main.JavaPlugin);
                  }
               }
            } else if (inputType.equals("title_input")) {
               if (message.equals("0")) {
                  p.sendMessage("§c已取消设置庄园名");
               } else {
                  final String title = message;
                  (new BukkitRunnable() {
                     public void run() {
                        if (p.isOnline()) {
                           String baseName = Util.getBaseHomeName(p.getWorld().getName());
                           if (!Util.CheckOwnerAndManagerAndOP(p, baseName)) {
                              p.sendMessage(Variable.Lang_YML.getString("NoOwnerAndManagerPermission"));
                           } else if (!Main.JavaPlugin.getConfig().getBoolean("HomeTitle.Enable")) {
                              p.sendMessage("§c庄园标题功能未开启");
                           } else {
                              int maxLen = Main.JavaPlugin.getConfig().getInt("HomeTitle.MaxTitleLength");
                              if (title.length() > maxLen) {
                                 p.sendMessage("§c庄园名长度不能超过 " + maxLen + " 个字符");
                              } else {
                                 Home home = HomeAPI.getHome(baseName);
                                 if (home == null) {
                                    p.sendMessage("§c未找到当前家园");
                                 } else {
                                    try {
                                       home.setTitle(title);
                                    } catch (IOException var5) {
                                       p.sendMessage("§c设置庄园名失败");
                                       return;
                                    }

                                    p.sendMessage("§a庄园名已设置为 §e" + title);
                                 }
                              }
                           }
                        }
                     }
                  }).runTask(Main.JavaPlugin);
               }
            } else if (inputType.equals("desc_input")) {
               if (message.equals("0")) {
                  p.sendMessage("§c已取消设置庄园描述");
               } else {
                  final String descMessage = message;
                  (new BukkitRunnable() {
                     public void run() {
                        if (p.isOnline()) {
                           String baseName = Util.getBaseHomeName(p.getWorld().getName());
                           if (!Util.CheckOwnerAndManagerAndOP(p, baseName)) {
                              p.sendMessage(Variable.Lang_YML.getString("NoOwnerAndManagerPermission"));
                           } else if (!Main.JavaPlugin.getConfig().getBoolean("HomeTitle.Enable")) {
                              p.sendMessage("§c庄园描述功能未开启");
                           } else {
                              int maxLines = Main.JavaPlugin.getConfig().getInt("HomeTitle.MaxDescriptionLines");
                              String normalized = descMessage.contains("|") ? descMessage : descMessage.replace(",", "|");
                              List<String> lines = new ArrayList<>(Arrays.asList(normalized.split("\\|")));
                              lines.removeIf(String::isEmpty);
                              if (lines.isEmpty()) {
                                 p.sendMessage("§c描述不能为空");
                              } else if (lines.size() > maxLines) {
                                 p.sendMessage("§c庄园描述最多 " + maxLines + " 行");
                              } else {
                                 Home home = HomeAPI.getHome(baseName);
                                 if (home == null) {
                                    p.sendMessage("§c未找到当前家园");
                                 } else {
                                    try {
                                       home.setDescription(lines);
                                    } catch (IOException var7) {
                                       p.sendMessage("§c设置庄园描述失败");
                                       return;
                                    }

                                    p.sendMessage("§a庄园描述已更新");
                                 }
                              }
                           }
                        }
                     }
                  }).runTask(Main.JavaPlugin);
               }
            } else if (inputType.startsWith("seed:")) {
               if (message.equals("0")) {
                  Variable.pendingCreateCostPaid.remove(p.getName());
                  Variable.pendingCreateSeed.remove(p.getName());
                  CreateCostLedger.refund(p.getName());
                  p.sendMessage("§c已取消自定义种子重置");
               } else {
                  String[] parts = inputType.split(":", 4);
                  final String worldName = parts.length > 1 ? parts[1] : "";
                  final int totalCost = parts.length > 2 ? tryParseInt(parts[2], 0) : 0;
                  final String seed = message;
                  final String chargedPlayerName = p.getName();
                  (new BukkitRunnable() {
                     public void run() {
                        if (p.isOnline()) {
                           if (totalCost > 0 && Variable.econ == null) {
                              p.sendMessage("§c经济系统不可用，家园未重置。");
                              return;
                           }
                           if (totalCost > 0) {
                              if (Variable.econ.getBalance(p) < totalCost) {
                                 p.sendMessage("§c金币不足! 需要 " + totalCost + " 金币");
                                 return;
                              }

                              EconomyResponse withdrawal = Variable.econ.withdrawPlayer(p, totalCost);
                              if (withdrawal == null || !withdrawal.transactionSuccess()) {
                                 p.sendMessage("§c扣费失败，家园未重置。");
                                 return;
                              }
                              CreateCostLedger.recordMoney(p.getName(), totalCost);
                           }

                           String baseName = worldName.replace(Variable.world_prefix, "");
                           World existing = Bukkit.getWorld(worldName);
                           World spawnWorld = Bukkit.getWorld(Main.JavaPlugin.getConfig().getString("Spawn", "world"));
                           if (spawnWorld == null) {
                              spawnWorld = (World)Bukkit.getWorlds().get(0);
                           }

                           World finalSpawn = spawnWorld;
                           if (existing != null) {
                              for (Player pl : existing.getPlayers()) {
                                 pl.teleport(finalSpawn.getSpawnLocation());
                                 pl.sendMessage("§c家园正在以自定义种子重置...");
                              }

                              Bukkit.unloadWorld(worldName, false);
                           }

                           p.sendMessage("§e正在异步删除旧世界文件，请稍候...");
                           Player finalPlayer = p;
                           Bukkit.getScheduler().runTaskAsynchronously(Main.JavaPlugin, () -> {
                              File worldDir = new File(worldName);
                              if (worldDir.exists()) {
                                 Util.deleteFile(worldDir);
                              }

                              Bukkit.getScheduler().runTask(Main.JavaPlugin, () -> {
                                 WorldCreator wc = new WorldCreator(worldName);

                                 try {
                                    wc.seed(Long.parseLong(seed));
                                 } catch (NumberFormatException var11) {
                                    wc.seed(seed.hashCode());
                                 }

                                 Variable.create_list_home.add(worldName);
                                 World newWorld;
                                 try {
                                    newWorld = Bukkit.createWorld(wc);
                                 } catch (RuntimeException failure) {
                                    Variable.create_list_home.remove(worldName);
                                    Variable.pendingCreateCostPaid.remove(chargedPlayerName);
                                    CreateCostLedger.refund(chargedPlayerName);
                                    Main.JavaPlugin.getLogger().log(
                                       java.util.logging.Level.WARNING,
                                       "Failed to recreate home world '" + worldName + "' with a custom seed",
                                       failure
                                    );
                                    if (finalPlayer.isOnline()) {
                                       finalPlayer.sendMessage("§c家园重置失败，已尝试退还费用。");
                                    }
                                    return;
                                 }
                                 if (!Variable.bungee) {
                                    File rf = new File(Variable.Tempf, baseName + ".yml");
                                    if (rf.exists()) {
                                       YamlConfiguration ry = YamlConfiguration.loadConfiguration(rf);
                                       ry.set("TpSet", null);

                                       try {
                                          ry.save(rf);
                                       } catch (IOException var10) {
                                          com.Util.Diag.warn("Could not clear TpSet after a home spawn reset; the old spawn point stays on disk", var10);
                                       }
                                    }
                                 }

                                 final World finalNewWorld = newWorld;
                                 if (finalNewWorld == null) {
                                    Variable.pendingCreateCostPaid.remove(chargedPlayerName);
                                    CreateCostLedger.refund(chargedPlayerName);
                                 } else {
                                    CreateCostLedger.settle(chargedPlayerName);
                                 }
                                 (new BukkitRunnable() {
                                    public void run() {
                                       if (finalPlayer.isOnline() && finalNewWorld != null) {
                                          finalPlayer.teleport(finalNewWorld.getSpawnLocation());
                                          finalPlayer.sendMessage("§a家园已用自定义种子重置完成，已传送至新出生点!");
                                       } else if (finalPlayer.isOnline() && finalNewWorld == null) {
                                          finalPlayer.sendMessage("§c家园重置失败，已尝试退还费用。");
                                       } else if (finalPlayer.isOnline()) {
                                          finalPlayer.sendMessage("§a家园已用自定义种子重置完成!");
                                       }
                                    }
                                 }).runTaskLater(Main.JavaPlugin, 40L);
                              });
                           });
                        }
                     }
                  }).runTask(Main.JavaPlugin);
               }
            } else if (inputType.startsWith("create_seed:")) {
               if (message.equals("0")) {
                  String tip = Variable.Lang_YML.getString("CreateCostSeedCancelled");
                  if (tip == null) {
                     tip = "§8[§6错误庄园§8] §c已取消自定义种子创建";
                  }

                  Variable.pendingCreateCostPaid.remove(p.getName());
                  Variable.pendingCreateSeed.remove(p.getName());
                  CreateCostLedger.refund(p.getName());
                  p.sendMessage(tip);
               } else {
                  final String createType = inputType.substring("create_seed:".length());
                  Variable.pendingCreateSeed.put(p.getName(), message);
                  Variable.pendingCreateCostPaid.put(p.getName(), Boolean.TRUE);
                  (new BukkitRunnable() {
                     public void run() {
                        if (p.isOnline()) {
                           Bukkit.dispatchCommand(p, "sh create " + createType);
                        }
                     }
                  }).runTask(Main.JavaPlugin);
               }
            }
         } else if (message.equals("0")) {
            p.sendMessage("§c已取消难度修改");
         } else {
            Difficulty chosen = null;
            if (message.equals("1")) {
               chosen = Difficulty.PEACEFUL;
            } else if (message.equals("2")) {
               chosen = Difficulty.EASY;
            } else if (message.equals("3")) {
               chosen = Difficulty.NORMAL;
            } else if (message.equals("4")) {
               chosen = Difficulty.HARD;
            }

            if (chosen == null) {
               p.sendMessage("§c无效输入，请输入 1/2/3/4");
               Variable.wait_chat_input.put(p.getName(), inputType);
            } else {
               final Difficulty finalChosen = chosen;
               (new BukkitRunnable() {
                  public void run() {
                     if (p.isOnline()) {
                        String baseName = Util.getBaseHomeName(p.getWorld().getName());
                        if (!Util.CheckOwnerAndManagerAndOP(p, baseName)) {
                           p.sendMessage(Variable.Lang_YML.getString("NoOwnerAndManagerPermission"));
                        } else {
                           int cost = Main.JavaPlugin.getConfig().getInt("DifficultyChange.Cost");
                           int points = Main.JavaPlugin.getConfig().getInt("DifficultyChange.Points", 0);
                           if (inputType.equals("difficulty:money")) {
                              if (!Util.chargeMoneyExact(p, cost, "设置家园难度")) {
                                 return;
                              }
                           } else if (inputType.equals("difficulty:points")) {
                              if (!Util.chargePointsExact(p, points, "设置家园难度")) {
                                 return;
                              }
                           } else if (!Util.chargeMoneyOrPoints(p, cost, points, "设置家园难度")) {
                              return;
                           }

                           p.getWorld().setDifficulty(finalChosen);
                           Home home = HomeAPI.getHome(baseName);
                           if (home != null) {
                              try {
                                 home.setRuleDifficulty(finalChosen);
                              } catch (IOException var6) {
                                 var6.printStackTrace();
                              }
                           }

                           p.sendMessage("§a已将家园难度设置为 §e" + finalChosen.name());
                        }
                     }
                  }
               }).runTask(Main.JavaPlugin);
            }
         }
      } else if (Main.JavaPlugin.getConfig().getBoolean("EnableChatPrefix")) {
         String temp = p.getWorld().getName().replace(Variable.world_prefix, "");
         if (Util.CheckIsHome(p.getWorld().getName().replace(Variable.world_prefix, ""))) {
            temp = Variable.Lang_YML.getString("PlaceHolders.WorldName");
            if (temp.contains("<PlayerName>")) {
               temp = temp.replace("<PlayerName>", p.getWorld().getName().replace(Variable.world_prefix, ""));
            }

            if (temp.contains("<WorldName>")) {
               temp = temp.replace("<WorldName>", p.getWorld().getName().replace(Variable.world_prefix, ""));
            }
         } else if (Util.getAliasName(p.getWorld().getName().replace(Variable.world_prefix, "")) != null) {
            temp = Util.getAliasName(p.getWorld().getName().replace(Variable.world_prefix, ""));
         } else if (!PlaceholderAPI.setPlaceholders(p, "%multiverse_world_alias%").equalsIgnoreCase("%multiverse_world_alias%")) {
            temp = PlaceholderAPI.setPlaceholders(p, "%multiverse_world_alias%");
         }

         event.setFormat(temp + event.getFormat());
      }
   }

   private static int tryParseInt(String s, int def) {
      try {
         return Integer.parseInt(s);
      } catch (Exception var3) {
         return def;
      }
   }
}
