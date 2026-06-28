You are guiding the user through creating a new SwissKitJ plugin in THIS repo. Each plugin is a self-contained Maven project in its own `SwissKitJ-Plugin-<Name>/` subdirectory, built into a fat JAR and dropped into the host's `.swisskit/plugin/` directory (hot-reload).

Pinned to **SwissKitJ-Api 3.1.0**. Older notes in the wild (3.0.0-alpha, `String` metadata returns, an `AiService` interface, manual `registerTool`) are **wrong** — ignore them.

---

## Source of truth (read these for anything not inline)

The authoritative plugin guide lives in the main SwissKitJ repo — consult it for **full file templates** (pom, `SwissKitJPlugin` impl, UI, AI tools, build):
- EN: https://github.com/MuskStark/SwissKitJ/blob/main/docs/plugins/guide.md
- 中文: https://github.com/MuskStark/SwissKitJ/blob/main/docs/zh/plugins/guide.md
- JavaFX pitfalls + contract: https://github.com/MuskStark/SwissKitJ/blob/main/CLAUDE.md

Fetch via WebFetch. If the repo is private, use `gh api repos/MuskStark/SwissKitJ/contents/<path>` or read from a local SwissKitJ clone.

**Living examples in this repo** (real, maintained — prefer these over stale templates): each `SwissKitJ-Plugin-*/` sibling. `SwissKitJ-Plugin-Qcc` demonstrates DB (H2 + MyBatis) + Excel (Fesod). Copy the closest sibling as your starting point.

---

## Step 1 — Gather requirements (ask if not specified)

1. **Plugin name** (e.g. `KeepAwake`) → subdir `SwissKitJ-Plugin-KeepAwake`, artifactId, class base
2. **Plugin id** — reverse-domain (e.g. `com.example.keepawake`)
3. **Base package** (e.g. `plugin.swisskit.keepawake`)
4. **Category** — `dev` / `text` / `image` / `net` / `other`
5. **One-line description**
6. **Capabilities**: AI tools? database (H2)? Excel I/O? background tasks?

Then scaffold a new `SwissKitJ-Plugin-<Name>/` subdir. Use a sibling's structure as the skeleton and pull full file contents from the guide.

---

## Step 2 — Non-negotiable 3.1.0 rules (the easy-to-break stuff)

