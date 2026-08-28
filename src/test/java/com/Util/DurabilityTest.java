package com.Util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Behavioural tests for the two durability contracts.
 *
 * <p>These replace the earlier source-text assertions (which grepped
 * {@code CreateCostLedger.java} for {@code "ATOMIC_MOVE"} and {@code HomeAudit.java} for
 * {@code "PENDING.addFirst"}). Grepping source cannot tell whether the code path is
 * actually reached, and it breaks on unrelated renames; these exercise the real
 * behaviour through the test storage hooks instead.</p>
 */
class DurabilityTest {

   @AfterEach
   void tearDown() {
      CreateCostLedger.resetForTests();
      HomeAudit.useStorageFolderForTests(null);
   }

   // ------------------------------------------------------------ ledger

   @Test
   @DisplayName("a recorded charge survives a reload from disk")
   void chargeSurvivesReload(@TempDir Path folder) {
      CreateCostLedger.useStorageFolderForTests(folder.toFile());
      CreateCostLedger.recordMoney("Alice", 59999.0);
      CreateCostLedger.recordPoints("Alice", 520);

      assertTrue(Files.exists(folder.resolve("create-cost-ledger.yml")), "the ledger must be written eagerly");

      // Simulate a restart: in-memory state is dropped, disk state is replayed.
      assertEquals(1, CreateCostLedger.reloadFromDiskForTests());
      assertEquals(59999.0, CreateCostLedger.recordedMoneyForTests("Alice"));
      assertEquals(520, CreateCostLedger.recordedPointsForTests("Alice"));
   }

   @Test
   @DisplayName("a reload replaces in-memory state instead of merging with it")
   void reloadDoesNotMergeStaleState(@TempDir Path folder) throws IOException {
      CreateCostLedger.useStorageFolderForTests(folder.toFile());
      CreateCostLedger.recordMoney("Alice", 100.0);
      CreateCostLedger.recordMoney("Bob", 250.0);
      assertTrue(CreateCostLedger.hasCharge("Bob"));

      // Rewrite the ledger so only Alice is on disk, leaving Bob in memory only. This is
      // the shape of a /reload inside one JVM: a merging load would keep Bob and later try
      // to refund a charge that is no longer recorded anywhere.
      Files.writeString(
         folder.resolve("create-cost-ledger.yml"),
         "charges:\n  Alice:\n    money: 100.0\n    points: 0\n",
         StandardCharsets.UTF_8
      );

      assertEquals(1, CreateCostLedger.reloadFromDiskForTests());
      assertTrue(CreateCostLedger.hasCharge("Alice"), "Alice was on disk and must come back");
      assertFalse(CreateCostLedger.hasCharge("Bob"), "Bob was only in memory and must not survive a reload");
   }

   @Test
   @DisplayName("a write after an unreloaded clear cannot resurrect removed rows")
   void persistReflectsCurrentMemoryExactly(@TempDir Path folder) throws IOException {
      CreateCostLedger.useStorageFolderForTests(folder.toFile());
      CreateCostLedger.recordMoney("Alice", 100.0);
      CreateCostLedger.recordMoney("Bob", 250.0);
      CreateCostLedger.settle("Alice");

      // persist() writes the whole in-memory map, so disk must equal memory after any
      // mutation - no stale row may linger.
      String written = Files.readString(folder.resolve("create-cost-ledger.yml"), StandardCharsets.UTF_8);
      assertFalse(written.contains("Alice"), written);
      assertTrue(written.contains("Bob"), written);
      assertEquals(1, CreateCostLedger.reloadFromDiskForTests());
   }

   @Test
   @DisplayName("settling a charge removes it from disk as well")
   void settleClearsDisk(@TempDir Path folder) throws IOException {
      CreateCostLedger.useStorageFolderForTests(folder.toFile());
      CreateCostLedger.recordMoney("Alice", 100.0);
      CreateCostLedger.settle("Alice");

      assertEquals(0, CreateCostLedger.reloadFromDiskForTests());
      String written = Files.readString(folder.resolve("create-cost-ledger.yml"), StandardCharsets.UTF_8);
      assertFalse(written.contains("Alice"), "a settled charge must not be left on disk: " + written);
   }

