package com.Util;

import com.ErrorTown.Main;
import com.ErrorTown.Variable;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

/**
 * Failure-tolerant helpers for the GUI layer.
 *
 * <p><b>Why this exists.</b> The GUI classes contained roughly forty
 * {@code try { ... } catch (Exception e) { }} blocks wrapping three recurring
 * operations: PlaceholderAPI substitution, skull-owner assignment, and parsing a
 * home's icon string. Each empty block hid a real failure mode — a broken
 * placeholder, an absent PlaceholderAPI, or a mistyped material in a home's data —
 * and turned it into "the GUI silently renders wrong".</p>
 *
 * <p>Every method here keeps the original lenient behaviour (a cosmetic failure must
 * never break a menu) but reports the cause once per distinct signature, so the
 * operator learns about a bad config value without the console being flooded.</p>
 *
 * <p>{@link #papi} deliberately catches {@link Throwable}: when PlaceholderAPI is not
 * installed the JVM raises {@link NoClassDefFoundError}, which is an {@code Error} and
 * would slip past {@code catch (Exception)}. Catching it here is what actually makes
 * PlaceholderAPI optional for menu rendering. Fatal VM errors are rethrown.</p>
 */
public final class GuiSafe {
   private static final Set<String> REPORTED = ConcurrentHashMap.newKeySet();
   private static final int MAX_REPORTED = 256;

   private GuiSafe() {
   }

   /**
    * Applies PlaceholderAPI substitution and then display formatting.
    *
    * <p>Formatting runs <i>after</i> substitution on purpose, so a placeholder may itself expand to
    * MiniMessage or {@code &} colour codes. See {@link Text}.</p>
    *
    * @param viewer may be {@code null} for server-context placeholders
    * @return the substituted, formatted text; on substitution failure the text is still formatted
    */
   public static String papi(OfflinePlayer viewer, String text) {
      return Text.format(substitute(viewer, text));
   }

   /** PlaceholderAPI substitution only, without display formatting. */
   public static String substitute(OfflinePlayer viewer, String text) {
      if (text == null || text.isEmpty()) {
         return text;
      }
      try {
         if (viewer instanceof Player online) {
            return me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(online, text);
         }
         return me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(viewer, text);
      } catch (Throwable failure) {
         rethrowIfFatal(failure);
         reportOnce("papi:" + failure.getClass().getName(), "PlaceholderAPI substitution failed; showing raw text", failure);
         return text;
      }
   }

   /** Convenience overload for the many call sites that have no viewer. */
   public static String papi(String text) {
      return papi(null, text);
   }

   /**
    * Applies PlaceholderAPI substitution and display formatting to every line of a lore list.
    *
    * @return a new list with substitutions and formatting applied
    */
   public static java.util.List<String> papi(OfflinePlayer viewer, java.util.List<String> lines) {
      if (lines == null || lines.isEmpty()) {
         return lines;
      }
      try {
         if (viewer instanceof Player online) {
            return Text.format(me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(online, lines));
         }
         return Text.format(me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(viewer, lines));
      } catch (Throwable failure) {
         rethrowIfFatal(failure);
         reportOnce("papi-list:" + failure.getClass().getName(), "PlaceholderAPI lore substitution failed; showing raw text", failure);
         return Text.format(lines);
      }
   }

   /**
    * Sets the skull texture owner.
    *
    * @return true when the owner was applied; false leaves the default skull texture
    */
   public static boolean setSkullOwner(SkullMeta meta, OfflinePlayer owner) {
      if (meta == null || owner == null) {
         return false;
      }
      try {
         meta.setOwningPlayer(owner);
         return true;
      } catch (Throwable failure) {
         rethrowIfFatal(failure);
         reportOnce("skull:" + failure.getClass().getName(), "Could not apply skull owner " + owner.getName(), failure);
         return false;
      }
   }

   /**
    * Applies a home icon specification to {@code item}.
    *
    * <p>Accepts {@code MATERIAL}, the legacy {@code MATERIAL:durability} form, and — when CraftEngine
    * is installed — a custom item id such as {@code myfurniture:oak_chair}.</p>
    *
    * @return true when the icon was applied; false leaves the item untouched
    */
   @SuppressWarnings("deprecation") // legacy icon specs carry a durability suffix
   public static boolean applyIcon(ItemStack item, String iconSpec) {
      if (item == null || iconSpec == null || iconSpec.trim().isEmpty()) {
         return false;
      }
      String spec = iconSpec.trim();
      ItemStack custom = CraftEngineBridge.item(spec, null);
      if (custom != null) {
         item.setType(custom.getType());
         item.setItemMeta(custom.getItemMeta());
         return true;
      }

      String[] parts = spec.split(":");
      Material material = Material.matchMaterial(parts[0].trim());
      if (material == null) {
         reportOnce("icon-material:" + parts[0], "Home icon '" + iconSpec + "' names an unknown material", null);
         return false;
      }
      item.setType(material);
      if (parts.length > 1) {
         try {
            item.setDurability(Short.parseShort(parts[1].trim()));
         } catch (NumberFormatException invalid) {
            reportOnce("icon-durability:" + iconSpec, "Home icon '" + iconSpec + "' has a non-numeric durability", null);
         }
      }
      return true;
   }

