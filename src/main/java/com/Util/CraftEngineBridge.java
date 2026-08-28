package com.Util;

import java.lang.reflect.Method;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Optional bridge to <a href="https://github.com/Xiao-MoMi/craft-engine">CraftEngine</a>.
 *
 * <p>CraftEngine registers custom blocks and items as <i>real</i> Minecraft blocks and items, so a
 * home world containing them already saves, loads and protects correctly with no help from this
 * plugin. What it cannot do on its own is let an operator name a custom item where ErrorTown expects
 * a {@code Material} — a GUI button, a home icon. That is what this class adds: any config value
 * shaped like {@code namespace:id} is offered to CraftEngine first.</p>
 *
 * <p><b>Why reflection rather than a compile-time dependency.</b> CraftEngine is pre-1.0 and says so
 * in its own source ("This will be refactored before the 1.0 release"). Reflection against three
 * small, stable entry points degrades to "CraftEngine features unavailable" if a signature moves,
 * where a compile-time binding would instead mean ErrorTown fails to load. The plugin is not even a
 * soft-depend requirement: everything here answers "no" when CraftEngine is absent.</p>
 *
 * <p>Entry points used, all from {@code net.momirealms.craftengine.bukkit.api}:</p>
 * <ul>
 *   <li>{@code CraftEngineItems.byId(String)} then {@code BukkitItemDefinition.buildBukkitItem(Player)}</li>
 *   <li>{@code CraftEngineItems.getCustomItemId(ItemStack)}</li>
 *   <li>{@code CraftEngineBlocks.isCustomBlock(Block)}</li>
 * </ul>
 */
public final class CraftEngineBridge {
   private static final String ITEMS_CLASS = "net.momirealms.craftengine.bukkit.api.CraftEngineItems";
   private static final String BLOCKS_CLASS = "net.momirealms.craftengine.bukkit.api.CraftEngineBlocks";

   private static volatile State state;

   private CraftEngineBridge() {
   }

   /** @return whether CraftEngine is installed and its API responded to a probe */
   public static boolean isAvailable() {
      return state().available;
   }

   /**
    * Builds a CraftEngine custom item.
    *
    * @param id     a CraftEngine item id such as {@code myfurniture:oak_chair}
    * @param viewer the player the item is built for; CraftEngine uses it for per-player content and
    *               tolerates {@code null}
    * @return the item, or {@code null} when CraftEngine is absent or does not know {@code id}
    */
   public static ItemStack item(String id, Player viewer) {
      State current = state();
      if (!current.available || current.itemById == null || current.buildBukkitItem == null || id == null) {
         return null;
      }
      String trimmed = id.trim();
      if (trimmed.isEmpty()) {
         return null;
      }
      try {
         Object definition = current.itemById.invoke(null, trimmed);
         if (definition == null) {
            return null;
         }
         Object built = current.buildBukkitItem.invoke(definition, viewer);
         return built instanceof ItemStack stack ? stack : null;
      } catch (ReflectiveOperationException | RuntimeException failure) {
         Diag.warnOnce("craftengine-item", "CraftEngine rejected item id '" + trimmed + "'", unwrap(failure));
         return null;
      }
   }

   /**
    * @return the CraftEngine item id of {@code stack}, or {@code null} when it is a plain vanilla
    *         item or CraftEngine is absent
    */
   public static String itemId(ItemStack stack) {
      State current = state();
      if (!current.available || current.customItemId == null || stack == null) {
         return null;
      }
      try {
         Object key = current.customItemId.invoke(null, stack);
         return key == null ? null : String.valueOf(key);
      } catch (ReflectiveOperationException | RuntimeException failure) {
         Diag.warnOnce("craftengine-item-id", "CraftEngine could not identify an item", unwrap(failure));
         return null;
      }
   }

   /**
    * @return whether {@code block} is a CraftEngine custom block; {@code false} when CraftEngine is
    *         absent
    */
   public static boolean isCustomBlock(Block block) {
      State current = state();
      if (!current.available || current.isCustomBlock == null || block == null) {
         return false;
      }
      try {
         return Boolean.TRUE.equals(current.isCustomBlock.invoke(null, block));
      } catch (ReflectiveOperationException | RuntimeException failure) {
         Diag.warnOnce("craftengine-block", "CraftEngine could not classify a block", unwrap(failure));
         return false;
      }
   }

