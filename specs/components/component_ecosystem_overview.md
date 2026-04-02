# ChibiOS ChibiForge Component Ecosystem — Overview

## 1. Purpose

This document provides a high-level map of the ChibiForge component ecosystem for ChibiOS. It establishes the scope, the layering model, and the catalogue of current and planned component classes. Individual component classes are specified in separate documents; this overview is the index and the architectural framing.

The component model is explicitly designed for **open-ended growth**: new subsystems, third-party libraries, and external software stacks can be added as components without modifying the ChibiForge tooling or the existing component contracts. The feature ID system is the stable integration surface.

All components, regardless of origin, are packaged as Eclipse plugins. This is the universal distribution and authoring format. See the *Component Authoring Rules — Eclipse Plugin Packaging* spec for the mandatory file layout and workflow.

---

## 2. Design Intent

The ChibiForge component ecosystem for ChibiOS is not a fixed set of components — it is a **framework for describing an ecosystem**. The rules are:

- Every software unit that can be independently enabled, configured, or replaced is a candidate for a component.
- Components depend on each other exclusively through feature IDs, never by component ID.
- The layering model (§3) determines which features a component may require and what it should export.
- Third-party and community components are first-class citizens: they follow the same rules as ChibiOS-authored components and integrate through the same feature contracts.

---

## 3. Layering Model

Components are organised into layers. Higher layers depend on lower layers through feature IDs; they never reach downward past their immediate dependency.

```
┌─────────────────────────────────────────────────────────────────┐
│  APPLICATION LAYER                                              │
│  SB (Sandbox), Application components, user code               │
├─────────────────────────────────────────────────────────────────┤
│  SERVICES LAYER                                                 │
│  VFS, LwIP, LittleFS, FatFS, USB stacks, crypto, ...           │
├─────────────────────────────────────────────────────────────────┤
│  HAL DRIVERS LAYER                                              │
│  Serial, SPI, I2C, ADC, DAC, PWM, USB, Ethernet, CAN, ...      │
├──────────────────────────────┬──────────────────────────────────┤
│  HAL CORE                    │  OSLIB                           │
│  features.hal, features.     │  features.chapiext               │
│  chhalapi                    │                                  │
├──────────────────────────────┴──────────────────────────────────┤
│  KERNEL LAYER                                                   │
│  RT / NIL  →  features.kernel, features.chapi                  │
│  OSAL      →  features.chosalapi                               │
├─────────────────┬───────────────────────────────────────────────┤
│  PORT LAYER     │  HAL PORT LAYER                               │
│  chport         │  chhalport + board                            │
│  features.      │  features.chhalport, features.chboard         │
│  chport         │                                               │
├─────────────────┴───────────────────────────────────────────────┤
│  PLATFORM LAYER                                                 │
│  Startup, linker, Makefile, CMSIS                               │
│  features.platform, features.platform.arch.*, .family.*        │
└─────────────────────────────────────────────────────────────────┘
```

---

## 4. Specified Components (Foundation)

The following components are fully specified as of this writing. They form the mandatory foundation stack that every ChibiOS project built with ChibiForge requires.

| Spec Document | Components | Key Features |
|---|---|---|
| Platform Component Spec v1 | `org.chibios.chibiforge.components.platform.<family>` | `features.platform` (excl.), `features.platform.arch.*` (excl.), `features.platform.family.*` (excl.) |
| chport Component Spec v1 | `org.chibios.chibiforge.components.chport.<arch>` | `features.chport` (excl.), `features.chport.arch.*` (excl.) |
| Kernel Component Spec v1 | `org.chibios.chibiforge.components.kernel.rt`, `org.chibios.chibiforge.components.kernel.nil` | `features.kernel` (excl.), `features.chapi` (excl.) |
| HAL Component Spec v1 | `org.chibios.chibiforge.components.hal.osal`, `org.chibios.chibiforge.components.hal.core`, `org.chibios.chibiforge.components.chhalport.<family>` | `features.chosalapi` (excl.), `features.hal` (excl.), `features.chhalapi` (excl.), `features.chhalport` (excl.) |
| Board Component Spec v1 | `org.chibios.chibiforge.components.board.<boardname>` | `features.chboard` (excl.), `features.chboard.<boardname>` (excl.) |
| OSLIB Component Spec v1 | `org.chibios.chibiforge.components.oslib` | `features.chapiext` (excl.) |

