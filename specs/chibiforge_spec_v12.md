# ChibiForge Specification v12

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
- Template execution and generation are **deterministic** given identical inputs.

---

## 2. Terminology

- **Project root**: top‑level directory of a user project.
- **Configuration root**: the directory containing a ChibiForge configuration file. A project may contain multiple configuration roots.
- **ChibiForge configuration file**: an XML configuration file, conventionally named `chibiforge.xcfg`, typically placed at the project root.
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

- `org.chibios.chibiforge.components.hal.stm32f4xx` → `org_chibios_chibiforge_components_hal_stm32f4xx`
- `HAL-Core_v2` → `hal_core_v2`
- `my..strange...id` → `my_strange_id`

This rule applies to: generated directory names, data model variable names, and any context where an ID is used as a filesystem or data model path segment.

---

## 3. Layout & Conventions

### 3.1 Minimal requirements

A configuration requires only:

- A ChibiForge configuration file (conventionally named `chibiforge.xcfg`):

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

Each configuration file defines an independent configuration root. Generated and root-relative outputs are always relative to the configuration root containing that file.

**Constraint:** ChibiForge outputs are defined relative to the configuration root. Tools and component authors MUST treat paths outside the configuration root as invalid targets.

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
### 4.1.1 Component identity and naming

A component has a **canonical identity** defined by its component ID.

For v12, the following identifiers MUST be identical:

- `component/schema.xml` → `<component id="...">`
- `META-INF/MANIFEST.MF` → `Bundle-SymbolicName` (for plugin containers)
- Component container name:
  - directory name (filesystem container)
  - JAR name prefix (plugin container)

Examples:

- Filesystem container:
  - directory: `org.chibios.chibiforge.components.platform.stm32g4xx`
  - `schema.xml @id`: `org.chibios.chibiforge.components.platform.stm32g4xx`

- Plugin container:
  - JAR: `org.chibios.chibiforge.components.board.stm32g4xx_1.0.0.jar`
  - `Bundle-SymbolicName`: `org.chibios.chibiforge.components.board.stm32g4xx`
  - `schema.xml @id`: `org.chibios.chibiforge.components.board.stm32g4xx`

### 4.1.2 Naming convention

Component IDs SHALL follow reverse-domain naming:

`<domain>.<project>.components.<category>.<name>`

Examples:

- `org.chibios.chibiforge.components.platform.stm32g4xx`
- `org.chibios.chibiforge.components.board.stm32g4xx`

Third-party components MUST use their own domain.

### 4.1.3 Validation

Tools MUST reject components where:

- container name ≠ component ID
- `Bundle-SymbolicName` ≠ component ID
- `schema.xml @id` ≠ component ID

For filesystem containers, the directory name MUST equal the component ID.

For plugin JAR containers, the naming convention MUST be:

`<componentId>_<version>.jar`

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

Images used in sections or layouts (see §6.5) or as icons (`rsc/icon.png`) should use formats with **alpha channel support** (e.g., PNG). Transparent backgrounds are preferred over solid white or black backgrounds, so images render cleanly on both light and dark GUI themes.

---

## 5. ComponentDefinition (`component/schema.xml`)

`component/schema.xml` contains a single `<component>` as its root element.

### 5.1 Root element

Example:

