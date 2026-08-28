package com.Util;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;

/**
 * Owns the in-memory lifecycle of home creation requests.
 *
 * <p>This class deliberately has no Bukkit dependency. Scheduling, player
 * lookup and command dispatch remain in {@link HomeCreationCoordinator}; this
 * class only decides which request is waiting, admitted, running or failed.
 * Keeping that state machine separate makes queue behavior deterministic and
 * inexpensive to test.</p>
 *
 * <p><b>Stale entry recovery.</b> Concurrency is hard-capped at two slots. An entry
 * that reaches {@code ADMITTED}/{@code RUNNING} and never completes therefore burns
 * a slot permanently: two leaked entries deadlock home creation for the whole
 * server until restart. The coordinator's {@code runTaskLater} timeout does not
 * survive a reload, so the queue now stamps every state transition and exposes
 * {@link #reapStale(long)} for a periodic sweep that is independent of any
 * scheduled task.</p>
 */
public final class HomeCreationQueue {
   private static final int MAX_FAILURE_HISTORY = 256;

   /** Default ceiling for one creation attempt before the slot is reclaimed. */
   public static final long DEFAULT_STALE_MILLIS = 300_000L;

   private final Queue<String> waitingOrder = new ArrayDeque<>();
   private final Map<String, QueueEntry> entries = new HashMap<>();
   private final Set<String> failedHomes = new HashSet<>();
   private final Queue<String> failedOrder = new ArrayDeque<>();

   public synchronized int enqueue(CreationRequest request) {
      if (request == null || normalize(request.homeName).isEmpty()) {
         return -1;
      }

      String key = normalize(request.homeName);
      if (entries.containsKey(key)) {
         return position(request.homeName);
      }

      failedHomes.remove(key);
      failedOrder.remove(key);
      entries.put(key, new QueueEntry(request, State.WAITING, now()));
      waitingOrder.add(key);
      return position(request.homeName);
   }

   /** Moves as many FIFO requests as possible into an admitted slot. */
   public synchronized List<CreationRequest> admitAvailable(int configuredLimit) {
      int limit = HomeTerrainPolicy.normalizeCreationLimit(configuredLimit);
      List<CreationRequest> admitted = new ArrayList<>();
      while (activeCount() < limit && !waitingOrder.isEmpty()) {
         String key = waitingOrder.poll();
         QueueEntry entry = entries.get(key);
         if (entry == null || entry.state != State.WAITING) {
            continue;
         }
         entry.state = State.ADMITTED;
         entry.stateSince = now();
         admitted.add(entry.request);
      }
      return admitted;
   }

   /** Consumes the admission token only for the exact request that was admitted. */
   public synchronized boolean consumeAdmission(CreationRequest request) {
      if (request == null) {
         return false;
      }
      QueueEntry entry = entries.get(normalize(request.homeName));
      if (entry == null || entry.request != request || entry.state != State.ADMITTED) {
         return false;
      }
      entry.state = State.RUNNING;
      entry.stateSince = now();
      return true;
   }

   public synchronized void complete(String homeName) {
      removeEntry(homeName);
   }

   public synchronized void fail(String homeName) {
      String key = normalize(homeName);
      if (key.isEmpty()) {
         return;
      }
      removeEntry(homeName);
      rememberFailure(key);
   }

   /**
    * Reclaims slots held by entries that have been ADMITTED or RUNNING for longer
    * than {@code maxAgeMillis}. Waiting entries are never reaped: they hold no slot
    * and the player is still being told their queue position.
    *
    * @return the original home names of the reaped requests, for operator messaging
    */
   public synchronized List<CreationRequest> reapStale(long maxAgeMillis) {
      long cutoff = Math.max(1L, maxAgeMillis);
      long current = now();
      List<CreationRequest> reaped = new ArrayList<>();
      Iterator<Map.Entry<String, QueueEntry>> iterator = entries.entrySet().iterator();
      while (iterator.hasNext()) {
         Map.Entry<String, QueueEntry> mapEntry = iterator.next();
         QueueEntry entry = mapEntry.getValue();
         if (entry.state == State.WAITING || current - entry.stateSince < cutoff) {
            continue;
         }
         iterator.remove();
         waitingOrder.remove(mapEntry.getKey());
         rememberFailure(mapEntry.getKey());
         reaped.add(entry.request);
      }
      return reaped;
   }

