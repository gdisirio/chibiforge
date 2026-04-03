# ChibiForge Configuration Specification

*(chibiforge-configuration-spec.md)*

---

## 1. Purpose and Scope

This document defines the **structure and semantics of the ChibiForge configuration file** (`chibiforge.xcfg`).

Authority order for this document is:

1. `chibiforge_xcfg.xsd`
2. this specification

If these differ, the XSD governs the XML structure and this document governs higher-level semantics.

This document establishes:

* the location of the configuration file
* the XML structure defined by the XSD
* the semantic role of targets and component configuration content
* conventions for value storage and target resolution

This document does **not** define:

* component schema structure
* component container layout
* generator behavior
* UI workflows

---

## 2. Configuration File Location

The configuration file SHALL be located at:

```text
<projectRoot>/chibiforge.xcfg
```

There is exactly one configuration file per project.

---

## 3. XSD-Defined XML Structure

### 3.1 Root Element

The root element SHALL be:

```xml
<chibiforgeConfiguration
    xmlns="http://chibiforge/schema/config"
    toolVersion="..."
    schemaVersion="...">
  ...
</chibiforgeConfiguration>
```

The root element content model is:

1. optional `<targets>`
2. required `<components>`

Root attributes are not yet defined by the XSD, but ChibiForge tools SHALL preserve and write:

* `toolVersion`
* `schemaVersion`

These attributes are semantic metadata for the configuration document.

---

### 3.2 `<targets>`

If present, the `<targets>` element contains zero or more:

```xml
<target id="..."/>
```

Rules defined by the XSD:

* `id` is required
* `id` must be a non-empty string

---

### 3.3 `<components>`

The `<components>` element is required.

It contains zero or more:

```xml
<component id="..." version="...">
  ...
</component>
```

Rules defined by the XSD:

* `id` is required
* `version` is required
* `version` must match the pattern:

```text
(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)
```

* component content is defined using `<any>`

The XSD therefore permits an arbitrary nested payload inside each `<component>` element.

---

## 4. Semantic Model

### 4.1 Purpose of the Configuration

The configuration file stores:

* the set of configured components
* the declared targets
* the component-local configuration payload for each component

The structure of the payload inside each `<component>` is governed by the corresponding component schema, not by `chibiforge_xcfg.xsd`.

---

### 4.2 Component Binding

Each `<component>` element semantically refers to a component definition identified by its `id`.

The `version` attribute identifies the component definition version expected by the configuration.

Tools SHALL resolve component bindings by the pair:

* component `id`
* component `version`

Rules:

* if the exact `(id, version)` cannot be resolved, the binding is not satisfied
* tools SHALL treat an unsatisfied binding as an error for generation
* interactive tools MAY offer an explicit update path when a later version of the same `id` is available
* tools SHALL NOT silently switch a configuration to a different component version

Version resolution policy:

* exact `(id, version)` match SHALL be preferred
* if no exact match exists and one or more later versions of the same `id` are available, interactive tools MAY prompt the user to upgrade
* if only older versions are available, tools SHALL NOT automatically downgrade
* non-interactive tools SHALL fail on unresolved exact-version binding

If the user accepts an upgrade:

* the configuration `component @version` SHALL be updated to the selected later version
* the existing component payload SHALL be retained
* the resulting payload SHALL then be validated against the new component schema
* any incompatibilities SHALL be surfaced as warnings or errors

---

### 4.3 Component Payload

The nested content inside a `<component>` element represents the component's configuration data.

Although the XSD allows arbitrary content, ChibiForge tools SHALL interpret this payload according to the corresponding component schema.

In practice, this means:

* section and property structure is schema-driven
* element naming follows the schema-defined configuration model
* values are interpreted according to schema-declared property types

---

## 5. Targets

### 5.1 Target Set

Targets represent named configuration variants within the project.

Rules:

* the logical `"default"` target SHALL always exist
* additional targets MAY be declared
* if `<targets>` is omitted, the configuration SHALL be interpreted as having only the `"default"` target

---

### 5.2 Declared Targets

If `<targets>` is present, it defines the explicitly declared target IDs.

