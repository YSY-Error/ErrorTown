package com.ErrorTown;

import com.GUI.CheckGui;
import com.GUI.CreateGui;
import com.GUI.DenyGui;
import com.GUI.GiftGui;
import com.GUI.InviteGui;
import com.GUI.MainGui;
import com.GUI.ManageGui;
import com.GUI.ManageGui2;
import com.GUI.OwnedHomesGui;
import com.GUI.SetSpawnGui;
import com.GUI.TrustGui;
import com.GUI.VisitGui;
import com.Listeners.BlockBreakListener;
import com.Listeners.BlockPlaceListener;
import com.Listeners.CreatureSpawnListener;
import com.Listeners.EntityBreedListener;
import com.Listeners.EntityInteractByEntityListener;
import com.Listeners.EntityPickupItemListener;
import com.Listeners.FarmProtectListener;
import com.Listeners.FrameProtectListener;
import com.Listeners.GiftGuiClickItemListener;
import com.Listeners.GiftGuiCloseListener;
import com.Listeners.HomePortalListener;
import com.Listeners.HomeProtectInteractListener;
import com.Listeners.HomeProtectPlaceListener;
import com.Listeners.HomeRulesListener;
import com.Listeners.InteractBlackListener;
import com.Listeners.InteractMenuListener;
import com.Listeners.InventoryClickListener;
import com.Listeners.InventoryDragListener;
import com.Listeners.InventoryMoveItemListener;
import com.Listeners.InventoryOpenListener;
import com.Listeners.InventoryPickupItemListener;
import com.Listeners.LivingEntityProtectInHomeListener;
import com.Listeners.MaxHeightPlaceListener;
import com.Listeners.PlayerChatListener;
import com.Listeners.PlayerDamageInHomeListener;
import com.Listeners.PlayerDeathListener;
import com.Listeners.PlayerDropListener;
import com.Listeners.PlayerJoinListener;
import com.Listeners.PlayerMoveListener;
import com.Listeners.PlayerPickupListener;
import com.Listeners.PlayerQuitListener;
import com.Listeners.PlayerRespawnListener;
import com.Listeners.PlayerTeleportListener;
import com.Listeners.PortalCreateListener;
import com.Listeners.ShiftFMenuListener;
import com.Listeners.TeleportHomeProtectListener;
import com.Listeners.WeatherChangeListener;
import com.Listeners.WorldBlockPlaceListener;
import com.Listeners.WorldInitListener;
import com.Listeners.WorldLoadListener;
import com.PlaceHolder.API;
import com.Util.CheckUpdate;
import com.Util.ConfigUpdate;
import com.Util.HikariCPUtils;
import com.Util.MySQL;
import com.Util.TimeAsync;
import com.Util.Util;
import com.Util.WaitToLoad;
import com.Util.HologramCompat;
import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteStreams;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.net.Socket;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Properties;
import net.milkbowl.vault.economy.Economy;
import org.black_ixx.playerpoints.PlayerPoints;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.bukkit.scheduler.BukkitRunnable;

public class Main extends JavaPlugin implements PluginMessageListener {
   /**
    * Data folder name, matching {@code name:} in {@code plugin.yml}.
    *
    * <p>Several paths are derived by string concatenation rather than from
    * {@link #getDataFolder()}, so the folder name has to exist as a constant. Keep the two
    * in sync: a mismatch silently splits the plugin's state across two directories.</p>
    */
   public static final String PLUGIN_FOLDER = "ErrorTown";

   /** Data folder used before the rename; migrated once on first start. */
   public static final String LEGACY_PLUGIN_FOLDER = "SummerTown";

   /** BungeeCord plugin-message subchannel. */
   public static final String BUNGEE_CHANNEL = "ErrorTown";

   /** Subchannel used before the rename; still accepted so a rolling upgrade works. */
   public static final String LEGACY_BUNGEE_CHANNEL = "SummerTown";

   public static JavaPlugin JavaPlugin;
   public static String type = BUNGEE_CHANNEL;
   public static String code = "dsfsdfdsfdsf";
   public static Socket socket;
   public static String version = "1.0";
   public static boolean first_success = false;
   public static boolean check_active = false;

   public static boolean isOSLinux() {
      Properties prop = System.getProperties();
      String os = prop.getProperty("os.name");
      return os != null && os.toLowerCase().indexOf("linux") > -1;
   }

   public void onLoad() {
      JavaPlugin = this;
      init();
   }

