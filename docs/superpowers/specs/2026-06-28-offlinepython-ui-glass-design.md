# OfflinePython Plugin UI — Glass Theme Alignment Design

**Date:** 2026-06-28
**Status:** Approved (pending implementation)
**Scope:** `SwissKitJ-Plugin-OfflinePython` module, `ui/` package only
**Related:** `docs/superpowers/specs/2026-06-26-offline-python-builder-design.md`, `docs/superpowers/plans/2026-06-26-offline-python-builder-v1.md`

---

## 1. Problem

The OfflinePython plugin UI does not match the SwissKitJ host ("main project") UI. Investigation found three disjoint looks in the repo:

1. **Host native glass theme** — provided by the API jar (`Themes`, `swisskit-common.css`, `UiUtils`, `GlassNotification`, `StepWizard`). Accent `#5b8cf7`, translucent `rgba(13,14,17,…)` surfaces, SF Pro Display font, `.glass-*` style classes.
2. **Sibling "board" aesthetic** — HappyLearning / Qcc / KeepAwake share a copy-pasted bespoke look (`#1a1a2e`/`#16213e` opaque navy, amber `#e2b714`, Menlo monospace) that does **not** use the host theme.
3. **OfflinePython (current)** — neither. It is built from raw JavaFX nodes (`Button`, `Label`, `ListView`, `GridPane`) with ad-hoc inline CSS (`rgba(0,0,0,0.28)`, `-fx-padding: 18`) and uses the host API only for `GlassNotification` and `I18n`. It matches neither the host nor the siblings.

Concrete symptoms in the current code:
- `CommandShell`: left nav is a bare `ListView<String>` of plain text items (`init/deps/build/verify/doctor`); top has an unstyled Python badge `Label`; content `StackPane` and bottom log dock are unstyled.
- `LogConsole`: `TextArea` with hand-written `-fx-control-inner-background: rgba(0,0,0,0.28); -fx-text-fill: rgba(255,255,255,0.85);`.
- `CommandPanel` base: `setStyle("-fx-padding: 18;")`, no card surface.
- All five panels: raw `new Button(...)`, raw `Label` titles, raw `TextField`; no host styling.
- `PythonInstallGuide`: plain `VBox` with `-fx-padding: 24;` and raw labels/buttons.

## 2. Goals

- Make the OfflinePython plugin visually consistent with the **SwissKitJ host glass theme**.
- Preserve the existing information architecture (left nav + bottom log dock) and **all** interaction behavior, command orchestration, lifecycle, and i18n.
- Keep the change confined to the `ui/` package; no business-logic changes, no new features.

## 3. Non-Goals

- Do **not** migrate HappyLearning / Qcc / KeepAwake to the host theme (out of scope by decision).
- Do **not** restructure the layout (no switch to tabs or `StepWizard`) — keep left-nav + reskin.
- Do **not** change i18n keys, command behavior, or unit tests.

## 4. Key Decisions (resolved in brainstorming)

| Decision | Choice | Rationale |
|---|---|---|
| Visual target | **Host glass theme** (not sibling board look) | "主项目" = SwissKitJ host; the API was built as the theming contract (`Themes` + 466-line CSS + `UiUtils` + `StepWizard`). |
| Layout | **Keep left sidebar nav + bottom log dock; reskin only** | Lowest risk; preserves current UX; matches host sidebar convention (`-sidebar-width` token). Minimal restructure. |
| Scope | **OfflinePython module only** | Siblings are a separate, deferred effort. |

## 5. Theming Strategy (the mechanism)

The plugin's `createView()` returns a `Node` that the host (or `PluginPreviewWindow` in dev) drops into its `Scene`. `.glass-*` CSS classes only take effect when `swisskit-common.css` is attached to that `Scene`; whether the host always attaches it to the plugin-render area is **not guaranteed**. To render correctly in **both** production and preview, use a robust hybrid:

