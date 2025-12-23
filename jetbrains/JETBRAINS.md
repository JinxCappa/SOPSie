# SOPSie JetBrains Plugin Implementation Plan

Port the VS Code SOPSie extension to JetBrains IDEs with full feature parity.

## Target
- All JetBrains IDEs (IntelliJ, WebStorm, PyCharm, GoLand, etc.)
- Kotlin implementation
- Gradle build with IntelliJ Platform plugin

## Package Structure

```
com.sopsie/
├── SopsiePlugin.kt                     # Plugin constants
├── actions/                            # AnAction commands
│   ├── EncryptAction.kt
│   ├── DecryptAction.kt
│   ├── ShowDecryptedPreviewAction.kt
│   ├── EditInPlaceAction.kt
│   ├── SwitchToEditModeAction.kt
│   ├── UpdateKeysAction.kt
│   ├── RotateDataKeyAction.kt
│   └── ReloadConfigAction.kt
├── config/                             # .sops.yaml management
│   ├── SopsConfigManager.kt
│   ├── SopsConfigParser.kt
│   └── SopsRulesMatcher.kt
├── detection/
│   └── SopsDetector.kt                 # Detect encrypted files
├── execution/
│   └── SopsRunner.kt                   # CLI wrapper
├── handlers/
│   ├── TempFileHandler.kt
│   ├── AutoBehaviorHandler.kt
│   └── DecryptedEditorHandler.kt
├── listeners/
│   ├── SopsFileEditorListener.kt       # File open/close events
│   ├── SopsSaveListener.kt             # Save events
│   ├── SopsConfigFileListener.kt       # .sops.yaml changes
│   └── SopsStartupActivity.kt          # Plugin init
├── model/
│   ├── SopsConfig.kt
│   ├── SopsCreationRule.kt
│   ├── FileEncryptionState.kt
│   └── SopsError.kt
├── services/
│   ├── SopsSettingsService.kt          # App-level settings
│   ├── SopsProjectService.kt           # Project-level service
│   ├── FileStateTracker.kt
│   └── LoggerService.kt
├── settings/
│   └── SopsSettingsConfigurable.kt     # Settings UI
├── ui/
│   ├── SopsStatusBarWidget.kt
│   ├── SopsStatusBarWidgetFactory.kt
│   ├── SopsIconProvider.kt
│   └── SopsEditorNotificationProvider.kt
└── util/
    ├── ErrorHandler.kt
    └── SopsIcons.kt
```

## Implementation Phases

### Phase 1: Foundation ✅ COMPLETE
**Files created:**

1. ✅ **`model/FileEncryptionState.kt`** - Encryption state enum

2. ✅ **`model/SopsError.kt`** - Error types and structured errors with factory methods

3. ✅ **`model/SopsConfig.kt`** - SopsConfig, SopsCreationRule, and key type data classes

4. ✅ **`services/SopsSettingsService.kt`** - PersistentStateComponent settings
   - OpenBehavior, SaveBehavior, DecryptedViewMode enums
   - All settings with sensible defaults

5. ✅ **`execution/SopsRunner.kt`** - SOPS CLI wrapper using GeneralCommandLine
   - decrypt(), encrypt(), encryptContent(), updateKeys(), rotate(), checkCliAvailable(), getVersion()

6. ✅ **`detection/SopsDetector.kt`** - Detect SOPS-encrypted files
   - YAML/JSON, INI, ENV, and binary format detection

### Phase 2: Configuration ✅ COMPLETE
**Files created:**

7. ✅ **`config/SopsConfigParser.kt`** - Parse .sops.yaml using SnakeYAML
   - Full validation of creation_rules, regex patterns, key groups

8. ✅ **`config/SopsRulesMatcher.kt`** - Match files to creation rules
   - First-match semantics, LRU regex cache, path normalization

9. ✅ **`config/SopsConfigManager.kt`** - Project service for config management
   - Directory tree walking, nearest config lookup, message bus events

10. ✅ **`listeners/SopsConfigFileListener.kt`** - Watch .sops.yaml for changes
    - BulkFileListener with 500ms debounce, handles create/change/delete/move/rename

### Phase 3: Core Commands ✅ COMPLETE
**Files created:**

