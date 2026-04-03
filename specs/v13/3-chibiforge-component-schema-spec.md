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
* tool-defined semantics for `@ref:` and `@cond:`

This document does **not** define:

* container layout
* configuration file encoding
* generator pipeline behavior
* UI workflows

---

## 2. Schema File and Namespace

A component schema SHALL be stored at:

```text id="4e6n8v"
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

```xml id="9lw16l"
<component
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

The following attributes are required by the XSD:

* `id`
* `name`
* `version`
* `hidden`
* `is_platform`

The following child elements are required by the XSD, in this order:

1. `<description>`
2. `<resources>`
3. `<categories>`
4. `<requires>`
5. `<provides>`
6. `<sections>`

---

## 4. Component Identity Attributes

### 4.1 `id`

`id` is the canonical component identifier.

It SHALL uniquely identify the component.

Its packaging-level consistency requirements are defined in the Component Container Specification.

---

### 4.2 `name`

`name` is a human-readable display name for the component.

---

### 4.3 `version`

`version` is the component definition version string.

The XSD requires only that it be present as a string.

Any stronger version-format rules, if imposed by tooling, are outside the XSD and SHALL be documented separately.

---

### 4.4 `hidden`

`hidden` is an XSD-constrained boolean string with the value `"true"` or `"false"`.

Semantics:

* `"true"` means the component is hidden by default in user-facing component browsing views
* `"false"` means the component is shown normally

---

### 4.5 `is_platform`

`is_platform` is an XSD-constrained boolean string with the value `"true"` or `"false"`.

Its semantic meaning is tool-defined.

At minimum, it identifies the component as belonging to the platform class for tooling and presentation purposes.

---

## 5. Description

The `<description>` element contains human-readable component documentation.

The XSD requires exactly one `<description>` element.

Its content is plain text.

---

## 6. Resources

### 6.1 XSD Structure

The `<resources>` element contains zero or more:

```xml id="xg0k2i"
<resource id="..." file="..."/>
```

Both `id` and `file` are required by the XSD.

---

### 6.2 Semantics

Each `<resource>` declaration defines a resource owned by the component.

Rules:

* `id` SHALL be unique within the component
* `file` SHALL be interpreted relative to the component container root
* the file format is determined by the referenced file and tool support
* XML and JSON are the standard supported resource formats

Resources are component-scoped.

They are used for:

* `@ref:` resolution
* template data model population

---

## 7. Categories

### 7.1 XSD Structure

The `<categories>` element contains one or more:

```xml id="jo4dko"
<category id="..."/>
```

The XSD requires at least one category.

---

### 7.2 Semantics

A category assigns the component to one or more classification paths.

The `id` value is a category path string.

The `/` character is interpreted as a hierarchy separator by tools that present categories hierarchically.

---

## 8. Feature Declarations

### 8.1 XSD Structure

`<requires>` and `<provides>` each contain zero or more:

```xml id="hxbk1j"
<feature id="..." exclusive="true|false"/>
```

The `id` attribute is required.

The `exclusive` attribute is optional.

---

### 8.2 Semantics

Feature declarations implement a soft dependency model.

* `<provides>` declares capabilities offered by the component
* `<requires>` declares capabilities needed from other configured components

If `exclusive="true"` is present, at most one configured component should provide that feature.

---

### 8.3 Resolution Rules

Tools SHALL apply the following rules:

* if a required feature is not provided by any configured component, emit a warning
* if more than one configured component provides the same exclusive feature, emit a warning

This mechanism is advisory.

No automatic component insertion, removal, or conflict resolution SHALL occur.

---

## 9. Sections

### 9.1 XSD Structure

The `<sections>` element contains one or more `<section>` elements.

A `<section>` has the form:

```xml id="gr2g1j"
<section name="..." expanded="..." editable="..." visible="...">
  <description>...</description>
  ...
</section>
```

The XSD requires:

