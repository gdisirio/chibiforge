# ChibiForge Specification

## 1. Overview

**ChibiForge** is a component‑based configuration and code generation system for embedded projects (C, C++, Rust, or mixed), developed as part of the **ChibiOS** project.

Key properties:

- Works inside a **normal project** (Makefile, CMake, Cargo, etc.).
- Uses **XML** for component definitions and configuration.
- Uses **freemarker‑codegen** (FreeMarker with classic and code‑first modes) for templates.
- Uses **FMPP** as the template processing layer (not FreeMarker directly).
- Has an **IDE‑agnostic generator engine** (CLI + library).
- Eclipse plugin JARs serve as a **portable component container format**, usable by any tool — not just Eclipse.
- Generated code lives entirely inside the configuration root.
- Core principle: **full functionality without IDE or ecosystem lock‑in**.

---

## 2. Terminology

- **Project root**: top‑level directory of a user project.
- **Configuration root**: the directory containing a `chibiforge.xcfg` file. A project may contain multiple configuration roots.
- **ChibiForge configuration file**: `chibiforge.xcfg`, typically placed at the project root.
- **Generated root**: `<configurationRoot>/generated/` (fixed, non-configurable).
- **Component**: a configuration + generation unit (e.g., RT, HAL, board, platform, environment).
- **ComponentDefinition**: XML file describing a component (`component/schema.xml`, root element `<component>`).
- **Component container**: a directory or plugin JAR containing a full component under `component/`.
- **Generator engine**: the ChibiForge core that reads configuration and definitions and writes generated files.
- **Scope**: where a static output is logically written (`generated` or configuration root).
- **Write policy**: overwrite behavior for static outputs (`always` vs `once`).
- **Target**: a named build/configuration variant within a single `chibiforge.xcfg` (e.g., "default", "debug", "release"). See §7.2.
- **Normalized ID**: an identifier transformed by the normalization rule (see §2.1).

### 2.1 Identifier Normalization

All identifiers (component IDs, resource IDs, section names, property names) are normalized when used in filesystem paths and data model paths. The normalization rule is:

1. Convert to lowercase.
2. Replace any character that is not a letter, digit, or underscore with `_`.
3. Collapse multiple consecutive underscores into a single `_`.

Examples:

- `org.chibios.hal.stm32f4xx` → `org_chibios_hal_stm32f4xx`
- `HAL-Core_v2` → `hal_core_v2`
- `my..strange...id` → `my_strange_id`

This rule applies to: generated directory names, data model variable names, and any context where an ID is used as a filesystem or data model path segment.

---

## 3. Layout & Conventions

### 3.1 Minimal requirements

A configuration requires only:

- A ChibiForge configuration file:

  ```text
  <configurationRoot>/chibiforge.xcfg
  ```

All other directories/files (e.g. `src/`, `include/`, `Makefile`, `Cargo.toml`, `CMakeLists.txt`) are defined by the project's own build system and IDE.

### 3.2 Conventional layout

```text
<projectRoot>/
  chibiforge.xcfg              # ChibiForge configuration file
  generated/                   # fixed root for generated files
  src/
  include/
  ldscripts/
  Makefile / CMakeLists.txt / Cargo.toml
```

### 3.3 Multiple configurations

A single project may contain multiple configurations:

```text
<projectRoot>/
  chibiforge.xcfg              # main configuration
  configs/
    variant_a/
      chibiforge.xcfg          # alternate configuration
      generated/
    variant_b/
      chibiforge.xcfg
      generated/
  src/
```

Each `chibiforge.xcfg` defines an independent configuration root. Generated and root-relative outputs are always relative to the configuration root containing the respective `chibiforge.xcfg`.

**Constraint:** ChibiForge must **never write outside** the configuration root.

---

## 4. Component Containers

A **component container** is a directory (or directory within a JAR) that encapsulates everything needed for one component.

### 4.1 Container layout

Filesystem or inside a plugin JAR:

```text
<containerRoot>/
  component/
    schema.xml                 # ComponentDefinition (root <component>)

    cfg/                       # Templates -> generated/
      mcuconf.h.ftlc
      board.h.ftlc
      ...

    cfg_root_wa/               # Templates -> config root, always written
      ldscripts/linker.ld.ftlc

    cfg_root_wo/               # Templates -> config root, write-once
      Makefile.ftlc

    source/                    # Static code -> generated/<normalizedComponentId>/
      hal_ll.c
      hal_dma.c

    source_root_wa/            # Static root-scope, always written
      ldscripts/memory.ld

    source_root_wo/            # Static root-scope, write-once
      src/main.c
      Makefile

    resources/                 # Static XML/JSON constants and catalogs
      stm32f4_limits.xml
      phy_catalog.json
      ...

    other/                     # Optional extra data, docs, etc. (ignored by default)

  rsc/                         # Optional UI assets (icons) for tool integration
    icon.png

  META-INF/ (plugins only)
  plugin.xml  (plugins only)
  build.properties (plugins only)
```

- Everything the generator needs is under `component/`:
  - `schema.xml` (definition),
  - `cfg/`, `cfg_root_wa/`, `cfg_root_wo/` (templates),
  - `source*` (static payload),
  - `resources/` (constants/catalogs used in schema and templates).
- `rsc/` is optional UI assets; `plugin.xml` is only for plugin-based containers.
- Icon convention: `rsc/icon.png` if present.

### 4.2 plugin.xml (for plugin-based containers)

Inside a plugin JAR, `plugin.xml` serves as a **marker** declaring that the JAR is a ChibiForge component container:

```xml
<plugin>
  <extension point="org.chibios.chibiforge.component"/>
</plugin>
```

All component metadata (ID, name, version, hidden, is_platform, description, categories, etc.) is read from `component/schema.xml`. The `plugin.xml` file exists solely to identify the JAR as a ChibiForge container; it does not duplicate any information from the schema.

Plugin JARs are a **portable container format**. Eclipse plugins contain **no configuration-related logic** — only component payloads. Non-Eclipse tools (CLI, standalone GUI, VS Code extensions) can read plugin JARs directly by parsing `plugin.xml` and extracting the `component/` subtree, with no dependency on Eclipse or OSGi APIs.

### 4.3 Component authoring in Eclipse

Eclipse remains useful for **component authoring**: developing `schema.xml`, writing templates, managing resources. The workflow is:

- The component project has a `.project` file (Eclipse project metadata), `plugin.xml`, `META-INF/MANIFEST.MF`, `build.properties`, and the `component/` subtree.
- Authors develop in Eclipse (edit schemas, write templates, add resources).
- Building/exporting the project produces the plugin JAR via Eclipse PDE, Maven/Tycho, or Gradle.
- The resulting JAR is dropped into `PLUGINS_ROOT` for consumption by the CLI and standalone GUI.

