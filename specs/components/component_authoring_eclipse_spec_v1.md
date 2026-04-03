# ChibiForge Component Authoring Rules — Eclipse Plugin Packaging

## 1. Purpose

This document specifies the mandatory **Eclipse plugin packaging rules** that apply to all ChibiForge components, regardless of their layer or subsystem. These rules ensure that:

- Every component is a valid Eclipse PDE plugin project that can be imported and developed in Eclipse.
- Every component is a valid ChibiForge component container that can be consumed by the CLI, standalone GUI, and any other ChibiForge-compatible tool.
- The Eclipse plugin JAR is the canonical distribution format for all components.

These rules complement the ChibiForge Specification v9 (§4, component containers) and apply to all components specified in the ChibiOS component ecosystem.

---

## 2. Universal Rule

**Every ChibiForge component MUST be packaged as an Eclipse plugin.**

This applies without exception to:

- All ChibiOS-authored components (platform, chport, kernel, HAL, board, OSLIB, SB, VFS, drivers, services).
- All third-party components integrating with the ChibiOS ecosystem.
- All community-contributed components.

The Eclipse plugin format is the **portable container format** for ChibiForge components. Non-Eclipse tools (CLI, standalone GUI, VS Code extensions) consume plugin JARs directly without any dependency on Eclipse or OSGi — they read `plugin.xml` as a marker and extract the `component/` subtree. Eclipse uses the same JARs natively through its PDE plugin infrastructure.

---

## 3. Naming Convention

### 3.1 Component ID and plugin name prefix

All ChibiOS-authored ChibiForge components MUST use the following prefix for both the component ID (in `component/schema.xml`) and the Eclipse plugin symbolic name (in `META-INF/MANIFEST.MF`):

```
org.chibios.chibiforge.components.
```

The full component ID pattern is therefore:

```
org.chibios.chibiforge.components.<subsystem>[.<sub-subsystem>][.<variant>]
```

Examples:

| Component | Full ID |
|---|---|
| Platform STM32G4xx | `org.chibios.chibiforge.components.platform.stm32g4xx` |
| chport ARMv7E-M | `org.chibios.chibiforge.components.chport.armv7em` |
| Kernel RT | `org.chibios.chibiforge.components.kernel.rt` |
| Kernel NIL | `org.chibios.chibiforge.components.kernel.nil` |
| HAL OSAL | `org.chibios.chibiforge.components.hal.osal` |
| HAL Core | `org.chibios.chibiforge.components.hal.core` |
| chhalport STM32G4xx | `org.chibios.chibiforge.components.chhalport.stm32g4xx` |
| Board Nucleo-G474RE | `org.chibios.chibiforge.components.board.nucleo_g474re` |
| OSLIB | `org.chibios.chibiforge.components.oslib` |
| HAL SPI driver | `org.chibios.chibiforge.components.hal.spi` |
| lwIP | `org.chibios.chibiforge.components.lwip` |
| VFS Core | `org.chibios.chibiforge.components.vfs.core` |

### 3.2 Third-party components

Third-party and community components MUST NOT use the `org.chibios.chibiforge.components.` prefix. They use their own organisation namespace:

```
<org>.<project>.chibiforge.components.<subsystem>.<name>
```

Example: `com.example.chibiforge.components.mydriver`

### 3.3 Propagation to other specs

The naming convention in §3.1 supersedes the shorter `org.chibios.*` component IDs used in earlier component specs (platform, kernel, chport, HAL, board, OSLIB). Those specs will be updated to use the full `org.chibios.chibiforge.components.*` prefix. The feature ID namespace (e.g. `features.platform`, `features.kernel`) is unaffected — feature IDs are not plugin names and retain their shorter form.

---

## 4. Required Files

Every component project MUST contain the following files, in addition to the `component/` subtree defined by the ChibiForge container layout:

### 3.1 `plugin.xml`

Declares the project as a ChibiForge component container. Must contain the ChibiForge extension point declaration:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<?eclipse version="3.4"?>
<plugin>
  <extension point="org.chibios.chibiforge.component"/>
</plugin>
```

No additional content is required or permitted in `plugin.xml`. All component metadata (ID, name, version, description, categories, features) lives exclusively in `component/schema.xml`. `plugin.xml` is a marker only.

### 3.2 `META-INF/MANIFEST.MF`

Standard OSGi bundle manifest. The bundle symbolic name MUST match the component ID:

```
Manifest-Version: 1.0
Bundle-ManifestVersion: 2
Bundle-Name: <component display name>
Bundle-SymbolicName: <component-id>;singleton:=true
Bundle-Version: <component-version>
Bundle-Vendor: <author or organisation>
```

The `Bundle-SymbolicName` MUST be identical to the `id` attribute on the root `<component>` element in `component/schema.xml`. This is the authoritative link between the OSGi identity and the ChibiForge identity.

`singleton:=true` is required on all ChibiForge component plugins to prevent multiple versions of the same component from being loaded simultaneously in an Eclipse installation.

Example for `org.chibios.chibiforge.components.hal.core`:

```
Manifest-Version: 1.0
Bundle-ManifestVersion: 2
Bundle-Name: ChibiOS HAL Core
Bundle-SymbolicName: org.chibios.chibiforge.components.hal.core;singleton:=true
Bundle-Version: 21.11.0
Bundle-Vendor: ChibiOS Project
```

### 3.3 `build.properties`

Eclipse PDE build configuration. Declares what is included in the exported JAR:

```properties
bin.includes = plugin.xml,\
               META-INF/,\
               component/,\
               rsc/
