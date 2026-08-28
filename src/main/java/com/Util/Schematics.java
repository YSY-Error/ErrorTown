package com.Util;

import com.ErrorTown.Main;
import com.ErrorTown.Variable;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.session.ClipboardHolder;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

/**
 * Pastes a WorldEdit schematic into a home world.
 *
 * <p>WorldEdit is optional. Callers must check {@link #isAvailable()} first; this class
 * never loads a WorldEdit type before that check succeeds.</p>
 *
 * <p>The paste itself is blocking. {@link #loadSchematic} must therefore not be called
 * from the server main thread for anything larger than a small structure — use
 * {@link #loadSchematicAsync} instead, which performs file and clipboard work off-thread
 * and only touches the world through WorldEdit's own thread-safe extent.</p>
 */
public final class Schematics {
   private Schematics() {
   }

   /** True when a WorldEdit-compatible plugin (WorldEdit or FastAsyncWorldEdit) is present. */
   public static boolean isAvailable() {
      return Bukkit.getPluginManager().getPlugin("WorldEdit") != null
         || Bukkit.getPluginManager().getPlugin("FastAsyncWorldEdit") != null;
   }

   /**
    * Pastes {@code name} at {@code location}.
    *
    * @return true when the paste completed, false when the schematic was missing,
    *         unreadable, in an unknown format, or WorldEdit rejected the operation
    */
   public static boolean loadSchematic(String name, World world, Location location) {
      return loadSchematic(name, world, location, false, true);
   }

   public static boolean loadSchematic(String name, World world, Location location, boolean ignoreAir, boolean copyEntities) {
      if (name == null || world == null || location == null) {
         return false;
      }
      if (!isAvailable()) {
         log(Level.WARNING, "Schematic '" + name + "' was requested but no WorldEdit plugin is installed.", null);
         return false;
      }

      File file = new File(Variable.worldFinal, name);
      if (!file.isFile()) {
         log(Level.WARNING, "Schematic file not found: " + file.getAbsolutePath(), null);
         return false;
      }

      ClipboardFormat format = ClipboardFormats.findByFile(file);
      if (format == null) {
         log(Level.WARNING, "Unsupported schematic format: " + file.getName(), null);
         return false;
      }

      // try-with-resources on both the reader and the edit session: the previous version
      // leaked the input stream and reported failures only through printStackTrace().
      try (InputStream input = new FileInputStream(file);
           ClipboardReader reader = format.getReader(input);
           EditSession editSession = WorldEdit.getInstance().newEditSession(BukkitAdapter.adapt(world))) {
         Clipboard clipboard = reader.read();
         Operations.complete(
            new ClipboardHolder(clipboard)
               .createPaste(editSession)
               .to(BlockVector3.at(location.getBlockX(), location.getBlockY(), location.getBlockZ()))
               .ignoreAirBlocks(ignoreAir)
               .copyEntities(copyEntities)
               .build()
         );
         return true;
      } catch (Exception failure) {
         log(Level.SEVERE, "Failed to paste schematic '" + name + "'", failure);
         return false;
      }
   }

   /** Runs {@link #loadSchematic} off the main thread and reports the result back on it. */
   public static void loadSchematicAsync(String name, World world, Location location, java.util.function.Consumer<Boolean> callback) {
      if (Main.JavaPlugin == null) {
         return;
      }
      Bukkit.getScheduler().runTaskAsynchronously(Main.JavaPlugin, () -> {
         boolean ok = loadSchematic(name, world, location);
         if (callback != null) {
            Bukkit.getScheduler().runTask(Main.JavaPlugin, () -> callback.accept(ok));
         }
      });
   }

   private static void log(Level level, String message, Throwable failure) {
      if (Main.JavaPlugin == null) {
         return;
      }
      if (failure == null) {
         Main.JavaPlugin.getLogger().log(level, message);
      } else {
         Main.JavaPlugin.getLogger().log(level, message, failure);
      }
   }
}
