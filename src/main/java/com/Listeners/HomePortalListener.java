package com.Listeners;

import com.ErrorTown.Main;
import com.ErrorTown.Variable;
import com.Util.Util;
import com.Util.BukkitCompat;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Axis;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.WorldCreator;
import org.bukkit.World.Environment;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Orientable;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPortalEnterEvent;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.BoundingBox;

public class HomePortalListener implements Listener {
   private static final int PORTAL_COOLDOWN_TICKS = 60;
   private static final int SURVIVAL_PORTAL_WARMUP_TICKS = 80;
   private static final int INSTANT_PORTAL_WARMUP_TICKS = 1;
   private static final Set<UUID> ACTIVE_PORTAL_PLAYERS = new HashSet<>();
   private static final Map<String, Location> PORTAL_LINKS = new HashMap<>();

   private static boolean useVanillaPortalResolver() {
      return Main.JavaPlugin.getConfig().getBoolean("HomeNetherPortal.UseVanillaResolver", true);
   }

   private boolean isNetherUnlocked(Player p, String baseName) {
      String suffix = Main.JavaPlugin.getConfig().getString("HomeNetherSuffix");
      if (suffix == null || suffix.isEmpty()) {
         suffix = "_nether";
      }

      String netherName = Variable.world_prefix + baseName + suffix;
      if (Bukkit.getWorld(netherName) != null) {
         return true;
      } else {
         File netherFolder = new File(netherName);
         if (!netherFolder.exists()) {
            netherFolder = new File(Main.JavaPlugin.getConfig().getString("server_file_world", "") + netherName);
         }

         if (netherFolder.exists() && netherFolder.isDirectory()) {
            return true;
         } else {
            if (!Variable.bungee) {
               File f = new File(Variable.Tempf, baseName + ".yml");
               if (f.exists()) {
                  YamlConfiguration yml = YamlConfiguration.loadConfiguration(f);
                  return yml.getBoolean("NetherUnlocked", false);
               }
            }

            return false;
         }
      }
   }

   @EventHandler(
      priority = EventPriority.HIGH,
      ignoreCancelled = true
   )
   public void onPortal(PlayerPortalEvent event) {
      if (!Main.JavaPlugin.getConfig().getBoolean("HomeTravel.AllowNetherEnd", false)
         && isHomePortalWorld(event.getFrom().getWorld())) {
         event.setCancelled(true);
         event.getPlayer().sendMessage("§8[§6错误庄园§8] §c家园世界禁止前往下界和末地。");
         return;
      }
      if (Main.JavaPlugin.getConfig().getBoolean("EnableHomeNether")) {
         if (event.getCause() == TeleportCause.NETHER_PORTAL) {
            if (event.getFrom() != null && event.getFrom().getWorld() != null) {
               Player p = event.getPlayer();
               if (!useVanillaPortalResolver() && isHomePortalWorld(p.getWorld()) && ACTIVE_PORTAL_PLAYERS.contains(p.getUniqueId())) {
                  event.setCancelled(true);
               } else if (p.isInsideVehicle() && isBoatEntity(p.getVehicle())) {
                  event.setCancelled(true);
                  teleportBoatAndPassengersThroughPortal(p.getVehicle(), event.getFrom(), TeleportCause.NETHER_PORTAL);
                  grantPortalInvincibility(p);
               } else {
                  Location from = event.getFrom();
                  World fromWorld = from.getWorld();
                  String name = fromWorld.getName().replace(Variable.world_prefix, "");
                  String suffix = Main.JavaPlugin.getConfig().getString("HomeNetherSuffix");
                  if (suffix == null || suffix.isEmpty()) {
                     suffix = "_nether";
                  }

                  double scale = Main.JavaPlugin.getConfig().getInt("HomeNetherScale");
                  if (scale <= 0.0) {
                     scale = 1.0;
                  }

                  if (Util.CheckIsHome(name) && !name.endsWith(suffix)) {
                     String baseName = Util.getBaseHomeName(fromWorld.getName());
                     if (!this.isNetherUnlocked(p, baseName)) {
                        event.setCancelled(true);
                        String msg = Variable.Lang_YML.getString("NetherNotUnlocked");
                        p.sendMessage(msg != null ? msg : "§c您的家园地狱尚未开启, 请使用 /st createNether 创建");
                     } else {
                        String netherRaw = Variable.world_prefix + name + suffix;
                        World nether = getOrCreateWorld(netherRaw, Environment.NETHER);
                        if (nether == null) {
                           event.setCancelled(true);
                        } else {
                           applyCorrespondingBorder(fromWorld.getWorldBorder(), nether.getWorldBorder(), scale, false);
                           if (useVanillaPortalResolver()) {
                              Location linkedTarget = resolveLinkedPortalTarget(from);
                              if (linkedTarget != null) {
                                 event.setTo(linkedTarget);
                                 event.setCanCreatePortal(false);
                                 event.setSearchRadius(getForcedLinkedPortalSearchRadius());
                              } else {
                                 event.setTo(buildScaledPortalTarget(from, nether, scale, 5.0, 120.0));
                                 event.setCanCreatePortal(true);
                                 setPortalSearchOptions(event);
                              }
                           } else {
                              Location to = resolveExistingPortalOrScaledTarget(from);
                              if (to == null) {
                                 to = resolveSafeTarget(buildScaledPortalTarget(from, nether, scale, 5.0, 120.0));
                              }

                              event.setCancelled(true);
                              teleportPlayerThroughHomePortal(p, to);
                              if (Variable.Lang_YML.contains("HomeNetherEnter")) {
                                 p.sendMessage(Variable.Lang_YML.getString("HomeNetherEnter"));
                              }

                              grantPortalInvincibility(p);
                           }
                        }
                     }
                  } else {
                     if (name.endsWith(suffix)) {
                        String base = name.substring(0, name.length() - suffix.length());
                        if (!Util.CheckIsHome(base)) {
                           return;
                        }

                        String overRaw = Variable.world_prefix + base;
                        World over = Bukkit.getWorld(overRaw);
                        if (over == null) {
                           over = getOrCreateWorld(overRaw, Environment.NORMAL);
                        }

                        if (over == null) {
                           event.setCancelled(true);
                           return;
                        }

                        if (useVanillaPortalResolver()) {
                           Location linkedTarget = resolveLinkedPortalTarget(from);
                           if (linkedTarget != null) {
                              event.setTo(linkedTarget);
                              event.setCanCreatePortal(false);
                              event.setSearchRadius(getForcedLinkedPortalSearchRadius());
                           } else {
                              event.setTo(clampToWorldBorder(over, buildScaledPortalTarget(from, over, scale, 5.0, 250.0)));
                              event.setCanCreatePortal(true);
                              setPortalSearchOptions(event);
                           }

                           return;
                        }

                        Location tox = resolveExistingPortalOrScaledTarget(from);
                        if (tox == null) {
                           tox = resolveSafeTarget(clampToWorldBorder(over, buildScaledPortalTarget(from, over, scale, 5.0, 250.0)));
                        }

                        event.setCancelled(true);
                        teleportPlayerThroughHomePortal(p, tox);
                        if (Variable.Lang_YML.contains("HomeNetherBack")) {
                           p.sendMessage(Variable.Lang_YML.getString("HomeNetherBack"));
                        }

                        grantPortalInvincibility(p);
                     }
                  }
               }
            }
         }
      }
   }

