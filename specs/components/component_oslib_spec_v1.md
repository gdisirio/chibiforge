# ChibiOS OSLIB Component — Specification v1

## 1. Purpose

OSLIB is a ChibiOS API extension layer that adds higher-level services on top of the CH API provided by RT or NIL. It exports `features.chapiext`, which components requiring these extended services declare as a dependency instead of `features.chapi` directly.

OSLIB is optional. Components that only need the base CH API continue to require `features.chapi`. Components that need OSLIB services require `features.chapiext`. A configuration without OSLIB is valid as long as no component requires `features.chapiext`.

---

## 2. Feature Contract

### 2.1 Provided features

```xml
<provides>
  <feature id="features.chapiext" exclusive="true"/>
</provides>
```

| Feature ID | Meaning | `exclusive` |
|---|---|---|
| `features.chapiext` | Extended ChibiOS CH API (OSLIB) is present | `true` — at most one CH API extension layer per configuration |

`features.chapiext` is exclusive because two competing extension layers would produce conflicting symbol definitions and duplicate service implementations at link time.

### 2.2 Required features

```xml
<requires>
  <feature id="features.chapi"/>
</requires>
```

OSLIB requires `features.chapi` — it extends RT or NIL, both of which provide it. It does not require `features.kernel` directly, and it has no dependency on the platform, chport, HAL, or board layers. OSLIB is purely a kernel-layer extension.

---

## 3. Component Definition

| Attribute | Value |
|---|---|
| **ID** | `org.chibios.chibiforge.components.oslib` |
| **Name** | `ChibiOS OSLIB` |
| **`is_platform`** | `false` |
| **`hidden`** | `false` |
| **Category** | `Kernel/Extensions` |

`hidden="false"` — OSLIB is an explicit user choice. A project that needs dynamic memory, heap, or the objects factory selects OSLIB from the palette.

---

## 4. Generated Outputs

OSLIB generates no output and carries no `<sections>`. It ships static source files only:

```
component/
  schema.xml          # no <sections> — no user-configurable properties

  source/
    oslib/
      ch_bsem.c       # Binary semaphores
      ch_cond.c       # Condition variables
      ch_dyn.c        # Dynamic threads
      ch_factory.c    # Objects factory
      ch_heap.c       # Heap allocator
      ch_mempools.c   # Memory pools
      ch_msgs.c       # Synchronous messages
      ch_pipes.c      # Pipes
      ch_delegate.c   # Delegate threads
      ...
```

The OSLIB header files are part of the ChibiOS source tree and are found via the include paths established by the platform's generated `Makefile`. No generated header is needed — OSLIB behaviour is controlled at compile time by `chconf.h` properties owned by the kernel component (RT or NIL), not by OSLIB itself.

---

## 5. Position in the Dependency Graph

OSLIB slots between the kernel and any component requiring extended services:

```
kernel      ──provides──►  features.kernel (excl.)
            ──provides──►  features.chapi (excl.)
                                    │
                                    ▼
oslib       ──requires──►  features.chapi
            ──provides──►  features.chapiext (excl.)
                                    │
                                    ▼
consumers   ──requires──►  features.chapiext
```

Components that require only base CH API services (`chThdCreateStatic`, `chMtxLock`, etc.) continue to declare `features.chapi` in their `<requires>` and are unaffected by whether OSLIB is present. Components that require OSLIB services (heap, dynamic threads, factory) declare `features.chapiext`.

The OSAL also requires only `features.chapi`, not `features.chapiext` — the HAL does not depend on OSLIB.

---

## 6. How Other Components Declare an OSLIB Dependency

A component requiring OSLIB services:

```xml
<requires>
  <feature id="features.chapiext"/>
</requires>
```

As with all feature dependencies, the requiring component has no knowledge of which component provides `features.chapiext`. If a future alternative extension layer were to provide the same feature ID, it would satisfy the dependency transparently.

---

## 7. Open Items

1. **OSLIB configuration**: some OSLIB services (heap, memory pools, factory) have tuneable parameters currently controlled via `chconf.h` in the kernel component. If OSLIB-specific compile-time configuration is separated into its own header in a future ChibiOS release, the OSLIB component would gain a `<sections>` block and a generated `chlibconf.h`. This is noted as a likely future revision.

2. **Partial OSLIB**: OSLIB contains several largely independent service groups (allocators, dynamic threads, factory, pipes, delegates). A future refinement could split these into sub-features (`features.chapiext.heap`, `features.chapiext.factory`, etc.) so components can express finer-grained dependencies. Deferred — `features.chapiext` as a single token is sufficient for v1.
