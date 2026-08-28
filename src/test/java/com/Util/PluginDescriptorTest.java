package com.Util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Verifies that directly linked runtime APIs are declared as hard dependencies. */
class PluginDescriptorTest {

   @Test
   @DisplayName("NBT-API is a hard dependency because Util links its classes directly")
   void nbtApiMustBeHardDependency() throws IOException {
      Path descriptor = Path.of("src", "main", "resources", "plugin.yml");
      String yaml = Files.readString(descriptor, StandardCharsets.UTF_8);

      assertTrue(yaml.contains("depend: [Vault, NBTAPI]"),
         "plugin.yml must list NBTAPI under depend");
      assertFalse(yaml.matches("(?s).*softdepend:\\s*[^\\r\\n]*\\bNBTAPI\\b[^\\r\\n]*"),
         "NBTAPI must not remain optional while Util imports NBT-API classes");
   }
}