   public static String getMD5() {
      String md5Hash = "null";

      try {
         byte[] programBytes = Files.readAllBytes(Paths.get(Main.class.getProtectionDomain().getCodeSource().getLocation().toURI()));
         MessageDigest md = MessageDigest.getInstance("MD5");
         byte[] md5Bytes = md.digest(programBytes);
         StringBuilder sb = new StringBuilder();

         for (byte b : md5Bytes) {
            sb.append(String.format("%02x", b));
         }

         md5Hash = sb.toString();
      } catch (IOException | URISyntaxException | NoSuchAlgorithmException failure) {
         com.Util.Diag.warn("Could not compute the plugin jar checksum", failure);
      }

      return md5Hash;
   }

   public void onDisable() {
      // Stop the stale-slot sweep before anything else so it cannot fire mid-shutdown.
      com.Util.HomeCreationCoordinator.stopReaper();
      // Release the Spigot chunk-load fallback queue too, so anything waiting on a chunk
      // runs its error path now instead of hanging on a future nobody will complete.
      com.Util.Platform.shutdown();

      for (Player p : Bukkit.getOnlinePlayers()) {
         if (p.getOpenInventory() != null) {
            InventoryHolder inv = p.getOpenInventory().getTopInventory().getHolder();
            if (inv instanceof CheckGui
               || inv instanceof CreateGui
               || inv instanceof DenyGui
               || inv instanceof InviteGui
               || inv instanceof MainGui
               || inv instanceof OwnedHomesGui
               || inv instanceof ManageGui
               || inv instanceof ManageGui2
               || inv instanceof SetSpawnGui
               || inv instanceof TrustGui
               || inv instanceof VisitGui
               || inv instanceof GiftGui) {
               p.sendMessage("§a§l§m--------------" + Variable.Prefix + "§a§l§m--------------");
               p.sendMessage(com.Util.Lang.get("CloseGuiWhenPluginReload", "[ErrorTown] CloseGuiWhenPluginReload"));
               p.sendMessage("§a§l§m--------------" + Variable.Prefix + "§a§l§m--------------");
               p.closeInventory();
            }
         }
      }

      for (World temp : Bukkit.getWorlds()) {
         if (Variable.hololist.containsKey(Variable.prefix_p + temp.getName())) {
            for (HologramCompat.Handle temp2 : Variable.hololist.get(Variable.prefix_p + temp.getName())) {
               temp2.delete();
            }
         }
      }

      for (World tempx : Bukkit.getWorlds()) {
         boolean is_jump = false;

         for (String str : JavaPlugin.getConfig().getStringList("UnAutoSaveWorlds")) {
            if (str.equalsIgnoreCase(Variable.prefix_p + tempx.getName().replace(Variable.world_prefix, ""))) {
               is_jump = true;
               break;
            }
         }

         if (!is_jump) {
            tempx.save();
         }
      }

      Bukkit.getConsoleSender().sendMessage(com.Util.Lang.get("AutoSaveSuccess", "§a[ErrorTown] 世界保存完成"));
      Bukkit.getConsoleSender().sendMessage(com.Util.Lang.get("DisablePlugin", "§c[ErrorTown] 插件已卸载"));

      // After the world saves, which do not touch the database, and before the audit flush,
      // which writes to audit.log rather than SQL. Leaving the pool open holds its connections
      // past disable; HikariCP's own threads are not daemons.
      com.Util.HikariCPUtils.shutdown();

      // Last statement in onDisable on purpose: the audit queue is normally drained by an
      // async task, which no longer runs once the plugin is disabled. flushForShutdown()
      // (not flush()) is required here — flush() returns without writing when the async
      // writer still holds the single-writer token, and that writer dies with the plugin.
      int unwritten = com.Util.HomeAudit.flushForShutdown();
      if (unwritten > 0) {
         getLogger().warning("Shutdown left " + unwritten + " audit record(s) unwritten.");
      }
   }

   private boolean setupEconomy() {
      if (this.getServer().getPluginManager().getPlugin("Vault") == null) {
         return false;
      } else {
         RegisteredServiceProvider<Economy> rsp = this.getServer().getServicesManager().getRegistration(Economy.class);
         if (rsp == null) {
            return false;
         } else {
            Variable.econ = rsp.getProvider();
            return Variable.econ != null;
         }
      }
   }

   public static boolean setupPlayerPoints() {
      Plugin plugin = Bukkit.getPluginManager().getPlugin("PlayerPoints");
      Variable.playerPoints = PlayerPoints.class.cast(plugin);
      return Variable.playerPoints != null;
   }

