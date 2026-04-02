# ChibiOS Board Components — Specification v1

## 1. Purpose

This document specifies the **Board component class** for ChibiForge. A board component represents a specific physical hardware board. It owns all board-level configuration: GPIO pin assignments and modes, clock tree settings, and any other hardware details that are fixed by the PCB layout rather than by the application.

The board component is the last mandatory piece of the ChibiOS foundation stack. HAL Core requires `features.chboard` — no board, no HAL.

---

## 2. Role in the Foundation Stack

The board component slots between the chhalport and HAL Core in the dependency graph:

```
platform        ──provides──►  features.platform
                ──provides──►  features.platform.arch.<arch>
                ──provides──►  features.platform.family.<family>
                                          │
                      ┌───────────────────┘
                      ▼
chhalport       ──requires──►  features.platform.family.<family>
                ──provides──►  features.chhalport
                                          │
                      ┌───────────────────┘
                      │
                      │    platform.family.<family>
                      │               │
                      ▼               ▼
board           ──requires──►  features.chhalport
                ──requires──►  features.platform.family.<family>
                ──provides──►  features.chboard (excl.)
                                          │
                      ┌───────────────────┘
                      ▼
hal.core        ──requires──►  features.chosalapi
                ──requires──►  features.chhalport
                ──requires──►  features.chboard        ← new
                ──provides──►  features.hal (excl.)
                ──provides──►  features.chhalapi
```

The board component is the only component in the foundation stack that a user might reasonably customise heavily — GPIO assignments and clock trees are inherently board-specific.

---

## 3. Feature Contract

### 3.1 Provided features

Every board component MUST provide:

```xml
<provides>
  <feature id="features.chboard" exclusive="true"/>
  <feature id="features.chboard.<boardname>" exclusive="true"/>
</provides>
```

| Feature ID | Meaning | `exclusive` |
|---|---|---|
| `features.chboard` | A board definition is present | `true` — at most one board per configuration |
| `features.chboard.<boardname>` | This specific board is present | `true` — one board identity per configuration |

`features.chboard` is exclusive: a configuration describes one physical board. `features.chboard.<boardname>` is informational and follows the same normalisation rule as all other token identifiers (lowercase, dots replaced by underscores).

### 3.2 Required features

Every board component MUST require:

```xml
<requires>
  <feature id="features.chhalport"/>
  <feature id="features.platform.family.<family>"/>
</requires>
```

The board requires `features.chhalport` because it uses the family-specific GPIO and clock register definitions that the chhalport provides. It requires the specific `features.platform.family.<family>` because GPIO port assignments and peripheral mappings are defined against a concrete MCU family register layout — a board component for an STM32G4xx board cannot be used with an STM32F4xx platform.

The board does NOT require `features.chosalapi` or `features.chapi`. It is independent of the kernel and OSAL stack.

### 3.3 Updated HAL Core `<requires>`

With the board component now specified, the HAL Core `<requires>` block from the HAL spec is updated:

```xml
<requires>
  <feature id="features.chosalapi"/>
  <feature id="features.chhalport"/>
  <feature id="features.chboard"/>
</requires>
```

---

## 4. Generated Outputs

A board component generates two files and delivers them to the **configuration root** (not to `generated/`), since they are files the build system and startup code reference directly:

| Output | Delivery | Write policy |
|---|---|---|
| `board.h` | `cfg_root_wa/` template | always overwrite |
| `board.c` | `cfg_root_wa/` template | always overwrite |

Both files are always overwritten on generation — they are fully derived from the component's configuration properties and must never be manually edited. This distinguishes them from `main.c` (platform, write-once) and the `Makefile` (platform, write-once).

### 4.1 `board.h`

Contains all compile-time board definitions consumed by the rest of the system:

- Board identification macros (`BOARD_NAME`, `BOARD_OEM_TAG`).
- Crystal/oscillator frequency definitions (`STM32_LSECLK`, `STM32_HSECLK`, etc.).
- GPIO pin definitions — one set of macros per pin: alternate function, mode, output type, speed, pull resistor.
- Any other board-level `#define` constants (e.g. LED pin aliases, button pin aliases).

