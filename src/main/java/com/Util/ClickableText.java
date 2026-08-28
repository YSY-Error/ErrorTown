package com.Util;

import com.ErrorTown.Variable;
import java.util.List;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Clickable chat lines that survive both the legacy {@code §} colour codes and a missing click
 * target.
 *
 * <p><b>Why this exists.</b> {@code /sh help} was documented as broken, and the workaround was to
 * force {@code Variable.has_no_click_message = true} — i.e. give up on clickable help entirely.
 * There were three separate faults behind it:</p>
 *
 * <ol>
 *   <li>{@code new TextComponent(text)} treats its argument as <b>literal</b> text. Feeding it a
 *       string containing {@code §} codes ships the section signs to the client as content, so the
 *       line renders with visible {@code §6} noise instead of colour. The fix is
 *       {@link TextComponent#fromLegacyText(String)}, which parses the codes into real component
 *       formatting.</li>
 *   <li>The help lines are {@code "text,command"} pairs read with {@code split(",")}, and
 *       {@code str[1]} was indexed unconditionally. A line without a comma threw
 *       {@link ArrayIndexOutOfBoundsException} out of the command handler.</li>
 *   <li>The {@code Help-1} … {@code Help-6} keys were absent from every shipped language file, so
 *       even a working renderer had nothing to print. They are shipped now, and
 *       {@link Lang#audit()} reports them if an existing install lacks them.</li>
 * </ol>
 *
 * <p>Bungee's chat components are used rather than Adventure because they exist on Spigot as well
 * as Paper, so one code path covers Spigot / Paper / Purpur / Leaves.</p>
 */
public final class ClickableText {
   /**
    * Substrings that mark a line as page navigation rather than a command hint.
    *
    * <p>Navigation lines run their command immediately; everything else only pre-fills the chat box,
    * so a misclick cannot execute something destructive. The list carries the Simplified Chinese,
    * Traditional Chinese and English wordings used by the shipped language files.</p>
    */
   private static final List<String> NAVIGATION_MARKERS = List.of("下一", "上一", "第一", "Next", "Prev", "First");

   private ClickableText() {
   }

   /**
    * Renders one {@code "text,command"} help line.
    *
    * <p>Splits on the first comma only, so the command part may itself contain commas. A line with
    * no comma is sent as plain text instead of failing.</p>
    */
   public static void sendPairLine(CommandSender target, String rawLine) {
      if (target == null || rawLine == null) {
         return;
      }
      String[] parts = rawLine.split(",", 2);
      String text = parts[0];
      if (parts.length < 2 || parts[1].trim().isEmpty()) {
         target.sendMessage(text);
         return;
      }
      send(target, text, parts[1].trim(), isNavigation(text));
   }

   /**
    * Sends {@code legacyText} as a clickable line.
    *
    * @param runDirectly {@code true} to run {@code command} on click, {@code false} to only put it
    *                    in the player's chat box
    */
   public static void send(CommandSender target, String legacyText, String command, boolean runDirectly) {
      if (target == null || legacyText == null) {
         return;
      }
      if (command == null || command.trim().isEmpty() || Variable.has_no_click_message || !(target instanceof Player player)) {
         // Console, or an operator who turned clickable chat off, or a fork whose chat components
         // are unusable: the text still has to arrive.
         target.sendMessage(Text.format(legacyText));
         return;
      }
      try {
         TextComponent line = new TextComponent(TextComponent.fromLegacyText(Text.format(legacyText)));
         line.setClickEvent(new ClickEvent(
            runDirectly ? ClickEvent.Action.RUN_COMMAND : ClickEvent.Action.SUGGEST_COMMAND,
            command.trim()
         ));
         player.spigot().sendMessage(line);
      } catch (RuntimeException | LinkageError unsupported) {
         // Some hybrid forks ship a broken bungee-chat implementation. Stop trying and fall back to
         // plain text for the rest of the session rather than losing the message.
         Variable.has_no_click_message = true;
         Diag.warnOnce(
            "clickable-chat",
            "This server cannot send clickable chat components; falling back to plain text",
            unsupported instanceof RuntimeException failure ? failure : null
         );
         target.sendMessage(Text.format(legacyText));
      }
   }

   /** Sends {@code legacyText} with a click event that only suggests {@code command}. */
   public static void suggest(CommandSender target, String legacyText, String command) {
      send(target, legacyText, command, false);
   }

   /** Sends {@code legacyText} with a click event that runs {@code command}. */
   public static void run(CommandSender target, String legacyText, String command) {
      send(target, legacyText, command, true);
   }

   /**
    * Converts legacy {@code §} text into components without attaching any click event.
    *
    * <p>Exposed for call sites that build their own component tree.</p>
    */
   public static BaseComponent[] legacy(String legacyText) {
      return TextComponent.fromLegacyText(Text.format(legacyText == null ? "" : legacyText));
   }

   private static boolean isNavigation(String text) {
      for (String marker : NAVIGATION_MARKERS) {
         if (text.contains(marker)) {
            return true;
         }
      }
      return false;
   }
}
