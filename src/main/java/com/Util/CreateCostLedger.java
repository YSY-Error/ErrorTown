package com.Util;

import com.ErrorTown.Main;
import com.ErrorTown.Variable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Remembers what a player was charged for a home creation so the amount can be
 * returned when the creation does not happen.
 *
 * <p>Before this existed, {@code CreateCost} took the money up front and
 * {@link HomeCreationCoordinator#fail} only cleared the "already paid" flag. Every
 * failure path — world creation error, chunk generation error, creator disconnected,
 * queue timeout, stale-slot reap — therefore consumed the payment and produced no
 * home. With the default config that is 59999 currency plus 520 points per incident.</p>
 *
 * <p><b>Durability.</b> The ledger is written to {@code create-cost-ledger.yml} on every
 * mutation and replayed by {@link #recoverPending()} during {@code onEnable}. Without
 * that, a hard crash or {@code /stop} between "money taken" and "world ready" destroyed
 * the record and the player simply lost the payment. Charges are rare (one per home
 * creation) and the file holds at most a handful of rows, so a synchronous write is
 * cheaper than the machinery needed to batch it.</p>
 *
 * <p>The ledger is keyed by player name to match the rest of the plugin's storage.
 * Refunds go through {@code OfflinePlayer} so a disconnected creator is still paid back.
 * A single unsettled charge per player is enforced by the caller, which is what keeps the
 * name key unambiguous.</p>
 */
public final class CreateCostLedger {
   private static final String FILE_NAME = "create-cost-ledger.yml";
   private static final Map<String, Charge> CHARGES = new LinkedHashMap<>();

   /** Set to false in headless tests so the class works without a plugin data folder. */
   private static boolean persistenceEnabled = true;

   /** Overrides the plugin data folder in headless tests. Null means "use the plugin's". */
   private static File storageFolder;

   private CreateCostLedger() {
   }

   /** Records a currency charge. Call immediately after a confirmed withdrawal. */
   public static synchronized void recordMoney(String playerName, double amount) {
      if (playerName == null || amount <= 0.0) {
         return;
      }
      charge(playerName).money += amount;
      persist();
   }

   /** Records a PlayerPoints charge. Call immediately after a confirmed take. */
   public static synchronized void recordPoints(String playerName, int amount) {
      if (playerName == null || amount <= 0) {
         return;
      }
      charge(playerName).points += amount;
      persist();
   }

   /** Drops the record without paying anything back. Call once the home exists. */
   public static synchronized void settle(String playerName) {
      if (playerName != null && CHARGES.remove(playerName) != null) {
         persist();
      }
   }

   public static synchronized boolean hasCharge(String playerName) {
      return playerName != null && CHARGES.containsKey(playerName);
   }

   /** Names with an outstanding, not yet refunded charge. */
   public static synchronized List<String> pendingPlayers() {
      return new ArrayList<>(CHARGES.keySet());
   }

   /** Retries every retained refund after a provider outage. */
   public static synchronized int retryPendingRefunds() {
      int completed = 0;
      for (String playerName : new ArrayList<>(CHARGES.keySet())) {
         refund(playerName);
         if (!CHARGES.containsKey(playerName)) {
            completed++;
         }
      }
      return completed;
   }

   /**
    * Returns everything recorded for {@code playerName}.
    *
    * <p>A partially successful refund keeps the unpaid remainder on record so a later
    * attempt can finish it; the record is dropped only once nothing is owed.</p>
    *
    * @return a human-readable summary of what was actually paid back, or {@code null}
    *         when nothing could be refunded
    */
   public static synchronized String refund(String playerName) {
      if (playerName == null) {
         return null;
      }
      Charge charge = CHARGES.get(playerName);
      if (charge == null || (charge.money <= 0.0 && charge.points <= 0)) {
         return null;
      }

      StringBuilder summary = new StringBuilder();
      double refundedMoney = 0.0;
      int refundedPoints = 0;

      if (charge.money > 0.0) {
         if (Variable.econ != null) {
            try {
               OfflinePlayer target = Bukkit.getOfflinePlayer(playerName);
               if (!Variable.econ.depositPlayer(target, charge.money).transactionSuccess()) {
                  throw new IllegalStateException("Vault rejected deposit");
               }
               summary.append((long)charge.money).append(" 金币");
               refundedMoney = charge.money;
               charge.money = 0.0;
            } catch (RuntimeException failure) {
               warn("Failed to refund " + (long)charge.money + " money to " + playerName, failure);
            }
         } else {
            warn("Cannot refund " + (long)charge.money + " money to " + playerName + ": Vault economy is unavailable", null);
         }
      }

      if (charge.points > 0) {
         if (Variable.PlyaerPointsModule && Variable.playerPoints != null) {
            try {
               if (!Variable.playerPoints.getAPI().give(Bukkit.getOfflinePlayer(playerName).getUniqueId(), charge.points)) {
                  throw new IllegalStateException("PlayerPoints rejected grant");
               }
               if (summary.length() > 0) {
                  summary.append(" 与 ");
               }
               summary.append(charge.points).append(" 点券");
               refundedPoints = charge.points;
               charge.points = 0;
            } catch (RuntimeException failure) {
               warn("Failed to refund " + charge.points + " points to " + playerName, failure);
            }
         } else {
            warn("Cannot refund " + charge.points + " points to " + playerName + ": PlayerPoints is unavailable", null);
         }
      }

      if (charge.money <= 0.0 && charge.points <= 0) {
         CHARGES.remove(playerName);
      }
      persist();

      if (summary.length() == 0) {
         return null;
      }
      HomeAudit.log("create.refund", null, playerName, "money=" + (long)refundedMoney + ",points=" + refundedPoints);
      return summary.toString();
   }

   // ------------------------------------------------------------- durability

   /**
    * Loads the on-disk ledger and refunds anything left over from a previous run.
    *
    * <p>Call from {@code onEnable} <em>after</em> Vault and PlayerPoints are hooked,
    * otherwise the refund attempt has no provider and the rows simply stay for the
    * periodic retry.</p>
    *
    * @return the number of players whose outstanding charge was fully returned
    */
   public static synchronized int recoverPending() {
      load();
      if (CHARGES.isEmpty()) {
         return 0;
      }
      Main.JavaPlugin
         .getLogger()
         .warning("Found " + CHARGES.size() + " unsettled home-creation charge(s) from a previous run; attempting refunds.");
      return retryPendingRefunds();
   }

   private static void load() {
      // A reload must represent disk state, not merge it with charges left by the
      // previous plugin instance in this JVM.
      CHARGES.clear();
      File file = ledgerFile();
      if (file == null || !file.isFile()) {
         return;
      }
      YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
      ConfigurationSection root = yaml.getConfigurationSection("charges");
      if (root == null) {
         return;
      }
      for (String playerName : root.getKeys(false)) {
         double money = root.getDouble(playerName + ".money", 0.0);
         int points = root.getInt(playerName + ".points", 0);
         if (money > 0.0 || points > 0) {
            Charge charge = charge(playerName);
            charge.money = money;
            charge.points = points;
         }
      }
   }

   private static void persist() {
      File file = ledgerFile();
      if (file == null) {
         return;
      }
      YamlConfiguration yaml = new YamlConfiguration();
      for (Map.Entry<String, Charge> entry : CHARGES.entrySet()) {
         Charge charge = entry.getValue();
         if (charge.money <= 0.0 && charge.points <= 0) {
            continue;
         }
         yaml.set("charges." + entry.getKey() + ".money", charge.money);
         yaml.set("charges." + entry.getKey() + ".points", charge.points);
      }
      try {
         Path target = file.toPath();
         Path temporary = Files.createTempFile(target.getParent(), FILE_NAME, ".tmp");
         try {
            yaml.save(temporary.toFile());
            try {
               Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
               Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
         } finally {
            Files.deleteIfExists(temporary);
         }
      } catch (IOException failure) {
         // Losing durability is serious: say so rather than silently degrading to memory.
         warn("Could not write " + FILE_NAME + "; an unsettled charge would be lost on shutdown", failure);
      }
   }

   private static File ledgerFile() {
      if (!persistenceEnabled) {
         return null;
      }
      File folder = storageFolder != null ? storageFolder : (Main.JavaPlugin == null ? null : Main.JavaPlugin.getDataFolder());
      if (folder == null) {
         return null;
      }
      if (!folder.isDirectory() && !folder.mkdirs()) {
         return null;
      }
      return new File(folder, FILE_NAME);
   }

   /**
    * Test hook: routes the ledger at {@code folder} and clears in-memory state.
    *
    * <p>Exists so the durability contract (reload starts from disk, writes replace the
    * file atomically) can be asserted by behaviour instead of by grepping this file's
    * source text.</p>
    *
    * @param folder directory to store the ledger in, or {@code null} to disable disk access
    */
   public static synchronized void useStorageFolderForTests(File folder) {
      storageFolder = folder;
      persistenceEnabled = folder != null;
      CHARGES.clear();
   }

   /** Test hook: forces a reload from disk and reports how many charges were read. */
   public static synchronized int reloadFromDiskForTests() {
      load();
      return CHARGES.size();
   }

   /** Test hook: disables disk access and clears state. */
   public static synchronized void resetForTests() {
      storageFolder = null;
      persistenceEnabled = false;
      CHARGES.clear();
   }

   /** Test hook: the outstanding amounts recorded for {@code playerName}. */
   public static synchronized double recordedMoneyForTests(String playerName) {
      Charge charge = CHARGES.get(playerName);
      return charge == null ? 0.0 : charge.money;
   }

   public static synchronized int recordedPointsForTests(String playerName) {
      Charge charge = CHARGES.get(playerName);
      return charge == null ? 0 : charge.points;
   }

   private static Charge charge(String playerName) {
      return CHARGES.computeIfAbsent(playerName, key -> new Charge());
   }

   private static void warn(String message, Throwable failure) {
      if (Main.JavaPlugin == null) {
         return;
      }
      if (failure == null) {
         Main.JavaPlugin.getLogger().warning(message);
      } else {
         Main.JavaPlugin.getLogger().log(Level.WARNING, message, failure);
      }
   }

   private static final class Charge {
      private double money;
      private int points;
   }
}
