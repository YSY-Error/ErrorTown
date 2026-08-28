package com.Listeners;

import com.ErrorTown.Variable;
import com.Util.Home;
import com.Util.HomeAPI;
import com.Util.Util;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.block.EntityBlockFormEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.entity.AreaEffectCloudApplyEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPlaceEvent;
import org.bukkit.event.entity.PlayerLeashEntityEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerBucketEntityEvent;
import org.bukkit.event.player.PlayerShearEntityEvent;
import org.bukkit.event.player.PlayerUnleashEntityEvent;
import org.bukkit.event.vehicle.VehicleDestroyEvent;
import org.bukkit.event.world.StructureGrowEvent;
import org.bukkit.event.world.WorldUnloadEvent;

/**
 * Closes the protection gaps that no other ErrorTown listener covered.
 *
 * <p>The plugin's promise is that only a home's owner, managers and trusted members
 * can change it. The original listener set handled the obvious paths
 * ({@code BlockPlace}, {@code BlockBreak}, {@code PlayerInteract}, buckets) but left
 * out the block-modification routes that land-claim plugins are normally attacked
 * through. Each handler below corresponds to one of those routes.</p>
 *
 * <p>Two categories are handled differently:</p>
 * <ul>
 *   <li><b>Player-attributable</b> events are checked with the same
 *       {@link Util#Check(Player, String)} rule the rest of the plugin uses.</li>
 *   <li><b>Environment</b> events (piston, fluid flow, fire spread, entity block
 *       changes) have no player. Those are blocked when they would cross the home's
 *       world border, or when the home's own rules forbid them. Border crossing is
 *       evaluated with {@link WorldBorder#isInside(Location)} so this listener can
 *       never disagree with the size computed by {@code HomeTerrainPolicy}.</li>
 * </ul>
 */
public class HomeProtectionListener implements Listener {

   // ------------------------------------------------------------------
   // Player-attributable modification
   // ------------------------------------------------------------------

   /** Item frames, paintings and other hangings could previously be placed by anyone. */
   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
   public void onHangingPlace(HangingPlaceEvent event) {
      if (denied(event.getPlayer(), event.getBlock() == null ? null : event.getBlock().getWorld())) {
         notifyAndCancel(event.getPlayer(), () -> event.setCancelled(true));
      }
   }

   /** Only HangingBreakByEntityEvent was covered, so explosions and block removal slipped through. */
   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
   public void onHangingBreak(HangingBreakEvent event) {
      if (event.getCause() == HangingBreakEvent.RemoveCause.ENTITY) {
         return; // FrameProtectListener owns that path.
      }
      String home = protectedHome(event.getEntity().getWorld());
      if (home != null) {
         event.setCancelled(true);
      }
   }

   /**
    * Armor stands do not fire PlayerInteractAtEntityEvent for equipment swaps, so
    * {@code EnableEntityInteract} never protected their contents.
    */
   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
   public void onArmorStandManipulate(PlayerArmorStandManipulateEvent event) {
      if (denied(event.getPlayer(), event.getRightClicked().getWorld())) {
         notifyAndCancel(event.getPlayer(), () -> event.setCancelled(true));
      }
   }

   /** Armor stands, boats, minecarts and end crystals are placed through this event, not BlockPlaceEvent. */
   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
   public void onEntityPlace(EntityPlaceEvent event) {
      if (denied(event.getPlayer(), event.getBlock() == null ? null : event.getBlock().getWorld())) {
         notifyAndCancel(event.getPlayer(), () -> event.setCancelled(true));
      }
   }

   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
   public void onSignChange(SignChangeEvent event) {
      if (denied(event.getPlayer(), event.getBlock().getWorld())) {
         notifyAndCancel(event.getPlayer(), () -> event.setCancelled(true));
      }
   }

   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
   public void onBucketEntity(PlayerBucketEntityEvent event) {
      if (denied(event.getPlayer(), event.getEntity().getWorld())) {
         notifyAndCancel(event.getPlayer(), () -> event.setCancelled(true));
      }
   }

   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
   public void onLeash(PlayerLeashEntityEvent event) {
      if (denied(event.getPlayer(), event.getEntity().getWorld())) {
         notifyAndCancel(event.getPlayer(), () -> event.setCancelled(true));
      }
   }

   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
   public void onUnleash(PlayerUnleashEntityEvent event) {
      if (event.getPlayer() != null && denied(event.getPlayer(), event.getEntity().getWorld())) {
         notifyAndCancel(event.getPlayer(), () -> event.setCancelled(true));
      }
   }

   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
   public void onShear(PlayerShearEntityEvent event) {
      if (denied(event.getPlayer(), event.getEntity().getWorld())) {
         notifyAndCancel(event.getPlayer(), () -> event.setCancelled(true));
      }
   }

   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
   public void onVehicleDestroy(VehicleDestroyEvent event) {
      if (event.getAttacker() instanceof Player attacker && denied(attacker, event.getVehicle().getWorld())) {
         notifyAndCancel(attacker, () -> event.setCancelled(true));
      }
   }