```

The `rsc/` entry is optional — include it only if the component ships UI assets (e.g. `rsc/icon.png`). The `component/` entry is always required.

### 3.4 `.project`

Eclipse project descriptor. Enables the project to be imported into Eclipse as a PDE plugin project for authoring:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<projectDescription>
  <name><component-id></name>
  <comment></comment>
  <projects/>
  <buildSpec>
    <buildCommand>
      <name>org.eclipse.pde.ManifestBuilder</name>
      <arguments/>
    </buildCommand>
    <buildCommand>
      <name>org.eclipse.pde.SchemaBuilder</name>
      <arguments/>
    </buildCommand>
  </buildSpec>
  <natures>
    <nature>org.eclipse.pde.PluginNature</nature>
  </natures>
</projectDescription>
```

The `<name>` element MUST match the `Bundle-SymbolicName` and the component `id`. This is what Eclipse displays as the project name in the Package Explorer and what it uses to resolve inter-project dependencies.

---

## 4. Complete Project Layout

A fully conformant component project has this layout:

```
org.chibios.chibiforge.components.<subsystem>.<name>/   # Project root, named after component ID
  .project                            # Eclipse project descriptor
  plugin.xml                          # ChibiForge extension point marker
  META-INF/
    MANIFEST.MF                       # OSGi bundle manifest
  build.properties                    # PDE build configuration

  component/
    schema.xml                        # ComponentDefinition (required)
    cfg/                              # FreeMarker templates → generated/
    cfg_root_wa/                      # FreeMarker templates → config root (always)
    cfg_root_wo/                      # FreeMarker templates → config root (once)
    source/                           # Static sources → generated/
    _root_wa/                         # Static payload → config root (always)
    _root_wo/                         # Static payload → config root (once)
    resources/                        # Resource files (XML, JSON)
    other/                            # Optional extras (docs, notes)

  rsc/                                # Optional UI assets
    icon.png                          # Component icon (optional)
```

Not all `component/` subdirectories need to be present — only those the component actually uses. `component/schema.xml` is always required.

---

## 5. Component ID Consistency Requirement

The component ID MUST be consistent across all four locations where it appears:

| Location | Field |
|---|---|
| Project root directory name | Directory name |
| `.project` | `<name>` element |
| `META-INF/MANIFEST.MF` | `Bundle-SymbolicName` (before `;singleton:=true`) |
| `component/schema.xml` | `id` attribute on `<component>` |

Any inconsistency is an authoring error. ChibiForge tooling uses `component/schema.xml` as the authoritative source for the component ID; Eclipse uses `MANIFEST.MF`; the directory name is convention. All four must agree.

---

## 6. Authoring Workflow in Eclipse

The recommended workflow for authoring a new component in Eclipse:

1. **Create a new plugin project** via `File → New → Plug-in Project`.
   - Set the project name to the component ID (e.g. `org.chibios.chibiforge.components.hal.spi`)..
   - Disable the "Generate an activator" and "This plug-in will make contributions to the UI" options — ChibiForge components have no Java code and no UI contributions.
   - Set version to match the target ChibiOS release.

2. **Add the ChibiForge extension point** to `plugin.xml`:
   - Open `plugin.xml` in the PDE editor.
   - Add the `org.chibios.chibiforge.component` extension point.

3. **Create the `component/` subtree**:
   - Add `component/schema.xml` with the `<component>` root element.
   - Add template directories (`cfg/`, `cfg_root_wa/`, etc.) as needed.
   - Add `component/resources/` for any XML or JSON resource files.

4. **Verify `MANIFEST.MF`**:
   - Confirm `Bundle-SymbolicName` matches the component ID in `schema.xml`.
   - Confirm `Bundle-Version` matches `version` in `schema.xml`.

5. **Update `build.properties`**:
   - Ensure `component/` is listed in `bin.includes`.
   - Add `rsc/` if an icon is present.

6. **Export the plugin**:
   - Use `File → Export → Plug-in Development → Deployable plug-ins and fragments`.
   - The exported JAR is the distributable component container, consumable by CLI, GUI, and Eclipse alike.

---

## 7. Consuming Plugin JARs Outside Eclipse

Non-Eclipse tools discover and load component plugin JARs as follows:

1. Scan the configured plugins directory for `.jar` files.
2. For each JAR, check for the presence of `plugin.xml` at the JAR root.
3. Parse `plugin.xml` and look for the `org.chibios.chibiforge.component` extension point. If absent, skip the JAR.
4. Load `component/schema.xml` from the JAR to read the component definition.
5. Load template and resource files from the JAR on demand during generation.

No OSGi runtime, no Eclipse installation, no Java plugin classloading is required. The JAR is treated as a ZIP archive.

---

## 8. Versioning

The `Bundle-Version` in `MANIFEST.MF` and the `version` in `component/schema.xml` MUST always be identical. Both follow semantic versioning (`major.minor.patch`). Keeping them in sync is an authoring discipline — there is no automated enforcement.

When a component is updated:

- **Patch increment**: backwards-compatible fix (template bug, resource data correction). No change to `<provides>`, `<requires>`, or `<sections>` structure.
- **Minor increment**: backwards-compatible addition (new optional property, new provided feature). Existing configurations remain valid.
- **Major increment**: breaking change (property removed or renamed, required feature changed, provided feature ID changed). Existing configurations using this component may need to be migrated.