```xml
<component
    xmlns="http://www.example.org/chibiforge_schema/"
    id="org.chibios.chibiforge.components.hal.stm32f4xx"
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
- `<sections>` (required): configuration schema — sections, properties, layouts, and images.

`<sections>` MUST contain at least one `<section>`.

The XSD schema (`chibiforge_schema.xsd`) defines the exact structure and is authoritative for `component/schema.xml`, including the XML namespace used by the document.

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

The configuration schema defines the structure of configurable content using sections, properties, layouts, and images. Images may appear directly in sections or inside layouts.

### 6.1 Sections

`<sections>` contains one or more `<section>` elements. Each section is a collapsible group of content.

```xml
<sections>
  <section name="Initialization Settings" expanded="false" editable="true" visible="true">
    <description>Core initialization parameters.</description>
    <property name="do_not_init" type="bool" ... />
    <property name="vdd" type="int" ... />
    <image file="rsc/block_diagram.png" align="center">
      <text>Peripheral block diagram</text>
    </image>
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
- `editable`: required by the XSD. Tools are expected to use `"true"`, `"false"`, or `"@cond:<xpath>"`.
- `visible`: required by the XSD. Tools are expected to use `"true"`, `"false"`, or `"@cond:<xpath>"`.

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
- `editable`: required by the XSD. Tools are expected to use `"true"`, `"false"`, or `"@cond:<xpath>"`. This controls whether the field can be edited by the user. When using `@cond:`, the XPath expression is evaluated against the live data model to dynamically control editability.
- `visible`: required by the XSD. Tools are expected to use `"true"`, `"false"`, or `"@cond:<xpath>"`. This controls whether the field is shown in the GUI. When using `@cond:`, the XPath expression is evaluated against the live data model to dynamically control visibility.
- `default`: default value.

Property types are limited to the following set:

- `bool`
- `string`
- `text`
- `int`
- `enum`
- `list`

No additional property types are supported in v1.

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
- **`list`**: `list_columns` — CSV of property names to show in the table view, optionally followed by `:` and column width in pixels (e.g., `"name:150,mode:100,speed"`). For `type="list"`, the nested `<sections>` element defines the schema of each list item.

The XSD permits a nested `<sections>` element on any `<property>`. In v12, tool-defined semantics exist only for `type="list"`; for other property types, nested `<sections>` content is outside the defined behavior of this specification.

Property types and their GUI widgets:

- `bool`: checkbox or toggle switch.
- `string`: single-line editable text field. Validate against `string_regex` if present.
- `text`: multi-line text box (monospaced, optional language highlighting). Enforce `text_maxsize` if present.
- `int`: editable text field with numeric validation. Enforce `int_min` / `int_max`.
- `enum`: combo box (dropdown) populated from `enum_of` (CSV or resolved `@ref:`).
- `list`: table view with drill-down editing (see §6.6).

### 6.2.1 Visibility and Editability

The `visible` and `editable` attributes control rendering and interaction. The XSD requires these attributes to be present on sections and properties.

Tool-defined values are:

- `"true"`
- `"false"`
- `"@cond:<xpath>"`

Other lexical values may satisfy the XSD's presence requirement, but their behavior is not defined by this specification.

When using `@cond:`, the XPath expression is evaluated against the live data model.

#### Effective State

Visibility and editability are evaluated hierarchically.

- `effectiveVisible = parentEffectiveVisible AND ownVisible`
- `effectiveEditable = parentEffectiveEditable AND ownEditable`

Consequently:

- If a section is effectively not visible, all descendant elements are effectively not visible, regardless of their own `visible` values.
- If a section is effectively not editable, all descendant elements are effectively not editable, regardless of their own `editable` values.

#### Evaluation Optimization

If an element is effectively not visible or not editable due to an ancestor, tools MAY skip evaluation of `@cond:` expressions within that subtree, as the effective state is already determined.

Skipping evaluation is an optimization and must not affect observable behavior.

### 6.2.2 Text Property Serialization

Properties of `type="text"` are intended to contain source code or other free-form text that may include XML-sensitive characters (such as `<`, `>`, and `&`).

When reading `chibiforge.xcfg`, tools MUST accept both XML CDATA sections and normal escaped character data as equivalent representations.

When writing `chibiforge.xcfg`, tools MUST serialize values of `type="text"` using a single XML CDATA section.

Tools SHOULD emit a single CDATA section per property value and avoid splitting content into multiple CDATA blocks.

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
- `<empty>`: a visual placeholder slot (for alignment purposes). The XSD models this as a string-valued element; tools SHOULD treat any text content as insignificant and render it as an empty slot.

Constraints:

- `<layout>` may contain only `<property>`, `<image>`, and `<empty>` elements.
- Nested `<layout>` elements are not allowed.
- `<section>` elements are not allowed inside `<layout>`.

