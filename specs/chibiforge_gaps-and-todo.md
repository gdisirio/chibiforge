# **ChibiForge — Gaps and TODO (v1 Readiness)**

## **1. Critical Gaps (Blockers)**

### **1.1 Version-Aware Component Binding**

* Implement exact `(component id, component version)` resolution in loader, registry, generator, and UI
* Reject silent fallback to a different component version
* Define and implement explicit GUI upgrade flow for newer component versions
* Surface version mismatch diagnostics clearly during load/generation

---

### **1.2 Schema-Aware Configuration Validation**

* Validate each `<component>` payload against its resolved component schema during load
* Report unknown sections/properties and incompatible payload structure as warnings/errors
* Keep `chibiforge_xcfg.xsd` as envelope validation only

---

## **2. Specification Consolidation**

### **2.1 Preset Model Integration**

* Keep split `v13` spec set aligned with:

  * preset XSD
  * UI spec (loading / logging behavior)
  * config version-binding rules

---

### **2.2 Full Spec Regeneration**

* Maintain cross-document consistency in the split `v13` corpus
* Ensure consistent terminology:

  * schema paths
  * normalization
  * targets
  * version binding
  * list semantics

---

### **2.3 Deferred Contract Items**

Tracked but intentionally out of the current normative spec:

* preset metadata embedded in `component/schema.xml`
* structured target-specific encoding for list values in `.xcfg`
* preset apply/export support for target-specific list values once that encoding is defined

---

## **3. Ecosystem Expansion**

### **3.1 Core Driver Components**

Implement at least 2–3 real components to validate the system:

* SPI
* UART / Serial
* I2C (optional)

Goals:

* validate HAL layer design
* stress-test configuration + generation
* validate presets on real components

---

### **3.2 Service Components (Optional for v1)**

* lwIP
* VFS / file systems (LittleFS, FatFS)

Purpose:

* validate higher-level dependency model
* test feature resolution across layers

---

## **4. UI Implementation Validation**

### **4.1 Behavior Verification**

Validate against UI spec:

* `@cond:` visibility/editability correctness
* `@ref:` dynamic resolution
* list drill-down UX
* multi-target indicator behavior
* DOM update rules (focus loss, validation)

---

### **4.2 Known Deferred Features**

Not required for v1, but tracked:

* full Undo/Redo stack
* Files tab provenance coloring
* New Configuration workflow

---

## **5. Open Design Items (Non-blocking)**

### **5.1 Platform Component**

* device variant selection within family
* multi-core support
* linker script variants
* non-Makefile build systems

---

### **5.2 ChPort Component**

* automatic chport selection
* Cortex-M variant handling (M4 vs M7, FPU)

---

### **5.3 OSLIB Component**

* potential future configuration header generation
* possible split into finer-grained features

---

## **6. Nice-to-Have Improvements**

* Detect duplicate normalized schema paths
* Optional validation tooling for presets vs schema
* Improved logging formatting / filtering
* Spec-derived test case generation (future)

---

## **7. Summary**

* Core architecture: **complete**
* Major blockers:

  * version-aware component binding
  * schema-aware configuration validation
* Remaining work:

  * spec consolidation
  * component implementation
  * UI validation

---

## **8. Priority Order**

1. Implement version-aware component binding
2. Implement schema-aware configuration validation
3. Implement real components
4. Validate UI behavior
5. Expand ecosystem

---

# **End of Document**