   public void onEnable() {
      JavaPlugin = this;
      // bStats service id for this plugin: https://bstats.org/plugin/bukkit/ErrorTown/33724
      int pluginId = 33724;
      new Metrics(JavaPlugin, pluginId);
      Variable.NMS_Version = Bukkit.getServer()
         .getClass()
         .getPackage()
         .toString()
         .substring(Bukkit.getServer().getClass().getPackage().toString().lastIndexOf(".") + 1, Bukkit.getServer().getClass().getPackage().toString().length())
         .replace("V", "v");
      if (!JavaPlugin.getConfig().getBoolean("DisableFunctionButTeleport")) {
         Bukkit.getPluginManager().registerEvents(new CreatureSpawnListener(), this);
         Bukkit.getPluginManager().registerEvents(new EntityBreedListener(), this);
         Bukkit.getPluginManager().registerEvents(new EntityPickupItemListener(), this);
         Bukkit.getPluginManager().registerEvents(new InteractBlackListener(), this);
         Bukkit.getPluginManager().registerEvents(new BlockBreakListener(), this);
         Bukkit.getPluginManager().registerEvents(new BlockPlaceListener(), this);
         Bukkit.getPluginManager().registerEvents(new HomeProtectInteractListener(), this);
         Bukkit.getPluginManager().registerEvents(new InventoryOpenListener(), this);
         Bukkit.getPluginManager().registerEvents(new LivingEntityProtectInHomeListener(), this);
         Bukkit.getPluginManager().registerEvents(new PlayerDamageInHomeListener(), this);
         Bukkit.getPluginManager().registerEvents(new FrameProtectListener(), this);
         Bukkit.getPluginManager().registerEvents(new PlayerDropListener(), this);
         Bukkit.getPluginManager().registerEvents(new PlayerPickupListener(), this);
         Bukkit.getPluginManager().registerEvents(new PlayerTeleportListener(), this);
         Bukkit.getPluginManager().registerEvents(new WeatherChangeListener(), this);
         Bukkit.getPluginManager().registerEvents(new WorldLoadListener(), this);
         Bukkit.getPluginManager().registerEvents(new WorldBlockPlaceListener(), this);
         Bukkit.getPluginManager().registerEvents(new HomeProtectPlaceListener(), this);
         Bukkit.getPluginManager().registerEvents(new InteractMenuListener(), this);
         Bukkit.getPluginManager().registerEvents(new TeleportHomeProtectListener(), this);
         Bukkit.getPluginManager().registerEvents(new PlayerDeathListener(), this);
         Bukkit.getPluginManager().registerEvents(new PlayerRespawnListener(), this);
         Bukkit.getPluginManager().registerEvents(new EntityInteractByEntityListener(), this);
         Bukkit.getPluginManager().registerEvents(new FarmProtectListener(), this);
         Bukkit.getPluginManager().registerEvents(new GiftGuiCloseListener(), this);
         Bukkit.getPluginManager().registerEvents(new GiftGuiClickItemListener(), this);
         Bukkit.getPluginManager().registerEvents(new PlayerQuitListener(), this);
         Bukkit.getPluginManager().registerEvents(new PlayerChatListener(), this);
         Bukkit.getPluginManager().registerEvents(new PlayerJoinListener(), this);
         Bukkit.getPluginManager().registerEvents(new PortalCreateListener(), this);
         Bukkit.getPluginManager().registerEvents(new HomePortalListener(), this);
         Bukkit.getPluginManager().registerEvents(new HomeRulesListener(), this);
         Bukkit.getPluginManager().registerEvents(new WorldInitListener(), this);
         // Registering the move listener unconditionally means it is invoked ~20x per
         // second per player only to return immediately when the feature is off.
         if (JavaPlugin.getConfig().getBoolean("EnableMoveListener")) {
            Bukkit.getPluginManager().registerEvents(new PlayerMoveListener(), this);
         }
         if (JavaPlugin.getConfig().getBoolean("EnableAsnycTime")) {
            TimeAsync.asnycTime();
         }
      }

      Bukkit.getPluginManager().registerEvents(new InventoryClickListener(), this);
      Bukkit.getPluginManager().registerEvents(new InventoryDragListener(), this);
      Bukkit.getPluginManager().registerEvents(new InventoryPickupItemListener(), this);
      Bukkit.getPluginManager().registerEvents(new InventoryMoveItemListener(), this);
      Bukkit.getPluginManager().registerEvents(new ShiftFMenuListener(), this);
      if (JavaPlugin.getConfig().getBoolean("EnableHeightLimit")) {
         Bukkit.getPluginManager().registerEvents(new MaxHeightPlaceListener(), this);
         Bukkit.getConsoleSender().sendMessage(com.Util.Lang.get("EnableHeightLimit", "[ErrorTown] EnableHeightLimit"));
      }

      // Must stay outside the EnableHeightLimit branch: home protection and the
      // creation-slot reaper are unconditional. A previous edit nested them inside that
      // "if", so every server with EnableHeightLimit=false silently lost both.
      Bukkit.getPluginManager().registerEvents(new com.Listeners.HomeProtectionListener(), this);
      com.Util.HomeCreationCoordinator.startReaper(this);

      this.getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");
      this.getServer().getMessenger().registerIncomingPluginChannel(this, "BungeeCord", this);
      if (JavaPlugin.getConfig().getBoolean("BungeeCord")
         && JavaPlugin.getConfig().getBoolean("AutoReCreateInLowerLagHome")
         && !JavaPlugin.getConfig().getBoolean("DisableFunctionButTeleport")) {
         MySQL.autoUpdateServer();
      }

      if (!JavaPlugin.getConfig().getBoolean("DisableFunctionButTeleport") && JavaPlugin.getConfig().getBoolean("EnableMoveListener")) {
         Bukkit.getConsoleSender().sendMessage(com.Util.Lang.get("EnableMoveListener", "[ErrorTown] EnableMoveListener"));
      }

      Bukkit.getPluginCommand("st").setExecutor(new CommandListener());
      Bukkit.getPluginCommand("st").setTabCompleter(new CommandListener());
      Variable.Prefix = this.getConfig().getString("Prefix");
      if (!JavaPlugin.getConfig().getBoolean("DisableFunctionButTeleport")) {
         if (JavaPlugin.getServer().getPluginManager().getPlugin("Vault") == null) {
            Bukkit.getConsoleSender().sendMessage(com.Util.Lang.get("NotHookVault", "[ErrorTown] NotHookVault"));
            this.getServer().getPluginManager().disablePlugin(this);
            return;
         }

         this.setupEconomy();
         Bukkit.getConsoleSender().sendMessage(com.Util.Lang.get("HookVault", "[ErrorTown] HookVault"));
         if (JavaPlugin.getServer().getPluginManager().getPlugin("PlayerPoints") == null) {
            Bukkit.getConsoleSender().sendMessage(com.Util.Lang.get("NotHookPlayerPoints", "[ErrorTown] NotHookPlayerPoints"));
         } else {
            setupPlayerPoints();
            Variable.PlyaerPointsModule = true;
            Bukkit.getConsoleSender().sendMessage(com.Util.Lang.get("HookPlayerPoints", "[ErrorTown] HookPlayerPoints"));
         }

         if (JavaPlugin.getServer().getPluginManager().getPlugin("NBTAPI") == null) {
            Bukkit.getConsoleSender().sendMessage(com.Util.Lang.get("NotHookNBTAPI", "[ErrorTown] NotHookNBTAPI"));
         } else {
            Bukkit.getConsoleSender().sendMessage(com.Util.Lang.get("HookNBTAPI", "[ErrorTown] HookNBTAPI"));
         }

         if (JavaPlugin.getServer().getPluginManager().getPlugin("ProtocolLib") == null) {
            Bukkit.getConsoleSender().sendMessage(com.Util.Lang.get("NotHookProtocolLib", "[ErrorTown] NotHookProtocolLib"));
         } else {
            Bukkit.getConsoleSender().sendMessage(com.Util.Lang.get("HookProtocolLib", "[ErrorTown] HookProtocolLib"));
         }

         if (JavaPlugin.getServer().getPluginManager().getPlugin("HolographicDisplays") == null
            && JavaPlugin.getServer().getPluginManager().getPlugin("DecentHolograms") == null) {
            Bukkit.getConsoleSender().sendMessage(com.Util.Lang.get("NotHookHolographicDisplays", "[ErrorTown] NotHookHolographicDisplays"));
            Variable.Hologram_switch = false;
         } else {
            Variable.Hologram_switch = true;
            Bukkit.getConsoleSender().sendMessage(com.Util.Lang.get("HookHolographicDisplays", "[ErrorTown] HookHolographicDisplays"));
         }

         if (JavaPlugin.getServer().getPluginManager().getPlugin("Multiverse-Core") != null && JavaPlugin.getConfig().getBoolean("MultiverseCoreCompability")) {
            Variable.hook_multiverseCore = true;
            Bukkit.getConsoleSender().sendMessage(com.Util.Lang.get("MultiverseCoreCompability", "[ErrorTown] MultiverseCoreCompability"));
         }

         if (JavaPlugin.getServer().getPluginManager().getPlugin("FastAsyncWorldEdit") != null && JavaPlugin.getConfig().getBoolean("FaweSwitch")) {
            Variable.hook_FastAsyncWorldEdit = true;
            Bukkit.getConsoleSender().sendMessage(com.Util.Lang.get("FaweAndWorldEditCompability", "[ErrorTown] FaweAndWorldEditCompability"));
         }
      }

      if (JavaPlugin.getServer().getPluginManager().getPlugin("PlaceholderAPI") == null) {
         Bukkit.getConsoleSender().sendMessage(com.Util.Lang.get("NotHookPlaceholderAPI", "[ErrorTown] NotHookPlaceholderAPI"));
      } else {
         API api = new API();
         // Registers %ErrorTown_...% plus the pre-rename %ErrorTown_...% alias, so
         // placeholder strings held in other plugins' configs keep resolving.
         api.registerWithLegacyAlias();
         Bukkit.getConsoleSender().sendMessage(com.Util.Lang.get("HookPlaceholderAPI", "[ErrorTown] HookPlaceholderAPI"));
      }

      ScheduledTasks.start();
      if (JavaPlugin.getConfig().getBoolean("CheckUpdate")) {
         CheckUpdate.checkUpdate();
      }

      // Startup self-checks. These run last so Vault, PlayerPoints and the language
      // file are all resolved: a refund attempted before the economy hook exists would
      // fail and simply stay queued.
      reportPlatform();
      // Recover keys added by newer plugin versions before auditing, otherwise every added key is
      // reported as missing forever: Bukkit writes a language file only when it does not yet exist.
      com.Util.Lang.seedMissingFromBundle();
      com.Util.Lang.audit();
      reportConfigFindings();
      recoverUnsettledCharges();

      Bukkit.getConsoleSender().sendMessage("§7[错误小镇] §a错误庄园系统 §7- §dYSYError");
      Bukkit.getConsoleSender().sendMessage("§7[错误小镇] §a加载完成, 祝您游玩愉快~");
   }