### 6.5 Images

Images may appear inside sections or inside layouts.

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
- **Drill-down**: double-clicking a row opens the item's configuration form, driven by the nested `<sections>` within the list property. For `type="list"`, the nested `<sections>` element defines the schema of each list item.
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
    <component id="org.chibios.chibiforge.components.hal.stm32f4xx">
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
<component id="org.chibios.chibiforge.components.hal.stm32f4xx">
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
- Example: component `org.chibios.chibiforge.components.hal.stm32f4xx` with `source/hal_ll.c` produces:

  ```text
  generated/org_chibios_chibiforge_components_hal_stm32f4xx/hal_ll.c
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

#### 8.2.4 FMPP integration details

The generator uses FMPP's `Engine` class directly (not `fmpp.Settings`), giving full control over source/output roots, data injection, and extension handling. FreeMarker is the freemarker-codegen fork (`org.freemarker:freemarker:2.3.35`) which adds code-first mode (`.ftlc`).

Templates in `cfg/`, `cfg_root_wa/`, and `cfg_root_wo/` should **not** use `pp.dropOutputFile` + `pp.changeOutputFile` for their primary output — the convention-based directory routing handles this automatically. These FMPP directives remain available for edge cases (e.g., a single template producing multiple output files).

The output boundary constraint is part of the contract, but explicit redirection via FMPP remains an author responsibility. FMPP's `pp.changeOutputFile` operates relative to the output root, which is `generated/` for `cfg/` templates and the configuration root for `cfg_root_wa/`/`cfg_root_wo/` templates. Current implementations do not provide complete runtime enforcement of the boundary.

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

The generator builds a data model that is passed to FMPP as top-level variables. Each component's templates are processed with a data model scoped to that component. The same data model structure is used by tools for live `@ref:` and `@cond:` resolution.

For v12, the template data model is intentionally simplified:

- Templates operate only on resolved configuration data and component-local resources.
- Target selection is resolved before template execution and is not visible to templates.
- Filesystem and execution-context concerns are handled by the generator, not by templates.
- The `configuration` variable is removed from the template context.

This change enforces the rule:

> Templates operate only on configuration data, not on generation context.

### 9.1 Top-level FMPP variables

When processing templates for a given component, the following top-level variables are available:

- **`doc`**: the current component's resolved configuration — scoped to just this component's sections and properties, with multi-target values resolved for the active target.
- **`components`**: all components' resolved configurations, keyed by normalized component ID. Used for cross-component access when needed.
- **One variable per resource**: each `<resource>` declared in the current component's `schema.xml` becomes a top-level variable using its `id` as the name.

No other top-level execution-context variables are exposed.

### 9.2 The `doc` variable

`doc` contains the current component's resolved configuration as an XML tree. Multi-target properties are resolved for the active target — templates see only single values. The structure directly reflects the component's sections and properties:

```xml
<!-- Accessed as: doc (for component org.chibios.chibiforge.components.hal.stm32f4xx) -->
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

The value exposed in `doc` is always the **effective value** for the active target.

Templates MUST NOT assume that a property was originally single-target or multi-target. That distinction is resolved before the data model is built.

### 9.3 The `components` variable

Provides all components' resolved configurations for cross-component access:

```xml
<!-- Accessed as: components -->
<components>
  <org_chibios_chibiforge_components_hal_stm32f4xx>
    <initialization_settings>
      <vdd>330</vdd>
      ...
    </initialization_settings>
  </org_chibios_chibiforge_components_hal_stm32f4xx>
  <org_chibios_chibiforge_components_board_stm32f4xx>
    <board_settings>
      ...
    </board_settings>
  </org_chibios_chibiforge_components_board_stm32f4xx>
</components>
```

Template access:

```
// Access another component's configuration
emit components.org_chibios_chibiforge_components_board_stm32f4xx.board_settings.board_name
```

`components` exposes only resolved configuration values. It does not expose target declarations, raw target-specific overrides, or any generation-context metadata.

### 9.4 Resource variables

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

