# ChibiOS chport Components — Specification v1

## 1. Purpose

A **chport component** is the architecture-specific adapter between the ChibiOS kernel and the target CPU. It provides the low-level port layer that the kernel requires — context switch code, critical section primitives, stack initialisation, and the port configuration header — without knowing anything about specific MCU peripherals or board wiring.

chport components close the dependency circle between the platform and the kernel:

```
platform  ──provides──►  features.platform.arch.<arch>
                                    │
                                    ▼
chport    ──requires──►  features.platform.arch.<arch>
          ──provides──►  features.chport
                                    │
                                    ▼
kernel    ──requires──►  features.platform
          ──requires──►  features.chport
```

The kernel component is fully architecture-agnostic. It knows only that `features.chport` is satisfied. Which architecture that port targets, and which platform component provided the architecture feature, are invisible to the kernel.

---

## 2. Role and Responsibilities

A chport component is responsible for:

### 2.1 Port configuration header

A generated header (`chportconf.h` or equivalent) placed in `generated/` that contains compile-time tuning of the port layer — stack alignment, FPU context save/restore enable, MPU configuration, Thumb/ARM mode selection, etc. This is the only generated output; all other port artefacts are static.

### 2.2 Static port sources

The architecture-specific C and assembly source files implementing the port contract expected by the ChibiOS kernel:

- Context switch implementation (`chcore.c`, `chcore_v*.s` or equivalent).
- Port header (`chcore.h`) declaring port types, stack macros, and critical section primitives.
- Any architecture-specific support files (e.g. MPU helpers, FPU save/restore stubs).

These are delivered as static files via `source/` into `generated/<normalised_chport_id>/`.

### 2.3 No startup, no linker, no build file

chport components do not own startup code, linker scripts, or build files. Those are platform responsibilities. The chport assumes the platform has already established a working toolchain environment and that the architecture-specific compiler flags (`-mcpu`, `-mfpu`, `-mfloat-abi`) are already set in the generated `Makefile`.

---

## 3. Feature Contract

### 3.1 Provided features

Every chport component MUST provide:

```xml
<provides>
  <feature id="features.chport" exclusive="true"/>
  <feature id="features.chport.arch.<arch>" exclusive="true"/>
</provides>
```

| Feature ID | Meaning | `exclusive` |
|---|---|---|
| `features.chport` | A ChibiOS port layer is present | `true` — at most one port per configuration |
| `features.chport.arch.<arch>` | The specific architecture this port implements | `true` — one port architecture per configuration |

`features.chport` is exclusive because having two port layers active simultaneously is meaningless and would produce conflicting symbol definitions at link time.

`features.chport.arch.<arch>` uses the same architecture token registry defined in the platform spec (§4.1 of the platform component spec): `armv6m`, `armv7m`, `armv7em`, `armv8m_base`, `armv8m_main`, `armv8_1m_main`, etc.

### 3.2 Required features

Every chport component MUST require the matching architecture feature from the platform:

```xml
<requires>
  <feature id="features.platform"/>
  <feature id="features.platform.arch.<arch>"/>
</requires>
```

The chport requires `features.platform` to ensure a platform is present (and therefore that startup code, linker scripts, and include paths are established before the port sources are compiled). It requires the specific `features.platform.arch.<arch>` to ensure the active platform's CPU architecture matches what this port implements.

Both are soft dependencies — a warning is emitted if either is unsatisfied, but generation is not blocked.

### 3.3 RT and NIL share the same chport

ChibiOS/RT and ChibiOS/NIL use the same port layer. A single chport component serves both kernels. The kernel component requires `features.chport`; it does not care whether `org.chibios.chibiforge.components.kernel.rt` or `org.chibios.chibiforge.components.kernel.nil` is also present — that is a separate dimension of the configuration.

The chport does not require `features.kernel` and does not require `features.chapi`. It is independent of which kernel is present.

---

## 4. Revised Kernel `<requires>`

With chport as a separate component, the kernel `<requires>` block from the kernel spec is updated:

```xml
<!-- Both RT and NIL -->
<requires>
  <feature id="features.platform"/>
  <feature id="features.chport"/>
</requires>
```

The kernel no longer requires a specific architecture feature directly — that dependency is delegated entirely to the chport component. The kernel trusts that any satisfied `features.chport` is appropriate for the active platform.

---

## 5. Component Definitions

### 5.1 chport component ID pattern

```
org.chibios.chibiforge.components.chport.<arch>
```

