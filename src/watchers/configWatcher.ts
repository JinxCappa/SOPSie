import * as vscode from 'vscode';

/**
 * Watches for .sops.yaml configuration file changes
 */
export class ConfigWatcher implements vscode.Disposable {
    private watcher: vscode.FileSystemWatcher;
    private _onDidChange = new vscode.EventEmitter<vscode.Uri>();
    private _onDidCreate = new vscode.EventEmitter<vscode.Uri>();
    private _onDidDelete = new vscode.EventEmitter<vscode.Uri>();
    private debounceTimers: Map<string, NodeJS.Timeout> = new Map();
    private readonly debounceMs = 500;
    private disposables: vscode.Disposable[] = [];

    // Map emitters to event type names for cleaner key generation
    private eventTypeNames: Map<vscode.EventEmitter<vscode.Uri>, string>;

    /**
     * Event fired when a config file changes
     */
    readonly onDidChange = this._onDidChange.event;

    /**
     * Event fired when a config file is created
     */
    readonly onDidCreate = this._onDidCreate.event;

    /**
     * Event fired when a config file is deleted
     */
    readonly onDidDelete = this._onDidDelete.event;

    constructor() {
        // Initialize event type names map
        this.eventTypeNames = new Map([
            [this._onDidChange, 'change'],
            [this._onDidCreate, 'create'],
            [this._onDidDelete, 'delete']
        ]);

        // Watch for .sops.yaml and .sops.yml files in all workspace folders
        this.watcher = vscode.workspace.createFileSystemWatcher(
            '**/.sops.{yaml,yml}'
        );

        // Store listener disposables for proper cleanup
        this.disposables.push(
            this.watcher.onDidChange((uri) => this.handleEvent(uri, this._onDidChange)),
            this.watcher.onDidCreate((uri) => this.handleEvent(uri, this._onDidCreate)),
            this.watcher.onDidDelete((uri) => this.handleEvent(uri, this._onDidDelete))
        );
    }

    private handleEvent(uri: vscode.Uri, emitter: vscode.EventEmitter<vscode.Uri>): void {
        const eventType = this.eventTypeNames.get(emitter) ?? 'unknown';
        const key = `${uri.fsPath}:${eventType}`;

        // Debounce rapid changes
        const existingTimer = this.debounceTimers.get(key);
        if (existingTimer) {
            clearTimeout(existingTimer);
        }

        const timer = setTimeout(() => {
            emitter.fire(uri);
            this.debounceTimers.delete(key);
        }, this.debounceMs);

        this.debounceTimers.set(key, timer);
    }

    dispose(): void {
        // Clear all debounce timers
        for (const timer of this.debounceTimers.values()) {
            clearTimeout(timer);
        }
        this.debounceTimers.clear();

        // Dispose all listener subscriptions
        this.disposables.forEach(d => d.dispose());
        this.disposables = [];

        // Dispose watcher and emitters
        this.watcher.dispose();
        this._onDidChange.dispose();
        this._onDidCreate.dispose();
        this._onDidDelete.dispose();
    }
}
