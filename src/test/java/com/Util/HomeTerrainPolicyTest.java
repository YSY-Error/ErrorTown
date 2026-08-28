package com.Util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Locks in the bounded border sizing rules and the fixes made to them. */
class HomeTerrainPolicyTest {

   private static final List<Integer> DEFAULT_TABLE =
      Arrays.asList(8, 16, 24, 32, 40, 48, 56, 64, 72, 80, 88, 96);

   @Test
   @DisplayName("the default table starts at 8x8, ends at 96x96 and is strictly increasing")
   void defaultTableIsMonotonic() {
      int previous = 0;
      for (int level = 1; level <= 12; level++) {
         int size = HomeTerrainPolicy.sizeForLevel(level, DEFAULT_TABLE);
         assertTrue(size > previous, "level " + level + " must be larger than level " + (level - 1));
         previous = size;
      }
      assertEquals(HomeTerrainPolicy.MINIMUM_SIZE, HomeTerrainPolicy.sizeForLevel(1, DEFAULT_TABLE));
      assertEquals(HomeTerrainPolicy.MAXIMUM_SIZE, HomeTerrainPolicy.sizeForLevel(12, DEFAULT_TABLE));
   }

   @Test
   @DisplayName("levels beyond the table stay at the cap instead of overflowing")
   void beyondTableIsClamped() {
      assertEquals(96, HomeTerrainPolicy.sizeForLevel(13, DEFAULT_TABLE));
      assertEquals(96, HomeTerrainPolicy.sizeForLevel(9999, DEFAULT_TABLE));
      assertEquals(8, HomeTerrainPolicy.sizeForLevel(0, DEFAULT_TABLE));
      assertEquals(8, HomeTerrainPolicy.sizeForLevel(-5, DEFAULT_TABLE));
   }

   @Test
   @DisplayName("an unsorted configured table can never make an upgrade shrink the home")
   void unsortedTableIsSorted() {
      int[] unsorted = {96, 8, 40};
      int first = HomeTerrainPolicy.sizeForLevel(1, unsorted);
      int second = HomeTerrainPolicy.sizeForLevel(2, unsorted);
      int third = HomeTerrainPolicy.sizeForLevel(3, unsorted);
      assertTrue(first < second && second < third, "expected ascending, got " + first + "," + second + "," + third);
      assertEquals(8, first);
      assertEquals(96, third);
   }

   @Test
   @DisplayName("duplicate configured sizes do not create no-op upgrade levels")
   void duplicateSizesAreRemoved() {
      int[] duplicated = {8, 16, 16, 32};

      assertEquals(8, HomeTerrainPolicy.sizeForLevel(1, duplicated));
      assertEquals(16, HomeTerrainPolicy.sizeForLevel(2, duplicated));
      assertEquals(32, HomeTerrainPolicy.sizeForLevel(3, duplicated));
      assertEquals(3, HomeTerrainPolicy.levelCount(duplicated));
   }

   @Test
   @DisplayName("out-of-range configured sizes are dropped, not clamped into a duplicate")
   void invalidSizesAreDropped() {
      assertEquals(2, HomeTerrainPolicy.levelCount(new int[]{8, 200, 24}));
      assertEquals(12, HomeTerrainPolicy.levelCount(null));
      assertEquals(12, HomeTerrainPolicy.levelCount(new int[]{0, -1, 500}));
   }

   @Test
   @DisplayName("maxLevelForSize honours a configured table instead of always using the default")
   void maxLevelForSizeUsesConfiguredTable() {
      assertEquals(12, HomeTerrainPolicy.maxLevelForSize(96, DEFAULT_TABLE));
      assertEquals(1, HomeTerrainPolicy.maxLevelForSize(8, DEFAULT_TABLE));
      assertEquals(5, HomeTerrainPolicy.maxLevelForSize(40, DEFAULT_TABLE));
      // A three-entry table can never report twelve levels.
      assertEquals(3, HomeTerrainPolicy.maxLevelForSize(96, Arrays.asList(8, 40, 96)));
      // Below the minimum there is no usable level, but the API still returns 1.
      assertEquals(1, HomeTerrainPolicy.maxLevelForSize(0, DEFAULT_TABLE));
   }

   @Test
   @DisplayName("every size is bounded by the global 8..96 block range")
   void sizesAreAlwaysBounded() {
      assertEquals(HomeTerrainPolicy.MINIMUM_SIZE, HomeTerrainPolicy.clampSize(-100));
      assertEquals(HomeTerrainPolicy.MAXIMUM_SIZE, HomeTerrainPolicy.clampSize(Integer.MAX_VALUE));
      assertEquals(
         HomeTerrainPolicy.MAXIMUM_SIZE,
         HomeTerrainPolicy.configuredBorderSize(12, true, DEFAULT_TABLE, 8, 8, 1000)
      );
   }

   @Test
   @DisplayName("concurrent creation is hard-capped at two slots")
   void creationLimitIsCapped() {
      assertEquals(1, HomeTerrainPolicy.normalizeCreationLimit(0));
      assertEquals(1, HomeTerrainPolicy.normalizeCreationLimit(-4));
      assertEquals(2, HomeTerrainPolicy.normalizeCreationLimit(2));
      assertEquals(2, HomeTerrainPolicy.normalizeCreationLimit(99));
   }

   @Test
   @DisplayName("chunk estimation covers a square that is not chunk aligned")
   void chunkCountCoversUnalignedSquares() {
      assertEquals(1, HomeTerrainPolicy.chunkCountForSquare(8));
      assertEquals(1, HomeTerrainPolicy.chunkCountForSquare(16));
      assertEquals(4, HomeTerrainPolicy.chunkCountForSquare(17));
      assertEquals(36, HomeTerrainPolicy.chunkCountForSquare(96));
   }

   @Test
   @DisplayName("storage estimation returns zero for non-positive inputs")
   void storageEstimation() {
      assertEquals(0L, HomeTerrainPolicy.estimatedBytesForHomes(0, 36, 1024L));
      assertEquals(0L, HomeTerrainPolicy.estimatedBytesForHomes(10, 0, 1024L));
      assertEquals(0L, HomeTerrainPolicy.estimatedBytesForHomes(10, 36, 0L));
      assertEquals(368640L, HomeTerrainPolicy.estimatedBytesForHomes(10, 36, 1024L));
   }
}
