package com.Util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/**
 * Contract for the {@code Help-1} … {@code Help-6} entries in every shipped language file.
 *
 * <p>{@code /sh help} was listed as a known broken command. The cause was not the renderer: the
 * {@code Help-N} keys were absent from all three language files, so the command printed a header and
 * a footer with nothing between them. Nothing in a normal build fails when that happens, which is
 * exactly why it went unnoticed — hence this test.</p>
 *
 * <p>Each entry is {@code "display text,command"}. {@link ClickableText#sendPairLine} splits on the
 * first comma, so a comma inside the display text would silently truncate the line and turn the rest
 * into the click command.</p>
 */
class HelpPagesResourceTest {
   private static final List<String> LANGUAGES = List.of("Chinese", "Chinese_TW", "English");
   private static final int PAGES = 6;

   private static Map<?, ?> language(String name) throws IOException {
      Path file = Path.of("src", "main", "resources", "Language", name + ".yml");
      assertTrue(Files.isRegularFile(file), "missing language file: " + file);
      try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
         Object loaded = new Yaml().load(reader);
         assertTrue(loaded instanceof Map, name + ".yml must parse as a YAML mapping");
         return (Map<?, ?>)loaded;
      }
   }

   private static List<String> page(Map<?, ?> language, int number) {
      Object value = language.get("Help-" + number);
      assertNotNull(value, "Help-" + number + " is missing; /sh help " + number + " would print an empty page");
      assertTrue(value instanceof List, "Help-" + number + " must be a list of \"text,command\" entries");
      List<String> lines = new ArrayList<>();
      for (Object entry : (List<?>)value) {
         lines.add(String.valueOf(entry));
      }
      return lines;
   }

   @Test
   @DisplayName("every language ships all six help pages, and none of them is empty")
   void allLanguagesShipEveryPage() throws IOException {
      for (String name : LANGUAGES) {
         Map<?, ?> language = language(name);
         for (int number = 1; number <= PAGES; number++) {
            assertFalse(page(language, number).isEmpty(), name + " Help-" + number + " is empty");
         }
      }
   }

   @Test
   @DisplayName("a command entry has exactly one comma, so the display text cannot be truncated")
   void commandEntriesHaveASingleComma() throws IOException {
      for (String name : LANGUAGES) {
         Map<?, ?> language = language(name);
         for (int number = 1; number <= PAGES; number++) {
            for (String line : page(language, number)) {
               int first = line.indexOf(',');
               if (first < 0) {
                  // Section headings carry no command; ClickableText sends them as plain text.
                  continue;
               }
               assertEquals(
                  first,
                  line.lastIndexOf(','),
                  name + " Help-" + number + " entry has more than one comma, which would move part of "
                     + "the display text into the click command: " + line
               );
            }
         }
      }
   }

   @Test
   @DisplayName("every command entry points at this plugin's own command")
   void commandEntriesTargetThePluginCommand() throws IOException {
      for (String name : LANGUAGES) {
         Map<?, ?> language = language(name);
         for (int number = 1; number <= PAGES; number++) {
            for (String line : page(language, number)) {
               String[] parts = line.split(",", 2);
               if (parts.length < 2) {
                  continue;
               }
               String command = parts[1].trim();
               assertTrue(
                  command.startsWith("/sh ") || command.equals("/sh"),
                  name + " Help-" + number + " entry has a click command that is not a /sh command: " + command
               );
               assertFalse(parts[0].trim().isEmpty(), name + " Help-" + number + " entry has no display text: " + line);
            }
         }
      }
   }

   @Test
   @DisplayName("navigation lines are recognisable so they run instead of only suggesting")
   void everyPageCanBeNavigatedAwayFrom() throws IOException {
      // ClickableText treats a line as navigation by looking for these markers; a page whose
      // navigation line does not contain one would only pre-fill the chat box, which is a dead end
      // for a player who clicked "next page".
      List<String> markers = List.of("下一", "上一", "第一", "Next", "Prev", "First");
      for (String name : LANGUAGES) {
         Map<?, ?> language = language(name);
         for (int number = 1; number <= PAGES; number++) {
            boolean hasNavigation = false;
            for (String line : page(language, number)) {
               String display = line.split(",", 2)[0];
               for (String marker : markers) {
                  if (display.contains(marker)) {
                     hasNavigation = true;
                     break;
                  }
               }
            }
            assertTrue(hasNavigation, name + " Help-" + number + " has no navigation line");
         }
      }
   }

   @Test
   @DisplayName("the missing-page notice is translated everywhere")
   void missingPageNoticeIsTranslated() throws IOException {
      for (String name : LANGUAGES) {
         Object notice = language(name).get("HelpPageMissing");
         assertNotNull(notice, name + ".yml is missing HelpPageMissing");
         assertTrue(String.valueOf(notice).contains("<Key>"), name + " HelpPageMissing must name the key via <Key>");
      }
   }
}
