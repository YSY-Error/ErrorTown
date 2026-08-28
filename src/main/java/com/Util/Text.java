package com.Util;

import com.ErrorTown.Main;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

/**
 * One place where every operator-authored string becomes display text.
 *
 * <p><b>What this buys.</b> Config and language files may be written in any of three styles, mixed
 * freely, with no migration:</p>
 *
 * <ul>
 *   <li><b>MiniMessage</b> — {@code <gradient:#ff5f6d:#ffc371>错误庄园</gradient>},
 *       {@code <bold><#55FF55>已创建}, {@code <rainbow>}, {@code <font:...>}. Full RGB and
 *       gradients, on Spigot as well as Paper.</li>
 *   <li><b>Legacy {@code §} codes</b> — everything the plugin already shipped keeps working
 *       untouched.</li>
 *   <li><b>Ampersand codes</b> — {@code &a}, {@code &l}, and {@code &#RRGGBB} hex, which is what
 *       operators actually type into YAML.</li>
 * </ul>
 *
 * <p><b>How.</b> MiniMessage parses to an Adventure {@link Component}, which is then serialised back
 * to a legacy {@code §} string. Everything downstream — {@code sendMessage(String)},
 * {@code ItemMeta.setDisplayName}, inventory titles — is the plain String Bukkit API that exists
 * identically on Spigot, Paper, Purpur and Leaves from 1.21 through 26.2. Adventure is shaded into
 * the jar and relocated, and <b>no Adventure type ever crosses a server API boundary</b>, which is
 * what keeps the relocated copy from colliding with the unrelocated one Paper bundles.</p>
 *
 * <p><b>Mixing.</b> Adventure's own FAQ says legacy and MiniMessage cannot be combined. That warning
 * is about producing a {@code Component} whose <i>style</i> comes from both; it does not apply here,
 * because the output is a legacy string in the first place. A stray {@code §a} inside MiniMessage
 * input survives as literal content and the client still renders it, so a line that came half from
 * config and half from a PlaceholderAPI expansion behaves sensibly.</p>
 *
 * <p>Parsing is cached: GUI lore is re-rendered on every menu open, and MiniMessage parsing is far
 * from free.</p>
 */
public final class Text {
   /** {@code Formatting.Mode}: {@code auto} (default), {@code minimessage}, or {@code legacy}. */
   public static final String MODE_KEY = "Formatting.Mode";
   /** {@code Formatting.TranslateAmpersand}: also read {@code &a} / {@code &#RRGGBB} as colour codes. */
   public static final String AMPERSAND_KEY = "Formatting.TranslateAmpersand";

   private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
   /** {@code §x§r§r§g§g§b§b} output, which every 1.16+ client understands. */
   private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder()
      .character(LegacyComponentSerializer.SECTION_CHAR)
      .hexColors()
      .useUnusualXRepeatedCharacterHexFormat()
      .build();

   private static final Map<String, String> CACHE = Util.boundedCache(4096);

   private Text() {
   }

   /**
    * Formats one operator-authored string for display.
    *
    * @return the text as legacy {@code §} markup, never {@code null} unless {@code raw} is
    */
   public static String format(String raw) {
      if (raw == null || raw.isEmpty()) {
         return raw;
      }
      String cached = CACHE.get(raw);
      if (cached != null) {
         return cached;
      }
      String rendered = render(raw);
      CACHE.put(raw, rendered);
      return rendered;
   }

   /** Formats every line of a lore or message list. */
   public static List<String> format(List<String> raw) {
      if (raw == null || raw.isEmpty()) {
         return raw;
      }
      List<String> rendered = new ArrayList<>(raw.size());
      for (String line : raw) {
         rendered.add(format(line));
      }
      return rendered;
   }

   private static String render(String raw) {
      String text = translateAmpersand(raw);
      if (!useMiniMessage(text)) {
         return text;
      }
      try {
         return LEGACY.serialize(MINI_MESSAGE.deserialize(text));
      } catch (RuntimeException malformed) {
         // A half-typed tag must not blank out a menu or a message.
         Diag.warnOnce(
            "minimessage-parse",
            "Could not parse MiniMessage in '" + abbreviate(raw) + "'; showing it unparsed",
            malformed
         );
         return text;
      } catch (LinkageError missing) {
         Diag.warnOnce(
            "minimessage-missing",
            "MiniMessage is unavailable in this build; falling back to legacy colour codes for all text"
         );
         return text;
      }
   }

