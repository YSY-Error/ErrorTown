package com.Util;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.LongSupplier;

/** Bounded, case-insensitive TTL cache for PlaceholderAPI values. */
public final class PlaceholderValueCache {
   private static final int DEFAULT_MAX_ENTRIES = 8192;

   private final int maxEntries;
   private final LongSupplier clock;
   private final Map<Key, Entry> entries = new LinkedHashMap<>(16, 0.75F, true);

   public PlaceholderValueCache() {
      this(DEFAULT_MAX_ENTRIES, System::currentTimeMillis);
   }

   public PlaceholderValueCache(int maxEntries, LongSupplier clock) {
      if (maxEntries < 1) {
         throw new IllegalArgumentException("maxEntries must be positive");
      }
      this.maxEntries = maxEntries;
      this.clock = Objects.requireNonNull(clock, "clock");
   }

   public synchronized String get(String playerName, String placeholder) {
      Key key = Key.of(playerName, placeholder);
      if (key == null) {
         return null;
      }
      Entry entry = entries.get(key);
      if (entry == null) {
         return null;
      }
      if (entry.expiresAtMillis <= clock.getAsLong()) {
         entries.remove(key);
         return null;
      }
      return entry.value;
   }

   public synchronized void put(String playerName, String placeholder, String value, long ttlMillis) {
      Key key = Key.of(playerName, placeholder);
      if (key == null) {
         return;
      }
      if (value == null || ttlMillis <= 0L) {
         entries.remove(key);
         return;
      }
      long now = clock.getAsLong();
      entries.put(key, new Entry(value, expiration(now, ttlMillis)));
      evictIfNeeded();
   }

   public synchronized void invalidate(String playerName, String placeholder) {
      Key key = Key.of(playerName, placeholder);
      if (key != null) {
         entries.remove(key);
      }
   }

   public synchronized void invalidatePlayer(String playerName) {
      String normalizedPlayer = normalize(playerName);
      if (normalizedPlayer == null) {
         return;
      }
      Iterator<Key> iterator = entries.keySet().iterator();
      while (iterator.hasNext()) {
         if (iterator.next().playerName.equals(normalizedPlayer)) {
            iterator.remove();
         }
      }
   }

   public synchronized int size() {
      return entries.size();
   }

   private long expiration(long now, long ttlMillis) {
      return Long.MAX_VALUE - now < ttlMillis ? Long.MAX_VALUE : now + ttlMillis;
   }

   private void evictIfNeeded() {
      if (entries.size() <= maxEntries) {
         return;
      }
      // Access-ordered map: the head is the least recently used entry.
      Iterator<Key> iterator = entries.keySet().iterator();
      while (entries.size() > maxEntries && iterator.hasNext()) {
         iterator.next();
         iterator.remove();
      }
   }

   private static String normalize(String value) {
      if (value == null) {
         return null;
      }
      String normalized = value.trim();
      return normalized.isEmpty() ? null : normalized.toLowerCase(Locale.ROOT);
   }

   private record Key(String playerName, String placeholder) {
      private static Key of(String playerName, String placeholder) {
         String normalizedPlayer = normalize(playerName);
         String normalizedPlaceholder = normalize(placeholder);
         return normalizedPlayer == null || normalizedPlaceholder == null
            ? null
            : new Key(normalizedPlayer, normalizedPlaceholder);
      }
   }

   private record Entry(String value, long expiresAtMillis) {
   }
}