### 9.5 Removed `configuration` variable

For v12, the `configuration` variable SHALL NOT be exposed to templates.

Consequences:

- Templates SHALL NOT reference project root paths.
- Templates SHALL NOT reference generated output paths.
- Templates SHALL NOT reference target information.
- Templates SHALL NOT perform logic based on execution environment.

Any existing template usage of:

```ftl
${configuration.*}
```

is invalid in v12.

Migration strategy:

- Remove all references to `configuration`.
- Move path-related logic into the generator.
- Ensure templates rely only on configuration data and resources.

### 9.6 Determinism guarantee

With the v12 data model:

- template output depends only on resolved configuration data and resources
- generation is independent of execution environment
- builds are reproducible given identical inputs

This change affects the generator/template boundary only. It does not affect the structure of `schema.xml`, the `chibiforge.xcfg` format, or component packaging.


## 10. Generator Engine (Logical Behavior)

The generator engine (used by CLI and other tools) performs:

1. **Load configuration**:
   - From `<configurationRoot>/chibiforge.xcfg` (or override path).
   - Validate structure and required component references.

2. **Resolve target**:
   - Determine the active target (for example from CLI `--target`; defaults to `"default"`).
   - Target selection is a caller concern and is resolved before template execution.

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
        - Resource variables = this component's parsed resources.
        - Resolve `@ref:` expressions in schema property attributes.

     c. **Process static payload**:
        - Copy files from `source/`, `source_root_wa/`, and `source_root_wo/` per conventions (see §8.1).

     d. **Process templates via FMPP**:
        - Process templates from `cfg/` with `generated/` as output directory.
        - Process templates from `cfg_root_wa/` with configuration root as output directory (always overwrite).
        - Process templates from `cfg_root_wo/` with configuration root as output directory (skip if target exists).
       - Route outputs per the configured conventions; explicit template redirections remain subject to the configuration-root boundary contract.

7. **Logging**:
   - Log all actions.
   - Support `--dry-run` and `--verbose`.

### 10.1 Resolved-value rule

The generator SHALL provide templates only with effective values for the active target.

Templates NEVER see:

- `<target>` declarations
- `<targetValue>` elements
- `default` attributes used for target fallback
- execution-context variables such as `configuration`

All such concerns are resolved before template execution.

### 10.2 Filesystem responsibility

All filesystem-related concerns are handled by the generator:

- output paths
- file placement
- directory structure
- write policy (`always` vs `once`)

Templates produce content only and SHALL NOT depend on filesystem layout.

### 10.3 Deterministic execution

Given identical inputs:

- `chibiforge.xcfg`
- component containers
- selected target

the generator SHALL produce identical outputs.

No template output may depend on the process working directory, host-specific paths, or other implicit environment state.


## 11. Presets

A **preset** is a predefined configuration fragment for a single component.

Presets are intended for initialization, reuse, and sharing of component-specific configuration data. They do not introduce inheritance, linking, or dependency semantics. After application, the resulting configuration is fully editable and contains no persistent reference to the preset source.

### 11.1 Preset concept

Presets:

- are scoped to a specific component ID
- do not participate in dependency resolution
- do not create persistent references in the configuration
- are applied only through explicit user or tool actions

Components MAY include bundled presets under:

`component/presets/`

Bundled presets:

- are read-only
- are associated with the owning component

Tools SHALL also support loading presets from arbitrary filesystem locations.

External presets:

- are user-managed files
- are not part of component containers

### 11.2 Preset file requirements

For preset files, the authority order is:

1. `cli/src/main/resources/schemas/chibiforge_preset.xsd`
2. this specification's preset semantics in this section

If these differ, the XSD governs the XML structure and this section governs application semantics.

A preset file MUST:

- conform to `cli/src/main/resources/schemas/chibiforge_preset.xsd`
- declare the target component ID in the root `preset @id` attribute
- match the target component before application

A preset file SHALL contain:

- a root `<preset>` element with required attributes:
  - `name`
  - `id`
  - `version`
