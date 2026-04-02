# ChibiForge UI Specification v2

## 1. Overview

The ChibiForge GUI is a standalone **JavaFX** desktop application for editing `chibiforge.xcfg` configuration files. It provides a schema-driven editor where the UI is generated dynamically from component `schema.xml` definitions — no hardcoded component logic.

**Technology stack:**

- **JavaFX** (OpenJDK + OpenJFX) for the GUI.
- **FMPP** and **freemarker-codegen** for code generation (in-process, same JVM).
- Packaged via **jpackage** as a platform-native installer (`.msi`, `.dmg`, `.deb`) with bundled JRE — no Java installation required for end users.
- Registered as the handler for `.xcfg` files — double-clicking opens the editor.

**Key principles:**

- Schema-driven: all forms are generated from `component/schema.xml`.
- Live data model: `@ref:` and `@cond:` expressions are evaluated in real-time as the user edits.
- Light and dark theme support via CSS stylesheets.
- The GUI calls the generator engine directly (in-process) — no subprocess spawning.

---

## 2. Application Window Structure

The main window has the following structure from top to bottom:

### 2.1 Title Bar

Displays:

- Application name: "ChibiForge".
- Path to the currently open `chibiforge.xcfg` file.

### 2.2 Menu Bar

Menus:

- **File**: New, Open, Save, Save As, Recent Files, Exit.
- **Edit**: Undo, Redo, Preferences.
- **Components**: Add Component, Remove Component.
- **Generate**: Generate (run code generation), Clean (delete generated files).
- **Help**: About, Documentation.

### 2.3 Toolbar

Action buttons below the menu bar, left to right:

- **Save**: saves `chibiforge.xcfg` to disk.
- Separator.
- **Generate** (accented/primary style): runs the generator engine for the currently selected target. Prompts to save if modified.
- **Clean**: deletes generated files.
- Flexible spacer.
- **Inspector** (toggle button, accented when active): shows/hides the right-side Inspector panel.

### 2.4 Target Selector

Always visible in the title bar area or toolbar. Contains:

- A **dropdown/combo box** showing the active target. Options include all targets defined in `chibiforge.xcfg` (always includes "default").
- A **Manage** button (gear icon or "Manage..." entry at the bottom of the dropdown) that opens a **Manage Targets** dialog (see §7).

### 2.5 Main Content Area

Three-panel layout:

- **Left panel**: Component Palette (fixed width, resizable).
- **Center panel**: Configuration view (fills remaining space).
- **Right panel**: Inspector Panel (toggleable, fixed width, resizable).

### 2.6 Status Bar

Bottom bar showing:

- Left: component count, property count, active target name.
- Right: feature warnings count, validation error count, save state indicator (saved/modified).

---

## 3. Component Palette (Left Panel)

The left panel is a permanent palette showing all available components from the ComponentRegistry (filesystem + plugin containers).

### 3.1 Structure