   /**
    * The CraftEngine id of a placed custom block, as {@code namespace:id}.
    *
    * <p>Two routes are tried. The first walks the documented model —
    * {@code getCustomBlockState(Block)} to an {@code ImmutableBlockState}, then {@code owner()} to a
    * {@code Holder}, then {@code value()} to a {@code BlockDefinition} and its {@code id()}. The
    * second falls back to parsing the state's {@code toString()}, which begins with the id, and drops
    * any {@code [property=value]} suffix.</p>
    *
    * <p>The fallback exists because the walk touches more of CraftEngine's pre-1.0 internals than
    * {@link #item} or {@link #isCustomBlock} do; {@code toString()} is cruder but far less likely to
    * move. Both failing returns {@code null}, which every caller reads as "not limited".</p>
    *
    * @return the id, or {@code null} when the block is vanilla, CraftEngine is absent, or neither
    *         route worked
    */
   public static String blockId(Block block) {
      State current = state();
      if (!current.available || current.customBlockState == null || block == null) {
         return null;
      }
      Object blockState;
      try {
         blockState = current.customBlockState.invoke(null, block);
      } catch (ReflectiveOperationException | RuntimeException failure) {
         Diag.warnOnce("craftengine-block-state", "CraftEngine could not report a custom block state", unwrap(failure));
         return null;
      }
      if (blockState == null) {
         return null;
      }

      String viaModel = idFromOwner(blockState);
      if (viaModel != null) {
         return viaModel;
      }
      return idFromToString(blockState);
   }

   private static String idFromOwner(Object blockState) {
      try {
         Object holder = blockState.getClass().getMethod("owner").invoke(blockState);
         if (holder == null) {
            return null;
         }
         Object definition = holder.getClass().getMethod("value").invoke(holder);
         if (definition == null) {
            return null;
         }
         Object id = definition.getClass().getMethod("id").invoke(definition);
         return id == null ? null : normalizeId(String.valueOf(id));
      } catch (ReflectiveOperationException | RuntimeException unavailable) {
         Diag.warnOnce(
            "craftengine-block-owner",
            "CraftEngine's block definition walk is not available in this version; falling back to its state text",
            unwrap(unavailable)
         );
         return null;
      }
   }

   /** {@code mypack:chair[facing=north]} becomes {@code mypack:chair}. */
   private static String idFromToString(Object blockState) {
      String text = String.valueOf(blockState);
      int bracket = text.indexOf('[');
      String head = (bracket > 0 ? text.substring(0, bracket) : text).trim();
      return head.indexOf(':') > 0 ? normalizeId(head) : null;
   }

   private static String normalizeId(String id) {
      String trimmed = id.trim();
      return trimmed.isEmpty() ? null : trimmed.toLowerCase(java.util.Locale.ROOT);
   }

   /** One line for the startup self-check. */
   public static String describe() {
      State current = state();
      if (!current.available) {
         return "CraftEngine: not installed";
      }
      return "CraftEngine: " + current.version + " (items=" + (current.itemById != null) + ", blocks=" + (current.isCustomBlock != null) + ")";
   }

   /** Re-probes after a reload, so installing CraftEngine does not require a full restart. */
   public static void reset() {
      state = null;
   }

   private static State state() {
      State current = state;
      if (current == null) {
         synchronized (CraftEngineBridge.class) {
            current = state;
            if (current == null) {
               current = probe();
               state = current;
            }
         }
      }
      return current;
   }

   private static State probe() {
      if (Bukkit.getPluginManager().getPlugin("CraftEngine") == null) {
         return State.absent();
      }
      State found = new State();
      found.available = true;
      found.version = version();
      try {
         Class<?> items = Class.forName(ITEMS_CLASS);
         found.itemById = items.getMethod("byId", String.class);
         found.customItemId = items.getMethod("getCustomItemId", ItemStack.class);
         // The returned definition type is version-specific; resolve the builder from it, and pin the
         // Bukkit Player overload so the CraftEngine-internal Player overload cannot be picked.
         found.buildBukkitItem = found.itemById.getReturnType().getMethod("buildBukkitItem", Player.class);
      } catch (ReflectiveOperationException | RuntimeException incompatible) {
         Diag.warnOnce(
            "craftengine-items-api",
            "CraftEngine is installed but its item API does not match what ErrorTown expects; "
               + "custom items in GUI.yml and home icons are unavailable",
            unwrap(incompatible)
         );
      }
      try {
         Class<?> blocks = Class.forName(BLOCKS_CLASS);
         found.isCustomBlock = blocks.getMethod("isCustomBlock", Block.class);
         found.customBlockState = blocks.getMethod("getCustomBlockState", Block.class);
      } catch (ReflectiveOperationException | RuntimeException incompatible) {
         Diag.warnOnce(
            "craftengine-blocks-api",
            "CraftEngine is installed but its block API does not match what ErrorTown expects",
            unwrap(incompatible)
         );
      }
      return found;
   }

   private static String version() {
      try {
         return Bukkit.getPluginManager().getPlugin("CraftEngine").getDescription().getVersion();
      } catch (RuntimeException unknown) {
         return "unknown";
      }
   }

   private static Throwable unwrap(Throwable failure) {
      if (failure instanceof java.lang.reflect.InvocationTargetException wrapped && wrapped.getCause() != null) {
         return wrapped.getCause();
      }
      return failure;
   }

   private static final class State {
      private boolean available;
      private String version = "unknown";
      private Method itemById;
      private Method buildBukkitItem;
      private Method customItemId;
      private Method isCustomBlock;
      private Method customBlockState;

      private static State absent() {
         return new State();
      }
   }
}