   private static void teleportPlayerThroughHomePortal(final Player player, Location target) {
      if (player != null && target != null && target.getWorld() != null) {
         final Location safeTarget = resolveSafeTarget(target);
         (new BukkitRunnable() {
            public void run() {
               if (player.isOnline()) {
                  player.setPortalCooldown(PORTAL_COOLDOWN_TICKS);
                  player.setFallDistance(0.0F);
                  player.teleport(safeTarget, TeleportCause.NETHER_PORTAL);
               }
            }
         }).runTask(Main.JavaPlugin);
      }
   }

   @EventHandler(
      priority = EventPriority.HIGH,
      ignoreCancelled = false
   )
   public void onNetherDeath(PlayerDeathEvent event) {
      if (Main.JavaPlugin.getConfig().getBoolean("EnableHomeNether")) {
         Player p = event.getEntity();
         String worldName = p.getWorld().getName();
         String stripped = worldName.replace(Variable.world_prefix, "");
         String suffix = Main.JavaPlugin.getConfig().getString("HomeNetherSuffix");
         if (suffix == null || suffix.isEmpty()) {
            suffix = "_nether";
         }

         if (stripped.endsWith(suffix)) {
            String base = stripped.substring(0, stripped.length() - suffix.length());
            if (Util.CheckIsHome(base)) {
               event.getDrops().clear();
               event.setDroppedExp(0);
               event.setKeepInventory(true);
               event.setKeepLevel(true);
            }
         }
      }
   }

   private static void grantPortalInvincibility(final Player p) {
      (new BukkitRunnable() {
         public void run() {
            if (p.isOnline()) {
               BukkitCompat.addResistance(p, 60, 4, false, false, false);
            }
         }
      }).runTaskLater(Main.JavaPlugin, 5L);
   }

   private static void applyCorrespondingBorder(WorldBorder source, WorldBorder target, double scale, boolean reverse) {
      try {
         double cx = source.getCenter().getX();
         double cz = source.getCenter().getZ();
         double size = source.getSize();
         Location currentCenter = target.getCenter();
         if (Math.abs(currentCenter.getX() - cx) > 0.01 || Math.abs(currentCenter.getZ() - cz) > 0.01) {
            target.setCenter(cx, cz);
         }

         if (Math.abs(target.getSize() - size) > 0.01) {
            target.setSize(size);
         }
      } catch (Throwable failure) {
         com.Util.Diag.warnOnce("portal-border-size", "Could not resize the nether world border", failure);
      }
   }