If `"default"` is not explicitly listed, tools SHALL still treat `"default"` as logically present.

Tools SHOULD treat `<targets>` as the set of additional targets beyond the implicit `"default"` target.

Tools SHOULD NOT emit:

```xml
<target id="default"/>
```

unless compatibility with older documents or tools explicitly requires it.

---

## 6. Property Value Storage Conventions

The XSD does not define the internal representation of scalar values, target-specific overrides, or list payloads. Those are ChibiForge semantic conventions interpreted by tools according to the component schema.

### 6.1 Single-Target Scalar Property

A scalar property shared by all targets is conventionally stored as element text content.

Example:

```xml
<vdd>300</vdd>
```

---

### 6.2 Multi-Target Scalar Property

A scalar property with per-target overrides is conventionally stored using:

* a `default` attribute
* zero or more `<targetValue>` child elements

Example:

```xml
<vdd default="300">
  <targetValue target="debug">330</targetValue>
  <targetValue target="release">280</targetValue>
</vdd>
```

This structure is a ChibiForge semantic convention, not an XSD-level rule.

---

### 6.3 Multi-Target Scalar Resolution

For a selected target, scalar values SHALL resolve as follows:

1. if the selected target is `"default"` and a `default` attribute exists, use it
2. if a matching `<targetValue target="...">` exists, use that value
3. otherwise, if a `default` attribute exists, use the `default` value
4. otherwise, use the element text content when applicable

Tools SHALL ensure that the resolved value is unambiguous.

---

### 6.4 List Properties

List properties are stored as structured nested XML under the owning property element.

The exact nested structure is governed by the component schema and any associated list-item conventions.

List properties SHALL be treated as single-target unless and until the configuration format defines a normative structured encoding for target-specific list values.

---

### 6.5 Text Properties

Properties of type `text` MAY contain XML-sensitive characters.

Tools:

* MUST accept normal escaped text and CDATA when reading
* SHOULD write text property values using a single CDATA section

---

## 7. Normalization and Naming

When the component schema maps sections and properties into XML payload elements, naming and matching SHALL follow the normalization rules defined by the Core Specification unless a more specific schema rule overrides them.

---

## 8. Validation Model

Validation occurs at two levels.

### 8.1 XSD Validation

A configuration is structurally valid if it conforms to `chibiforge_xcfg.xsd`.

This includes:

* valid root structure
* valid target declarations
* valid component attributes

---

### 8.2 Schema-Aware Validation

A configuration is semantically valid only if each configured component payload is valid with respect to its component schema.

This includes:

* valid section/property structure
* valid value types
* valid target references
* valid target-specific override structure
* valid list structure

---

## 9. Unknown Content

Because component payload is defined with `<any>` at the XSD level, tools SHOULD preserve unknown nested content where possible.

However, tools MAY reject payload that is incompatible with the resolved component schema.

---

## 10. Writing Rules

When writing `chibiforge.xcfg`, tools SHOULD:

* emit formatted, human-readable XML
* preserve stable ordering where possible
* preserve semantically equivalent content where possible
* write component payload in schema order when such order is defined by the tool's schema model
* preserve or update `toolVersion` and `schemaVersion`
* omit an explicit `<target id="default"/>` unless compatibility mode requires it

Tools MAY normalize semantically equivalent representations during save.

---

## 11. Target Operations

### 11.1 Add Target

Adding a target creates a new logical target in the configuration.

If `<targets>` is present, a new `<target id="..."/>` element SHALL be added.

If `<targets>` is absent, tools SHALL create `<targets>` and then add the new explicit target.

---

### 11.2 Rename Target

Renaming a target updates:

* the declared `<target id="..."/>`
* all target-specific references that use that target ID

The `"default"` target SHALL NOT be renamed.

---

### 11.3 Delete Target

Deleting a target removes:

* the declared `<target id="..."/>`
* all target-specific references to that target

The `"default"` target SHALL NOT be deleted.

---

## 12. Non-Goals

This specification does NOT define:

* component schema semantics
* template execution behavior
* UI interaction details

---
