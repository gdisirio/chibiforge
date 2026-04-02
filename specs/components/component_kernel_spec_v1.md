# ChibiOS Kernel Components — Specification v1

## 1. Purpose

This document specifies the **Kernel component class** for ChibiForge. The class currently has two members: **ChibiOS/RT** and **ChibiOS/NIL**. Both are RTOS kernels for embedded systems; they differ in complexity, memory footprint, and scheduling model, but they expose an identical feature contract to the rest of the component ecosystem.

A third-party RTOS (FreeRTOS, Zephyr, etc.) could define its own kernel component providing `features.kernel` and its own RTOS-specific identifier, and components that only require a generic kernel would work with it transparently.

---

## 2. The Kernel Feature Contract

### 2.1 Feature hierarchy

The kernel class defines two exported feature IDs:

| Feature ID | Meaning | `exclusive` |
|---|---|---|
| `features.kernel` | A kernel is present and active | `true` |
| `features.chapi` | The ChibiOS CH API is available | `true` |

**`features.kernel`** is the generic, RTOS-agnostic capability. Any component that needs "something schedulable" — threads, mutexes, semaphores in some form — requires this. It makes no assumption about which RTOS provides it.

**`features.chapi`** is the ChibiOS-specific contract. It signals that the ChibiOS CH API (`chThdCreateStatic`, `chMtxLock`, `chSysLock`, etc.) is available. Components that are explicitly written against the ChibiOS API — such as OSLIB, the ChibiOS HAL, and any component using `ch.h` directly — require this. Non-ChibiOS RTOS components would provide `features.kernel` but not `features.chapi`.

Both RT and NIL provide both features. This means:

- A component requiring only `features.kernel` works with RT, NIL, and any future third-party kernel component.
- A component requiring `features.chapi` works with RT and NIL, but not with a non-ChibiOS kernel.
- OSLIB requires `features.chapi`, not `features.kernel` — it is explicitly a ChibiOS-API consumer.

### 2.2 Mutual exclusion

Both `features.kernel` and `features.chapi` carry `exclusive="true"`. At most one kernel component may be present in a configuration, and at most one CH API provider. Adding both RT and NIL to the same configuration produces a conflict warning on both features and the configuration is considered ill-formed.

---

## 3. Component Definitions

### 3.1 ChibiOS/RT

| Attribute | Value |
|---|---|
| **ID** | `org.chibios.chibiforge.components.kernel.rt` |
| **Name** | `ChibiOS/RT` |
| **`is_platform`** | `false` |
| **`hidden`** | `false` |
| **Category** | `Kernel/RT` |

**`<provides>`:**
```xml
<provides>
  <feature id="features.kernel" exclusive="true"/>
  <feature id="features.chapi" exclusive="true"/>
</provides>
```

**`<requires>`:**
```xml
<requires>
  <feature id="features.platform"/>
  <feature id="features.chport"/>
</requires>
```

**Description:** ChibiOS/RT is a full-featured, preemptive RTOS kernel. It provides a rich set of synchronisation primitives (mutexes, semaphores, condvars, events, mailboxes, message queues), a round-robin and priority-based scheduler, and optional kernel statistics. Suitable for projects where full RTOS capability is needed and RAM is not severely constrained. Generates `chconf.h` into `generated/`.

---

### 3.2 ChibiOS/NIL

| Attribute | Value |
|---|---|
| **ID** | `org.chibios.chibiforge.components.kernel.nil` |
| **Name** | `ChibiOS/NIL` |
| **`is_platform`** | `false` |
| **`hidden`** | `false` |
| **Category** | `Kernel/NIL` |

**`<provides>`:**
```xml
<provides>
  <feature id="features.kernel" exclusive="true"/>
  <feature id="features.chapi" exclusive="true"/>
</provides>
```

**`<requires>`:**
```xml
<requires>
  <feature id="features.platform"/>
  <feature id="features.chport"/>
</requires>
```

**Description:** ChibiOS/NIL is a minimal RTOS kernel targeting severely resource-constrained devices. It provides a reduced but CH-API-compatible set of primitives (semaphores, events, a simple scheduler). Suitable for Cortex-M0/M0+ class devices or any project where RAM and flash are at a premium. Generates `nilconf.h` into `generated/`. Mutually exclusive with `org.chibios.chibiforge.components.kernel.rt` via `features.kernel exclusive`.

