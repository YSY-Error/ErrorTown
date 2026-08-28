package com.Util;

import com.ErrorTown.Main;
import java.lang.reflect.Method;
import org.bukkit.World;

/**
 * Reflection-only bridge for the optional RealisticSeasons plugin.
 *
 * <p>RealisticSeasons is not published to any public Maven repository, so it must
 * never appear as a compile-time type. Every call fails closed: when the plugin is
 * absent, or its API changed, the method returns {@code false} and the caller keeps
 * its vanilla behaviour.</p>
 */
public final class SeasonsCompat {
   private static final String API_CLASS = "me.casperge.realisticseasons.api.SeasonsAPI";
   private static volatile Boolean available;

   private SeasonsCompat() {
   }

   /** True only when RealisticSeasons is installed and its API class is loadable. */
   public static boolean isAvailable() {
      Boolean cached = available;
      if (cached != null) {
         return cached;
      }
      boolean resolved;
      try {
         Class.forName(API_CLASS);
         resolved = true;
      } catch (ReflectiveOperationException | LinkageError ignored) {
         resolved = false;
      }
      available = resolved;
      return resolved;
   }

   /**
    * Copies season and in-game date from {@code source} to {@code target}.
    *
    * @return true when both values were applied, false when the plugin or API is missing
    */
   public static boolean copySeasonAndDate(World source, World target) {
      if (source == null || target == null || !isAvailable()) {
         return false;
      }
      try {
         Class<?> apiClass = Class.forName(API_CLASS);
         Object api = apiClass.getMethod("getInstance").invoke(null);
         if (api == null) {
            return false;
         }
         return copy(apiClass, api, "getSeason", "setSeason", source, target)
            && copy(apiClass, api, "getDate", "setDate", source, target);
      } catch (ReflectiveOperationException | LinkageError | RuntimeException failure) {
         warn(failure);
         return false;
      }
   }

   private static boolean copy(Class<?> apiClass, Object api, String getter, String setter, World source, World target)
         throws ReflectiveOperationException {
      Method read = findMethod(apiClass, getter, 1);
      if (read == null) {
         return false;
      }
      Object value = read.invoke(api, source);
      if (value == null) {
         return false;
      }
      Method write = findMethod(apiClass, setter, 2);
      if (write == null) {
         return false;
      }
      write.invoke(api, target, value);
      return true;
   }

   private static Method findMethod(Class<?> type, String name, int parameterCount) {
      for (Method method : type.getMethods()) {
         if (method.getName().equals(name) && method.getParameterCount() == parameterCount) {
            return method;
         }
      }
      return null;
   }

   private static void warn(Throwable failure) {
      if (Main.JavaPlugin != null) {
         Main.JavaPlugin.getLogger().warning("RealisticSeasons bridge failed, falling back to vanilla time: " + failure);
      }
   }
}