   @Test
   @DisplayName("writing the ledger leaves no temporary files behind")
   void atomicReplacementLeavesNoTempFiles(@TempDir Path folder) throws IOException {
      CreateCostLedger.useStorageFolderForTests(folder.toFile());
      for (int i = 0; i < 5; i++) {
         CreateCostLedger.recordMoney("Player" + i, 10.0 * (i + 1));
      }

      try (var stream = Files.list(folder)) {
         List<String> names = stream.map(path -> path.getFileName().toString()).toList();
         assertEquals(List.of("create-cost-ledger.yml"), names, "temp files from the atomic replace must be cleaned up");
      }
   }

   @Test
   @DisplayName("with persistence disabled the ledger still works in memory")
   void worksWithoutStorage() {
      CreateCostLedger.resetForTests();
      CreateCostLedger.recordMoney("Alice", 10.0);
      assertTrue(CreateCostLedger.hasCharge("Alice"));
      assertEquals(List.of("Alice"), CreateCostLedger.pendingPlayers());
   }

   // ------------------------------------------------------------ audit

   @Test
   @DisplayName("a record reaches disk on flush")
   void auditRecordIsWritten(@TempDir Path folder) throws IOException {
      HomeAudit.useStorageFolderForTests(folder.toFile());
      HomeAudit.log("create.cost", null, "Alice", "mode=money,cost=59999");
      HomeAudit.flush();

      assertEquals(0, HomeAudit.queuedRecordsForTests());
      String written = Files.readString(folder.resolve("audit.log"), StandardCharsets.UTF_8);
      assertTrue(written.contains("create.cost"), written);
      assertTrue(written.contains("Alice"), written);
      assertTrue(written.contains("CONSOLE"), "a null player must be recorded as CONSOLE: " + written);
   }

   @Test
   @DisplayName("a failed append returns the batch to the queue instead of dropping it")
   void failedAppendRequeuesRecords(@TempDir Path folder) throws IOException {
      // Make the append impossible: audit.log exists as a directory, so opening it for
      // append throws. Records must survive for the next attempt.
      Files.createDirectory(folder.resolve("audit.log"));

      HomeAudit.useStorageFolderForTests(folder.toFile());
      HomeAudit.log("create.cost", null, "Alice", "first");
      HomeAudit.log("create.refund", null, "Alice", "second");
      HomeAudit.flush();

      assertEquals(2, HomeAudit.queuedRecordsForTests(), "both records must be back in the queue");
   }

   @Test
   @DisplayName("requeued records keep their original order and are written on retry")
   void requeuedRecordsKeepOrder(@TempDir Path folder) throws IOException {
      Path blocked = folder.resolve("audit.log");
      Files.createDirectory(blocked);

      HomeAudit.useStorageFolderForTests(folder.toFile());
      HomeAudit.log("first", null, "Alice", "1");
      HomeAudit.log("second", null, "Alice", "2");
      HomeAudit.flush();
      assertEquals(2, HomeAudit.queuedRecordsForTests());

      // Unblock the path and retry.
      Files.delete(blocked);
      HomeAudit.flush();

      assertEquals(0, HomeAudit.queuedRecordsForTests());
      List<String> lines = Files.readAllLines(blocked, StandardCharsets.UTF_8);
      assertEquals(2, lines.size(), lines.toString());
      assertTrue(lines.get(0).contains("first"), lines.toString());
      assertTrue(lines.get(1).contains("second"), lines.toString());
   }

   @Test
   @DisplayName("a record with newlines or pipes stays on one greppable line")
   void recordsAreSanitised(@TempDir Path folder) throws IOException {
      HomeAudit.useStorageFolderForTests(folder.toFile());
      HomeAudit.log("create.cost", null, "Ali\nce", "a|b\r\nc");
      HomeAudit.flush();

      List<String> lines = Files.readAllLines(folder.resolve("audit.log"), StandardCharsets.UTF_8);
      assertEquals(1, lines.size(), lines.toString());
      assertFalse(lines.get(0).contains("\n"), lines.get(0));
   }
}