---

## 4. Architecture Dependency

Kernel components require only `features.platform` — they do not declare a dependency on any specific architecture feature. The rationale is:

- The platform component has already established the CPU architecture and provides the correct startup code, linker script, and CMSIS headers for that architecture.
- The kernel's port layer (the assembly stubs, context-switch code, critical section macros) is part of the ChibiOS source tree, not part of the ChibiForge-managed generated output. It is selected at build time by the toolchain flags and include paths that the platform's generated `Makefile` sets up.
- ChibiForge does not need to model the kernel port selection explicitly — it is an implicit consequence of the platform choice.

A kernel component therefore trusts that any platform satisfying `features.platform` has already done the right thing for the architecture. If a kernel port does not exist for a given architecture, that is a build-time failure, not a ChibiForge configuration warning.

---

## 5. Generated Outputs

Kernel components generate **configuration headers only**. They own no startup code, no linker script, and no build file — those are all platform responsibilities.

| Component | Generated file | Delivery mechanism | Write policy |
|---|---|---|---|
| `org.chibios.chibiforge.components.kernel.rt` | `generated/org_chibios_kernel_rt/chconf.h` | `cfg/` template | always overwrite |
| `org.chibios.chibiforge.components.kernel.nil` | `generated/org_chibios_kernel_nil/nilconf.h` | `cfg/` template | always overwrite |

The generated header is driven entirely by the properties the user sets in the component's configuration form. It contains `#define` macros that the ChibiOS kernel sources `#include` to tune their behaviour at compile time.

The platform's generated `Makefile` is responsible for adding the correct `generated/` subdirectory to the compiler include path so that `chconf.h` / `nilconf.h` is found without any explicit path in `#include` directives.

---

## 6. Container Layout

```
component/
  schema.xml

  cfg/
    chconf.h.ftlc         # (RT only) configuration header template
    nilconf.h.ftlc        # (NIL only) configuration header template

  source/
    (none — kernel sources are part of the ChibiOS source tree,
     not managed by this component)

  resources/
    (none required for v1)
```

Kernel components carry no static source files. The ChibiOS RT and NIL source trees are expected to be present in the project (as a submodule, vendored copy, or library) and referenced from the platform's generated `Makefile`. The kernel component's sole job in ChibiForge is to generate the correctly-tuned configuration header.

---

## 7. Relationship to OSLIB

OSLIB is a separate component that provides higher-level ChibiOS services (memory pools, heap, dynamic threads, registry, etc.) built on top of the CH API. It requires `features.chapi`, not `features.kernel`:

```xml
<!-- OSLIB component <requires> — for reference -->
<requires>
  <feature id="features.chapi"/>
</requires>
```

This means OSLIB works with both RT and NIL (both provide `features.chapi`) and is independent of any non-ChibiOS kernel. OSLIB is specified as a separate component and is out of scope for this document.

---

## 8. How Other Components Use the Kernel Contract

A component that needs a kernel but is RTOS-agnostic:

```xml
<requires>
  <feature id="features.kernel"/>
</requires>
```

A component written against the ChibiOS CH API (most ChibiOS HAL drivers):

```xml
<requires>
  <feature id="features.chapi"/>
</requires>
```

A component that needs both a platform and a ChibiOS kernel (the common case for ChibiOS HAL components):

```xml
<requires>
  <feature id="features.platform"/>
  <feature id="features.chapi"/>
</requires>
```

In all cases the requiring component has no knowledge of whether RT or NIL is present. The feature ID is the contract; the component providing it is an implementation detail resolved at configuration time by the ChibiForge dependency system.

---

## 9. Open Items

1. **SMP kernels**: ChibiOS/RT has SMP support for multi-core devices (e.g. RP2040 dual-M33, STM32H7 M7+M4 in symmetric mode). An SMP-capable RT variant may need to provide an additional `features.kernel.smp` feature so components can express an SMP dependency or incompatibility. Deferred pending multi-core platform support.

2. **Kernel version visibility**: components may eventually need to express a minimum kernel version dependency (e.g. "requires RT ≥ 6.0"). The current feature system has no version qualifier on `<requires>`. This is a known limitation to be addressed in a future ChibiForge spec revision.

3. **OSLIB spec cross-reference**: this document references OSLIB as a `features.chapi` consumer. The OSLIB component spec is pending and will be authored as part of the HAL/services component class.
