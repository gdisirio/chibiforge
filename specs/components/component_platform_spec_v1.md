# ChibiOS Platform Component — Specification v1

## 1. Purpose

A **platform component** is the single mandatory component in any ChibiForge configuration. It establishes the complete toolchain and MCU foundation for the project: startup code, linker scripts, top-level build file, an initial `main.c`, and the CMSIS (or equivalent) device headers. Every other component in the configuration builds on top of what the platform component provides.

---

## 2. Role and Responsibilities

A platform component is solely responsible for the following outputs:

### 2.1 Startup code

The CPU startup sequence for the target MCU family. This includes (but is not limited to):

- Vector table definition.
- Reset handler (`Reset_Handler`): initialise `.data`, zero `.bss`, call `SystemInit`, branch to `main`.
- Default and weak exception/IRQ handler stubs.
- Architecture-specific early-boot code (FPU enable, cache enable on Cortex-M7/M33, etc.).

These are delivered as static source files via `source/` or as generated files via `cfg/`, depending on whether any user configuration drives their content.

### 2.2 Linker script

The memory layout descriptor for the target device. Covers:

- Flash and RAM region definitions (origin and length).
- Standard ELF section placement (`.text`, `.rodata`, `.data`, `.bss`, `.heap`, `.stack`).
- Any MCU-specific memory regions (CCMRAM, DTCM, ITCM, backup SRAM, etc.).

The linker script is delivered to the **configuration root** (not to `generated/`) via `cfg_root_wa/` or `cfg_root_wo/`, since it is a file the build system references directly by path.

### 2.3 Top-level build file

A top-level `Makefile` (or equivalent build system file) that:

- Specifies the toolchain (compiler, linker, flags).
- Sets architecture-specific compile and link flags (e.g. `-mcpu=cortex-m4 -mfpu=fpv4-sp-d16 -mfloat-abi=hard`).
- Defines the include paths (system headers, CMSIS headers, `generated/` subtrees).
- Provides standard targets: `all`, `clean`, `flash` (optional).

This file is delivered to the configuration root via `cfg_root_wo/` (write-once, user may customise after initial generation).

### 2.4 Initial `main.c`

A minimal, compilable `main.c` scaffold delivered write-once (`cfg_root_wo/` or `_root_wo/`). It serves as the entry point the startup code calls. The intent is "project bootstrap" — the user is expected to modify it freely after generation. The platform component generates it exactly once and never overwrites it.

### 2.5 CMSIS / device headers

The platform component ships CMSIS-Core headers (or the equivalent for non-ARM architectures) and the device-specific peripheral register definition headers for its MCU family. These are delivered as static files via `source/` into `generated/<normalised_platform_id>/include/` (or similar), and that path is added to the include path in the generated build file.

Other components in the configuration may `#include` CMSIS and device headers without declaring an explicit dependency on them — they are unconditionally available once a platform component is present.

---

## 3. Feature Contract

### 3.1 Provided features

Every platform component MUST provide:

```xml
<provides>
  <feature id="features.platform" exclusive="true"/>
  <feature id="features.platform.arch.<arch>" exclusive="true"/>
  <feature id="features.platform.family.<family>" exclusive="true"/>
</provides>
```

| Feature ID | Meaning | `exclusive` |
|---|---|---|
| `features.platform` | A platform is present | `true` — at most one platform per configuration |
| `features.platform.arch.<arch>` | CPU architecture | `true` — one architecture per configuration |
| `features.platform.family.<family>` | MCU family | `true` — one device family per configuration |

The `<arch>` and `<family>` tokens in the feature IDs are **fixed, normalised strings** defined per component (see §4). They are not user-configurable.

### 3.2 Architecture and family as feature IDs

The architecture and device family are expressed as **feature IDs, not as free-form property values**. This keeps the dependency system uniform: a component requiring a specific architecture or family uses exactly the same `<requires>` mechanism as a component requiring any other capability.

A component that requires `features.platform.arch.armv7em` or `features.platform.family.stm32g4xx` **does not know, and must not assume, which component provides those features**. The ChibiForge dependency resolver matches provided feature IDs against required feature IDs at configuration-validation time. The requiring component is decoupled from the providing component by design — today `org.chibios.chibiforge.components.platform.stm32g4xx` provides `features.platform.family.stm32g4xx`; a third-party platform component for the same family could provide the same feature ID and satisfy the same dependency transparently.

This has two practical consequences:

1. **Dependency declarations are portable.** An STM32G4xx-specific HAL peripheral component declares `features.platform.family.stm32g4xx` in its `<requires>`. It works identically regardless of which platform component in the registry provides that feature.