### 4.2 `board.c`

Contains the board initialisation function `boardInit()`, called from the platform startup code after `SystemInit()` and before `main()`. Responsibilities:

- GPIO port configuration — writes the computed register values for all configured pins.
- Any board-level hardware initialisation that must happen before the RTOS and HAL start (e.g. external oscillator enable, power rail sequencing).

The platform startup code calls `boardInit()` as a weak symbol hook. The board component's `board.c` provides the strong definition. If no board component is present, the weak stub is used — but since `features.chboard` is required by HAL Core, a missing board is flagged as a configuration warning.

---

## 5. Component Definitions

### 5.1 Component ID pattern

```
org.chibios.chibiforge.components.board.<boardname>
```

`<boardname>` is the canonical lowercase board identifier, derived from the official board name with spaces and hyphens removed. It does not encode the MCU family — the family dependency is expressed through `<requires>`, not the ID.

### 5.2 Common metadata

| Attribute | Value |
|---|---|
| `is_platform` | `false` |
| `hidden` | `false` |
| Category | `Board/<Vendor>` or `Board/Custom` |

Board components are `hidden="false"` — the user selects a board from the palette as a primary configuration step. The category groups boards by vendor or type:

| Category | Used by |
|---|---|
| `Board/STMicroelectronics` | ST evaluation and discovery boards |
| `Board/RaspberryPi` | RP-series boards |
| `Board/Custom` | User-defined and third-party boards |

### 5.3 Defined board components

| Component ID | Board Name | Family Required |
|---|---|---|
| `org.chibios.chibiforge.components.board.stm32g4discovery` | STM32G4-Discovery | `stm32g4xx` |
| `org.chibios.chibiforge.components.board.stm32nucleo_g474re` | Nucleo-G474RE | `stm32g4xx` |
| `org.chibios.chibiforge.components.board.stm32f4discovery` | STM32F4-Discovery | `stm32f4xx` |
| `org.chibios.chibiforge.components.board.stm32nucleo_f401re` | Nucleo-F401RE | `stm32f4xx` |
| `org.chibios.chibiforge.components.board.stm32nucleo_f746zg` | Nucleo-F746ZG | `stm32f7xx` |
| `org.chibios.chibiforge.components.board.stm32h743zi_nucleo` | Nucleo-H743ZI | `stm32h7xx` |
| `org.chibios.chibiforge.components.board.rp2040_zero` | RP2040-Zero | `rp2040` |

This list grows as board components are authored. It is not exhaustive.

### 5.4 `<boardname>` token normalisation

The `<boardname>` token in both the component ID and the `features.chboard.<boardname>` feature ID follows the standard ChibiForge identifier normalisation rule: lowercase, non-alphanumeric characters replaced with `_`, consecutive underscores collapsed. The canonical source is the official board name as used by the silicon or board vendor.

Examples:

| Official name | `<boardname>` token |
|---|---|
| STM32G4-Discovery | `stm32g4discovery` |
| Nucleo-G474RE | `nucleo_g474re` |
| STM32F4-Discovery | `stm32f4discovery` |
| RP2040-Zero | `rp2040_zero` |

### 5.5 Example: `org.chibios.chibiforge.components.board.nucleo_g474re`

```xml
<component
    xmlns="http://chibiforge/schema/component"
    id="org.chibios.chibiforge.components.board.nucleo_g474re"
    name="Nucleo-G474RE"
    version="21.11.0"
    hidden="false"
    is_platform="false">

  <description>
    Board support for the STMicroelectronics Nucleo-G474RE development board.
    Generates board.h and board.c with GPIO pin assignments and clock
    configuration for the on-board 24 MHz HSE crystal, user LED (PA5),
    and user button (PC13).
  </description>

  <resources>
    <resource id="stm32g4_gpio" file="resources/stm32g4_gpio.xml"/>
  </resources>

  <categories>
    <category id="Board/STMicroelectronics"/>
  </categories>

  <requires>
    <feature id="features.chhalport"/>
    <feature id="features.platform.family.stm32g4xx"/>
  </requires>

  <provides>
    <feature id="features.chboard" exclusive="true"/>
    <feature id="features.chboard.nucleo_g474re" exclusive="true"/>
  </provides>

  <sections>
    <!-- GPIO pin configuration, clock settings, oscillator frequencies.
         (Implementation detail — out of scope for this spec.) -->
  </sections>

</component>
```

