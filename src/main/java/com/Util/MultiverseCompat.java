package com.Util;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import org.bukkit.Location;
import org.bukkit.World.Environment;
import org.bukkit.WorldType;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.plugin.Plugin;

/**
 * Runtime bridge for Multiverse-Core 2.x and Multiverse-Core 5.x.
 *
 * <p>Multiverse is optional. Every operation therefore fails closed and
 * returns a neutral value when the plugin is absent or its API changes.</p>
 */
public final class MultiverseCompat {
   private static final String PLUGIN_NAME = "Multiverse-Core";

   private MultiverseCompat() {
   }

   public static boolean isAvailable() {
      return plugin() != null;
   }

   public static MultiverseCore plugin() {
      Server server = Bukkit.getServer();
      if (server == null) {
         return null;
      }
      Plugin plugin = server.getPluginManager().getPlugin(PLUGIN_NAME);
      return plugin != null && plugin.isEnabled() ? new MultiverseCore(plugin) : null;
   }

   public static MVWorldManager worldManager() {
      MultiverseCore plugin = plugin();
      if (plugin == null) {
         return null;
      }
      Object manager = invoke(plugin.delegate, "getMVWorldManager");
      if (manager == null) {
         Object api = invoke(plugin.delegate, "getApi");
         manager = api == null ? null : invoke(api, "getWorldManager");
      }
      return manager == null ? null : new MVWorldManager(manager);
   }

   public static MultiverseWorld getWorld(String worldName) {
      MVWorldManager manager = worldManager();
      if (manager == null || worldName == null) {
         return null;
      }
      return manager.getMVWorld(worldName);
   }

   public static boolean isWorld(String worldName) {
      MVWorldManager manager = worldManager();
      if (manager == null || worldName == null) {
         return false;
      }
      return manager.isMVWorld(worldName);
   }

   public static boolean setSpawnLocation(String worldName, Location location) {
      MultiverseWorld world = getWorld(worldName);
      return world != null && world.setSpawnLocation(location);
   }

   public static boolean removeWorldFromConfig(String worldName) {
      MVWorldManager manager = worldManager();
      if (manager == null || worldName == null) {
         return false;
      }
      return manager.removeWorldFromConfig(worldName);
   }

   public static boolean setAlias(String worldName, String alias) {
      MultiverseWorld world = getWorld(worldName);
      return world != null && world.setAlias(alias);
   }

   public static boolean setAutoLoad(String worldName, boolean autoLoad) {
      MultiverseWorld world = getWorld(worldName);
      return world != null && world.setAutoLoad(autoLoad);
   }

   public static boolean setPvp(String worldName, boolean pvp) {
      MultiverseWorld world = getWorld(worldName);
      return world != null && world.setPvp(pvp);
   }

   public static boolean setAllowMonsterSpawn(String worldName, boolean allow) {
      MultiverseWorld world = getWorld(worldName);
      return world != null && world.setAllowMonsterSpawn(allow);
   }

   public static boolean setAllowAnimalSpawn(String worldName, boolean allow) {
      MultiverseWorld world = getWorld(worldName);
      return world != null && world.setAllowAnimalSpawn(allow);
   }

   /** Adds a world through the legacy API or the modern CreateWorldOptions API. */
   public static boolean addWorld(String name, Environment environment, String seed, WorldType type,
         boolean structures, String generator) {
      MVWorldManager manager = worldManager();
      if (manager == null || name == null) {
         return false;
      }
      if (manager.addWorld(name, environment, seed, type, structures, generator)) return true;

      try {
         Class<?> optionsClass = Class.forName(
               "org.mvplugins.multiverse.core.world.options.CreateWorldOptions");
         Method factory = optionsClass.getMethod("worldName", String.class);
         Object options = factory.invoke(null, name);
         options = invokeFluent(options, "environment", environment);
         options = invokeFluent(options, "worldType", type);
         options = invokeFluent(options, "generateStructures", structures);
         options = invokeFluent(options, "generator", generator == null ? "" : generator);
         if (seed != null && !seed.isEmpty()) {
            options = invokeFluent(options, "seed", seed);
         }
         Object result = invoke(manager.delegate, "createWorld", options);
         return result != null && successfulResult(result);
      } catch (ReflectiveOperationException ignored) {
         return false;
      }
   }

