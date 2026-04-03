# **ChibiForge — Gaps and TODO (v1 Readiness)**

## **1. Critical Gaps (Blockers)**

### **1.1 `chibiforge.xcfg` XSD**

* Define a formal XSD for `chibiforge.xcfg`
* Align with:

  * component schema (`schema.xml`)
  * preset XSD (`chibiforge_preset.xsd`)
* Must cover:

  * `<component>` structure
  * `<sections>` / `<property>` serialization
  * multi-target (`default` + `<targetValue>`)
  * list encoding
* This is the **only true structural blocker**

---

## **2. Specification Consolidation**

### **2.1 Preset Model Integration**

* Merge v12 preset semantics into main specification
* Remove reliance on upgrade document
* Ensure alignment across:

  * main spec
  * preset XSD
  * UI spec (loading / logging behavior)

---

### **2.2 Full Spec Regeneration**

* Regenerate unified specification document
* Eliminate:

  * v11 + upgrade fragmentation
  * duplicated or outdated sections
* Ensure consistent terminology:

  * schema paths
  * normalization
  * targets
  * list semantics

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
* Major blocker: **missing `chibiforge.xcfg` XSD**
* Remaining work:

  * spec consolidation
  * component implementation
  * UI validation

---

## **8. Priority Order**

1. `chibiforge.xcfg` XSD
2. Merge preset v12 into main spec
3. Implement real components
4. Validate UI behavior
5. Expand ecosystem

---

# **End of Document**