* `name`
* `expanded`
* `editable`
* `visible`

and exactly one `<description>` child.

After `<description>`, a section may contain any number of:

* `<layout>`
* `<image>`
* `<property>`

in any order permitted by the XSD choice model.

---

### 9.2 Semantics

A section defines a named grouping of configuration content.

The section hierarchy defined in the schema also defines the logical path space used for:

* configuration payload interpretation
* property addressing
* preset matching

---

### 9.3 `expanded`

`expanded` is XSD-constrained to `"true"` or `"false"`.

It defines the initial expanded/collapsed presentation state for tools that support collapsible sections.

---

### 9.4 `editable` and `visible`

The XSD requires these attributes to be present as non-empty strings, but does not constrain their lexical values beyond non-emptiness.

ChibiForge-defined semantic values are:

* `"true"`
* `"false"`
* `@cond:<xpath>`

Other lexical values may satisfy the XSD but have no defined ChibiForge semantics unless a tool explicitly defines them.

---

## 10. Properties

### 10.1 XSD Structure

A property has the form:

```xml id="no1y0s"
<property
    name="..."
    type="..."
    brief="..."
    required="true|false"
    default="..."
    editable="..."
    visible="..."
    ...>
  <sections>...</sections>
</property>
```

The XSD requires these attributes:

* `type`
* `brief`
* `name`
* `required`
* `default`
* `editable`
* `visible`

A property may optionally contain one nested `<sections>` element.

---

### 10.2 Property Types

The XSD permits exactly these `type` values:

* `int`
* `string`
* `text`
* `bool`
* `enum`
* `list`

No additional property types are valid.

---

### 10.3 Optional Type-Related Attributes

The XSD permits the following optional attributes:

* `string_regex`
* `enum_of`
* `list_columns`
* `int_min`
* `int_max`
* `text_maxsize`

The XSD does not enforce cross-consistency between these attributes and `type`.

Their semantic use is defined by this specification.

---

### 10.4 Property Semantics by Type

#### `bool`

Represents a boolean value.

#### `string`

Represents a single-line textual value.

If `string_regex` is present, it defines a validation rule.

#### `text`

Represents a free-form textual value, potentially multi-line.

If `text_maxsize` is present, it defines a maximum length rule.

#### `int`

Represents an integer value.

If `int_min` or `int_max` are present, they define numeric bounds.

#### `enum`

Represents a value selected from a finite set.

If `enum_of` is present, it defines the allowed values.

#### `list`

Represents an ordered list of items.

For `type="list"`, the nested `<sections>` element defines the schema of each list item.

For other property types, nested `<sections>` content is allowed by the XSD but has no defined ChibiForge semantics.

---

### 10.5 `required`

`required` is XSD-constrained to `"true"` or `"false"`.

It indicates whether the property is semantically required by the schema.

The exact enforcement strategy is tool-defined, but tools SHALL treat `"true"` as normative.

---

### 10.6 `default`

`default` is required by the XSD for every property.

Its value is the schema-defined default value for the property.

The XSD treats it as a string. Type interpretation is tool-defined according to the property type.

The value MAY use `@ref:`.

---

### 10.7 `editable` and `visible`

For properties, the XSD requires these attributes as strings.

ChibiForge-defined values are:

* `"true"`
* `"false"`
* `@cond:<xpath>`

Their semantics match the section-level rules.

---

## 11. Effective Visibility and Editability

Visibility and editability are evaluated hierarchically.

Effective state SHALL be computed as:

```text id="ybvln5"
effectiveVisible = parentEffectiveVisible AND ownVisible
effectiveEditable = parentEffectiveEditable AND ownEditable
```

Consequences:

* if an ancestor is effectively not visible, all descendants are effectively not visible
* if an ancestor is effectively not editable, all descendants are effectively not editable

Tools MAY skip evaluating descendant `@cond:` expressions when an ancestor already determines the effective state.

