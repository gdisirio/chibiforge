# **ChibiForge — Future Ideas (v2+)**

## **0. Roadmap Classification**

| # | Capability                            | Target Version | Notes                                                            |
| - | ------------------------------------- | -------------- | ---------------------------------------------------------------- |
| 2 | External Source Linking               | v2             | Requires generator-level path resolution and environment binding |
| 3 | Cross-Component References (`@xref:`) | v2             | Requires global DOM access and dependency validation             |
| 4 | Computed Properties (`<compute>`)     | v2             | Requires XPath-based evaluation on load and update               |
| 5 | Enhanced `<image>` display            | v1.x           | Pure UI feature, no generator or DOM impact                      |
| 6 | `<line/>` layout separator            | v1.x           | UI-only layout primitive                                         |
| 7 | Embedded mini-scripts                 | v2             | Schema-level dynamic validation and computed choices/defaults    |
| 8 | Java extension mechanism              | v2             | Plugin-JAR-backed advanced extension point                       |
| 9 | Dynamic field population              | v2             | Runtime choice/default sourcing beyond static catalogs           |

---

## **1. Purpose**

This document captures potential future capabilities for ChibiForge beyond v1.

These items are **not required for v1 readiness** and are intentionally deferred or staged to avoid unnecessary complexity in the initial release.

---

## **2. External Source Linking for Component Content**

### **Target: v2**

### **2.1 Overview**

Components MAY reference static files and directories from an external source tree instead of embedding them directly in the component container.

---

### **2.2 Scope**

Generator-only feature.

---

### **2.3 Resolution Rules**

* Environment variables MUST resolve
* Paths MUST exist
* Failure is fatal

---

### **2.4 Conflict Resolution**

If both local and linked content exist for the same logical path:

* Local component content SHALL take precedence
* Linked content SHALL be ignored
* A warning SHOULD be logged

---

## **3. Cross-Component Property References (`@xref:`)**

### **Target: v2**

### **3.1 Overview**

Properties MAY derive their values from properties defined in other components.

---

### **3.2 Semantics**

* `@xref:` resolves against the global `components` DOM
* The resolved value SHALL be stored as a plain value in `chibiforge.xcfg`

---

### **3.3 Persistence Model**

Derived values are serialized as plain values in `chibiforge.xcfg`.

On load:

* Tools MUST recompute all `@xref:` values
* Recomputed values SHALL override stored values in memory

On save:

* Tools SHOULD write recomputed values back

---

### **3.4 Constraints**

* A property SHALL NOT reference another derived property
* No chaining (`@xref:` → `@xref:` or `@xref:` → `<compute>`)
* Circular dependencies are forbidden

---

## **4. Computed Properties via `<compute>`**

### **Target: v2**

### **4.1 Overview**

A `<property>` MAY include an optional `<compute>` element defining a derived value using XPath.

---

### **4.2 Semantics**

* `<compute>` is evaluated using XPath on the live DOM
* The result SHALL define the effective value of the property
* The value SHALL be stored as a plain value in `chibiforge.xcfg`

---

### **4.3 Persistence Model**

On load:

* Tools MUST recompute all `<compute>` values
* Recomputed values SHALL override stored values

On save:

* Tools SHOULD persist recomputed values

---

### **4.4 Constraints**

* XPath-based expressions only
* Pure and read-only
* No side effects
* No chaining with other derived values

---

### **4.5 Error Handling**

* Evaluation failure results in error state
* Stored value MUST NOT be used as fallback

---

## **5. Enhanced `<image>` Display Element**

### **Target: v1.x**

### **5.1 Overview**

The `<image>` element is extended to support:

* image-only
* text-only
* image + text

---

### **5.2 Enhancements**

* `file` becomes optional
* `<text>` optional
* `text_position`: `below`, `right`
* `text_align`: `left`, `center`, `right`

---

### **5.3 Scope**

* UI-only
* No DOM impact
* No generator impact

---

## **6. Layout Separator Element (`<line/>`)**

### **Target: v1.x**

### **6.1 Overview**

A `<line/>` element MAY be used inside `<layout>` to insert a visual separator.

---

### **6.2 Behavior**

* Forces row break
* Renders horizontal line
* Spans full width

---

### **6.3 Scope**

* UI-only
* No DOM impact
* No persistence
* No generator impact

---

## **7. Status**

* v1.x items may be promoted into the main specification
* v2 items require architectural extensions and remain deferred

---

## **8. Embedded Mini-Scripts for Properties**

### **Target: v2**

Properties MAY include small inline scripts for validation and dynamic behavior,
embedded directly in `schema.xml`. This keeps simple logic self-contained within
the component definition and avoids requiring compiled Java classes for every
dynamic rule.

### **8.1 Possible Syntax**

```xml
<property name="vdd" type="int" brief="Supply voltage" ...>
  <script event="validate">
    if (doc.initialization_settings.use_dma == "true" &amp;&amp; value &lt; 250)
      return "VDD must be >= 250mV when DMA is enabled";
  </script>
</property>

<property name="clock_source" type="enum" brief="Clock source" ...>
  <script event="choices">
    return ["PLL", "HSI", "HSE"];
  </script>
</property>
```

### **8.2 Possible Script Events**

* `validate`: called on focus loss. Receives the current `value` and `doc`.
  Returns `null` for success or an error/warning message string.
* `choices`: dynamically computes the list of allowed values for `enum`
  properties, replacing or supplementing `enum_of`.
* `default`: dynamically computes a default value based on other property
  values.

### **8.3 Implementation Considerations**

* Scripts would be evaluated by an embedded JavaScript engine such as GraalJS.
* Scripts have read-only access to the live data model (`doc`, resource
  variables) and the current `value`.
* Scripts should remain minimal and fast enough to avoid affecting GUI
  responsiveness.

---

## **9. Java Extension Mechanism (Plugin JARs)**

### **Target: v2**

For more complex scenarios beyond what mini-scripts can handle, plugin JARs
could include compiled Java classes that participate in the configuration
lifecycle.

Possible uses:

* custom validators for cross-field or hardware-specific checks
* dynamic field population from external data sources
* custom transformations before values reach the data model or generated output

### **9.1 Mechanism**

* Property or section elements reference a Java class name, for example
  `validator="org.chibios.hal.validators.VddValidator"`.
* The class lives in the plugin JAR and implements a ChibiForge-defined
  interface.
* At runtime, ChibiForge loads the class via a classloader and calls it with the
  relevant DOM/data model.

### **9.2 Considerations**

* Requires classloader management and a stable API contract.
* Security sandboxing may be needed for untrusted plugins.
* Applies only to plugin JAR containers, not plain filesystem containers.
* Mini-scripts should remain the preferred mechanism for simple logic.

---

## **10. Dynamic Field Population**

### **Target: v2**

Enum choices and list item defaults could be populated dynamically at runtime,
going beyond static `enum_of` CSV values and `@ref:` resource lookups.

Possible mechanisms:

* the `choices` mini-script event
* Java extension classes for external data sources

Possible use cases:

* available serial ports
* connected hardware
* device family databases
* firmware version catalogs

---

# **End of Document**