The `build.properties` file tells the build what to include in the JAR (typically `component/`, `rsc/`, `plugin.xml`, `META-INF/`).

### 4.4 Image assets

Images referenced in `<image>` elements (see §6.5) or used as icons (`rsc/icon.png`) should use formats with **alpha channel support** (e.g., PNG). Transparent backgrounds are preferred over solid white or black backgrounds, so images render cleanly on both light and dark GUI themes.

---

## 5. ComponentDefinition (`component/schema.xml`)

`component/schema.xml` contains a single `<component>` as its root element.

### 5.1 Root element

Example:

```xml
<component
    xmlns="http://chibiforge/schema/component"
    id="org.chibios.hal.stm32f4xx"
    name="HAL STM32F4xx"
    version="1.0.0"
    hidden="true"
    is_platform="false">

  <description>HAL configuration for STM32F4xx devices.</description>

  <resources>
    <resource id="stm32f4_limits"
              file="resources/stm32f4_limits.xml"/>
    <resource id="phy_catalog"
              file="resources/phy_catalog.json"/>
  </resources>

  <categories>
    <category id="HAL/Platforms/STM32F4xx"/>
  </categories>

  <requires>
    <feature id="features.hal.core"/>
  </requires>

  <provides>
    <feature id="features.hal.platform.stm32f4xx" exclusive="true"/>
  </provides>

  <sections>
    <!-- configuration sections and properties -->
  </sections>

</component>
```

Attributes (all required):

- `id`: unique component ID.
- `name`: human-readable name.
- `version`: component version.
- `hidden`: `"true"` or `"false"`.
- `is_platform`: `"true"` or `"false"`.

Child elements:

- `<description>` (required): human-readable description.
- `<resources>` (required): resource declarations (may be empty).
- `<categories>` (required): category assignments (at least one).
- `<requires>` (required): required features (may be empty).
- `<provides>` (required): provided features (may be empty).
- `<sections>` (optional): configuration schema — sections, properties, layouts, images.

The XSD schema (`chibiforge_schema.xsd`) defines the exact structure.

### 5.2 Feature Dependencies

Components declare soft dependencies using `<requires>` and `<provides>`:

- **`<provides>`**: a component advertises capabilities it offers. Each `<feature>` has a string `id`.
- **`<requires>`**: a component declares capabilities it needs from *some other* component in the configuration.
- **`exclusive="true"`**: at most one component in a configuration may provide this feature.

Resolution at generation time (or in the GUI):

1. Collect all provided features from all components in the configuration.
2. For each required feature across all components, check that at least one component provides it. If not, **emit a warning** (not a hard error).
3. For each feature marked `exclusive="true"`, check that at most one component provides it. If multiple components provide the same exclusive feature, **emit a conflict warning**.

This is a **soft dependency system**: warnings inform the user of likely misconfiguration but do not prevent generation. Components are never automatically pulled in or removed.

---

## 6. Configuration Schema (`<sections>`)

The configuration schema defines the structure of configurable properties using sections, properties, layouts, and images.

### 6.1 Sections

`<sections>` contains one or more `<section>` elements. Each section is a collapsible group of content.

```xml
<sections>
  <section name="Initialization Settings" expanded="false">
    <description>Core initialization parameters.</description>
    <property name="do_not_init" type="bool" ... />
    <property name="vdd" type="int" ... />
    <layout columns="2" align="left">
      <property name="hse_frequency" type="int" ... />
      <property name="lse_frequency" type="int" ... />
    </layout>
  </section>
</sections>
```

Section attributes (all required):

- `name`: display name (used as section title in GUIs).
- `expanded`: `"true"` or `"false"` — initial collapsed/expanded state.

Child elements:

- `<description>` (required): help text shown in the GUI.
- Then any mix of `<property>`, `<layout>`, and `<image>` elements.

**GUI rendering**: sections expand as document-style blocks — a horizontal line with title, slightly indented for each nesting level. The exact styling is up to the GUI implementation, but the intent is lightweight, document-like flow.

### 6.2 Properties

Properties define individual configurable values.

```xml
<property
    name="vdd"
    type="int"
    brief="Supply voltage in millivolts"
    required="true"
    editable="true"
    visible="true"
    default="300"
    int_min="180"
    int_max="360"/>
```

Required attributes:

- `name`: property identifier (used in data model paths and configuration XML).
- `type`: one of `bool`, `string`, `text`, `int`, `enum`, `list`.
- `brief`: short description (shown as label or tooltip).
- `required`: `"true"` or `"false"`.
- `editable`: `"true"`, `"false"`, or `"@cond:<xpath>"` — whether the field can be edited by the user. When using `@cond:`, the XPath expression is evaluated against the live data model to dynamically control editability.
- `visible`: `"true"`, `"false"`, or `"@cond:<xpath>"` — whether the field is shown in the GUI. When using `@cond:`, the XPath expression is evaluated against the live data model to dynamically control visibility.
- `default`: default value.

Examples:

```xml
<!-- Always visible, editable only when DMA is enabled -->
<property name="dma_channel_count"
          type="int"
          brief="Number of DMA channels"
          required="true"
          editable="@cond:doc/dma_settings/use_dma = 'true'"
          visible="true"
          default="1"
          int_min="1"
          int_max="8"/>

<!-- Only visible when clock source is PLL -->
<property name="pll_multiplier"
          type="int"
          brief="PLL multiplication factor"
          required="true"
          editable="true"
          visible="@cond:doc/initialization_settings/system_clock_source = 'PLL'"
          default="8"
          int_min="2"
          int_max="16"/>
```

The `@cond:` prefix triggers XPath evaluation against the same live data model used by `@ref:` (see §6.3). The GUI evaluates these expressions in real-time as the user edits values.

Type-specific optional attributes:

- **`int`**: `int_min`, `int_max` — minimum/maximum value constraints.
- **`string`**: `string_regex` — regular expression for validation.
- **`text`**: `text_maxsize` — maximum character count.
- **`enum`**: `enum_of` — CSV of allowed values (e.g., `"PLL,HSI,HSE"`).
- **`list`**: `list_columns` — CSV of property names to show in the table view, optionally followed by `:` and column width in pixels (e.g., `"name:150,mode:100,speed"`). Contains a nested `<sections>` element defining the structure of each list item.

Property types and their GUI widgets:

- `bool`: checkbox or toggle switch.
- `string`: single-line editable text field. Validate against `string_regex` if present.
- `text`: multi-line text box (monospaced, optional language highlighting). Enforce `text_maxsize` if present.
- `int`: editable text field with numeric validation. Enforce `int_min` / `int_max`.
- `enum`: combo box (dropdown) populated from `enum_of` (CSV or resolved `@ref:`).
- `list`: table view with drill-down editing (see §6.6).

### 6.3 Resource References (`@ref:`)

Any property attribute value can contain a resource reference using the syntax:

