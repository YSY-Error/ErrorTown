package com.Util;

import java.util.Map;

/**
 * Monotonic high-water mark for the VIP border bonus of a single home.
 *
 * <p><b>Why a ratchet is needed.</b> The VIP bonus is derived from who is <em>online</em> — each
 * {@code VIPAdd} entry is a permission plus a size, and the bonus is the largest entry held by the
 * owner or by any currently-connected home OP. It therefore collapses to {@code 0} the moment the
 * VIP logs off. Feeding that straight into the border size would shrink a border under players who
 * are standing inside it, so every call site instead remembers the largest bonus it has ever seen
 * for that home and never goes below it.</p>
 *
 * <p><b>Why this is a separate class.</b> Six call sites each kept their own copy of the same
 * nested {@code containsKey}/compare/{@code put} block: {@link Util#refreshBorder},
 * {@code WBControl.setDisable}, {@code ScheduledTasks}, {@code BlockPlaceListener},
 * {@code PlayerMoveListener} and {@code PlayerTeleportListener}. Five were identical; the copy in
 * {@code Util} was missing the branch that writes an increased value back, so its map kept whatever
 * bonus was observed first and never rose again. The per-call result was still correct, but after
 * the VIP disconnected the hologram corner markers were drawn at the base size while the
 * authoritative {@code WorldBorder} stayed at the high-water mark, leaving the markers inside the
 * real border.</p>
 *
 * <p>The caller still supplies the map, so the six caches stay independent and consolidating them
 * remains a separate decision. This class is deliberately free of Bukkit types so it can be tested
 * without a running server.</p>
 */
public final class VipBorderRatchet {
   private VipBorderRatchet() {
   }

   /**
    * Raises the remembered bonus for {@code homeName} to {@code freshBonus} when that is larger,
    * and returns the value the caller should use.
    *
    * <p>A negative {@code freshBonus} is treated as {@code 0}: {@code VIPAdd} sizes are radii added
    * to a diameter and a negative one would shrink the border below its level size.
    *
    * @param cache     per-home bonus cache; a {@code null} map makes this a pure {@code max}
    * @param homeName  home name as used by the rest of the plugin — never a UUID, matching the
    *                  on-disk and MySQL layout
    * @param freshBonus bonus just computed from the players currently online
    * @return the greater of {@code freshBonus} and the previously remembered value, never negative
    */
   public static int highWaterMark(Map<String, Integer> cache, String homeName, int freshBonus) {
      int fresh = Math.max(0, freshBonus);
      if (cache == null || homeName == null) {
         return fresh;
      }
      // merge() is the whole ratchet: it stores and returns max(existing, fresh) in one step, and
      // Collections.synchronizedMap overrides it, so the read and the write cannot interleave with
      // another thread the way a containsKey/get/put sequence could.
      Integer settled = cache.merge(homeName, fresh, Math::max);
      return settled == null ? fresh : settled.intValue();
   }
}
