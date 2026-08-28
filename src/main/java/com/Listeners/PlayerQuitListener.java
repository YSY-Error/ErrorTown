package com.Listeners;

import com.ErrorTown.Main;
import com.ErrorTown.Variable;
import com.Util.HomeWorldManager;
import com.Util.Util;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerQuitListener implements Listener {
   @EventHandler(priority = EventPriority.MONITOR)
   public void onQuit(PlayerQuitEvent event) {
      String currentBase = Util.getBaseHomeName(event.getPlayer().getWorld().getName());
      if (Util.CheckIsHome(currentBase)) {
         HomeWorldManager.markActive(currentBase);
         Bukkit.getScheduler().runTaskLater(Main.JavaPlugin, () -> HomeWorldManager.scheduleUnload(currentBase), 20L);
      }

      String joinWorld = Main.JavaPlugin.getConfig().getString("NormalJoinWorld");
      if (joinWorld != null && !joinWorld.isEmpty()) {
         World world = Bukkit.getWorld(Variable.world_prefix + joinWorld);
         if (world != null) {
            event.getPlayer().teleport(world.getSpawnLocation());
         } else {
            Main.JavaPlugin.getLogger().warning("NormalJoinWorld '" + joinWorld + "' does not exist; quit teleport skipped.");
         }
      }

      releaseSessionState(event.getPlayer().getName());
   }

   /**
    * Drops the per-session state that is keyed by player name.
    *
    * <p>None of these maps were ever pruned, so on a long-running server they grew
    * monotonically for every player who ever used a menu or a chat prompt. They also hold
    * conversational state: a player who quit half-way through a chat prompt and rejoined
    * had the stale prompt applied to their next chat message.</p>
    *
    * <p>Deliberately <b>not</b> cleared:</p>
    * <ul>
    *   <li>{@code setSpawnCooldown} — a cooldown that must survive relogging, otherwise
    *       quitting bypasses the configured {@code SetSpawn.CooldownSeconds}.</li>
    *   <li>{@code pendingCreateCostPaid} — payment state. The creation coordinator owns
    *       its lifecycle and pairs it with the persistent {@code CreateCostLedger};
    *       clearing it here would drop the paid marker without refunding.</li>
    *   <li>{@code popularity_list}, {@code flowers_list},
    *       {@code toplist_popularity_flowers} — daily/global statistics, not session
    *       state.</li>
    *   <li>{@code homeUnloadTaskIds}, {@code border_redis} — keyed by home, not player.</li>
    * </ul>
    */
   private static void releaseSessionState(String playerName) {
      if (playerName == null) {
         return;
      }

      Variable.cache.invalidatePlayer(playerName);

      // Chat prompts and multi-step menu flows.
      Variable.wait_chat_input.remove(playerName);
      Variable.wait_to_command.remove(playerName);
      Variable.wait_to_spawn_home.remove(playerName);
      Variable.pendingSetSpawnTarget.remove(playerName);
      Variable.pendingCreateSeed.remove(playerName);
      Variable.pendingCreateHomeName.remove(playerName);
      Variable.inviteHomeName.remove(playerName);
      Variable.invite_list.remove(playerName);
      Variable.has_open_gifts_list.remove(playerName);
      Variable.flying_list.remove(playerName);

      // Confirmation and cooldown lists that only make sense within one session.
      Variable.waitDeleteconfirm.remove(playerName);
      Variable.wait_to_confirm_command.remove(playerName);
      Variable.calc_cooldown.remove(playerName);
      Variable.Debug.remove(playerName);
      Variable.AddDebuff.remove(playerName);
      Variable.has_already_hide_border.remove(playerName);
   }
}