   /**
    * Logs which server this actually is, and refuses to pretend Folia works.
    *
    * <p>ErrorTown supports Spigot, Paper, Purpur and Leaves on Minecraft 1.21 through 26.2.
    * Folia is deliberately out of scope: it has no world load/unload API and has permanently
    * removed synchronous {@code Entity#teleport}, and this plugin is a per-player-world plugin
    * built on both. Saying so once at startup is far better than a stack trace on the first
    * {@code /sh create}.</p>
    */
   private void reportPlatform() {
      getLogger().info("Platform: " + com.Util.Platform.describe());
      getLogger().info(com.Util.CraftEngineBridge.describe());
      if (!com.Util.Platform.isSupportedPlatform()) {
         getLogger().severe("Folia detected. ErrorTown does NOT support Folia: Folia provides no world");
         getLogger().severe("load/unload API and has removed synchronous Entity#teleport, which home");
         getLogger().severe("creation and home travel both require. Use Paper, Purpur, Leaves or Spigot.");
      }
   }

   /**
    * Runs {@link com.Util.ConfigValidator} and logs what it finds.
    *
    * <p>Several config invariants used to be implicit — most importantly that
    * {@code MaxLevel} equals the price-list length plus one. Breaking one produced a
    * silent misbehaviour rather than an error, so the check is explicit now.</p>
    */
   private void reportConfigFindings() {
      java.util.List<com.Util.ConfigValidator.Finding> findings;
      try {
         findings = com.Util.ConfigValidator.validate(configView());
      } catch (RuntimeException failure) {
         getLogger().log(java.util.logging.Level.WARNING, "Configuration validation could not complete", failure);
         return;
      }
      if (findings.isEmpty()) {
         getLogger().info("Configuration check passed.");
         return;
      }
      for (com.Util.ConfigValidator.Finding finding : findings) {
         String line = "config.yml [" + finding.getKey() + "] " + finding.getMessage();
         switch (finding.getSeverity()) {
            case ERROR -> getLogger().severe(line);
            case WARN -> getLogger().warning(line);
            default -> getLogger().info(line);
         }
      }
   }