---

## 6. Container Layout

```
component/
  schema.xml

  cfg_root_wa/
    board.h.ftlc          # GPIO definitions, clock macros, board name
    board.c.ftlc          # boardInit() — GPIO register writes, early init

  source/
    (none — board files are fully generated, no static sources)

  resources/
    stm32g4_gpio.xml      # GPIO alternate function map, pin capability table
                          # used to populate pin mode/AF dropdowns in the GUI
```

Board components carry no static source files — `board.h` and `board.c` are entirely template-driven outputs. The GPIO resource file (`stm32g4_gpio.xml` or equivalent) drives the configuration schema: it provides the list of available pins, valid alternate functions per pin, and any family-specific constraints, used via `@ref:` in the component's property definitions.

---

## 7. The Complete Foundation Stack

A fully specified ChibiOS/RT + HAL configuration on a Nucleo-G474RE requires exactly these components:

| Component | Provides | Requires |
|---|---|---|
| `org.chibios.chibiforge.components.platform.stm32g4xx` | `features.platform` (excl.), `features.platform.arch.armv7em`, `features.platform.family.stm32g4xx` | — |
| `org.chibios.chibiforge.components.chport.armv7em` | `features.chport` (excl.), `features.chport.arch.armv7em` | `features.platform`, `features.platform.arch.armv7em` |
| `org.chibios.chibiforge.components.kernel.rt` | `features.kernel` (excl.), `features.chapi` | `features.platform`, `features.chport` |
| `org.chibios.chibiforge.components.hal.osal` | `features.chosalapi` (excl.) | `features.chapi` |
| `org.chibios.chibiforge.components.chhalport.stm32g4xx` | `features.chhalport` (excl.), `features.chhalport.family.stm32g4xx` | `features.platform`, `features.platform.family.stm32g4xx` |
| `org.chibios.chibiforge.components.board.nucleo_g474re` | `features.chboard` (excl.), `features.chboard.nucleo_g474re` | `features.chhalport`, `features.platform.family.stm32g4xx` |
| `org.chibios.chibiforge.components.hal.core` | `features.hal` (excl.), `features.chhalapi` | `features.chosalapi`, `features.chhalport`, `features.chboard` |

All requirements satisfied, no warnings. This is the complete mandatory foundation — every additional component (peripheral drivers, OSLIB, application components) builds on top of this seven-component base.

---

## 8. Open Items

1. **GPIO resource file format**: the `stm32g4_gpio.xml` resource that drives pin configuration properties in the GUI needs a defined schema. It must encode available pins, valid modes, alternate function assignments per pin, and family-specific constraints. This is a shared resource between the board component and potentially the chhalport component (which also needs pin capability data for DMA and peripheral instance configuration).

2. **Board-specific feature dependencies**: some application components may require a specific board (e.g. a component that drives the on-board LCD on a Discovery kit). These components would require `features.chboard.stm32g4discovery`. This usage pattern is valid under the current model and needs no design change — it is noted here for completeness.

3. **Clock tree validation**: the board component configures clock frequencies (HSE, LSE crystal values) that must be consistent with the chhalport's clock tree configuration (PLL settings, bus prescalers). Cross-component validation of clock values is currently out of scope for ChibiForge v1 — it is the user's responsibility to ensure consistency. A future `@cond:` or cross-component `@ref:` mechanism could enforce this.

4. **Custom board workflow**: users creating a board component for custom hardware need a starting point. A `org.chibios.chibiforge.components.board.template` component or a "new board" wizard in the GUI would assist. Deferred to a future GUI enhancement.