This is an optimization and SHALL NOT change observable semantics.

---

## 12. `@cond:` Expressions

### 12.1 Syntax

A ChibiForge conditional expression uses the form:

```text id="21cf52"
@cond:<xpath>
```

---

### 12.2 Scope

`@cond:` is defined for:

* section `editable`
* section `visible`
* property `editable`
* property `visible`

---

### 12.3 Semantics

The XPath expression is evaluated against the live configuration data model.

The result SHALL be interpreted as a boolean condition controlling:

* visibility, or
* editability

Evaluation failures SHALL be treated as errors or validation failures by tools.

---

## 13. `@ref:` References

### 13.1 Syntax

A ChibiForge resource reference uses the form:

```text id="ja4v8s"
@ref:<resource_id>/<xpath>
```

---

### 13.2 Scope

`@ref:` MAY appear in any property attribute value.

This includes, but is not limited to:

* `default`
* `enum_of`
* `int_min`
* `int_max`

---

### 13.3 Semantics

The reference resolves against a component-owned resource identified by `resource_id`.

The XPath portion is evaluated against that resource's parsed structure.

Resolution failures SHALL be treated as errors or validation failures by tools.

---

## 14. Layouts

### 14.1 XSD Structure

A layout has the form:

```xml id="7k5905"
<layout columns="1|2|3|4" align="left|center|right">
  ...
</layout>
```

It may contain zero or more of:

* `<image>`
* `<property>`
* `<empty>`

Nested `<layout>` is not allowed by the XSD.

Nested `<section>` is not allowed by the XSD.

---

### 14.2 Semantics

A layout arranges its children in a grid-like presentation structure.

* `columns` defines the number of columns
* `align` defines content alignment within the layout

`<empty>` represents an intentionally empty visual slot.

Its text content, if any, is insignificant.

---

## 15. Images

### 15.1 XSD Structure

An image has the form:

```xml id="m78gj1"
<image file="..." align="left|center|right">
  <text>...</text>
</image>
```

The XSD requires:

* `file`
* `align`
* exactly one `<text>` child

---

### 15.2 Semantics

* `file` is interpreted relative to the component container root
* `<text>` provides caption or descriptive text
* image presentation is tool-defined

---

## 16. List Properties

### 16.1 Structural Model

For `type="list"`, the optional nested `<sections>` element defines the schema of one list item.

The list value is therefore an ordered collection of items, each of which conforms to that nested section schema.

---

### 16.2 Semantics

List properties:

* are ordered
* support nested structure
* may themselves contain nested list properties recursively

---

### 16.3 Target Model Constraint

List properties SHALL be treated as single-target.

Multi-target list values are not part of the current ChibiForge-defined configuration model.

---

## 17. Text Property Serialization Semantics

For properties of `type="text"`:

* tools MUST accept both escaped character data and CDATA on read
* tools SHOULD write values using a single CDATA section

This is a semantic rule. The XSD does not encode it.

---

## 18. Schema Paths

A property is identified semantically by its schema path.

A schema path consists of:

* the hierarchy of enclosing section names
* followed by the property name

Matching operations that use schema paths SHALL use normalized identifiers as defined by the Core Specification.

---

## 19. Validation Model

Validation occurs at two levels.

### 19.1 XSD Validation

The schema document is structurally valid if it conforms to `chibiforge_schema.xsd`.

### 19.2 Semantic Validation

Tools SHALL additionally validate:

* property values against their declared property type
* `int_min` / `int_max` semantics for `int`
* `string_regex` semantics for `string`
* `text_maxsize` semantics for `text`
* `enum_of` semantics for `enum`
* valid `@ref:` resolution
* valid `@cond:` evaluation

Invalid semantic content SHALL be rejected or reported as an error by tools.

---

## 20. Non-Goals

This specification does NOT define:

* configuration file encoding
* generator pipeline behavior
* exact UI rendering
* framework-specific widget mappings

---

