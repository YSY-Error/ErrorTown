package com.Util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Regression tests for the comma-separated name lists.
 *
 * <p>These lock in the fix for the substring-removal defect: the old code used
 * {@code String.replace} on the joined list, so removing one name mangled the
 * neighbouring ones.</p>
 */
class CsvUtilTest {

   @Test
   @DisplayName("removing a name that is a prefix of another must not corrupt it")
   void removeDoesNotCorruptPrefixMatches() {
      // Old behaviour: "Alice,Bob".replace("Bo", "") -> "Alice,b"
      assertEquals("Alice,Bob", CsvUtil.remove("Alice,Bob", "Bo"));
      // Old behaviour: "Bob,Bobby".replace("Bob", "") -> ",by"
      assertEquals("Bobby", CsvUtil.remove("Bob,Bobby", "Bob"));
      assertEquals("Bob", CsvUtil.remove("Bob,Bobby", "Bobby"));
   }

   @Test
   @DisplayName("removal is case-insensitive and drops every occurrence")
   void removeIsCaseInsensitive() {
      assertEquals("Carol", CsvUtil.remove("bob,Carol,BOB", "Bob"));
      assertEquals("", CsvUtil.remove("Bob", "bob"));
   }

   @Test
   @DisplayName("removing an absent name leaves the list intact")
   void removeAbsentIsNoOp() {
      assertEquals("Alice,Bob", CsvUtil.remove("Alice,Bob", "Carol"));
      assertEquals("Alice,Bob", CsvUtil.remove("Alice,Bob", ""));
      assertEquals("Alice,Bob", CsvUtil.remove("Alice,Bob", null));
   }

   @Test
   @DisplayName("contains matches whole elements only")
   void containsMatchesWholeElements() {
      assertTrue(CsvUtil.contains("Alice,Bob", "bob"));
      assertFalse(CsvUtil.contains("Alice,Bob", "Bo"));
      assertFalse(CsvUtil.contains("Alice,Bob", "ob"));
      assertFalse(CsvUtil.contains(null, "Bob"));
      assertFalse(CsvUtil.contains("Alice", null));
   }

   @Test
   @DisplayName("blank and duplicate entries are normalised away")
   void joinNormalises() {
      assertEquals("Alice,Bob", CsvUtil.join(Arrays.asList("Alice", "", "  ", "Bob", "alice")));
      assertEquals("", CsvUtil.join(new ArrayList<>()));
      assertEquals("", CsvUtil.join(null));
   }

   @Test
   @DisplayName("split trims and skips empty segments")
   void splitTrims() {
      assertEquals(List.of("Alice", "Bob"), CsvUtil.split(" Alice , Bob "));
      assertEquals(List.of("Alice"), CsvUtil.split(",,Alice,,"));
      assertTrue(CsvUtil.split(null).isEmpty());
      assertTrue(CsvUtil.split("").isEmpty());
   }

   @Test
   @DisplayName("add is idempotent")
   void addIsIdempotent() {
      assertEquals("Alice,Bob", CsvUtil.add("Alice", "Bob"));
      assertEquals("Alice,Bob", CsvUtil.add("Alice,Bob", "bob"));
      assertEquals("Bob", CsvUtil.add("", "Bob"));
   }

   @Test
   @DisplayName("removeFrom edits a list in place without touching similar names")
   void removeFromList() {
      List<String> values = new ArrayList<>(Arrays.asList("Alice", "Bob", "Bobby"));
      assertTrue(CsvUtil.removeFrom(values, "bob"));
      assertEquals(List.of("Alice", "Bobby"), values);
      assertFalse(CsvUtil.removeFrom(values, "Carol"));
   }
}