- **Versions / scopes**: `fan.summer.api:SwissKitJ-Api:3.1.0` and JavaFX 21.0.2 are both `provided` (host supplies at runtime). Java 21. Shade plugin MUST include `ServicesResourceTransformer` (else SPI files collide and the plugin isn't discovered).
- **Metadata methods return enums, NOT strings**:
  - `getCategory()` → `ToolCategory.DEV|TEXT|IMAGE|NET|OTHER`
  - `getIconStyle()` → `IconStyle.BLUE|PURPLE|TEAL|AMBER|RED|PINK|GRAY`
  - `getType()` → `ToolType.PLUGIN` (external plugins; `BUILTIN` is host-only)
- **SPI file**: `src/main/resources/META-INF/services/fan.summer.api.SwissKitJPlugin` — one FQCN per line. Path is `META-INF/services/` (not `services/`).
- **i18n**: in `createView()`, call `I18n.registerPluginBundle("i18n.messages", getClass().getClassLoader())` **before** building the UI. English goes in the root `messages.properties` (NOT `messages_en.properties`). Prefix keys `plugin.<slug>.`. Use `I18n.bind(prop, key)` for static labels, `I18n.get(key, args…)` for dynamic text.
- **Logging**: `fan.summer.api.log.LoggerFactory` + `PluginLogger`. NEVER `org.slf4j` directly.
- **Notifications**: `GlassNotification.toast/notify/confirm` — NOT `Alert`.
- **Layout**: fill via `setMaxWidth(Double.MAX_VALUE)` + `HBox/VBox.setHgrow`. NEVER `setPrefWidth(Double.MAX_VALUE)`.

---

## Step 3 — If AI tools are needed (v3.1.0+)

- **Declare tools via `aiTools()`** on the plugin class; the host auto-registers/unregisters them (incl. hot-reload). **NEVER call `AiServiceProvider.registerTool(...)` from a plugin.**
- The AI backend type is **`ChatBackend`** (via `AiServiceProvider.getService()` → `Optional<ChatBackend>`). There is **no `AiService` interface** and no `ai.registerTool(...)` — ignore any older doc that mentions them.
- `AiToolResult` is `(boolean success, String output)`. The model only sees `output` — follow the JSON convention:
  - success → `{"success":true,"summary":"…",…payload}`
  - error → `{"success":false,"error":"…"}`
  - Build JSON with your own Gson (shade `com.google.code.gson:gson`) or hand-build — the host's `JsonHelper` is NOT importable by external plugins.
- Give each tool an `Example: toolname{"arg":"…"}` line in its description; provide a short `getLocalDescription()` for the local Qwen3-4B model. Tool name is `snake_case`.

No sibling plugin has AI tools yet — use the guide §8 and the host's `fan.summer.buildintool.*.ai` tools as patterns.

---

## Step 4 — If background tasks are needed

Override `hasRunningTasks()` to return true while a `javafx.concurrent.Task` is running; the host then calls `onBackground()`/`onForeground()` instead of `onDeactivate()`. Clear the flag on success/failure/cancel. Update UI via `Platform.runLater`. Release resources in `onUnload()`. (Full pattern: guide §9.)

---

## Step 5 — If database / Excel are needed

Copy `SwissKitJ-Plugin-Qcc` as the reference. Critical gotchas:
- **H2 path**: `user.dir/.swisskit/plugins/database/pl_<slug>`; URL needs `AUTO_SERVER=TRUE`; call `DatabaseInit.init()` before any DB op.
- **MyBatis**: the XML mapper `namespace` MUST exactly match the Java interface FQCN (mismatch → `BindingException`).
- **Fesod (Excel)**: declare `org.apache.fesod:fesod-sheet` (compile scope, gets shaded); read via `FesodSheet.read(...).sheet().doRead()` with a `ReadListener` that batches.
- Excel split/porting reference (POI/Fesod internals): see SwissKitJ `CLAUDE.md` → "Excel Splitter — Porting Reference".

---

## Step 6 — Build & verify

```bash
mvn clean compile -Pdev && mvn javafx:run -Pdev   # UI debugging via zero-JavaFX-import DevLauncher + PluginPreviewWindow
mvn clean package                                  # fat JAR → target/<plugin>-<ver>.jar
unzip -p target/<plugin>-<ver>.jar META-INF/services/fan.summer.api.SwissKitJPlugin   # verify SPI present
```

Deploy: copy the fat JAR into the host's `.swisskit/plugin/` (hot-reload).

---

## Common mistakes (3.1.0-specific)

| Mistake | Fix |
|---|---|
| `return "OTHER"` / `"BLUE"` from metadata | Return the enum (`ToolCategory.OTHER`, `IconStyle.BLUE`) |
| `swisskit.api.version=3.0.0` | `3.1.0` |
| Manually `AiServiceProvider.registerTool(...)` | Declare via `aiTools()` |
| `AiService` / `ai.registerTool(...)` / `ai.chat(...)` | It's `ChatBackend` via `AiServiceProvider.getService()`; tools via `aiTools()` |
| Tool returns a raw string | JSON: success `{success,summary,…}`, error `{success:false,error}` |
| English in `messages_en.properties` | Root `messages.properties` |
| `org.slf4j.LoggerFactory` | `fan.summer.api.log.LoggerFactory` |
| `Alert` for notifications | `GlassNotification` |
| Shade without `ServicesResourceTransformer` | Add it |
| `setPrefWidth(Double.MAX_VALUE)` | `setMaxWidth(MAX_VALUE)` + grow priority |

For anything beyond these rules (full pom, DB/MyBatis, Excel listener, UI template, `PluginPreviewWindow` config), **read the guide linked at the top** — it is the single source of truth and stays in sync with the API.
