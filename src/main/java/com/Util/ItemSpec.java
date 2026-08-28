package com.Util;

import com.ErrorTown.Variable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

/**
 * Builds a GUI button from its {@code GUI.yml} entry.
 *
 * <p><b>What an operator can put on a button.</b> {@code Material}, {@code CustomName},
 * {@code Lores}, {@code Enchants}, {@code Index}, {@code SubID}, {@code LeftInTo},
 * {@code RightInTo} and {@code KeepOpen} were the whole vocabulary. This class adds the keys that
 * make a menu actually themeable, all optional and all ignored when absent:</p>
 *
 * <ul>
 *   <li><b>{@code Material}</b> now also accepts a CraftEngine item id — anything containing a
 *       {@code :} that is not a plain material name is offered to
 *       {@link CraftEngineBridge}, so a button can be a custom-modelled item.</li>
 *   <li><b>{@code Amount}</b> — stack size shown on the button.</li>
 *   <li><b>{@code CustomModelData}</b> — the classic resource-pack predicate.</li>
 *   <li><b>{@code ItemModel}</b> — the 1.21.4+ {@code item_model} component, applied reflectively so
 *       the same jar still runs on 1.21.0.</li>
 *   <li><b>{@code Glow}</b> — enchantment shimmer with no enchantment text.</li>
 *   <li><b>{@code Unbreakable}</b>, <b>{@code HideAll}</b>, <b>{@code ItemFlags}</b> — cosmetic
 *       cleanup of the tooltip.</li>
 *   <li><b>{@code SkullOwner}</b> — render the button as a player head.</li>
 * </ul>
 *
 * <p>Display text goes through {@link Text}, so names and lore accept MiniMessage, {@code &} codes
 * and legacy {@code §} codes interchangeably.</p>
 */
public final class ItemSpec {
   /**
    * Stand-in material for a button whose {@code Material} is a CraftEngine id.
    *
    * <p>The GUI classes check {@code Material.matchMaterial(...) == null} before building anything and
    * log {@code MaterialNotFound} if it is. A CraftEngine id is not a material, so
    * {@link #material(String)} answers with this sentinel to get past that gate;
    * {@link #build(String, Player, Material)} then replaces the item wholesale. Nothing renders the
    * sentinel — if it ever showed up in a menu, CraftEngine failed between the two calls and the
    * warning below says so.</p>
    */
   private static final Material CRAFT_ENGINE_SENTINEL = Material.PAPER;

   private ItemSpec() {
   }

   /**
    * Whether a button should be shown to {@code viewer}.
    *
    * <p>A button may carry {@code Permission: some.node}. Without one — the case for every button the
    * plugin ships — this returns {@code true}, so nothing changes for an existing configuration. This
    * is presentation only: the command a button runs still performs its own permission check, so
    * hiding a button is not a way to grant anything.</p>
    *
    * <p>Permission tests go through {@link Perm}, which keeps the pre-rename {@code SummerTown.*}
    * fallback working.</p>
    */
   public static boolean visibleTo(String buttonPath, Player viewer) {
      if (buttonPath == null || Variable.GUI_YML == null) {
         return true;
      }
      String node = string(buttonPath, "Permission");
      if (node == null || node.trim().isEmpty()) {
         return true;
      }
      if (viewer == null) {
         // A menu rendered without a viewer cannot evaluate a per-player node; showing the button
         // keeps the historical layout rather than silently emptying the menu.
         return true;
      }
      return Perm.has(viewer, node.trim());
   }

   /**
    * Resolves the {@code Material} of a button.
    *
    * @param spec a material name, or a CraftEngine item id
    * @return the material, the CraftEngine sentinel, or {@code null} when neither resolves — callers
    *         keep their existing "unknown material" reporting for {@code null}
    */
   public static Material material(String spec) {
      if (spec == null || spec.trim().isEmpty()) {
         return null;
      }
      String trimmed = spec.trim();
      Material vanilla = Material.matchMaterial(trimmed);
      if (vanilla != null) {
         return vanilla;
      }
      if (looksLikeCraftEngineId(trimmed) && CraftEngineBridge.item(trimmed, null) != null) {
         return CRAFT_ENGINE_SENTINEL;
      }
      return null;
   }

   /**
    * Builds the button item for {@code buttonPath}.
    *
    * @param buttonPath the {@code GUI.yml} key of the button, e.g. {@code Button7}
    * @param viewer     the player the menu is being rendered for; may be {@code null}
    * @param fallback   material to use when the spec is not a CraftEngine item
    */
   public static ItemStack build(String buttonPath, Player viewer, Material fallback) {
      String spec = string(buttonPath, "Material");
      ItemStack item = null;
      if (spec != null && looksLikeCraftEngineId(spec)) {
         item = CraftEngineBridge.item(spec, viewer);
         if (item == null && Material.matchMaterial(spec) == null) {
            Diag.warnOnce(
               "gui-craftengine-" + spec,
               "GUI button '" + buttonPath + "' names '" + spec + "', which is neither a material nor a "
                  + "CraftEngine item this server knows; using " + fallback
            );
         }
      }
      if (item == null) {
         item = new ItemStack(fallback == null ? Material.STONE : fallback);
      }
      return decorate(item, buttonPath, viewer);
   }

