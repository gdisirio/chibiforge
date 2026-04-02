# ChibiOS HAL Components — Specification v1

## 1. Purpose

This document specifies the **HAL component class** for ChibiForge. The class covers three tightly related components that together form the hardware abstraction layer:

- **OSAL** (`org.chibios.chibiforge.components.hal.osal`) — OS Abstraction Layer, wraps a kernel and exposes a uniform OS API to the HAL.
- **HAL Core** (`org.chibios.chibiforge.components.hal.core`) — the architecture- and MCU-agnostic HAL infrastructure.
- **chhalport** (`org.chibios.chibiforge.components.chhalport.<family>`) — the MCU-family-specific Low Level Driver (LLD) implementation.

These three components, combined with a platform and a kernel stack, constitute a complete ChibiOS HAL environment. Peripheral driver components (SPI, UART, ADC, etc.) are a separate component class and are specified elsewhere.

---

## 2. The Full HAL Dependency Chain

```
platform    ──provides──►  features.platform
            ──provides──►  features.platform.arch.<arch>
            ──provides──►  features.platform.family.<family>
                │                         │
                ▼                         ▼
chport      ──requires──►  features.platform.arch.<arch>
            ──provides──►  features.chport
                │
                ▼
kernel      ──requires──►  features.platform + features.chport
            ──provides──►  features.kernel + features.chapi
                │
                ▼
osal        ──requires──►  features.chapi
            ──provides──►  features.chosalapi
                │
                │          features.platform.family.<family>
                │                         │
                ▼                         ▼
chhalport   ──requires──►  features.platform.family.<family>
            ──provides──►  features.chhalport
                │
                ▼
hal         ──requires──►  features.chosalapi + features.chhalport
            ──provides──►  features.hal + features.chhalapi
```

Each layer is decoupled from all others except through feature IDs. The HAL knows nothing about which kernel is present — only that `features.chosalapi` is satisfied. The OSAL knows nothing about which MCU family is present. The chhalport knows nothing about the kernel or OSAL.

---

## 3. OSAL Component

### 3.1 Role

The OSAL (OS Abstraction Layer) is a thin wrapper that translates HAL synchronisation and threading needs into calls on a specific kernel API. It is the boundary that makes the HAL kernel-agnostic. A ChibiOS OSAL wraps the CH API; a FreeRTOS OSAL would wrap FreeRTOS primitives — both would satisfy `features.chosalapi` and the HAL would be oblivious to the difference.

### 3.2 Component definition

| Attribute | Value |
|---|---|
| **ID** | `org.chibios.chibiforge.components.hal.osal` |
| **Name** | `HAL OSAL (ChibiOS)` |
| **`is_platform`** | `false` |
| **`hidden`** | `true` |
| **Category** | `HAL/OSAL` |

**`<provides>`:**
```xml
<provides>
  <feature id="features.chosalapi" exclusive="true"/>
</provides>
```

**`<requires>`:**
```xml
<requires>
  <feature id="features.chapi"/>
</requires>
```

**Description:** ChibiOS implementation of the HAL OSAL. Wraps the ChibiOS CH API (`chapi`) and exposes the uniform `chosalapi` interface consumed by HAL Core and all HAL peripheral drivers. Requires either ChibiOS/RT or ChibiOS/NIL to be present (both provide `features.chapi`). Hidden — selected as a consequence of kernel choice, not directly by the user.

### 3.3 Generated outputs

The OSAL component generates **no output**. It ships static source files only:

```
component/
  schema.xml

  source/
    osal.h          # OSAL interface header
    osal.c          # ChibiOS CH API implementation of the OSAL interface
```

The OSAL has no user-configurable properties and therefore no `<sections>`. Its `schema.xml` carries only metadata, `<requires>`, and `<provides>`.

### 3.4 `features.chosalapi` is exclusive

`features.chosalapi` carries `exclusive="true"`. At most one OSAL component may be present in a configuration. Having two OSAL implementations active simultaneously would produce conflicting symbol definitions at link time.

### 3.5 Third-party OSAL components

A third-party OSAL wrapping a non-ChibiOS kernel would provide `features.chosalapi` (exclusive) and require `features.kernel` instead of `features.chapi`:

```xml
<!-- Example: a FreeRTOS OSAL, for illustration -->
<requires>
  <feature id="features.kernel"/>
</requires>
<provides>
  <feature id="features.chosalapi" exclusive="true"/>
</provides>
```

The HAL Core and all peripheral driver components would remain unchanged — they require only `features.chosalapi`.

---

## 4. HAL Core Component

### 4.1 Role

HAL Core is the architecture- and MCU-agnostic HAL infrastructure. It provides the driver model, the HAL initialisation sequence (`halInit()`), and the base types and interfaces that all peripheral driver components build on. It knows nothing about specific MCU peripherals — that knowledge lives in the chhalport.

### 4.2 Component definition

| Attribute | Value |
|---|---|
| **ID** | `org.chibios.chibiforge.components.hal.core` |
| **Name** | `HAL Core` |
| **`is_platform`** | `false` |
| **`hidden`** | `false` |
| **Category** | `HAL/Core` |