1. **Attach the host stylesheet to the plugin root.**
   `root.getStylesheets().add(Themes.commonStylesheetUrl())` on the root `Parent`. Node-level stylesheets apply to the subtree and are independent of the hosting `Scene`; the call is idempotent. This brings global selectors (thin scrollbars, default `.progress-bar`, looked-up color tokens) into scope.
2. **Use `UiUtils` helpers for standard controls.**
   `UiUtils.glassBtn(text, primary)`, `fieldStyle()`, `comboStyle()`, `sectionTitle(text)`, `subLabel(text)`, `hSpacer()`. These emit self-contained inline CSS that is byte-identical to the `.glass-*` classes, so they render with zero stylesheet dependency.
3. **Centralize the rest in a new `ui/OpbStyle.java`.**
   For surfaces/controls `UiUtils` does not cover (card/panel background, log text area, nav list items, the Python badge capsule, status badges), use inline styles that reference the **host token values** directly, exposed as named constants from `OpbStyle` (e.g. `GLASS_BG`, `GLASS_BORDER`, `ACCENT`, `TEXT_PRIMARY`, `CARD_RADIUS`, `NAV_RADIUS`). Centralizing avoids scattering magic values and keeps one place to update if the host rethemes.

> Note: looked-up color variables (`-accent`, `-glass-bg`, …) are defined on `.root` in `swisskit-common.css`. Because the plugin root may not carry the `.root` class, inline styles in `OpbStyle` use the **concrete token values**, not the variable names, to avoid resolution gaps. Step 1 still attaches the stylesheet so that any descendant relying on a class selector (e.g. a bare `ProgressBar`) is styled by the host globals.

## 6. Host Design Tokens (reference — the styling contract)

Sourced from `swisskit-common.css` and `UiUtils` bytecode in `SwissKitJ-Api-3.1.0.jar`:

| Token | Value |
|---|---|
| `-accent` | `#5b8cf7` (hover `#4a7bf5`) |
| `-glass-bg` | `rgba(255,255,255,0.055)` |
| `-glass-bg-hover` | `rgba(255,255,255,0.09)` |
| `-glass-border` | `rgba(255,255,255,0.10)` |
| `-glass-border-hi` | `rgba(255,255,255,0.22)` |
| `-accent-soft` | `rgba(91,140,247,0.18)` |
| `-text-primary` | `rgba(255,255,255,0.92)` |
| `-text-secondary` | `rgba(255,255,255,0.50)` |
| `-text-tertiary` | `rgba(255,255,255,0.28)` |
| `-success` / `-warning` / `-danger` | `#4cd97b` / `#f5a623` / `#f25c5c` |
| `-card-radius` | `12px` |
| `-nav-radius` | `8px` |
| `-sidebar-width` | `220px` |
| font | `"SF Pro Display","Segoe UI","PingFang SC","Microsoft YaHei",sans-serif` 13px |

Host style classes available: `.glass-btn-primary`, `.glass-btn-secondary`, `.glass-field`, `.glass-field-label`, `.glass-combo`, `.glass-tab-pane`, `.glass-table`, `.glass-checkbox`, `.section-title`, `.section-header`, `.content-scroll`, `.progress-bar` (global) with `.success`/`.danger` modifiers, `.glass-notif-*`.

Host API helpers: `UiUtils.glassBtn/sectionTitle/subLabel/fieldStyle/comboStyle/hSpacer`, `Themes.applyTo(scene)` / `Themes.commonStylesheetUrl()`, `GlassNotification.toast/notify/confirm`, `StepWizard`, `I18n.get/bind/registerPluginBundle/addListener`.

## 7. Component-by-Component Restyle

