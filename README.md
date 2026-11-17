# FuzzyPermissions

A high-performance, strictly asynchronous, role-based permissions plugin for PaperMC.

FuzzyPermissions abandons legacy Bukkit permission concepts (like inheritance trees, wildcards, and blocking database reads) in favour of a modern, lock-free, compiled snapshot architecture. It is built entirely in Kotlin using Virtual Threads for database I/O, ensuring that the main server thread never stalls on a permission check.

## Features

* **Async by default:** All database reads and writes execute on Virtual Threads.
* **Lock-free cache:** Permissions are pre-calculated as immutable snapshots on login and stored in a `ConcurrentHashMap` for `O(1)` read speed by Vault and Paper.
* **Flat permission model:** No inheritance. A player can hold multiple roles simultaneously. Roles include exact nodes and optional "groups" (reusable bundles of nodes) which are flattened during cache generation.
* **No wildcards or negations:** Enforces explicit permission management. (`foo.*` and `-foo.bar` are not supported).
* **Database driven:** Supports PostgreSQL (with native UUIDs) and SQLite (for local/single-server setups). Role definitions, assignments, and audit logs are completely database-backed.
* **Elevation API:** An exposed public API allowing authorized plugins (like TOTP authenticators) to grant cryptographically signed, session-only, temporary role elevations with cooldowns and bossbar timers.
* **Vault integration:** 100% non-blocking Vault permission bridge.

## Architecture

The project is split into three Gradle modules:

* **`:api`**: The public surface. Contains pure Kotlin, platform-agnostic models (`RoleSnapshot`, `PlayerPermissionSnapshot`) and the `ElevationApi` interface.
* **`:core`**: The engine. Handles HikariCP connection pooling, Flyway database migrations, plain JDBC data access, and the snapshot compilation logic.
* **`:plugin`**: The Paper platform layer. Contains the bootstrapper, Paper Event listeners, Vault integration, and Brigadier commands via the Lifecycle API.

## Requirements

* **PaperMC** 1.21.1+

## Development setup

1. **Clone the repository:**
   ```bash
   git clone <repository_url>
   cd FuzzyPermissions
   ```

2. **Build the plugin:**
   ```bash
   ./gradlew clean build
   ```

*The final shadowed JAR will be located in `plugin/build/libs/plugin.jar`.*

3. **Run a test server:**
   ```bash
   ./gradlew runServer
   ```

*This uses the `run-paper` Gradle plugin to download Paper and boot a local server automatically. See the `runServer` task in `plugin/build.gradle.kts` for configuration.*

## Roadmap & next steps

* **Permissible injection:** Inject a custom `PermissibleBase` into players upon join to intercept Paper's standard permission checks (`hasPermission()`) and route them to our lock-free cache.
* **Lifecycle brigadier commands:** Implement `/role` admin commands using Paper's `LifecycleEventManager`.
* **Config mode / staging session:** Allow admins to edit roles in-game using a transactional staging memory that commits directly to the database.
* **Vault bridge implementation:** Implement the `net.milkbowl.vault.permission.Permission` interface.
