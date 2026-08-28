package com.Util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Locks in the permission-node translation used by the SummerTown → ErrorTown rename.
 *
 * <p>Only the pure node arithmetic is exercised here: {@link Perm#has} needs a
 * {@code Permissible}, and every implementation Bukkit ships requires a running server.
 * The mapping is the part that must not regress — getting it wrong silently strips every
 * player's permissions on upgrade.</p>
 */
class PermTest {

   @Test
   @DisplayName("the current prefix matches plugin.yml and differs from the legacy one")
   void prefixesAreDistinct() {
      assertEquals("ErrorTown.", Perm.PREFIX);
      assertEquals("SummerTown.", Perm.LEGACY_PREFIX);
      assertFalse(Perm.PREFIX.equals(Perm.LEGACY_PREFIX));
   }

   @Test
   @DisplayName("a current node maps to its pre-rename equivalent")
   void currentNodeMapsToLegacy() {
      assertEquals("SummerTown.Create.1", Perm.legacyNodeFor("ErrorTown.Create.1"));
      assertEquals("SummerTown.Level.12", Perm.legacyNodeFor("ErrorTown.Level.12"));
      assertEquals("SummerTown.Gift.Open", Perm.legacyNodeFor("ErrorTown.Gift.Open"));
   }

   @Test
   @DisplayName("only the prefix is rewritten, the rest of the node is untouched")
   void onlyThePrefixChanges() {
      // A node that happens to contain the old name later on must not be mangled.
      assertEquals("SummerTown.Alias.SummerTown", Perm.legacyNodeFor("ErrorTown.Alias.SummerTown"));
      assertEquals("SummerTown.", Perm.legacyNodeFor("ErrorTown."));
   }

   @Test
   @DisplayName("prefix matching is case-insensitive, as Bukkit nodes are")
   void prefixMatchIsCaseInsensitive() {
      assertEquals("SummerTown.visit", Perm.legacyNodeFor("errortown.visit"));
      assertEquals("SummerTown.Visit", Perm.legacyNodeFor("ERRORTOWN.Visit"));
   }

   @Test
   @DisplayName("a foreign node has no legacy form")
   void foreignNodesHaveNoLegacyForm() {
      assertNull(Perm.legacyNodeFor("essentials.home"));
      assertNull(Perm.legacyNodeFor("SummerTown.Visit"), "an already-legacy node must not be translated again");
      assertNull(Perm.legacyNodeFor(null));
      assertNull(Perm.legacyNodeFor(""));
   }

   @Test
   @DisplayName("has() tolerates a null target or node instead of throwing")
   void hasIsNullSafe() {
      assertFalse(Perm.has(null, "ErrorTown.Visit"));
   }

   @Test
   @DisplayName("normalize trims and lowercases for stable comparison")
   void normalizeIsStable() {
      assertEquals("errortown.visit", Perm.normalize("  ErrorTown.Visit  "));
      assertEquals("", Perm.normalize(null));
      assertTrue(Perm.normalize("ErrorTown.A").equals(Perm.normalize("errortown.a")));
   }
}
