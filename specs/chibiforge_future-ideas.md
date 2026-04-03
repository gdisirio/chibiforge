# **ChibiForge — Future Ideas (v2+)**

## **0. Roadmap Classification**

| # | Capability                            | Target Version | Notes                                                            |
| - | ------------------------------------- | -------------- | ---------------------------------------------------------------- |
| 2 | External Source Linking               | v2             | Requires generator-level path resolution and environment binding |
| 3 | Cross-Component References (`@xref:`) | v2             | Requires global DOM access and dependency validation             |
| 4 | Computed Properties (`<compute>`)     | v2             | Requires XPath-based evaluation on load and update               |
| 5 | Enhanced `<image>` display            | v1.x           | Pure UI feature, no generator or DOM impact                      |
| 6 | `<line/>` layout separator            | v1.x           | UI-only layout primitive                                         |

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

# **End of Document**