   /** Adapter from Bukkit's configuration to the Bukkit-free validator view. */
   private com.Util.ConfigValidator.ConfigView configView() {
      org.bukkit.configuration.file.FileConfiguration config = getConfig();
      return new com.Util.ConfigValidator.ConfigView() {
         @Override
         public boolean contains(String key) {
            return config.contains(key);
         }

         @Override
         public int getInt(String key, int fallback) {
            return config.getInt(key, fallback);
         }

         @Override
         public long getLong(String key, long fallback) {
            return config.getLong(key, fallback);
         }

         @Override
         public boolean getBoolean(String key, boolean fallback) {
            return config.getBoolean(key, fallback);
         }

         @Override
         public String getString(String key, String fallback) {
            return config.getString(key, fallback);
         }

         @Override
         public java.util.List<Integer> getIntegerList(String key) {
            return config.getIntegerList(key);
         }

         @Override
         public java.util.List<String> getStringList(String key) {
            return config.getStringList(key);
         }
      };
   }

   /**
    * Replays the on-disk creation-charge ledger and keeps retrying whatever the economy
    * provider refuses, so a crash between "money taken" and "world ready" cannot make a
    * payment disappear.
    */
   private void recoverUnsettledCharges() {
      try {
         com.Util.CreateCostLedger.recoverPending();
      } catch (RuntimeException failure) {
         getLogger().log(java.util.logging.Level.WARNING, "Could not recover unsettled creation charges", failure);
      }
      Bukkit.getScheduler().runTaskTimer(this, () -> {
         if (!com.Util.CreateCostLedger.pendingPlayers().isEmpty()) {
            com.Util.CreateCostLedger.retryPendingRefunds();
         }
      }, 20L * 120L, 20L * 120L);
   }