| Component ID | Architecture |
|---|---|
| `org.chibios.chibiforge.components.chport.armv6m` | ARMv6-M (Cortex-M0, Cortex-M0+) |
| `org.chibios.chibiforge.components.chport.armv7m` | ARMv7-M (Cortex-M3) |
| `org.chibios.chibiforge.components.chport.armv7em` | ARMv7E-M (Cortex-M4, Cortex-M7) |
| `org.chibios.chibiforge.components.chport.armv8m_base` | ARMv8-M Baseline (Cortex-M23) |
| `org.chibios.chibiforge.components.chport.armv8m_main` | ARMv8-M Mainline (Cortex-M33, Cortex-M35P) |
| `org.chibios.chibiforge.components.chport.armv8_1m_main` | ARMv8.1-M Mainline (Cortex-M55, Cortex-M85) |

### 5.2 Common metadata for all chport components

| Attribute | Value |
|---|---|
| `is_platform` | `false` |
| `hidden` | `true` |
| Category | `Kernel/Ports` |

chport components are `hidden="true"` because the user never selects a port directly. The correct port is determined by the architecture the platform provides — in a well-formed configuration the user picks a platform and a kernel, and the chport is the component that bridges them. GUI tooling may automate chport selection in a future version; for now it is a manual step that the ChibiForge warning system guides.

### 5.3 Example: `org.chibios.chibiforge.components.chport.armv7em`

```xml
<component
    xmlns="http://chibiforge/schema/component"
    id="org.chibios.chibiforge.components.chport.armv7em"
    name="ChibiOS Port ARMv7E-M"
    version="21.11.0"
    hidden="true"
    is_platform="false">

  <description>
    ChibiOS port layer for ARMv7E-M (Cortex-M4, Cortex-M7). Provides context
    switch, critical section primitives, and stack initialisation for both
    ChibiOS/RT and ChibiOS/NIL. Requires an ARMv7E-M platform to be present.
  </description>

  <resources/>

  <categories>
    <category id="Kernel/Ports"/>
  </categories>

  <requires>
    <feature id="features.platform"/>
    <feature id="features.platform.arch.armv7em"/>
  </requires>

  <provides>
    <feature id="features.chport" exclusive="true"/>
    <feature id="features.chport.arch.armv7em" exclusive="true"/>
  </provides>

  <sections>
    <!-- Port configuration properties: FPU context save, MPU enable,
         stack alignment, privileged mode, etc. (implementation detail) -->
  </sections>

</component>
```

---

## 6. Container Layout

```
component/
  schema.xml

  cfg/
    chportconf.h.ftlc       # Generated port configuration header

  source/                   # Static port sources -> generated/<normalised_id>/
    chcore.h                # Port header (types, macros, critical sections)
    chcore.c                # Port C implementation
    chcore_v7m.s            # Context switch assembly (ARMv7-M example)
    chcore_v7m_fpv4.s       # FPU variant context switch (ARMv7E-M example)

  resources/
    (none required for v1)
```

The port assembly file naming follows ChibiOS conventions and varies per architecture. The container layout is the same for all chport components; only the files inside `source/` differ.

---

## 7. The Complete Dependency Circle

A minimal ChibiOS/RT configuration on STM32G4xx requires exactly these three components working together:

| Component | Provides | Requires |
|---|---|---|
| `org.chibios.chibiforge.components.platform.stm32g4xx` | `features.platform` (excl.), `features.platform.arch.armv7em`, `features.platform.family.stm32g4xx` | _(nothing)_ |
| `org.chibios.chibiforge.components.chport.armv7em` | `features.chport` (excl.), `features.chport.arch.armv7em` | `features.platform`, `features.platform.arch.armv7em` |
| `org.chibios.chibiforge.components.kernel.rt` | `features.kernel` (excl.), `features.chapi` | `features.platform`, `features.chport` |

All feature requirements are satisfied with no warnings. Replacing `org.chibios.chibiforge.components.kernel.rt` with `org.chibios.chibiforge.components.kernel.nil` produces an identical dependency resolution — the chport is oblivious to which kernel is present.

---

## 8. Open Items

1. **Automated chport selection**: given that the correct chport is fully determined by the platform's architecture feature, a future GUI enhancement could automatically add the matching chport when a platform is selected, and warn or auto-swap it if the platform is changed. This requires no changes to the component model — it is purely a GUI convenience.

2. **Cortex-M7 vs Cortex-M4 FPU variants**: both are ARMv7E-M but have different FPU implementations (FPv5-D16 vs FPv4-SP-D16). Whether this distinction requires separate chport components or is handled by properties within `org.chibios.chibiforge.components.chport.armv7em` is an implementation decision deferred to the component author.

3. **RISC-V ports**: architecture tokens `riscv32` and `riscv64` are reserved in the platform spec. Corresponding chport components would follow the same pattern: `org.chibios.chibiforge.components.chport.riscv32`, requiring `features.platform.arch.riscv32`.
