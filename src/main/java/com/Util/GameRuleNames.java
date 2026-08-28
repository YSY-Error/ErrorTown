package com.Util;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Game rule name translation between the naming schemes ErrorTown has to span.
 *
 * <p>Minecraft 1.21.11 moved game rules into a registry and rewrote every name from
 * {@code camelCase} to {@code minecraft:snake_case}. Server APIs followed inconsistently: Spigot
 * 26.2 renamed its {@code GameRule} constants to match, Paper 26.2 kept the historical ones. A
 * plugin that must run on both, and on 1.21 as well, therefore cannot name a {@code GameRule}
 * constant in source at all — it has to resolve rules by name at runtime.</p>
 *
 * <p>This class holds the pure naming logic so it is testable without a server;
 * {@link Platform} does the actual Bukkit lookups. Every name handled here is
 * <b>normalized</b>: lowercase, no {@code minecraft:} namespace, no underscores.</p>
 *
 * @see <a href="https://minecraft.wiki/w/Game_rule">Minecraft Wiki: Game rule (history)</a>
 */
public final class GameRuleNames {
   /**
    * Rules that were <b>renamed</b> in 1.21.11, not merely re-cased.
    *
    * <p>{@link #normalize} already absorbs the mechanical part of the rename — {@code keepInventory}
    * and {@code keep_inventory} normalize alike — so only word-for-word changes are listed.</p>
    */
   private static final Map<String, String> LEGACY_TO_MODERN = Map.ofEntries(
      Map.entry("announceadvancements", "showadvancementmessages"),
      Map.entry("commandblocksenabled", "commandblockswork"),
      Map.entry("commandmodificationblocklimit", "maxblockmodifications"),
      Map.entry("disableelytramovementcheck", "elytramovementcheck"),
      Map.entry("disableplayermovementcheck", "playermovementcheck"),
      Map.entry("disableraids", "raids"),
      Map.entry("dodaylightcycle", "advancetime"),
      Map.entry("doentitydrops", "entitydrops"),
      Map.entry("doimmediaterespawn", "immediaterespawn"),
      Map.entry("doinsomnia", "spawnphantoms"),
      Map.entry("dolimitedcrafting", "limitedcrafting"),
      Map.entry("domobloot", "mobdrops"),
      Map.entry("domobspawning", "spawnmobs"),
      Map.entry("dopatrolspawning", "spawnpatrols"),
      Map.entry("dotiledrops", "blockdrops"),
      Map.entry("dotraderspawning", "spawnwanderingtraders"),
      Map.entry("dovinesspread", "spreadvines"),
      Map.entry("dowardenspawning", "spawnwardens"),
      Map.entry("doweathercycle", "advanceweather"),
      Map.entry("maxcommandchainlength", "maxcommandsequencelength"),
      Map.entry("maxcommandforkcount", "maxcommandforks"),
      Map.entry("naturalregeneration", "naturalhealthregeneration"),
      Map.entry("snowaccumulationheight", "maxsnowaccumulationheight"),
      Map.entry("spawnradius", "respawnradius"),
      Map.entry("spawnerblocksenabled", "spawnerblockswork")
   );

   private static final Map<String, String> MODERN_TO_LEGACY = invert(LEGACY_TO_MODERN);

   /**
    * Renames that also <b>flipped the meaning</b> of the value: {@code disableRaids=true} says the
    * same thing as {@code raids=false}. Keyed by the legacy name.
    */
   private static final Set<String> INVERTED_BY_RENAME = Set.of(
      "disableelytramovementcheck",
      "disableplayermovementcheck",
      "disableraids"
   );

   /**
    * The boolean fire rules 1.21.11 <b>deleted</b> rather than renamed.
    *
    * <p>Both were replaced by the integer {@link #FIRE_SPREAD_RADIUS}, so there is no name mapping
    * for them — only the value shim in {@link Platform}.</p>
    */
   private static final Set<String> LEGACY_FIRE_TICK_RULES = Set.of("dofiretick", "allowfireticksawayfromplayer");

   /** Normalized name of {@code minecraft:fire_spread_radius_around_player} (integer, default 128). */
   public static final String FIRE_SPREAD_RADIUS = "firespreadradiusaroundplayer";

   private GameRuleNames() {
   }

   /**
    * Folds away everything the 1.21.11 rename changed mechanically: the {@code minecraft:}
    * namespace, letter case, and the {@code _} word separators.
    *
    * <p>{@code doMobSpawning}, {@code DoMobSpawning} and {@code minecraft:do_mob_spawning} all
    * become {@code domobspawning}.</p>
    *
    * @return the normalized name, or {@code ""} for {@code null}
    */
   public static String normalize(String name) {
      if (name == null) {
         return "";
      }
      String trimmed = name.trim().toLowerCase(Locale.ROOT);
      int namespace = trimmed.indexOf(':');
      if (namespace >= 0) {
         trimmed = trimmed.substring(namespace + 1);
      }
      return trimmed.replace("_", "");
   }

   /**
    * Every normalized name a server might expose for the requested rule, most likely first.
    *
    * <p>The requested name itself comes first, then its counterpart across the 1.21.11 rename in
    * whichever direction applies. Callers try each against the rules the server actually has.</p>
    */
   public static Set<String> candidates(String requestedName) {
      String requested = normalize(requestedName);
      Set<String> ordered = new LinkedHashSet<>(3);
      if (requested.isEmpty()) {
         return Set.of();
      }
      ordered.add(requested);
      String modern = LEGACY_TO_MODERN.get(requested);
      if (modern != null) {
         ordered.add(modern);
      }
      String legacy = MODERN_TO_LEGACY.get(requested);
      if (legacy != null) {
         ordered.add(legacy);
      }
      return ordered;
   }

   /**
    * Whether a boolean read or written under {@code requestedName} has to be flipped because it was
    * answered by {@code resolvedName}.
    *
    * <p>{@code disableRaids} and {@code raids} are the same switch with opposite senses, so asking
    * for one and getting the other means inverting the value. Asking for a name and getting that
    * same name never inverts.</p>
    */
   public static boolean isInverted(String requestedName, String resolvedName) {
      String requested = normalize(requestedName);
      String resolved = normalize(resolvedName);
      if (requested.equals(resolved)) {
         return false;
      }
      return INVERTED_BY_RENAME.contains(requested) || INVERTED_BY_RENAME.contains(resolved);
   }

   /**
    * Whether this is one of the boolean fire rules that 1.21.11 removed outright.
    *
    * <p>Callers should only reach for the {@link #FIRE_SPREAD_RADIUS} shim when the requested rule
    * is one of these <i>and</i> the server does not actually expose it.</p>
    */
   public static boolean isLegacyFireTickRule(String requestedName) {
      return LEGACY_FIRE_TICK_RULES.contains(normalize(requestedName));
   }

   private static Map<String, String> invert(Map<String, String> forward) {
      java.util.Map<String, String> reversed = new java.util.HashMap<>(forward.size());
      forward.forEach((legacy, modern) -> reversed.putIfAbsent(modern, legacy));
      return Map.copyOf(reversed);
   }
}
