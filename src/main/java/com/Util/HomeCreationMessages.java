package com.Util;

import java.util.function.Function;

/** Resolves optional language keys while preserving defaults for old installs. */
public final class HomeCreationMessages {
   private final Function<String, String> lookup;

   public HomeCreationMessages(Function<String, String> lookup) {
      this.lookup = lookup;
   }

   public String text(String key, String fallback) {
      String configured = lookup == null ? null : lookup.apply(key);
      return configured == null || configured.trim().isEmpty() ? fallback : configured;
   }

   public String format(String key, String fallback, String placeholder, String value) {
      return text(key, fallback).replace(placeholder, value == null ? "" : value);
   }
}