   public static void init() {
      // Before anything writes a default resource: once plugins/ErrorTown/ contains files
      // the migration is skipped and the real plugins/SummerTown/ data is orphaned.
      com.Util.RenameMigration.migrateDataFolder();

      if (isOSLinux()) {
         Variable.linux_os = true;
         Variable.file_loc_prefix = "/";
      } else {
         Variable.file_loc_prefix = "\\";
      }

      Variable.prefix_p = JavaPlugin.getConfig().getString("WorldPrefix");
      if (Bukkit.getVersion().toString().toUpperCase().contains("THERMOS")) {
         Variable.has_no_click_message = true;
      }

      if (!Bukkit.getVersion().toString().toUpperCase().contains("CATSERVER")
         && !Bukkit.getVersion().toString().toUpperCase().contains("URANIUM")
         && !Bukkit.getVersion().toString().toUpperCase().contains("KCAULDRON")
         && !Bukkit.getVersion().toString().toUpperCase().contains("THERMOS")
         && !Bukkit.getVersion().toString().toUpperCase().contains("MOHIST")) {
         Variable.world_prefix = "ErrorTownWorld/";
      } else {
         Variable.world_prefix = "";
         Variable.Cat_Check = true;
      }

      if (Bukkit.getVersion().toString().contains("1.7.10")) {
         Variable.world_prefix = "";
         Variable.Cat_Check = true;
      }

      if (Bukkit.getVersion().toString().contains("1.16.5")) {
         Variable.world_prefix = "";
         Variable.Cat_Check = true;
      }

      if (Bukkit.getVersion().toString().toUpperCase().contains("ARCLIGHT")) {
         Variable.world_prefix = "";
         Variable.Cat_Check = false;
      }

      if (Bukkit.getVersion().toString().contains("1.20.1") && Bukkit.getVersion().toString().toUpperCase().contains("1.20.1")) {
         Variable.world_prefix = "";
         Variable.Cat_Check = true;
      }

      if (Bukkit.getVersion().toString().contains("1.20.1") && Bukkit.getVersion().toString().toUpperCase().contains("ARCLIGHT")) {
         Variable.world_prefix = "";
         Variable.Cat_Check = true;
      }

      if (Bukkit.getVersion().toString().contains("Banner") && Bukkit.getVersion().toString().toUpperCase().contains("1.20.1")) {
         Variable.world_prefix = "ErrorTownWorld/";
      }

      JavaPlugin.saveDefaultConfig();
      JavaPlugin.reloadConfig();
      if (!new File(JavaPlugin.getDataFolder() + Variable.file_loc_prefix + "GUI.yml").exists()) {
         JavaPlugin.saveResource("GUI.yml", false);
      }

      if (!new File(JavaPlugin.getDataFolder() + Variable.file_loc_prefix + "GUI_en.yml").exists()) {
         JavaPlugin.saveResource("GUI_en.yml", false);
      }

      if (!new File(JavaPlugin.getDataFolder() + Variable.file_loc_prefix + "log.yml").exists()) {
         JavaPlugin.saveResource("log.yml", false);
      }

      if (!new File(JavaPlugin.getDataFolder() + Variable.file_loc_prefix + "Language" + Variable.file_loc_prefix + "Chinese.yml").exists()) {
         JavaPlugin.saveResource("Language/Chinese.yml", false);
      }

      if (!new File(JavaPlugin.getDataFolder() + Variable.file_loc_prefix + "Language" + Variable.file_loc_prefix + "Chinese_TW.yml").exists()) {
         JavaPlugin.saveResource("Language/Chinese_TW.yml", false);
      }

      if (!new File(JavaPlugin.getDataFolder() + Variable.file_loc_prefix + "Language" + Variable.file_loc_prefix + "English.yml").exists()) {
         JavaPlugin.saveResource("Language/English.yml", false);
      }

      Variable.Lang_YML = YamlConfiguration.loadConfiguration(
         new File(
            JavaPlugin.getDataFolder()
               + Variable.file_loc_prefix
               + "Language"
               + Variable.file_loc_prefix
               + JavaPlugin.getConfig().getString("Language")
               + ".yml"
         )
      );
      File f = new File("");
      String Tempf0 = null;

      try {
         Tempf0 = f.getCanonicalPath();
      } catch (IOException ioFailure) {
         ioFailure.printStackTrace();
      }

      Variable.Final = "";
      if (Variable.linux_os) {
         Variable.ab = Tempf0.split(Variable.file_loc_prefix);
      } else {
         Variable.ab = Tempf0.split(Variable.file_loc_prefix + Variable.file_loc_prefix);
      }

      if (isOSLinux()) {
         String[] args = Tempf0.split(Variable.file_loc_prefix);

         for (int i = 0; i < args.length - 1; i++) {
            Variable.Final = Variable.Final + Variable.file_loc_prefix + args[i];
         }
      } else {
         String[] args = Tempf0.split(Variable.file_loc_prefix + Variable.file_loc_prefix);

         for (int i = 0; i < args.length - 1; i++) {
            Variable.Final = Variable.Final + Variable.file_loc_prefix + args[i];
         }
      }

      Variable.Final = Variable.Final + Variable.file_loc_prefix;
      if (isOSLinux()) {
         Variable.Final = Variable.Final.replaceFirst(Variable.file_loc_prefix, "");
      } else {
         Variable.Final = Variable.Final.replaceFirst(Variable.file_loc_prefix + Variable.file_loc_prefix, "");
      }

      if (JavaPlugin.getConfig().getBoolean("BungeeCord")) {
         Bukkit.getConsoleSender().sendMessage(com.Util.Lang.get("EnableBungeeCord", "[ErrorTown] EnableBungeeCord"));
         Variable.bungee = true;
         HikariCPUtils.setSqlConnectionPool();
         if (JavaPlugin.getConfig().getBoolean("DisableFunctionButTeleport")) {
            Bukkit.getConsoleSender().sendMessage(com.Util.Lang.get("DisableFunctionButTeleport", "[ErrorTown] DisableFunctionButTeleport"));
         }
      } else {
         Variable.bungee = false;
         Bukkit.getConsoleSender().sendMessage(com.Util.Lang.get("DisableBungeeCord", "[ErrorTown] DisableBungeeCord"));
      }

      Variable.custom_playerdata_location = Variable.Final
         + Variable.ab[Variable.ab.length - 1]
         + Variable.file_loc_prefix
         + "plugins"
         + Variable.file_loc_prefix
         + PLUGIN_FOLDER
         + Variable.file_loc_prefix
         + "playerdata";
      Variable.custom_autobackup_location = Variable.Final
         + Variable.ab[Variable.ab.length - 1]
         + Variable.file_loc_prefix
         + "plugins"
         + Variable.file_loc_prefix
         + PLUGIN_FOLDER
         + Variable.file_loc_prefix
         + "backup";
      Variable.server_file_world = Variable.Final;
      Variable.worldFinal = Tempf0 + Variable.file_loc_prefix + "plugins" + Variable.file_loc_prefix + PLUGIN_FOLDER + Variable.file_loc_prefix;
      Variable.Log_All = Tempf0 + Variable.file_loc_prefix + "plugins" + Variable.file_loc_prefix + PLUGIN_FOLDER + Variable.file_loc_prefix;
      Variable.single_server_gen = Variable.Final + Variable.file_loc_prefix + Variable.ab[Variable.ab.length - 1] + Variable.file_loc_prefix;
      Variable.Final = Variable.custom_playerdata_location;
      File check_file = new File(Variable.Final);
      if (!check_file.isDirectory()) {
         check_file.mkdir();
      }

      File autobackup = new File(Variable.custom_autobackup_location);
      if (!autobackup.isDirectory()) {
         autobackup.mkdir();
      }

      Variable.CheckIsHome = Variable.Final;
      File aaa = new File(Variable.Final);
      if (!aaa.isDirectory()) {
         aaa.mkdir();
      }

      Variable.Tempf = Variable.Final;
      Variable.Tempf2 = Variable.Temp;
      Variable.f_log = new File(Variable.Log_All, "log.yml");
      if (!Variable.f_log.exists()) {
         try {
            Variable.f_log.createNewFile();
         } catch (IOException ioFailure) {
            ioFailure.printStackTrace();
         }

         Variable.f_log = new File(Variable.Log_All, "log.yml");
      }

      Variable.Papi_world = JavaPlugin.getConfig().getString("WorldName");
      File f2 = new File(Variable.worldFinal, "config.yml");
      Variable.getName_yml = YamlConfiguration.loadConfiguration(f2);
      Variable.GUI_YML = YamlConfiguration.loadConfiguration(new File(JavaPlugin.getDataFolder() + Variable.file_loc_prefix + "GUI.yml"));
      ConfigUpdate.update();
      String configuredSoil = JavaPlugin.getConfig().getString("SoilType");
      if (configuredSoil != null && !configuredSoil.trim().isEmpty()) {
         Variable.Soil = configuredSoil.trim();
      }
      HomeDataUpgrade.apply();
   }