**`<provides>`:**
```xml
<provides>
  <feature id="features.hal" exclusive="true"/>
  <feature id="features.chhalapi" exclusive="true"/>
</provides>
```

**`<requires>`:**
```xml
<requires>
  <feature id="features.chosalapi"/>
  <feature id="features.chhalport"/>
  <feature id="features.chboard"/>
</requires>
```

**Description:** HAL Core provides the ChibiOS Hardware Abstraction Layer infrastructure: driver model, `halInit()`, base types, and the generic driver interfaces. Requires an OSAL component (providing `features.chosalapi`), an MCU-family HAL port (providing `features.chhalport`), and a board component (providing `features.chboard`). All HAL peripheral driver components require `features.hal` and/or `features.chhalapi` to express their dependency on this component.

### 4.3 Feature semantics

| Feature ID | Meaning | `exclusive` |
|---|---|---|
| `features.hal` | The HAL infrastructure is present | `true` — at most one HAL core per configuration |
| `features.chhalapi` | The ChibiOS HAL API is available | `true` — at most one ChibiOS HAL API provider per configuration |

`features.hal` is the generic contract. A non-ChibiOS HAL component could provide `features.hal` without providing `features.chhalapi`. Components written against the ChibiOS HAL API specifically require `features.chhalapi`.

### 4.4 Generated outputs

HAL Core generates a single configuration header:

| Generated file | Delivery | Write policy |
|---|---|---|
| `generated/org_chibios_hal_core/halconf.h` | `cfg/` template | always overwrite |

`halconf.h` enables or disables individual HAL subsystems and sets HAL-wide compile-time options. Its content is driven entirely by the properties configured in the component's `<sections>`.

---

## 5. chhalport Components

### 5.1 Role

A chhalport component provides the MCU-family-specific Low Level Driver (LLD) implementations for a given device family. It is the HAL equivalent of chport in the kernel stack: it bridges the generic HAL interface down to specific peripheral register layouts, DMA channels, clock trees, and interrupt vectors of a concrete MCU family.

The chhalport knows about the MCU family but nothing about the kernel, OSAL, or architecture — those are handled by other components. It matches the platform's family feature 1-to-1.

### 5.2 Feature contract

Every chhalport component MUST provide:

```xml
<provides>
  <feature id="features.chhalport" exclusive="true"/>
  <feature id="features.chhalport.family.<family>" exclusive="true"/>
</provides>
```

Every chhalport component MUST require the matching platform family feature:

```xml
<requires>
  <feature id="features.platform"/>
  <feature id="features.platform.family.<family>"/>
</requires>
```

`features.chhalport` is exclusive — at most one HAL port per configuration. `features.chhalport.family.<family>` is informational, using the same family token registry as the platform spec.

The chhalport does NOT require `features.chosalapi`, `features.hal`, or `features.chapi`. It is independent of the kernel and OSAL stack.

### 5.3 Component ID pattern

```
org.chibios.chibiforge.components.chhalport.<family>
```

| Component ID | Family |
|---|---|
| `org.chibios.chibiforge.components.chhalport.stm32f0xx` | STM32F0xx |
| `org.chibios.chibiforge.components.chhalport.stm32f1xx` | STM32F1xx |
| `org.chibios.chibiforge.components.chhalport.stm32f3xx` | STM32F3xx |
| `org.chibios.chibiforge.components.chhalport.stm32f4xx` | STM32F4xx |
| `org.chibios.chibiforge.components.chhalport.stm32f7xx` | STM32F7xx |
| `org.chibios.chibiforge.components.chhalport.stm32h7xx` | STM32H7xx |
| `org.chibios.chibiforge.components.chhalport.stm32l0xx` | STM32L0xx |
| `org.chibios.chibiforge.components.chhalport.stm32l4xx` | STM32L4xx |
| `org.chibios.chibiforge.components.chhalport.stm32g0xx` | STM32G0xx |
| `org.chibios.chibiforge.components.chhalport.stm32g4xx` | STM32G4xx |
| `org.chibios.chibiforge.components.chhalport.stm32u5xx` | STM32U5xx |
| `org.chibios.chibiforge.components.chhalport.rp2040` | RP2040 |

### 5.4 Common metadata for all chhalport components

| Attribute | Value |
|---|---|
| `is_platform` | `false` |
| `hidden` | `true` |
| Category | `HAL/Ports` |

chhalport components are `hidden="true"`. The user never selects a HAL port directly — the correct chhalport is fully determined by the platform's family feature. The same GUI automation opportunity noted for chport applies here.

### 5.5 Generated outputs

Each chhalport generates one port configuration header and ships static LLD sources:

| Output | Delivery | Write policy |
|---|---|---|
| `generated/<normalised_id>/mcuconf.h` | `cfg/` template | always overwrite |
| `generated/<normalised_id>/<lld_sources>` | `source/` static | always overwrite |

`mcuconf.h` is the MCU-specific configuration header that enables and tunes individual peripheral LLD instances (clock sources, DMA channel assignments, IRQ priorities, etc.). It is generated from the component's configuration properties and is the primary user-facing output of the chhalport.

