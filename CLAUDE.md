# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

ErrorTown (错误庄园) is a Bukkit/Spigot plugin that gives every player a private world
("home"/island) with a level-based border (8×8 → 96×96, 12 levels), members, admins, biome
switching, a per-home nether, and mob-spawn rules. One jar covers **Spigot / Paper / Purpur /
Leaves on Minecraft 1.21 through 26.2**. Folia is deliberately unsupported (no world
load/unload API, no synchronous `Entity#teleport`).

`README.md` is the operator-facing document and is authoritative for configuration semantics.
It and everything in `docs/` are written in Chinese; in-code Javadoc is English.

## Build, test, verify

```bash
./gradlew shadowJar     # the distributable: build/libs/ErrorTown-<version>.jar (~1.5 MB)
./gradlew apiCheck      # recompile all sources against Spigot 26.2 AND Paper 26.2
./gradlew test          # 123 JUnit 5 tests, none need a running server
./gradlew check         # test + apiCheckSpigot + apiCheckPaper
./gradlew test --tests 'com.Util.TextTest'   # one class
./gradlew test --tests 'com.Util.TextTest.miniMessageTagsAreResolved'   # one method
```

`--tests` matches Java method names, not the `@DisplayName` strings the console prints.

`build/libs/ErrorTown-<version>-thin.jar` is the pre-shade intermediate. Never deploy it.

**`./gradlew test` fails in this working copy.** The repository lives at `E:\桌面\ErrorTown`,
and a non-ASCII path breaks the Gradle test worker's classpath encoding — every test class
reports `Could not execute test class ...`. This is an environment fault, not a code fault:
`compileJava`, `apiCheck` and `shadowJar` all succeed here. To actually run tests, copy the
build inputs to an ASCII path first:

```bash
mkdir -p /c/temp/et-verify
cp -r src libs gradle gradlew gradlew.bat build.gradle.kts settings.gradle.kts \
      gradle.properties /c/temp/et-verify/
cd /c/temp/et-verify && ./gradlew test --console=plain
```

Verified working: 123 tests, 0 failures. Do this before claiming tests pass.

Toolchain is **Java 25, `release = 21`**. Java 25 is required because 26.1+ server API jars are
Java 25 class files that a Java 21 `javac` cannot read; bytecode 21 keeps the jar loadable on
the Java 21 of 1.21.x. Everything resolves from public Maven — no local server needed.

`scripts/*.ps1` set up an isolated Paper test server, but `New-TestServer.ps1` expects
`paper-1.21.8-60.jar` and `eula.txt` two directories above the repo root. `docs/natural-home-terrain.md`
still references a `tools/` directory of probe scripts that no longer exists.

## This is reconstructed decompiled source

The tree was rebuilt from Vineflower output of an existing plugin, then renamed and repaired
(see `git log`). That history explains most of what looks wrong:

- `CommandListener.java` is 8858 lines and `MySQL.java` is 3147. `onCommandPlayer`
  (`CommandListener.java:2449-8597`) is one ~6100-line nested `if/else if` chain on
  `args.length` and `args[0]`.
- `com.Util.Diag` exists because the decompiled source had ~90 empty `catch` blocks. The lenient
  control flow is intentional — a failed YAML save must not abort a command — but the cause has
  to be visible. Use `Diag.warn` for one-shot operations and `Diag.warnOnce(signature, ...)` for
  anything that can repeat per tick, per block or per menu render.
- New behaviour goes into small, Bukkit-free, unit-testable classes in `com.Util`, called from
  the command class. Do **not** add state collections, queue bookkeeping or YAML-list arithmetic
  to `CommandListener` (`docs/natural-home-terrain.md:49`). `HomeTerrainPolicy`,
  `HomeCreationQueue`, `HomeCreationMessages`, `ConfigValidator`, `CsvUtil`, `GameRuleNames` and
  `Text` are the model: pure logic, no `Bukkit.*` statics, covered by tests.

## Cross-version strategy

Production compiles against the **floor** (`org.spigotmc:spigot-api:1.21`) so any call the oldest
supported server cannot serve is a compile error rather than a runtime `NoSuchMethodError`.
`apiCheckSpigot` / `apiCheckPaper` recompile the same sources against the **ceiling** (26.2) to
catch removals and signature changes. Both directions must pass; `check` runs all three. Spigot's
API is a subset of Paper's and Purpur/Leaves are Paper forks that remove nothing, so three
compilations cover four platforms.