   private static double clamp(double value, double min, double max) {
      if (value < min) {
         return min;
      } else {
         return value > max ? max : value;
      }
   }

   private static World getOrCreateWorld(String worldName, Environment env) {
      World w = Bukkit.getWorld(worldName);
      if (w != null) {
         return w;
      } else {
         try {
            WorldCreator creator = new WorldCreator(worldName);
            creator.environment(env);
            w = Bukkit.createWorld(creator);
         } catch (Throwable failure) {
            Bukkit.getConsoleSender().sendMessage("§c[错误庄园] 创建世界失败: " + worldName + " -> " + failure.getMessage());
         }

         return w;
      }
   }

   private static Location computePortalTarget(Location from) {
      if (from != null && from.getWorld() != null) {
         World fromWorld = from.getWorld();
         String name = fromWorld.getName().replace(Variable.world_prefix, "");
         String suffix = Main.JavaPlugin.getConfig().getString("HomeNetherSuffix");
         if (suffix == null || suffix.isEmpty()) {
            suffix = "_nether";
         }

         double scale = Main.JavaPlugin.getConfig().getInt("HomeNetherScale");
         if (scale <= 0.0) {
            scale = 1.0;
         }

         if (Util.CheckIsHome(name) && !name.endsWith(suffix)) {
            String netherRaw = Variable.world_prefix + name + suffix;
            World nether = getOrCreateWorld(netherRaw, Environment.NETHER);
            if (nether == null) {
               return null;
            } else {
               applyCorrespondingBorder(fromWorld.getWorldBorder(), nether.getWorldBorder(), scale, false);
               return buildScaledPortalTarget(from, nether, scale, 5.0, 120.0);
            }
         } else if (name.endsWith(suffix)) {
            String base = name.substring(0, name.length() - suffix.length());
            if (!Util.CheckIsHome(base)) {
               return null;
            } else {
               World over = Bukkit.getWorld(Variable.world_prefix + base);
               if (over == null) {
                  over = getOrCreateWorld(Variable.world_prefix + base, Environment.NORMAL);
               }

               return over == null ? null : clampToWorldBorder(over, buildScaledPortalTarget(from, over, scale, 5.0, 250.0));
            }
         } else {
            return null;
         }
      } else {
         return null;
      }
   }

   private static int getPortalSearchRadius() {
      return Math.max(1, Main.JavaPlugin.getConfig().getInt("HomeNetherPortal.SearchRadius", 16));
   }

   private static int getPortalCreationRadius() {
      return Math.max(1, Main.JavaPlugin.getConfig().getInt("HomeNetherPortal.CreationRadius", 16));
   }

   private static int getForcedLinkedPortalSearchRadius() {
      return 1;
   }

   private static int getPortalVerticalSearchRadius(World world) {
      return world == null ? 32 : Math.max(32, world.getMaxHeight() - world.getMinHeight());
   }

   private static Location resolveExistingPortalOrScaledTarget(Location from) {
      Location sourcePortal = findNearestPortalCenter(from, 4, 12);
      if (sourcePortal != null) {
         Location cached = getLinkedPortal(sourcePortal);
         if (isValidPortalLocation(cached)) {
            return cached.clone();
         }

         String staleKey = toPortalKey(sourcePortal);
         if (staleKey != null) {
            PORTAL_LINKS.remove(staleKey);
         }
      }

      Location scaledTarget = computePortalTarget(from);
      if (scaledTarget == null) {
         return null;
      } else {
         Location existingPortal = findNearestPortalCenter(scaledTarget, getPortalSearchRadius(), getPortalVerticalSearchRadius(scaledTarget.getWorld()));
         if (existingPortal != null) {
            if (sourcePortal != null) {
               linkPortals(sourcePortal, existingPortal);
            }

            return existingPortal;
         } else {
            Location createdPortal = createPortalAtScaledTarget(scaledTarget, sourcePortal);
            if (createdPortal != null) {
               if (sourcePortal != null) {
                  linkPortals(sourcePortal, createdPortal);
               }

               return createdPortal;
            } else {
               return scaledTarget;
            }
         }
      }
   }

   private static Location resolveLinkedPortalTarget(Location from) {
      Location sourcePortal = findNearestPortalCenter(from, 4, 12);
      if (sourcePortal == null) {
         return null;
      } else {
         Location cached = getLinkedPortal(sourcePortal);
         if (isValidPortalLocation(cached)) {
            return cached.clone();
         } else {
            if (cached != null) {
               String staleKey = toPortalKey(sourcePortal);
               if (staleKey != null) {
                  PORTAL_LINKS.remove(staleKey);
               }
            }

            return null;
         }
      }
   }

   private static String toPortalKey(Location location) {
      return location != null && location.getWorld() != null
         ? location.getWorld().getName() + ":" + location.getBlockX() + ":" + location.getBlockY() + ":" + location.getBlockZ()
         : null;
   }

