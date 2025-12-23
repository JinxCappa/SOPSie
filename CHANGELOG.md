# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.1.3] - 2025-12-23

### Added

- JetBrains IDE support (IntelliJ IDEA, WebStorm, PyCharm, and other JetBrains IDEs)
  - Editor notification banners with quick actions for encrypted files
  - Decrypt and encrypt actions with full encryption lifecycle management
  - Read-only preview mode to view decrypted content without modifying files
  - Edit-in-place mode with automatic re-encryption on save
  - Status bar widget showing current file encryption state
  - Auto-decrypt option when opening encrypted files
  - Key management: update keys from `.sops.yaml` or rotate data keys
  - Context menu integration for all SOPS commands
  - Multi-format support for YAML, JSON, INI, and ENV files

### Changed

- Reorganized repository structure: VS Code extension sources moved to `vscode/` subdirectory

### Fixed

- Fixed encryption failing on Windows with "cannot operate on non-existent file c:\dev\stdin" error
- Fixed encryption failing in containerized/restricted environments with "open /dev/stdin: no such device or address" error
- Use temp file approach for content encryption instead of unreliable stdin piping

## [0.1.2] - 2025-11-27

### Added

- Add edit button to preview tabs for switching from read-only preview to editable mode
- New `switchToEditInPlace` command that transitions decrypted previews to editable temp files while preserving column layout
- Add `untrackDocument` method to EditorGroupTracker for proper mode switching

### Changed

- Replace manual path parsing with `path.basename()` for cleaner code
- Remove deprecated error handler functions (log, logError, dispose)

## [0.1.1] - 2025-11-27

### Fixed

- Fixed column layout preservation when switching between encrypted files in beside mode
- Fixed document watcher to skip already-tracked decrypted temp files, preventing unnecessary re-processing

## [0.1.0] - 2025-11-27

- Initial release