### 7.1 `ui/CommandShell.java` (the shell)
- Root `BorderPane`: transparent background; attach `Themes.commonStylesheetUrl()` to its stylesheets.
- **Left nav**: replace the bare `ListView<String>` with a styled vertical nav. Each item shows an MDI-style glyph + i18n label (use the existing `opb.*.title` keys). Width `-sidebar-width` (220). Item visuals via `OpbStyle.navItem(active, hover)`: resting = transparent + `-text-secondary`; hover = `-glass-bg-hover`; selected = `-accent-soft` background + `-accent` text + `-nav-radius` (8) corners. (Implementation may keep a `ListView` with a custom `ListCell`, or a `VBox` of toggle buttons — either is acceptable as long as the visuals match.)
- **Top Python badge**: glass capsule (`OpbStyle.badge(ok)`): detected → `-success` tint + "Python x.y · pip z"; missing → `-danger` tint + `opb.python.missing`.
- **Center content `StackPane`**: transparent.
- **Bottom log dock**: keep docking; the `LogConsole` itself is restyled (7.2).

### 7.2 `ui/LogConsole.java`
- `TextArea`: `-fx-control-inner-background` → `rgba(0,0,0,0.25)` (sits on the glass surface), `-fx-text-fill` → `-text-primary` value, host font, read-only, wrap. Outer pane gets `.content-scroll` class for thin scrollbars. Keep the `log(String)` API, timestamp prefix, and auto-scroll behavior unchanged.

### 7.3 `ui/panel/CommandPanel.java` (base)
- Replace `-fx-padding: 18;` with a glass card surface via `OpbStyle.card()`: background `-glass-bg`, border `-glass-border`, radius `-card-radius` (12), host padding/spacing. Provide a `titleNode()` helper returning `UiUtils.sectionTitle(title())` so panels render a consistent `.section-header`.

### 7.4 Panels (`InitPanel`, `DepsPanel`, `BuildPanel`, `VerifyPanel`, `DoctorPanel`)
Common rules:
- Title via `UiUtils.sectionTitle(...)`.
- Primary action button → `UiUtils.glassBtn(text, true)`; secondary/cancel/copy → `UiUtils.glassBtn(text, false)`.
- Inputs (`TextField`) → `setStyle(UiUtils.fieldStyle())`; labels for fields → `UiUtils.subLabel(...)`.

Per panel:
- **InitPanel**: "Initialize Project…" → primary glass button. Keep `DirectoryChooser` flow, `InitService` call, toasts.
- **DepsPanel**: "Open requirements.txt" (secondary), "Add" (secondary), "Save" (primary); package/version fields use `fieldStyle()`; `ListView` styled as glass list (host font, `-glass-bg` rows, `-accent-soft` selection). Keep parse/save logic.
- **BuildPanel**: "Build" (primary), "Cancel" (secondary). `ProgressBar` stays a bare node (host globals render the 6px accent bar); on completion add `.success` (code 0) or `.danger` style class. Keep `PluginTask`/`ProcessRunner`/disable-during-run logic and `cancel()`.
- **VerifyPanel**: "Verify" (primary). Result rows: each `CheckResult` renders as a status badge (`PASS`→`-success`, `WARN`→`-warning`, `FAIL`→`-danger`) + `subLabel` detail. Keep `VerifyService` call and report `VBox`.
- **DoctorPanel**: "Run diagnostics" (primary). Grid rows: key via `subLabel`, value prefixed ✓ (`-success`) / ✕ (`-danger`). Keep `DoctorService` call and `GridPane`.

### 7.5 `ui/PythonInstallGuide.java`
- Wrap in a glass card (`OpbStyle.card()`). Warning header via `UiUtils.sectionTitle(...)` with a `-warning`/`-danger` glyph; explanatory line via `subLabel`. Command rows: render the command in a `fieldStyle()` (monospace-ish) read-only field + `UiUtils.glassBtn("Copy", false)` (keep clipboard + `GlassNotification.toast` success). "Re-detect" → primary glass button calling `onRedetect`.

