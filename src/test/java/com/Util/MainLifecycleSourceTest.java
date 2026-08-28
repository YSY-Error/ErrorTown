package com.Util;

import static com.Util.SourceContract.read;
import static com.Util.SourceContract.region;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Guards two ordering rules inside {@code Main.onDisable()} that only a running server
 * could exercise.
 */
class MainLifecycleSourceTest {

   private static String onDisable() {
      return region(read("com", "ErrorTown", "Main.java"), "onDisable", "public void onDisable()", "\n   private ");
   }

   @Test
   @DisplayName("onDisable does not request another plugin disable")
   void onDisableDoesNotRequestPluginDisableAgain() {
      assertFalse(onDisable().contains("disablePlugin(this)"),
         "onDisable is already running and must not recursively request another disable");
   }

   @Test
   @DisplayName("the audit queue is flushed last, after every step that can still log")
   void auditFlushIsTheFinalShutdownStep() {
      String body = onDisable();
      int flush = body.indexOf("HomeAudit.flushForShutdown()");
      int lastWorldSave = body.lastIndexOf(".save()");

      assertTrue(flush > 0,
         "onDisable must call HomeAudit.flushForShutdown(); plain flush() returns without writing "
            + "when the async writer holds the single-writer token, and that writer dies with the plugin");
      assertTrue(lastWorldSave < 0 || flush > lastWorldSave,
         "the audit flush must come after the shutdown work, otherwise records emitted during shutdown are lost");
   }

   @Test
   @DisplayName("shutdown messages resolve language keys through the null-safe helper")
   void shutdownMessagesUseLangHelper() {
      assertFalse(onDisable().contains("Lang_YML.getString"),
         "onDisable runs when the language file may have failed to load; use Lang.get(key, fallback)");
   }
}
