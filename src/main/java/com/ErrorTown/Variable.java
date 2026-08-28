package com.ErrorTown;

import com.Util.HologramCompat;
import com.Util.PlaceholderValueCache;
import com.Util.StaticsTick;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import net.milkbowl.vault.economy.Economy;
import org.black_ixx.playerpoints.PlayerPoints;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * Process-wide mutable state.
 *
 * <p>Thread safety: several of these collections are written from the async chat
 * listener ({@code AsyncPlayerChatEvent}) and read or removed from the main thread
 * — {@link #wait_chat_input}, {@link #pendingCreateSeed},
 * {@link #pendingCreateCostPaid}, {@link #pendingCreateHomeName} and
 * {@link #inviteHomeName} in particular. They were plain {@code HashMap}s, which can
 * lose entries or corrupt their internal table under concurrent mutation. A lost
 * {@code pendingCreateCostPaid} entry means a player pays twice, so every shared
 * collection here is now a concurrent implementation.</p>
 *
 * <p>Keys are player names rather than UUIDs for backwards compatibility with the
 * on-disk and MySQL layout. That remains a known limitation: state is lost when a
 * player renames.</p>
 */
public class Variable {
   public static Economy econ;
   public static List<String> waitDeleteconfirm = new CopyOnWriteArrayList<>();
   public static List<String> Deletecooldown = new CopyOnWriteArrayList<>();
   public static List<String> KeepWorlds = new CopyOnWriteArrayList<>();
   public static Boolean First = true;
   public static String Final = "";
   public static String worldFinal = "";
   public static String backupFinal = "";
   public static String Log_All = "";
   public static String Tempf;
   public static String Temp;
   public static String Tempf2;
   public static String DelDir;
   public static String server_file_world;
   public static String single_server_gen;
   public static String world_prefix = "";
   public static boolean Cat_Check = false;
   public static String Prefix = "";
   public static boolean check_first_start = true;
   public static File f_log;
   public static File f_PlaceHolders;
   public static Map<String, String> invite_list = new ConcurrentHashMap<>();
   public static String Papi_world;
   public static String CheckIsHome = null;
   public static FileConfiguration getName_yml;
   public static String custom_playerdata_location = "";
   public static String custom_autobackup_location = "";
   public static boolean bungee = true;
   public static String[] ab;
   public static List<String> Debug = new CopyOnWriteArrayList<>();
   /** Bounded TTL cache used by PlaceholderAPI expansions. */
   public static final PlaceholderValueCache cache = new PlaceholderValueCache();
   public static FileConfiguration GUI_YML;
   public static List<String> list_home = new CopyOnWriteArrayList<>();
   /** Sorted in place by the statistics task, so a lock-based list beats copy-on-write here. */
   public static List<StaticsTick> world_StaticsTick = Collections.synchronizedList(new ArrayList<>());
   public static boolean linux_os = false;
   public static String file_loc_prefix = "\\";
   public static String world_subfix_end = "\\";
   public static PlayerPoints playerPoints;
   public static Map<String, List<HologramCompat.Handle>> hololist = new ConcurrentHashMap<>();
   public static FileConfiguration Lang_YML;
   public static boolean Hologram_switch = true;
   public static List<String> AddDebuff = new CopyOnWriteArrayList<>();
   public static List<String> DispathCommand = new CopyOnWriteArrayList<>();
   public static boolean PlyaerPointsModule = false;
   public static Map<String, String> wait_to_spawn_home = new ConcurrentHashMap<>();
   public static String NMS_Version = null;
   public static Map<String, String> wait_to_command = new ConcurrentHashMap<>();
   public static String Soil = "FARMLAND";
   public static Map<String, List<String>> popularity_list = new ConcurrentHashMap<>();
   public static Map<String, Integer> flowers_list = new ConcurrentHashMap<>();
   public static Map<String, String> has_open_gifts_list = new ConcurrentHashMap<>();
   public static Map<String, Double> toplist_popularity_flowers = new ConcurrentHashMap<>();
   public static List<String> has_already_move_world = new CopyOnWriteArrayList<>();
   public static List<String> wait_to_confirm_command = new CopyOnWriteArrayList<>();
   public static boolean hook_multiverseCore = false;
   public static boolean hook_FastAsyncWorldEdit = false;
   public static List<String> create_list_home = new CopyOnWriteArrayList<>();
   public static List<String> has_already_hide_border = new CopyOnWriteArrayList<>();
   public static boolean not_adopt_nms = false;
   // Paper 1.21.8 rejects raw section-sign formatting in legacy Bungee JSON components.
   // true keeps help/action text on Player.sendMessage(String); click actions are intentionally disabled.
   /**
    * Disables clickable chat and falls back to plain {@code sendMessage}.
    *
    * <p>Was {@code true} by default as a workaround for {@code /sh help} being broken. The three
    * underlying faults are fixed (see {@link com.Util.ClickableText}), so clickable chat is on again;
    * {@code Main} still forces this back to {@code true} on hybrid forks that cannot render chat
    * components, and {@code ClickableText} sets it if a component send actually fails.</p>
    */
   public static boolean has_no_click_message = false;
   public static Map<String, String> flying_list = new ConcurrentHashMap<>();
   public static String prefix_p = "";
   public static List<String> calc_cooldown = new CopyOnWriteArrayList<>();
   public static Map<String, String> wait_chat_input = new ConcurrentHashMap<>();
   public static Map<String, String> pendingSetSpawnTarget = new ConcurrentHashMap<>();
   public static Map<String, String> pendingCreateSeed = new ConcurrentHashMap<>();
   public static Map<String, Boolean> pendingCreateCostPaid = new ConcurrentHashMap<>();
   public static Map<String, String> pendingCreateHomeName = new ConcurrentHashMap<>();
   public static Map<String, String> inviteHomeName = new ConcurrentHashMap<>();
   public static Map<String, Long> setSpawnCooldown = new ConcurrentHashMap<>();
   public static Map<String, Integer> homeUnloadTaskIds = new ConcurrentHashMap<>();
}
