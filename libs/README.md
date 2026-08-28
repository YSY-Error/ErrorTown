# libs/

Drop-in directory for plugin APIs that are **not published to any public Maven
repository**. Every `*.jar` placed here is added to the `compileOnly` classpath
automatically by `build.gradle.kts`.

`libs/*.jar` is git-ignored on purpose: these are third-party binaries and must
not be committed.

## Is anything required here?

**No.** A clean checkout compiles with zero jars in this directory. Every API the
plugin touches is either resolved from Maven or accessed reflectively.

This directory exists for two cases:

1. **You want to compile against a paid/private API directly.** RealisticSeasons
   is the only such integration in the codebase, and it is already reached through
   `com.Util.SeasonsCompat` (reflection), so no jar is needed.
2. **You need to pin a plugin API to the exact build your server runs.** Drop that
   server's jar here and remove the matching Maven coordinate from
   `build.gradle.kts`.

## Version reference

The Maven coordinates in `build.gradle.kts` were verified against these runtime
versions:

| Plugin | Runtime version tested | Maven coordinate |
|---|---|---|
| Paper | 1.21.8-60 | `io.papermc.paper:paper-api:1.21.8-R0.1-SNAPSHOT` |
| PlaceholderAPI | 2.12.2 | `me.clip:placeholderapi:2.11.6` |
| Vault | 1.7.x | `com.github.MilkBowl:VaultAPI:1.7.1` |
| ProtocolLib | 5.x | `com.comphenix.protocol:ProtocolLib:5.3.0` |
| NBT-API | 2.15.7 | `de.tr7zw:item-nbt-api-plugin:2.13.2` |
| PlayerPoints | 3.3.3 | `org.black_ixx:playerpoints:3.2.6` |
| WorldEdit / FAWE | FAWE 2.15.0 | `com.sk89q.worldedit:worldedit-*:7.3.6`, `com.fastasyncworldedit:FastAsyncWorldEdit-Core:2.13.0` |
| Multiverse-Core | 5.5.1 | *reflection only — no compile dependency* |
| DecentHolograms | 2.9.9 | *reflection only — no compile dependency* |
| RealisticSeasons | any | *reflection only — no compile dependency* |

Compile-time versions are intentionally allowed to be **older** than the runtime
versions: the plugin only uses long-stable API surface, and compiling against the
older API guarantees it keeps working on the newer runtime.