- a `<sections>` payload matching the preset XSD structure
- for `<property type="list">`, an `<items>` payload containing zero or more `<item>` elements
- each list `<item>` SHALL contain one or more `<sections>` wrappers, each containing schema-named `<section>` / `<property>` entries
- no target declarations
- no feature definitions

### 11.3 Preset application model

Preset application semantics in v12 use a **permissive patch model**.

Applying a preset SHALL:

- update only the `<property>` values it explicitly defines
- leave all other component values unchanged
- NOT modify the preset file
- NOT create any persistent link to the preset

No implicit reset or full replacement occurs for scalar properties that are omitted from the preset.

### 11.4 Component matching

A preset MUST declare the component ID in the root `preset @id` attribute.

- The value MUST match the target `<component>@id`.
- If the preset `@id` does not match, preset application SHALL fail.
- No partial application SHALL occur in case of mismatch.

### 11.5 Property identification and schema paths

Preset values SHALL be matched using the **schema path** of each `<property>`.

A schema path is defined as:

- the hierarchy of enclosing `<section @name>` elements
- followed by the terminal `<property @name>`

Preset files SHALL use the original `<section @name>` and `<property @name>` values as defined in the component schema.

### 11.6 Path normalization and matching

Matching SHALL be performed using **normalized schema paths**.

- Each `<section @name>` and `<property @name>` SHALL be normalized using the standard ChibiForge identifier normalization rule defined in §2.1.
- The normalized elements SHALL be joined using `/` to form the schema path.

Example:

```text
Initialization Settings / System clock source
→ initialization_settings/system_clock_source
```

Normalization is applied only for matching and SHALL NOT modify the preset file.

### 11.7 Property application semantics

Preset application proceeds per schema path.

For each `<property>` defined in the preset:

- If a `<property>` with the same normalized schema path exists in the target component schema, its value SHALL be applied.
- If no matching schema path exists, the preset entry SHALL be ignored.

For `<property>` elements defined in the component schema but not present in the preset:

- The existing value SHALL remain unchanged.

This rule intentionally tolerates schema evolution and enables partial presets.

### 11.8 List property semantics

For `<property type="list">`:

- Matching is performed using the normalized schema path of the list property.
- If the property is present in the preset, the entire list SHALL be replaced.
- No element-by-element merge SHALL be performed.
- If the property is not present in the preset, the existing list SHALL remain unchanged.
- Preset list values SHALL use the structured XSD form:
  - `<items>`
  - `<item>`
  - `<sections>`
  - nested schema-named `<section>` / `<property>` entries
- Within each replacement item, nested `<section @name>` and `<property @name>` matching SHALL follow the list property's nested schema.
- Unspecified nested item properties SHALL be initialized using the component schema default values.

### 11.9 Target model interaction

Presets are target-local snapshots.

Presets:

- SHALL NOT define or modify project targets
- SHALL be applied to the currently selected target context

For each matched scalar `<property>`:

- If a `<targetValue target="...">` exists for the active target, it SHALL be replaced.
- Otherwise, the `default` attribute SHALL be replaced.

Tools MAY provide a mechanism to create explicit target values for multi-target properties that currently inherit from `default`.

When enabled:

- it applies only if the active target ≠ `default`
- if no explicit target value exists:
  - a target-specific value SHALL be created
  - the preset value SHALL be written to that target

Existing target values SHALL always be replaced.

Current implementation note:

- target-local replacement is implemented for scalar properties
- `<property type="list">` replacement currently applies only to single-target list values
- multi-target list replacement is not yet supported because the `.xcfg` format does not currently define a structured target-specific encoding for list items analogous to scalar `<targetValue>` entries

### 11.10 Schema evolution behavior

Preset application SHALL be tolerant to schema changes.

If a schema path defined in the preset no longer exists in the current component schema due to:

- renamed `<section>` elements
- inserted or removed sections
- renamed or removed `<property>` elements

then:

- the preset entry SHALL be ignored
- a warning SHALL be logged

### 11.11 Error conditions

The following SHALL be treated as errors:

