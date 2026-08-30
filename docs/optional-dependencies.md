# Optional dependencies

ErrorTown compiles against several plugin APIs that may or may not be installed at
runtime. Referencing an absent class throws `NoClassDefFoundError` at the moment the
JVM first resolves it, which in a Bukkit plugin usually means a broken command or a
listener that silently stops firing. Each integration therefore uses one of two
strategies.

## Strategy A — reflection bridge (no compile dependency)

The API never appears as a compile-time type. A dedicated bridge class in
`com.Util` loads it by name and fails closed.

| Plugin | Bridge | Behaviour when absent |
|---|---|---|
| Multiverse-Core 2.x / 5.x | `MultiverseCompat` | every method returns `false` / `null` |
| HolographicDisplays / DecentHolograms | `HologramCompat` | returns a no-op `Handle` |
| RealisticSeasons | `SeasonsCompat` | `copySeasonAndDate` returns `false`, vanilla time is used |

Use this strategy for anything not published to Maven, anything whose API changed
incompatibly between major versions, and anything genuinely optional.

## Strategy B — `compileOnly` plus a runtime guard

The API is a real compile-time type, so **every** call site must sit behind a check
that the plugin is present. The guard has to be in the enclosing method, before the
first line that mentions the type, so that constant-pool resolution never happens on
a server without the plugin.

| Plugin | Guard | Guarded call sites |
|---|---|---|
| Vault (`Economy`) | `Variable.econ != null` | all economy operations |
| PlayerPoints | `Variable.PlyaerPointsModule && Variable.playerPoints != null` | all point operations |
| FastAsyncWorldEdit | `Variable.hook_FastAsyncWorldEdit` (set in `Main`, requires the plugin **and** `FaweSwitch: true`) | `FirstBorderShaped` |
| ProtocolLib | plugin present check before use | `GiftGui`, `GiftGuiCloseListener` |
| NBT-API | shipped as a hard requirement | `Util` NBT helpers |
| PlaceholderAPI | hard requirement, declared in `plugin.yml` | GUI and `API` |

## Rules for new integrations

1. Prefer Strategy A. A reflection bridge costs about 40 lines and removes a whole
   class of runtime failure.
2. If you use Strategy B, the guard goes in the **same method**, above the first
   mention of the type. A guard in a caller is not sufficient once the JIT inlines.
3. A bridge must fail **closed**. Returning "success" when the API is unrecognised
   turns a missing plugin into silent data loss — that was the original defect in
   `MultiverseCompat.successfulResult`.
4. Log once at `WARNING` when a bridge gives up. Silent reflection failures are
   indistinguishable from "the feature is off".
5. Add the Maven coordinate to `libs/README.md`'s version table so the next
   maintainer knows which runtime version the compile target was checked against.