2. **The ChibiForge warning system catches mismatches automatically.** If a component requiring `features.platform.arch.armv7em` is added to a configuration whose platform only provides `features.platform.arch.armv6m`, a conflict warning is emitted at generation time — no component-specific validation code is needed.

Architecture and family strings are **not available as data model variables** through this mechanism — they are feature IDs only. If a template needs to branch on the architecture at generation time, the platform component must also expose an appropriate read-only property in its `<sections>` (e.g. an `enum` property `architecture` with value `"armv7-m"`, `editable="false"`).

### 3.3 Required features

A platform component has **no required features** (`<requires>` is empty). The platform is the foundation; it depends on nothing else in the configuration.

```xml
<requires>
  <!-- empty — the platform has no dependencies on other components -->
</requires>
```

---

## 4. Architecture and Family Token Registry

The following tokens are defined for use in `features.platform.arch.*` and `features.platform.family.*` feature IDs.

### 4.1 Architecture tokens

| Token | Architecture | Typical CPUs |
|---|---|---|
| `armv6m` | ARMv6-M | Cortex-M0, Cortex-M0+ |
| `armv7m` | ARMv7-M | Cortex-M3 |
| `armv7em` | ARMv7E-M | Cortex-M4, Cortex-M7 |
| `armv8m_base` | ARMv8-M Baseline | Cortex-M23 |
| `armv8m_main` | ARMv8-M Mainline | Cortex-M33, Cortex-M35P |
| `armv8_1m_main` | ARMv8.1-M Mainline | Cortex-M55, Cortex-M85 |
| `riscv32` | RISC-V 32-bit | (reserved) |
| `riscv64` | RISC-V 64-bit | (reserved) |

New architecture tokens require a revision of this document.

### 4.2 Family tokens

Family tokens mirror the MCU family naming conventions used by the silicon vendor, lowercased and with non-alphanumeric characters replaced per the ChibiForge identifier normalisation rule.

**STMicroelectronics:**

| Token | MCU Family |
|---|---|
| `stm32f0xx` | STM32F0 series |
| `stm32f1xx` | STM32F1 series |
| `stm32f3xx` | STM32F3 series |
| `stm32f4xx` | STM32F4 series |
| `stm32f7xx` | STM32F7 series |
| `stm32h7xx` | STM32H7 series |
| `stm32l0xx` | STM32L0 series |
| `stm32l4xx` | STM32L4 series |
| `stm32g0xx` | STM32G0 series |
| `stm32g4xx` | STM32G4 series |
| `stm32u5xx` | STM32U5 series |

**Raspberry Pi / RP:**

| Token | MCU Family |
|---|---|
| `rp2040` | RP2040 |
| `rp2350` | RP2350 (reserved) |

New family tokens are added as new platform components are authored.

---

## 5. Component Metadata Rules

### 5.1 `is_platform`

Platform components MUST set `is_platform="true"`. This flag signals to the GUI that the component is a platform and enables platform-specific palette behaviour (at most one, displayed prominently).

### 5.2 `hidden`

Platform components MUST set `hidden="false"`. They are user-facing: the user selects one platform from the palette as their first action when setting up a new configuration.

### 5.3 Component ID pattern

Platform component IDs follow the pattern:

```
org.chibios.chibiforge.components.platform.<family>
```

Examples:

| Component ID | Target Family |
|---|---|
| `org.chibios.chibiforge.components.platform.stm32g4xx` | STM32G4xx |
| `org.chibios.chibiforge.components.platform.stm32h7xx` | STM32H7xx |
| `org.chibios.chibiforge.components.platform.stm32f4xx` | STM32F4xx |
| `org.chibios.chibiforge.components.platform.rp2040` | RP2040 |

Note: the `hal.platform.*` namespace used in the earlier component spec draft is superseded by this top-level `platform.*` namespace, reflecting that the platform concept is broader than HAL — it covers the build system, startup, and linker as well.

### 5.4 Category

All platform components MUST use the category `Platform/<VendorFamily>`:

| Category path | Used by |
|---|---|
| `Platform/STM32` | All STM32 platform components |
| `Platform/RP` | All RP-series platform components |

### 5.5 `schema.xml` `<provides>` example

A complete, correct `<provides>` block for `org.chibios.chibiforge.components.platform.stm32g4xx`:

```xml
<provides>
  <feature id="features.platform" exclusive="true"/>
  <feature id="features.platform.arch.armv7em" exclusive="true"/>
  <feature id="features.platform.family.stm32g4xx" exclusive="true"/>
</provides>
```

---

## 6. Container Layout

