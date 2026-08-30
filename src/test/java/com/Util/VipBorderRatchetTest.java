package com.Util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for the VIP border high-water mark.
 *
 * <p>The guarantee being locked in is that a bonus never decreases while a home stays in the
 * cache. The VIP bonus is derived from who is online, so it collapses to {@code 0} as soon as the
 * VIP disconnects; without the ratchet the border would shrink under players standing inside it.</p>
 *
 * <p>These also pin the defect this class replaced: the copy of the logic in {@code Util} omitted
 * the branch that writes an increased value back, so its map kept the first bonus it ever saw. The
 * per-call result stayed correct, but after the VIP logged off the hologram corner markers were
 * drawn at base size while the authoritative {@code WorldBorder} stayed expanded.</p>
 */
class VipBorderRatchetTest {

   @Test
   @DisplayName("a larger bonus is remembered and returned")
   void risesToTheLargerBonus() {
      Map<String, Integer> cache = new HashMap<>();
      assertEquals(0, VipBorderRatchet.highWaterMark(cache, "Alice", 0));
      assertEquals(32, VipBorderRatchet.highWaterMark(cache, "Alice", 32));
      assertEquals(32, cache.get("Alice").intValue());
   }

   @Test
   @DisplayName("the bonus never drops when the VIP goes offline")
   void neverDropsBelowTheHighWaterMark() {
      Map<String, Integer> cache = new HashMap<>();
      VipBorderRatchet.highWaterMark(cache, "Alice", 32);
      // The VIP disconnects, so the freshly computed bonus is 0.
      assertEquals(32, VipBorderRatchet.highWaterMark(cache, "Alice", 0));
      // ...and the mark survives for the next caller. This is the assertion the old Util copy
      // failed: it left the map at whatever was seen first instead of at the maximum.
      assertEquals(32, cache.get("Alice").intValue());
      assertEquals(32, VipBorderRatchet.highWaterMark(cache, "Alice", 8));
   }

   @Test
   @DisplayName("a rise after a fall is still recorded")
   void risesAgainAfterAFall() {
      Map<String, Integer> cache = new HashMap<>();
      VipBorderRatchet.highWaterMark(cache, "Alice", 8);
      VipBorderRatchet.highWaterMark(cache, "Alice", 0);
      assertEquals(48, VipBorderRatchet.highWaterMark(cache, "Alice", 48));
      assertEquals(48, cache.get("Alice").intValue());
   }

   @Test
   @DisplayName("homes do not read each other's bonus")
   void marksAreKeyedPerHome() {
      Map<String, Integer> cache = new HashMap<>();
      VipBorderRatchet.highWaterMark(cache, "Alice", 32);
      assertEquals(0, VipBorderRatchet.highWaterMark(cache, "Bob", 0));
      assertEquals(32, cache.get("Alice").intValue());
      assertEquals(0, cache.get("Bob").intValue());
   }

   @Test
   @DisplayName("home names are matched exactly, as the storage layout does")
   void keysAreCaseSensitive() {
      // Deliberate: playerdata files and the MySQL Name column are keyed by the exact name, so a
      // differently-cased name is a different home here too rather than being folded together.
      Map<String, Integer> cache = new HashMap<>();
      VipBorderRatchet.highWaterMark(cache, "Alice", 32);
      assertEquals(0, VipBorderRatchet.highWaterMark(cache, "alice", 0));
   }

   @Test
   @DisplayName("a negative configured bonus cannot shrink the border")
   void negativeBonusIsClampedToZero() {
      // VIPAdd sizes are radii added to a diameter; a negative one would take the border below the
      // size the home's level grants.
      Map<String, Integer> cache = new HashMap<>();
      assertEquals(0, VipBorderRatchet.highWaterMark(cache, "Alice", -16));
      assertEquals(0, cache.get("Alice").intValue());
      VipBorderRatchet.highWaterMark(cache, "Alice", 24);
      assertEquals(24, VipBorderRatchet.highWaterMark(cache, "Alice", -16));
   }

   @Test
   @DisplayName("a missing cache or home name degrades to a plain maximum")
   void toleratesNullInputs() {
      assertEquals(16, VipBorderRatchet.highWaterMark(null, "Alice", 16));
      assertEquals(0, VipBorderRatchet.highWaterMark(null, "Alice", -3));
      assertEquals(16, VipBorderRatchet.highWaterMark(new HashMap<>(), null, 16));
   }

   @Test
   @DisplayName("works on the bounded LRU map the call sites actually pass")
   void worksWithTheBoundedCache() {
      // Util.boundedCache is a synchronizedMap wrapping an access-ordered LinkedHashMap; merge()
      // has to behave the same there as on a plain HashMap.
      Map<String, Integer> cache = Util.boundedCache(2048);
      assertEquals(32, VipBorderRatchet.highWaterMark(cache, "Alice", 32));
      assertEquals(32, VipBorderRatchet.highWaterMark(cache, "Alice", 0));
      assertEquals(64, VipBorderRatchet.highWaterMark(cache, "Alice", 64));
      assertEquals(64, cache.get("Alice").intValue());
   }
}
