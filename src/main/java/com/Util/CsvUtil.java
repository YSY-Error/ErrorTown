package com.Util;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Single implementation for the comma-separated name lists ErrorTown stores in
 * MySQL (`OP`, `Members`, `Deny`, ...).
 *
 * <p>These lists used to be edited with {@code String.replace(","+name, "")}, which
 * is substring replacement, not element removal. Removing {@code "Bo"} from
 * {@code "Alice,Bob"} produced {@code "Alice,b"}, and removing {@code "Bob"} from
 * {@code "Bob,Bobby"} produced {@code ",by"}. Every mutation therefore has to go
 * through this class, which splits into elements first.</p>
 *
 * <p>Comparison is case-insensitive because the rest of the plugin compares player
 * names with {@code equalsIgnoreCase}. Stored casing of the surviving entries is
 * preserved.</p>
 */
public final class CsvUtil {
   public static final String SEPARATOR = ",";

   private CsvUtil() {
   }

   /** Splits a stored list into trimmed, non-empty elements, preserving order. */
   public static List<String> split(String csv) {
      List<String> values = new ArrayList<>();
      if (csv == null || csv.isEmpty()) {
         return values;
      }
      for (String part : csv.split(SEPARATOR, -1)) {
         String trimmed = part.trim();
         if (!trimmed.isEmpty()) {
            values.add(trimmed);
         }
      }
      return values;
   }

   /** Joins elements back into the stored form, skipping blanks and duplicates. */
   public static String join(List<String> values) {
      if (values == null || values.isEmpty()) {
         return "";
      }
      Set<String> seen = new LinkedHashSet<>();
      StringBuilder builder = new StringBuilder();
      for (String value : values) {
         if (value == null) {
            continue;
         }
         String trimmed = value.trim();
         if (trimmed.isEmpty() || !seen.add(trimmed.toLowerCase(Locale.ROOT))) {
            continue;
         }
         if (builder.length() > 0) {
            builder.append(SEPARATOR);
         }
         builder.append(trimmed);
      }
      return builder.toString();
   }

   /** True when {@code name} is present as a whole element (not as a substring). */
   public static boolean contains(String csv, String name) {
      if (name == null || name.trim().isEmpty()) {
         return false;
      }
      String target = name.trim();
      for (String value : split(csv)) {
         if (value.equalsIgnoreCase(target)) {
            return true;
         }
      }
      return false;
   }

   /** Removes every element equal to {@code name}. Returns the rebuilt list. */
   public static String remove(String csv, String name) {
      if (name == null || name.trim().isEmpty()) {
         return join(split(csv));
      }
      String target = name.trim();
      List<String> kept = new ArrayList<>();
      for (String value : split(csv)) {
         if (!value.equalsIgnoreCase(target)) {
            kept.add(value);
         }
      }
      return join(kept);
   }

   /** Adds {@code name} unless already present. Returns the rebuilt list. */
   public static String add(String csv, String name) {
      List<String> values = split(csv);
      if (name != null && !name.trim().isEmpty() && !contains(csv, name)) {
         values.add(name.trim());
      }
      return join(values);
   }

   /** Removes every element equal to {@code name} from a mutable list, in place. */
   public static boolean removeFrom(List<String> values, String name) {
      if (values == null || name == null || name.trim().isEmpty()) {
         return false;
      }
      String target = name.trim();
      return values.removeIf(value -> value != null && value.trim().equalsIgnoreCase(target));
   }
}
