package com.Util;

import com.ErrorTown.Main;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;

/**
 * Superflat generator settings for type-2 ("超平坦" / superflat) homes.
 *
 * <p><b>Why this exists.</b> Creating a type-2 home logged
 * {@code No key layers in MapLike[{}]} once per creation. The world was still produced, which is why
 * the cause stayed unclear: {@code WorldCreator.type(WorldType.FLAT)} was set but
 * {@code generatorSettings} was left empty, so CraftBukkit handed vanilla an empty JSON object
 * {@code {}}. Vanilla's {@code flat_level_generator_settings} codec requires a {@code layers} entry,
 * failed to read one, logged that message and silently fell back to its built-in preset.</p>
 *
 * <p>Passing real settings removes the error and makes the layers explicit instead of depending on a
 * vanilla fallback that Mojang is free to change. The JSON is operator-overridable through
 * {@code Superflat.GeneratorSettings} for servers that want a different stack.</p>
 *
 * <p>The format has been stable since Minecraft 1.16 and is unchanged through 26.2.</p>
 */
public final class SuperflatPreset {
   /** Config key holding a raw {@code flat_level_generator_settings} JSON object. */
   public static final String SETTINGS_KEY = "Superflat.GeneratorSettings";

   /**
    * The classic superflat stack: bedrock, two dirt, one grass block, plains biome.
    *
    * <p>Structures and lakes are off because a bounded 8x8 to 96x96 home has no room for them, and
    * the home creation flow already drives {@code generateStructures} separately.</p>
    */
   public static final String DEFAULT_SETTINGS =
      "{\"biome\":\"minecraft:plains\","
         + "\"lakes\":false,"
         + "\"features\":false,"
         + "\"layers\":["
         + "{\"block\":\"minecraft:bedrock\",\"height\":1},"
         + "{\"block\":\"minecraft:dirt\",\"height\":2},"
         + "{\"block\":\"minecraft:grass_block\",\"height\":1}"
         + "]}";

   private SuperflatPreset() {
   }

   /**
    * Marks {@code creator} as superflat and gives it valid generator settings.
    *
    * @return the same creator, for chaining
    */
   public static WorldCreator apply(WorldCreator creator) {
      if (creator == null) {
         return null;
      }
      creator.type(WorldType.FLAT);
      creator.generatorSettings(settings());
      return creator;
   }

   /**
    * @return the configured {@code flat_level_generator_settings} JSON, or {@link #DEFAULT_SETTINGS}
    *         when unset or blank
    */
   public static String settings() {
      if (Main.JavaPlugin == null) {
         return DEFAULT_SETTINGS;
      }
      String configured = Main.JavaPlugin.getConfig().getString(SETTINGS_KEY);
      if (configured == null || configured.trim().isEmpty()) {
         return DEFAULT_SETTINGS;
      }
      String trimmed = configured.trim();
      if (!trimmed.contains("\"layers\"") && !trimmed.contains("layers")) {
         Diag.warnOnce(
            "superflat-settings",
            SETTINGS_KEY + " has no 'layers' entry, which Minecraft requires; using the built-in superflat preset"
         );
         return DEFAULT_SETTINGS;
      }
      return trimmed;
   }
}
