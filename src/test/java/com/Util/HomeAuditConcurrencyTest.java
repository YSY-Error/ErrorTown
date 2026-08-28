package com.Util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Behavioural tests for the single-writer audit drain.
 *
 * <p>These replace an earlier source-grep test that only asserted the string
 * {@code "flushInProgress"} appeared somewhere in {@code HomeAudit.java}. That check
 * passed whether or not the guard was reachable, and said nothing about the two failure
 * modes that actually matter: a second writer interleaving with the first, and shutdown
 * dropping a batch because another writer held the token.</p>
 */
class HomeAuditConcurrencyTest {

   @AfterEach
   void tearDown() {
      HomeAudit.endSimulatedWriteForTests();
      HomeAudit.useStorageFolderForTests(null);
   }

   @Test
   @DisplayName("only one writer drains the queue at a time")
   void secondWriterIsRejectedWhileOneIsRunning(@TempDir Path folder) {
      HomeAudit.useStorageFolderForTests(folder.toFile());

      // Order matters: headless there is no scheduler, so log() drains inline. Taking the
      // token first is what makes log() queue the record instead of writing it, which is
      // exactly the state a real async write produces.
      HomeAudit.beginSimulatedWriteForTests();
      HomeAudit.log("first", null, "Alice", "1");
      assertEquals(1, HomeAudit.queuedRecordsForTests(), "log() must not write while a writer holds the token");

      HomeAudit.flush();
      assertEquals(1, HomeAudit.queuedRecordsForTests(),
         "a concurrent flush must leave the queue to the running writer");
      assertFalse(Files.exists(folder.resolve("audit.log")), "the rejected writer must not have written anything");

      HomeAudit.endSimulatedWriteForTests();
      HomeAudit.flush();
      assertEquals(0, HomeAudit.queuedRecordsForTests());
   }

   @Test
   @DisplayName("shutdown drains the queue even when a writer holds the token")
   void shutdownFlushDoesNotGiveUpOnAConcurrentWriter(@TempDir Path folder) throws IOException {
      HomeAudit.useStorageFolderForTests(folder.toFile());

      // This is the exact shape of the bug: onDisable used plain flush(), which returned
      // without writing while the async writer held the token — and that writer is killed
      // with the plugin, so the batch never reached disk.
      HomeAudit.beginSimulatedWriteForTests();
      HomeAudit.log("create.cost", null, "Alice", "59999");
      assertEquals(1, HomeAudit.queuedRecordsForTests());

      new Thread(() -> {
         try {
            Thread.sleep(80L);
         } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
         }
         HomeAudit.endSimulatedWriteForTests();
      }).start();

      int remaining = HomeAudit.flushForShutdown();

      assertEquals(0, remaining, "flushForShutdown must wait for the in-flight writer and then drain");
      String written = Files.readString(folder.resolve("audit.log"), StandardCharsets.UTF_8);
      assertTrue(written.contains("create.cost"), written);
   }

   @Test
   @DisplayName("shutdown gives up with a warning rather than hanging forever")
   void shutdownFlushIsBounded(@TempDir Path folder) {
      HomeAudit.useStorageFolderForTests(folder.toFile());

      // Never released: flushForShutdown must return on its own deadline.
      HomeAudit.beginSimulatedWriteForTests();
      HomeAudit.log("stuck", null, "Alice", "1");
      assertEquals(1, HomeAudit.queuedRecordsForTests());

      long start = System.nanoTime();
      int remaining = HomeAudit.flushForShutdown();
      long elapsedMillis = (System.nanoTime() - start) / 1_000_000L;

      assertEquals(1, remaining, "the record could not be written, so it must still be reported as queued");
      assertTrue(elapsedMillis < 10_000L, "flushForShutdown must be bounded, took " + elapsedMillis + " ms");
   }

   @Test
   @DisplayName("the writer token is released even when writing throws unexpectedly")
   void tokenIsReleasedAfterAFailedWrite(@TempDir Path folder) throws IOException {
      // audit.log as a directory makes every append fail.
      Path blocked = folder.resolve("audit.log");
      Files.createDirectory(blocked);

      HomeAudit.useStorageFolderForTests(folder.toFile());
      HomeAudit.log("first", null, "Alice", "1");
      HomeAudit.flush();

      assertFalse(HomeAudit.writeInProgressForTests(),
         "leaking the writer token would permanently stop the audit trail");
      assertEquals(1, HomeAudit.queuedRecordsForTests(), "the failed batch must stay queued");

      Files.delete(blocked);
      HomeAudit.flush();
      assertEquals(0, HomeAudit.queuedRecordsForTests());
   }

   @Test
   @DisplayName("records added while a writer runs are drained by that same writer")
   void runningWriterPicksUpLateRecords(@TempDir Path folder) throws IOException, InterruptedException {
      HomeAudit.useStorageFolderForTests(folder.toFile());

      int producers = 4;
      int perProducer = 25;
      CountDownLatch start = new CountDownLatch(1);
      CountDownLatch done = new CountDownLatch(producers);

      for (int p = 0; p < producers; p++) {
         final int id = p;
         new Thread(() -> {
            try {
               start.await();
               for (int i = 0; i < perProducer; i++) {
                  HomeAudit.log("t" + id, null, "Alice", String.valueOf(i));
               }
            } catch (InterruptedException interrupted) {
               Thread.currentThread().interrupt();
            } finally {
               done.countDown();
            }
         }).start();
      }

      start.countDown();
      assertTrue(done.await(10, TimeUnit.SECONDS), "producers must finish");

      // No scheduler in a headless test, so log() drained inline; make sure nothing is left.
      HomeAudit.flushForShutdown();

      assertEquals(0, HomeAudit.queuedRecordsForTests());
      List<String> lines = Files.readAllLines(folder.resolve("audit.log"), StandardCharsets.UTF_8);
      assertEquals(producers * perProducer, lines.size(),
         "every record must be written exactly once, got " + lines.size());
      assertTrue(lines.stream().allMatch(line -> line.contains(" | ")), "no line may be truncated or interleaved");
   }
}