Everything outside the floor goes through `com.Util.Platform` (673 lines) — platform identity,
game rules by name, spawn-chunk policy, `toCenterLocation`, async chunk loading with a paced
synchronous fallback queue, dropped-item protection, and config material resolution. It uses
reflection (`optionalMethod`, `anyClassPresent`) for Paper-only members. `Platform.shutdown()`
is called from `Main.onDisable` and **fails** pending chunk futures rather than dropping them.

MiniMessage/Adventure 4.26.1 is shaded and relocated to `com.ErrorTown.libs.kyori`. Relocation is
mandatory: Paper ships its own unrelocated Adventure, and it is only safe here because no
Adventure type ever crosses a Bukkit API boundary — `Text` always hands Bukkit a legacy `§` String.

## Architecture

### Lifecycle

`Main.onLoad()` → `Main.init()`: runs `RenameMigration.migrateDataFolder()` **first** (before any
`saveDefaultConfig`/`saveResource`, or the new folder gets files and migration is skipped), detects
the OS, derives `Variable.world_prefix` per server flavour, writes default resources, loads
`config.yml` / `GUI.yml` / language file into `Variable`, runs `ConfigUpdate.update()`, then
`HomeDataUpgrade.apply()`.

`Main.onEnable()`: registers ~40 listeners (most gated on `DisableFunctionButTeleport`), then hooks
Vault, PlayerPoints, NBTAPI, ProtocolLib, holograms, Multiverse, FAWE and PlaceholderAPI. Vault and
NBTAPI are `depend:` entries in `plugin.yml`, and a missing Vault additionally makes `onEnable`
disable the plugin outright unless `DisableFunctionButTeleport` is on. Then `ScheduledTasks.start()`
and the startup self-checks: `reportPlatform` → `Lang.seedMissingFromBundle` → `Lang.audit` →
`reportConfigFindings` → `CreateCostLedger.recoverPending`.

`Main.onDisable()` order matters: stop the creation-slot reaper, `Platform.shutdown()`, close
plugin GUIs, delete holograms, save worlds, and **last** `HomeAudit.flushForShutdown()` (not
`flush()` — `flush()` returns without writing when the async writer holds the single-writer token,
and that writer dies with the plugin). `MainLifecycleSourceTest` pins this ordering.

`/sh reload` calls `Main.init()` again, which re-runs `HikariCPUtils.setSqlConnectionPool()` in
BungeeCord mode. That method reads every credential from the config on each call, so a reload does
pick up changed database settings. It closes the previous pool first (`HikariCPUtils.shutdown()`)
because a replaced `HikariDataSource` otherwise keeps its connections — one `maximumPoolSize` worth
per reload — until the server restarts. `shutdown()` leaves the field null, which is why
`MySQL.getConnection()` null-checks the pool instead of dereferencing it.

### Global mutable state: `Variable`

`com.ErrorTown.Variable` is a bag of ~80 public static mutable fields: derived filesystem paths,
hook flags (`econ`, `playerPoints`, `hook_FastAsyncWorldEdit`, `Hologram_switch`), loaded
`FileConfiguration`s (`Lang_YML`, `GUI_YML`, `getName_yml`) and cross-event caches
(`pendingCreateCostPaid`, `wait_chat_input`, `invite_list`, `flying_list`, …).

Two rules: every shared collection is a concurrent implementation on purpose — the async chat
listener writes several of them while the main thread reads and removes, and a lost
`pendingCreateCostPaid` entry means a player pays twice. Keys are **player names, not UUIDs**, to
match the on-disk and MySQL layout; state is lost when a player renames. That is a known,
accepted limitation — do not "fix" it piecemeal.

Useful path constants set in `Main.init()`: `Variable.Tempf` = `plugins/ErrorTown/playerdata`
(one YAML per home), `Variable.single_server_gen` = server root, `Variable.world_prefix` =
`ErrorTownWorld/` or `""`.

### Storage: two backends, branched at every call site

