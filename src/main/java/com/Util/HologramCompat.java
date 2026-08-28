package com.Util;

import java.lang.reflect.Method;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.plugin.Plugin;

/** Reflection-only bridge for optional legacy HolographicDisplays and DecentHolograms APIs. */
public final class HologramCompat {
   private HologramCompat() {
   }

   public static Handle create(Plugin plugin, Location location) {
      // Deliberately empty catches: these are API probes. The first branch tests for
      // legacy HolographicDisplays, the second for DecentHolograms; on any given server
      // at most one is present, so a ReflectiveOperationException here is the expected
      // "not this one" answer, not a failure. Genuine absence of both is reported by
      // Main via Variable.Hologram_switch.
      try {
         Class<?> api = Class.forName("com.gmail.filoghost.holographicdisplays.api.HologramsAPI");
         Method method = api.getMethod("createHologram", Plugin.class, Location.class);
         return new Handle(method.invoke(null, plugin, location));
      } catch (ReflectiveOperationException notLegacyApi) {
         // fall through to the modern API
      }

      try {
         Class<?> api = Class.forName("eu.decentsoftware.holograms.api.DHAPI");
         Method method = api.getMethod("createHologram", String.class, Location.class);
         String id = "summontown-border-" + UUID.randomUUID();
         return new Handle(method.invoke(null, id, location));
      } catch (ReflectiveOperationException ignored) {
         return Handle.NOOP;
      }
   }

   public static Handle wrapLegacy(Object delegate) {
      return new Handle(delegate);
   }

   public static final class Handle {
      private static final Handle NOOP = new Handle(null);
      private final Object delegate;

      private Handle(Object delegate) {
         this.delegate = delegate;
      }

      public void insertTextLine(int index, String text) {
         if (delegate != null && delegate.getClass().getName().startsWith("eu.decentsoftware.")) {
            try {
               Class<?> api = Class.forName("eu.decentsoftware.holograms.api.DHAPI");
               Method method = api.getMethod("addHologramLine", delegate.getClass(), String.class);
               method.invoke(null, delegate, text);
               return;
         } catch (ReflectiveOperationException notDecentApi) {
            // Fall through to the legacy insertTextLine path below.
         }
         }
         invoke("insertTextLine", new Class<?>[]{int.class, String.class}, index, text);
      }

      public void teleport(Location location) {
         if (!invoke("teleport", new Class<?>[]{Location.class}, location)) {
            invoke("setLocation", new Class<?>[]{Location.class}, location);
         }
      }

      public void delete() {
         if (!invoke("delete", new Class<?>[0])) {
            invoke("destroy", new Class<?>[0]);
         }
      }

      private boolean invoke(String name, Class<?>[] parameterTypes, Object... args) {
         if (delegate == null) {
            return false;
         }
         try {
            Method method = delegate.getClass().getMethod(name, parameterTypes);
            method.invoke(delegate, args);
            return true;
         } catch (ReflectiveOperationException ignored) {
            return false;
         }
      }
   }
}
