plugins {
    java
    // Shades MiniMessage into the jar. See the `shadowJar` block for why relocation is mandatory.
    id("com.gradleup.shadow") version "9.6.1"
}

group = "com.ErrorTown"
version = "2.1.6.0"

/*
 * Supported platforms: Spigot, Paper, Purpur, Leaves (Folia is NOT supported, see README).
 * Supported Minecraft versions: 1.21 up to and including 26.2.
 *
 * The production classpath is the *lowest* supported server API (Spigot 1.21). Spigot's API is a
 * strict subset of Paper's, and 1.21's is a strict subset of 26.2's, so compiling against that
 * floor mechanically rejects any call the oldest supported server could not serve. Paper-only
 * conveniences are reached reflectively instead (see com.Util.Platform).
 *
 * Removals and signature changes in newer versions are not caught by the floor, so
 * `apiCheck` recompiles the exact same sources against the *ceiling* of every supported
 * platform. Both directions have to pass; see the `apiCheck` task group.
 */
val serverApiFloor = "org.spigotmc:spigot-api:1.21-R0.1-SNAPSHOT"
val serverApiCeilings = mapOf(
    // Spigot 26.2 — the strictest ceiling: anything removed from Bukkit shows up here.
    "Spigot" to "org.spigotmc:spigot-api:26.2-R0.1-SNAPSHOT",
    // Paper 26.2 — also covers Purpur and Leaves, which are Paper forks that do not remove API.
    "Paper" to "io.papermc.paper:paper-api:26.2.build.119-stable",
)

/** Adventure/MiniMessage version shaded into the jar. See the `shaded` configuration. */
val adventureVersion = "4.26.1"

/** CraftEngine API, compile-only: the integration is reflective and the plugin is a soft-depend. */
val craftEngineVersion = "26.8.1"

/*
 * All server/plugin APIs are resolved from public Maven repositories so that this
 * repository builds on a clean checkout without a local Minecraft server present.
 *
 * Drop any jar that is not published to Maven (paid or private plugins) into `libs/`
 * and it is picked up automatically. See libs/README.md.
 */
repositories {
    // A few Gradle installations inject repositories through a global init script.
    // Those repositories are outside this project and may be unavailable or stale;
    // clear them so the build is reproducible from the sources declared below.
    clear()
    mavenCentral()
    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.extendedclip.com/content/repositories/placeholderapi/")
    maven("https://repo.dmulloy2.net/repository/public/")
    maven("https://repo.codemc.io/repository/maven-public/")
    maven("https://maven.enginehub.org/repo/")
    maven("https://repo.rosewooddev.io/repository/public/")
    // CraftEngine (custom blocks / items / furniture). Optional at runtime, reflective at use sites.
    maven("https://repo.momirealms.net/releases/")
    maven("https://jitpack.io")
}

/*
 * Everything on the compile classpath that is *not* the server API. The api-check tasks below
 * reuse this so they can swap only the server jar; putting two server APIs on one classpath
 * would silently resolve against whichever came first and defeat the whole check.
 */
val pluginApis: Configuration by configurations.creating
configurations.compileOnly { extendsFrom(pluginApis) }

/**
 * Libraries that are compiled against *and* packaged into the jar, relocated.
 * Everything else on the compile classpath is provided by the server or another plugin.
 */
val shaded: Configuration by configurations.creating
configurations.compileOnly { extendsFrom(shaded) }
// Tests exercise the MiniMessage pipeline directly, so they need the same library the jar carries.
configurations.testImplementation { extendsFrom(shaded) }

val apiCheckClasspaths: Map<String, Configuration> = serverApiCeilings.keys.associateWith { platform ->
    configurations.create("apiCheck$platform") { extendsFrom(pluginApis, shaded) }
}

