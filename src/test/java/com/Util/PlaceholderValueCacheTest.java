package com.Util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class PlaceholderValueCacheTest {

   @Test
   void keysAreCaseInsensitiveAndUpdatesDoNotDuplicateEntries() {
      AtomicLong clock = new AtomicLong(1000L);
      PlaceholderValueCache cache = new PlaceholderValueCache(8, clock::get);

      cache.put("Alice", "Public", "yes", 1000L);
      cache.put("alice", "public", "no", 1000L);

      assertEquals("no", cache.get("ALICE", "PUBLIC"));
      assertEquals(1, cache.size());
   }

   @Test
   void expiredValuesAreRemovedLazily() {
      AtomicLong clock = new AtomicLong(1000L);
      PlaceholderValueCache cache = new PlaceholderValueCache(8, clock::get);
      cache.put("Alice", "pvp", "enabled", 50L);

      clock.set(1050L);
      assertNull(cache.get("alice", "PVP"));
      assertEquals(0, cache.size());
   }

   @Test
   void capacityEvictsLeastRecentlyUsedEntry() {
      AtomicLong clock = new AtomicLong(1000L);
      PlaceholderValueCache cache = new PlaceholderValueCache(2, clock::get);
      cache.put("Alice", "one", "1", 1000L);
      cache.put("Alice", "two", "2", 1000L);
      assertEquals("1", cache.get("alice", "ONE"));

      cache.put("Alice", "three", "3", 1000L);

      assertEquals("1", cache.get("alice", "one"));
      assertNull(cache.get("alice", "two"));
      assertEquals("3", cache.get("alice", "three"));
   }

   @Test
   void invalidationCanTargetOneKeyOrAnEntirePlayer() {
      PlaceholderValueCache cache = new PlaceholderValueCache(8, System::currentTimeMillis);
      cache.put("Alice", "one", "1", 1000L);
      cache.put("Alice", "two", "2", 1000L);
      cache.put("Bob", "one", "3", 1000L);

      cache.invalidate("Alice", "ONE");
      assertNull(cache.get("alice", "one"));
      assertEquals("2", cache.get("alice", "two"));

      cache.invalidatePlayer("ALICE");
      assertNull(cache.get("alice", "two"));
      assertEquals("3", cache.get("bob", "one"));
   }

   @Test
   void nullBlankAndNonPositiveTtlInputsAreIgnoredSafely() {
      PlaceholderValueCache cache = new PlaceholderValueCache(8, System::currentTimeMillis);

      cache.put(null, "pvp", "x", 1000L);
      cache.put("Alice", "", "x", 1000L);
      cache.put("Alice", "pvp", "x", 0L);
      cache.put("Alice", "pvp", null, 1000L);

      assertNull(cache.get(null, "pvp"));
      assertNull(cache.get("Alice", "pvp"));
      assertEquals(0, cache.size());
   }

   @Test
   void ttlOverflowKeepsTheEntryUsable() {
      AtomicLong clock = new AtomicLong(Long.MAX_VALUE - 5L);
      PlaceholderValueCache cache = new PlaceholderValueCache(8, clock::get);

      cache.put("Alice", "pvp", "enabled", 100L);

      assertEquals("enabled", cache.get("alice", "pvp"));
   }
}
