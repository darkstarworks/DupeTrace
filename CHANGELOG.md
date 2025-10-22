# Changelog

All notable changes to DupeTrace will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.5] - 2025-10-23

### Changed
- Improved config.yml readability: added a descriptive overview header while preserving inline default values (no behavioral changes).
- Bumped version to 1.0.5.

## [1.0.4] - 2025-10-23

### Changed
- Bumped version to 1.0.4 and verified PaperMC 1.21.x compatibility.
- Shaded server artifact is now published as `-paper` classifier: DupeTrace-1.0.4-paper.jar; development jar uses `-dev` classifier (no dependencies).
- Updated README to reflect the -paper jar name and build output path.

### Notes
- The plugin is built against Paper API 1.21.10-R0.1-SNAPSHOT and Java 21.
- Final jar includes Kotlin stdlib and database drivers (HikariCP, H2, PostgreSQL); size >5MB is expected.

## [1.0.3] - 2025-10-22

### Fixed
- Default artifact is now the shaded jar (no '-paper' suffix) so servers won't hit `NoClassDefFoundError` for Kotlin or other runtime dependencies.
- Added a friendly startup check that logs a clear error and disables the plugin if runtime dependencies are missing (e.g., when accidentally using the dev jar on a server).

### Changed
- The development jar is now published with a `-dev` classifier (renamed from `-thin`) to make its purpose clearer; most users should use the default shaded jar.

## [1.0.2] - 2025-10-22

### Changed
- The database table creation code was updated to use a different identity column definition for non-Postgres databases.

## [1.0.1] - 2025-10-22

### Fixed
- H2 database URL path normalization to comply with H2 2.x; accepts './' or absolute paths and auto-prefixes relative paths to avoid startup failure.

## [1.0] - 2025-01-XX

### Added
- Comprehensive KDoc documentation for all public APIs
- Configuration validation on plugin startup with warnings for invalid values
- MIT License
- .gitignore for proper version control hygiene
- CHANGELOG.md to track version history

### Changed
- Fixed version number inconsistency (build.gradle.kts now correctly set to 1.0)
- Improved error handling: replaced generic `Throwable` catches with specific `Exception` types
- Enhanced error logging for bundle and shulker box tagging operations
- Updated README.md with user-facing installation and configuration guide

### Removed
- Non-functional placeholder code in DupeTrace.kt onEnable() method

## [0.8] - 2025-01-XX

### Added
- Creative mode duplicate handling with `allow-creative-duplicates` configuration option
- Creative context tracking for duplicate detection
- Enhanced item tagging logic for Shulker Box and Bundle contents
- [CREATIVE] tag in logs and alerts for duplicates created in Creative mode

### Changed
- Refined event handling for better Creative mode detection
- Updated duplicate detection to respect Creative mode context

## [0.7] - 2025-01-XX

### Added
- Advanced duplicate management configuration options:
  - `movement-grace-ms`: Grace period to avoid false positives during rapid legitimate moves (default: 750ms)
  - `known-items-ttl-ms`: Time-to-live for in-memory item tracking to prevent memory growth (default: 10 minutes)
  - `duplicate-alert-debounce-ms`: Minimum interval between duplicate alerts for the same item (default: 2000ms)
- Periodic cleanup task for in-memory known items based on TTL
- Enhanced duplicate detection logic with shulker box content scanning

### Changed
- Optimized event handling for better performance
- Improved in-memory item tracking with automatic TTL-based cleanup
- Mitigated false positives with configurable grace period

## [0.6] - 2025-01-XX

### Added
- `keep-oldest-on-dup-remove` configuration option to prefer keeping the item with the earliest recorded interaction when auto-removing duplicates
- Database query method `getEarliestTransferTs()` to determine the earliest player interaction with an item
- Expanded event tracking for more comprehensive duplicate detection

### Changed
- Improved async database write methods for better concurrency
- Enhanced duplicate removal logic to support oldest-first policy
- Cleanup of edge cases in event handlers

## [0.5] - 2025-01-XX

### Added
- Expanded duplicate detection with additional event handlers
- Enhanced logging for better debugging and audit trails

### Fixed
- H2 database configuration typo corrected

## [0.4] - Initial ActivityListener Release

### Added
- `ActivityListener` for extensive event tracking and duplicate detection
- Database logging for item activities and transfers
- Periodic inventory scanning with configurable intervals
- Configuration options in `config.yml`:
  - `alert-admins`: Toggle for admin alerts
  - `scan-interval`: Configure periodic scan frequency
- Transfer logging support in `DatabaseManager`

### Changed
- Refined `DupeTestCommand` messaging

## [0.3] - Initial Release

### Added
- Core duplicate detection system using per-item UUIDs
- Database persistence (H2 and PostgreSQL support)
- HikariCP connection pooling
- Item tagging with persistent data containers
- Basic event listeners for inventory interactions
- `/dupetest` command for testing
- Permission system (`dupetrace.admin`, `dupetrace.alerts`)
- Configuration file with duplicate detection settings