The static LLD sources are the family-specific C files implementing the low-level driver interfaces declared by HAL Core (e.g. `hal_lld.c`, `stm32_dma.c`, `stm32_rcc.c`, and per-peripheral LLD files).

### 5.6 Example: `org.chibios.chibiforge.components.chhalport.stm32g4xx`

```xml
<component
    xmlns="http://chibiforge/schema/component"
    id="org.chibios.chibiforge.components.chhalport.stm32g4xx"
    name="ChibiOS HAL Port STM32G4xx"
    version="21.11.0"
    hidden="true"
    is_platform="false">

  <description>
    ChibiOS HAL Low Level Driver port for STM32G4xx devices. Provides
    MCU-specific LLD implementations and generates mcuconf.h. Requires
    an STM32G4xx platform to be present.
  </description>

  <resources>
    <resource id="stm32g4_limits" file="resources/stm32g4_limits.xml"/>
  </resources>

  <categories>
    <category id="HAL/Ports"/>
  </categories>

  <requires>
    <feature id="features.platform"/>
    <feature id="features.platform.family.stm32g4xx"/>
  </requires>

  <provides>
    <feature id="features.chhalport" exclusive="true"/>
    <feature id="features.chhalport.family.stm32g4xx" exclusive="true"/>
  </provides>

  <sections>
    <!-- mcuconf.h generation properties: clock sources, DMA assignments,
         IRQ priorities, peripheral enables. (Implementation detail.) -->
  </sections>

</component>
```

---

## 6. Container Layout Summary

### OSAL (`org.chibios.chibiforge.components.hal.osal`)
```
component/
  schema.xml          # no <sections> — no user-configurable properties
  source/
    osal.h
    osal.c
```

### HAL Core (`org.chibios.chibiforge.components.hal.core`)
```
component/
  schema.xml
  cfg/
    halconf.h.ftlc
  source/
    hal.h
    hal.c
    hal_cb.h          # driver callback types
    ... (other HAL core headers and sources)
```

### chhalport (`org.chibios.chibiforge.components.chhalport.<family>`)
```
component/
  schema.xml
  cfg/
    mcuconf.h.ftlc
  source/
    hal_lld.h
    hal_lld.c
    stm32_dma.h       # (family-specific, example)
    stm32_dma.c
    stm32_rcc.h
    stm32_rcc.c
    ... (per-peripheral LLD files)
  resources/
    stm32g4_limits.xml   # peripheral instance counts, DMA channel map, etc.
```

---

## 7. The Complete Minimal HAL Configuration

A minimal ChibiOS/RT + HAL configuration on STM32G4xx requires these components:

| Component | Provides | Requires |
|---|---|---|
| `org.chibios.chibiforge.components.platform.stm32g4xx` | `features.platform` (excl.), `features.platform.arch.armv7em`, `features.platform.family.stm32g4xx` | — |
| `org.chibios.chibiforge.components.chport.armv7em` | `features.chport` (excl.), `features.chport.arch.armv7em` | `features.platform`, `features.platform.arch.armv7em` |
| `org.chibios.chibiforge.components.kernel.rt` | `features.kernel` (excl.), `features.chapi` | `features.platform`, `features.chport` |
| `org.chibios.chibiforge.components.hal.osal` | `features.chosalapi` (excl.) | `features.chapi` |
| `org.chibios.chibiforge.components.chhalport.stm32g4xx` | `features.chhalport` (excl.), `features.chhalport.family.stm32g4xx` | `features.platform`, `features.platform.family.stm32g4xx` |
| `org.chibios.chibiforge.components.board.<boardname>` | `features.chboard` (excl.), `features.chboard.<boardname>` | `features.chhalport`, `features.platform.family.stm32g4xx` |
| `org.chibios.chibiforge.components.hal.core` | `features.hal` (excl.), `features.chhalapi` | `features.chosalapi`, `features.chhalport`, `features.chboard` |

All seven feature requirements are satisfied with no warnings. Each component depends only on feature IDs — no component references any other component by its `id`.

---

## 8. Open Items

1. **OSLIB placement**: OSLIB provides higher-level ChibiOS services on top of the CH API. It requires `features.chapi` and likely `features.hal`. Its relationship to the HAL (peer vs. dependent) needs to be settled when the OSLIB component is specified.

2. **HAL-only configurations**: some projects use the ChibiOS HAL without the RT or NIL kernel, running bare-metal with the nil-OSAL stub. This would require a bare-metal OSAL component providing `features.chosalapi` without requiring `features.chapi`. The component model supports this cleanly — it is an authoring task, not a design change.

3. **chhalport and device sub-variants**: like the platform component, chhalport components for large families (STM32H7, STM32U5) may need a device sub-variant selector to correctly populate DMA channel maps and peripheral instance counts in `mcuconf.h`. The `resources/<family>_limits.xml` mechanism from the platform spec applies here too.

4. **Peripheral driver dependency on chhalport**: HAL peripheral driver components (SPI, UART, ADC, etc.) will need to express a family-specific dependency when they require a specific LLD. Whether they require `features.chhalport` generically or `features.chhalport.family.<family>` specifically is a design question deferred to the peripheral driver component spec.
