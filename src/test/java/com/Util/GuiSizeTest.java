package com.Util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.ErrorTown.Variable;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Behaviour of the configurable menu size.
 *
 * <p>Menu sizes were hard-coded at 27, 45 or 54 slots. Now they come from {@code GUI.yml}, and a bad
 * value must never reach {@code Bukkit.createInventory}: a size that is not a multiple of nine, or is
 * over 54, throws and takes the whole menu down. These cases pin the clamping.</p>
 */
class GuiSizeTest {
   private static final int FALLBACK = 45;

   @AfterEach
   void clearConfig() {
      Variable.GUI_YML = null;
   }

   private static void gui(String yaml) {
      Variable.GUI_YML = (FileConfiguration)YamlConfiguration.loadConfiguration(new java.io.StringReader(yaml));
   }

   @Test
   @DisplayName("an absent key keeps the historical size")
   void absentKeyFallsBack() {
      gui("Unrelated: 1");
      assertEquals(FALLBACK, GuiSafe.size("MainSize", FALLBACK));
   }

   @Test
   @DisplayName("a null config keeps the historical size")
   void nullConfigFallsBack() {
      Variable.GUI_YML = null;
      assertEquals(FALLBACK, GuiSafe.size("MainSize", FALLBACK));
   }

   @Test
   @DisplayName("1-6 is read as a row count")
   void rowCountsBecomeSlots() {
      gui("A: 1\nB: 3\nC: 6");
      assertEquals(9, GuiSafe.size("A", FALLBACK));
      assertEquals(27, GuiSafe.size("B", FALLBACK));
      assertEquals(54, GuiSafe.size("C", FALLBACK));
   }

   @Test
   @DisplayName("a valid slot count is taken as-is")
   void slotCountsArePassedThrough() {
      gui("A: 9\nB: 27\nC: 54");
      assertEquals(9, GuiSafe.size("A", FALLBACK));
      assertEquals(27, GuiSafe.size("B", FALLBACK));
      assertEquals(54, GuiSafe.size("C", FALLBACK));
   }

   @Test
   @DisplayName("a size Bukkit would reject falls back instead of throwing later")
   void invalidSizesFallBack() {
      // 7 and 8 are neither a row count nor a multiple of nine; 20 is not a multiple of nine;
      // 63 exceeds a chest; 0 and negatives are nonsense. Every one of these would throw inside
      // Bukkit.createInventory.
      gui("A: 7\nB: 8\nC: 20\nD: 63\nE: 0\nF: -9\nG: 108");
      for (String key : new String[] {"A", "B", "C", "D", "E", "F", "G"}) {
         assertEquals(FALLBACK, GuiSafe.size(key, FALLBACK), "size '" + key + "' must fall back");
      }
   }

   @Test
   @DisplayName("a non-numeric size falls back")
   void nonNumericFallsBack() {
      gui("A: 'five rows'");
      assertEquals(FALLBACK, GuiSafe.size("A", FALLBACK));
   }

   @Test
   @DisplayName("every size the shipped GUI.yml declares is accepted")
   void shippedSizesAreValid() throws java.io.IOException {
      java.nio.file.Path shipped = java.nio.file.Path.of("src", "main", "resources", "GUI.yml");
      Variable.GUI_YML = (FileConfiguration)YamlConfiguration.loadConfiguration(shipped.toFile());
      String[] keys = {
         "MainSize", "CheckSize", "CreateSize", "ManageSize", "Manage2Size", "Manage3Size",
         "VisitSize", "InviteSize", "TrustSize", "DenySize", "BiomeSize", "RulesSize",
         "UpgradeSize", "SetSpawnSize", "OwnedHomesSize", "CreateCostSize", "ServiceCostSize", "GiftSize"
      };
      for (String key : keys) {
         int size = GuiSafe.size(key, -1);
         assertEquals(0, size % 9, key + " must resolve to a multiple of nine but was " + size);
         org.junit.jupiter.api.Assertions.assertTrue(size >= 9 && size <= 54, key + " resolved to " + size);
      }
   }
}
