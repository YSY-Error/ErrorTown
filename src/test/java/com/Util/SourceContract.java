package com.Util;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Helper for the handful of contracts that can only be checked against source text.
 *
 * <p>Some invariants live in code that needs a running Paper server to execute — the
 * creation command flow, {@code onDisable} ordering, the request-token plumbing. Until
 * those have integration coverage, asserting on the source is the only automated guard
 * available.</p>
 *
 * <p>The point of this class is to make such a test <b>fail informatively</b>. Written by
 * hand these tests did {@code source.substring(indexOf(a), indexOf(b))}, and an
 * unrelated rename made {@code indexOf} return -1, so the test died with an opaque
 * {@link StringIndexOutOfBoundsException} instead of saying which marker disappeared.</p>
 */
final class SourceContract {

   private SourceContract() {
   }

   /** Reads a file under {@code src/main/java}. Paths are relative to the project root. */
   static String read(String... pathParts) {
      Path path = Path.of("src", "main", "java");
      for (String part : pathParts) {
         path = path.resolve(part);
      }
      if (!Files.isRegularFile(path)) {
         fail("Source file not found: " + path.toAbsolutePath()
            + ". Source-contract tests must run with the project root as the working directory.");
      }
      try {
         return Files.readString(path, StandardCharsets.UTF_8);
      } catch (IOException failure) {
         throw new UncheckedIOException(failure);
      }
   }

   /**
    * Returns the text between {@code startMarker} and the first {@code endMarker} after it.
    *
    * <p>Fails with the missing marker named, rather than throwing on a negative index.</p>
    */
   static String region(String source, String what, String startMarker, String endMarker) {
      int start = source.indexOf(startMarker);
      if (start < 0) {
         fail("Cannot check " + what + ": start marker \"" + startMarker
            + "\" no longer exists. Update this test together with the code it guards.");
      }
      int end = source.indexOf(endMarker, start + startMarker.length());
      if (end < 0) {
         fail("Cannot check " + what + ": end marker \"" + endMarker
            + "\" no longer exists after the start marker. Update this test together with the code it guards.");
      }
      return source.substring(start, end);
   }

   /** Returns up to {@code width} characters preceding {@code marker}. */
   static String before(String source, String what, String marker, int width) {
      int at = source.indexOf(marker);
      if (at < 0) {
         fail("Cannot check " + what + ": \"" + marker + "\" no longer exists in the source.");
      }
      return source.substring(Math.max(0, at - width), at);
   }

   static void requireContains(String haystack, String needle, String message) {
      assertTrue(haystack.contains(needle), message + " (looked for \"" + needle + "\")");
   }
}
