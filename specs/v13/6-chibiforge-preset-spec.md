# ChibiForge Preset Specification

*(chibiforge-preset-spec.md)*

---

## 1. Purpose and Scope

This document defines the **structure and semantics of ChibiForge presets**.

Authority order for this document is:

1. `chibiforge_preset.xsd`
2. this specification

If these differ, the XSD governs the XML structure and this document governs higher-level semantics.

This document establishes:

* what a preset is
* how presets are matched to components
* how preset content is applied
* how presets interact with targets
* how presets are exported

This document does **not** define:

* component schema structure
* configuration file structure
* generator behavior
* UI workflows

---

## 2. Preset Concept

A preset is a predefined configuration fragment for a single component.

Presets:

* are scoped to exactly one component ID
* are applied only through explicit tool or user action
* do not participate in dependency resolution
* do not create persistent links in the configuration
* do not modify the preset source when applied

After application, the resulting component configuration is fully editable.

---

## 3. Preset Sources

### 3.1 Bundled Presets

A component container MAY include presets under:

```text id="k9n2jx"
component/presets/
```

Bundled presets:

* are read-only
* are associated with the owning component

---

### 3.2 External Presets

Tools SHALL also support loading presets from arbitrary filesystem locations.

External presets:

* are user-managed files
* are not part of component containers

---

## 4. XSD-Defined Structure

### 4.1 Root Element

A preset file SHALL contain a root `<preset>` element.

The root element SHALL include the attributes required by the XSD:

* `name`
* `id`
* `version`

The `id` attribute identifies the target component ID.

---

### 4.2 Payload Structure

A preset file SHALL contain a `<sections>` payload conforming to `chibiforge_preset.xsd`.

For list properties, the XSD-defined structured form SHALL be used, including:

* `<items>`
* `<item>`
* nested `<sections>`

---

## 5. Component Matching

A preset SHALL declare the target component ID in `preset @id`.

Rules:

* `preset @id` MUST match the target component `@id`
* if the IDs do not match, application SHALL fail
* no partial application SHALL occur on ID mismatch

---

## 6. Schema Path Model

Preset entries are matched using schema paths.

A schema path is defined as:

* the hierarchy of enclosing section names
* followed by the terminal property name

Example:

```text id="78v2pk"
Initialization Settings / System clock source
```

---

## 7. Path Normalization

Matching SHALL use normalized schema paths.

Each section name and property name SHALL be normalized using the Core Specification identifier normalization rule.

Example:

```text id="t80dt0"
Initialization Settings / System clock source
-> initialization_settings/system_clock_source
```

Normalization is used only for matching and SHALL NOT modify the preset file.

---

## 8. Application Model

Preset application uses a **permissive patch model**.

Applying a preset SHALL:

* update only the properties explicitly defined in the preset
* leave all other properties unchanged
* NOT create a persistent reference to the preset
* NOT modify the preset file

No implicit full reset SHALL occur for omitted scalar properties.

---

## 9. Scalar Property Semantics

For each scalar property defined in the preset:

* if a matching schema path exists in the target component schema, the value SHALL be applied
* if no matching schema path exists, the preset entry SHALL be ignored and a warning SHALL be logged

For scalar properties defined in the component schema but omitted from the preset:

* the existing value SHALL remain unchanged

---

## 10. List Property Semantics

For `<property type="list">`:

* matching SHALL be performed using the normalized schema path of the list property
* if the list property is present in the preset, the entire list SHALL be replaced
* no element-by-element merge SHALL be performed
* if the list property is absent from the preset, the existing list SHALL remain unchanged

Within each replacement item:

* nested section/property matching SHALL follow the list item's nested schema
* unspecified nested properties SHALL be initialized using schema default values

---

## 11. Target Model Interaction

Presets are target-local snapshots.

Presets:

* SHALL NOT define targets
* SHALL NOT modify the set of project targets
* SHALL be applied to the currently selected target context

---

### 11.1 Scalar Properties

For each matched scalar property:

* if an explicit target-specific value exists for the active target, it SHALL be replaced
* otherwise, the inherited/default value slot SHALL be replaced

If the active target is `"default"`, application SHALL update the default value.

---

### 11.2 Optional Explicit Override Creation

Tools MAY provide an option to create explicit target-specific values for inherited scalar properties.

When enabled:

* it applies only if the active target is not `"default"`
* if no explicit value exists for the active target, one SHALL be created
* the preset value SHALL then be written to that explicit target-specific value

When disabled:

* inherited values SHALL be written to the default value slot

---

### 11.3 List Properties

List properties SHALL be treated as single-target.

Preset-driven replacement of list values using target-specific encoding is not supported.

---

## 12. Schema Evolution Behavior

Preset application SHALL be tolerant of schema evolution.

If a preset entry refers to a schema path that no longer exists:

* that entry SHALL be ignored
* a warning SHALL be logged

This tolerance applies to cases such as:

* renamed sections
* removed sections
* renamed properties
* removed properties

---

## 13. Error Conditions

The following SHALL be treated as errors:

* preset/component ID mismatch
* malformed preset XML
* XSD-invalid preset structure
* invalid value type for a property
* other schema-incompatible content that prevents safe application

On error, preset application SHALL abort.

---

## 14. Logging Requirements

Preset application SHALL produce structured logs.

---

### 14.1 Ignored Properties

If a preset property does not match any schema path:

* it SHALL be ignored
* a warning SHALL be logged

Example:

```text id="wqk7mj"
[Preset] Ignored property 'Initialization Settings / PLL old mode' — path not present in component schema
```

---

### 14.2 Unmodified Properties

If a schema-defined property is not present in the preset:

* its existing value SHALL remain unchanged
* tools MAY emit an informational log entry

---

### 14.3 Summary

Tools SHOULD emit a summary after application.

Example:

```text id="0s4r0e"
Preset applied:
- 12 properties updated
- 2 properties ignored
- 5 properties unchanged
```

---

## 15. Preset Export

Tools SHALL support exporting presets.

Exporting a preset SHALL:

* serialize the current component configuration into the preset XSD format
* produce a standalone preset file
* set `preset @id` to the component ID

The exported preset SHALL reflect:

* the currently selected target
* the effective values visible for that target

The exported preset SHALL be fully materialized:

* inherited scalar values SHALL be resolved
* no target declarations SHALL be included
* no target-specific override structures SHALL be included

---

## 16. Backward Compatibility

Existing configurations:

* remain valid
* do not require presets

Existing component models MAY continue to function without presets.

---

## 17. Non-Goals

This specification does NOT define:

* UI presentation of preset selection
* generator behavior
* automatic migration of legacy configurations
* preset inheritance or chaining

---