A platform component container follows the standard ChibiForge container layout with the following conventions:

```
component/
  schema.xml

  cfg/                        # Templates for generated/ outputs
    system_stm32g4xx.h.ftlc   # Clock/system config header (user-configurable content)

  cfg_root_wa/                # Templates -> config root, always overwritten
    (none typical)

  cfg_root_wo/                # Templates -> config root, write-once
    Makefile.ftlc             # Top-level build file
    src/main.c.ftlc           # Initial main.c scaffold

  source/                     # Static files -> generated/<normalised_id>/
    startup/
      startup_stm32g4xx.s     # Startup assembly
    linker/
      stm32g4xx_flash.ld      # Linker script
    cmsis/
      core_cm4.h              # CMSIS-Core headers
      cmsis_gcc.h
      ...
    device/
      stm32g4xx.h             # Device peripheral register definitions
      stm32g474xx.h
      ...

  _root_wo/             # Static files -> config root, write-once
    (none typical)

  resources/                  # Resource XML/JSON used in schema and templates
    device_catalog.xml        # Device variant list (used in schema enum_of)
    memory_map.xml            # Flash/RAM sizes per device variant
```

Key conventions:

- **Linker scripts** are placed under `source/linker/` as static files when they are fixed per-family. If the user must select a device variant (affecting flash/RAM sizes), the linker script is a template under `cfg_root_wo/` that uses a `@ref:` to the selected device's memory map.
- **CMSIS headers** are always static (`source/`). They are never templated.
- **The device catalog** (`resources/device_catalog.xml`) drives the device-variant property in the component's configuration schema, allowing the user to select the exact device (e.g. `STM32G474VE`). The generator uses this selection to emit correct linker region sizes and compile-time defines.
- **`main.c`** is write-once (`cfg_root_wo/` or `_root_wo/`). The platform generates it once as a bootstrap scaffold; it is the user's file thereafter.

---

## 7. Interaction with Other Components

### 7.1 How other components declare a platform dependency

Components declare platform dependencies using feature IDs in `<requires>`. They never reference a platform component by its `id` — only by the features it provides. The component author does not need to know (and must not hardcode) which platform component satisfies the dependency.

**Dependency on any platform** — the component works on any architecture and any family:

```xml
<requires>
  <feature id="features.platform"/>
</requires>
```

**Dependency on a specific architecture** — the component requires ARMv7E-M (e.g. it uses DSP intrinsics), but works on any STM32 or any other family on that architecture:

```xml
<requires>
  <feature id="features.platform"/>
  <feature id="features.platform.arch.armv7em"/>
</requires>
```

**Dependency on a specific MCU family** — the component uses STM32G4xx-specific peripheral registers, but does not care which component provides the platform as long as it is STM32G4xx:

```xml
<requires>
  <feature id="features.platform"/>
  <feature id="features.platform.family.stm32g4xx"/>
</requires>
```

All of these are **soft dependencies**: the ChibiForge resolver emits a warning at generation time if the required feature is not satisfied by any component in the configuration. Generation is not blocked. The warning is the signal to the user that the configuration is likely incorrect.

### 7.2 Include path assumption

Components may include CMSIS and device headers unconditionally, without any `#ifdef` guard or path workaround, as long as a platform component is in the configuration. The platform's generated build file ensures the correct include paths are set globally. This is a **convention**, not a mechanism enforced by ChibiForge tooling.

### 7.3 No circular dependencies

Platform components must not require any feature provided by non-platform components. The platform is the root of the dependency graph and must remain unconditionally usable.

---

## 8. Open Items

1. **Device variant sub-selection**: some MCU families have dozens of variants differing in flash size, pin count, and peripheral availability. The exact mechanism for device sub-selection (a required `enum` property in the schema vs. a sub-component per variant) is not yet resolved. The `resources/device_catalog.xml` + `enum` property approach is preferred but needs a worked example.

2. **Multi-core platforms**: platforms with dual-core MCUs (e.g. STM32H7 with M7+M4, RP2350 with dual M33) may require either two separate platform components (one per core) or a single platform component with a core-selector property. This is deferred — all initial platform components target single-core configurations.

3. **Linker script variants**: some projects need multiple linker scripts (e.g. flash execution vs. RAM execution, bootloader vs. application). Whether these are modelled as multiple write-once outputs from a single platform component or as separate components is an open design question.

4. **Non-Makefile build systems**: the current spec assumes a `Makefile` as the top-level build file. CMake and Cargo support are noted as future work. The platform component structure is designed so that the build file template is the only part that changes — startup, linker, headers, and CMSIS remain the same.
