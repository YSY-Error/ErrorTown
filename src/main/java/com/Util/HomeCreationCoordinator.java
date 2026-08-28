package com.Util;

import com.ErrorTown.Variable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class HomeCreationCoordinator {
   private static final HomeCreationQueue CREATION_QUEUE = new HomeCreationQueue();
   private static final long REAP_INTERVAL_TICKS = 20L * 30L;
   private static Integer reaperTaskId;
   private static final ThreadLocal<HomeCreationQueue.CreationRequest> DISPATCHED_REQUEST = new ThreadLocal<>();

   private HomeCreationCoordinator() {
   }

   /**
    * Starts the periodic stale-slot sweep. Safe to call more than once.
    *
    * <p>Creation concurrency is hard-capped at two slots and the per-request
    * {@code runTaskLater} timeout does not survive a plugin reload, so without this
    * sweep two leaked entries deadlock home creation for the whole server.</p>
    */
   public static synchronized void startReaper(JavaPlugin plugin) {
      if (plugin == null || reaperTaskId != null) {
         return;
      }
      reaperTaskId = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
         long limit = staleMillis(plugin);
         for (HomeCreationQueue.CreationRequest request : CREATION_QUEUE.reapStale(limit)) {
            Player creator = Bukkit.getPlayer(request.getPlayerId());
            plugin.getLogger().warning(
               "Reclaimed a stuck home creation slot for '" + request.getHomeName() + "' after " + (limit / 1000L) + "s."
            );
            refundAndNotify(creator, request.getPlayerName(), request.getHomeName(), messages().text("HomeCreationTimeout", "家园创建等待超时"));
         }
         CreateCostLedger.retryPendingRefunds();
         startAvailable(plugin, configuredLimit(plugin));
      }, REAP_INTERVAL_TICKS, REAP_INTERVAL_TICKS).getTaskId();
   }

   public static synchronized void stopReaper() {
      if (reaperTaskId != null) {
         Bukkit.getScheduler().cancelTask(reaperTaskId);
         reaperTaskId = null;
      }
   }

   public static synchronized int enqueue(JavaPlugin plugin, Player player, String homeName, String command, int configuredLimit) {
      if (plugin == null || player == null || homeName == null || command == null) {
         return -1;
      }
      int position = CREATION_QUEUE.enqueue(new HomeCreationQueue.CreationRequest(player.getUniqueId(), player.getName(), homeName, command));
      startReaper(plugin);
      startAvailable(plugin, configuredLimit);
      return position;
   }

   public static String queuePositionMessage(int position) {
      return messages().format(
         "HomeCreationQueueEntered",
         "§8[§6错误庄园§8] §e家园已进入低负载创建队列，当前位置: §f<position>",
         "<position>",
         Integer.toString(position)
      );
   }

   public static String queueWaitingMessage() {
      return messages().text("HomeCreationQueueWaiting", "§8[§6错误庄园§8] §7地形准备完成前无法进入家园，请耐心等待。");
   }

   public static String worldCreationFailedMessage() {
      return messages().text("HomeCreationWorldFailed", "主世界地形创建失败");
   }

   public static synchronized boolean consumeAdmission(HomeCreationQueue.CreationRequest request) {
      return CREATION_QUEUE.consumeAdmission(request);
   }

   /** Request token for the command currently dispatched by this coordinator. */
   public static HomeCreationQueue.CreationRequest currentRequest(String homeName) {
      HomeCreationQueue.CreationRequest request = DISPATCHED_REQUEST.get();
      return request != null && request.getHomeName().equalsIgnoreCase(homeName) ? request : null;
   }

   public static synchronized void markReady(JavaPlugin plugin, String homeName) {
      complete(plugin, homeName);
   }

   public static synchronized boolean isPending(String homeName) {
      return CREATION_QUEUE.isPending(homeName);
   }

   public static synchronized boolean hasFailed(String homeName) {
      return CREATION_QUEUE.hasFailed(homeName);
   }

   public static void prepareInitialArea(
      JavaPlugin plugin,
      String homeName,
      World world,
      Player creator,
      int initialSize,
      boolean permanentDay,
      long dayTime,
      Runnable completion
   ) {
      HomeCreationQueue.CreationRequest request = currentRequest(homeName);
      if (plugin == null || homeName == null || world == null) {
         fail(plugin, request, homeName, creator, messages().text("HomeCreationWorldInitFailed", "世界初始化失败"));
         return;
      }

      applyWorldPolicy(world, initialSize, permanentDay, dayTime);
      Location spawn = world.getSpawnLocation();
      int radius = Math.max(1, HomeTerrainPolicy.clampSize(initialSize) / 2);
      int minChunkX = ((int)Math.floor(spawn.getX() - radius)) >> 4;
      int maxChunkX = ((int)Math.floor(spawn.getX() + radius - 1)) >> 4;
      int minChunkZ = ((int)Math.floor(spawn.getZ() - radius)) >> 4;
      int maxChunkZ = ((int)Math.floor(spawn.getZ() + radius - 1)) >> 4;
      List<CompletableFuture<Chunk>> futures = new ArrayList<>();

      for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
         for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
            // Paper loads chunks off-thread; Spigot has no async chunk API, so Platform paces the
            // synchronous loads across ticks instead of freezing the server on a 96x96 home.
            futures.add(Platform.chunkAsync(plugin, world, chunkX, chunkZ));
         }
      }

      CompletableFuture.allOf(futures.toArray(CompletableFuture<?>[]::new)).whenComplete((ignored, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
         if (request != null && !CREATION_QUEUE.isPending(request)) {
            return;
         }
         if (error != null) {
            fail(plugin, request, homeName, creator, messages().text("HomeCreationSpawnFailed", "出生区域生成失败"));
            return;
         }
         if (completion != null) {
            completion.run();
         }
         // The home exists from here on, so the recorded charge is final.
         String creatorName = creator != null ? creator.getName() : request == null ? null : request.getPlayerName();
         if (creatorName != null) {
            CreateCostLedger.settle(creatorName);
         }
         if (creator != null && creator.isOnline()) {
            creator.sendMessage(messages().text("HomeCreationReady", "§8[§6错误庄园§8] §a家园地形准备完成，现在可以进入。"));
            creator.teleport(world.getSpawnLocation());
         }
         complete(plugin, request, homeName);
      }));
   }

   /**
    * Applies the bounded natural-home world settings.
    *
    * <p>Mob spawning is driven by the {@code doMobSpawning} config key. This method
    * used to force {@code DO_MOB_SPAWNING=false} and every spawn limit to 0
    * unconditionally, which silently overrode the config, contradicted the documented
    * "dual dimension mob spawning" feature of {@code EnableHomeNether}, and made both
    * {@code HomeSpawnCompensation} and the whole {@code HomeRulesDefaults} mob-rule
    * GUI meaningless.</p>
    */
   public static void applyWorldPolicy(World world, int size, boolean permanentDay, long dayTime) {
      if (world == null) {
         return;
      }

      boolean mobSpawning = mobSpawningEnabled();

      world.setKeepSpawnInMemory(false);
      applySpawnLimits(world, mobSpawning);
      // Resolved by name rather than through GameRule constants: Spigot 26.2 renamed every
      // constant (DO_MOB_SPAWNING -> SPAWN_MOBS and so on) while Paper 26.2 kept the old ones,
      // so no constant compiles against the whole supported range. See com.Util.Platform.
      Platform.setGameRule(world, "doMobSpawning", String.valueOf(mobSpawning));
      Platform.setGameRule(world, "doPatrolSpawning", "false");
      Platform.setGameRule(world, "doTraderSpawning", "false");
      Platform.setGameRule(world, "doWardenSpawning", "false");
      Platform.setGameRule(world, "doInsomnia", "false");
      Platform.setGameRule(world, "doDaylightCycle", String.valueOf(!permanentDay));
      if (permanentDay) {
         world.setTime(Math.max(0L, dayTime) % 24000L);
      }
      world.setStorm(false);
      world.setThundering(false);
      world.getWorldBorder().setCenter(world.getSpawnLocation());
      world.getWorldBorder().setSize(HomeTerrainPolicy.clampSize(size));

      // Keep the compensation pass as the single owner of the monster cap so the two
      // subsystems cannot disagree about the effective value.
      if (mobSpawning) {
         HomeSpawnUtil.applyHomeSpawnCompensation(world);
      }
   }

   private static void applySpawnLimits(World world, boolean mobSpawning) {
      if (!mobSpawning) {
         world.setMonsterSpawnLimit(0);
         world.setAnimalSpawnLimit(0);
         world.setAmbientSpawnLimit(0);
         world.setWaterAnimalSpawnLimit(0);
         world.setWaterAmbientSpawnLimit(0);
         return;
      }

      // -1 means "server default" in ErrorTown's config, which Bukkit expresses as -1 too.
      world.setMonsterSpawnLimit(configuredLimitOrDefault("MaxSpawnMonstersAmount"));
      world.setAnimalSpawnLimit(configuredLimitOrDefault("MaxSpawnAnimalsAmount"));
   }

   private static int configuredLimitOrDefault(String key) {
      if (com.ErrorTown.Main.JavaPlugin == null) {
         return -1;
      }
      int value = com.ErrorTown.Main.JavaPlugin.getConfig().getInt(key, -1);
      return value < 0 ? -1 : value;
   }

   private static boolean mobSpawningEnabled() {
      return com.ErrorTown.Main.JavaPlugin == null
         || com.ErrorTown.Main.JavaPlugin.getConfig().getBoolean("doMobSpawning", true);
   }

   public static synchronized void complete(JavaPlugin plugin, String homeName) {
      CREATION_QUEUE.complete(homeName);
      startAvailable(plugin, configuredLimit(plugin));
   }

   private static synchronized void complete(
      JavaPlugin plugin,
      HomeCreationQueue.CreationRequest request,
      String homeName
   ) {
      if (request == null) {
         CREATION_QUEUE.complete(homeName);
      } else if (!CREATION_QUEUE.complete(request)) {
         return;
      }
      startAvailable(plugin, configuredLimit(plugin));
   }

   public static synchronized void fail(JavaPlugin plugin, String homeName, Player player, String message) {
      CREATION_QUEUE.fail(homeName);
      refundAndNotify(player, player == null ? null : player.getName(), homeName, message);
      startAvailable(plugin, configuredLimit(plugin));
   }

   public static synchronized void fail(
      JavaPlugin plugin,
      HomeCreationQueue.CreationRequest request,
      Player player,
      String message
   ) {
      fail(plugin, request, request == null ? null : request.getHomeName(), player, message);
   }

   private static synchronized void fail(
      JavaPlugin plugin,
      HomeCreationQueue.CreationRequest request,
      String homeName,
      Player player,
      String message
   ) {
      if (request == null) {
         CREATION_QUEUE.fail(homeName);
      } else if (!CREATION_QUEUE.fail(request)) {
         return;
      }
      refundAndNotify(player, request == null ? null : request.getPlayerName(), homeName, message);
      startAvailable(plugin, configuredLimit(plugin));
   }

   /**
    * Returns any recorded creation charge and tells the player what happened.
    *
    * <p>{@code fail} previously only removed the "already paid" marker, so every
    * failure path silently consumed the player's money and points.</p>
    */
   private static void refundAndNotify(Player player, String playerName, String homeName, String message) {
      if (playerName == null && player != null) {
         playerName = player.getName();
      }
      if (playerName == null) {
         return;
      }

      Variable.pendingCreateCostPaid.remove(playerName);
      Variable.pendingCreateSeed.remove(playerName);
      String refunded = CreateCostLedger.refund(playerName);

      if (player == null || !player.isOnline()) {
         return;
      }

      player.sendMessage(
         messages().format(
            "HomeCreationFailed",
            "§8[§6错误庄园§8] §c<reason>，请稍后重试或联系管理员。",
            "<reason>",
            message
         )
      );
      if (refunded != null) {
         player.sendMessage(
            messages().format(
               "HomeCreationRefunded",
               "§8[§6错误庄园§8] §a已退还 <Cost>。",
               "<Cost>",
               refunded
            )
         );
      }
   }

   private static synchronized void startAvailable(JavaPlugin plugin, int configuredLimit) {
      if (plugin == null) {
         return;
      }
      for (HomeCreationQueue.CreationRequest request : CREATION_QUEUE.admitAvailable(configuredLimit)) {
         Bukkit.getScheduler().runTask(plugin, () -> {
            Player player = Bukkit.getPlayer(request.getPlayerId());
            if (player == null || !player.isOnline()) {
               fail(
                  plugin,
                  request,
                  request.getHomeName(),
                  player,
                  messages().text("HomeCreationCreatorOffline", "创建者已离线，任务已取消")
               );
               return;
            }
            // Only re-assert the prepaid marker when a charge is actually on record.
            // Setting it unconditionally granted a free create to any request that
            // reached the queue without paying.
            if (CreateCostLedger.hasCharge(player.getName())) {
               Variable.pendingCreateCostPaid.put(player.getName(), Boolean.TRUE);
            }
            // ErrorTown returns false after handling many successful command branches;
            // world readiness, rather than Command dispatch's boolean, is the success signal.
            DISPATCHED_REQUEST.set(request);
            try {
               Bukkit.dispatchCommand(player, request.getCommand());
            } finally {
               DISPATCHED_REQUEST.remove();
            }
         });
         Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (CREATION_QUEUE.isPending(request)) {
               fail(
                  plugin,
                  request,
                  request.getHomeName(),
                  Bukkit.getPlayer(request.getPlayerId()),
                  messages().text("HomeCreationTimeout", "家园创建等待超时")
               );
            }
         }, Math.max(20L, staleMillis(plugin) / 50L));
      }
   }

   private static long staleMillis(JavaPlugin plugin) {
      if (plugin == null) {
         return HomeCreationQueue.DEFAULT_STALE_MILLIS;
      }
      long seconds = plugin.getConfig().getLong("HomeCreationQueue.TimeoutSeconds", 300L);
      return Math.max(30L, seconds) * 1000L;
   }

   private static int configuredLimit(JavaPlugin plugin) {
      return plugin == null ? HomeTerrainPolicy.DEFAULT_MAX_CONCURRENT_CREATIONS : plugin.getConfig().getInt("HomeCreationQueue.MaxConcurrent", 2);
   }

   private static HomeCreationMessages messages() {
      return new HomeCreationMessages(key -> Variable.Lang_YML == null ? null : Variable.Lang_YML.getString(key));
   }
}