11. ✅ **`actions/EncryptAction.kt`** - Encrypt file in-place
    - Background task with progress, notification feedback

12. ✅ **`actions/DecryptAction.kt`** - Decrypt file in-place
    - Background task with progress, notification feedback

13. ✅ **`actions/UpdateKeysAction.kt`** - Re-encrypt with .sops.yaml keys
    - Confirmation dialog, file reload after update

14. ✅ **`actions/RotateDataKeyAction.kt`** - Rotate data key
    - Confirmation dialog, file reload after rotation

### Phase 4: Preview & Edit-in-Place ✅ COMPLETE
**Files created:**

15. ✅ **`handlers/TempFileHandler.kt`** - Manage temp files for edit-in-place
    - Create temp files, watch for saves, encrypt back
    - FileDocumentManagerListener for save events, FileEditorManagerListener for close events

16. ✅ **`handlers/DecryptedEditorHandler.kt`** - Open editor with decrypted view
    - Creates temp files for both preview (read-only) and edit-in-place modes
    - Routes based on decryptedViewMode setting

17. ✅ **`actions/ShowDecryptedPreviewAction.kt`** - Open read-only preview
    - Opens decrypted content in a read-only temp file

18. ✅ **`actions/EditInPlaceAction.kt`** - Open editable temp file
    - Opens decrypted content in editable temp file that encrypts on save

19. ✅ **`actions/SwitchToEditModeAction.kt`** - Switch preview to edit mode
    - Converts a read-only preview to an editable temp file
    - Closes preview, cleans up, opens edit-in-place for the same source file

### Phase 5: Auto Behaviors ✅ COMPLETE
**Files created:**

20. ✅ **`services/FileStateTracker.kt`** - Track per-file encryption state
    - ConcurrentHashMap-backed set for thread-safe tracking
    - markDecrypted(), markEncrypted(), isMarkedDecrypted(), clearFile() methods

21. ✅ **`listeners/SopsFileEditorListener.kt`** - Handle file open/close events
    - FileEditorManagerListener implementation
    - Delegates to AutoBehaviorHandler on file open
    - Cleans up FileStateTracker on file close

22. ✅ **`listeners/SopsSaveListener.kt`** - Handle file save events
    - FileDocumentManagerListener.beforeDocumentSaving implementation
    - Delegates to AutoBehaviorHandler for auto-encrypt behavior

23. ✅ **`handlers/AutoBehaviorHandler.kt`** - Auto-decrypt/encrypt logic
    - handleFileOpened(): Routes to AUTO_DECRYPT or SHOW_DECRYPTED based on settings
    - handleDocumentWillSave(): AUTO_ENCRYPT, PROMPT, or MANUAL_ENCRYPT behaviors
    - Background task execution with ProgressManager
    - User prompts via Messages.showYesNoCancelDialog

### Phase 6: UI Components ✅ COMPLETE
**Files created:**

24. ✅ **`ui/SopsStatusBarWidgetFactory.kt`** & **`ui/SopsStatusBarWidget.kt`**
    - Show encryption state for current file
    - Clickable to encrypt/decrypt
    - Updates on file selection and config changes

25. ✅ **`ui/SopsIconProvider.kt`** - File decorators for encrypted files
    - Shows lock icon for encrypted files in Project View
    - Shows warning for unencrypted files matching SOPS rules

26. ✅ **`ui/SopsEditorNotificationProvider.kt`** - Banner on encrypted files
    - Shows contextual actions based on encryption state
    - Preview, Edit In Place, Encrypt/Decrypt buttons

27. ✅ **`settings/SopsSettingsConfigurable.kt`** - Settings UI panel
    - Uses Kotlin UI DSL
    - Configures SOPS path, timeout, behaviors, confirmations

28. ✅ **`util/SopsIcons.kt`** - Lock/unlock icons
    - Uses standard IntelliJ platform icons

### Phase 7: Initialization & Polish ✅ COMPLETE
**Files created:**

29. ✅ **`listeners/SopsStartupActivity.kt`** - Plugin initialization
    - ProjectActivity implementation for startup tasks
    - Check CLI availability, show warning if missing
    - Initialize SopsConfigManager on project open