There is **no storage abstraction**. `Variable.bungee` (from `config.yml`'s `BungeeCord`) is checked
inside essentially every accessor:

- `true` → MySQL tables `ErrorTown_Users` / `ErrorTown_Servers`, via HikariCP
  (`HikariCPUtils`). All SQL lives in `private static final String` constants at the head of
  `MySQL.java`; every one of the ~76 `prepareStatement` calls names one, and no inline SQL literal
  remains. Keep it that way — the decompiler had inlined all of them, and the duplicated literals
  are how `Insert_Value` drifted to 22 placeholders while the live insert bound 23.
- `false` → one YAML file per home at `Variable.Tempf/<homeName>.yml`.

`com.Util.Home` is not a cache or a registry — it is a thin **accessor object** holding only
`name`, and every getter/setter re-reads or re-writes the backend (`Home.java:54-79` is the
pattern, repeated for ~25 fields). Constructing a `Home` is free; calling its getters is I/O.
Entry points are `HomeAPI.getHome(name)`, `HomeAPI.getHomes()`, `HomeAPI.getOwnedHomes(player)`,
`HomeAPI.delHome(name)`. In single-server mode `getHomes()` lists the playerdata directory.

Member/admin/deny lists are stored as **comma-separated strings**. Always mutate them through
`com.Util.CsvUtil`, never with `String.replace(","+name,"")` — substring replacement turned
removing `"Bob"` from `"Bob,Bobby"` into `",by"`. `CsvUtil` splits into elements first and compares
case-insensitively.

`com.Util.Channel` is the BungeeCord plugin-messaging sender (`Connect`, plus `Forward` messages
`waitDelayToHome` / `waitToCommand` / `waitToLoad`); `Main.onPluginMessageReceived` is the
receiver and accepts both the `ErrorTown` and legacy `SummerTown` subchannel so a rolling upgrade
works.

Two durability side-files in the data folder:

- `audit.log` — `HomeAudit`, one line per record, appended by exactly one writer at a time with an
  in-memory queue. It is deliberately not YAML: the original re-parsed and rewrote the whole file
  per record, on the calling thread.
- `create-cost-ledger.yml` — `CreateCostLedger`. Records what a player was charged for a creation
  so every failure path (world error, chunk error, disconnect, queue timeout, stale-slot reap) can
  refund. Written synchronously on each mutation and replayed by `recoverPending()` in `onEnable`;
  a background task retries stuck refunds every 120 s.

`RenameMigration` handles the SummerTown → ErrorTown rename: it moves the data folder
(`ATOMIC_MOVE`, falling back to copy, and refusing to act when both directories exist and the new
one is non-empty) and renames the MySQL tables after the pool is up but **before** `MySQL.init()`
runs `CREATE TABLE IF NOT EXISTS`. Permissions and placeholders are not migrated — they get
runtime fallbacks instead. See `docs/migration-to-errortown.md`.

### Commands

`plugin.yml` declares exactly one command, `st`, aliased `sh`. `CommandListener` implements both
`CommandExecutor` and `TabExecutor`. `onCommand` splits three ways: a degraded
`DisableFunctionButTeleport && bungee` proxy mode that re-implements a subset of subcommands
(`:934-2383`), a few early redirects, then `onCommandPlayer` (`:2449-8597`) — roughly 120 distinct
`args[0]` tokens in a nested if/else chain, falling through to opening `MainGui`.

There is no authoritative subcommand list. Three partial ones must be kept in sync by hand: the
tab completer (`:8598-8643`, which is already incomplete and lists `setspawn` and `info` twice),
the `Help-1`…`Help-6` string lists in each language file, and the permission tree in `plugin.yml`.

### GUIs

Every class in `com.GUI` implements `InventoryHolder` and is its own holder. `InventoryClickListener`
runs at `EventPriority.HIGH`, does `instanceof` on the holder to derive a menu name string
(`"Main"`, `"Create"`, …), cancels the click, and then dispatches on the clicked item's **display
name**. Generic buttons resolve through `getBtnID`, which scans `GUI.yml` root keys for one whose
`InMenu` matches the menu and whose `CustomName` matches the clicked name; the button's `LeftInTo` /
`RightInTo` string is then run via `Bukkit.dispatchCommand` on the next tick.

Because `getBtnID` compares the **raw** YAML `CustomName` against the **rendered** display name,
any MiniMessage or `&` code in a `CustomName` makes that button unclickable. Keep that in mind
before touching either side.

Titles come from `GUI.yml`'s `<Menu>Title`, sizes from `<Menu>Size`, buttons from root-level
`ButtonN` blocks with `InMenu` / `Index` (1-based) / `Material` / `CustomName` / `Lores` /
`Permission` / `LeftInTo` / `RightInTo` / `KeepOpen` and the decoration keys documented in the
README. `GuiSafe` supplies fault tolerance (title/size/material/PlaceholderAPI resolution, each
failure reported once per signature); `ItemSpec` builds the ItemStack and applies the decoration
vocabulary. `GuiSafe.size` accepts 1–6 (rows) or 9–54 in multiples of 9 and falls back with a
warning otherwise, because an illegal value makes `Bukkit.createInventory` throw and the menu never
opens. It intentionally does not grow a menu to fit the highest `Index` — out-of-range buttons are
skipped. `ItemSpec.visibleTo` (`Permission`) is **display control only**; the dispatched command
still checks permissions itself.

### Text and language

`com.Util.Text.format` unifies three authoring styles into one legacy `§` string: MiniMessage tags,
`&` codes (including `&#RRGGBB`), and raw `§` codes. `Formatting.Mode` is `auto` (default),
`minimessage` or `legacy`; in `auto` only strings that *look* like they contain a tag go through
MiniMessage. Results are cached in a bounded LRU.

The critical constraint: language files are full of `<Name>`, `<player>`, `<Key>` placeholders that
look exactly like tags. They survive only because MiniMessage emits unknown tags as literal
content, and `TextTest` pins that. So: never default `Formatting.Mode` to `minimessage`, never
introduce a placeholder token that collides with a real tag name (`<key>`, `<color>`, `<font>`,
`<newline>`, `<reset>`, `<lang>`), and never `Text.format` a string after interpolating
player-supplied text into it.

Read messages through `com.Util.Lang` (`get` / `list` / `format` / `send` / `sendFramed`), not
`Variable.Lang_YML.getString`. `Lang.get` falls back to a bundled default, reports a missing key
once, and passes the result through `Text.format`. `Lang.seedMissingFromBundle()` exists because
Bukkit writes a language file only when it does not already exist, so keys added by a later plugin
version never reach an existing server — this is why `/sh help` used to come back empty.
`Lang.audit()` then diffs the live file against the bundled copy and logs what is still missing.

### Placeholders

`com.PlaceHolder.API extends PlaceholderExpansion` with identifier `ErrorTown`, and
`registerWithLegacyAlias()` also registers a subclass whose identifier is `SummerTown`, because the
placeholder strings live in *other* plugins' configs. `onRequest` is a ~1550-line
`check.contains(...)` chain: a server-scope half when `player == null`, and a per-viewer half.

`onRequest` runs on the main thread and is called many times per tick by scoreboard/tab plugins,
while the resolvers do MySQL reads, fresh `YamlConfiguration.loadConfiguration` calls and world
scans. `PlaceholderValueCache` (one instance at `Variable.cache`) is therefore consulted before any
work in the per-viewer branch: bounded at 8192, access-ordered LRU, case-insensitive keys,
synchronized, TTL from `PlaceHolders.RefreshTime` — which is read from the **language file**, not
`config.yml`. The server-scope branch is not cached.

### Home creation pipeline

Home types are the `/sh create <type>` argument: `1` normal, `2` superflat (`SuperflatPreset` emits
explicit `flat_level_generator_settings` JSON), `airland` sky island (`CustomChunkGenerator`), plus
template-copy variants. When `HomeTerrain.Enabled` is on, `normalizeCreateMode` forces **every**
request to type 1 — vanilla `WorldType.NORMAL` with structures off.

Flow: name resolution → cost gate (`CreateCostGui`) → queue admission
(`HomeCreationCoordinator.consumeAdmission`) → `Bukkit.createWorld` on the main thread →
`applyWorldPolicy` → persist the home row → `HomeCreationCoordinator.prepareInitialArea`. Only
`prepareInitialArea` is async: it joins `Platform.chunkAsync` futures, then bounces back to the main
thread via `runTask` before settling the charge, teleporting and completing. `Bukkit.createWorld`
cannot be moved off-thread, so creation is not spike-free — the queue exists to avoid mass
synchronous pre-generation and to keep players out of a half-built world.

`HomeCreationQueue` is a pure-Java FIFO state machine (WAITING / ADMITTED / RUNNING, plus a bounded
256-entry failure log). The queue serialises **command re-dispatch**, not world I/O:
`startAvailable` re-runs `Bukkit.dispatchCommand` with the request in a `ThreadLocal`, because the
command returns `false` even on success and world readiness is the only usable signal.
`HomeCreationQueue.MaxConcurrent` is clamped to `[1, 2]` by
`HomeTerrainPolicy.normalizeCreationLimit`; two leaked ADMITTED/RUNNING entries would deadlock
creation server-wide, which is why `startReaper` sweeps every 30 s independently of any scheduled
task. WAITING entries are never reaped.

While a home `isPending`, `PlayerTeleportListener` cancels all teleports into it. That is the only
thing keeping players out of a half-generated world.

### Sizes and borders

`HomeTerrainPolicy` is the single source of border sizes. `configuredBorderSize` forks on
`HomeTerrain.Enabled`: the `HomeUpgrade.LevelSizes` table (default 8…96 step 8) or the legacy
`WorldBoard + (level-1) * UpdateRadius` formula. `VIPAdd` is a *radius*, so it adds twice that to
the diameter. Everything ends in a clamp to `[8, 96]`; the 96 cap is a disk/chunk budget decision
(~36–49 chunks and 2–8 MiB per home — see `docs/natural-home-terrain.md`). `upgradedBorderSize`
replaces only the base level size so an upgrade never revokes already-granted VIP diameter, and
`validConfiguredSizes` drops out-of-range entries and sorts them, because an unsorted table would
make an upgrade shrink a home. Five call sites depend on this and all must route through it:
`ScheduledTasks` (60-tick border refresh), `WBControl`, `Util.refreshBorder`, `HomeWorldManager.showHomeInfo`,
`FirstBorderShaped.radiusFor`.

Borders are enforced four ways: the Bukkit `WorldBorder` API (authoritative, re-asserted every 60
ticks), hologram corner markers (`Util.refreshBorder`, gated on `HDSwitch`), an optional physical
block shell (`FirstBorderShaped`, only when FAWE is present **and** `FaweSwitch: true`; coordinates
are computed off-thread but blocks are applied on the main thread at 2000/tick), and a per-player
border for `/sh togglecc` (`WorldBorder/WBControl`).

### Protection

`Util.Check(player, homeName)` → `Util.CheckOwnerAndManagerAndOP` is the central authorization
helper: owner, server op, `OP` list, then the `Members` list with `"*"` meaning public. It is
called from ~27 sites in 16 files, so the decision is centralized but the invocation is repeated
per listener. `HomeProtectionListener` wraps it once as `denied(player, world)`.

For events with no player (piston push, fluid flow, fire spread, `EntityChangeBlock`, dispensers,
`StructureGrow`, explosions, potion clouds) the decision is made with `WorldBorder#isInside`
deliberately, so this listener can never disagree with the size `HomeTerrainPolicy` computed.
Explosions are **filtered**, not cancelled, unless the home's explosion-protect rule is on.

Note `Util.CheckIsHome` returns false for `_nether`, `_end` and `_the_end` worlds and depends on
`Variable.list_home`, which is repopulated every 100 ticks.

### Config: migration and validation

`ConfigUpdate.update()` migrates an existing `config.yml`/`GUI.yml`/language file in place, four
ways: (1) `mergeMissingDefaultConfigKeys` copies every non-section key the live file lacks from the
jar's defaults — **so a brand-new config key needs no `ConfigUpdate` edit as long as it is in
`src/main/resources/config.yml`**; (2) explicit `setConfigDefault` one-offs; (3) blocks gated on the
`Version` value plus idempotent `InternalMigrations.<name><date>` marker flags — this is what you
need when you change the *default value* of an existing key; (4) `repairGuiAndLanguageTexts`, which
rewrites values that are missing or mojibake (`isBrokenText` looks for `搂`, `璁`, `涓`, U+FFFD).

`ConfigValidator` is Bukkit-free by design — rules run against its own `ConfigView` interface so
they are unit-testable, and `Main` supplies the adapter. It runs 11 rules and returns
`Finding(severity, key, message)` logged at startup. The headline invariant: `MaxLevel` counts
*levels* but `MoneyNeed`/`PointsNeed`/`ItemsNeed` count *upgrades*, so each enabled price list needs
`MaxLevel - 1` entries.

## Rules that prevent silent breakage

- **Permissions:** always `Perm.has(target, "ErrorTown.X")`, never `hasPermission`. `Perm` falls
  back to the pre-rename `SummerTown.X` node while `LegacyPermissions` is true, because permissions
  live in LuckPerms and this plugin cannot migrate them.
- **Game rules:** never name a `GameRule` constant in source. Spigot 26.2 renamed all of them and
  Paper 26.2 kept the old ones, so no constant compiles across the supported range. Use
  `Platform.setGameRule(world, "name", "value")` / `Platform.gameRuleValue(...)`, which resolve
  through `GameRuleNames`. That class normalizes namespace/case/underscores, maps the 25 word-for-word
  1.21.11 renames in both directions, and **inverts the value** for the three `disable*` rules whose
  polarity flipped (`disableRaids=true` ≡ `raids=false`). `doFireTick` was deleted rather than
  renamed and is shimmed onto the integer `fire_spread_radius_around_player`.
  `gameRuleValue` returns `""`, never `null`.
- **Materials from config:** never `Material.valueOf(configString)` — it throws and takes the
  enclosing GUI or listener down. Use `Platform.material(...)` / `GuiSafe.material(...)`, which
  include a pre-1.13 alias table. Also resolve it once, not inside a per-block loop.
- **Listener registration:** `HomeProtectionListener` and `HomeCreationCoordinator.startReaper` must
  stay outside the `if (EnableHeightLimit)` branch in `onEnable`. A past edit nested them there and
  every server with `EnableHeightLimit: false` silently lost all protection and the slot reaper.
- **Creation failures must refund.** `HomeCreationCoordinator.fail` goes through `CreateCostLedger`;
  clearing the "already paid" flag alone consumed the payment (default config: 59999 currency +
  520 points per incident). Conversely, `pendingCreateCostPaid` may only be re-asserted when
  `CreateCostLedger.hasCharge` is true, or queued requests get a free create.
- **Mob spawning:** `applyWorldPolicy` must not force spawning off and limits to zero.
  `HomeSpawnUtil` is the single owner of the monster cap; the policy lives in the `doMobSpawning`
  game rule plus the `HomeSpawnCompensation.*` and `HomeRulesDefaults.*` config trees (those two
  are config-key prefixes, not classes — don't go looking for a file).
- **Optional dependencies:** prefer a reflection bridge (`MultiverseCompat`, `HologramCompat`,
  `SeasonsCompat`, `CraftEngineBridge`) that fails *closed*. If you use `compileOnly` instead, the
  presence guard must be in the **same method**, above the first mention of the type — a guard in the
  caller is not enough once inlined. Full policy in `docs/optional-dependencies.md`.
- `docs/world-generation-investigation.md` records that the `No key layers in MapLike[{}]` warning is
  unresolved. Do not delete a world, rewrite generator settings or change `WorldType.FLAT` because
  of it.

## Adding things

**A subcommand:** branch in `onCommandPlayer`; add to the tab completer; add the node to
`plugin.yml` (and as a child of `ErrorTown.command.user` if it should default on); gate it with
`Perm.has`; add message keys to all three `Language/*.yml`; add a `Help-N` line to all three;
optionally add a `GUI.yml` button with `LeftInTo`.

**A user-visible message:** add the key to `Chinese.yml`, `Chinese_TW.yml` and `English.yml`, and
read it via `Lang.get` / `Lang.format`. Existing installs are covered by `seedMissingFromBundle`.

**A config key:** add it to `src/main/resources/config.yml` with a comment; `ConfigUpdate` merges it
into existing files automatically. Add a `ConfigValidator` rule if it can contradict another key.
Changing the *default* of an existing key needs an `InternalMigrations` marker block.

## Tests

`src/test/java/com/Util/` — 123 tests, all headless. The test classpath carries `spigot-api` and
Adventure, so tests may name Bukkit types, but there is no running server and Bukkit statics are
unusable.

Some guarantees need a live server, so they are checked as **text contracts** instead.
`SourceContract` reads `.java` files from `src/main/java` as plain text and asserts on substrings
with informative failure messages. `MainLifecycleSourceTest` uses it to pin the `onDisable`
ordering; `CreateCostContractTest` uses it to pin that every charge is recorded and every failure
path refunds. `HelpPagesResourceTest` and `PluginDescriptorTest` do the equivalent against
`Language/*.yml` and `plugin.yml` directly rather than the loaded config.

These pin literal text — method signatures, field paths, string literals, even the three-space
`"\n   private "` indentation used as a region terminator. If you rename, reformat or move the code
they guard, update the marker in the same commit. The assertion messages tell you which marker broke.

## Repository notes

`bin/` and `build/` are stale build output and are git-ignored; `src/main/resources/` is the source
of truth for `config.yml`, `GUI.yml`, `plugin.yml` and the language files. `libs/` takes jars not
published to Maven and is empty by design — a clean checkout builds with nothing in it.

Commit messages in this repository are Chinese, with a short subject (sometimes a `docs:` /
`refactor:` prefix) and a body that explains *why* and what the change does not cover.

`docs/` map: `migration-to-errortown.md` (SummerTown rename, the four kinds of affected state),
`natural-home-terrain.md` (terrain/queue design and disk estimates), `optional-dependencies.md`
(integration policy), `world-generation-investigation.md` (open superflat warning).
