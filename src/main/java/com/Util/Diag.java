package com.Util;

import com.ErrorTown.Main;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Reporting helpers for failure paths that must stay non-fatal.
 *
 * <p><b>Why this exists.</b> The reconstructed source contained ~90 empty
 * {@code catch} blocks. Most of them wrap an operation that genuinely should not abort
 * the surrounding command or listener — saving a YAML file, parsing an operator-supplied
 * number, applying a cosmetic world rule. Deleting the {@code catch} would turn a
 * cosmetic problem into a broken command; leaving it empty means the operator never
 * learns that a save failed or a config value is malformed.</p>
 *
 * <p>These helpers keep the lenient control flow and make the cause visible.
 * {@link #warnOnce} is for anything that can repeat per tick, per block or per menu
 * render, so a persistent problem produces one line rather than a flooded console.</p>
 */
public final class Diag {
   private static final Set<String> REPORTED = ConcurrentHashMap.newKeySet();
   private static final int MAX_SIGNATURES = 512;

   private Diag() {
   }

   /** Logs every time. Use for one-shot operations such as a save on command. */
   public static void warn(String message, Throwable failure) {
      if (Main.JavaPlugin == null) {
         return;
      }
      if (failure == null) {
         Main.JavaPlugin.getLogger().warning(message);
      } else {
         Main.JavaPlugin.getLogger().log(Level.WARNING, message, failure);
      }
   }

   public static void warn(String message) {
      warn(message, null);
   }

   /**
    * Logs the first occurrence of {@code signature} and stays quiet afterwards.
    *
    * @param signature stable identity of the failure, e.g. {@code "biome-set"} or a
    *                  config key; keep it independent of per-call data
    */
   public static void warnOnce(String signature, String message, Throwable failure) {
      if (Main.JavaPlugin == null || signature == null) {
         return;
      }
      if (REPORTED.size() >= MAX_SIGNATURES || !REPORTED.add(signature)) {
         return;
      }
      warn(message + " (reported once)", failure);
   }

   public static void warnOnce(String signature, String message) {
      warnOnce(signature, message, null);
   }

   /**
    * Parses an operator-supplied integer.
    *
    * @return the parsed value, or {@code fallback} when the text is absent or malformed;
    *         a malformed value is reported once per {@code signature}
    */
   public static int parseInt(String text, int fallback, String signature, String what) {
      if (text == null || text.trim().isEmpty()) {
         return fallback;
      }
      try {
         return Integer.parseInt(text.trim());
      } catch (NumberFormatException invalid) {
         warnOnce(signature, what + " is not a number: '" + text.trim() + "'; using " + fallback);
         return fallback;
      }
   }

   /** Test hook so a fresh run reports again. */
   public static void resetForTests() {
      REPORTED.clear();
   }
}