30. ✅ **`services/SopsProjectService.kt`** - Project-level service coordinator
    - Unified API for encryption state, temp file management
    - Delegates to specialized services
    - Provides isCliAvailable(), getCliVersion() methods

31. ✅ **`services/LoggerService.kt`** - Debug logging
    - Centralized logging with debug toggle from settings
    - ComponentLogger for prefixed logging per class

32. ✅ **`util/ErrorHandler.kt`** - User-friendly error messages
    - Notification helpers with action buttons
    - Links to settings for CLI_NOT_FOUND
    - Links to docs for troubleshooting

33. ✅ **`actions/ReloadConfigAction.kt`** - Manual config reload
    - Reloads all .sops.yaml configurations
    - Shows notification with count of loaded configs

**Files modified:**

34. ✅ **`jetbrains/build.gradle.kts`**
    - Remove `bundledPlugin("com.intellij.java")` for cross-IDE compatibility

35. ✅ **`jetbrains/src/main/resources/META-INF/plugin.xml`**
    - ✅ Application services: SopsSettingsService, SopsRunner, SopsDetector, LoggerService
    - ✅ Project services: SopsConfigManager, SopsProjectService, TempFileHandler, DecryptedEditorHandler, FileStateTracker, AutoBehaviorHandler
    - ✅ Application listeners: SopsConfigFileListener
    - ✅ Project listeners: SopsFileEditorListener, SopsSaveListener
    - ✅ Notification group
    - ✅ Actions: Encrypt, Decrypt, UpdateKeys, RotateDataKey, ShowPreview, EditInPlace, ReloadConfig
    - ✅ UI: StatusBarWidgetFactory, IconProvider, EditorNotificationProvider
    - ✅ Settings: SopsSettingsConfigurable
    - ✅ Startup: SopsStartupActivity (postStartupActivity)

## Key JetBrains APIs

| VS Code Concept | JetBrains Equivalent |
|-----------------|---------------------|
| `registerCommand()` | `AnAction` + plugin.xml |
| `WorkspaceConfiguration` | `PersistentStateComponent` |
| `onDidOpenTextDocument` | `FileEditorManagerListener` |
| `onWillSaveTextDocument` | `FileDocumentManagerListener.beforeDocumentSaving` |
| `TextDocumentContentProvider` | Temp files or custom `FileEditor` |
| `StatusBarItem` | `StatusBarWidgetFactory` |
| `spawn()` | `GeneralCommandLine` + `OSProcessHandler` |
| Context keys | `AnAction.update()` method |

## Critical Files to Reference

- `vscode/src/types.ts` - Data models
- `vscode/src/sops/sopsRunner.ts` - CLI execution
- `vscode/src/sops/sopsDetector.ts` - Encryption detection
- `vscode/src/config/configManager.ts` - Config loading
- `vscode/src/watchers/documentWatcher.ts` - File events coordinator
- `vscode/src/services/settingsService.ts` - Settings structure
- `vscode/src/handlers/tempFileHandler.ts` - Temp file management
- `vscode/src/handlers/autoBehaviorHandler.ts` - Auto behaviors

## plugin.xml Structure

### Final (Completed)

