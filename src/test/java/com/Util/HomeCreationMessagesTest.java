package com.Util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Confirms language lookups degrade to the built-in default rather than to null. */
class HomeCreationMessagesTest {

   private static HomeCreationMessages messages(Map<String, String> language) {
      return new HomeCreationMessages(language::get);
   }

   @Test
   @DisplayName("a configured value wins over the default")
   void configuredValueWins() {
      Map<String, String> language = new HashMap<>();
      language.put("HomeCreationReady", "ready!");
      assertEquals("ready!", messages(language).text("HomeCreationReady", "fallback"));
   }

   @Test
   @DisplayName("a missing key falls back to the built-in default")
   void missingKeyFallsBack() {
      assertEquals("fallback", messages(new HashMap<>()).text("HomeCreationReady", "fallback"));
   }

   @Test
   @DisplayName("a blank configured value falls back instead of showing an empty line")
   void blankValueFallsBack() {
      Map<String, String> language = new HashMap<>();
      language.put("HomeCreationReady", "   ");
      assertEquals("fallback", messages(language).text("HomeCreationReady", "fallback"));
   }

   @Test
   @DisplayName("placeholders are substituted in both configured and default text")
   void placeholdersAreSubstituted() {
      Map<String, String> language = new HashMap<>();
      language.put("Queue", "position <position>");
      assertEquals("position 3", messages(language).format("Queue", "default <position>", "<position>", "3"));
      assertEquals("default 7", messages(new HashMap<>()).format("Queue", "default <position>", "<position>", "7"));
   }

   @Test
   @DisplayName("a null language provider is tolerated")
   void nullProviderIsTolerated() {
      HomeCreationMessages nullBacked = new HomeCreationMessages(key -> null);
      assertEquals("fallback", nullBacked.text("Anything", "fallback"));
   }
}