### 7.6 `ui/OpbStyle.java` (new)
A final helper class holding:
- Constants for the host token values listed in §6 (`ACCENT`, `GLASS_BG`, `GLASS_BORDER`, `TEXT_PRIMARY`, `TEXT_SECONDARY`, `SUCCESS`, `WARNING`, `DANGER`, `CARD_RADIUS`, `NAV_RADIUS`, `SIDEBAR_WIDTH`, `FONT`).
- Small builders returning inline-CSS strings or styled nodes: `card()` (background+border+radius), `navItem(boolean active, boolean hover)`, `badge(boolean ok)`, `statusColor(Status)`, `logTextAreaStyle()`.
- Thin wrappers around `UiUtils` where convenient, so panels depend on `OpbStyle`/`UiUtils` rather than raw CSS.

## 8. Files Touched

**Modify** (all under `SwissKitJ-Plugin-OfflinePython/src/main/java/plugin/swisskit/offlinepython/`):
- `ui/CommandShell.java`
- `ui/LogConsole.java`
- `ui/PythonInstallGuide.java`
- `ui/panel/CommandPanel.java`
- `ui/panel/InitPanel.java`
- `ui/panel/DepsPanel.java`
- `ui/panel/BuildPanel.java`
- `ui/panel/VerifyPanel.java`
- `ui/panel/DoctorPanel.java`

**Add**:
- `ui/OpbStyle.java`

**Unchanged**: `OfflinePythonPlugin.java` (entry point; i18n already registered, `IconStyle.BLUE` kept), `domain/`, `infra/`, `command/`, `task/`, all tests, `pom.xml`, i18n bundles.

## 9. Behavior Preserved (regression contract)

- Plugin lifecycle: `createView()` registration, `hasRunningTasks()`, `onBackground()` / `onForeground()` / `onUnload()` (build cancel).
- Nav: 5 items (init/deps/build/verify/doctor), same switch semantics; build panel cached.
- Log console: append-with-timestamp, auto-scroll, `log(String)` API.
- Each panel's command wiring, file choosers, toasts, and error logging.
- Python detection badge + install-guide fallback when Python missing.
- i18n keys and copy.

## 10. Verification

1. **Compile**: `mvn -pl SwissKitJ-Plugin-OfflinePython compile` via IntelliJ Maven panel (no `mvn` on this machine's PATH).
2. **Unit regression**: existing 8 test classes still pass (pure logic; UI change must not affect them).
3. **Visual (DevLauncher)**: first apply the prior fix (Reload All Maven Projects + regenerate the `DevLauncher` run config so `SwissKitJ-Api:3.1.0` is on the classpath), then launch and confirm:
   - Left nav is glass-styled (icon+label, selected/hover states, 220px).
   - Python badge capsule renders (success/danger).
   - Each of the 5 panels shows glass buttons/fields/titles; build progress bar is the host 6px accent bar with success/danger state; verify shows status-colored badges; doctor shows ✓/✕ colors.
   - Log console has the dark glass inner background + thin scrollbars.
   - Python-missing path shows the glass install-guide card.
4. **Host parity**: visually compare against the host glass shell (accent `#5b8cf7`, translucent surfaces, SF Pro font) — the plugin should read as part of the host, not an inset block.

## 11. Risks & Mitigations

- **`.glass-*` classes not rendering** (stylesheet not on hosting Scene) → mitigated by §5 step 1 (attach stylesheet to root) + step 2 (`UiUtils` self-contained inline).
- **Looked-up color variables not resolving** (plugin root not `.root`) → mitigated by using concrete token values in `OpbStyle`, not variable names.
- **DevLauncher can't start** (`NoClassDefFoundError: SwissKitJPlugin`) → already diagnosed; resolved by Maven reload + run-config regeneration before visual verification.
- **Token drift if host rethemes** → accepted; `OpbStyle` centralizes values so a future sync is one-file. `UiUtils`-backed controls auto-track the host.

## 12. Out of Scope (deferred)

- Migrating sibling plugins (HappyLearning/Qcc/KeepAwake) to the host theme.
- Switching layout to tabs or `StepWizard`.
- New features or i18n additions.
- Extracting a shared `OpbStyle`-equivalent into the host API for cross-plugin reuse (could be proposed after this lands).
