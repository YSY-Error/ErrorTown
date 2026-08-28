package com.Util;

import com.ErrorTown.Main;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Appends an audit record to {@code audit.log}.
 *
 * <p><b>Why it is not YAML.</b> The original version called
 * {@code YamlConfiguration.loadConfiguration(log.yml)} then {@code save()} for every
 * record, on the calling thread. That parses and rewrites the whole file each time, so
 * the cost of writing one record grew linearly with the number of records already
 * stored — an audit trail that gets slower the more it is used, on the main thread.</p>
 *
 * <h2>Concurrency model</h2>
 *
 * <p>Records are single lines appended to a plain text file, buffered in memory and
 * written by <b>exactly one</b> writer at a time. {@link #writing} is the single-writer
 * token: {@link #log} only schedules a new drain when nobody holds it, and the drain loop
 * keeps going until the queue is empty, so a record produced while a write is in flight
 * is still picked up by the running writer rather than by a second one.</p>
 *
 * <p>Two things this class must never do, both of which were real defects:</p>
 * <ul>
 *   <li><b>Lose records at shutdown.</b> A plain {@code flush()} that returns early
 *       because another writer holds the token is wrong for {@code onDisable}: the async
 *       writer dies with the plugin and its batch is gone. Shutdown must call
 *       {@link #flushForShutdown()}, which waits for the in-flight writer and then drains
 *       inline.</li>
 *   <li><b>Leak the token.</b> If the writer throws anything other than
 *       {@link IOException} — a {@code RuntimeException} from the filesystem layer, an
 *       {@code Error} — the token must still be released, otherwise the audit trail is
 *       permanently dead and {@link #log} never schedules again. Hence try/finally.</li>
 * </ul>
 */
public final class HomeAudit {
   private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
   private static final String FILE_NAME = "audit.log";
   private static final int MAX_QUEUE = 4096;

   /** How long {@link #flushForShutdown()} waits for an in-flight async writer. */
   private static final long SHUTDOWN_WAIT_MILLIS = 2000L;

   /** Delay before retrying a write that failed, in server ticks. */
   private static final long RETRY_DELAY_TICKS = 100L;

   private static final ArrayDeque<String> PENDING = new ArrayDeque<>();

   /** A drain has been handed to the scheduler but has not started yet. */
   private static boolean drainScheduled;

   /** The single-writer token. True means some thread is inside the drain loop. */
   private static boolean writing;

   private static boolean overflowReported;
   private static File storageFolder;

   private HomeAudit() {
   }

   public static void log(String type, Player player, String homeName, String detail) {
      String line = TS.format(LocalDateTime.now())
         + " | " + safe(type)
         + " | " + (player != null ? player.getName() : "CONSOLE")
         + " | " + safe(homeName)
         + " | " + safe(detail);

      boolean needsSchedule = false;
      synchronized (PENDING) {
         if (PENDING.size() >= MAX_QUEUE) {
            if (!overflowReported) {
               overflowReported = true;
               warn("Audit queue is full (" + MAX_QUEUE + " records); further records are dropped until it drains.", null);
            }
            return;
         }
         PENDING.add(line);
         // A running writer drains until the queue is empty, so it will pick this up.
         if (!drainScheduled && !writing) {
            drainScheduled = true;
            needsSchedule = true;
         }
      }
      if (needsSchedule) {
         scheduleDrain();
      }
   }

   /**
    * Writes everything queued so far, unless another writer is already doing it.
    *
    * <p>Safe to call from any thread. Returns immediately when another writer holds the
    * token — that writer will drain the queue. For shutdown use
    * {@link #flushForShutdown()} instead.</p>
    */
   public static void flush() {
      if (!acquireWriter()) {
         return;
      }
      drainUntilEmpty();
   }

   /**
    * Drains the queue during shutdown, waiting briefly for an in-flight async writer.
    *
    * <p>{@link #flush()} may not be used here: if the async writer holds the token it
    * would return without writing, and the async task is about to be cancelled with the
    * plugin, so its batch would never reach disk.</p>
    *
    * @return the number of records still queued afterwards; 0 means everything was written
    */
   public static int flushForShutdown() {
      long deadline = System.nanoTime() + SHUTDOWN_WAIT_MILLIS * 1_000_000L;
      while (true) {
         if (acquireWriter()) {
            drainUntilEmpty();
            break;
         }
         if (System.nanoTime() >= deadline) {
            warn("An audit write was still running after " + SHUTDOWN_WAIT_MILLIS + " ms; "
               + queuedRecordsForTests() + " record(s) may not reach disk.", null);
            break;
         }
         try {
            Thread.sleep(20L);
         } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            break;
         }
      }
      return queuedRecordsForTests();
   }

   /**
    * Takes the single-writer token.
    *
    * @return true when this thread may write; false when another writer already holds it,
    *         when there is nothing to write, or when there is nowhere to write yet
    */
   private static boolean acquireWriter() {
      synchronized (PENDING) {
         if (writing) {
            return false;
         }
         if (PENDING.isEmpty()) {
            drainScheduled = false;
            return false;
         }
         if (auditFile() == null) {
            // Nowhere to write yet: keep the records queued for the next attempt.
            drainScheduled = false;
            return false;
         }
         writing = true;
         drainScheduled = false;
         return true;
      }
   }

   /**
    * Drains the queue until it is empty or a write fails.
    *
    * <p>The caller must hold the writer token. It is always released here, including on
    * an unexpected {@code RuntimeException} or {@code Error}; leaking it would silently
    * stop the whole audit trail.</p>
    */
   private static void drainUntilEmpty() {
      boolean rescheduleAfterFailure = false;
      try {
         while (true) {
            File target = auditFile();
            StringBuilder batch = new StringBuilder();
            synchronized (PENDING) {
               if (PENDING.isEmpty() || target == null) {
                  return;
               }
               String line;
               while ((line = PENDING.poll()) != null) {
                  batch.append(line).append(System.lineSeparator());
               }
               overflowReported = false;
            }

            try (BufferedWriter writer = Files.newBufferedWriter(
                  target.toPath(), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
               writer.write(batch.toString());
            } catch (IOException failure) {
               requeueBatch(batch);
               rescheduleAfterFailure = true;
               warn("Failed to append " + FILE_NAME + "; " + batch.length() + " bytes were returned to the audit queue", failure);
               return;
            }
         }
      } finally {
         synchronized (PENDING) {
            writing = false;
         }
         // A failed write leaves records queued with nobody scheduled to retry them. Hand
         // the retry to the scheduler so they are not stuck until the next log() call.
         // This must never fall back to an inline call: an inline retry would re-enter the
         // same failing write immediately and recurse until the stack overflows.
         if (rescheduleAfterFailure) {
            requestScheduledRetry();
         }
      }
   }

   /** Puts a failed batch back at the head of the queue, preserving record order. */
   private static void requeueBatch(StringBuilder batch) {
      String[] lines = batch.toString().split("\\R");
      synchronized (PENDING) {
         for (int index = lines.length - 1; index >= 0; index--) {
            if (!lines[index].isEmpty()) {
               PENDING.addFirst(lines[index]);
            }
         }
      }
   }

   /**
    * Asks the scheduler to retry a failed write after a short delay.
    *
    * <p>Unlike {@link #scheduleDrain()} this never writes inline. Without a scheduler
    * there is nothing useful to do: retrying immediately would hit the same failure and
    * recurse. The records stay queued for the next {@link #log} or
    * {@link #flushForShutdown()}.</p>
    */
   private static void requestScheduledRetry() {
      if (Main.JavaPlugin == null || !Main.JavaPlugin.isEnabled()) {
         return;
      }
      boolean needsSchedule = false;
      synchronized (PENDING) {
         if (!PENDING.isEmpty() && !drainScheduled && !writing) {
            drainScheduled = true;
            needsSchedule = true;
         }
      }
      if (!needsSchedule) {
         return;
      }
      try {
         Bukkit.getScheduler().runTaskLaterAsynchronously(Main.JavaPlugin, HomeAudit::flush, RETRY_DELAY_TICKS);
      } catch (IllegalStateException schedulerUnavailable) {
         synchronized (PENDING) {
            drainScheduled = false;
         }
      }
   }

   private static void scheduleDrain() {
      if (Main.JavaPlugin == null || !Main.JavaPlugin.isEnabled()) {
         flush();
         return;
      }
      try {
         Bukkit.getScheduler().runTaskLaterAsynchronously(Main.JavaPlugin, HomeAudit::flush, 1L);
      } catch (IllegalStateException schedulerUnavailable) {
         // Shutdown already started: write inline instead of losing the record.
         flush();
      }
   }

   private static File auditFile() {
      File folder = storageFolder != null ? storageFolder : (Main.JavaPlugin == null ? null : Main.JavaPlugin.getDataFolder());
      if (folder == null || (!folder.isDirectory() && !folder.mkdirs())) {
         return null;
      }
      return new File(folder, FILE_NAME);
   }

   /**
    * Test hook: routes audit output at {@code folder} and empties the queue.
    *
    * <p>{@code null} disables disk access, which is also what a headless test without a
    * plugin instance sees.</p>
    */
   public static void useStorageFolderForTests(File folder) {
      synchronized (PENDING) {
         storageFolder = folder;
         PENDING.clear();
         drainScheduled = false;
         writing = false;
         overflowReported = false;
      }
   }

   /** Test hook: number of records still waiting to be written. */
   public static int queuedRecordsForTests() {
      synchronized (PENDING) {
         return PENDING.size();
      }
   }

   /** Test hook: true while a writer holds the single-writer token. */
   public static boolean writeInProgressForTests() {
      synchronized (PENDING) {
         return writing;
      }
   }

   /** Test hook: forcibly marks a write as running, to simulate an overlapping writer. */
   public static void beginSimulatedWriteForTests() {
      synchronized (PENDING) {
         writing = true;
      }
   }

   /** Test hook: releases a simulated write. */
   public static void endSimulatedWriteForTests() {
      synchronized (PENDING) {
         writing = false;
      }
   }

   /** Keeps one record on one line so the file stays greppable. */
   private static String safe(String value) {
      if (value == null) {
         return "-";
      }
      return value.replace('\r', ' ').replace('\n', ' ').replace('|', '/');
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
}