dependencies {
    // Server API floor. Brings in the legacy Bungee chat components and Guava.
    compileOnly(serverApiFloor)
    serverApiCeilings.forEach { (platform, coordinates) ->
        apiCheckClasspaths.getValue(platform)(coordinates)
    }

    // Hard runtime requirements of the plugin.
    pluginApis("me.clip:placeholderapi:2.11.6") { isTransitive = false }
    pluginApis("com.github.MilkBowl:VaultAPI:1.7.1") { isTransitive = false }
    pluginApis("com.zaxxer:HikariCP:5.1.0") { isTransitive = false }

    /*
     * Annotations only, erased at runtime. These used to arrive transitively through the server
     * API; 26.2 no longer exports them, so they are declared here. Without them every
     * `@Nonnull`/`@NotNull`/`@Nullable` in the sources is a compile error against the new API.
     */
    pluginApis("com.google.code.findbugs:jsr305:3.0.2")
    pluginApis("org.jetbrains:annotations:26.0.2")

    // Optional integrations. Every use site is guarded, see docs/optional-dependencies.md.
    // Transitives are disabled on purpose: only the API types are needed at compile time and
    // the plugins ship their own copies of Guava/GSON/fastutil at runtime.
    pluginApis("com.comphenix.protocol:ProtocolLib:5.3.0") { isTransitive = false }
    pluginApis("de.tr7zw:item-nbt-api-plugin:2.13.2") { isTransitive = false }
    pluginApis("org.black_ixx:playerpoints:3.2.6") { isTransitive = false }
    pluginApis("com.sk89q.worldedit:worldedit-bukkit:7.3.6") { isTransitive = false }
    pluginApis("com.sk89q.worldedit:worldedit-core:7.3.6") { isTransitive = false }
    pluginApis("com.fastasyncworldedit:FastAsyncWorldEdit-Core:2.13.0") { isTransitive = false }
    pluginApis("net.momirealms:craft-engine-core:$craftEngineVersion") { isTransitive = false }
    pluginApis("net.momirealms:craft-engine-bukkit:$craftEngineVersion") { isTransitive = false }

    // Jars that are not published to any public Maven repository.
    pluginApis(fileTree("libs") { include("*.jar") })

    /*
     * MiniMessage, shaded into the jar.
     *
     * Paper bundles Adventure; Spigot does not expose it to plugin class loaders at all, so a
     * plugin that must run on both has to bring its own copy. `libraries:` in plugin.yml would also
     * work but downloads from Maven Central on first start, which is a poor deal for operators
     * behind a slow or filtered connection — the jar carries it instead.
     *
     * Only text processing is used: MiniMessage parses, the legacy serializer writes `§` strings,
     * and nothing ever hands an Adventure type to a server API. That is what makes the relocation
     * below harmless on Paper, where a second, unrelocated Adventure is already present.
     *
     * 4.x rather than 5.x on purpose: 4.26.1 targets an older bytecode level, so the same jar loads
     * on the Java 21 of 1.21.x and the Java 25 of 26.x.
     */
    shaded("net.kyori:adventure-api:$adventureVersion")
    shaded("net.kyori:adventure-text-minimessage:$adventureVersion")
    shaded("net.kyori:adventure-text-serializer-legacy:$adventureVersion")

    testImplementation("org.junit.jupiter:junit-jupiter:5.11.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.11.3")
    // Tests need the server API on their own classpath: `compileOnly` above does not
    // propagate to testCompile/testRuntime, so a test could not so much as name a
    // Bukkit type in a method signature it calls. Bukkit statics are still unusable
    // without a running server — only type resolution is provided here.
    testImplementation(serverApiFloor)
}

java {
    /*
     * Toolchain 25, bytecode 21. Minecraft 26.1+ servers require Java 25 and their API jars are
     * Java 25 class files, which a Java 21 javac cannot even read — so the compiler has to be 25.
     * `release = 21` keeps the emitted bytecode loadable on 1.21.x servers, which run Java 21.
     */
    toolchain { languageVersion = JavaLanguageVersion.of(25) }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = 21
    options.compilerArgs.addAll(listOf("-Xlint:all", "-parameters"))
}

/*
 * Recompile the production sources against the newest release of every supported platform.
 * These tasks only need to succeed — their class files are thrown away.
 */
val apiCheck by tasks.registering {
    group = "verification"
    description = "Compiles the plugin against the newest supported release of every platform."
}

apiCheckClasspaths.forEach { (platform, classpathConfiguration) ->
    val check = tasks.register<JavaCompile>("apiCheck$platform") {
        group = "verification"
        description = "Compiles the plugin against ${serverApiCeilings.getValue(platform)}."
        source(sourceSets.main.get().java)
        classpath = classpathConfiguration
        destinationDirectory = layout.buildDirectory.dir("classes/api-check/$platform")
        // Deprecation noise from the ceiling API is expected and is not what this task guards.
        options.compilerArgs = mutableListOf("-parameters", "-nowarn")
    }
    apiCheck { dependsOn(check) }
}

tasks.check { dependsOn(apiCheck) }

tasks.processResources {
    filesMatching("plugin.yml") {
        expand("version" to project.version)
    }
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }
}

tasks.jar {
    // The distributable is the shaded jar; this one exists only as its input.
    archiveBaseName = "ErrorTown"
    archiveClassifier = "thin"
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.shadowJar {
    archiveBaseName = "ErrorTown"
    archiveClassifier = ""
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    configurations = listOf(shaded)

    /*
     * Relocation is not optional. Paper ships its own unrelocated Adventure; two copies of
     * `net.kyori.adventure.text.Component` visible to one class loader is exactly the
     * NoSuchMethodError/LinkageError class of failure the Adventure FAQ warns about. Relocating ours
     * makes the two independent, which is safe here precisely because no Adventure type ever crosses
     * a server API boundary — see com.Util.Text.
     */
    relocate("net.kyori", "com.ErrorTown.libs.kyori")

    // MiniMessage resolves tags through java.util.ServiceLoader; dropping unused classes breaks it.
    // No minimize() on purpose.
    mergeServiceFiles()
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "META-INF/maven/**")
}

tasks.assemble { dependsOn(tasks.shadowJar) }

tasks.wrapper {
    gradleVersion = "9.3.1"
    distributionType = Wrapper.DistributionType.BIN
    // The build host may not be able to reach services.gradle.org; the URL is still
    // written into gradle-wrapper.properties for everyone else.
    validateDistributionUrl = false
}