   public void onPluginMessageReceived(String channel, Player player, byte[] message) {
      if (channel.equals("BungeeCord")) {
         ByteArrayDataInput in = ByteStreams.newDataInput(message);
         String subchannel = in.readUTF();
         if (subchannel.equals(BUNGEE_CHANNEL) || subchannel.equals(LEGACY_BUNGEE_CHANNEL)) {
            short len = in.readShort();
            byte[] msgbytes = new byte[len];
            in.readFully(msgbytes);
            DataInputStream msgin = new DataInputStream(new ByteArrayInputStream(msgbytes));

            try {
               String[] somedata = msgin.readUTF().split(",");
               if (somedata[0].equalsIgnoreCase("waitDelayToHome")) {
                  if (JavaPlugin.getConfig().getBoolean("Debug")) {
                     JavaPlugin.getLogger().info("[调试]:收到延时回家数据包:" + somedata[1] + "," + somedata[2]);
                  }

                  Variable.wait_to_spawn_home.put(somedata[1], somedata[2]);
               } else if (somedata[0].equalsIgnoreCase("waitToCommand")) {
                  if (JavaPlugin.getConfig().getBoolean("Debug")) {
                     JavaPlugin.getLogger().info("[调试]:收到延时执行指令数据包:" + somedata[1] + "," + somedata[2]);
                  }

                  Variable.wait_to_command.put(somedata[1], somedata[2]);
               } else if (somedata[0].equalsIgnoreCase("waitToLoad")) {
                  final WaitToLoad wt = new WaitToLoad();
                  wt.home_name = somedata[2];
                  wt.file_loc = somedata[3];
                  MySQL.setServer(wt.home_name, JavaPlugin.getConfig().getString("Server"));
                  File new_f = null;
                  if (Variable.world_prefix.equalsIgnoreCase("")) {
                     if (!Bukkit.getVersion().toString().toUpperCase().contains("ARCLIGHT") && !Bukkit.getVersion().toString().contains("1.20.1")) {
                        new_f = new File(Variable.single_server_gen + "world" + Variable.file_loc_prefix + wt.home_name);
                     } else {
                        new_f = new File(Variable.single_server_gen + Variable.world_prefix + wt.home_name);
                     }
                  } else {
                     new_f = new File(Variable.single_server_gen + Variable.world_prefix + wt.home_name);
                  }

                  if (new_f.isDirectory()) {
                     Util.deleteFile(new_f);
                  }

                  Util.copyDir(wt.file_loc + Variable.file_loc_prefix + wt.home_name, new_f.getAbsolutePath());
                  Variable.wait_to_command.put(somedata[1], "sh v " + wt.home_name);
                  Variable.has_already_move_world.add(somedata[1]);
                  (new BukkitRunnable() {
                     public void run() {
                        Util.deleteFile(new File(wt.file_loc + Variable.file_loc_prefix + wt.home_name));
                     }
                  }).runTaskLater(JavaPlugin, 20L);
               }
            } catch (IOException ioFailure) {
               ioFailure.printStackTrace();
            }
         }
      }
   }
}