- **Header**: "Available components".
- **Filter field**: text input at top for filtering components by name.
- **Category tree**: components grouped by category (derived from `<categories>/<category id>` in each component's `schema.xml`, split on `/` for hierarchy).
- **Component entries**: each showing:
  - Icon loaded from `rsc/icon.png` in the component container (fallback to a default icon if absent).
  - Component name (from `@name` in `schema.xml`).
  - Status indicator: ✓ checkmark for components already in the configuration (shown greyed/dimmed). Available components shown at full contrast.

### 3.2 Behavior

- **Click** a component in the palette: selects it (for use with "Add selected" in the center panel).
- **Double-click** an available component: adds it to the configuration directly.
- **Double-click** an already-added component: navigates to its configuration in the center panel.

### 3.3 Hidden Components

Components with `hidden="true"` in `schema.xml` are hidden by default. A "Show hidden" checkbox at the bottom of the palette reveals them.

### 3.4 Single Instance Enforcement

Each component ID may appear at most once in a configuration. Already-added components are shown greyed with ✓ and cannot be added again. If a duplicate is detected in a manually edited `chibiforge.xcfg`, the GUI shows a validation error.


### 3.5 Component Source Resolution

When opening a configuration file (`.xcfg`), the UI resolves component containers automatically without requiring command-line arguments.

A **component root** is a filesystem path containing component containers. A component container may be either:

- a directory (filesystem-based component), or
- a JAR file.

#### Resolution Sources

Component roots are discovered from the following sources, which are combined:

1. **Project-local directory**

   If a `components/` directory exists under the configuration root, it is used as a component root:

   ```text
   <configurationRoot>/components/
   ```

2. **Sidecar configuration file**

   If present, a file named `chibiforge_sources.json` located next to the `.xcfg` file defines additional component roots.

   Example:

   ```json
   {
     "componentRoots": [
       "./components",
       "../shared/components"
     ]
   }
   ```

   Relative paths are resolved against the configuration root.

3. **Environment variable**

   The environment variable `CHIBIFORGE_COMPONENTS` may define one or more global component roots.

   Multiple paths are separated using the platform-specific path separator.

#### Merging and Precedence

All discovered component roots are combined into a single registry.

If duplicate component IDs are found, the following precedence applies:

1. Component roots defined in the sidecar file
2. Project-local `components/` directory
3. Component roots from `CHIBIFORGE_COMPONENTS`

#### Failure Handling

If no valid component roots are found or required components are missing:

- The configuration file MUST still be opened.
- The UI MUST indicate unresolved components.
- The user SHOULD be prompted to add or correct component roots.
- The user MAY choose to persist these settings globally or for the current project.

---

## 4. Center Panel

The center panel has two levels of navigation, managed via a breadcrumb bar at the top.

### 4.1 Breadcrumb Bar

Always visible at the top of the center panel. Shows the current navigation path:

- **Top level**: `Components`
- **Component level**: `Components > HAL STM32F4xx`
- **Section/property level**: `Components > HAL STM32F4xx > Initialization Settings`
- **List drill-down**: `Components > HAL STM32F4xx > Pin configuration > [3]`

Each segment is clickable to navigate back to that level. Pressing **Escape** navigates up one level.

### 4.2 Components View (Top Level)

When the breadcrumb shows just "Components", the center panel displays a grid of cards representing all components currently in the configuration.

**Mini-toolbar** (visible only at this level):

- **Add selected**: adds the component currently selected in the palette to the configuration.
- **Remove**: removes the currently selected component card from the configuration (with confirmation).
- Component count label (e.g., "4 components configured").

**Component cards**:

- Displayed in a responsive grid layout.
- Each card shows:
  - Large icon (from `rsc/icon.png`).
  - Component name.
  - Version.
- Single-click a card to select it (shows its info in the Inspector Help tab).
- **Double-click** a card to enter its configuration form.
- Selected card has a highlighted border.

**Hint text** at bottom: "Double-click to configure · Select + Remove to delete".

### 4.3 Component Configuration Form

When the user double-clicks a component card, the center panel shows the configuration form for that component. The breadcrumb updates to `Components > <ComponentName>`.

The form is a scrollable vertical layout rendered from the component's `schema.xml`.

#### 4.3.1 Sections

Each `<section>` in `schema.xml` renders as a **collapsible block**:

- A horizontal line with the section title (`@name`) as a header.
- The initial expanded/collapsed state is determined by the `@expanded` attribute.
- The `<description>` text is shown as help text below the title when expanded.
- Child elements (`<property>`, `<layout>`, `<image>`) are rendered in order within the section body.
- Nested sections (within list items) are visually indented one level deeper.

#### 4.3.2 Property Widgets

Each `<property>` renders a row with:

- **Left side**: property label (`@name`) and brief description (`@brief` in smaller/muted text).
- **Right side**: the input widget appropriate to the type, plus the multi-target indicator (see §4.3.8).

Widget mapping by type:

- **`bool`**: `CheckBox` or toggle switch.
- **`string`**: `TextField` (single-line). Validated against `string_regex` on focus loss if present.
- **`text`**: `TextArea` (multi-line, monospaced font). Enforces `text_maxsize` if present.
- **`int`**: `TextField` with numeric validation. Validated against `int_min`/`int_max` on focus loss.
- **`enum`**: `ComboBox` populated from `enum_of` (CSV values or resolved `@ref:` expression).
- **`list`**: renders as a table (see §4.3.6).

#### 4.3.3 Visibility and Editability

Each property has `visible` and `editable` attributes that accept three forms:

- **`"true"`**: static — always visible / always editable.
- **`"false"`**: static — always hidden / always read-only.
- **`"@cond:<xpath>"`**: dynamic — evaluated against the live data model.

Examples:

```xml
<property name="dma_channels" type="int"
          visible="true"
          editable="@cond:doc/dma_settings/use_dma = 'true'"
          .../>

<property name="pll_multiplier" type="int"
          visible="@cond:doc/initialization_settings/system_clock_source = 'PLL'"
          editable="true"
          .../>
```

When a property's `@cond:` expression evaluates to false:

- For `visible`: the property row is hidden. The value is retained in the configuration — hiding is GUI-only.
- For `editable`: the widget is rendered as read-only (greyed/disabled).

`@cond:` expressions are re-evaluated whenever the DOM updates (see §9).

#### 4.3.4 Layouts

A `<layout>` element arranges its children in a multi-column grid:

- `@columns` (`1`–`4`): number of columns.
- `@align` (`left`, `center`, `right`): content alignment within cells.
- Children: `<property>`, `<image>`, or `<empty>` (blank cell for spacing).

JavaFX implementation: use `GridPane` with column constraints based on `@columns`.

#### 4.3.5 Images

An `<image>` element renders as:

- The image loaded from `@file` (path relative to the component container).
- Positioned according to `@align` (`left`, `center`, `right`).
- The `<text>` child is shown as a caption below the image.
- Images should have alpha transparency for light/dark theme compatibility.

#### 4.3.6 List Properties (Table + Drill-Down)

A `<property type="list">` renders as:

**Table view** (default):

- A `TableView` showing list items as rows.
- Columns are determined by the `list_columns` attribute: a CSV of property names with optional pixel widths (e.g., `"name:150,mode:100,speed"`).
- Column headers use the referenced property's `@name`.
- Row controls below the table: **Add**, **Remove**, **Duplicate**, **Move Up** (▲), **Move Down** (▼).
- The item count is shown next to the section title (e.g., "Pin configuration — 5 items").

**Drill-down** (item editing):

- Double-clicking a row navigates into that item's configuration.
- The center panel replaces the table with the item's form, driven by the nested `<sections>` inside the `<property type="list">`.
- The breadcrumb updates to show the drill-down path (e.g., `Components > HAL STM32F4xx > Pin configuration > [3]`).
- Clicking a breadcrumb segment navigates back to that level.

**Recursive**: if a list item contains another list, the same pattern applies recursively.

#### 4.3.7 Validation

Validation is triggered on **focus loss** for editable fields:

1. Validate the field value against its constraints (`string_regex`, `int_min`/`int_max`, `text_maxsize`, `enum_of` membership).
2. **If valid**: update the DOM, re-evaluate all `@ref:` and `@cond:` expressions for the current view (see §9).
3. **If invalid**: show an error indicator (red dot overlay, red border, or similar). Do **not** update the DOM — the model retains the last valid value. The field shows the invalid text so the user can correct it.

Additional validation:

- `@ref:` resolution failures (resource not found, XPath returns no result).
- `@cond:` evaluation errors (invalid XPath syntax).
- Feature dependency warnings (shown in Inspector and status bar).

Error indicators:

- Red border or highlight on the field.
- Error message text below the field (or as a tooltip).
- Aggregated in the status bar (error count).
- Shown in the Inspector Help tab for the focused field.

#### 4.3.8 Multi-Target Indicator and Toggle

Each property widget displays a **multi-target indicator** — a small icon adjacent to the input widget:

**Visual states:**

- **Single-target** (default): indicator shows as an outline/empty icon (e.g., ◇). The property has one value shared across all targets.
- **Multi-target**: indicator shows as a filled/colored icon (e.g., ◆ in the theme's accent color). The property has per-target values.

**Promoting to multi-target:**

- Click the indicator icon, or right-click the property row and select "Enable per-target values".
- The current value becomes the `default` attribute value in `chibiforge.xcfg`.
- The user can then switch targets (via the Target Selector) and set different values per target.

**Reverting to single-target:**

- Click the indicator icon, or right-click the property row and select "Use single value (default)".
- A **confirmation dialog** is shown: "This will discard all per-target overrides and use only the default value. Continue?"
- On confirm: the `default` attribute value becomes the plain text content, all `<targetValue>` elements are removed.

**Display behavior when multi-target:**

- The widget shows the value for the **currently selected target** (per the Target Selector).
- If the current target has no explicit `<targetValue>`, the default value is shown with a visual distinction (dimmed text, italic, or a "default" badge) to indicate it's a fallback.
- Hovering over the indicator shows a tooltip listing all target values (e.g., "default: 300, debug: 330, release: (default)").

---

## 5. Inspector Panel (Right Panel, Toggleable)

The Inspector panel is a side panel on the right, toggled via the Inspector toolbar button. It has four tabs.

### 5.1 Outline Tab

Shows a navigable tree of the current component's schema structure:

- **When at the Components top level**: shows a list of all configured components. Clicking one navigates to it.
- **When inside a component**: shows a tree of sections and properties from the component's `schema.xml`.

Tree structure example:

```
▼ Initialization Settings
    Do not init
    VDD
    System clock source
    ▼ Clock frequencies (layout)
        HSE frequency
        LSE frequency
    Device ID
▶ DMA Settings
▼ Pin Configuration
    [list: 4 items]
```

**Behavior**: clicking a section or property in the Outline tree scrolls the center panel to that element and focuses it. This is useful for navigating large configurations.

### 5.2 Help Tab

Shows contextual documentation for the currently focused element:

**When at the Components top level (no component selected):**

- Configuration overview: component count, target list (active highlighted), feature warnings.
- List of all configured components with versions.

**When a component card is selected (top level):**

- Component name, ID, version.
- Component `<description>`.
- Categories.
- Required features (with resolved/unresolved status indicators).
- Provided features.
- List of sections in the schema.

**When a property is focused (inside a component):**

- Property name and type badge (e.g., `int`, `enum`, `bool`).
- Brief description (`@brief`).
- Constraints table: default value, min, max, regex, choices (as applicable).
- Required/editable/visible status (resolved values, including `@cond:` results).
- Multi-target values: all targets listed, active target highlighted, fallback values shown in italics.
- Data model path (e.g., `doc.initialization_settings.vdd`).
- Validation status (errors/warnings or "no validation errors").

**When a section is focused:**

- Section name.
- Section `<description>`.

### 5.3 Files Tab

Shows the configuration root's filesystem as a tree, color-coded by how ChibiForge manages each file:

**Color coding:**

- **Red ("clean")**: files inside `generated/`. These are deleted by the Clean action.
- **Amber ("managed")**: root-scope files produced by `source_root_wa/`, `cfg_root_wa/`, or `cfg_root_wo/`/`source_root_wo/`. These are overwritten (wa) or initially created (wo) by Generate.
- **Grey ("user")**: files not managed by ChibiForge. Untouched by Generate or Clean.

**Badges:**

- `wa` — write-always (overwritten every generation).
- `wo` — write-once (created only if absent; shown in italics to indicate "won't overwrite").
- `clean` — deleted by Clean action.

**Footer**: file counts per category (e.g., "6 files cleaned · 5 files managed · 3 user files").

The Files tab is populated by scanning the configuration root and cross-referencing with the component containers' `source*` and `cfg*` directory conventions.

### 5.4 Log Tab

Shows generation and clean output:

- Timestamped log entries.
- Files written, files skipped (write-once, already exists), warnings, errors.
- Scrollable, clearable.
- Updated in real-time during generation.

---

## 6. Component Palette Interaction

Adding and removing components uses the palette (left panel) together with the Components view (center panel, top level).

### 6.1 Adding a Component

Two methods:

1. **Select in palette + click "Add selected"** in the center panel mini-toolbar.
2. **Double-click** an available (non-greyed) component in the palette.

On add:

- A new `<component id="X">` is created in `chibiforge.xcfg` with default values from the schema.
- The component card appears in the Components view.
- The palette entry becomes greyed with ✓.
- The DOM is updated, feature dependencies are re-evaluated.

### 6.2 Removing a Component

Two methods:

1. **Select a card + click "Remove"** in the center panel mini-toolbar.
2. **Right-click a card** and select "Remove".

On remove:

- Confirmation dialog: "Remove <ComponentName> from configuration? This will delete all its settings."
- The `<component id="X">` element is removed from `chibiforge.xcfg`.
- The card disappears from the Components view.
- The palette entry returns to full contrast (available).
- The DOM is updated, feature dependencies are re-evaluated.

---

## 7. Manage Targets Dialog

Opened via the gear icon or "Manage..." entry in the Target Selector dropdown.

### 7.1 Layout

- **Target list**: shows all defined targets with the "default" target always first.
- **Add**: creates a new target (prompts for name).
- **Rename**: renames the selected target (disabled for "default").
- **Delete**: deletes the selected target (disabled for "default"). Confirmation dialog: "This will discard all per-target overrides for this target. Continue?"
- **Close** button.

### 7.2 Behavior

- On delete: all `<targetValue target="X">` elements referencing the deleted target are removed from `chibiforge.xcfg`. Properties that were multi-target only for this target may revert to single-target if no other overrides remain.
- On rename: all `<targetValue target="X">` and `<target id="X">` elements are updated.
- Changes are reflected immediately in the Target Selector dropdown and all multi-target indicators.

---

## 8. Generate and Clean Actions

### 8.1 Generate

Triggered via toolbar button, menu, or **Ctrl+G** / **F5**.

Flow:

1. If configuration is modified: prompt "Save changes before generating?" → Save / Don't Save / Cancel.
2. If saved (or not modified): run the generator for the currently selected target.
3. Show a progress indicator (progress bar or spinner).
4. Auto-switch Inspector to the Log tab. Output results in real-time.
5. On completion: show success/failure in the status bar.
6. Refresh the Files tab to reflect any new/changed files.

Errors:

- Feature dependency warnings: shown in the Log tab and status bar.
- Template processing errors: shown in the Log tab with file/line information.
- File I/O errors: shown in the Log tab.

### 8.2 Clean

Triggered via toolbar button or menu.

Flow:

1. Confirm: "This will delete all files in generated/. Continue?"
2. Delete the `generated/` directory and all contents.
3. Root-scope managed files are **not** deleted by Clean — they are only overwritten by Generate. Clean only removes `generated/`.
4. Log the action to the Log tab.
5. Refresh the Files tab.
6. Update the status bar.

---

## 9. Live Data Model and DOM Updates

### 9.1 Model Structure

The GUI maintains a live DOM identical in structure to what the generator passes to FMPP. It contains:

- **`doc`**: the current component's resolved configuration (scoped to the selected component, multi-target values resolved for the active target).
- **`components`**: all components' resolved configurations, keyed by normalized component ID.
- **`configuration`**: metadata (root path, generated root path, active target).
- **Resource variables**: one top-level variable per `<resource>` declared in the current component's `schema.xml`, using the resource `id` as the variable name.

### 9.2 DOM Update Events

The DOM is updated on specific GUI events — not continuously during typing:

| Widget type | Update trigger |
|---|---|
| `bool` (checkbox/toggle) | On state change (immediate) |
| `enum` (combo box) | On selection change (immediate) |
| `string` (text field) | On focus loss, **only if valid** |
| `int` (text field) | On focus loss, **only if valid** |
| `text` (text area) | On focus loss, **only if valid** |
| `list` (table) | On add/remove/reorder (immediate) |

**Critical rule**: the DOM is only updated with valid values. If validation fails on focus loss, the DOM retains the last valid value and the field shows an error indicator. This ensures the DOM is always in a consistent state for XPath evaluation.

### 9.3 Expression Re-evaluation

After every DOM update, the following expressions are re-evaluated — but **only for fields in the current view** (the current breadcrumb level):

- **`@ref:`** expressions in property attributes: re-resolve defaults, constraints, and choices.
- **`@cond:`** expressions in `visible` and `editable` attributes: re-evaluate to show/hide fields and toggle editability.

Fields not currently displayed (in collapsed sections, other components, or deeper/shallower breadcrumb levels) are re-evaluated lazily when the user navigates to them.

### 9.4 XPath Evaluation

`@ref:` references and `@cond:` expressions use XPath. The GUI uses `javax.xml.xpath` (JDK built-in) to evaluate expressions against the live DOM.

The XPath context for `@cond:` has `doc` as the root. The expression `doc/initialization_settings/use_dma = 'true'` navigates from `doc` into the component's configuration tree.

### 9.5 Model Lifecycle

- **On open**: load `chibiforge.xcfg`, resolve all components, load all resources, build the DOM.
- **On edit**: update the DOM (per §9.2), re-evaluate expressions for current view (per §9.3).
- **On target switch**: re-resolve all multi-target properties for the new target, rebuild `doc` and `components`, re-evaluate expressions for current view.
- **On component add/remove**: rebuild `components`, re-evaluate feature dependencies.
- **On navigate**: evaluate expressions for the newly visible view (lazy evaluation).

---

## 10. Theming

### 10.1 Light and Dark Themes

The application supports light and dark themes via JavaFX CSS stylesheets:

- A default light theme.
- A dark theme.
- Switchable via Preferences (Edit > Preferences) or following the OS setting.

### 10.2 Design Guidelines

- Clean, flat surfaces. Minimal borders. Generous whitespace.
- Component icons should use alpha transparency (PNG) to render on both light and dark backgrounds.
- Sections render as document-style blocks with horizontal line + title, not heavy tabbed panels.
- The overall feel should be lightweight and professional — a tool, not a colorful IDE.

---

## 11. File Association and Startup

### 11.1 File Association

The installer registers `.xcfg` as a file type associated with the ChibiForge application. Double-clicking a `.xcfg` file opens it in ChibiForge.

### 11.2 Startup Behavior

When opening a `.xcfg` file (including via file association), the application MUST:

1. Determine the configuration root from the file location.
2. Resolve component roots using the Component Source Resolution mechanism (§3.5).
3. Load component containers from all resolved roots.
4. Build the component registry.
5. Load the configuration into the live data model.

Opening a configuration MUST NOT require command-line arguments.

If the application is started without a file argument, it shows a welcome screen with:

- Recent files list.
- "Open" button.
- "New Configuration" button.

### 11.3 Single Instance

If the application is already running and the user double-clicks another `.xcfg` file, it should open in a new tab or window within the existing instance (or a new instance — implementation choice).


### 11.4 Component Source Preferences

The UI MAY provide a Preferences page for managing global component roots.

Global preferences complement, but do not override, project-associated sources discovered via:

- `<configurationRoot>/components/`
- `chibiforge_sources.json`
- `CHIBIFORGE_COMPONENTS`

---

## 12. Save and Dirty State

### 12.1 Dirty Tracking

Any edit to a property value, adding/removing components, or modifying targets marks the configuration as "modified" (dirty).

### 12.2 Save Behavior

- **Save** (Ctrl+S or toolbar button): writes `chibiforge.xcfg` to disk.
- **Save As**: writes to a new file path.
- The status bar shows "Modified" or "Saved" state.
- On close with unsaved changes: prompt "Save changes before closing?"

### 12.3 File Format

`chibiforge.xcfg` is written as formatted XML (indented, human-readable). The GUI preserves the structure defined in the main ChibiForge specification (§7).

---

## 13. Keyboard Shortcuts

| Shortcut | Action |
|---|---|
| Ctrl+S | Save |
| Ctrl+Z | Undo |
| Ctrl+Y / Ctrl+Shift+Z | Redo |
| Ctrl+O | Open file |
| Ctrl+N | New configuration |
| Ctrl+G | Generate |
| F5 | Generate (alternate) |
| Escape | Navigate up in breadcrumb |
| Delete | Remove selected component or list row (with confirmation) |

---

## 14. JavaFX Component Mapping

| Concept | JavaFX Component |
|---|---|
| Main window | `Stage` + `BorderPane` |
| Menu bar | `MenuBar` + `Menu` + `MenuItem` |
| Toolbar | `ToolBar` + `Button` |
| Target selector | `ComboBox<String>` + gear `Button` |
| Component palette | `VBox` with `TextField` (filter) + `TreeView` |
| Components view | `FlowPane` or `GridPane` with card nodes |
| Components mini-toolbar | `HBox` with `Button` (Add, Remove) |
| Breadcrumb | `HBox` with clickable `Label` or `Hyperlink` |
| Configuration form | `ScrollPane` + `VBox` |
| Section | `TitledPane` or custom collapsible `VBox` |
| Bool property | `CheckBox` or custom toggle |
| String property | `TextField` |
| Text property | `TextArea` |
| Int property | `TextField` + `TextFormatter` (numeric) |
| Enum property | `ComboBox<String>` |
| Multi-target indicator | `Label` or `Button` (◇/◆ icon) |
| List table | `TableView` |
| List row controls | `HBox` with `Button` (Add, Remove, Duplicate, ▲, ▼) |
| Layout grid | `GridPane` |
| Image | `ImageView` + `Label` (caption) |
| Inspector panel | `TabPane` with `Tab` (Outline, Help, Files, Log) |
| Outline tree | `TreeView<SchemaNode>` |
| Files tree | `TreeView<FileNode>` |
| Log view | `TextArea` (read-only, monospaced) or `ListView` |
| Status bar | `HBox` with `Label` elements |
| Manage Targets dialog | `Dialog` + `ListView` + `Button` (Add, Rename, Delete) |

---

## 15. Implementation Notes

### 15.1 Project Structure

Recommended Java package structure:

```
org.chibios.chibiforge/
  app/                     # Application entry point, main window
  model/                   # Data model, DOM, configuration, component definitions
  engine/                  # Generator engine, FMPP integration
  gui/
    palette/               # Component palette (left panel)
    components/            # Components view (center, top level)
    form/                  # Configuration form rendering (center, component level)
    inspector/             # Inspector panel (outline, help, files, log tabs)
    targets/               # Manage targets dialog
    widgets/               # Property widget factories
  schema/                  # Schema parsing, XPath evaluation, @ref:/@cond: resolution
  io/                      # File I/O, xcfg reading/writing
  container/               # Component container loading (filesystem, JAR)
  sources/                 # Component source resolution (project-local, sidecar, environment)
```

### 15.2 Form Rendering Strategy

The configuration form is built dynamically by walking the `schema.xml` DOM:

1. For each `<section>`: create a collapsible container.
2. For each `<property>`: look up the type, create the appropriate widget via the widget factory, add the multi-target indicator, bind to the DOM.
3. For each `<layout>`: create a `GridPane`, fill cells with child widgets.
4. For each `<image>`: create an `ImageView` with caption.
5. For `<property type="list">`: create a `TableView` with drill-down navigation.

A **widget factory** pattern is recommended: a factory that takes a `<property>` element and returns the configured JavaFX control with its multi-target indicator.

### 15.3 Data Binding

JavaFX properties and bindings connect the DOM to the GUI:

- Each property value in the DOM is backed by a JavaFX `Property` (e.g., `StringProperty`, `IntegerProperty`, `BooleanProperty`).
- Widget values are bidirectionally bound to the model properties.
- When the DOM changes (e.g., target switch), all bound widgets update automatically.
- `@cond:` expressions are implemented as computed bindings that re-evaluate XPath when the DOM changes.
- Multi-target state (single vs multi, per-target values) is part of the model and drives the indicator icon state.

### 15.4 Dependencies

Required libraries:

- **OpenJFX**: GUI toolkit.
- **FMPP**: template processing (in-process).
- **freemarker-codegen**: FreeMarker with code-first mode.
- **javax.xml.xpath** (JDK built-in): XPath evaluation for `@ref:` and `@cond:`.
- **javax.xml.parsers** (JDK built-in): XML parsing for `schema.xml`, `chibiforge.xcfg`, resources.

Build tool: **Gradle** (recommended) or Maven.

---

## 16 Preset Management

### 16.1 General

A preset is a predefined configuration fragment for a single component.

Presets:

* are scoped to a specific component ID
* are applied only through explicit user actions
* do not participate in dependency resolution
* do not create persistent links in the configuration

Applying a preset replaces the component configuration in memory.

---

### 16.2 Preset Sources

#### 16.2.1 Bundled Presets

A component container MAY include presets under:

`component/presets/`

Bundled presets:

* are read-only
* are associated with the owning component
* MAY be declared in `schema.xml` or discovered by convention

---

#### 16.2.2 External Presets

The UI SHALL allow loading presets from arbitrary filesystem locations.

External presets:

* are selected via file chooser
* MUST be validated before application

---

### 16.3 Load Preset

#### 16.3.1 Invocation

The component editor SHALL provide a *Load Preset* action.

The UI SHALL allow:

* selection of bundled presets
* selection of an external preset file

---

#### 16.3.2 Validation

Before applying a preset:

* the preset MUST declare a `componentId`
* the `componentId` MUST match the current component
* invalid presets MUST be rejected

---

#### 16.3.3 Apply Semantics

Applying a preset SHALL:

* replace the current component configuration in memory
* NOT modify the preset file
* NOT create any persistent reference to the preset

If the component contains data, the UI SHOULD request confirmation.

---

### 16.4 Target Semantics

Presets are target-local configuration snapshots.

Presets:

* SHALL NOT define or modify project targets
* SHALL NOT contain target declarations
* SHALL be applied to the currently selected target context

---

#### 16.4.1 Single-target Properties

The value SHALL be replaced.

---

#### 16.4.2 Multi-target Properties

For each property:

* if an explicit value exists for the active target, it SHALL be replaced
* otherwise, the `default` value SHALL be replaced

---

#### 16.4.3 Optional Target Override Creation

The UI MAY provide an option to create explicit values for inherited multi-target properties.

When enabled:

* applies only if active target is not `default`
* if no explicit value exists for the active target:

  * a target-specific value SHALL be created
  * the preset value SHALL be written to that target
* existing target-specific values SHALL always be replaced

When disabled:

* inherited values SHALL be written to `default`

---

### 16.5 Save Preset

#### 16.5.1 Invocation

The component editor SHALL provide a *Save Preset As...* action.

---

#### 16.5.2 Behavior

Saving a preset SHALL:

* serialize the current component configuration
* write the preset to a user-selected file
* NOT modify bundled presets or component containers

---

#### 16.5.3 Export Semantics

The saved preset SHALL reflect:

* the currently selected target
* the effective values visible in the editor

This includes:

* explicit target values
* inherited values resolved from `default`

The saved preset SHALL be a fully materialized configuration snapshot.

---

### 16.6 Post-Load Behavior

After applying a preset, the UI SHALL:

* refresh the component editor
* re-evaluate all `@ref:` and `@cond:` expressions
* mark the configuration as modified

---

### 16.7 Error Handling

The UI SHALL report:

* invalid XML
* mismatched `componentId`
* schema validation errors

Preset application SHALL be aborted on error.

---

### 16.8 Notes

* Presets are intended for initialization, not inheritance
* After loading, the configuration is fully editable
* Presets may be shared across projects and users

---

