# **ChibiForge Specification — Upgrade Notes (v11 → v12 draft)**

## **1. Purpose**

This document records incremental changes and extensions to **ChibiForge Specification v11**.

It is intended as a temporary companion document until a full regeneration of the specification is performed.

---

## **2. Component Identity and Naming**

### **2.1 Canonical Identity Rule**

The following identifiers MUST be identical:

* `component/schema.xml` → `<component id="...">`
* `META-INF/MANIFEST.MF` → `Bundle-SymbolicName`
* Component container name:

  * directory name (filesystem container)
  * JAR name prefix (plugin container)

---

### **2.2 Naming Convention**

Component IDs SHALL follow reverse-domain naming:

`<domain>.<project>.components.<category>.<name>`

Examples:

* `org.chibios.chibiforge.components.platform.stm32g4xx`
* `org.chibios.chibiforge.components.board.stm32g4xx`

Third-party components MUST use their own domain.

---

### **2.3 Container Naming**

Filesystem:

* directory name MUST equal component ID

Plugin JAR:

* naming MUST follow: `<componentId>_<version>.jar`

---

### **2.4 Validation**

Tools MUST reject components where:

* container name ≠ component ID
* `Bundle-SymbolicName` ≠ component ID
* `schema.xml @id` ≠ component ID

---

## **3. Preset Concept**

### **3.1 Definition**

A preset is a predefined configuration fragment for a single component.

Presets:

* are scoped to a specific component ID
* do not participate in dependency resolution
* do not create persistent references in the configuration

---

### **3.2 Storage**

Components MAY include presets under:

`component/presets/`

Presets in this directory are:

* read-only
* owned by the component

---

### **3.3 External Presets**

Tools SHALL support loading presets from arbitrary filesystem locations.

External presets:

* are user-managed files
* are not part of component containers

---

## **4. Preset File Requirements**

A preset file MUST:

* declare a `componentId`
* match the target component before application

A preset file SHALL contain:

* a single component configuration fragment
* no target declarations
* no feature definitions

---

## **5. Preset Application Semantics**

### **5.1 General**

Applying a preset SHALL:

* replace the current component configuration in memory
* NOT modify the preset file
* NOT create any persistent link to the preset

---

### **5.2 Target Model**

Presets are target-local snapshots.

Presets:

* SHALL NOT define or modify project targets
* SHALL be applied to the currently selected target context

---

### **5.3 Property Semantics**

#### **5.3.1 Single-target properties**

* value SHALL be replaced

---

#### **5.3.2 Multi-target properties**

For each property:

* if an explicit value exists for the active target → replace it
* otherwise → replace the `default` value

---

### **5.4 Optional Target Override Creation**

Tools MAY provide a mechanism to create explicit target values for multi-target properties that currently inherit from `default`.

When enabled:

* applies only if active target ≠ `default`
* if no explicit value exists:

  * a target-specific value SHALL be created
  * preset value SHALL be written to that target

Existing target values SHALL always be replaced.

---

## **6. Preset Export**

### **6.1 General**

Tools SHALL support exporting presets.

Exporting a preset SHALL:

* serialize the current component configuration
* produce a standalone preset file

---

### **6.2 Export Semantics**

The exported preset SHALL reflect:

* the currently selected target
* the effective values for that target

The preset SHALL be fully materialized:

* inherited values MUST be resolved
* no target structures SHALL be included

---

## **7. Board Component Model Revision**

### **7.1 Change of Approach**

The board component model is revised as follows:

* one board component per MCU family
* commercial boards are represented as presets

---

### **7.2 Example**

Instead of:

* `board.nucleo_g474re`
* `board.stm32g4discovery`

Use:

* `board.stm32g4xx`

  * presets:

    * `nucleo_g474re`
    * `stm32g4discovery`

---

### **7.3 Implications**

* board generation logic remains in the component
* board-specific data moves to presets
* component count is reduced
* maintenance complexity is reduced

---

## **8. Backward Compatibility**

Existing configurations:

* remain valid
* continue to function without presets

Existing board components:

* MAY be retained temporarily
* SHOULD be migrated to preset-based model

---

## **9. Deferred Items**

The following are deferred to full specification update:

* preset XML schema (XSD)
* preset metadata in `schema.xml`
* preset discovery rules (implicit vs declared)
* automated migration of legacy board components

---

# **End of Document**