   /** Applies the optional presentation keys of {@code buttonPath} to an already-built item. */
   public static ItemStack decorate(ItemStack item, String buttonPath, Player viewer) {
      if (item == null || buttonPath == null || Variable.GUI_YML == null) {
         return item;
      }

      int amount = Variable.GUI_YML.getInt(buttonPath + ".Amount", 0);
      if (amount > 0) {
         item.setAmount(Math.min(amount, item.getMaxStackSize()));
      }

      ItemMeta meta = item.getItemMeta();
      if (meta == null) {
         return item;
      }

      int modelData = Variable.GUI_YML.getInt(buttonPath + ".CustomModelData", 0);
      if (modelData > 0) {
         meta.setCustomModelData(modelData);
      }

      applyItemModel(meta, string(buttonPath, "ItemModel"), buttonPath);

      if (Variable.GUI_YML.getBoolean(buttonPath + ".Unbreakable", false)) {
         meta.setUnbreakable(true);
      }

      if (Variable.GUI_YML.getBoolean(buttonPath + ".HideAll", false)) {
         meta.addItemFlags(ItemFlag.values());
      } else {
         for (ItemFlag flag : flags(buttonPath)) {
            meta.addItemFlags(flag);
         }
      }

      if (Variable.GUI_YML.getBoolean(buttonPath + ".Glow", false)) {
         // A hidden enchantment is the only portable shimmer: Paper's setEnchantmentGlintOverride is
         // absent on Spigot, and the 1.20.5+ component form is absent on 1.21.0.
         meta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 1, true);
         meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
      }

      applySkullOwner(meta, string(buttonPath, "SkullOwner"), viewer);

      item.setItemMeta(meta);
      return item;
   }

   /**
    * Sets the 1.21.4+ {@code item_model} component.
    *
    * <p>Reflective because {@code ItemMeta#setItemModel} does not exist on 1.21.0–1.21.3, and this jar
    * has to load there too. An operator on an older server gets one warning instead of a crash.</p>
    */
   private static void applyItemModel(ItemMeta meta, String model, String buttonPath) {
      if (model == null || model.trim().isEmpty()) {
         return;
      }
      try {
         Class<?> namespacedKey = Class.forName("org.bukkit.NamespacedKey");
         Object key = namespacedKey.getMethod("fromString", String.class).invoke(null, model.trim());
         if (key == null) {
            Diag.warnOnce("gui-item-model-" + model, "GUI button '" + buttonPath + "' has an invalid ItemModel '" + model + "'");
            return;
         }
         meta.getClass().getMethod("setItemModel", namespacedKey).invoke(meta, key);
      } catch (NoSuchMethodException unsupported) {
         Diag.warnOnce(
            "gui-item-model-unsupported",
            "This server predates the item_model component (Minecraft 1.21.4); GUI 'ItemModel' keys are ignored"
         );
      } catch (ReflectiveOperationException | RuntimeException failure) {
         Diag.warnOnce("gui-item-model-fail", "Could not apply ItemModel '" + model + "'", failure);
      }
   }

   private static void applySkullOwner(ItemMeta meta, String owner, Player viewer) {
      if (owner == null || owner.trim().isEmpty() || !(meta instanceof SkullMeta skull)) {
         return;
      }
      String name = owner.trim();
      if (name.equalsIgnoreCase("<viewer>") || name.equalsIgnoreCase("%player%")) {
         if (viewer == null) {
            return;
         }
         GuiSafe.setSkullOwner(skull, viewer);
         return;
      }
      GuiSafe.setSkullOwner(skull, org.bukkit.Bukkit.getOfflinePlayer(name));
   }

   private static List<ItemFlag> flags(String buttonPath) {
      List<String> configured = Variable.GUI_YML.getStringList(buttonPath + ".ItemFlags");
      if (configured.isEmpty()) {
         return List.of();
      }
      List<ItemFlag> resolved = new ArrayList<>(configured.size());
      for (String name : configured) {
         if (name == null || name.trim().isEmpty()) {
            continue;
         }
         try {
            resolved.add(ItemFlag.valueOf(name.trim().toUpperCase(Locale.ROOT)));
         } catch (IllegalArgumentException unknown) {
            Diag.warnOnce("gui-item-flag-" + name, "GUI button '" + buttonPath + "' has an unknown ItemFlag '" + name + "'");
         }
      }
      return resolved;
   }

   private static String string(String buttonPath, String key) {
      return Variable.GUI_YML == null ? null : Variable.GUI_YML.getString(buttonPath + "." + key);
   }

   /**
    * @return whether {@code spec} is shaped like a CraftEngine id rather than a material name
    *
    * <p>{@code minecraft:stone} is deliberately <i>not</i> one: {@link Material#matchMaterial} already
    * understands the vanilla namespace, and CraftEngine should not be consulted for it.</p>
    */
   private static boolean looksLikeCraftEngineId(String spec) {
      int colon = spec.indexOf(':');
      if (colon <= 0 || colon == spec.length() - 1) {
         return false;
      }
      String namespace = spec.substring(0, colon).toLowerCase(Locale.ROOT);
      if (namespace.equals("minecraft")) {
         return false;
      }
      // A legacy "MATERIAL:durability" icon spec is numeric after the colon.
      return !spec.substring(colon + 1).chars().allMatch(Character::isDigit);
   }
}