   /**
    * Whether {@code text} should go through MiniMessage.
    *
    * <p>In {@code auto} mode only text that actually contains a tag is parsed. That keeps the
    * thousands of existing {@code §}-only strings on a zero-cost path and means an operator who
    * never writes a tag cannot be surprised by MiniMessage's own escaping rules.</p>
    */
   private static boolean useMiniMessage(String text) {
      String mode = mode();
      if (mode.equals("legacy")) {
         return false;
      }
      if (mode.equals("minimessage")) {
         return true;
      }
      return looksLikeMiniMessage(text);
   }

   /** @return whether {@code text} contains something shaped like a MiniMessage tag */
   public static boolean looksLikeMiniMessage(String text) {
      if (text == null) {
         return false;
      }
      int open = text.indexOf('<');
      while (open >= 0 && open + 1 < text.length()) {
         char next = text.charAt(open + 1);
         if (next == '/' || next == '#' || Character.isLetter(next)) {
            if (text.indexOf('>', open + 1) > open) {
               return true;
            }
         }
         open = text.indexOf('<', open + 1);
      }
      return false;
   }

   private static String mode() {
      if (Main.JavaPlugin == null) {
         return "auto";
      }
      String configured = Main.JavaPlugin.getConfig().getString(MODE_KEY, "auto");
      if (configured == null) {
         return "auto";
      }
      String normalized = configured.trim().toLowerCase(Locale.ROOT);
      return switch (normalized) {
         case "legacy", "minimessage", "auto" -> normalized;
         default -> {
            Diag.warnOnce(MODE_KEY, MODE_KEY + " must be auto, minimessage or legacy but is '" + configured + "'; using auto");
            yield "auto";
         }
      };
   }

   /**
    * Turns {@code &} colour codes into {@code §} ones, including {@code &#RRGGBB} hex.
    *
    * <p>Done before MiniMessage so an operator can mix the two, and skipped entirely when
    * {@code Formatting.TranslateAmpersand} is off — a server whose messages legitimately contain a
    * literal {@code &a} needs that escape hatch.</p>
    *
    * <p>Implemented here rather than through {@code ChatColor.translateAlternateColorCodes} so that
    * this class stays pure text processing: {@code ChatColor}'s static initialiser pulls in Guava,
    * which turns a formatting helper into something that cannot run outside a live server.</p>
    */
   private static String translateAmpersand(String raw) {
      if (raw.indexOf('&') < 0 || !ampersandEnabled()) {
         return raw;
      }
      String hexExpanded = expandAmpersandHex(raw);
      char[] chars = hexExpanded.toCharArray();
      for (int i = 0; i < chars.length - 1; i++) {
         if (chars[i] == '&' && isFormatCode(chars[i + 1])) {
            chars[i] = '\u00a7';
            chars[i + 1] = Character.toLowerCase(chars[i + 1]);
         }
      }
      return new String(chars);
   }

   /** The colour, decoration, reset and hex-marker codes Minecraft accepts after a {@code §}. */
   private static boolean isFormatCode(char code) {
      return "0123456789AaBbCcDdEeFfKkLlMmNnOoRrXx".indexOf(code) >= 0;
   }

   private static boolean ampersandEnabled() {
      return Main.JavaPlugin == null || Main.JavaPlugin.getConfig().getBoolean(AMPERSAND_KEY, true);
   }

   /** Rewrites {@code &#RRGGBB} as the {@code §x§r§r§g§g§b§b} form clients accept. */
   private static String expandAmpersandHex(String raw) {
      int marker = raw.indexOf("&#");
      if (marker < 0) {
         return raw;
      }
      StringBuilder out = new StringBuilder(raw.length() + 16);
      int cursor = 0;
      while (marker >= 0) {
         if (marker + 8 > raw.length() || !isHex(raw, marker + 2, 6)) {
            out.append(raw, cursor, marker + 2);
            cursor = marker + 2;
            marker = raw.indexOf("&#", cursor);
            continue;
         }
         out.append(raw, cursor, marker).append('\u00a7').append('x');
         for (int i = marker + 2; i < marker + 8; i++) {
            out.append('\u00a7').append(raw.charAt(i));
         }
         cursor = marker + 8;
         marker = raw.indexOf("&#", cursor);
      }
      return out.append(raw, cursor, raw.length()).toString();
   }

   private static boolean isHex(String text, int from, int length) {
      for (int i = from; i < from + length; i++) {
         if (Character.digit(text.charAt(i), 16) < 0) {
            return false;
         }
      }
      return true;
   }

   private static String abbreviate(String text) {
      return text.length() <= 60 ? text : text.substring(0, 57) + "...";
   }

   /** Test hook: config changes on reload must not be masked by stale renders. */
   public static void clearCache() {
      CACHE.clear();
   }
}
