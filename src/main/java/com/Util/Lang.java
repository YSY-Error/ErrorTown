package com.Util;

import com.ErrorTown.Main;
import com.ErrorTown.Variable;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Null-safe access to the active language file.
 *
 * <p><b>Why this exists.</b> The codebase performs about 1300 raw
 * {@code Variable.Lang_YML.getString(key)} calls, and the dominant pattern is:</p>
 * <pre>{@code
 * String temp = Variable.Lang_YML.getString("SomeKey");
 * if (temp.contains("<Name>")) { ... }      // NPE when the key is absent
 * sender.sendMessage(temp);                  // prints "null" when absent
 * }</pre>
 *
 * <p>The three shipped language files differ in size (Chinese 40 KB, Chinese_TW 40 KB,
 * English 42.8 KB), and an operator who keeps an older copy of a language file across a
 * plugin update will be missing keys. The failure then surfaces as a random
 * {@code NullPointerException} mid-command, frequently swallowed by one of the
 * historical empty {@code catch} blocks.</p>
 *
 * <p>Rewriting 1300 call sites is neither safe nor reviewable, so this class attacks the
 * problem from two directions instead:</p>
 * <ol>
 *   <li>{@link #get(String)} and friends never return {@code null} and record every
 *       missing key exactly once, so new and touched code cannot reintroduce the bug.</li>
 *   <li>{@link #audit()} runs at startup, diffs the active language file against the
 *       copy bundled in the jar, and prints the missing keys. A latent
 *       {@code NullPointerException} becomes an explicit, actionable startup warning.</li>
 * </ol>
 */
public final class Lang {
   /** Reference language used when the active language has no bundled counterpart. */
   private static final String REFERENCE_LANGUAGE = "Chinese";

   private static final Set<String> REPORTED_MISSING = ConcurrentHashMap.newKeySet();
   private static final int MAX_REPORTED = 512;

   private Lang() {
   }

   /** Returns the configured text, or an empty string when the key is missing. */
   public static String get(String key) {
      return get(key, "");
   }

   /**
    * Returns the configured text for {@code key}.
    *
    * @param fallback returned when the key is absent, blank, or the language file failed
    *                 to load; never returns {@code null} unless {@code fallback} is null
    */
   public static String get(String key, String fallback) {
      if (key == null) {
         return fallback;
      }
      FileConfiguration language = Variable.Lang_YML;
      if (language == null) {
         return fallback;
      }
      String value = language.getString(key);
      if (value == null) {
         reportMissing(key);
         return fallback == null ? null : Text.format(fallback);
      }
      return value.trim().isEmpty() ? Text.format(fallback) : Text.format(value);
   }

   /** Returns the configured list, never {@code null}. */
   public static List<String> list(String key) {
      FileConfiguration language = Variable.Lang_YML;
      if (language == null || key == null) {
         return Collections.emptyList();
      }
      if (!language.contains(key)) {
         reportMissing(key);
         return Collections.emptyList();
      }
      List<String> values = language.getStringList(key);
      return values == null ? Collections.emptyList() : Text.format(values);
   }

   /**
    * Substitutes {@code placeholder}/{@code value} pairs into the text for {@code key}.
    *
    * @param placeholdersAndValues alternating placeholder and value, e.g.
    *                              {@code "<Name>", name, "<Level>", "3"}
    */
   public static String format(String key, String fallback, String... placeholdersAndValues) {
      String text = get(key, fallback);
      if (text == null || placeholdersAndValues == null) {
         return text;
      }
      for (int i = 0; i + 1 < placeholdersAndValues.length; i += 2) {
         String placeholder = placeholdersAndValues[i];
         String value = placeholdersAndValues[i + 1];
         if (placeholder != null) {
            text = text.replace(placeholder, value == null ? "" : value);
         }
      }
      return text;
   }

   /** Sends the text for {@code key}, skipping the message entirely when it resolves blank. */
   public static void send(CommandSender target, String key, String... placeholdersAndValues) {
      if (target == null) {
         return;
      }
      String text = format(key, "", placeholdersAndValues);
      if (text != null && !text.isEmpty()) {
         target.sendMessage(text);
      }
   }

   /** Sends the plugin's standard header / body / footer triple, skipping blank parts. */
   public static void sendFramed(CommandSender target, String key, String... placeholdersAndValues) {
      if (target == null) {
         return;
      }
      send(target, "HeadLineTtitle");
      send(target, key, placeholdersAndValues);
      send(target, "BottomLineTtitle");
   }

   private static void reportMissing(String key) {
      if (Main.JavaPlugin == null || REPORTED_MISSING.size() >= MAX_REPORTED || !REPORTED_MISSING.add(key)) {
         return;
      }
      Main.JavaPlugin
         .getLogger()
         .warning("Language key '" + key + "' is missing from " + activeLanguageName() + ".yml; using the built-in fallback.");
   }

   /**
    * Copies keys the operator's language file is missing out of the copy bundled in the jar.
    *
    * <p>Bukkit only writes a language file once — on first start, when the file does not exist yet.
    * Every key added by a later plugin version is therefore absent forever on an existing server,
    * and {@link #audit()} could only complain about it. That is how {@code /sh help} came to print an
    * empty page: the {@code Help-1} … {@code Help-6} lists were added to the bundled files but never
    * reached anyone's {@code plugins/ErrorTown/Language/}.</p>
    *
    * <p>Only absent keys are written, so customised text is never overwritten. Seeding is done from
    * the <i>same</i> language as the active one; a custom language file with no bundled counterpart is
    * left untouched rather than having another language's text injected into it.</p>
    *
    * @return the number of keys added
    */
   public static int seedMissingFromBundle() {
      if (Main.JavaPlugin == null || Variable.Lang_YML == null) {
         return 0;
      }
      String active = activeLanguageName();
      YamlConfiguration bundled = loadBundled(active);
      if (bundled == null) {
         return 0;
      }

      List<String> added = new ArrayList<>();
      for (String key : bundled.getKeys(true)) {
         if (bundled.isConfigurationSection(key) || Variable.Lang_YML.contains(key)) {
            continue;
         }
         Variable.Lang_YML.set(key, bundled.get(key));
         added.add(key);
      }
      if (added.isEmpty()) {
         return 0;
      }

      java.util.logging.Logger log = Main.JavaPlugin.getLogger();
      try {
         Variable.Lang_YML.save(activeLanguageFile(active));
         log.info("Added " + added.size() + " missing key(s) to " + active + ".yml from the bundled copy: " + preview(added));
      } catch (java.io.IOException failure) {
         log.log(
            java.util.logging.Level.WARNING,
            "Could not write " + active + ".yml; the " + added.size() + " recovered key(s) apply to this run only",
            failure
         );
      }
      return added.size();
   }

   private static java.io.File activeLanguageFile(String languageName) {
      return new java.io.File(new java.io.File(Main.JavaPlugin.getDataFolder(), "Language"), languageName + ".yml");
   }

   private static String preview(List<String> keys) {
      int shown = Math.min(keys.size(), 8);
      String joined = String.join(", ", keys.subList(0, shown));
      return keys.size() > shown ? joined + ", ... (+" + (keys.size() - shown) + ")" : joined;
   }

   /**
    * Diffs the active language file against the copy bundled in the jar and logs the
    * keys the operator's file is missing.
    *
    * @return the missing keys, in file order; empty when the file is complete or no
    *         bundled reference is available
    */
   public static List<String> audit() {
      List<String> missing = new ArrayList<>();
      if (Main.JavaPlugin == null || Variable.Lang_YML == null) {
         return missing;
      }

      String active = activeLanguageName();
      YamlConfiguration bundled = loadBundled(active);
      String referenceName = active;
      if (bundled == null) {
         bundled = loadBundled(REFERENCE_LANGUAGE);
         referenceName = REFERENCE_LANGUAGE;
      }
      if (bundled == null) {
         return missing;
      }

      for (String key : bundled.getKeys(true)) {
         if (bundled.isConfigurationSection(key)) {
            continue;
         }
         if (!Variable.Lang_YML.contains(key)) {
            missing.add(key);
         }
      }

      if (!missing.isEmpty()) {
         java.util.logging.Logger log = Main.JavaPlugin.getLogger();
         log.warning(
            "Language file " + active + ".yml is missing " + missing.size() + " key(s) present in the bundled "
               + referenceName + ".yml. Commands using them fall back to built-in text."
         );
         int shown = Math.min(missing.size(), 25);
         for (int i = 0; i < shown; i++) {
            log.warning("  missing language key: " + missing.get(i));
         }
         if (missing.size() > shown) {
            log.warning("  ... and " + (missing.size() - shown) + " more. Delete the file to regenerate it.");
         }
      }
      return missing;
   }

   private static String activeLanguageName() {
      if (Main.JavaPlugin == null) {
         return REFERENCE_LANGUAGE;
      }
      String configured = Main.JavaPlugin.getConfig().getString("Language");
      return configured == null || configured.trim().isEmpty() ? REFERENCE_LANGUAGE : configured.trim();
   }

   private static YamlConfiguration loadBundled(String languageName) {
      try (InputStream stream = Main.JavaPlugin.getResource("Language/" + languageName + ".yml")) {
         if (stream == null) {
            return null;
         }
         return YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
      } catch (Exception failure) {
         Main.JavaPlugin.getLogger().warning("Could not read bundled language file " + languageName + ".yml: " + failure);
         return null;
      }
   }
}
