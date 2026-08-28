package com.Util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.Util.HomeCreationQueue.CreationRequest;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Locks in the queue state machine, including the stale-slot recovery that was missing. */
class HomeCreationQueueTest {

   private HomeCreationQueue queue;

   @BeforeEach
   void setUp() {
      queue = new HomeCreationQueue();
   }

   private static CreationRequest request(String home) {
      return new CreationRequest(UUID.randomUUID(), home, "sh create 1");
   }

   @Test
   @DisplayName("requests are admitted first in, first out")
   void admissionIsFifo() {
      CreationRequest alpha = request("alpha");
      CreationRequest beta = request("beta");
      queue.enqueue(alpha);
      queue.enqueue(beta);
      queue.enqueue(request("gamma"));

      List<CreationRequest> admitted = queue.admitAvailable(2);
      assertEquals(2, admitted.size());
      assertEquals("alpha", admitted.get(0).getHomeName());
      assertEquals("beta", admitted.get(1).getHomeName());
      assertEquals(2, queue.activeSlots());
   }

   @Test
   @DisplayName("the same home cannot occupy two queue slots")
   void duplicatesAreRejected() {
      queue.enqueue(request("alpha"));
      queue.enqueue(request("ALPHA"));
      queue.enqueue(request("  alpha  "));

      assertEquals(1, queue.admitAvailable(2).size());
      assertTrue(queue.isPending("alpha"));
   }

   @Test
   @DisplayName("the admission token is single use")
   void admissionTokenIsOneShot() {
      CreationRequest admitted = request("alpha");
      queue.enqueue(admitted);
      queue.admitAvailable(2);

      assertTrue(queue.consumeAdmission(admitted));
      assertFalse(queue.consumeAdmission(admitted));
   }

   @Test
   @DisplayName("an old request cannot consume the admission token of a same-name retry")
   void admissionRequiresExactRequestIdentity() {
      CreationRequest first = request("alpha");
      queue.enqueue(first);
      queue.admitAvailable(2);
      queue.complete(first);

      CreationRequest retry = request("alpha");
      queue.enqueue(retry);
      queue.admitAvailable(2);

      assertFalse(queue.consumeAdmission(first));
      assertTrue(queue.consumeAdmission(retry));
   }

   @Test
   @DisplayName("completing a request frees its slot for the next one")
   void completingReleasesSlot() {
      queue.enqueue(request("alpha"));
      queue.enqueue(request("beta"));
      queue.enqueue(request("gamma"));
      queue.admitAvailable(2);

      queue.complete("alpha");
      assertEquals(1, queue.activeSlots());
      List<CreationRequest> next = queue.admitAvailable(2);
      assertEquals(1, next.size());
      assertEquals("gamma", next.get(0).getHomeName());
   }

   @Test
   @DisplayName("failures are remembered until the home is queued again")
   void failureIsRemembered() {
      queue.enqueue(request("alpha"));
      queue.fail("alpha");

      assertTrue(queue.hasFailed("alpha"));
      assertFalse(queue.isPending("alpha"));

      queue.enqueue(request("alpha"));
      assertFalse(queue.hasFailed("alpha"));
   }

   @Test
   @DisplayName("a RUNNING request that never completes is reclaimed instead of deadlocking the queue")
   void staleRunningEntriesAreReaped() throws InterruptedException {
      CreationRequest alpha = request("alpha");
      CreationRequest beta = request("beta");
      queue.enqueue(alpha);
      queue.enqueue(beta);
      queue.admitAvailable(2);
      assertTrue(queue.consumeAdmission(alpha));
      assertTrue(queue.consumeAdmission(beta));
      assertEquals(2, queue.activeSlots());

      // Both slots are held. Without recovery no further home can ever be created.
      queue.enqueue(request("gamma"));
      assertTrue(queue.admitAvailable(2).isEmpty());

      Thread.sleep(30L);
      List<CreationRequest> reaped = queue.reapStale(10L);

      assertEquals(2, reaped.size());
      assertEquals(0, queue.activeSlots());
      assertTrue(queue.hasFailed("alpha"));
      assertTrue(queue.hasFailed("beta"));

      List<CreationRequest> recovered = queue.admitAvailable(2);
      assertEquals(1, recovered.size());
      assertEquals("gamma", recovered.get(0).getHomeName());
   }

   @Test
   @DisplayName("waiting requests are never reaped: they hold no slot")
   void waitingEntriesAreNotReaped() throws InterruptedException {
      queue.enqueue(request("alpha"));
      Thread.sleep(30L);

      assertTrue(queue.reapStale(10L).isEmpty());
      assertTrue(queue.isPending("alpha"));
      assertFalse(queue.hasFailed("alpha"));
   }

   @Test
   @DisplayName("queue position counts the occupied slots ahead of the waiting request")
   void positionIncludesActiveSlots() {
      queue.enqueue(request("alpha"));
      queue.enqueue(request("beta"));
      queue.enqueue(request("gamma"));
      queue.admitAvailable(2);

      // alpha and beta now hold slots and report 0, meaning "already started".
      assertEquals(0, queue.position("alpha"));
      assertEquals(0, queue.position("beta"));
      assertEquals(3, queue.position("gamma"));
      assertEquals(-1, queue.position("unknown"));
   }

   @Test
   @DisplayName("a request without a player, home or command is rejected outright")
   void invalidRequestsAreRejected() {
      assertThrows(IllegalArgumentException.class, () -> new CreationRequest(null, "alpha", "sh create 1"));
      assertThrows(IllegalArgumentException.class, () -> new CreationRequest(UUID.randomUUID(), " ", "sh create 1"));
      assertThrows(IllegalArgumentException.class, () -> new CreationRequest(UUID.randomUUID(), "alpha", " "));
      assertEquals(-1, queue.enqueue(null));
   }

   @Test
   @DisplayName("an expired callback cannot cancel a newer request for the same home")
   void requestIdentityPreventsOldTimeoutFromCancellingRetry() {
      CreationRequest first = request("alpha");
      queue.enqueue(first);
      queue.admitAvailable(2);
      queue.consumeAdmission(first);
      queue.complete("alpha");

      CreationRequest retry = request("alpha");
      queue.enqueue(retry);

      assertFalse(queue.isPending(first));
      assertTrue(queue.isPending(retry));
      assertEquals("alpha", retry.getHomeName());
   }

   @Test
   @DisplayName("an expired async completion cannot remove a newer request for the same home")
   void requestIdentityPreventsOldCompletionFromRemovingRetry() {
      CreationRequest first = request("alpha");
      queue.enqueue(first);
      queue.admitAvailable(2);
      queue.consumeAdmission(first);
      queue.complete("alpha");

      CreationRequest retry = request("alpha");
      queue.enqueue(retry);

      assertFalse(queue.complete(first));
      assertTrue(queue.isPending(retry));
      assertTrue(queue.complete(retry));
      assertFalse(queue.isPending(retry));
   }
}