- preset `@id` mismatch with `<component>@id`
- malformed preset XML
- invalid value type for a `<property>` (violates schema constraints)

In such cases, preset application SHALL abort.

### 11.12 Logging requirements

Preset application SHALL produce structured logs.

#### 11.12.1 Ignored properties

If a preset `<property>` does not match any schema path:

- The property SHALL be ignored.
- A warning SHALL be logged.

Example:

```
[Preset] Ignored property 'Initialization Settings / PLL old mode' — path not present in component schema
```

#### 11.12.2 Unmodified properties

If a schema-defined `<property>` is not present in the preset:

- Its value SHALL remain unchanged.
- An informational log entry MAY be emitted.

Example:

```
[Preset] Property 'DMA Settings / Channels' not defined in preset — existing value retained
```

#### 11.12.3 Summary

Tools SHOULD emit a summary after preset application:

```
Preset applied:
- 12 properties updated
- 2 properties ignored
- 5 properties unchanged
```

### 11.13 Preset export

Tools SHALL support exporting presets.

Exporting a preset SHALL:

- serialize the current component configuration into the preset XSD format
- produce a standalone preset file

The exported preset SHALL reflect:

- the currently selected target
- the effective values for that target

The preset SHALL be fully materialized:

- inherited values MUST be resolved
- no target structures SHALL be included
- the exported root `preset @id` SHALL equal the component ID

### 11.14 Board component model revision

The board component model is revised as follows:

- one board component per MCU family
- commercial boards are represented as presets

Example:

Instead of:

- `board.nucleo_g474re`
- `board.stm32g4discovery`

Use:

- `board.stm32g4xx`

  - presets:
    - `nucleo_g474re`
    - `stm32g4discovery`

Implications:

- board generation logic remains in the component
- board-specific data moves to presets
- component count is reduced
- maintenance complexity is reduced

### 11.15 Backward compatibility

Existing configurations:

- remain valid
- continue to function without presets

Existing board components:

- MAY be retained temporarily
- SHOULD be migrated to the preset-based model

### 11.16 Still deferred

The following preset-related items remain deferred beyond the current v12 contract:

- preset metadata embedded in `component/schema.xml`
- automated migration of legacy board components into the preset-based model


## 12. Tool Integration

ChibiForge is designed to be used from multiple front-ends. The generator engine and component model are independent of any specific tool. User-interface behavior is specified separately; this section is limited to integration and packaging concerns.

### 12.1 Possible front-ends

- **CLI**: standalone command-line tool (see Addendum A).
- **Standalone GUI**: dedicated desktop application built with **JavaFX** (OpenJDK + OpenJFX). Registered as the handler for `.xcfg` files — double-clicking opens the editor. Packaged as a self-contained application via `jpackage` (bundled JRE, no Java installation required).
- **Eclipse**: plugin-based integration using the ChibiForge component extension point.
- **VS Code**: extension with webview-based configuration UI (future).
- **Web UI**: browser-based configuration editor (future).


### 12.3 Eclipse integration

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

### 12.4 Non-Eclipse tools and plugin JARs

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

- `--config`: path to the configuration file. Accepts any filename, not just `chibiforge.xcfg` — the engine uses the exact path provided. The directory containing this file becomes the configuration root.
- `--components`: preferred filesystem component source root (see A.3).
- `--plugins`: preferred plugin JAR source root or JAR file (see A.3).
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
- `CHIBIFORGE_COMPONENTS` → additional auto-discovered component source roots.

Resolution:

- Config path:
  - `--config` > `CHIBIFORGE_CONFIG_PATH` > `./chibiforge.xcfg` (current working directory).
- Preferred roots:
  - `--components` > `CHIBIFORGE_COMPONENTS_ROOT`
  - `--plugins` > `CHIBIFORGE_PLUGINS_ROOT`
- Auto-discovered roots (after preferred roots):
  - `chibiforge_sources.json` next to the configuration file
  - `<configurationRoot>/components`
  - `CHIBIFORGE_COMPONENTS`

Preferred roots override auto-discovered roots when duplicate component IDs are present.

