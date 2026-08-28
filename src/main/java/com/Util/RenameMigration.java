package com.Util;

import com.ErrorTown.Main;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * One-time migrations for the SummerTown → ErrorTown rename.
 *
 * <p>Three pieces of state are addressed by the plugin's own name and therefore move when
 * the plugin is renamed. Two of them can be migrated automatically and are handled here;
 * the third cannot and is documented instead.</p>
 *
 * <table>
 *   <caption>Rename impact</caption>
 *   <tr><th>State</th><th>Handling</th></tr>
 *   <tr><td>Data folder {@code plugins/SummerTown/}</td>
 *       <td>{@link #migrateDataFolder()} renames it to {@code plugins/ErrorTown/} before any
 *           default is written, so configs, home files and backups follow the plugin.</td></tr>
 *   <tr><td>MySQL tables {@code SummerTown_Users} / {@code SummerTown_Servers}</td>
 *       <td>{@link #migrateDatabaseTables()} renames them when the new names do not exist
 *           yet. Without this a cross-server network silently starts with empty data.</td></tr>
 *   <tr><td>Permission nodes {@code SummerTown.*}</td>
 *       <td><b>Cannot</b> be migrated: they live in LuckPerms or a similar manager.
 *           {@link Perm} keeps honouring them until the operator flips
 *           {@code LegacyPermissions: false}.</td></tr>
 * </table>
 *
 * <p>Every method is idempotent and safe to call on an already-migrated install.</p>
 */
public final class RenameMigration {

   private RenameMigration() {
   }

   /**
    * Moves {@code plugins/SummerTown/} to {@code plugins/ErrorTown/} when only the old one
    * exists.
    *
    * <p>Must run before the plugin writes any default resource, otherwise the new folder is
    * created first and the migration is skipped, leaving the real data orphaned. A rename is
    * attempted first; if the filesystem refuses it (different volumes, open handles) the
    * contents are copied and the source is left in place as a manual fallback.</p>
    *
    * @return true when a migration was performed
    */
   public static boolean migrateDataFolder() {
      if (Main.JavaPlugin == null) {
         return false;
      }
      File target = Main.JavaPlugin.getDataFolder();
      if (target == null) {
         return false;
      }
      File legacy = new File(target.getParentFile(), Main.LEGACY_PLUGIN_FOLDER);
      if (!legacy.isDirectory()) {
         return false;
      }
      if (target.isDirectory() && !isEffectivelyEmpty(target)) {
         Diag.warn(
            "Both plugins/" + Main.LEGACY_PLUGIN_FOLDER + "/ and plugins/" + Main.PLUGIN_FOLDER
               + "/ exist and the new one is not empty; leaving both untouched. Merge them by hand — "
               + "see docs/migration-to-errortown.md."
         );
         return false;
      }

      try {
         if (target.isDirectory()) {
            // Freshly created and empty: remove it so the rename can take the name.
            Files.deleteIfExists(target.toPath());
         }
         Files.move(legacy.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE);
         info("Migrated plugins/" + Main.LEGACY_PLUGIN_FOLDER + "/ to plugins/" + Main.PLUGIN_FOLDER + "/.");
         return true;
      } catch (IOException | UnsupportedOperationException renameFailed) {
         return copyDataFolder(legacy, target, renameFailed);
      }
   }

   private static boolean copyDataFolder(File legacy, File target, Exception cause) {
      try {
         copyTree(legacy.toPath(), target.toPath());
         info(
            "Copied plugins/" + Main.LEGACY_PLUGIN_FOLDER + "/ to plugins/" + Main.PLUGIN_FOLDER
               + "/ because it could not be renamed. The old folder is left in place; delete it once you have "
               + "verified the new one."
         );
         return true;
      } catch (IOException copyFailed) {
         Diag.warn(
            "Could not migrate plugins/" + Main.LEGACY_PLUGIN_FOLDER + "/ to plugins/" + Main.PLUGIN_FOLDER
               + "/. The plugin will start with fresh defaults; move the folder by hand before letting players in. "
               + "Rename attempt failed with: " + cause,
            copyFailed
         );
         return false;
      }
   }

   private static void copyTree(Path from, Path to) throws IOException {
      try (var stream = Files.walk(from)) {
         for (Path source : stream.toList()) {
            Path destination = to.resolve(from.relativize(source).toString());
            if (Files.isDirectory(source)) {
               Files.createDirectories(destination);
            } else {
               Files.createDirectories(destination.getParent());
               Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
            }
         }
      }
   }

   private static boolean isEffectivelyEmpty(File folder) {
      String[] entries = folder.list();
      return entries == null || entries.length == 0;
   }

   /**
    * Renames the pre-rename MySQL tables when the current ones do not exist yet.
    *
    * <p>Call before {@code MySQL.init()} creates empty tables: once
    * {@code CREATE TABLE IF NOT EXISTS} has run, the new table exists and this method has
    * nothing to do, which would leave every home invisible on a cross-server network.</p>
    *
    * @return the table names that were migrated
    */
   public static List<String> migrateDatabaseTables() {
      List<String> migrated = new ArrayList<>();
      Connection connection = MySQL.getConnection();
      if (connection == null) {
         return migrated;
      }
      migrateTable(connection, "SummerTown_Users", "ErrorTown_Users", migrated);
      migrateTable(connection, "SummerTown_Servers", "ErrorTown_Servers", migrated);
      if (!migrated.isEmpty()) {
         info("Renamed database table(s) for the ErrorTown rename: " + migrated);
      }
      return migrated;
   }

   private static void migrateTable(Connection connection, String legacyName, String currentName, List<String> migrated) {
      try {
         if (!tableExists(connection, legacyName) || tableExists(connection, currentName)) {
            return;
         }
         // Table identifiers cannot be bound as parameters; both names are compile-time
         // constants from this class, never user input.
         try (PreparedStatement rename = connection.prepareStatement("RENAME TABLE " + legacyName + " TO " + currentName)) {
            rename.executeUpdate();
         }
         migrated.add(legacyName + " -> " + currentName);
      } catch (SQLException failure) {
         Diag.warn(
            "Could not rename " + legacyName + " to " + currentName
               + ". Cross-server data will look empty until this is done by hand — see docs/migration-to-errortown.md.",
            failure
         );
      }
   }

   private static boolean tableExists(Connection connection, String tableName) throws SQLException {
      try (ResultSet tables = connection.getMetaData().getTables(null, null, tableName, new String[]{"TABLE"})) {
         return tables.next();
      }
   }

   private static void info(String message) {
      if (Main.JavaPlugin != null) {
         Main.JavaPlugin.getLogger().info(message);
      }
   }
}