   public synchronized boolean isPending(String homeName) {
      return entries.containsKey(normalize(homeName));
   }

   /** Completes only the exact request that is still active for the home. */
   public synchronized boolean complete(CreationRequest request) {
      return removeEntry(request);
   }

   /** Fails only the exact request that is still active for the home. */
   public synchronized boolean fail(CreationRequest request) {
      if (!removeEntry(request)) {
         return false;
      }
      rememberFailure(normalize(request.homeName));
      return true;
   }

   /** Returns whether this exact request is still the active request for its home. */
   public synchronized boolean isPending(CreationRequest request) {
      if (request == null) {
         return false;
      }
      QueueEntry entry = entries.get(normalize(request.homeName));
      return entry != null && entry.request == request;
   }

   public synchronized boolean hasFailed(String homeName) {
      return failedHomes.contains(normalize(homeName));
   }

   /** Number of entries currently holding one of the limited creation slots. */
   public synchronized int activeSlots() {
      return activeCount();
   }

   public synchronized int position(String homeName) {
      String targetKey = normalize(homeName);
      QueueEntry target = entries.get(targetKey);
      if (target == null) {
         return -1;
      }
      if (target.state != State.WAITING) {
         return 0;
      }

      int position = activeCount();
      for (String key : waitingOrder) {
         position++;
         if (key.equals(targetKey)) {
            return position;
         }
      }
      return -1;
   }

   private int activeCount() {
      int count = 0;
      for (QueueEntry entry : entries.values()) {
         if (entry.state == State.ADMITTED || entry.state == State.RUNNING) {
            count++;
         }
      }
      return count;
   }

   private void removeEntry(String homeName) {
      String key = normalize(homeName);
      entries.remove(key);
      waitingOrder.remove(key);
   }

   private boolean removeEntry(CreationRequest request) {
      if (request == null) {
         return false;
      }
      String key = normalize(request.homeName);
      QueueEntry entry = entries.get(key);
      if (entry == null || entry.request != request) {
         return false;
      }
      entries.remove(key);
      waitingOrder.remove(key);
      return true;
   }


   private void rememberFailure(String key) {
      failedHomes.add(key);
      failedOrder.remove(key);
      failedOrder.add(key);
      while (failedOrder.size() > MAX_FAILURE_HISTORY) {
         failedHomes.remove(failedOrder.poll());
      }
   }

   private static long now() {
      return System.nanoTime() / 1_000_000L;
   }

   private static String normalize(String homeName) {
      return homeName == null ? "" : homeName.trim().toLowerCase(Locale.ROOT);
   }

   private enum State {
      WAITING,
      ADMITTED,
      RUNNING
   }

   private static final class QueueEntry {
      private final CreationRequest request;
      private State state;
      private long stateSince;

      private QueueEntry(CreationRequest request, State state, long stateSince) {
         this.request = request;
         this.state = state;
         this.stateSince = stateSince;
      }
   }

   /** Immutable data passed from the queue to the Bukkit coordinator. */
   public static final class CreationRequest {
      private final UUID playerId;
      private final String playerName;
      private final String homeName;
      private final String command;

      public CreationRequest(UUID playerId, String homeName, String command) {
         this(playerId, null, homeName, command);
      }

      public CreationRequest(UUID playerId, String playerName, String homeName, String command) {
         if (playerId == null || homeName == null || homeName.trim().isEmpty() || command == null || command.trim().isEmpty()) {
            throw new IllegalArgumentException("Creation request requires a player, home name and command");
         }
         this.playerId = playerId;
         this.playerName = playerName == null ? null : playerName.trim();
         this.homeName = homeName;
         this.command = command;
      }

      public UUID getPlayerId() {
         return playerId;
      }

      public String getPlayerName() {
         return playerName;
      }

      public String getHomeName() {
         return homeName;
      }

      public String getCommand() {
         return command;
      }
   }
}