   private static Object invokeFluent(Object target, String name, Object... args) {
      Object result = invoke(target, name, args);
      return result != null && target.getClass().isInstance(result) ? result : target;
   }

   private static boolean invokeBoolean(Object target, String name, Object... args) {
      if (target == null) {
         return false;
      }
      Object result = invoke(target, name, args);
      return result != null && successfulResult(result);
   }

   private static Object invoke(Object target, String name, Object... args) {
      if (target == null) {
         return null;
      }
      Method method = findMethod(target.getClass(), name, args);
      if (method == null) {
         return null;
      }
      try {
         if (!method.canAccess(Modifier.isStatic(method.getModifiers()) ? null : target)) {
            method.setAccessible(true);
         }
         return method.invoke(Modifier.isStatic(method.getModifiers()) ? null : target, args);
      } catch (IllegalAccessException | InvocationTargetException | RuntimeException ignored) {
         return null;
      }
   }

   /**
    * Cache for {@link #findMethod}.
    *
    * <p>Resolution used to scan {@code Class.getMethods()} on every call. That array is
    * freshly allocated by the JVM each time, and {@code setPvp} alone performed four
    * lookups per invocation, so a single world operation triggered several full method
    * scans. Multiverse types are stable for the lifetime of the server, so the resolved
    * {@link Method} is memoised per (class, name, argument shape).</p>
    */
   private static final java.util.Map<String, java.util.Optional<Method>> METHOD_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

   private static Method findMethod(Class<?> type, String name, Object[] args) {
      StringBuilder key = new StringBuilder(type.getName()).append('#').append(name).append('/').append(args.length);
      for (Object arg : args) {
         key.append(':').append(arg == null ? "null" : arg.getClass().getName());
      }
      return METHOD_CACHE
         .computeIfAbsent(key.toString(), ignored -> java.util.Optional.ofNullable(resolveMethod(type, name, args)))
         .orElse(null);
   }

   private static Method resolveMethod(Class<?> type, String name, Object[] args) {
      for (Method method : type.getMethods()) {
         if (!method.getName().equals(name) || method.getParameterCount() != args.length) {
            continue;
         }
         Class<?>[] parameters = method.getParameterTypes();
         boolean compatible = true;
         for (int i = 0; i < parameters.length; i++) {
            if (args[i] != null && !wrap(parameters[i]).isInstance(args[i])) {
               compatible = false;
               break;
            }
         }
         if (compatible) {
            return method;
         }
      }
      return null;
   }

   private static Class<?> wrap(Class<?> type) {
      if (!type.isPrimitive()) return type;
      if (type == boolean.class) return Boolean.class;
      if (type == byte.class) return Byte.class;
      if (type == short.class) return Short.class;
      if (type == int.class) return Integer.class;
      if (type == long.class) return Long.class;
      if (type == float.class) return Float.class;
      if (type == double.class) return Double.class;
      if (type == char.class) return Character.class;
      return type;
   }

   private static Object unwrapOption(Object value) {
      if (value == null) return null;
      Object defined = invoke(value, "isDefined");
      if (defined instanceof Boolean) {
         return (Boolean)defined ? invoke(value, "get") : null;
      }
      return value;
   }

   /**
    * Interprets a Multiverse return value as success or failure.
    *
    * <p>Fails <b>closed</b>: an object we cannot interpret is reported as failure. The
    * original version returned true in that case, which turned "Multiverse changed its
    * API" into "the operation succeeded" and silently dropped world operations.</p>
    */
   private static boolean successfulResult(Object result) {
      if (result == null) {
         return false;
      }
      if (result instanceof Boolean) {
         return (Boolean)result;
      }
      Object success = invoke(result, "isSuccess");
      if (success instanceof Boolean) {
         return (Boolean)success;
      }
      Object failed = invoke(result, "isFailure");
      if (failed instanceof Boolean) {
         return !(Boolean)failed;
      }
      warn("Unrecognised Multiverse result type " + result.getClass().getName() + "; treating as failure.");
      return false;
   }

   private static void warn(String message) {
      if (com.ErrorTown.Main.JavaPlugin != null) {
         com.ErrorTown.Main.JavaPlugin.getLogger().warning("[MultiverseCompat] " + message);
      }
   }