```xml
<idea-plugin>
    <id>com.sopsie.jetbrains</id>
    <name>SOPSie</name>
    <vendor>JinxCappa</vendor>
    <depends>com.intellij.modules.platform</depends>

    <extensions defaultExtensionNs="com.intellij">
        <applicationService serviceImplementation="com.sopsie.services.SopsSettingsService"/>
        <applicationService serviceImplementation="com.sopsie.execution.SopsRunner"/>
        <applicationService serviceImplementation="com.sopsie.detection.SopsDetector"/>
        <applicationService serviceImplementation="com.sopsie.services.LoggerService"/>
        <projectService serviceImplementation="com.sopsie.config.SopsConfigManager"/>
        <projectService serviceImplementation="com.sopsie.services.SopsProjectService"/>
        <projectService serviceImplementation="com.sopsie.handlers.TempFileHandler"/>
        <projectService serviceImplementation="com.sopsie.handlers.DecryptedEditorHandler"/>
        <projectService serviceImplementation="com.sopsie.services.FileStateTracker"/>
        <projectService serviceImplementation="com.sopsie.handlers.AutoBehaviorHandler"/>
        <applicationConfigurable parentId="tools" instance="com.sopsie.settings.SopsSettingsConfigurable" id="com.sopsie.settings" displayName="SOPSie"/>
        <statusBarWidgetFactory id="SopsieStatusBar" implementation="com.sopsie.ui.SopsStatusBarWidgetFactory" order="after Encoding"/>
        <iconProvider implementation="com.sopsie.ui.SopsIconProvider" order="first"/>
        <editorNotificationProvider implementation="com.sopsie.ui.SopsEditorNotificationProvider"/>
        <notificationGroup id="Sopsie.Notifications" displayType="BALLOON"/>
        <postStartupActivity implementation="com.sopsie.listeners.SopsStartupActivity"/>
    </extensions>

    <applicationListeners>
        <listener class="com.sopsie.listeners.SopsConfigFileListener" topic="com.intellij.openapi.vfs.newvfs.BulkFileListener"/>
    </applicationListeners>

    <projectListeners>
        <listener class="com.sopsie.listeners.SopsFileEditorListener" topic="com.intellij.openapi.fileEditor.FileEditorManagerListener"/>
        <listener class="com.sopsie.listeners.SopsSaveListener" topic="com.intellij.openapi.fileEditor.FileDocumentManagerListener"/>
    </projectListeners>

    <actions>
        <group id="Sopsie.MainMenu" text="SOPS" popup="true">
            <add-to-group group-id="ToolsMenu" anchor="last"/>
        </group>
        <action id="Sopsie.Encrypt" class="com.sopsie.actions.EncryptAction" text="Encrypt File">
            <add-to-group group-id="Sopsie.MainMenu"/>
            <add-to-group group-id="EditorPopupMenu" anchor="last"/>
            <add-to-group group-id="ProjectViewPopupMenu" anchor="last"/>
        </action>
        <action id="Sopsie.Decrypt" class="com.sopsie.actions.DecryptAction" text="Decrypt File">
            <add-to-group group-id="Sopsie.MainMenu"/>
            <add-to-group group-id="EditorPopupMenu" anchor="last"/>
            <add-to-group group-id="ProjectViewPopupMenu" anchor="last"/>
        </action>
        <action id="Sopsie.ShowPreview" class="com.sopsie.actions.ShowDecryptedPreviewAction" text="Show Decrypted Preview">
            <add-to-group group-id="Sopsie.MainMenu"/>
            <add-to-group group-id="EditorPopupMenu" anchor="last"/>
        </action>
        <action id="Sopsie.EditInPlace" class="com.sopsie.actions.EditInPlaceAction" text="Edit In Place">
            <add-to-group group-id="Sopsie.MainMenu"/>
            <add-to-group group-id="EditorPopupMenu" anchor="last"/>
        </action>
        <action id="Sopsie.SwitchToEditMode" class="com.sopsie.actions.SwitchToEditModeAction" text="Switch to Edit Mode">
            <add-to-group group-id="Sopsie.MainMenu"/>
            <add-to-group group-id="EditorPopupMenu" anchor="last"/>
        </action>
        <action id="Sopsie.UpdateKeys" class="com.sopsie.actions.UpdateKeysAction" text="Update Keys">
            <add-to-group group-id="Sopsie.MainMenu"/>
        </action>
        <action id="Sopsie.RotateDataKey" class="com.sopsie.actions.RotateDataKeyAction" text="Rotate Data Key">
            <add-to-group group-id="Sopsie.MainMenu"/>
        </action>
        <action id="Sopsie.ReloadConfig" class="com.sopsie.actions.ReloadConfigAction" text="Reload Configuration">
            <add-to-group group-id="Sopsie.MainMenu"/>
        </action>
    </actions>
</idea-plugin>
```

## Notes

- JetBrains doesn't have virtual document providers like VS Code - use temp files for preview/edit
- All UI updates must run on EDT: `ApplicationManager.getApplication().invokeLater {}`
- Long operations use `ProgressManager.getInstance().run(Task.Backgroundable(...))`
- File writes require `WriteCommandAction.runWriteCommandAction()`
