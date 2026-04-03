# ChibiForge Component Schema Specification

*(chibiforge-component-schema-spec.md)*

---

## 1. Purpose and Scope

This document defines the **structure and semantics of component schemas** (`component/schema.xml`).

Authority order for this document is:

1. `chibiforge_schema.xsd`
2. this specification

If these differ, the XSD governs the XML structure and this document governs higher-level semantics.

This document establishes:

* the structure of `component/schema.xml`
* the semantic meaning of schema elements and attributes
* feature dependency declarations
* resource declarations
* configuration structure declarations
* UI-level semantics for `@xxx` constructs

This document does **not** define:

* container layout
* configuration file encoding
* generator pipeline behavior
* UI workflows (see `chibiforge-ui_spec_v2.md`)

---

## 2. Schema File and Namespace

A component schema SHALL be stored at:

```text id="4h2g2a"
component/schema.xml
```

The root element SHALL be `<component>` in the namespace defined by `chibiforge_schema.xsd`.

The XSD is authoritative for:

* element and attribute names
* required element ordering
* cardinality
* lexical restrictions encoded in the schema

---

## 3. XSD-Defined Top-Level Structure

The root element is:

```xml id="d7xk3u"
<component
    xmlns="http://chibiforge/schema/component"
    id="..."
    name="..."
    version="..."
    hidden="true|false"
    is_platform="true|false">
  <description>...</description>
  <resources>...</resources>
  <categories>...</categories>
  <requires>...</requires>
  <provides>...</provides>
  <sections>...</sections>
</component>
```

The XSD defines:

* required attributes: `id`, `name`, `version`, `hidden`, `is_platform`
* required child elements and their ordering

---

## 4. Component Identity Attributes

### 4.1 `id`

Canonical component identifier.

---

### 4.2 `name`

Human-readable name.

---

### 4.3 `version`

Component version string.

No semantic format is enforced by the XSD.

---

### 4.4 `hidden`

Boolean string.

Defines default visibility in tooling.

---

### 4.5 `is_platform`

Boolean string.

Identifies platform-class components.

---

## 5. Resources

### 5.1 Structure

```xml id="6v1p5u"
<resources>
  <resource id="..." file="..."/>
</resources>
```

---

### 5.2 Semantics

* resources are component-scoped
* `id` SHALL be unique
* `file` is container-relative

Resources are used:

* by UI tools for `@ref:` resolution
* by the generator as resolved data (no `@ref:` evaluation at generation time)

---

## 6. Categories

```xml id="m2c0yz"
<categories>
  <category id="A/B/C"/>
</categories>
```

* at least one category required
* `/` defines hierarchy (tool interpretation)

---

## 7. Feature Declarations

```xml id="u2v1tm"
<requires>
  <feature id="X"/>
</requires>

<provides>
  <feature id="Y" exclusive="true"/>
</provides>
```

Semantics:

* advisory dependency model
* no automatic resolution

Tools SHALL:

* warn on missing required features
* warn on exclusive conflicts

---

## 8. Sections

### 8.1 Structure

```xml id="kz2q9o"
<section name="..." expanded="..." editable="..." visible="...">
  <description>...</description>
  ...
</section>
```

---

### 8.2 Attributes

* `name`: identifier
* `expanded`: initial UI state
* `editable`, `visible`: string values interpreted by UI

---

### 8.3 Semantics

Sections define hierarchical configuration structure.

They define:

* configuration paths
* grouping
* preset matching scope

---

## 9. Properties

### 9.1 Structure

```xml id="i4x7qj"
<property
    name="..."
    type="..."
    brief="..."
    required="true|false"
    default="..."
    editable="..."
    visible="..."
/>
```

Optional nested `<sections>` allowed by XSD.

---

### 9.2 Types

Allowed:

* `int`
* `string`
* `text`
* `bool`
* `enum`
* `list`

---

### 9.3 Type Attributes

* `int_min`, `int_max`
* `string_regex`
* `text_maxsize`
* `enum_of`
* `list_columns`

---

### 9.4 Semantics

Property semantics are defined by `type`.

The XSD does not enforce cross-attribute consistency.

Tools SHALL enforce type semantics.

---

### 9.5 List Properties

For `type="list"`:

* nested `<sections>` defines item schema
* ordered collection

Constraint:

* list properties are single-target only

---

## 10. Visibility and Editability

### 10.1 Values

XSD allows any string.

ChibiForge semantics define:

* `"true"`
* `"false"`
* `@cond:<xpath>`

---

### 10.2 Effective State

```text id="v3i1nm"
effectiveVisible = parentVisible AND ownVisible
effectiveEditable = parentEditable AND ownEditable
```

---

## 11. `@xxx` Constructs

### 11.1 Scope

Includes:

* `@cond:`
* `@ref:`

---

### 11.2 Layer Responsibility

All `@xxx` constructs are **UI-layer semantics only**.

They SHALL:

* be evaluated by UI / editing tools
* NOT be evaluated by the generator

---

### 11.3 Persistence Model

Configuration files SHALL contain only resolved values.

No `@xxx` expressions SHALL appear in generator input data.

---

## 12. `@cond:` Semantics

```text id="i9r6q2"
@cond:<xpath>
```

* evaluated against live configuration model
* produces boolean

Controls:

* visibility
* editability

---

## 13. `@ref:` Semantics

```text id="0whbpf"
@ref:<resource_id>/<xpath>
```

* resolves values from component resources
* result is interpreted as a string and converted according to attribute semantics

---

## 14. Layouts

```xml id="4fwj9y"
<layout columns="1..4" align="left|center|right">
  ...
</layout>
```

* arranges properties visually
* no semantic effect on configuration

---

## 15. Images

```xml id="qv2m1c"
<image file="..." align="...">
  <text>...</text>
</image>
```

* UI-only
* no effect on configuration

---

## 16. Schema Paths

A schema path consists of:

* section hierarchy
* property name

Used for:

* configuration mapping
* preset matching

Normalization follows Core Specification.

---

## 17. Validation Model

### 17.1 XSD Validation

Structure MUST conform to XSD.

---

### 17.2 Semantic Validation

Tools SHALL validate:

* type correctness
* constraints
* `@cond:` evaluation
* `@ref:` resolution

---

## 18. Generator Boundary

The generator SHALL:

* receive only resolved values
* NOT evaluate `@xxx` constructs
* NOT depend on schema expressions

---

## 19. Non-Goals

This specification does NOT define:

* configuration encoding
* generator execution
* UI rendering details

---
