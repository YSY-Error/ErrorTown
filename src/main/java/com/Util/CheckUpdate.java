package com.Util;

import com.ErrorTown.Variable;
import org.bukkit.Bukkit;

public class CheckUpdate {
   public static final String NOW_VERSION = "V2.1.6.0";
   public static String new_Version = null;

   public static void checkUpdate() {
      try {
         String msg = Variable.Lang_YML.getString("NowIsTheLatestPlugin");
         if (msg != null) {
            if (msg.contains("<Now>")) {
               msg = msg.replace("<Now>", NOW_VERSION);
            }

            Bukkit.getConsoleSender().sendMessage(msg);
         }
      } catch (Exception failure) {
         com.Util.Diag.warnOnce("check-update", "Update check failed", failure);
      }
   }
}