   /** Prevents visitors from claiming a respawn point inside somebody else's home. */
   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
   public void onBedEnter(PlayerBedEnterEvent event) {
      if (denied(event.getPlayer(), event.getBed().getWorld())) {
         notifyAndCancel(event.getPlayer(), () -> event.setCancelled(true));
      }
   }

   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
   public void onIgnite(BlockIgniteEvent event) {
      Player player = event.getPlayer();
      if (player != null) {
         if (denied(player, event.getBlock().getWorld())) {
            notifyAndCancel(player, () -> event.setCancelled(true));
         }
         return;
      }
      // No player: this is fire spreading. doFireTick already limits it, but the
      // per-home NoFireSpread rule was never enforced for ignition.
      if (fireSpreadBlocked(event.getBlock().getWorld())) {
         event.setCancelled(true);
      }
   }

   // ------------------------------------------------------------------
   // Environment modification with no responsible player
   // ------------------------------------------------------------------

   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
   public void onBurn(BlockBurnEvent event) {
      if (fireSpreadBlocked(event.getBlock().getWorld())) {
         event.setCancelled(true);
      }
   }

   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
   public void onSpread(BlockSpreadEvent event) {
      World world = event.getBlock().getWorld();
      if (protectedHome(world) == null) {
         return;
      }
      if (fireSpreadBlocked(world) && event.getNewState().getType().name().contains("FIRE")) {
         event.setCancelled(true);
         return;
      }
      if (crossesBorder(event.getSource().getLocation(), event.getBlock().getLocation())) {
         event.setCancelled(true);
      }
   }

   /** Water and lava poured just outside the border used to flow straight into the home. */
   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
   public void onFromTo(BlockFromToEvent event) {
      if (protectedHome(event.getBlock().getWorld()) == null) {
         return;
      }
      if (crossesBorder(event.getBlock().getLocation(), event.getToBlock().getLocation())) {
         event.setCancelled(true);
      }
   }

   /** Classic claim bypass: push blocks across the border with a piston. */
   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
   public void onPistonExtend(BlockPistonExtendEvent event) {
       if (pistonCrossesBorder(event.getBlock(), event.getBlocks(), true)) {
         event.setCancelled(true);
      }
   }

   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
   public void onPistonRetract(BlockPistonRetractEvent event) {
       if (pistonCrossesBorder(event.getBlock(), event.getBlocks(), false)) {
         event.setCancelled(true);
      }
   }

   /**
    * Endermen picking blocks, sheep eating grass, villagers farming, ravagers
    * trampling crops, falling blocks and withers all arrive through this event.
    */
   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
   public void onEntityChangeBlock(EntityChangeBlockEvent event) {
      Entity entity = event.getEntity();
      World world = event.getBlock().getWorld();
      if (entity instanceof Player player) {
         if (denied(player, world)) {
            notifyAndCancel(player, () -> event.setCancelled(true));
         }
         return;
      }
      if (protectedHome(world) == null) {
         return;
      }
      if (!isInsideBorder(event.getBlock().getLocation())) {
         event.setCancelled(true);
         return;
      }
      // Mob griefing inside the home follows the home's explosion/grief rule.
      Home home = home(world);
      if (home != null && home.getRuleExplosionProtect()) {
         event.setCancelled(true);
      }
   }

   /** Frost walker and snow golems forming blocks outside the border. */
   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
   public void onBlockForm(EntityBlockFormEvent event) {
      if (protectedHome(event.getBlock().getWorld()) != null && !isInsideBorder(event.getBlock().getLocation())) {
         event.setCancelled(true);
      }
   }

   /** Dispensers firing lava, water or TNT over the border. */
   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
   public void onDispense(BlockDispenseEvent event) {
       if (protectedHome(event.getBlock().getWorld()) != null && dispenserCrossesBorder(event)) {
          event.setCancelled(true);
       }
   }

   /** Trees growing across the border into a neighbour's plot. */
   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
   public void onStructureGrow(StructureGrowEvent event) {
      World world = event.getWorld();
      if (protectedHome(world) == null) {
         return;
      }
      if (event.getPlayer() != null && denied(event.getPlayer(), world)) {
         event.setCancelled(true);
         return;
      }
      for (org.bukkit.block.BlockState state : event.getBlocks()) {
         if (!isInsideBorder(state.getLocation())) {
            event.setCancelled(true);
            return;
         }
      }
   }

   /**
    * Refuses to unload a home world that still has players in it.
    *
    * <p>{@code HomeWorldLifecycle.EnableAutoUnload} plus a missed callback could
    * otherwise unload a world underneath a player, and there was no listener guarding
    * against it.</p>
   */
   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
   public void onWorldUnload(WorldUnloadEvent event) {
      World world = event.getWorld();
      if (protectedHome(world) != null && !world.getPlayers().isEmpty()) {
         event.setCancelled(true);
      }
   }

