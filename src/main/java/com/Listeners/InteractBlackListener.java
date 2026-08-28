package com.Listeners;

import com.Util.ClickableText;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;

/**
 * Operator debug helper: right-clicking an entity while holding an apple prints its type with a
 * click-to-copy line.
 *
 * <p>The 1.12.2 and 1.7.10 branches this class used to carry were unreachable — they keyed off
 * {@code Bukkit.getBukkitVersion()} containing those strings, which cannot happen on the supported
 * 1.21–26.2 range — and the helpers they called ({@code R1_12_2.getName} / {@code R1_7_10.getName})
 * only normalised {@code EntityType.name()}, which is exactly what the surviving branch does. One of
 * them also had a copy-paste bug: the 1.7.10 branch built its text from {@code R1_7_10} but attached
 * {@code R1_12_2} to the click event.</p>
 *
 * <p>Text is emitted through {@link ClickableText} so the {@code §} codes become real formatting
 * instead of literal section signs.</p>
 */
public class InteractBlackListener implements Listener {
   private static final String DEBUG_RULE = "§e§l§m--------------§7[§eDeBug§7]§e§l§m--------------";

   @EventHandler(
      priority = EventPriority.HIGHEST,
      ignoreCancelled = true
   )
   public void onInteract(PlayerInteractEntityEvent event) {
      String playerName = event.getPlayer().getName().toUpperCase();
      if (playerName.contains("FAKEPLAYER") || playerName.contains("AS-FAKEPLAYER")) {
         return;
      }
      if (!event.getPlayer().isOp() || event.getPlayer().getItemInHand().getType() != Material.APPLE) {
         return;
      }
      if (event.getRightClicked() == null) {
         return;
      }

      String type = event.getRightClicked().getType().toString().toUpperCase();
      event.getPlayer().sendMessage(DEBUG_RULE);
      ClickableText.suggest(event.getPlayer(), "§eType:" + type + " §b>> §dCopy", type);
      event.getPlayer().sendMessage(DEBUG_RULE);
   }
}