```
@ref:<resource_id>/<xpath>
```

This resolves against the FMPP data model at runtime. Resource IDs map directly to top-level FMPP variables (which are the parsed resource sets declared in the current component's `<resources>`).

Examples:

```xml
<property name="max_channels"
          type="int"
          brief="Maximum ADC channels"
          required="true"
          editable="false"
          visible="true"
          default="@ref:stm32f4_limits/adc/@max_channels"
          int_max="@ref:stm32f4_limits/adc/@max_channels"/>

<property name="phy_type"
          type="enum"
          brief="PHY interface type"
          required="true"
          editable="true"
          visible="true"
          default="MII"
          enum_of="@ref:phy_catalog/interfaces/interface/@name"/>
```

**Live data model**: the GUI must build and maintain the FMPP-compatible data model while the user is editing — not just at generation time. Resource sets are loaded when the configuration is opened, and `@ref:` expressions are evaluated in real-time to populate constraints, defaults, and choices. The `@cond:` expressions in `visible` and `editable` attributes are also evaluated against this live data model. The same data model structure is used by both the editor and the generator.

### 6.4 Layouts

Layouts arrange properties and images in a multi-column grid within a section.

```xml
<layout columns="2" align="left">
  <property name="hse_frequency" type="int" ... />
  <property name="lse_frequency" type="int" ... />
  <image file="rsc/clock_tree.png" align="center">
    <text>Clock tree overview</text>
  </image>
  <empty/>
</layout>
```

Attributes (all required):

- `columns`: `"1"`, `"2"`, `"3"`, or `"4"`.
- `align`: `"left"`, `"center"`, or `"right"`.

Child elements (any mix):

- `<property>`: a property rendered in the grid cell.
- `<image>`: an image rendered in the grid cell.
- `<empty>`: an empty visual slot (for alignment purposes).

### 6.5 Images

Images can appear at the component level (inside `<component>`, alongside `<sections>`), inside sections, or inside layouts.

```xml
<image file="rsc/block_diagram.png" align="center">
  <text>Block diagram of the peripheral</text>
</image>
```

Attributes (all required):

- `file`: path to the image file, relative to the component container.
- `align`: `"left"`, `"center"`, or `"right"`.

Child elements:

- `<text>` (required): caption or alt text for the image.

Images should use formats with alpha channel support (e.g., PNG) with transparent backgrounds to render cleanly on both light and dark GUI themes (see §4.4).

### 6.6 List Editing (GUI Behavior)

Properties of `type="list"` are presented as a table and edited with a drill-down pattern:

- **Table view**: list items are shown as a table. Columns are determined by the `list_columns` attribute (CSV of property names with optional pixel widths).
- **Controls**: add, remove, duplicate, reorder rows.
- **Drill-down**: double-clicking a row opens the item's configuration form, driven by the nested `<sections>` within the list property.
- **Breadcrumb navigation**: the edit window displays a breadcrumb or clickable XPath at the top (e.g., `Component > Initialization Settings > pins > [3]`), allowing the user to navigate back to any parent level. Each segment is clickable.
- **Recursive**: if a list item contains another list, the same table → drill-down → breadcrumb pattern applies recursively.

All list operations mutate the corresponding list structure under `<component id="X">` in `chibiforge.xcfg`.

---

## 7. Configuration File (`chibiforge.xcfg`)

`chibiforge.xcfg` stores per‑configuration values. It is typically placed at the project root but may reside in any directory, which then becomes the configuration root.

### 7.1 Root structure

```xml
<chibiforgeConfiguration
    xmlns="http://chibiforge/schema/config"
    toolVersion="1.0.0"
    schemaVersion="1.0">

  <targets>
    <target id="default"/>
    <target id="debug"/>
    <target id="release"/>
  </targets>

  <components>
    <component id="org.chibios.hal.stm32f4xx">
      <initialization_settings>
        <do_not_init>false</do_not_init>
        <vdd>300</vdd>
        <system_clock_source>PLL</system_clock_source>
      </initialization_settings>
      <dma_settings>...</dma_settings>
      <irq_settings>...</irq_settings>
    </component>
    <!-- more components -->
  </components>
</chibiforgeConfiguration>
```

Rules:

- Each `<component>` `@id` must match a ComponentDefinition `id`.
- Child elements map to section names and property names defined in `schema.xml`.
- List properties are serialized as nested items under their section/property.

### 7.2 Targets

Targets model named build/configuration variants within a single `chibiforge.xcfg`, analogous to debug/release configurations in a build system.

**Core rules:**

- A configuration always has a **"default" target** that cannot be removed.
- Users may define additional targets (e.g., "debug", "release", "board_v2").
- The `<targets>` element lists all defined targets. If omitted, only the "default" target exists.

**Property values and targets:**

- By default, every property is **single-target**: it has one value shared across all targets. The value is stored as the element's text content.
- A property can be promoted to **multi-target** (via the GUI or by manual XML editing), at which point the default target's value moves to a `default` attribute and per-target overrides are stored as `<targetValue>` child elements.
- **Fallback**: if a multi-target property has no explicit `<targetValue>` for a given target, the value from the `default` attribute is used.

**Serialization:**

```xml
<component id="org.chibios.hal.stm32f4xx">
  <initialization_settings>
    <!-- Single-target property: plain text content (all targets get this value) -->
    <do_not_init>false</do_not_init>

    <!-- Multi-target property: default attribute + per-target overrides -->
    <vdd default="300">
      <targetValue target="debug">330</targetValue>
      <targetValue target="release">280</targetValue>
    </vdd>
  </initialization_settings>
</component>
```

**Resolution logic:**

1. If the selected target is "default" → use the `default` attribute value.
2. If a `<targetValue>` matches the selected target → use that element's value.
3. If no matching `<targetValue>` → fall back to the `default` attribute.

**Generation behavior:**

- The generator runs **for a specific target** (specified via CLI `--target` or GUI selection).
- The data model contains only resolved values — templates see a single value per property with no awareness of the multi-target mechanism.
- All outputs go to the shared `generated/` directory; there is no per-target output isolation. The caller is responsible for regenerating when switching targets.

---

## 8. Outputs

Outputs are produced by two distinct mechanisms:

- **Static payload**: convention-based file copying from `source*` directories.
- **Templates**: processed by FMPP, with output directory determined by convention (`cfg/`, `cfg_root_wa/`, `cfg_root_wo/`).

### 8.1 Static payload (`source*`)

Static payload directories use conventions to determine where files are copied. No XML declaration is needed — the generator walks the directory structure and applies the rules.

#### 8.1.1 `component/source/` → `generated/<normalizedComponentId>/`

- All files under `component/source/` are copied to:

  ```text
  <configurationRoot>/generated/<normalizedComponentId>/<relativePath>
  ```

- The component ID is normalized per §2.1.
- Example: component `org.chibios.hal.stm32f4xx` with `source/hal_ll.c` produces:

  ```text
  generated/org_chibios_hal_stm32f4xx/hal_ll.c
  ```

- Semantics: always overwritten on generation.

#### 8.1.2 `component/source_root_wa/` → root-scope, always

- `component/source_root_wa/<relativePath>` → `<configurationRoot>/<relativePath>`.
- Semantics: always overwritten on generation.

#### 8.1.3 `component/source_root_wo/` → root-scope, once

- `component/source_root_wo/<relativePath>` → `<configurationRoot>/<relativePath>`.
- Behavior:
  - If target file exists: do not overwrite.
  - If not: copy file.
- Semantics: write-once (user edits are preserved).

These static outputs do not involve templates.

### 8.2 Templates (`cfg/`, `cfg_root_wa/`, `cfg_root_wo/`)

Templates are processed by **FMPP** (FreeMarker-based File PreProcessor). The output directory is determined by which template directory the template resides in:

| Directory | Output root | Write policy |
|---|---|---|
| `component/cfg/` | `generated/` | always |
| `component/cfg_root_wa/` | configuration root | always |
| `component/cfg_root_wo/` | configuration root | write-once |

This mirrors the `source*` convention: `cfg/` is to `source/` as `cfg_root_wa/` is to `source_root_wa/`, and `cfg_root_wo/` is to `source_root_wo/`.

#### 8.2.1 `component/cfg/` → `generated/`

FMPP is configured with `<configurationRoot>/generated/` as the output directory. Output paths mirror the template's relative path under `cfg/` with the template extension stripped:

- `cfg/mcuconf.h.ftlc` → `generated/mcuconf.h`
- `cfg/board.h.ftlc` → `generated/board.h`
- `cfg/ldscripts/linker.ld.ftl` → `generated/ldscripts/linker.ld`

Always overwritten on generation.

#### 8.2.2 `component/cfg_root_wa/` → configuration root, always

Templates under `cfg_root_wa/` generate files relative to the configuration root, always overwriting:

- `cfg_root_wa/ldscripts/linker.ld.ftlc` → `<configurationRoot>/ldscripts/linker.ld`
- `cfg_root_wa/include/board.h.ftlc` → `<configurationRoot>/include/board.h`

#### 8.2.3 `component/cfg_root_wo/` → configuration root, write-once

Templates under `cfg_root_wo/` generate files relative to the configuration root, but only if the target file does not already exist:

- `cfg_root_wo/Makefile.ftlc` → `<configurationRoot>/Makefile` (only if `Makefile` doesn't exist)
- `cfg_root_wo/src/main.c.ftlc` → `<configurationRoot>/src/main.c` (only if `src/main.c` doesn't exist)

Write-once templates are useful for generating starter files that the user can then customize.

#### 8.2.4 FMPP output redirection

In addition to the convention-based directories, templates in any `cfg*` directory may use FMPP's output redirection APIs to write files to arbitrary locations within the configuration root. This is available for edge cases where the convention-based approach is insufficient. However, using FMPP redirection from `cfg_root_wa/` or `cfg_root_wo/` is discouraged — the convention already handles the common output patterns.

The constraint enforced by ChibiForge is that any output path **must remain within the configuration root**.

#### 8.2.5 Template modes

Mode detection:

- Files ending in `.ftlc`: **code-first** mode.
- Files ending in `.ftl`:
  - If they include `<#ftl syntax="code-first">` at the top → code-first.
  - Otherwise → classic mode.

Each file is parsed independently; `.ftl` and `.ftlc` can import/include each other.

**Classic (`.ftl`)**:

- Text outside directives is output literally.
- Use `<#if>`, `<#list>`, `${...}`.

**Code-first (`.ftlc` or `.ftl` with `syntax="code-first"`)**:

- Logic-oriented syntax:
  - `if/elseif/else/endif`, `list/endlist`, `switch/endswitch`, `macro`, `function`, etc.
- No implicit output; must use:
  - `emit expr`,
  - `emit "..."`,
  - `emit """..."""` for blocks.
- Supports:
  - Bitwise ops (`&`, `|`, `^`, `~`, `<<`, `>>`),
  - Hex literals (`0xFF`),
  - Extended built-ins (`?tab_to`, `?indent`, `?dedent`, `?pad_lines`, `?wrap`, etc.).

For the full code-first syntax reference, see the [freemarker-codegen](https://github.com/gdisirio/freemarker-codegen) repository.

### 8.3 Resource sets (`component/resources/`)

Resource sets are declared in `schema.xml`:

```xml
<resources>
  <resource id="stm32f4_limits"
            file="resources/stm32f4_limits.xml"/>
  <resource id="phy_catalog"
            file="resources/phy_catalog.json"/>
</resources>
```

- `file` is relative to `component/`, typically `"resources/..."`.
- The resource format (XML or JSON) is inferred from the file extension.
- Resources are **scoped to the owning component** — only the current component's resources are loaded into the data model when processing that component's templates.
- Used for:
  - `@ref:` resolution in schema property attributes.
  - Direct access in templates as top-level FMPP variables.

### 8.4 Output directory summary

| Directory | Type | Output | Policy |
|---|---|---|---|
| `source/` | static | `generated/<normalizedId>/` | always |
| `source_root_wa/` | static | configuration root | always |
| `source_root_wo/` | static | configuration root | write-once |
| `cfg/` | template | `generated/` | always |
| `cfg_root_wa/` | template | configuration root | always |
| `cfg_root_wo/` | template | configuration root | write-once |

---

## 9. Data Model for Templates

The generator builds a data model that is passed to FMPP as top-level variables. Each component's templates are processed with a data model scoped to that component. The same data model structure is used by the GUI for live `@ref:` and `@cond:` resolution.

### 9.1 Top-level FMPP variables

When processing templates for a given component, the following top-level variables are available:

- **`doc`**: the current component's resolved configuration — scoped to just this component's sections and properties, with multi-target values resolved for the active target.
- **`components`**: all components' resolved configurations, keyed by normalized component ID. Used for cross-component access when needed.
- **`configuration`**: metadata about the current generation context.
- **One variable per resource**: each `<resource>` declared in the current component's `schema.xml` becomes a top-level variable using its `id` as the name.

### 9.2 The `doc` variable

`doc` contains the current component's resolved configuration as an XML tree. Multi-target properties are resolved for the active target — templates see only single values. The structure directly reflects the component's sections and properties:

```xml
<!-- Accessed as: doc (for component org.chibios.hal.stm32f4xx) -->
<doc>
  <initialization_settings>
    <do_not_init>false</do_not_init>
    <vdd>330</vdd>  <!-- resolved for active target "debug" -->
    <system_clock_source>PLL</system_clock_source>
  </initialization_settings>
  <dma_settings>...</dma_settings>
</doc>
```

Template access examples (code-first mode):

```
// Access a scalar value — short, direct paths
emit doc.initialization_settings.vdd

// Iterate over a list
list doc.pin_settings.pins as pin
  emit pin.name
  emit pin.mode
endlist
```

### 9.3 The `components` variable

Provides all components' resolved configurations for cross-component access:

```xml
<!-- Accessed as: components -->
<components>
  <org_chibios_hal_stm32f4xx>
    <initialization_settings>
      <vdd>330</vdd>
      ...
    </initialization_settings>
  </org_chibios_hal_stm32f4xx>
  <org_chibios_board_stm32f4xx>
    <board_settings>
      ...
    </board_settings>
  </org_chibios_board_stm32f4xx>
</components>
```

Template access:

```
// Access another component's configuration
emit components.org_chibios_board_stm32f4xx.board_settings.board_name
```

### 9.4 The `configuration` variable

Provides metadata about the generation context:

```xml
<!-- Accessed as: configuration -->
<configuration>
  <root>/path/to/configurationRoot</root>
  <generatedRoot>/path/to/configurationRoot/generated</generatedRoot>
  <target>debug</target>
</configuration>
```

### 9.5 Resource variables

Each resource declared in the current component's `schema.xml` is loaded and exposed as a top-level FMPP variable using its `id`:

```xml
<!-- Accessed as: stm32f4_limits -->
<stm32f4_limits>
  <adc max_channels="16" max_resolution="12"/>
  <dma max_streams="8"/>
  ...
</stm32f4_limits>

<!-- Accessed as: phy_catalog (JSON converted to XML tree) -->
<phy_catalog>
  <interfaces>
    <interface name="MII"/>
    <interface name="RMII"/>
  </interfaces>
</phy_catalog>
```

Template access:

```
// Access a resource value
emit stm32f4_limits.adc.@max_channels
```

`@ref:` expressions in schema property attributes resolve against the same top-level variables:

```
@ref:stm32f4_limits/adc/@max_channels
```

Resources are scoped to the owning component — a component's templates can only access that component's resources. If a value needs to be shared between components, it should be exposed as a configuration property and accessed via the `components` variable.

---

## 10. Generator Engine (Logical Behavior)

The generator engine (used by both CLI and GUI tools) performs:

1. **Load configuration**:
   - From `<configurationRoot>/chibiforge.xcfg` (or override path).
   - Validate structure and required component references.

2. **Resolve target**:
   - Determine the active target (from CLI `--target` option or GUI selection; defaults to "default").

3. **Resolve components**:
   - For each `<component id="...">` in `chibiforge.xcfg`:
     - Load the corresponding `component/schema.xml`.
     - Build a ComponentDefinition object.

4. **Check feature dependencies**:
   - Collect provided features, check required features, check exclusive conflicts.
   - Emit warnings for unresolved or conflicting features.

5. **Build resolved configurations**:
   - For each component, resolve multi-target properties for the active target.
   - Build the `components` variable (all components' resolved configs).

6. **Process each component**:
   - For each component in the configuration:

     a. **Load resources**:
        - Load and parse the component's `<resource>` files (format inferred from file extension).
        - Create top-level variables using each resource `id`.

     b. **Build component data model**:
        - `doc` = this component's resolved config (scoped).
        - `components` = all components' resolved configs (shared).
        - `configuration` = metadata (root paths, active target).
        - Resource variables = this component's parsed resources.
        - Resolve `@ref:` expressions in schema property attributes.

     c. **Process static payload**:
        - Copy files from `source/`, `source_root_wa/`, and `source_root_wo/` per conventions (see §8.1).

     d. **Process templates via FMPP**:
        - Process templates from `cfg/` with `generated/` as output directory.
        - Process templates from `cfg_root_wa/` with configuration root as output directory (always overwrite).
        - Process templates from `cfg_root_wo/` with configuration root as output directory (skip if target exists).
        - Ensure all output paths remain within the configuration root.

7. **Logging**: log all actions; support `--dry-run` and `--verbose`.

---

## 11. Tool Integration

ChibiForge is designed to be used from multiple front-ends. The generator engine and component model are independent of any specific tool.

### 11.1 Possible front-ends

- **CLI**: standalone command-line tool (see Addendum A).
- **Standalone GUI**: dedicated desktop application built with **JavaFX** (OpenJDK + OpenJFX). Registered as the handler for `.xcfg` files — double-clicking opens the editor. Packaged as a self-contained application via `jpackage` (bundled JRE, no Java installation required).
- **Eclipse**: plugin-based integration using the ChibiForge component extension point.
- **VS Code**: extension with webview-based configuration UI (future).
- **Web UI**: browser-based configuration editor (future).

### 11.2 Standalone GUI

The primary GUI tool is a standalone JavaFX application:

- Opens `chibiforge.xcfg` files directly (file association with `.xcfg` extension).
- Provides the full schema-driven editor described in Addendum B.
- Calls the generator engine directly (in-process, same JVM as FMPP and freemarker-codegen).
- Supports light and dark themes via CSS stylesheets.
- Distributed as a platform-native installer (`.msi`, `.dmg`, `.deb`) with bundled JRE.

### 11.3 Eclipse integration

Eclipse integration wraps the generator:

- A core plugin:
  - Defines `org.chibios.chibiforge.component` extension point.
  - Discovers and loads plugin-based containers (`component/schema.xml`, `component/`, etc.).
  - Detects `chibiforge.xcfg` and offers a ChibiForge editor (forms + XML).
  - Provides a "Generate Configuration" action that calls the generator.
  - Optionally provides a builder that runs generation before builds.

- Component container plugins:
  - Provide the same `component/` layout as filesystem containers.
  - Contain **no configuration-related logic** — only component payloads (schemas, templates, static files, resources).
  - Allow reuse of exactly the same components in CLI, IDE, and CI.

### 11.4 Non-Eclipse tools and plugin JARs

Non-Eclipse tools can consume Eclipse plugin JARs as component containers:

- Parse `plugin.xml` to find the `org.chibios.chibiforge.component` extension point marker.
- Load `component/schema.xml` from the fixed path within the JAR.
- No Eclipse, OSGi, or Equinox APIs are required.

This ensures components packaged as Eclipse plugins are reusable across all ChibiForge tools.

---

# Addendum A – CLI Generator and Component Sources

This addendum defines the behavior of the **ChibiForge CLI** and how it discovers components from filesystem and plugin JAR sources.

---

## A.1 Purpose

The **ChibiForge CLI**:

- Is a standalone tool (no IDE required).
- Reads `chibiforge.xcfg`.
- Loads component containers from:
  - A **components directory** (filesystem containers),
  - An optional **plugins directory** (JAR containers).
- Runs the generator engine to populate the configuration root with generated and static files.

---

## A.2 CLI Interface

### A.2.1 Command

```bash
chibiforge generate \
  --config CONFIG_PATH \
  --components COMPONENTS_ROOT \
  [--plugins PLUGINS_ROOT] \
  [--target TARGET_ID] \
  [--dry-run] \
  [--verbose]
```

Parameters:

- `--config`: path to `chibiforge.xcfg`. The directory containing this file becomes the configuration root.
- `--components`: filesystem components root (see A.3.1).
- `--plugins`: plugin JARs root (see A.3.2).
- `--target`: target to generate for (defaults to `"default"`).
- `--dry-run`: no writes, just log intended actions.
- `--verbose`: extended logging.

### A.2.2 Environment Variables and Precedence

The CLI reads configuration from:

1. CLI options,
2. Environment variables,
3. Defaults.

Environment variables:

- `CHIBIFORGE_CONFIG_PATH` → default for `--config`.
- `CHIBIFORGE_COMPONENTS_ROOT` → default for `--components`.
- `CHIBIFORGE_PLUGINS_ROOT` → default for `--plugins`.

Resolution:

- Config path:
  - `--config` > `CHIBIFORGE_CONFIG_PATH` > `./chibiforge.xcfg` (current working directory).
- Components root (filesystem):
  - `--components` > `CHIBIFORGE_COMPONENTS_ROOT` > no filesystem source.
- Plugins root (JARs):
  - `--plugins` > `CHIBIFORGE_PLUGINS_ROOT` > no plugin source.

If no component sources (neither filesystem nor plugins) are configured, the CLI must error with a clear message.

---

## A.3 Component Sources

### A.3.1 Filesystem Component Containers

Layout:

```text
COMPONENTS_ROOT/
  <componentId>/
    component/
      schema.xml
      cfg/
      cfg_root_wa/
      cfg_root_wo/
      source/
      source_root_wa/
      source_root_wo/
      resources/
        ...
    rsc/ (optional)
```

Discovery:

- For each subdirectory `D` under `COMPONENTS_ROOT`:
  - Check for `D/component/schema.xml`:
    - If present, parse as ComponentDefinition `<component>`.
    - Extract `@id`.
    - For v1: require `D` name to equal `@id`.

If `component/schema.xml` is missing, `D` is not a valid component container.

### A.3.2 Plugin JAR Component Containers

Within a JAR under `PLUGINS_ROOT`:

```text
component/
  schema.xml
  cfg/
  cfg_root_wa/
  cfg_root_wo/
  source/
  source_root_wa/
  source_root_wo/
  resources/
    ...
plugin.xml
META-INF/MANIFEST.MF
...
```

Discovery:

1. Scan JAR files under `PLUGINS_ROOT`.
2. For each JAR:
   - Open `plugin.xml`.
   - Check for `<extension point="org.chibios.chibiforge.component"/>`.
   - If present, load `component/schema.xml` to extract component metadata.

---

## A.4 ComponentContainer and ComponentContent

The CLI uses abstractions to handle filesystem and JAR containers uniformly.

### A.4.1 ComponentContainer

Conceptual interface:

```text
ComponentContainer
  - getId() : String
  - loadDefinition() : ComponentDefinition
  - getComponentContent() : ComponentContent   # for component/ (schema, cfg*, source*, resources/)
```

### A.4.2 ComponentContent

Abstracts file listing and opening within the `component/` subtree:

```text
ComponentContent
  - open(relativePath: String) : InputStream
  - list(prefix: String) : List<String>
    # e.g. list("cfg/"), list("cfg_root_wa/"), list("source/"), list("resources/")
```

Implementations:

- **FilesystemContent(root = <containerRoot>/component)**:
  - `open("cfg/mcuconf.h.ftlc")` → `Files.newInputStream(root.resolve("cfg/mcuconf.h.ftlc"))`.
  - `list("source/")` → walk `root/source/`.

- **JarContent(jarFile, base = "component/")**:
  - `open("cfg/mcuconf.h.ftlc")` → `jarFile.getInputStream(new JarEntry("component/cfg/mcuconf.h.ftlc"))`.
  - `list("source/")` → iterate over JAR entries with names starting with `"component/source/"`.

The generator uses only `ComponentContainer` and `ComponentContent`, independent of the source type.

---

## A.5 ComponentRegistry Behavior

The CLI builds a **ComponentRegistry** by merging:

- Filesystem containers from `COMPONENTS_ROOT`.
- JAR containers from `PLUGINS_ROOT`.

Algorithm:

1. If `componentsRoot` is configured:
   - For each subdirectory under `componentsRoot`:
     - Build a filesystem `ComponentContainer` if `component/schema.xml` exists.

2. If `pluginsRoot` is configured:
   - For each JAR:
     - Check `plugin.xml` for the ChibiForge extension point marker.
     - If present, create a `JarContainer`.

3. Merge into a map:

   ```text
   componentId → ComponentContainer
   ```

4. Conflict resolution:
   - If the same `componentId` appears in both filesystem and JAR sources:
     - **Filesystem container overrides** JAR container.
     - This enables local overrides of components shipped as plugins.

The generator then uses this registry to resolve components referenced in `chibiforge.xcfg`.

---

## A.6 Generator Pipeline (CLI Context)

The main pipeline is identical to the general spec; the CLI-specific steps are:

1. Resolve `configPath`, `componentsRoot`, `pluginsRoot`, `targetId` via CLI + env.
2. Derive configuration root from the directory containing `configPath`.
3. Build `ComponentRegistry` from `componentsRoot` and `pluginsRoot`.
4. Load `chibiforge.xcfg` from `configPath`.
5. Resolve the active target (from `--target` or default).
6. For each `<component id="X">` in configuration:
   - Look up `X` in the registry; error if not present.
   - Call `loadDefinition()` to parse `component/schema.xml`.
7. Check feature dependencies; emit warnings as needed.
8. Build resolved configurations for all components (resolve multi-target properties).
9. For each component:
   - Load resources, build scoped data model, process static payload, process templates (cfg/, cfg_root_wa/, cfg_root_wo/) via FMPP.
10. Log all actions (respecting `--dry-run`, `--verbose`).

No Eclipse or OSGi APIs are used; all operations are filesystem/JAR-based.

---

## A.7 Implementation Phasing

Recommended phases:

1. **Phase 1 – Filesystem-only v1**:
   - Support only `COMPONENTS_ROOT` (filesystem containers).
   - Implement generator core (config, static payload, FMPP template processing, resources).
   - Single target ("default") only.

2. **Phase 2 – Multi-target support**:
   - Implement target resolution and multi-target property fallback.
   - Add `--target` CLI option.

3. **Phase 3 – JAR backend**:
   - Implement `JarContainer` and `JarContent`.
   - Parse `plugin.xml` for extension point marker.
   - Respect `CHIBIFORGE_PLUGINS_ROOT` / `--plugins`.

4. **Phase 4 – Combined sources**:
   - Merge filesystem and JAR containers in registry.
   - Support local overrides (filesystem wins).

This approach yields an immediately useful CLI and allows seamless reuse of the same components in IDE and CI environments.

---

# Addendum B – GUI Concepts and Behavior

This addendum defines recommended GUI behavior for tools integrating with ChibiForge (e.g. the standalone JavaFX application, Eclipse, VS Code, web UIs). It is **IDE-agnostic** and describes how to build the UI from:

- Component containers: `component/schema.xml`, `component/cfg*/`, `component/source*`, `component/resources/`.
- Configuration file: `chibiforge.xcfg`.

The goal is a **schema-driven** GUI: no hardcoded component logic, only generic UI generated from the schema.

---

## B.1 High-Level UI Model

GUI consists of:

1. A **ChibiForge Configuration Editor** for each configuration file:
   - Bound to a `chibiforge.xcfg`.
   - Owns reading/writing that file.

2. Within the editor:
   - A **Target Selector** (always visible):
     - Dropdown or similar control showing the active target.
     - Controls for creating, renaming, and deleting targets ("default" cannot be deleted).
   - A **Components Page** (overview):
     - Palette of available components (from the registry).
     - Configuration Components list (components currently in the configuration).
   - A **Configuration Page**:
     - Left: tree/list of configuration components.
     - Right: schema-driven configuration form for the selected component.

3. No multiple editors writing the same `chibiforge.xcfg`:
   - All edits go through the single Configuration Editor.

4. **Live data model**:
   - The GUI maintains the FMPP-compatible data model (including loaded resources) while the user is editing.
   - `@ref:` expressions in schema property attributes are resolved in real-time.
   - `@cond:` expressions in `visible` and `editable` attributes are evaluated in real-time as the user edits values, dynamically showing/hiding fields and toggling editability.
   - Resource sets are loaded when the configuration is opened and remain available throughout the editing session.

---

## B.2 Single Instance per Component

**Invariant** for ChibiForge v1:

- `chibiforge.xcfg` MAY contain at most **one** `<component>` element per component ID.

Example:

```xml
<components>
  <component id="org.chibios.hal.stm32f4xx"> ... </component>
  <component id="org.chibios.board.stm32f4xx"> ... </component>
</components>
```

- There MUST NOT be two `<component>` elements with the same `id`.
- Multiplicity (e.g. many pins, multiple UART channels) is modeled via **`type="list"` properties inside the component**, not by multiple `<component>` entries.

GUIs MUST enforce this by:

- Preventing adding a second instance of the same component via the palette.
- Optionally treating multiple instances as an error if manually edited.

---

## B.3 Components Page

### B.3.1 Component Palette (Available Components)

The palette shows all components in the **ComponentRegistry** (filesystem + plugin containers). For each component:

- Read from `component/schema.xml`:

  - `@id`: component ID.
  - `@name`: display name.
  - `<description>`: long description (for tooltip/help).
  - `<categories>/<category id="...">`: category labels.

- Optionally read icon from container: `rsc/icon.png` if present.

**Categories**:

- Each `<category id>` is a string; UIs MAY interpret `/` as hierarchy:

  - Example: `HAL/Platforms/STM32F4xx` ⇒ group `HAL` → subgroup `Platforms` → leaf `STM32F4xx`.

- A component may specify multiple categories:

  ```xml
  <categories>
    <category id="HAL/Platforms/STM32F4xx"/>
    <category id="RTOS"/>
  </categories>
  ```

  → appear in every listed category group in the palette.

**Hidden components**:

- If `hidden="true"` in the component definition:
  - The GUI MAY omit them from the palette by default.
  - But MUST show them in Configuration Components if they are present in `chibiforge.xcfg`.

**Single-instance behavior**:

- If a component `X` is already present in the configuration (a `<component id="X">` exists in `chibiforge.xcfg`):
  - Palette entry for `X` SHOULD be:
    - Greyed out / disabled, or
    - Hidden (optionally behind a "show used components" filter).
  - Dragging `X` again should be a no-op or produce an informational message ("Already in configuration").

### B.3.2 Configuration Components List

The Configuration Components list shows all components **currently configured**:

- Derived from `chibiforge.xcfg`:

  ```xml
  <components>
    <component id="X">...</component>
    ...
  </components>
  ```

- For each `<component id="X">`:
  - Lookup in ComponentRegistry.
  - Show:
    - Icon,
    - Name,
    - (Optional) categories or brief description.

**Actions:**

- **Configure**:
  - Double-click:
    - Focuses the Configuration Editor,
    - Switches to Configuration page,
    - Selects that component in the tree and shows its configuration form.

- **Remove**:
  - Delete removes `<component id="X">` from `chibiforge.xcfg`.
  - Optional: warn if other components depend on `X` via features (future enhancement).

---

## B.4 Configuration Page (Per-Component Configuration)

The Configuration Page is a master‑detail view of configuration components.

### B.4.1 Components Tree (Left)

- Lists each `<component id="X">` from `chibiforge.xcfg`.
- Each node:
  - Icon + Name (from ComponentDefinition).
  - Optional: group by category for better navigation.

**Selection behavior:**

- Clicking a component node `X` displays the configuration form for `X` on the right.

### B.4.2 Component Configuration Form (Right)

For selected component `X`:

- Load:
  - `component/schema.xml` → ComponentDefinition.
  - `<component id="X">` subtree from `chibiforge.xcfg` → current values.

Render a form driven by the schema:

#### Sections

For each `<section>`:

- Render a **collapsible section** in document-style flow:
  - Horizontal line with title (`@name`).
  - Initial expanded state from `@expanded`.
  - Show `<description>` as help text.
  - Slightly indented for each nesting level.
- Render child elements (`<property>`, `<layout>`, `<image>`) in order.

#### Properties

For each `<property>` within a section or layout:

- Evaluate `visible` to determine whether to show the field. If the value starts with `@cond:`, evaluate the XPath expression against the live data model.
- If visible, render the appropriate widget based on type (see §6.2).
- Evaluate `editable` to determine whether the field is interactive. If the value starts with `@cond:`, evaluate the XPath expression. Non-editable fields are rendered as read-only.
- `@cond:` expressions are re-evaluated whenever the data model changes (i.e., when the user edits any value), enabling reactive UI behavior.

#### Layouts

For `<layout columns="N" align="...">`:

- Render child elements in an N-column grid.
- `<empty/>` elements produce blank cells for alignment.
- `<image>` elements render the image with caption in the grid cell.

#### Images

For `<image>`:

- Display the image referenced by `@file` (relative to the component container).
- Show `<text>` as a caption.
- Apply `@align` for positioning.

#### Multi-target toggle

Each property widget should offer a mechanism (e.g., a context menu, toggle icon, or right-click option) to **promote** it from single-target to multi-target. Once promoted:

- The widget shows the value for the **currently selected target** (per the Target Selector).
- A visual indicator (icon, badge, or color) marks the field as multi-target.
- Switching targets in the Target Selector updates all multi-target fields to show the selected target's values.
- If a multi-target field has no explicit value for the selected target, the default target's value is shown, visually distinguished (e.g., dimmed, italic, or with a "default" badge).

#### Lists

List properties follow the drill-down editing pattern described in §6.6: table view → double-click to enter item → breadcrumb navigation to return.

---

## B.5 Interaction with Configuration Components View

The Configuration Components View (outside the editor, e.g. as a separate view pane) and the ChibiForge Configuration Editor should be:

- **Synchronized**:

  - Selecting a component in Configuration Components View highlights it in the Configuration Page tree.
  - Double‑clicking a component in Configuration Components View:
    - Opens/activates the ChibiForge Configuration Editor for `chibiforge.xcfg`.
    - Switches to the Configuration Page.
    - Selects the component and shows its form.

- **Single source of truth**:
  - Only the Configuration Editor writes `chibiforge.xcfg`.
  - The Configuration Components View is read-only wrt on-disk config; its changes must go through the Configuration Editor's model.

---

## B.6 Error Handling and Validation

The GUI should:

- Reflect validation errors from:
  - Schema constraints (types, required, int_min/int_max, string_regex, text_maxsize, enum_of).
  - `@ref:` resolution failures (resource not found, XPath returns no result).
  - `@cond:` evaluation failures (invalid XPath).
  - Feature dependency warnings (unresolved or conflicting features).
  - Generator-level validation (e.g. conflicting settings).
- Represent errors as:
  - Field-level messages next to widgets.
  - Aggregated markers in a problem view.
  - Component-level status icons in Configuration Components Tree and View.

---

## B.7 IDE-Agnostic Nature

While this addendum uses UI terminology that may echo Eclipse conventions, the concepts are independent of any specific IDE or toolkit:

- Any front‑end (JavaFX standalone app, Eclipse, VS Code extension via Webview, web app) can implement:

  - A target selector for switching between build/configuration variants.
  - A palette of available components (from ComponentRegistry).
  - A configuration components list (from `chibiforge.xcfg`).
  - Schema-driven forms for each component based on `component/schema.xml`.
  - Single-instance enforcement (one `<component id>` per component per configuration).
  - Multi-target property management with fallback to default.
  - Live data model with real-time `@ref:` and `@cond:` resolution.

The only required contract is:

- **Read** and **write** `chibiforge.xcfg` in accordance with the schema.
- **Interpret** `component/schema.xml` and `component/resources/` as specified in the main spec.
- **Maintain a live data model** for `@ref:` and `@cond:` resolution during editing.

---

This Addendum B fixes the GUI behavior to the "1 + N" pattern:

- One configuration-level editor per `chibiforge.xcfg` file,
- N per-component configuration views inside it,
- Single instance per component ID,
- Palette that greys‑out or hides components already placed in the configuration,
- Target selector governing which variant's values are displayed and generated,
- Live data model enabling real-time constraint, visibility, editability, and default resolution.

---

# Addendum C – Items Pending Detailed Specification

The following items are referenced in this spec but require further design work before implementation:

1. **XSD for `chibiforge.xcfg`**: formal XML Schema definition for the configuration file. The component schema XSD (`chibiforge_schema.xsd`) is provided; the configuration file schema is pending. This will also finalize the exact serialization format for multi-target values.

2. **FMPP output redirection**: the exact FMPP mechanism used by templates to redirect their output path at runtime. The ChibiForge constraint (must stay within configuration root) is defined; the FMPP API details are documented in the FMPP project.

---

# Addendum D – Future Features (v2.0 and Beyond)

This addendum collects ideas for future ChibiForge versions that go beyond the v1 scope.

---

## D.1 Embedded Mini-Scripts for Properties

Properties could include small inline scripts for validation and dynamic behavior, embedded directly in `schema.xml`. This avoids the need for compiled Java classes and keeps logic self-contained within the component definition.

**Possible syntax:**

```xml
<property name="vdd" type="int" brief="Supply voltage" ...>
  <script event="validate">
    if (doc.initialization_settings.use_dma == "true" &amp;&amp; value &lt; 250)
      return "VDD must be >= 250mV when DMA is enabled";
  </script>
</property>

<property name="clock_source" type="enum" brief="Clock source" ...>
  <script event="choices">
    // Dynamically compute available options based on other fields
    return ["PLL", "HSI", "HSE"];
  </script>
</property>
```

**Possible script events:**

- **`validate`**: called on focus loss. Receives the current `value` and `doc`. Returns `null` for success or an error/warning message string. Should execute quickly to avoid impacting GUI responsiveness.
- **`choices`**: dynamically computes the list of allowed values for `enum` properties, replacing or supplementing `enum_of`.
- **`default`**: dynamically computes a default value based on other property values.

**Implementation considerations:**

- Scripts would be evaluated by an embedded JavaScript engine (e.g., GraalJS) running in the same JVM.
- Scripts have read-only access to the data model (`doc`, resource variables) and the current `value`.
- Scripts should be minimal — a few lines at most — for simple conditional logic that cannot be expressed with static constraints or XPath expressions.
- Validation scripts execute on focus loss, so they do not impact GUI responsiveness during typing.

---

## D.2 Java Extension Mechanism (Plugin JARs)

For more complex scenarios beyond what mini-scripts can handle, plugin JARs could include compiled Java classes that participate in the configuration lifecycle:

- **Custom validators**: cross-field, cross-section, hardware-specific validation rules.
- **Dynamic field population**: choices computed at runtime from external data sources (e.g., scanning hardware, querying device databases).
- **Custom transformations**: pre-process or post-process values before they reach the data model or generated output.

**Mechanism:**

- Property or section elements reference a Java class name (e.g., `validator="org.chibios.hal.validators.VddValidator"`).
- The class lives in the plugin JAR and implements a ChibiForge-defined interface.
- At runtime, ChibiForge loads the class via a classloader, instantiates it, and calls it with the relevant DOM/data model.

**Considerations:**

- Requires classloader management and a well-defined API contract.
- Security sandboxing may be needed for untrusted plugins.
- Only available for plugin JAR containers (not filesystem containers).
- Mini-scripts (§D.1) are preferred for simple logic; Java extensions are for heavy-lifting scenarios.

---

## D.3 Dynamic Field Population

Enum choices and list item defaults could be populated dynamically at runtime, going beyond static `enum_of` CSV values and `@ref:` resource lookups:

- The `choices` mini-script event (§D.1).
- Java extension classes (§D.2) for external data sources.
- Use cases: available serial ports, connected hardware, device family databases, firmware version catalogs.