   // ------------------------------------------------------------------
   // Explosions and projectiles
   //
   // The block-level handlers above stop a dispenser from firing across the border, but
   // they cannot follow what the dispensed entity does next: TNT primed inside the plot
   // can still destroy blocks outside it, and a fire charge can ignite past the boundary.
   // Rather than cancel the whole explosion (which would change vanilla behaviour inside
   // the plot) the affected block list is filtered down to what is actually inside.
   // ------------------------------------------------------------------

   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
   public void onEntityExplode(EntityExplodeEvent event) {
      confineExplosion(event.getLocation().getWorld(), event.blockList());
   }

   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
   public void onBlockExplode(BlockExplodeEvent event) {
      confineExplosion(event.getBlock().getWorld(), event.blockList());
   }

   /** Drops every block outside the border from an explosion's affected list. */
   private static void confineExplosion(World world, java.util.List<Block> affected) {
      if (world == null || affected == null || affected.isEmpty() || protectedHome(world) == null) {
         return;
      }
      Home home = home(world);
      boolean protectAll = home != null && home.getRuleExplosionProtect();
      if (protectAll) {
         affected.clear();
         return;
      }
      affected.removeIf(block -> !isInsideBorder(block.getLocation()));
   }

   /**
    * Stops a projectile that left the plot from having a block effect outside it.
    *
    * <p>Fire charges, wind charges and splash effects all resolve through this event, and
    * none of the block handlers see them because the change is attributed to the
    * projectile, not to a player placing a block.</p>
    */
   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
   public void onProjectileHit(ProjectileHitEvent event) {
      Block hit = event.getHitBlock();
      if (hit == null || protectedHome(hit.getWorld()) == null) {
         return;
      }
      if (!isInsideBorder(hit.getLocation())) {
         event.setCancelled(true);
      }
   }

   /** Keeps splash and lingering potions from reaching players outside the plot. */
   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
   public void onPotionSplash(PotionSplashEvent event) {
      World world = event.getEntity().getWorld();
      if (protectedHome(world) == null) {
         return;
      }
      for (org.bukkit.entity.LivingEntity affected : new java.util.ArrayList<>(event.getAffectedEntities())) {
         if (!isInsideBorder(affected.getLocation())) {
            event.setIntensity(affected, 0.0);
         }
      }
   }

   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
   public void onAreaEffectCloud(AreaEffectCloudApplyEvent event) {
      World world = event.getEntity().getWorld();
      if (protectedHome(world) == null) {
         return;
      }
      event.getAffectedEntities().removeIf(affected -> !isInsideBorder(affected.getLocation()));
   }

   // ------------------------------------------------------------------
   // Helpers
   // ------------------------------------------------------------------

   /** Returns the home base name when {@code world} is a managed home, else null. */
   private static String protectedHome(World world) {
      if (world == null) {
         return null;
      }
      String base = Util.getBaseHomeName(world.getName());
      return Util.CheckIsHome(base) ? base : null;
   }

   private static Home home(World world) {
      String base = protectedHome(world);
      return base == null ? null : HomeAPI.getHome(base);
   }

   /** True when {@code player} may not modify {@code world}. */
   private static boolean denied(Player player, World world) {
      if (player == null) {
         return false;
      }
      String base = protectedHome(world);
      return base != null && !Util.Check(player, base);
   }

   private static boolean fireSpreadBlocked(World world) {
      Home home = home(world);
      return home != null && home.getRuleNoFireSpread();
   }

   private static boolean isInsideBorder(Location location) {
      if (location == null || location.getWorld() == null) {
         return true;
      }
      return location.getWorld().getWorldBorder().isInside(location);
   }

   private static boolean crossesBorder(Location from, Location to) {
      return isInsideBorder(from) != isInsideBorder(to);
   }

   private static boolean dispenserCrossesBorder(BlockDispenseEvent event) {
      Location origin = event.getBlock().getLocation();
      if (!isInsideBorder(origin)) {
         return true;
      }
      BlockFace facing = event.getBlock().getBlockData() instanceof Directional directional
         ? directional.getFacing()
         : null;
      if (facing == null) {
         return false;
      }
      Location target = origin.clone().add(facing.getModX(), facing.getModY(), facing.getModZ());
      return !isInsideBorder(target);
   }

   private static boolean pistonCrossesBorder(Block piston, java.util.List<Block> moved, boolean extending) {
      if (protectedHome(piston.getWorld()) == null) {
         return false;
      }
      boolean pistonInside = isInsideBorder(piston.getLocation());
      BlockFace direction = piston.getBlockData() instanceof Directional directional
         ? directional.getFacing()
         : null;
      if (direction != null && !extending) {
         direction = direction.getOppositeFace();
      }
      for (Block block : moved) {
         if (isInsideBorder(block.getLocation()) != pistonInside) {
            return true;
         }
         if (direction != null) {
            Location target = block.getLocation().clone().add(
               direction.getModX(), direction.getModY(), direction.getModZ());
            if (isInsideBorder(target) != pistonInside) {
               return true;
            }
         }
      }
      return false;
   }

   private static void notifyAndCancel(Player player, Runnable cancel) {
      cancel.run();
      if (player == null) {
         return;
      }
      String message = Variable.Lang_YML == null ? null : Variable.Lang_YML.getString("NoPermissionInteract");
      player.sendMessage(message != null ? message : "§8[§6错误庄园§8] §c你没有权限改动这个家园。");
   }
}