   /** Type-compatible facade retained for the command listener's legacy call sites. */
   public static final class MultiverseCore {
      private final Object delegate;
      private MultiverseCore(Object delegate) { this.delegate = delegate; }
      public MVWorldManager getMVWorldManager() { return worldManager(); }
   }

   /** Type-compatible world manager facade backed by either Multiverse API generation. */
   public static final class MVWorldManager {
      private final Object delegate;
      private MVWorldManager(Object delegate) { this.delegate = delegate; }
      public MultiverseWorld getMVWorld(String name) {
         Object result = invoke(delegate, "getMVWorld", name);
         if (result == null) result = invoke(delegate, "getWorld", name);
         return wrapWorld(unwrapOption(result));
      }
      public boolean isMVWorld(String name) {
         Object result = invoke(delegate, "isMVWorld", name);
         if (result instanceof Boolean) return (Boolean)result;
         result = invoke(delegate, "isWorld", name);
         return result instanceof Boolean && (Boolean)result;
      }
      public boolean removeWorldFromConfig(String name) {
         Method legacy = findMethod(delegate.getClass(), "removeWorldFromConfig", new Object[]{name});
         if (legacy != null) {
            // Report what the API actually returned instead of assuming success.
            Object result = invoke(delegate, "removeWorldFromConfig", name);
            return result == null ? false : successfulResult(result);
         }
         Object result = invoke(delegate, "removeWorld", name);
         return result != null && successfulResult(result);
      }
      public boolean addWorld(String name, Environment environment, String seed, WorldType type,
            boolean structures, String generator) {
         Method method = findMethod(delegate.getClass(), "addWorld",
               new Object[]{name, environment, seed, type, structures, generator});
         if (method == null) {
            return false;
         }
         // The legacy API returns a boolean. Discarding it reported a refused world
         // creation (duplicate name, bad generator, disk error) as a success and
         // short-circuited the modern CreateWorldOptions fallback.
         Object result = invoke(delegate, "addWorld", name, environment, seed, type, structures, generator);
         if (result == null) {
            warn("Multiverse addWorld('" + name + "') threw or returned null.");
            return false;
         }
         return successfulResult(result);
      }
   }

   /**
    * World facade.
    *
    * <p>Every mutator returns whether the underlying Multiverse method was found and
    * invoked. They used to be {@code void}, so the static wrappers above reflected on
    * this facade, got {@code null} back (as {@code Method.invoke} does for void) and
    * reported failure even on success.</p>
    */
   public static final class MultiverseWorld {
      private final Object delegate;
      private MultiverseWorld(Object delegate) { this.delegate = delegate; }
      public boolean setSpawnLocation(Location location) { return called("setSpawnLocation", location); }
      public boolean setAlias(String alias) { return called("setAlias", alias); }
      public boolean setAutoLoad(boolean value) { return called("setAutoLoad", value); }
      public boolean setPVPMode(boolean value) { return setPvp(value); }
      public boolean setPvp(boolean value) {
         // Multiverse 2.x exposes setPVPMode, 5.x exposes setPvp. Try each once.
         return called("setPVPMode", value) || called("setPvp", value);
      }
      public boolean setAllowMonsterSpawn(boolean value) { return called("setAllowMonsterSpawn", value); }
      public boolean setAllowAnimalSpawn(boolean value) { return called("setAllowAnimalSpawn", value); }

      /** True when a matching method existed and completed without throwing. */
      private boolean called(String name, Object... args) {
         Method method = findMethod(delegate.getClass(), name, args);
         if (method == null) {
            return false;
         }
         try {
            if (!method.canAccess(Modifier.isStatic(method.getModifiers()) ? null : delegate)) {
               method.setAccessible(true);
            }
            Object result = method.invoke(Modifier.isStatic(method.getModifiers()) ? null : delegate, args);
            // void -> null means "ran fine"; a returned value is interpreted properly.
            return method.getReturnType() == void.class || successfulResult(result);
         } catch (IllegalAccessException | InvocationTargetException | RuntimeException failure) {
            warn("Multiverse " + name + " failed: " + failure);
            return false;
         }
      }
   }

   private static MultiverseWorld wrapWorld(Object value) {
      return value == null ? null : new MultiverseWorld(value);
   }
}
