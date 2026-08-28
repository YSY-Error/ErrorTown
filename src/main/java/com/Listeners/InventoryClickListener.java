package com.Listeners;

import com.GUI.BiomeGui;
import com.GUI.CheckGui;
import com.GUI.CreateCostGui;
import com.GUI.CreateGui;
import com.GUI.DenyGui;
import com.GUI.InviteGui;
import com.GUI.MainGui;
import com.GUI.ManageGui;
import com.GUI.ManageGui2;
import com.GUI.ManageGui3;
import com.GUI.OwnedHomesGui;
import com.GUI.RulesGui;
import com.GUI.ServiceCostGui;
import com.GUI.SetSpawnGui;
import com.GUI.TrustGui;
import com.GUI.UpgradeGui;
import com.GUI.VisitGui;
import com.ErrorTown.Main;
import com.ErrorTown.Variable;
import com.Util.Channel;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

public class InventoryClickListener implements Listener {
   /**
    * Runs late and cancels.
    *
    * <p>This was {@code priority = LOWEST, ignoreCancelled = true}, a combination that
    * cannot work: {@code LOWEST} executes first, so nothing has cancelled the event yet
    * and {@code ignoreCancelled} is a no-op. Worse, cancelling at {@code LOWEST} made
    * every later handler — including this plugin's own {@code ignoreCancelled = true}
    * listeners and any other plugin's anti-dupe checks — see an already-cancelled event.
    * {@code HIGH} lets other plugins inspect the click first and still leaves
    * {@code HIGHEST}/{@code MONITOR} free for loggers.</p>
    */
   @EventHandler(
      priority = EventPriority.HIGH
   )
   public void onOpen(InventoryClickEvent event) {
      if (event.getInventory().getHolder() != null) {
         if (event.getSlot() != -1 && event.getSlot() != 999) {
            Player p = (Player)event.getWhoClicked();
            boolean checkGuiInPlugins = false;
            String holder = "";
            if (event.getInventory().getHolder() instanceof CheckGui) {
               holder = "Check";
               checkGuiInPlugins = true;
            } else if (event.getInventory().getHolder() instanceof CreateGui) {
               holder = "Create";
               checkGuiInPlugins = true;
            } else if (event.getInventory().getHolder() instanceof DenyGui) {
               holder = "Deny";
               checkGuiInPlugins = true;
            } else if (event.getInventory().getHolder() instanceof InviteGui) {
               holder = "Invite";
               checkGuiInPlugins = true;
            } else if (event.getInventory().getHolder() instanceof MainGui) {
               holder = "Main";
               checkGuiInPlugins = true;
            } else if (event.getInventory().getHolder() instanceof OwnedHomesGui) {
               holder = "OwnedHomes";
               checkGuiInPlugins = true;
            } else if (event.getInventory().getHolder() instanceof ManageGui) {
               holder = "Manage";
               checkGuiInPlugins = true;
            } else if (event.getInventory().getHolder() instanceof ManageGui2) {
               holder = "Manage2";
               checkGuiInPlugins = true;
            } else if (event.getInventory().getHolder() instanceof ManageGui3) {
               holder = "Manage3";
               checkGuiInPlugins = true;
            } else {
               if (event.getInventory().getHolder() instanceof BiomeGui) {
                  BiomeGui biomeGui = (BiomeGui)event.getInventory().getHolder();
                  biomeGui.handleClick(p, event.getSlot(), event.getClick() == ClickType.LEFT);
                  event.setCancelled(true);
                  return;
               }

               if (event.getInventory().getHolder() instanceof RulesGui) {
                  holder = "Rules";
                  checkGuiInPlugins = true;
               } else if (event.getInventory().getHolder() instanceof UpgradeGui) {
                  holder = "Upgrade";
                  checkGuiInPlugins = true;
               } else if (event.getInventory().getHolder() instanceof TrustGui) {
                  holder = "Trust";
                  checkGuiInPlugins = true;
               } else if (event.getInventory().getHolder() instanceof VisitGui) {
                  holder = "Visit";
                  checkGuiInPlugins = true;
               } else if (event.getInventory().getHolder() instanceof CreateCostGui) {
                  holder = "CreateCost";
                  checkGuiInPlugins = true;
               } else if (event.getInventory().getHolder() instanceof ServiceCostGui) {
                  holder = "ServiceCost";
                  checkGuiInPlugins = true;
               } else if (event.getInventory().getHolder() instanceof SetSpawnGui) {
                  holder = "SetSpawn";
                  checkGuiInPlugins = true;
               }
            }

            if (checkGuiInPlugins) {
               event.setCancelled(true);
               ItemStack i = event.getCurrentItem();
               if (i != null && i.hasItemMeta() && i.getItemMeta().getDisplayName() != null) {
                  if (i.getItemMeta().getDisplayName().equalsIgnoreCase(Variable.GUI_YML.getString("Next"))) {
                     if (event.getClickedInventory().getHolder() instanceof DenyGui) {
                        ((DenyGui)event.getClickedInventory().getHolder()).OpenNextInventory(p);
                     }

                     if (event.getClickedInventory().getHolder() instanceof InviteGui) {
                        ((InviteGui)event.getClickedInventory().getHolder()).OpenNextInventory(p);
                     }

                     if (event.getClickedInventory().getHolder() instanceof TrustGui) {
                        ((TrustGui)event.getClickedInventory().getHolder()).OpenNextInventory(p);
                     }

                     if (event.getClickedInventory().getHolder() instanceof VisitGui) {
                        ((VisitGui)event.getClickedInventory().getHolder()).OpenNextInventory(p);
                     }
                  } else if (i.getItemMeta().getDisplayName().equalsIgnoreCase(Variable.GUI_YML.getString("Prev"))) {
                     if (event.getClickedInventory().getHolder() instanceof DenyGui) {
                        ((DenyGui)event.getClickedInventory().getHolder()).OpenPrevInventory(p);
                     }

                     if (event.getClickedInventory().getHolder() instanceof InviteGui) {
                        ((InviteGui)event.getClickedInventory().getHolder()).OpenPrevInventory(p);
                     }

                     if (event.getClickedInventory().getHolder() instanceof TrustGui) {
                        ((TrustGui)event.getClickedInventory().getHolder()).OpenPrevInventory(p);
                     }

                     if (event.getClickedInventory().getHolder() instanceof VisitGui) {
                        ((VisitGui)event.getClickedInventory().getHolder()).OpenPrevInventory(p);
                     }
                  }

                  String name = i.getItemMeta().getDisplayName();
                  if (name.contains(Variable.Lang_YML.getString("VisitGuiHomeSuffix")) && name.contains(Variable.Lang_YML.getString("VisitGuiHomePrefix"))) {
                     String targetName = name.replace(Variable.Lang_YML.getString("VisitGuiHomePrefix"), "")
                        .replace(Variable.Lang_YML.getString("VisitGuiHomeSuffix"), "");
                     if (event.getClick() == ClickType.RIGHT) {
                        p.closeInventory();
                        Variable.wait_chat_input.put(p.getName(), "flower:" + targetName);
                        p.sendMessage("§8[§6错误庄园§8] §e你正在给 §b" + targetName + " §e的家园送花");
                        p.sendMessage("§8[§6错误庄园§8] §7请在聊天框输入 §a送花数量§7 (输入 §c0§7 取消):");
                     } else {
                        Bukkit.dispatchCommand(p, "sh v " + targetName);
                     }
                  } else if (name.contains(Variable.Lang_YML.getString("CheckGuiHomePrefix"))
                     && name.contains(Variable.Lang_YML.getString("CheckGuiHomeSuffix"))) {
                     name = name.replace(Variable.Lang_YML.getString("CheckGuiHomePrefix"), "");
                     name = name.replace(Variable.Lang_YML.getString("CheckGuiHomeSuffix"), "");
                     Bukkit.dispatchCommand(p, "sh v " + name);
                  } else if (name.contains(Variable.Lang_YML.getString("TrustGuiPrefix"))) {
                     name = name.replace(Variable.Lang_YML.getString("TrustGuiPrefix"), "");
                     if (event.getClick() == ClickType.LEFT) {
                        Bukkit.dispatchCommand(p, "sh trust " + name);
                     } else {
                        Bukkit.dispatchCommand(p, "sh remove " + name);
                     }

                     scheduleGuiRefresh(p, "trust");
                  } else if (name.contains(Variable.Lang_YML.getString("InviteGuiPrefix"))) {
                     name = name.replace(Variable.Lang_YML.getString("InviteGuiPrefix"), "");
                     if (event.getClick() == ClickType.LEFT) {
                        Bukkit.dispatchCommand(p, "sh invite " + name);
                     } else {
                        Bukkit.dispatchCommand(p, "sh kick " + name);
                     }

                     scheduleGuiRefresh(p, "invite");
                  } else if (name.contains(Variable.Lang_YML.getString("DenyGuiPrefix"))) {
                     name = name.replace(Variable.Lang_YML.getString("DenyGuiPrefix"), "");
                     if (event.getClick() == ClickType.LEFT) {
                        Bukkit.dispatchCommand(p, "sh deny " + name);
                     } else {
                        Bukkit.dispatchCommand(p, "sh undeny " + name);
                     }

                     scheduleGuiRefresh(p, "deny");
                  } else if (holder.equals("Rules")) {
                     String dispName = i.getItemMeta().getDisplayName();
                     if (dispName.equals("§6爆炸保护")) {
                        Bukkit.dispatchCommand(p, "sh rule explosion");
                     } else if (dispName.equals("§6火焰蔓延")) {
                        Bukkit.dispatchCommand(p, "sh rule fire");
                     } else if (dispName.equals("§6自然刷怪")) {
                        Bukkit.dispatchCommand(p, "sh rule mob");
                     } else if (dispName.equals("§6敌对生物")) {
                        Bukkit.dispatchCommand(p, "sh rule hostile");
                     } else if (dispName.equals("§6被动生物")) {
                        Bukkit.dispatchCommand(p, "sh rule passive");
                     } else if (dispName.equals("§6刷怪笼生效")) {
                        Bukkit.dispatchCommand(p, "sh rule spawner");
                     } else if (dispName.equals("§6动物繁殖")) {
                        Bukkit.dispatchCommand(p, "sh rule breed");
                     } else {
                        if (!dispName.equals("§6刷怪数量上限")) {
                           if (dispName.equals("§8[§a返回§8]")) {
                              Bukkit.dispatchCommand(p, "sh open manage3");
                              return;
                           }

                           return;
                        }

                        if (event.getClick() == ClickType.RIGHT) {
                           Bukkit.dispatchCommand(p, "sh rule mobcap sub");
                        } else {
                           Bukkit.dispatchCommand(p, "sh rule mobcap add");
                        }
                     }

                     p.closeInventory();
                     scheduleGuiRefresh(p, "rules");
                  } else if (holder.equals("Upgrade")) {
                     String dispName = i.getItemMeta().getDisplayName();
                     if (dispName.equals("§6§l金币升级")) {
                        p.closeInventory();
                        Bukkit.dispatchCommand(p, "sh update money");
                     } else if (dispName.equals("§b§l点券升级")) {
                        p.closeInventory();
                        Bukkit.dispatchCommand(p, "sh update points");
                     } else if (dispName.equals("§a§l物品升级")) {
                        p.closeInventory();
                        Bukkit.dispatchCommand(p, "sh update items");
                     } else if (dispName.equals("§8[§a返回§8]")) {
                        Bukkit.dispatchCommand(p, "sh open manage");
                     }
                  } else if (holder.equals("CreateCost")) {
                     CreateCostGui ccg = (CreateCostGui)event.getInventory().getHolder();
                     String dispName = i.getItemMeta().getDisplayName();
                     if (dispName.equals("§6§l金币创建")) {
                        p.closeInventory();
                        Bukkit.dispatchCommand(p, "sh createcost money " + ccg.getCreateType() + " " + ccg.getSeedMode());
                     } else if (dispName.equals("§b§l点券创建")) {
                        p.closeInventory();
                        Bukkit.dispatchCommand(p, "sh createcost points " + ccg.getCreateType() + " " + ccg.getSeedMode());
                     } else if (dispName.equals("§8[§a返回§8]")) {
                        Bukkit.dispatchCommand(p, "sh open create");
                     }
                  } else if (holder.equals("OwnedHomes")) {
                     if (name.startsWith("§a")) {
                        Bukkit.dispatchCommand(p, "sh home " + name.substring(2));
                     } else {
                        Bukkit.dispatchCommand(p, "sh open main");
                     }
                  } else if (holder.equals("ServiceCost")) {
                     ServiceCostGui scg = (ServiceCostGui)event.getInventory().getHolder();
                     String dispName = i.getItemMeta().getDisplayName();
                     if (dispName.equals("§6§l金币支付")) {
                        p.closeInventory();
                        Bukkit.dispatchCommand(p, "sh servicecost money " + scg.getServiceKey());
                     } else if (dispName.equals("§b§l点券支付")) {
                        p.closeInventory();
                        Bukkit.dispatchCommand(p, "sh servicecost points " + scg.getServiceKey());
                     } else if (dispName.equals("§8[§a返回§8]")) {
                        Bukkit.dispatchCommand(p, scg.getBackCommandForClick());
                     }
                  } else if (holder.equals("SetSpawn")) {
                     String dispName = i.getItemMeta().getDisplayName();
                     if (dispName.equals("§a设到脚下")) {
                        p.closeInventory();
                        Bukkit.dispatchCommand(p, "sh open servicecost setspawn_here");
                     } else if (dispName.equals("§b输入坐标")) {
                        p.closeInventory();
                        Variable.wait_chat_input.put(p.getName(), "setspawn_coords");
                        p.sendMessage("§8[§6错误庄园§8] §e请输入目标坐标: §aX Y Z");
                        p.sendMessage("§8[§6错误庄园§8] §7例如: §e128 75 -64");
                        p.sendMessage("§8[§6错误庄园§8] §7输入 §c0 §7取消");
                     } else if (dispName.equals("§8[§a返回§8]")) {
                        Bukkit.dispatchCommand(p, "sh open manage2");
                     }
                  } else {
                     if (holder.equals("Create")) {
                        boolean createCostEnabled = Main.JavaPlugin.getConfig().getBoolean("CreateCost.Enable", false);
                        String createBtnNormal = Variable.GUI_YML.getString("Button7.CustomName");
                        String createBtnFlat = Variable.GUI_YML.getString("Button8.CustomName");
                        if (createBtnNormal != null && name.equals(createBtnNormal) && createCostEnabled) {
                           if (event.getClick() == ClickType.LEFT) {
                              p.closeInventory();
                              p.openInventory(new CreateCostGui(p, "1", "random").getInventory());
                           } else if (event.getClick() == ClickType.RIGHT) {
                              p.closeInventory();
                              p.openInventory(new CreateCostGui(p, "1", "custom").getInventory());
                           }

                           return;
                        }

                        if (createBtnFlat != null && name.equals(createBtnFlat) && createCostEnabled) {
                           p.closeInventory();
                           p.openInventory(new CreateCostGui(p, "2", "random").getInventory());
                           return;
                        }
                     }

                     String btnId = getBtnID(i, holder);
                     if (btnId != null) {
                        if (holder.equals("Main") && "Button1".equalsIgnoreCase(btnId) && event.getClick() == ClickType.LEFT) {
                           Bukkit.dispatchCommand(p, "sh open ownedhomes");
                        } else {
                           if (event.getClick() == ClickType.LEFT) {
                              String leftCmd = Variable.GUI_YML.getString(btnId + ".LeftInTo");
                              if (leftCmd != null && !leftCmd.equalsIgnoreCase("")) {
                                 handleGenericClick(p, holder, leftCmd, btnId);
                              }
                           } else if (event.getClick() == ClickType.RIGHT) {
                              String rightCmd = Variable.GUI_YML.getString(btnId + ".RightInTo");
                              if (rightCmd != null && !rightCmd.equalsIgnoreCase("")) {
                                 handleGenericClick(p, holder, rightCmd, btnId);
                              }
                           }
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private static void handleGenericClick(final Player p, final String holder, final String cmd, String btnId) {
      if (handleProxyServerCommand(p, cmd)) {
         p.closeInventory();
      } else if (handlePlayerChatCommand(p, cmd)) {
         p.closeInventory();
      } else {
         final boolean isMenuCmd = cmd.toLowerCase().contains("open ") || cmd.toLowerCase().contains("close");
         final boolean keepOpen = Variable.GUI_YML.getBoolean(btnId + ".KeepOpen", false);
         if (!isMenuCmd && !keepOpen) {
            p.closeInventory();
         }

         (new BukkitRunnable() {
            public void run() {
               if (p.isOnline()) {
                  Bukkit.dispatchCommand(p, cmd);
                  if (!isMenuCmd && keepOpen) {
                     p.sendMessage("§8[§6错误庄园§8] §e菜单刷新中, 请稍候...");
                     InventoryClickListener.scheduleGuiRefresh(p, holder.toLowerCase());
                  }
               }
            }
         }).runTask(Main.JavaPlugin);
      }
   }

   private static boolean handlePlayerChatCommand(final Player p, String rawCmd) {
      if (rawCmd == null) {
         return false;
      } else {
         String cmd = rawCmd.trim();
         if (cmd.startsWith("/")) {
            cmd = cmd.substring(1).trim();
         }

         if (!cmd.toLowerCase().startsWith("server ")) {
            return false;
         } else {
            final String finalCmd = cmd;
            (new BukkitRunnable() {
               public void run() {
                  if (p.isOnline()) {
                     p.chat("/" + finalCmd);
                  }
               }
            }).runTask(Main.JavaPlugin);
            return true;
         }
      }
   }

   private static void scheduleGuiRefresh(final Player p, String guiType) {
      String gt = guiType.toLowerCase();
      final Inventory inv;
      switch (gt) {
         case "manage":
            inv = new ManageGui(p).getInventory();
            break;
         case "manage2":
            inv = new ManageGui2(p).getInventory();
            break;
         case "manage3":
            inv = new ManageGui3(p).getInventory();
            break;
         case "setspawn":
            inv = new SetSpawnGui(p).getInventory();
            break;
         case "trust":
            inv = new TrustGui().getInventory();
            break;
         case "invite":
            inv = new InviteGui().getInventory();
            break;
         case "deny":
            inv = new DenyGui().getInventory();
            break;
         case "rules":
            inv = new RulesGui(p).getInventory();
            break;
         default:
            return;
      }

      (new BukkitRunnable() {
         public void run() {
            if (p.isOnline()) {
               p.openInventory(inv);
            }
         }
      }).runTaskLater(Main.JavaPlugin, 5L);
   }

   private static boolean handleProxyServerCommand(Player p, String rawCmd) {
      if (rawCmd == null) {
         return false;
      } else {
         String cmd = rawCmd.trim();
         if (cmd.startsWith("/")) {
            cmd = cmd.substring(1).trim();
         }

         if (!cmd.toLowerCase().startsWith("server ")) {
            return false;
         } else {
            String[] args = cmd.split("\\s+", 2);
            if (args.length >= 2 && !args[1].trim().isEmpty()) {
               Channel.sendPlayerToServer(p, args[1].trim());
               return true;
            } else {
               return false;
            }
         }
      }
   }

   public static String getBtnID(ItemStack i, String now) {
      String result = null;
      ConfigurationSection cs = Variable.GUI_YML.getConfigurationSection("");

      for (String temp : cs.getKeys(false)) {
         if (Variable.GUI_YML.getString(temp + ".InMenu") != null
            && Variable.GUI_YML.getString(temp + ".CustomName").equalsIgnoreCase(i.getItemMeta().getDisplayName())
            && now.equalsIgnoreCase(Variable.GUI_YML.getString(temp + ".InMenu"))) {
            result = temp;
            break;
         }
      }

      return result;
   }
}
