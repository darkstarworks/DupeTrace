DupeTrace — PaperMC plugin (Kotlin + Gradle)

This repository contains a PaperMC plugin written in Kotlin and built with Gradle. This guide is tailored to this project and covers setup, development, building, running, and releasing with a target of Minecraft 1.21.10.

Note: The coordinates in build.gradle.kts currently depend on Paper API 1.21.10-SNAPSHOT and use Java Toolchain 21. The run-paper task is configured to download and run a Paper server for fast local testing.

1) Prerequisites
- Java Development Kit (JDK) 21 installed and on PATH.
- Git (optional but recommended).
- An IDE with Kotlin support (IntelliJ IDEA recommended).
- Internet access for Gradle and PaperMC dependencies.

2) Project layout
- build.gradle.kts — Gradle build script (Kotlin DSL). Uses:
  - Kotlin JVM plugin 2.3.0-Beta1
  - Shadow plugin for shading
  - run-paper plugin for local server
- gradle.properties — Gradle JVM settings and project properties.
- settings.gradle.kts — Gradle project name and settings.
- src/main/kotlin/io/github/darkstarworks/dupeTrace/DupeTrace.kt — Main plugin source.
- src/main/resources/plugin.yml — Bukkit/Spigot/Paper plugin descriptor. The version field is expanded from Gradle’s project version.

3) Minecraft and PaperMC versions
- API dependency: io.papermc.paper:paper-api:1.21.10-R0.1-SNAPSHOT (as set in build.gradle.kts).
- Local run task: tasks.runServer { minecraftVersion("1.21") } in build.gradle.kts.
  - You can change this to the exact version you want to run locally.
- If you change Paper API or server versions, keep API and runtime close to avoid unexpected behavior.

4) How to build
- Windows PowerShell (from project root):
  - ./gradlew.bat clean build
- The build task is configured to depend on shadowJar, so the shaded artifact will be produced automatically.
- Outputs:
  - build/libs/DupeTrace-<version>-all.jar (shaded jar for deployment)
  - build/libs/DupeTrace-<version>.jar (plain jar)

5) Run a local Paper server
- Start a development server with your plugin loaded:
  - ./gradlew.bat runServer
- The first run downloads the specified Paper server. Your plugin’s latest built jar (or shadowJar if present) is automatically used.
- To change the Minecraft version used by runServer, edit in build.gradle.kts:
  tasks {
    runServer {
      minecraftVersion("1.21")
    }
  }

6) Kotlin-specific guidelines for Paper plugins
- Nullability: Many Bukkit/Paper APIs are platform types; be explicit with null checks when interacting with the API.
- Event listeners: Prefer Kotlin functions and use @EventHandler. Consider extension functions for clean code.
- Schedulers: Use Paper’s async tasks with care. Do not access Bukkit API from async threads unless explicitly allowed.
- Data classes: Useful for configuration and serializable data.
- Top-level functions/objects: Keep plugin state encapsulated in your JavaPlugin subclass to avoid global state.

7) Configuration and resources
- plugin.yml: Located at src/main/resources/plugin.yml.
  - The version field is populated from Gradle via processResources and ${version}.
  - Update name, main, api-version, and permissions as needed.
- Additional configs: Create files in src/main/resources and load them from the plugin on enable.

8) Dependency management and shading
- Add library dependencies in dependencies { ... } in build.gradle.kts.
- Use implementation for libraries you want shaded into your plugin jar.
- Keep compileOnly for server APIs provided by Paper at runtime.
- The Shadow plugin (com.gradleup.shadow) already creates an -all.jar. If you relocate packages to avoid classpath conflicts, configure shadowJar with relocations.

9) Testing and debugging
- Logging: Use the built-in JavaPlugin logger or slf4j if shaded. Keep logs concise.
- Quick cycle: Make code changes, run ./gradlew.bat runServer, test in-game.
- Unit tests: You can add JVM unit tests under src/test/kotlin. Mocking the Bukkit API may require test doubles or libraries such as MockBukkit.

10) Releasing
- Ensure the plugin builds and the -all.jar runs on your target Paper version.
- Tag the repository and update version in build.gradle.kts (version = "x.y.z").
- Publish the shaded jar (build/libs/*-all.jar).
- Provide a README with install steps for server admins (copy jar to plugins/ directory, restart server).

11) Keeping versions in sync
- Paper API:
  dependencies {
    compileOnly("io.papermc.paper:paper-api:<mc-version>-R0.1-SNAPSHOT")
  }
- Run server version:
  tasks.runServer.minecraftVersion("<mc-version>")
- For consistency, align <mc-version> in both places. If you intentionally test different minor versions, document it.

12) Common tasks cheat sheet (Windows)
- Build (clean + shaded):
  ./gradlew.bat clean build
- Run dev server:
  ./gradlew.bat runServer
- Update dependencies metadata:
  ./gradlew.bat dependencies
- Show tasks:
  ./gradlew.bat tasks

13) Troubleshooting
- ClassNotFoundException at runtime: Ensure the dependency is shaded (implementation + in shadow jar) or provided by Paper.
- API method missing: Check your Paper API version matches the server you run.
- Kotlin stdlib conflicts: Prefer a single stdlib (kotlin-stdlib-jdk8 already included). If servers already provide Kotlin, consider relocating or excluding duplicates if necessary.

Appendix: File snippets referenced
- build.gradle.kts (relevant parts):
  dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.10-R0.1-SNAPSHOT")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
  }
  tasks {
    runServer {
      minecraftVersion("1.21")
    }
  }
  kotlin {
    jvmToolchain(21)
  }

If you need any adjustments (e.g., targeting a different Paper version or adding CI), let us know.

14) DupeTrace configuration highlights
- auto-remove-duplicates: If true, the plugin will try to automatically remove duplicate copies of the same non-stackable item when detected.
- keep-oldest-on-dup-remove: When auto-removal is enabled, prefer keeping the holder (player) with the earliest recorded interaction with the item (based on dupetrace_item_transfers timestamps). If the intended holder is offline, removal falls back to the current holder.
- movement-grace-ms: Milliseconds to wait before treating a holder change as a duplicate (prevents false positives during legitimate rapid moves). Default 750.
- duplicate-alert-debounce-ms: Minimum milliseconds between repeated duplicate alerts for the same item. Default 2000.
- allow-creative-duplicates: If true, duplicates created in Creative mode are allowed (no alert/removal), and will be tagged as [CREATIVE] in logs/alerts when not allowed.
- known-items-ttl-ms: Time-to-live for in-memory knownItems entries to avoid memory growth. Default 600000 (10 minutes).
- inventory-open-scan-enabled: If false, skips full inventory scans on open to reduce overhead (defaults true).
