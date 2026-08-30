package com.Util;

import com.ErrorTown.Main;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.SQLException;
import org.bukkit.Bukkit;

public class HikariCPUtils {
   public static HikariDataSource sqlConnectionPool;

   public static void setSqlConnectionPool() {
      HikariConfig hikariConfig = new HikariConfig();

      // HikariCP hard-rejects connectionTimeout below 250 ms and silently forces
      // minimumIdle down to maximumPoolSize when the two are inverted. The shipped
      // default (minimumIdle 50 > maximumPoolSize 30) hits the second case, which held
      // 30 permanent connections open. Clamp explicitly and tell the operator.
      long timeout = Math.max(250L, Main.JavaPlugin.getConfig().getLong("HikariCP.connectionTimeout"));
      int maxPool = Math.max(1, Main.JavaPlugin.getConfig().getInt("HikariCP.maximumPoolSize"));
      int configuredIdle = Main.JavaPlugin.getConfig().getInt("HikariCP.minimumIdle");
      int minIdle = Math.max(0, Math.min(configuredIdle, maxPool));
      if (configuredIdle > maxPool) {
         Diag.warn(
            "HikariCP.minimumIdle (" + configuredIdle + ") exceeds maximumPoolSize (" + maxPool + "); using " + minIdle
               + ". Lower minimumIdle in config.yml to stop holding the whole pool open."
         );
      }

      hikariConfig.setConnectionTimeout(timeout);
      hikariConfig.setMinimumIdle(minIdle);
      hikariConfig.setMaximumPoolSize(maxPool);
      hikariConfig.setPoolName("ErrorTown");
      hikariConfig.setIdleTimeout(600000L);
      hikariConfig.setMaxLifetime(800000L);
      hikariConfig.setConnectionTestQuery("SELECT 1");
      String type = Main.JavaPlugin.getConfig().getString("Type");
      String host = Main.JavaPlugin.getConfig().getString("Host");
      String port = String.valueOf(Main.JavaPlugin.getConfig().getInt("Port"));
      String database = Main.JavaPlugin.getConfig().getString("Database");
      String username = Main.JavaPlugin.getConfig().getString("Username");
      String password = Main.JavaPlugin.getConfig().getString("Password");
      String url = "jdbc:"
         + type
         + "://"
         + host
         + ":"
         + port
         + "/"
         + database
         + "?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&autoReconnect=true";
      hikariConfig.setJdbcUrl(url);
      hikariConfig.setUsername(username);
      hikariConfig.setPassword(password);
      hikariConfig.setAutoCommit(true);
      // `/sh reload` runs Main.init() again, which calls this method again. Without closing the
      // previous pool first its connections are never released, so every reload strands a whole
      // maximumPoolSize worth of them (30 by default) until the server restarts.
      shutdown();
      sqlConnectionPool = new HikariDataSource(hikariConfig);

      try {
         if (sqlConnectionPool.getConnection() != null) {
            Bukkit.getConsoleSender().sendMessage(Lang.get("DataBaseConnectionSuccess", "§a[ErrorTown] 数据库连接成功"));
            // Must precede init(): CREATE TABLE IF NOT EXISTS would create empty tables and
            // make the pre-rename data look like it disappeared.
            RenameMigration.migrateDatabaseTables();
            MySQL.init();
         }
      } catch (SQLException sqlFailure) {
         Bukkit.getConsoleSender().sendMessage(Lang.get("DataBaseConnectionError", "§c[ErrorTown] 数据库连接失败"));
         Diag.warn("Database connection failed; cross-server features will not work", sqlFailure);
      }
   }

   /**
    * Closes the connection pool if one is open, leaving {@link #sqlConnectionPool} null.
    *
    * <p>Safe to call when no pool was ever created (single-server mode never opens one) and
    * safe to call twice. {@link MySQL#getConnection()} tolerates the null in between.
    */
   public static void shutdown() {
      HikariDataSource pool = sqlConnectionPool;
      sqlConnectionPool = null;
      if (pool != null && !pool.isClosed()) {
         try {
            pool.close();
         } catch (Throwable closeFailure) {
            Diag.warn("Closing the database connection pool failed", closeFailure);
         }
      }
   }
}
