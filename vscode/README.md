# SOPSie

Seamlessly edit SOPS-encrypted files in VS Code.

## Features

- **Editor Title Bar Icons**: Lock/unlock icons appear for files matching your `.sops.yaml` rules
- **Decrypt Files**: Click to decrypt files in-place for editing
- **Encrypt Files**: Re-encrypt files with a single click
- **Read-Only Preview**: View decrypted content without modifying the original file
- **Edit In-Place**: Edit decrypted content in a temporary file that auto-encrypts on save
- **Auto-Decrypt**: Optionally auto-decrypt files when opened
- **Status Bar**: Shows encryption status for the current file
- **Key Management**: Update keys from `.sops.yaml` or rotate data keys
- **Multi-Key Support**: Full support for `.sops.yaml` creation_rules with different keys per file pattern
- **Context Menu**: Right-click files in the explorer to access SOPS commands
- **Multi-Format Support**: Works with YAML, JSON, INI, and ENV files

## Requirements

- [SOPS CLI](https://github.com/getsops/sops) must be installed and available in your PATH
- A `.sops.yaml` configuration file in your workspace

> **Note**: SOPSie will detect if SOPS is not installed and provide installation guidance.

## Settings

Settings are available in VS Code's Settings UI (`Ctrl+,` / `Cmd+,`) or via `settings.json`.

### General

| Setting | Default | Description |
|---------|---------|-------------|
| `sopsPath` | `sops` | Path to the SOPS CLI executable |
| `decryptionTimeout` | `30000` | Timeout in ms for decryption operations |

### Behavior

| Setting | Default | Description |
|---------|---------|-------------|
| `openBehavior` | `showEncrypted` | How to handle opening encrypted files: `showEncrypted`, `autoDecrypt`, or `showDecrypted` |
| `saveBehavior` | `manualEncrypt` | How to handle saving: `manualEncrypt`, `autoEncrypt`, or `prompt` |
| `decryptedViewMode` | `preview` | Toolbar button behavior: `preview` (read-only) or `editInPlace` (editable temp file) |
| `confirmUpdateKeys` | `true` | Show confirmation dialog before updating SOPS keys |
| `confirmRotate` | `true` | Show confirmation dialog before rotating data keys |

### Editor

| Setting | Default | Description |
|---------|---------|-------------|
| `autoClosePairedTab` | `true` | Close paired tab when closing either the encrypted or decrypted file |
| `autoCloseTab` | `true` | Auto-close decrypted tabs when opening another file |
| `openDecryptedBeside` | `true` | Open decrypted preview/edit in a side-by-side column |
| `showStatusBar` | `true` | Show SOPS status in the status bar |

### Debugging

| Setting | Default | Description |
|---------|---------|-------------|
| `enableDebugLogging` | `false` | Enable verbose debug logging to the SOPSie output channel |

### Open Behavior Options

- **showEncrypted**: Show the encrypted content as-is (default)
- **autoDecrypt**: Automatically decrypt the file in-place for editing
- **showDecrypted**: Automatically open a decrypted view in an adjacent column (uses `decryptedViewMode` setting)

### Save Behavior Options

- **manualEncrypt**: Require manual encryption via command (default)
- **autoEncrypt**: Automatically encrypt on save
- **prompt**: Prompt before each save to choose encrypt or save as-is

### Decrypted View Mode Options

- **preview**: Open a read-only preview of the decrypted content
- **editInPlace**: Open an editable temporary file that encrypts back on save

## Usage

### Getting Started

1. Install [SOPS CLI](https://github.com/getsops/sops) if you haven't already
2. Create a `.sops.yaml` file in your workspace with your encryption rules
3. Open any file that matches one of your creation rules
4. Use the lock/unlock icons in the editor title bar to encrypt/decrypt

> **Tip**: If you don't see the icons in the editor title bar, check the **More Actions** menu (the `...` button).

### Workflows

SOPSie supports three main workflows depending on your preferences:

#### Manual Workflow (Default)

Best for: Careful, deliberate encryption management

1. Open an encrypted file (shows encrypted content)
2. Click the unlock icon or use the Command Palette to decrypt
3. Make your edits
4. Click the lock icon to re-encrypt when done

#### Auto-Decrypt Workflow

Best for: Frequent editing of encrypted files

Set `openBehavior` to `autoDecrypt`:

1. Open an encrypted file (automatically decrypts in-place)
2. Edit freely
3. Optionally set `saveBehavior` to `autoEncrypt` to re-encrypt on save

#### Side-by-Side Workflow

Best for: Reviewing encrypted content or collaborative editing

Set `openBehavior` to `showDecrypted`:

1. Open an encrypted file
2. A decrypted view automatically opens in an adjacent column
3. The original encrypted file stays untouched
4. Choose between `preview` (read-only) or `editInPlace` (editable) mode via `decryptedViewMode`

### Preview vs Edit-in-Place

When viewing decrypted content (via icons or `showDecrypted` behavior), you have two modes:

**Preview Mode** (`decryptedViewMode: "preview"`)

- Opens a read-only view of the decrypted content
- Original file is never modified
- Great for viewing secrets without risk of accidental changes

**Edit-in-Place Mode** (`decryptedViewMode: "editInPlace"`)

- Opens an editable temporary file with decrypted content
- Saving the temp file automatically encrypts and writes back to the original
- The temp file is cleaned up when closed

### Example `.sops.yaml`

```yaml
creation_rules:
  - path_regex: secrets/.*\.yaml$
    age: age1xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
  - path_regex: \.env\.encrypted$
    pgp: FINGERPRINT
```

### Configuration Hot-Reload

SOPSie automatically watches your `.sops.yaml` files. When you modify them, the configuration is reloaded instantly - no need to restart VS Code.

## Commands

All commands are available via the Command Palette (`Ctrl+Shift+P` / `Cmd+Shift+P`).

| Command | Description |
|---------|-------------|
| **SOPS: Encrypt File** | Encrypt the current file |
| **SOPS: Decrypt File** | Decrypt the current file in-place |
| **SOPS: Show Decrypted Preview** | Open a read-only decrypted preview |
| **SOPS: Edit In-Place** | Open an editable temp file that encrypts on save |
| **SOPS: Update Keys** | Re-encrypt with keys from `.sops.yaml` (changes who can access the file) |
| **SOPS: Rotate Data Key** | Rotate the internal data encryption key |
| **SOPS: Reload Configuration** | Reload `.sops.yaml` configuration |
| **SOPS: Toggle Debug Logging** | Toggle verbose debug logging for the current session |

## Context Menu

Right-click access to SOPSie commands is available in two locations:

### Editor Title Bar

When viewing a file that matches your `.sops.yaml` rules, a **SOPSie** submenu appears in the editor title bar's **More Actions** menu (`...`). For encrypted files, this submenu contains:

| Command | Description |
|---------|-------------|
| **Update Keys** | Re-encrypt with current keys from `.sops.yaml` |
| **Rotate Data Key** | Generate a new data encryption key |

### File Explorer

Right-clicking a file in the explorer shows the **SOPSie** submenu with the same commands. Note: This menu only appears when the active editor has a SOPS-matching file open (see [Troubleshooting](#context-menu-not-showing-in-explorer)).

## Troubleshooting

### Icons not visible in the title bar

VS Code may hide editor title bar icons when the window is narrow. Look for the **More Actions** menu (the `...` button at the right of the tab bar) to find the SOPSie commands.

### Context menu not showing in explorer

The SOPSie context menu in the file explorer only appears when the active editor has a SOPS-matching file open. This is a VS Code limitation - extension context menus in the explorer cannot evaluate custom conditions per-file. Use the editor title bar icons or Command Palette instead.

### SOPS CLI not found

If you see an error about SOPS not being found:

1. Ensure SOPS is installed: `sops --version`
2. If installed but not in PATH, set the full path in `sopsPath`
3. Restart VS Code after making changes

### File not detected as SOPS-encrypted

Ensure your file path matches a `path_regex` or `filename_regex` pattern in your `.sops.yaml` creation rules.

### Getting more information for troubleshooting

Enable debug logging to see detailed information about what SOPSie is doing:

1. **Enable SOPSie debug logging** (choose one):
   - **Via Setting**: Settings > SOPSie > Debugging > Enable Debug Logging
   - **Via Command**: Command Palette > "SOPS: Toggle Debug Logging" (session-only)

2. **Set VS Code's log level to Debug**:
   - Open the **Output** panel (`View > Output`)
   - Select **SOPSie** from the dropdown
   - Click the gear icon in the Output panel and select **Debug** or **Trace**
   - Alternatively: Command Palette > "Developer: Set Log Level..." > select **Debug**

Both steps are required - SOPSie's setting controls whether debug messages are generated, and VS Code's log level controls whether they are displayed.

## Release Notes

See [CHANGELOG.md](CHANGELOG.md) for version history.
