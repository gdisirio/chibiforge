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

For preset files, the authority order is:

1. `cli/src/main/resources/schemas/chibiforge_preset.xsd`
2. this document's revised preset semantics in §10

If these differ, the XSD governs the XML structure and §10 governs application semantics.

A preset file MUST:

* conform to `cli/src/main/resources/schemas/chibiforge_preset.xsd`
* declare the target component ID in the root `preset @id` attribute
* match the target component before application

A preset file SHALL contain:

* a root `<preset>` element with required attributes:
  * `name`
  * `id`
  * `version`
* a `<sections>` payload matching the preset XSD structure
* for `<property type="list">`, an `<items>` payload containing zero or more `<item>` elements
* each list `<item>` SHALL contain one or more `<sections>` wrappers, each containing schema-named `<section>` / `<property>` entries
* no target declarations
* no feature definitions

---

## **5. Preset Application Semantics**

### **5.1 General**

Applying a preset SHALL:

* update only the properties explicitly defined in the preset
* leave all other component values unchanged
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

* serialize the current component configuration into the preset XSD format
* produce a standalone preset file

---

### **6.2 Export Semantics**

The exported preset SHALL reflect:

* the currently selected target
* the effective values for that target

The preset SHALL be fully materialized:

* inherited values MUST be resolved
* no target structures SHALL be included
* the exported root `preset @id` SHALL equal the component ID

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

* preset metadata in `schema.xml`
* preset discovery rules (implicit vs declared)
* automated migration of legacy board components

---

# **End of Document**

---

# **Addendum — Preset Semantics Revision (v12)**

## **10. Preset Application Model (Revised)**

### **10.1 General**

Preset application semantics are revised from **full replacement** to a **permissive patch model**.

A preset SHALL update only the `<property>` values it explicitly defines. All other values in the target `<component>` SHALL remain unchanged.

Presets:

* SHALL NOT create persistent references in the configuration
* SHALL NOT participate in dependency resolution
* SHALL NOT modify the preset file itself

---

### **10.2 Component Matching**

A preset MUST declare the component ID in the root `preset @id` attribute.

* The value MUST match the target `<component>@id`.
* If the preset `@id` does not match, preset application SHALL fail.
* No partial application SHALL occur in case of mismatch.

---

### **10.3 Property Identification (Schema Path)**

Preset values SHALL be matched using the **schema path** of each `<property>`.

A schema path is defined as:

* the hierarchy of enclosing `<section @name>` elements
* followed by the terminal `<property @name>`

Preset files SHALL use the original `<section @name>` and `<property @name>` values as defined in the component schema.

---

### **10.4 Path Normalization and Matching**

Matching SHALL be performed using **normalized schema paths**.

* Each `<section @name>` and `<property @name>` SHALL be normalized using the standard ChibiForge identifier normalization rule defined in the main specification.
* The normalized elements SHALL be joined using `/` to form the schema path.

Example:

```text
Initialization Settings / System clock source
→ initialization_settings/system_clock_source
```

Normalization SHALL be applied only for matching and SHALL NOT modify the preset file.

---

### **10.5 Property Application Semantics**

Preset application proceeds per schema path.

For each `<property>` defined in the preset:

* If a `<property>` with the same normalized schema path exists in the target component schema, its value SHALL be applied.
* If no matching schema path exists, the preset entry SHALL be ignored.

For `<property>` elements defined in the component schema but not present in the preset:

* The existing value SHALL remain unchanged.

---

### **10.6 List Property Semantics**

For `<property type="list">`:

* Matching is performed using the normalized schema path of the list property.
* If the property is present in the preset, the entire list SHALL be replaced.
* No element-by-element merge SHALL be performed.
* If the property is not present in the preset, the existing list SHALL remain unchanged.
* Preset list values SHALL use the structured XSD form:

  * `<items>`
  * `<item>`
  * `<sections>`
  * nested schema-named `<section>` / `<property>` entries
* Within each replacement item, nested `<section @name>` and `<property @name>` matching SHALL follow the list property's nested schema.
* Unspecified nested item properties SHALL be initialized using the component schema default values.

---

### **10.7 Target Model Interaction**

Preset application is target-local.

For each matched `<property>`:

* If a `<targetValue target="...">` exists for the active target, it SHALL be replaced.
* Otherwise, the `default` attribute SHALL be replaced.

Tools MAY provide an option to create `<targetValue>` elements for properties that currently inherit from `default`.

Current implementation note:

* target-local replacement is implemented for scalar properties
* `<property type="list">` replacement currently applies only to single-target list values
* replacement of list values using target-specific encoding is not yet supported because the `.xcfg` format does not currently define a structured target-specific encoding for list items analogous to scalar `<targetValue>` entries

---

### **10.8 Schema Evolution Behavior**