   private static void linkPortals(Location first, Location second) {
      if (first != null && second != null && first.getWorld() != null && second.getWorld() != null) {
         String firstKey = toPortalKey(first);
         String secondKey = toPortalKey(second);
         if (firstKey != null && secondKey != null) {
            PORTAL_LINKS.put(firstKey, second.clone());
            PORTAL_LINKS.put(secondKey, first.clone());
         }
      }
   }

   private static Location getLinkedPortal(Location source) {
      String key = toPortalKey(source);
      return key == null ? null : PORTAL_LINKS.get(key);
   }

   @EventHandler(
      priority = EventPriority.HIGH,
      ignoreCancelled = true
   )
   public void onEntityPortal(EntityPortalEvent event) {
      if (Main.JavaPlugin.getConfig().getBoolean("EnableHomeNether")) {
         if (event.getFrom() != null && event.getFrom().getWorld() != null) {
            Entity entity = event.getEntity();
            if (!(entity instanceof Player)) {
               Location linkedTarget = useVanillaPortalResolver() ? resolveLinkedPortalTarget(event.getFrom()) : null;
               Location target = useVanillaPortalResolver()
                  ? (linkedTarget != null ? linkedTarget : computePortalTarget(event.getFrom()))
                  : resolveExistingPortalOrScaledTarget(event.getFrom());
               if (target != null) {
                  if (isBoatEntity(entity)) {
                     event.setCancelled(true);
                     teleportBoatAndPassengersThroughPortal(entity, event.getFrom(), TeleportCause.NETHER_PORTAL);
                  } else if (entity instanceof Item) {
                     if (!useVanillaPortalResolver()) {
                        entity.setPortalCooldown(PORTAL_COOLDOWN_TICKS);
                     }

                     event.setTo(target);
                     event.setSearchRadius(linkedTarget != null ? getForcedLinkedPortalSearchRadius() : getPortalSearchRadius());
                  } else if (!(entity instanceof LivingEntity)) {
                     event.setCancelled(true);
                  } else {
                     event.setTo(target);
                     event.setSearchRadius(linkedTarget != null ? getForcedLinkedPortalSearchRadius() : getPortalSearchRadius());
                  }
               }
            }
         }
      }
   }

   @EventHandler(
      priority = EventPriority.HIGH,
      ignoreCancelled = true
   )
   public void onBoatPortalEnter(EntityPortalEnterEvent event) {
      if (Main.JavaPlugin.getConfig().getBoolean("EnableHomeNether")) {
         if (event.getEntity() != null && event.getLocation() != null) {
            Entity vehicle = event.getEntity();
            if (isBoatEntity(vehicle)) {
               if (isPortalBlock(event.getLocation())) {
                  vehicle.setPortalCooldown(PORTAL_COOLDOWN_TICKS);
                  teleportBoatAndPassengersThroughPortal(vehicle, event.getLocation(), TeleportCause.NETHER_PORTAL);
               }
            }
         }
      }
   }