   /**
    * Resolves a material name from configuration.
    *
    * @return the configured material, or {@code fallback} when the name is unknown
    */
   public static Material material(String configuredName, Material fallback) {
      if (configuredName == null || configuredName.trim().isEmpty()) {
         return fallback;
      }
      Material resolved = Material.matchMaterial(configuredName.trim());
      if (resolved != null) {
         return resolved;
      }
      reportOnce("material:" + configuredName, "Configured material '" + configuredName + "' is unknown; using " + fallback, null);
      return fallback;
   }

   /**
    * Resolves a menu's inventory size from {@code GUI.yml}.
    *
    * <p>Menu sizes were hard-coded at 27, 45 or 54 slots. An operator who wanted a smaller menu, or
    * room for more buttons, had no way to say so. The key holds a <b>row count</b> (1-6) because that
    * is how players think about chest menus; a raw slot count that is already a valid multiple of nine
    * is accepted too, so either style works.</p>
    *
    * <p>Shrinking a menu below the highest configured {@code Index} would silently drop buttons, so
    * the value is only ever used as given — the button loops already skip out-of-range slots and
    * report it.</p>
    *
    * @param key      e.g. {@code MainSize}
    * @param fallback the historical size, used when the key is absent or nonsense
    * @return a slot count that is a multiple of nine, between 9 and 54
    */
   public static int size(String key, int fallback) {
      if (Variable.GUI_YML == null || !Variable.GUI_YML.contains(key)) {
         return fallback;
      }
      int configured = Variable.GUI_YML.getInt(key, 0);
      if (configured >= 1 && configured <= 6) {
         return configured * 9;
      }
      if (configured >= 9 && configured <= 54 && configured % 9 == 0) {
         return configured;
      }
      reportOnce(
         "gui-size:" + key,
         "GUI size '" + key + "' must be 1-6 rows (or a multiple of 9 up to 54) but is " + configured + "; using " + fallback,
         null
      );
      return fallback;
   }

   /** Resolves a {@code GUI.yml} material key with a fallback. */
   public static Material guiMaterial(String key, Material fallback) {
      if (Variable.GUI_YML == null) {
         return fallback;
      }
      return material(Variable.GUI_YML.getString(key), fallback);
   }

   /**
    * Resolves a menu title from {@code GUI.yml}.
    *
    * <p>Several menus — biome, rules, upgrade, set-spawn, cost and home-selection — had their titles
    * hard-coded in Java and could not be translated or restyled at all. They read a key now, with the
    * historical text as the fallback, so an existing install looks unchanged until the operator edits
    * it. Titles go through {@link Text}, so MiniMessage and {@code &} codes work here too.</p>
    */
   public static String title(String key, String fallback) {
      if (Variable.GUI_YML == null) {
         return Text.format(fallback);
      }
      String configured = Variable.GUI_YML.getString(key);
      if (configured == null || configured.trim().isEmpty()) {
         return Text.format(fallback);
      }
      return Text.format(configured);
   }

   private static void reportOnce(String signature, String message, Throwable failure) {
      if (Main.JavaPlugin == null || REPORTED.size() >= MAX_REPORTED || !REPORTED.add(signature)) {
         return;
      }
      if (failure == null) {
         Main.JavaPlugin.getLogger().warning(message);
      } else {
         Main.JavaPlugin.getLogger().log(Level.WARNING, message + " (" + failure + ")");
      }
   }

   /**
    * Rethrows errors that must never be swallowed.
    *
    * <p>{@link NoClassDefFoundError} is intentionally <em>not</em> fatal here: it is the
    * signal that an optional plugin such as PlaceholderAPI is absent, which callers
    * handle by falling back to raw text.</p>
    */
   private static void rethrowIfFatal(Throwable failure) {
      if (failure instanceof VirtualMachineError || failure instanceof ThreadDeath) {
         throw (Error)failure;
      }
   }
}