Preset application SHALL be tolerant to schema changes.

If a schema path defined in the preset no longer exists in the current component schema (due to:

* renamed `<section>` elements,
* inserted or removed sections,
* renamed or removed `<property>` elements),

then:

* the preset entry SHALL be ignored,
* a warning SHALL be logged.

---

### **10.9 Logging Requirements**

Preset application SHALL produce structured logs.

#### **10.9.1 Ignored Properties**

If a preset `<property>` does not match any schema path:

* The property SHALL be ignored.
* A warning SHALL be logged.

Example:

```
[Preset] Ignored property 'Initialization Settings / PLL old mode' — path not present in component schema
```

---

#### **10.9.2 Unmodified Properties**

If a schema-defined `<property>` is not present in the preset:

* Its value SHALL remain unchanged.
* An informational log entry MAY be emitted.

Example:

```
[Preset] Property 'DMA Settings / Channels' not defined in preset — existing value retained
```

---

#### **10.9.3 Summary**

Tools SHOULD emit a summary after preset application:

```
Preset applied:
- 12 properties updated
- 2 properties ignored
- 5 properties unchanged
```

---

### **10.10 Error Conditions**

The following SHALL be treated as errors:

* preset `@id` mismatch with `<component>@id`
* malformed preset XML
* invalid value type for a `<property>` (violates schema constraints)

In such cases, preset application SHALL abort.

---

### **10.11 Export Semantics (Unchanged)**

Preset export behavior remains unchanged:

* A preset SHALL serialize a single `<preset>` document conforming to `cli/src/main/resources/schemas/chibiforge_preset.xsd`
* Values SHALL reflect the currently selected target
* All inherited values MUST be resolved into explicit values
* No `<target>` or `<targetValue>` structures SHALL be included

---

## **11. Rationale**

This revision:

* aligns preset matching with the hierarchical `<section>` / `<property>` schema model
* uses normalized schema paths for robust matching while preserving readable preset files
* enables safe reuse of presets across schema evolution
* preserves deterministic behavior for `<property type="list">`
* avoids ambiguity caused by duplicate property names in different sections

Presets remain **deterministic, schema-aligned configuration patches** applied onto a `<component>` instance.

---

## **12. Template Data Model Simplification and Removal of `configuration`**

### **12.1 Overview**

This addendum defines a simplification of the FMPP data model exposed to templates.

The `configuration` variable is removed from the template context.

Templates SHALL operate exclusively on:

* resolved configuration data (`doc`)
* cross-component configuration (`components`)
* component-local resources

---

### **12.2 Rationale**

The previous model exposed a `configuration` variable containing execution context such as:

* project root paths
* generated output paths
* target information

This introduced:

* unnecessary coupling between templates and the execution environment
* potential non-deterministic behavior
* misuse of templates for filesystem concerns

The updated model enforces:

> Templates operate only on configuration data, not on generation context.

---

### **12.3 Template Data Model**

The following variables are available to FMPP templates:

| Variable        | Scope             | Description                                            |
| --------------- | ----------------- | ------------------------------------------------------ |
| `doc`           | Current component | Fully resolved configuration for the current component |
| `components`    | Global            | Fully resolved configurations of all components        |
| `<resource id>` | Current component | Parsed resource declared in the component              |

---

### **12.4 Removal of `configuration`**

#### **12.4.1 Rule**

The `configuration` variable SHALL NOT be exposed to templates.

---

#### **12.4.2 Consequences**

Templates SHALL NOT:

* reference project root paths
* reference generated output paths
* reference target information
* perform logic based on execution environment

---

### **12.5 Target Handling**

Target selection and resolution are defined as:

* a UI concern
* resolved before generation
* not visible to templates

The DOM provided to the generator SHALL already contain values for the active target.

---

### **12.6 Filesystem Responsibility**

All filesystem-related concerns are handled by the generator:

* output paths
* file placement
* directory structure

Templates SHALL produce content only and SHALL NOT depend on filesystem layout.

---

### **12.7 Migration Notes**

#### **12.7.1 Deprecated Usage**

Any existing template usage of:

```ftl
${configuration.*}
```

is invalid in v12.

---

#### **12.7.2 Migration Strategy**

* Remove all references to `configuration`
* Move path-related logic into the generator
* Ensure templates rely only on configuration data and resources

---

### **12.8 Determinism Guarantee**

With this change:

* template output depends only on configuration data
* generation is independent of execution environment
* builds are reproducible given identical inputs

---

### **12.9 Scope**

This change affects:

* template execution model (FMPP)
* generator/template boundary

It does **not** affect:

* schema structure
* configuration format (`chibiforge.xcfg`)
* component packaging

---

### **12.10 Status**

* Applies to v12
* Mandatory for all compliant tools and templates

---

# **End of Addendum**