If no valid component sources are resolved after applying all discovery rules, the CLI must error with a clear message.

---

## A.3 Component Sources

A component source root may be:

- a directory containing component directories
- a single filesystem component directory
- a directory containing plugin JARs
- a single plugin JAR file

The CLI and other tools resolve an ordered list of such roots, then build a single merged `ComponentRegistry`.

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

- If a source root itself contains `component/schema.xml`, it is treated as a single filesystem component container.
- Otherwise, if it is a directory, each subdirectory is checked for `component/schema.xml`.
- The directory name MUST equal the component ID.

If `component/schema.xml` is missing, the directory is not a valid component container.

### A.3.2 Plugin JAR Component Containers

Within a JAR source:

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

1. Scan JAR files under the source root, or inspect the source directly if it is already a JAR file.
2. For each JAR:
   - Open `plugin.xml`.
   - Check for `<extension point="org.chibios.chibiforge.component"/>`.
   - If present, load `component/schema.xml` to extract component metadata.
   - Validate `Bundle-SymbolicName` and JAR filename prefix against the component ID.

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

The CLI builds a **ComponentRegistry** by merging the resolved ordered source roots.

Algorithm:

1. Resolve the ordered component source roots from:
   - preferred roots (`--components`, `--plugins`, `CHIBIFORGE_COMPONENTS_ROOT`, `CHIBIFORGE_PLUGINS_ROOT`)
   - `chibiforge_sources.json`
   - `<configurationRoot>/components`
   - `CHIBIFORGE_COMPONENTS`

2. For each resolved root:
   - discover filesystem containers and/or plugin JAR containers depending on the root type

3. Merge into a map:

   ```text
   componentId → ComponentContainer
   ```

4. Conflict resolution:
   - Roots are evaluated in precedence order.
   - Earlier roots override later roots for duplicate component IDs.
   - This allows explicit or project-local overrides of shared/global component sources.

The generator then uses this registry to resolve components referenced in `chibiforge.xcfg`.

---

## A.6 Generator Pipeline (CLI Context)

The main pipeline is identical to the general spec; the CLI-specific steps are:

1. Resolve `configPath`, preferred roots, and `targetId` via CLI + env.
2. Derive configuration root from the directory containing `configPath`.
3. Resolve the ordered component source roots.
4. Build `ComponentRegistry` from those roots.
5. Load the configuration file from `configPath`.
6. Resolve the active target (from `--target` or default).
7. For each `<component id="X">` in configuration:
   - Look up `X` in the registry; error if not present.
   - Call `loadDefinition()` to parse `component/schema.xml`.
8. Check feature dependencies; emit warnings as needed.
9. Build resolved configurations for all components (resolve multi-target properties).
10. For each component:
   - Load resources, build scoped data model, process static payload, process templates (cfg/, cfg_root_wa/, cfg_root_wo/) via FMPP.
11. Log all actions (respecting `--dry-run`, `--verbose`).

No Eclipse or OSGi APIs are used; all operations are filesystem/JAR-based.

---

## A.7 Implementation Phasing

Recommended phases:

This functionality is already implemented in the current toolchain:

- ordered source-root resolution
- filesystem and JAR containers
- registry merging with precedence-based overrides
- target selection
- shared engine reuse between CLI and UI

---

## A.8 Build and Dependencies

**Build system**: Maven. The CLI is a Maven module (`cli/`). The UI (when implemented) will be a sibling Maven module alongside `cli/`, sharing the same parent POM.

**Dependencies**: all FMPP and freemarker-codegen JARs are vendored in `cli/lib/` as a Maven file-based repository. The build is fully self-contained — no external setup or `publishToMavenLocal` step needed.

**Build output**: the fat JAR (`chibiforge.jar`) and wrapper script are placed in a top-level `bin/` directory, parallel to `cli/`. This directory will also hold future UI binaries.

**Engine reuse**: the UI module depends on the CLI module as a library for the generator engine, component loading, data model building, etc. There is no separate `engine/` package in the UI — the CLI module provides these services.
