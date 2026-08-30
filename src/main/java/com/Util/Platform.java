package com.Util;

import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.entity.Item;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Single place where "which server am I on?" is answered.
 *
 * <p><b>Why this exists.</b> ErrorTown ships one jar for Spigot, Paper, Purpur and Leaves across
 * Minecraft 1.21 through 26.2. The union of those APIs is not the API of any single one of them:
 *
 * <ul>
 *   <li>Minecraft 1.21.11 moved game rules into a registry and renamed all of them, and Spigot
 *       renamed its {@code GameRule} constants to match while Paper kept the old ones. No
 *       {@code GameRule.X} constant is therefore safe to name in source, and {@code doFireTick}
 *       was deleted outright. Game rules are resolved by name at runtime here.</li>
 *   <li>{@code World#setGameRuleValue(String, String)} and {@code World#getGameRuleValue(String)}
 *       exist on 1.21 but were <b>removed in 26.2</b>.</li>
 *   <li>{@code WorldCreator#keepSpawnInMemory(boolean)} exists on Spigot but was
 *       <b>removed from Paper 26.2</b> in favour of {@code keepSpawnLoaded(TriState)}, which
 *       Spigot in turn does not have. Neither can be named directly, so both are reflective.</li>
 *   <li>{@code Location#toCenterLocation()}, {@code World#getChunkAtAsync(...)} and the
 *       {@code Item} pickup/age setters are Paper-only.</li>
 * </ul>
 *
 * <p>The production classpath is deliberately the <i>oldest</i> supported API (Spigot 1.21) so that
 * a call the oldest server cannot serve is a compile error rather than a runtime
 * {@code NoSuchMethodError}. Anything outside that floor has to come through this class. The
 * {@code apiCheck} Gradle tasks recompile the same sources against the newest release of each
 * platform to catch the opposite direction — removals.</p>
 *
 * <p><b>Folia is not supported.</b> Folia has no world load/unload API and has permanently removed
 * synchronous {@code Entity#teleport}, both of which this plugin is built on. Folia is detected
 * only so {@link #isSupportedPlatform()} can report it and the operator gets a clear message
 * instead of a stack trace on the first home creation.</p>
 */
public final class Platform {
   /**
    * Chunks force-loaded per tick when falling back to the synchronous path. Generating a 96x96
    * home means up to 49 chunks; doing them all in one tick freezes the server for seconds, so
    * the fallback is paced. Paper servers never reach this path.
    */
   private static final int SYNC_CHUNKS_PER_TICK = 4;

   private static final boolean FOLIA = anyClassPresent("io.papermc.paper.threadedregions.RegionizedServer");
   private static final boolean PAPER = anyClassPresent(
      "io.papermc.paper.ServerBuildInfo",
      "io.papermc.paper.configuration.GlobalConfiguration",
      "com.destroystokyo.paper.PaperConfig"
   );
   private static final boolean PURPUR = anyClassPresent("org.purpurmc.purpur.PurpurConfig");
   private static final boolean LEAVES = anyClassPresent(
      "org.leavesmc.leaves.LeavesConfig",
      "top.leavesmc.leaves.LeavesConfig"
   );

   /** Paper: {@code WorldCreator keepSpawnLoaded(TriState)}. Absent on Spigot. */
   private static final Method CREATOR_KEEP_SPAWN_LOADED = optionalMethod(WorldCreator.class, "keepSpawnLoaded", triStateClass());
   /** Spigot: {@code WorldCreator keepSpawnInMemory(boolean)}. Removed in Paper 26.2. */
   private static final Method CREATOR_KEEP_SPAWN_IN_MEMORY = optionalMethod(WorldCreator.class, "keepSpawnInMemory", boolean.class);
   private static final Object TRI_STATE_TRUE = triStateConstant("TRUE");
   private static final Object TRI_STATE_FALSE = triStateConstant("FALSE");

   /** Paper: {@code CompletableFuture<Chunk> getChunkAtAsync(int, int, boolean)}. */
   private static final Method WORLD_CHUNK_AT_ASYNC = optionalMethod(World.class, "getChunkAtAsync", int.class, int.class, boolean.class);

   private static final Method ITEM_SET_WILL_AGE = optionalMethod(Item.class, "setWillAge", boolean.class);
   private static final Method ITEM_SET_CAN_MOB_PICKUP = optionalMethod(Item.class, "setCanMobPickup", boolean.class);
   private static final Method ITEM_SET_CAN_PLAYER_PICKUP = optionalMethod(Item.class, "setCanPlayerPickup", boolean.class);

   private static final Deque<PendingChunk> SYNC_CHUNK_QUEUE = new ArrayDeque<>();
   private static BukkitTask syncChunkTask;

   private Platform() {
   }

   // ------------------------------------------------------------------ identity

   public static boolean isPaper() {
      return PAPER;
   }

   public static boolean isFolia() {
      return FOLIA;
   }

   /** {@code false} only on Folia, whose missing world and teleport APIs this plugin cannot work around. */
   public static boolean isSupportedPlatform() {
      return !FOLIA;
   }

   /** Most specific fork name first, because every fork here is also "Paper". */
   public static String flavor() {
      if (FOLIA) {
         return "Folia";
      }
      if (LEAVES) {
         return "Leaves";
      }
      if (PURPUR) {
         return "Purpur";
      }
      if (PAPER) {
         return "Paper";
      }
      return "Spigot/CraftBukkit";
   }

   /** One line for the startup self-check, e.g. {@code Purpur | MC 26.2-R0.1-SNAPSHOT | Java 25}. */
   public static String describe() {
      return flavor()
         + " | MC " + Bukkit.getBukkitVersion()
         + " | Java " + System.getProperty("java.version", "?");
   }

   // ------------------------------------------------------------------ game rules

   /**
    * Fallback used when the server will not report the default of
    * {@code fire_spread_radius_around_player}. 128 is the vanilla default.
    */
   private static final int FIRE_SPREAD_RADIUS_FALLBACK_DEFAULT = 128;

   /** Normalized rule name to the rule this server actually exposes. Built once, on first use. */
   private static volatile Map<String, GameRule<?>> ruleIndex;

   /**
    * Sets a game rule by name, across every naming scheme in the supported version range.
    *
    * <p>The name may be given in either the 1.21 {@code camelCase} or the 1.21.11+
    * {@code minecraft:snake_case} form; both resolve on both. The rule name and value can come from
    * operator config, so an unknown rule or a malformed value is reported once and ignored rather
    * than thrown.</p>
    *
    * @return whether the rule was applied
    * @see GameRuleNames
    */
   public static boolean setGameRule(World world, String rule, String value) {
      if (world == null || rule == null || value == null) {
         return false;
      }
      String text = value.trim();
      GameRule<?> resolved = findRule(rule);

      if (resolved == null && GameRuleNames.isLegacyFireTickRule(rule)) {
         return setFireSpreadFromBoolean(world, rule, text);
      }
      if (resolved == null) {
         Diag.warnOnce("gamerule-unknown-" + GameRuleNames.normalize(rule), "This server has no game rule '" + rule + "'; left unchanged");
         return false;
      }

      Class<?> type = resolved.getType();
      if (type == Boolean.class) {
         Boolean parsed = parseBoolean(text, rule);
         if (parsed == null) {
            return false;
         }
         boolean effective = GameRuleNames.isInverted(rule, resolved.getName()) ? !parsed : parsed;
         return world.setGameRule(narrow(resolved), Boolean.valueOf(effective));
      }
      if (type == Integer.class) {
         try {
            return world.setGameRule(narrow(resolved), Integer.valueOf(text));
         } catch (NumberFormatException malformed) {
            Diag.warnOnce("gamerule-bad-int-" + GameRuleNames.normalize(rule), "Game rule '" + rule + "' expects a number but got '" + text + "'");
            return false;
         }
      }
      Diag.warnOnce("gamerule-type-" + GameRuleNames.normalize(rule), "Game rule '" + rule + "' has unsupported type " + type.getName());
      return false;
   }

   /**
    * Reads a game rule by name, accepting either naming scheme.
    *
    * @return the current value as text, or {@code ""} when the world or rule is unknown — never
    *         {@code null}, because the placeholder call sites compare with
    *         {@code equalsIgnoreCase}. Boolean rules whose meaning was inverted by the 1.21.11
    *         rename are reported in the sense of the name that was asked for.
    */
   public static String gameRuleValue(World world, String rule) {
      if (world == null || rule == null) {
         return "";
      }
      GameRule<?> resolved = findRule(rule);

      if (resolved == null && GameRuleNames.isLegacyFireTickRule(rule)) {
         return fireSpreadAsBoolean(world);
      }
      if (resolved == null) {
         Diag.warnOnce("gamerule-unknown-" + GameRuleNames.normalize(rule), "This server has no game rule '" + rule + "'; reported as empty");
         return "";
      }

      Object value = world.getGameRuleValue(resolved);
      if (value == null) {
         return "";
      }
      if (value instanceof Boolean flag && GameRuleNames.isInverted(rule, resolved.getName())) {
         return String.valueOf(!flag);
      }
      return String.valueOf(value);
   }

   /**
    * Maps the deleted boolean {@code doFireTick} onto {@code fire_spread_radius_around_player}.
    *
    * <p>1.21.11 replaced the boolean with a radius, so there is no faithful translation. ErrorTown
    * reads "no fire spread" as radius {@code 0} and "fire spread" as the rule's own default, which
    * is what the home fire toggle means to a player.</p>
    */
   private static boolean setFireSpreadFromBoolean(World world, String requestedName, String text) {
      GameRule<?> radius = findRule(GameRuleNames.FIRE_SPREAD_RADIUS);
      if (radius == null || radius.getType() != Integer.class) {
         Diag.warnOnce(
            "gamerule-firetick-missing",
            "This server has neither '" + requestedName + "' nor an integer fire spread radius rule; fire spread left unchanged"
         );
         return false;
      }
      Boolean parsed = parseBoolean(text, requestedName);
      if (parsed == null) {
         return false;
      }
      GameRule<Integer> typed = narrow(radius);
      int applied = 0;
      if (parsed) {
         Integer serverDefault = world.getGameRuleDefault(typed);
         applied = serverDefault == null ? FIRE_SPREAD_RADIUS_FALLBACK_DEFAULT : serverDefault;
      }
      return world.setGameRule(typed, Integer.valueOf(applied));
   }

   /** Reports {@code fire_spread_radius_around_player} as the boolean {@code doFireTick} used to be. */
   private static String fireSpreadAsBoolean(World world) {
      GameRule<?> radius = findRule(GameRuleNames.FIRE_SPREAD_RADIUS);
      if (radius == null || radius.getType() != Integer.class) {
         return "";
      }
      Integer value = world.getGameRuleValue(Platform.<Integer>narrow(radius));
      return value == null ? "" : String.valueOf(value != 0);
   }

   private static Boolean parseBoolean(String text, String ruleName) {
      if (text.equalsIgnoreCase("true")) {
         return Boolean.TRUE;
      }
      if (text.equalsIgnoreCase("false")) {
         return Boolean.FALSE;
      }
      Diag.warnOnce(
         "gamerule-bad-bool-" + GameRuleNames.normalize(ruleName),
         "Game rule '" + ruleName + "' expects true/false but got '" + text + "'"
      );
      return null;
   }

   /** @return the rule this server exposes for any accepted spelling of {@code requestedName}, else {@code null} */
   private static GameRule<?> findRule(String requestedName) {
      Map<String, GameRule<?>> index = ruleIndex();
      for (String candidate : GameRuleNames.candidates(requestedName)) {
         GameRule<?> found = index.get(candidate);
         if (found != null) {
            return found;
         }
      }
      if (index.isEmpty()) {
         // GameRule.values() is deprecated for removal on Paper 26.2. If a future server drops it,
         // exact-name lookup still works — cross-scheme aliasing is what is lost, hence the warning.
         Diag.warnOnce(
            "gamerule-index-empty",
            "This server exposes no enumerable game rules; falling back to exact-name lookup, so "
               + "game rule names must match this Minecraft version exactly"
         );
         try {
            return GameRule.getByName(requestedName);
         } catch (RuntimeException unavailable) {
            Diag.warnOnce("gamerule-getbyname", "GameRule.getByName failed on this server", unavailable);
         }
      }
      return null;
   }

   /**
    * Indexes every rule this server exposes by normalized name.
    *
    * <p>{@code GameRule.getByName} is not enough on its own: it matches only the exact name the
    * running server uses, so a 1.21-era {@code "doMobSpawning"} misses on 26.2 and a modern
    * {@code "spawn_mobs"} misses on 1.21.</p>
    */
   private static Map<String, GameRule<?>> ruleIndex() {
      Map<String, GameRule<?>> cached = ruleIndex;
      if (cached != null) {
         return cached;
      }
      Map<String, GameRule<?>> built = new HashMap<>();
      try {
         for (GameRule<?> rule : GameRule.values()) {
            if (rule != null && rule.getName() != null) {
               built.putIfAbsent(GameRuleNames.normalize(rule.getName()), rule);
            }
         }
      } catch (RuntimeException | LinkageError unavailable) {
         built.clear();
         Diag.warnOnce("gamerule-values", "GameRule.values() is unavailable on this server", unavailable instanceof RuntimeException runtime ? runtime : null);
      }
      Map<String, GameRule<?>> immutable = Map.copyOf(built);
      ruleIndex = immutable;
      return immutable;
   }

   /**
    * Narrows a wildcard rule to its own type parameter.
    *
    * <p>Unchecked, but not unsound: every caller has already compared {@link GameRule#getType()}
    * against the class it is about to pass as the value.</p>
    */
   @SuppressWarnings("unchecked")
   private static <T> GameRule<T> narrow(GameRule<?> rule) {
      return (GameRule<T>)rule;
   }

   // ------------------------------------------------------------------ world creation

   /**
    * Applies the spawn-chunk policy to a creator without naming either version's method.
    *
    * <p>Paper 26.2 removed {@code keepSpawnInMemory(boolean)}; Spigot never had
    * {@code keepSpawnLoaded(TriState)}. When neither is reachable the server default stands,
    * which only costs a few permanently loaded chunks per home.</p>
    */
   public static void keepSpawnLoaded(WorldCreator creator, boolean keepLoaded) {
      if (creator == null) {
         return;
      }
      Object triState = keepLoaded ? TRI_STATE_TRUE : TRI_STATE_FALSE;
      if (CREATOR_KEEP_SPAWN_LOADED != null && triState != null) {
         try {
            CREATOR_KEEP_SPAWN_LOADED.invoke(creator, triState);
            return;
         } catch (ReflectiveOperationException | RuntimeException unavailable) {
            Diag.warnOnce("platform-keep-spawn-loaded", "WorldCreator#keepSpawnLoaded failed; trying keepSpawnInMemory", unavailable);
         }
      }
      if (CREATOR_KEEP_SPAWN_IN_MEMORY != null) {
         try {
            CREATOR_KEEP_SPAWN_IN_MEMORY.invoke(creator, keepLoaded);
            return;
         } catch (ReflectiveOperationException | RuntimeException unavailable) {
            Diag.warnOnce("platform-keep-spawn-memory", "WorldCreator#keepSpawnInMemory failed", unavailable);
         }
      }
      Diag.warnOnce(
         "platform-keep-spawn-none",
         "This server exposes neither WorldCreator#keepSpawnLoaded nor #keepSpawnInMemory; "
            + "home spawn chunks keep the server default"
      );
   }

   // ------------------------------------------------------------------ locations

   /**
    * Paper's {@code Location#toCenterLocation()}, computed directly.
    *
    * <p>Returns the centre of the containing block and keeps the original yaw and pitch, matching
    * Paper's behaviour exactly. Pure arithmetic, so no capability check is needed.</p>
    */
   public static Location toCenterLocation(Location location) {
      if (location == null) {
         return null;
      }
      return new Location(
         location.getWorld(),
         location.getBlockX() + 0.5D,
         location.getBlockY() + 0.5D,
         location.getBlockZ() + 0.5D,
         location.getYaw(),
         location.getPitch()
      );
   }

   // ------------------------------------------------------------------ chunks

   /**
    * Loads and generates a chunk, off the main thread where the platform allows it.
    *
    * <p>On Paper this is {@code getChunkAtAsync}. On Spigot there is no async chunk API, so the
    * request joins a queue drained at {@value #SYNC_CHUNKS_PER_TICK} chunks per tick on the main
    * thread; the returned future still completes asynchronously from the caller's point of view,
    * it just takes more ticks.</p>
    *
    * @param plugin used to schedule the paced fallback; when {@code null} or disabled the chunk is
    *               loaded on the calling thread instead
    */
   public static CompletableFuture<Chunk> chunkAsync(Plugin plugin, World world, int chunkX, int chunkZ) {
      CompletableFuture<Chunk> result = new CompletableFuture<>();
      if (world == null) {
         result.completeExceptionally(new IllegalArgumentException("world is null"));
         return result;
      }
      if (WORLD_CHUNK_AT_ASYNC != null) {
         try {
            Object future = WORLD_CHUNK_AT_ASYNC.invoke(world, chunkX, chunkZ, true);
            if (future instanceof CompletableFuture) {
               return asChunkFuture((CompletableFuture<?>)future);
            }
         } catch (ReflectiveOperationException | RuntimeException unavailable) {
            Diag.warnOnce("platform-chunk-async", "World#getChunkAtAsync failed; loading chunks on the main thread", unavailable);
         }
      }
      return enqueueSyncChunkLoad(plugin, world, chunkX, chunkZ, result);
   }

   /** Paper declares the return type as {@code CompletableFuture<Chunk>}; reflection erases it. */
   @SuppressWarnings("unchecked")
   private static CompletableFuture<Chunk> asChunkFuture(CompletableFuture<?> future) {
      return (CompletableFuture<Chunk>)future;
   }

   private static CompletableFuture<Chunk> enqueueSyncChunkLoad(
      Plugin plugin,
      World world,
      int chunkX,
      int chunkZ,
      CompletableFuture<Chunk> result
   ) {
      if (plugin == null || !plugin.isEnabled()) {
         completeWithChunk(world, chunkX, chunkZ, result);
         return result;
      }
      synchronized (SYNC_CHUNK_QUEUE) {
         SYNC_CHUNK_QUEUE.add(new PendingChunk(world, chunkX, chunkZ, result));
         if (syncChunkTask == null) {
            syncChunkTask = Bukkit.getScheduler().runTaskTimer(plugin, Platform::drainSyncChunkQueue, 1L, 1L);
         }
      }
      return result;
   }

   private static void drainSyncChunkQueue() {
      for (int loaded = 0; loaded < SYNC_CHUNKS_PER_TICK; loaded++) {
         PendingChunk pending;
         synchronized (SYNC_CHUNK_QUEUE) {
            pending = SYNC_CHUNK_QUEUE.poll();
            if (pending == null) {
               if (syncChunkTask != null) {
                  syncChunkTask.cancel();
                  syncChunkTask = null;
               }
               return;
            }
         }
         completeWithChunk(pending.world, pending.chunkX, pending.chunkZ, pending.result);
      }
   }

   private static void completeWithChunk(World world, int chunkX, int chunkZ, CompletableFuture<Chunk> result) {
      try {
         result.complete(world.getChunkAt(chunkX, chunkZ));
      } catch (RuntimeException failure) {
         result.completeExceptionally(failure);
      }
   }

   /**
    * Releases the fallback chunk queue on plugin disable.
    *
    * <p>Pending futures are failed rather than dropped so that whoever is waiting on them runs its
    * error path instead of hanging until the JVM exits.</p>
    */
   public static void shutdown() {
      synchronized (SYNC_CHUNK_QUEUE) {
         if (syncChunkTask != null) {
            syncChunkTask.cancel();
            syncChunkTask = null;
         }
         PendingChunk pending;
         while ((pending = SYNC_CHUNK_QUEUE.poll()) != null) {
            pending.result.completeExceptionally(new IllegalStateException("ErrorTown is shutting down"));
         }
      }
   }

   // ------------------------------------------------------------------ dropped items

   /**
    * Stops a protected home drop from ageing out and keeps it pickable.
    *
    * <p>{@code setUnlimitedLifetime} is the part that matters and exists everywhere. The three
    * Paper-only setters are applied when present; on Spigot the unlimited lifetime alone already
    * freezes the despawn timer, so behaviour matches.</p>
    */
   public static void protectDroppedItem(Item item) {
      if (item == null) {
         return;
      }
      item.setUnlimitedLifetime(true);
      applyItemFlag(ITEM_SET_WILL_AGE, item, false, "item-will-age");
      applyItemFlag(ITEM_SET_CAN_MOB_PICKUP, item, true, "item-mob-pickup");
      applyItemFlag(ITEM_SET_CAN_PLAYER_PICKUP, item, true, "item-player-pickup");
   }

   private static void applyItemFlag(Method setter, Item item, boolean value, String signature) {
      if (setter == null) {
         // Spigot has no such setter; setUnlimitedLifetime already covers the despawn behaviour.
         return;
      }
      try {
         setter.invoke(item, value);
      } catch (ReflectiveOperationException | RuntimeException unavailable) {
         Diag.warnOnce("platform-" + signature, "Item#" + setter.getName() + " failed on this server", unavailable);
      }
   }

   // ------------------------------------------------------------------ materials

   /**
    * Pre-1.13 material names that operators still carry in old {@code config.yml} / {@code GUI.yml}
    * files, mapped to the modern name.
    *
    * <p>{@code Material.valueOf} throws on these, and the call sites that read them build GUI
    * items — an exception there breaks a whole menu. The plugin's own shipped configs use modern
    * names; this table exists for configs carried over from a 1.12-era server.</p>
    */
   private static final Map<String, String> LEGACY_MATERIAL_ALIASES = Map.ofEntries(
      Map.entry("SOIL", "FARMLAND"),
      Map.entry("SKULL_ITEM", "PLAYER_HEAD"),
      Map.entry("SKULL", "PLAYER_HEAD"),
      Map.entry("EXP_BOTTLE", "EXPERIENCE_BOTTLE"),
      Map.entry("INK_SACK", "INK_SAC"),
      Map.entry("ENDER_STONE", "END_STONE"),
      Map.entry("GRASS", "GRASS_BLOCK"),
      Map.entry("LONG_GRASS", "SHORT_GRASS"),
      Map.entry("STAINED_GLASS_PANE", "BLACK_STAINED_GLASS_PANE"),
      Map.entry("STAINED_GLASS", "BLACK_STAINED_GLASS"),
      Map.entry("THIN_GLASS", "GLASS_PANE"),
      Map.entry("WATCH", "CLOCK"),
      Map.entry("SIGN", "OAK_SIGN"),
      Map.entry("WOOD_DOOR", "OAK_DOOR"),
      Map.entry("SAPLING", "OAK_SAPLING"),
      Map.entry("WORKBENCH", "CRAFTING_TABLE"),
      Map.entry("ENCHANTMENT_TABLE", "ENCHANTING_TABLE"),
      Map.entry("BED", "RED_BED"),
      Map.entry("DOUBLE_PLANT", "SUNFLOWER"),
      Map.entry("WEB", "COBWEB"),
      Map.entry("REDSTONE_LAMP_OFF", "REDSTONE_LAMP"),
      Map.entry("GOLD_INGOT", "GOLD_INGOT")
   );

   /**
    * Resolves an operator-configured material name without ever throwing.
    *
    * <p>Tries the name as given (accepting the {@code minecraft:} namespaced form via
    * {@link Material#matchMaterial}), then the pre-1.13 alias table, then gives up and returns
    * {@code fallback} after reporting the bad value once. This is the only way config-driven
    * materials should be read: a raw {@code Material.valueOf} throws and takes the surrounding
    * GUI or listener down with it.</p>
    *
    * @param signature stable identity for the one-shot warning, e.g. {@code "gui-head-material"}
    */
   public static Material material(String configured, Material fallback, String signature) {
      if (configured != null && !configured.trim().isEmpty()) {
         String name = configured.trim();
         Material direct = Material.matchMaterial(name);
         if (direct != null) {
            return direct;
         }
         String modern = LEGACY_MATERIAL_ALIASES.get(name.toUpperCase(Locale.ROOT));
         if (modern != null) {
            Material aliased = Material.matchMaterial(modern);
            if (aliased != null) {
               Diag.warnOnce(
                  signature + "-legacy",
                  "Config material '" + name + "' is a pre-1.13 name; using '" + modern + "'. Please update your configuration."
               );
               return aliased;
            }
         }
         Diag.warnOnce(
            signature,
            "Config material '" + name + "' does not exist on this server (Minecraft " + Bukkit.getBukkitVersion()
               + "); using '" + fallback + "'"
         );
         return fallback;
      }
      Diag.warnOnce(signature + "-missing", "Config material for " + signature + " is unset; using '" + fallback + "'");
      return fallback;
   }

   // ------------------------------------------------------------------ reflection plumbing

   private static boolean anyClassPresent(String... names) {
      for (String name : names) {
         try {
            Class.forName(name);
            return true;
         } catch (ClassNotFoundException | LinkageError absent) {
            // Expected on servers that are not this fork; try the next candidate.
         }
      }
      return false;
   }

   private static Class<?> triStateClass() {
      try {
         return Class.forName("net.kyori.adventure.util.TriState");
      } catch (ClassNotFoundException | LinkageError absent) {
         // Spigot ships no Adventure; the keepSpawnInMemory path is used instead.
         return null;
      }
   }

   private static Object triStateConstant(String name) {
      Class<?> triState = triStateClass();
      if (triState == null) {
         return null;
      }
      try {
         return triState.getField(name).get(null);
      } catch (ReflectiveOperationException | RuntimeException absent) {
         Diag.warnOnce("platform-tristate-" + name, "TriState." + name + " is not readable on this server", absent);
         return null;
      }
   }

   /**
    * @return the public method, or {@code null} when this server's API does not declare it —
    *         which is the normal case for every method looked up here
    */
   private static Method optionalMethod(Class<?> owner, String name, Class<?>... parameterTypes) {
      for (Class<?> parameterType : parameterTypes) {
         if (parameterType == null) {
            return null;
         }
      }
      try {
         return owner.getMethod(name, parameterTypes);
      } catch (NoSuchMethodException | LinkageError absent) {
         return null;
      }
   }

   private static final class PendingChunk {
      private final World world;
      private final int chunkX;
      private final int chunkZ;
      private final CompletableFuture<Chunk> result;

      private PendingChunk(World world, int chunkX, int chunkZ, CompletableFuture<Chunk> result) {
         this.world = world;
         this.chunkX = chunkX;
         this.chunkZ = chunkZ;
         this.result = result;
      }
   }
}