   @EventHandler(
      priority = EventPriority.HIGH,
      ignoreCancelled = true
   )
   public void onPlayerPortalEnter(EntityPortalEnterEvent event) {
      if (Main.JavaPlugin.getConfig().getBoolean("EnableHomeNether")) {
         if (!useVanillaPortalResolver()) {
            if (event.getEntity() instanceof Player) {
               final Player player = (Player)event.getEntity();
               if (event.getLocation() != null && isPortalBlock(event.getLocation())) {
                  if (isHomePortalWorld(player.getWorld())) {
                     if (player.getPortalCooldown() <= 0) {
                        if (ACTIVE_PORTAL_PLAYERS.add(player.getUniqueId())) {
                           int warmupTicks = getPlayerPortalWarmupTicks(player);
                           player.setPortalCooldown(Math.max(PORTAL_COOLDOWN_TICKS, warmupTicks + 5));
                           player.setFallDistance(0.0F);
                           (new BukkitRunnable() {
                                 public void run() {
                                    try {
                                       if (!player.isOnline()) {
                                          return;
                                       }

                                       if (HomePortalListener.isEntityTouchingPortal(player)) {
                                          if (player.isInsideVehicle() && HomePortalListener.isBoatEntity(player.getVehicle())) {
                                             HomePortalListener.teleportBoatAndPassengersThroughPortal(
                                                player.getVehicle(), player.getLocation(), TeleportCause.NETHER_PORTAL
                                             );
                                          } else {
                                             Location target = HomePortalListener.resolveExistingPortalOrScaledTarget(player.getLocation());
                                             if (target == null) {
                                                target = HomePortalListener.computePortalTarget(player.getLocation());
                                             }

                                             if (target != null && target.getWorld() != null) {
                                                HomePortalListener.teleportPlayerThroughHomePortal(player, target);
                                             } else {
                                                player.setPortalCooldown(0);
                                             }
                                          }

                                          HomePortalListener.grantPortalInvincibility(player);
                                          return;
                                       }

                                       player.setPortalCooldown(0);
                                    } finally {
                                       HomePortalListener.ACTIVE_PORTAL_PLAYERS.remove(player.getUniqueId());
                                    }
                                 }
                              })
                              .runTaskLater(Main.JavaPlugin, warmupTicks);
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private static boolean isBoatEntity(Entity entity) {
      String typeName = entity.getType().name();
      return typeName.endsWith("BOAT");
   }

   private static boolean isHomePortalWorld(World world) {
      if (world == null) {
         return false;
      } else {
         String name = world.getName().replace(Variable.world_prefix, "");
         String suffix = Main.JavaPlugin.getConfig().getString("HomeNetherSuffix");
         if (suffix == null || suffix.isEmpty()) {
            suffix = "_nether";
         }

         return Util.CheckIsHome(name) || name.endsWith(suffix) && Util.CheckIsHome(name.substring(0, name.length() - suffix.length()));
      }
   }

   private static int getPlayerPortalWarmupTicks(Player player) {
      if (player == null) {
         return SURVIVAL_PORTAL_WARMUP_TICKS;
      } else {
         GameMode gameMode = player.getGameMode();
         return gameMode != GameMode.CREATIVE && gameMode != GameMode.SPECTATOR
            ? SURVIVAL_PORTAL_WARMUP_TICKS
            : INSTANT_PORTAL_WARMUP_TICKS;
      }
   }

   private static void teleportBoatAndPassengersThroughPortal(final Entity vehicle, Location sourcePortalLocation, final TeleportCause cause) {
      if (vehicle != null && sourcePortalLocation != null) {
         Location target = resolveExistingPortalOrScaledTarget(sourcePortalLocation);
         if (target == null) {
            target = computePortalTarget(sourcePortalLocation);
         }

         if (target != null && target.getWorld() != null) {
            final Location vehicleTarget = resolveSafeTarget(target.clone());
            final List<Entity> passengers = new ArrayList<>(vehicle.getPassengers());
            (new BukkitRunnable() {
               public void run() {
                  if (vehicle != null && vehicle.isValid()) {
                     for (Entity passenger : new ArrayList<>(passengers)) {
                        if (passenger != null && passenger.isValid() && passenger.isInsideVehicle()) {
                           passenger.leaveVehicle();
                        }
                     }

                     vehicle.setPortalCooldown(PORTAL_COOLDOWN_TICKS);
                     vehicle.teleport(vehicleTarget, cause);
                     final Entity finalVehicle = vehicle;
                     final List<Entity> finalPassengers = new ArrayList<>(passengers);
                     (new BukkitRunnable() {
                        public void run() {
                           if (finalVehicle != null && finalVehicle.isValid()) {
                              for (Entity passenger : finalPassengers) {
                                 if (passenger != null && passenger.isValid()) {
                                    passenger.setPortalCooldown(PORTAL_COOLDOWN_TICKS);
                                    passenger.setFallDistance(0.0F);
                                    passenger.teleport(vehicleTarget, cause);

                                    try {
                                       finalVehicle.addPassenger(passenger);
                                    } catch (Throwable var4x) {
                                       com.Util.Diag.warnOnce("portal-remount", "Could not re-seat a passenger after a portal teleport", var4x);
                                    }
                                 }
                              }
                           }
                        }
                     }).runTaskLater(Main.JavaPlugin, 1L);
                  }
               }
            }).runTask(Main.JavaPlugin);
         }
      }
   }

   private static boolean isPortalBlock(Location location) {
      return location != null && isNetherPortalMaterial(location.getBlock().getType());
   }

   private static boolean isNetherPortalMaterial(Material material) {
      return material != null && material.name().equals("NETHER_PORTAL");
   }

   private static Axis getPreferredPortalAxis(Location sourcePortal) {
      if (sourcePortal != null && sourcePortal.getWorld() != null) {
         BlockData data = sourcePortal.getBlock().getBlockData();
         if (data instanceof Orientable) {
            Axis axis = ((Orientable)data).getAxis();
            if (axis == Axis.X || axis == Axis.Z) {
               return axis;
            }
         }
      }

      return Axis.Z;
   }

   private static int clampInt(int value, int min, int max) {
      if (value < min) {
         return min;
      } else {
         return value > max ? max : value;
      }
   }

   private static Location createPortalAtScaledTarget(Location scaledTarget, Location sourcePortal) {
      if (scaledTarget != null && scaledTarget.getWorld() != null) {
         World world = scaledTarget.getWorld();
         Axis axis = getPreferredPortalAxis(sourcePortal);
         int baseX = scaledTarget.getBlockX();
         int baseZ = scaledTarget.getBlockZ();
         int portalBottomY = resolvePortalBuildY(scaledTarget);
         portalBottomY = clampInt(portalBottomY, world.getMinHeight() + 1, world.getMaxHeight() - 4);
         clearPortalWalkway(world, baseX, portalBottomY, baseZ, axis);
         buildPortalFrame(world, baseX, portalBottomY, baseZ, axis);
         Location portalLocation = getPortalTeleportLocation(world, baseX, portalBottomY, baseZ, axis, scaledTarget.getYaw(), scaledTarget.getPitch());
         return isValidPortalLocation(portalLocation) ? portalLocation : null;
      } else {
         return null;
      }
   }

   private static int resolvePortalBuildY(Location target) {
      World world = target.getWorld();
      if (world == null) {
         return target.getBlockY();
      } else {
         int minY = world.getMinHeight() + 1;
         int maxY = world.getMaxHeight() - 4;
         int x = target.getBlockX();
         int z = target.getBlockZ();
         if (world.getEnvironment() == Environment.NETHER) {
            return clampInt(target.getBlockY(), minY, Math.min(120, maxY));
         } else {
            int surfaceY;
            try {
               surfaceY = world.getHighestBlockAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES).getY() + 1;
            } catch (Throwable failure) {
               surfaceY = target.getBlockY();
            }

            return clampInt(surfaceY, minY, maxY);
         }
      }
   }

   private static void clearPortalWalkway(World world, int baseX, int baseY, int baseZ, Axis axis) {
      if (world != null) {
         if (axis == Axis.Z) {
            for (int x = baseX - 1; x <= baseX + 2; x++) {
               for (int y = baseY; y <= baseY + 2; y++) {
                  setBlockType(world, x, y, baseZ - 1, Material.AIR);
                  setBlockType(world, x, y, baseZ + 1, Material.AIR);
               }
            }
         } else {
            for (int z = baseZ - 1; z <= baseZ + 2; z++) {
               for (int y = baseY; y <= baseY + 2; y++) {
                  setBlockType(world, baseX - 1, y, z, Material.AIR);
                  setBlockType(world, baseX + 1, y, z, Material.AIR);
               }
            }
         }
      }
   }

   private static void buildPortalFrame(World world, int baseX, int baseY, int baseZ, Axis axis) {
      if (world != null) {
         BlockData portalData = Bukkit.createBlockData(Material.NETHER_PORTAL);
         if (portalData instanceof Orientable) {
            ((Orientable)portalData).setAxis(axis);
         }

         if (axis == Axis.Z) {
            for (int x = baseX - 1; x <= baseX + 2; x++) {
               setBlockType(world, x, baseY - 1, baseZ, Material.OBSIDIAN);
               setBlockType(world, x, baseY + 3, baseZ, Material.OBSIDIAN);
            }

            for (int y = baseY; y <= baseY + 2; y++) {
               setBlockType(world, baseX - 1, y, baseZ, Material.OBSIDIAN);
               setBlockType(world, baseX + 2, y, baseZ, Material.OBSIDIAN);
               setPortalBlock(world, baseX, y, baseZ, portalData);
               setPortalBlock(world, baseX + 1, y, baseZ, portalData);
            }
         } else {
            for (int z = baseZ - 1; z <= baseZ + 2; z++) {
               setBlockType(world, baseX, baseY - 1, z, Material.OBSIDIAN);
               setBlockType(world, baseX, baseY + 3, z, Material.OBSIDIAN);
            }

            for (int y = baseY; y <= baseY + 2; y++) {
               setBlockType(world, baseX, y, baseZ - 1, Material.OBSIDIAN);
               setBlockType(world, baseX, y, baseZ + 2, Material.OBSIDIAN);
               setPortalBlock(world, baseX, y, baseZ, portalData);
               setPortalBlock(world, baseX, y, baseZ + 1, portalData);
            }
         }
      }
   }

   private static void setBlockType(World world, int x, int y, int z, Material material) {
      if (world != null && material != null) {
         world.getBlockAt(x, y, z).setType(material, false);
      }
   }

   private static void setPortalBlock(World world, int x, int y, int z, BlockData portalData) {
      if (world != null && portalData != null) {
         Block block = world.getBlockAt(x, y, z);
         block.setType(Material.AIR, false);
         block.setBlockData(portalData, false);
      }
   }

   private static Location getPortalTeleportLocation(World world, int baseX, int baseY, int baseZ, Axis axis, float yaw, float pitch) {
      return axis == Axis.Z
         ? new Location(world, baseX + 0.5, baseY + 0.1, baseZ + 0.5, yaw, pitch)
         : new Location(world, baseX + 0.5, baseY + 0.1, baseZ + 0.5, yaw, pitch);
   }

   private static boolean isValidPortalLocation(Location location) {
      return location != null && location.getWorld() != null
         ? isPortalColumn(location.getWorld(), location.getBlockX(), location.getBlockY(), location.getBlockZ())
         : false;
   }

   private static boolean isEntityTouchingPortal(Entity entity) {
      if (entity != null && entity.getWorld() != null) {
         BoundingBox box = entity.getBoundingBox();
         int minX = (int)Math.floor(box.getMinX());
         int maxX = (int)Math.floor(box.getMaxX());
         int minY = (int)Math.floor(box.getMinY());
         int maxY = (int)Math.floor(box.getMaxY());
         int minZ = (int)Math.floor(box.getMinZ());
         int maxZ = (int)Math.floor(box.getMaxZ());
         World world = entity.getWorld();

         for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY + 1; y++) {
               for (int z = minZ; z <= maxZ; z++) {
                  if (isNetherPortalMaterial(world.getBlockAt(x, y, z).getType())) {
                     return true;
                  }
               }
            }
         }

         return false;
      } else {
         return false;
      }
   }

   private static Location resolveSafeTarget(Location center) {
      if (center != null && center.getWorld() != null) {
         World world = center.getWorld();
         int baseX = center.getBlockX();
         int baseY = center.getBlockY();
         int baseZ = center.getBlockZ();
         int minY = Math.max(world.getMinHeight() + 1, baseY - 6);
         int maxY = Math.min(world.getMaxHeight() - 2, baseY + 6);
         if (isPortalColumn(world, baseX, baseY, baseZ)) {
            return new Location(world, baseX + 0.5, baseY + 0.1, baseZ + 0.5, center.getYaw(), center.getPitch());
         } else {
            for (int offset = 0; offset <= 2; offset++) {
               int upY = baseY + offset;
               if (upY <= maxY && isSafeStandingSpot(world, baseX, upY, baseZ)) {
                  return new Location(world, baseX + 0.5, upY, baseZ + 0.5, center.getYaw(), center.getPitch());
               }

               if (offset != 0) {
                  int downY = baseY - offset;
                  if (downY >= minY && isSafeStandingSpot(world, baseX, downY, baseZ)) {
                     return new Location(world, baseX + 0.5, downY, baseZ + 0.5, center.getYaw(), center.getPitch());
                  }
               }
            }

            Location columnFallback = findSafeStandingSpotInColumn(center, world.getMinHeight() + 1, world.getMaxHeight() - 2);
            if (columnFallback != null) {
               return columnFallback;
            } else if (world.getEnvironment() == Environment.NETHER) {
               return center;
            } else {
               int highestY;
               try {
                  highestY = world.getHighestBlockAt(baseX, baseZ, HeightMap.MOTION_BLOCKING_NO_LEAVES).getY() + 1;
               } catch (Throwable failure) {
                  highestY = baseY;
               }

               highestY = Math.max(minY, Math.min(highestY, world.getMaxHeight() - 2));
               Location fallback = new Location(world, baseX + 0.5, highestY, baseZ + 0.5, center.getYaw(), center.getPitch());
               return isSafeStandingSpot(world, baseX, highestY, baseZ) ? fallback : center;
            }
         }
      } else {
         return center;
      }
   }

   private static boolean isPortalColumn(World world, int x, int y, int z) {
      return isNetherPortalMaterial(world.getBlockAt(x, y, z).getType()) || isNetherPortalMaterial(world.getBlockAt(x, y + 1, z).getType());
   }

   private static boolean isSafeStandingSpot(World world, int x, int y, int z) {
      Block below = world.getBlockAt(x, y - 1, z);
      Block feet = world.getBlockAt(x, y, z);
      Block head = world.getBlockAt(x, y + 1, z);
      return !below.isPassable() && feet.isPassable() && head.isPassable();
   }

   private static Location findSafeStandingSpotInColumn(Location center, int minY, int maxY) {
      World world = center.getWorld();
      if (world == null) {
         return null;
      } else {
         int x = center.getBlockX();
         int z = center.getBlockZ();
         int baseY = Math.max(minY, Math.min(center.getBlockY(), maxY));

         for (int y = baseY; y >= minY; y--) {
            if (isSafeStandingSpot(world, x, y, z)) {
               return new Location(world, x + 0.5, y, z + 0.5, center.getYaw(), center.getPitch());
            }
         }

         if (world.getEnvironment() == Environment.NETHER) {
            return null;
         } else {
            for (int yx = baseY + 1; yx <= maxY; yx++) {
               if (isSafeStandingSpot(world, x, yx, z)) {
                  return new Location(world, x + 0.5, yx, z + 0.5, center.getYaw(), center.getPitch());
               }
            }

            return null;
         }
      }
   }

   private static Location clampToWorldBorder(World world, Location location) {
      if (world != null && location != null) {
         WorldBorder border = world.getWorldBorder();
         double half = border.getSize() / 2.0;
         double minX = border.getCenter().getX() - half + 1.0;
         double maxX = border.getCenter().getX() + half - 1.0;
         double minZ = border.getCenter().getZ() - half + 1.0;
         double maxZ = border.getCenter().getZ() + half - 1.0;
         double x = Math.max(minX, Math.min(location.getX(), maxX));
         double z = Math.max(minZ, Math.min(location.getZ(), maxZ));
         return x == location.getX() && z == location.getZ() ? location : new Location(world, x, location.getY(), z, location.getYaw(), location.getPitch());
      } else {
         return location;
      }
   }

   private static Location buildScaledPortalTarget(Location from, World targetWorld, double scale, double minY, double maxY) {
      if (from != null && from.getWorld() != null && targetWorld != null) {
         double multiplier = from.getWorld().getEnvironment() == Environment.NETHER ? scale : 1.0 / scale;
         double y = clamp(from.getY(), minY, maxY);
         return new Location(
            targetWorld, Math.floor(from.getX() * multiplier) + 0.5, y, Math.floor(from.getZ() * multiplier) + 0.5, from.getYaw(), from.getPitch()
         );
      } else {
         return null;
      }
   }

   private static void setPortalSearchOptions(PlayerPortalEvent event) {
      try {
         event.setSearchRadius(getPortalSearchRadius());
      } catch (Throwable failure) {
         com.Util.Diag.warnOnce("portal-search-radius", "This server rejects PlayerPortalEvent.setSearchRadius; vanilla search is used", failure);
      }

      try {
         event.setCreationRadius(getPortalCreationRadius());
      } catch (Throwable failure) {
         com.Util.Diag.warnOnce("portal-creation-radius", "This server rejects PlayerPortalEvent.setCreationRadius; vanilla creation is used", failure);
      }
   }

   private static Location findNearestPortalCenter(Location center, int horizontalRadius, int verticalRadius) {
      if (center != null && center.getWorld() != null) {
         World world = center.getWorld();
         int baseX = center.getBlockX();
         int baseY = center.getBlockY();
         int baseZ = center.getBlockZ();
         int minY = Math.max(world.getMinHeight(), baseY - Math.max(1, verticalRadius));
         int maxY = Math.min(world.getMaxHeight() - 1, baseY + Math.max(1, verticalRadius));
         double bestDistance = Double.MAX_VALUE;
         Location best = null;

         for (int x = baseX - horizontalRadius; x <= baseX + horizontalRadius; x++) {
            for (int z = baseZ - horizontalRadius; z <= baseZ + horizontalRadius; z++) {
               for (int y = minY; y <= maxY; y++) {
                  if (isNetherPortalMaterial(world.getBlockAt(x, y, z).getType())) {
                     int floorY = y;

                     while (floorY > minY && isNetherPortalMaterial(world.getBlockAt(x, floorY - 1, z).getType())) {
                        floorY--;
                     }

                     double dx = x + 0.5 - center.getX();
                     double dy = floorY + 0.5 - center.getY();
                     double dz = z + 0.5 - center.getZ();
                     double distance = dx * dx + dy * dy + dz * dz;
                     if (!(distance >= bestDistance)) {
                        bestDistance = distance;
                        best = new Location(world, x + 0.5, floorY + 0.1, z + 0.5, center.getYaw(), center.getPitch());
                     }
                  }
               }
            }
         }

         return best;
      } else {
         return null;
      }
   }

   @EventHandler(
      priority = EventPriority.HIGH,
      ignoreCancelled = true
   )
   public void onEnderPearlPortal(PlayerTeleportEvent event) {
      if (Main.JavaPlugin.getConfig().getBoolean("EnableHomeNether")) {
         if (event.getCause() == TeleportCause.ENDER_PEARL) {
            if (event.getFrom() != null && event.getTo() != null) {
               if (event.getFrom().getWorld() != null && event.getTo().getWorld() != null) {
                  if (!event.getFrom().getWorld().equals(event.getTo().getWorld())) {
                     World fromWorld = event.getFrom().getWorld();
                     World toWorld = event.getTo().getWorld();
                     String fromName = fromWorld.getName().replace(Variable.world_prefix, "");
                     String suffix = Main.JavaPlugin.getConfig().getString("HomeNetherSuffix");
                     if (suffix == null || suffix.isEmpty()) {
                        suffix = "_nether";
                     }

                     if (Util.CheckIsHome(fromName) && !fromName.endsWith(suffix)) {
                        String expectedNether = Variable.world_prefix + fromName + suffix;
                        if (!toWorld.getName().equals(expectedNether)) {
                           event.setCancelled(true);
                        }
                     } else {
                        if (fromName.endsWith(suffix)) {
                           String base = fromName.substring(0, fromName.length() - suffix.length());
                           String expectedOver = Variable.world_prefix + base;
                           if (!toWorld.getName().equals(expectedOver)) {
                              event.setCancelled(true);
                           }
                        }
                     }
                  }
               }
            }
         }
      }
   }

   @EventHandler(
      priority = EventPriority.MONITOR,
      ignoreCancelled = true
   )
   public void onNetherPortalTeleportRecord(PlayerTeleportEvent event) {
      if (Main.JavaPlugin.getConfig().getBoolean("EnableHomeNether")) {
         if (event.getCause() == TeleportCause.NETHER_PORTAL) {
            if (event.getFrom() != null && event.getTo() != null) {
               if (event.getFrom().getWorld() != null && event.getTo().getWorld() != null) {
                  if (!event.getFrom().getWorld().equals(event.getTo().getWorld())) {
                     Location fromPortal = findNearestPortalCenter(event.getFrom(), 4, 12);
                     Location toPortal = findNearestPortalCenter(event.getTo(), 4, 12);
                     if (fromPortal != null && toPortal != null) {
                        linkPortals(fromPortal, toPortal);
                     }
                  }
               }
            }
         }
      }
   }
}