---

## 5. Planned Component Classes

The following classes are identified but not yet specified. They are listed here to establish their intended place in the layering model and their anticipated feature contracts. Each will be specified in a dedicated document.

### 5.1 HAL Peripheral Drivers

Individual driver components for each HAL peripheral interface. Each driver sits in the HAL Drivers layer, requires `features.chhalapi`, and provides its own `features.hal.<driver>` feature.

| Component ID pattern | Driver | Provided feature |
|---|---|---|
| `org.chibios.chibiforge.components.hal.serial` | Serial (buffered) | `features.hal.serial` |
| `org.chibios.chibiforge.components.hal.uart` | UART (async low-level) | `features.hal.uart` |
| `org.chibios.chibiforge.components.hal.spi` | SPI | `features.hal.spi` |
| `org.chibios.chibiforge.components.hal.i2c` | I2C | `features.hal.i2c` |
| `org.chibios.chibiforge.components.hal.adc` | ADC | `features.hal.adc` |
| `org.chibios.chibiforge.components.hal.dac` | DAC | `features.hal.dac` |
| `org.chibios.chibiforge.components.hal.pwm` | PWM | `features.hal.pwm` |
| `org.chibios.chibiforge.components.hal.gpt` | General Purpose Timer | `features.hal.gpt` |
| `org.chibios.chibiforge.components.hal.icu` | Input Capture Unit | `features.hal.icu` |
| `org.chibios.chibiforge.components.hal.can` | CAN | `features.hal.can` |
| `org.chibios.chibiforge.components.hal.usb` | USB (device) | `features.hal.usb` |
| `org.chibios.chibiforge.components.hal.eth` | Ethernet | `features.hal.eth` |
| `org.chibios.chibiforge.components.hal.flash` | Flash / EEPROM | `features.hal.flash` |
| `org.chibios.chibiforge.components.hal.rtc` | RTC | `features.hal.rtc` |
| `org.chibios.chibiforge.components.hal.wdg` | Watchdog | `features.hal.wdg` |
| `org.chibios.chibiforge.components.hal.pal` | PAL (I/O ports) | `features.hal.pal` |
| `org.chibios.chibiforge.components.hal.mac` | MAC (low-level Ethernet) | `features.hal.mac` |
| `org.chibios.chibiforge.components.hal.mmc` | MMC / SDCard | `features.hal.mmc` |

### 5.2 ChibiOS/SB — Sandbox

SB provides a protected execution environment for user-mode applications running on top of ChibiOS/RT. It requires the kernel and HAL and provides isolation primitives.

| Component ID | Provided feature | Requires |
|---|---|---|
| `org.chibios.chibiforge.components.sb.core` | `features.sb` (excl.) | `features.chapi`, `features.hal` |

SB will likely require its own port layer (`features.sbport`) mirroring the chport/chhalport pattern, since it depends on MPU/PMP configuration that is architecture-specific.

### 5.3 ChibiOS/VFS — Virtual File System

VFS provides a unified file system interface over multiple backends. It sits in the Services layer and requires `features.chapiext` (for dynamic allocation) and `features.hal`.

| Component ID | Provided feature | Requires |
|---|---|---|
| `org.chibios.chibiforge.components.vfs.core` | `features.vfs` (excl.) | `features.chapiext`, `features.hal` |

VFS backends are separate components requiring `features.vfs`:

| Component ID | Backend | Provided feature |
|---|---|---|
| `org.chibios.chibiforge.components.vfs.littlefs` | LittleFS | `features.vfs.littlefs` |
| `org.chibios.chibiforge.components.vfs.fatfs` | FatFS | `features.vfs.fatfs` |
| `org.chibios.chibiforge.components.vfs.overlay` | Overlay FS | `features.vfs.overlay` |
| `org.chibios.chibiforge.components.vfs.streams` | Stream-based pseudo-FS | `features.vfs.streams` |

### 5.4 LittleFS

LittleFS can be used standalone (without VFS) or as a VFS backend. As a standalone component it requires `features.hal.flash` or `features.hal.mmc` depending on the storage medium.

| Component ID | Provided feature | Requires |
|---|---|---|
| `org.chibios.chibiforge.components.littlefs` | `features.littlefs` (excl.) | `features.chapi` |

### 5.5 FatFS

FatFS similarly can operate standalone or as a VFS backend.

| Component ID | Provided feature | Requires |
|---|---|---|
| `org.chibios.chibiforge.components.fatfs` | `features.fatfs` (excl.) | `features.chapi` |

### 5.6 lwIP — Lightweight IP Stack

lwIP provides TCP/IP networking. It requires an Ethernet MAC driver and the kernel.

| Component ID | Provided feature | Requires |
|---|---|---|
| `org.chibios.chibiforge.components.lwip` | `features.lwip` (excl.) | `features.chapi`, `features.hal.mac` |

### 5.7 USB Class Drivers

USB device class drivers sit above `features.hal.usb` and provide class-specific protocol handling. Each is an independent component.

| Component ID | Class | Provided feature |
|---|---|---|
| `org.chibios.chibiforge.components.usb.cdc_acm` | CDC-ACM (serial over USB) | `features.usb.cdc_acm` |
| `org.chibios.chibiforge.components.usb.hid` | HID | `features.usb.hid` |
| `org.chibios.chibiforge.components.usb.msc` | Mass Storage | `features.usb.msc` |

All USB class drivers require `features.hal.usb`.

### 5.8 Crypto / Security

Cryptographic services, potentially backed by hardware accelerators exposed through the HAL.

| Component ID | Provided feature | Requires |
|---|---|---|
| `org.chibios.chibiforge.components.crypto` | `features.crypto` (excl.) | `features.chapi` |

### 5.9 Third-Party and External Components

The component model places no restriction on the origin of a component. Third-party components follow the same rules:

- Use the author's own namespace in the component ID (e.g. `com.example.mydriver`).
- Declare `<requires>` using standard ChibiOS feature IDs to integrate with the foundation stack.
- Export their own feature IDs for downstream components to depend on.

Examples of anticipated external component integrations:

| Software | Anticipated component ID | Provided feature |
|---|---|---|
| Mbed TLS | `org.mbedtls.core` | `features.mbedtls` |
| FreeRTOS (as kernel alt.) | `org.freertos.kernel` | `features.kernel`, `features.freertos` |
| Azure RTOS ThreadX | `org.threadx.kernel` | `features.kernel`, `features.threadx` |
| Zephyr OSAL adapter | `org.zephyr.osal` | `features.chosalapi` |

---

## 6. Feature ID Namespace Summary

The table below records all top-level feature namespaces defined or reserved across the ecosystem. New namespaces require a revision of this document.

| Namespace | Owner | Notes |
|---|---|---|
| `features.platform` | Platform components | arch and family sub-features |
| `features.chport` | chport components | arch sub-features |
| `features.kernel` | Kernel components | generic, RTOS-agnostic |
| `features.chapi` | ChibiOS kernel (RT/NIL) | ChibiOS CH API |
| `features.chapiext` | OSLIB | CH API extension |
| `features.chosalapi` | OSAL components | HAL OS abstraction |
| `features.chhalport` | chhalport components | family sub-features |
| `features.chboard` | Board components | boardname sub-features |
| `features.hal` | HAL Core | generic HAL |
| `features.chhalapi` | HAL Core | ChibiOS HAL API |
| `features.hal.*` | HAL driver components | one per driver |
| `features.sb` | SB components | sandbox |
| `features.vfs` | VFS components | backend sub-features |
| `features.littlefs` | LittleFS component | |
| `features.fatfs` | FatFS component | |
| `features.lwip` | lwIP component | |
| `features.usb.*` | USB class drivers | one per class |
| `features.crypto` | Crypto component | |

---

## 7. Versioning and Evolution

- This document is updated whenever a new component class is specified or a new feature namespace is introduced.
- Feature IDs are **stable contracts**: once published, a feature ID must not be renamed or repurposed without a major version increment of all components that provide or require it.
- The ecosystem grows by addition, not by modification of existing contracts.
