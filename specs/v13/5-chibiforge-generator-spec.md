# ChibiForge Generator and Data Model Specification

*(chibiforge-generator-spec.md)*

---

## 1. Purpose and Scope

This document defines the **generator behavior and template data model** of ChibiForge.

It establishes:

* how outputs are produced
* how component content is processed
* the structure of the template data model
* execution rules and guarantees

This document does **not** define:

* component schema structure (see Component Schema Specification)
* configuration file structure (see Configuration Specification)
* container layout (see Component Container Specification)
* UI behavior (see UI Specification)

---

## 2. Generator Responsibilities

The generator SHALL:

* load configuration
* resolve components
* resolve target values
* build the data model
* process static payload
* process templates
* write outputs

---

## 3. Output Model

Outputs are produced by two mechanisms:

* static payload
* templates

---

## 4. Static Payload

### 4.1 source/

Files under:

```text id="2agji5"
component/source/
```

are copied to:

```text id="wzjt25"
<projectRoot>/generated/<normalizedComponentId>/
```

* always overwritten

---

### 4.2 source_root_wa/

```text id="mucy0o"
component/source_root_wa/
```

→ project root

* always overwritten

---

### 4.3 source_root_wo/

```text id="2slvzg"
component/source_root_wo/
```

→ project root

* written only if file does not exist

---

## 5. Template Processing

Templates are processed using FMPP.

---

### 5.1 cfg/

```text id="v0j7c2"
component/cfg/
```

→ `<projectRoot>/generated/`

* always overwritten

---

### 5.2 cfg_root_wa/

```text id="uqahri"
component/cfg_root_wa/
```

→ project root

* always overwritten

---

### 5.3 cfg_root_wo/

```text id="3trcju"
component/cfg_root_wo/
```

→ project root

* write-once

---

## 6. Template Modes

Mode detection:

* `.ftlc` → code-first
* `.ftl` → classic unless explicitly switched to code-first syntax

Each file is processed independently.

---

## 7. Data Model

The generator SHALL provide templates with a structured data model.

---

### 7.1 Top-Level Variables

Available variables:

* `doc`
* `components`
* resource variables

No additional variables SHALL be exposed.

---

### 7.2 `doc`

`doc` SHALL contain exactly one resolved `<component>` element corresponding to the component currently being processed.

Its content model is the resolved form of that component's payload as stored in `chibiforge.xcfg`.

Semantics:

* `doc` represents exactly one component
* `doc` is the current component
* `doc` contains only effective values for the active target
* `doc` SHALL NOT expose raw target-specific structures

Conceptually, if `chibiforge.xcfg` contains:

```xml id="6v46rg"
<components>
  <component id="vendor.example.a" version="1.0.0">...</component>
  <component id="vendor.example.b" version="1.0.0">...</component>
</components>
```

then, while processing `vendor.example.a`, `doc` is the resolved form of that single `<component id="vendor.example.a" ...>` element.

---

### 7.3 `components`

`components` SHALL contain the full set of resolved `<component>` elements from `chibiforge.xcfg`, indexed by component ID.

Semantics:

* `components` is a collection of component elements
* each entry corresponds to one `<component>` element from the configuration
* entries are indexed by component ID
* each entry contains only effective values for the active target
* raw target-specific structures SHALL NOT be exposed

Conceptually:

```text id="te95vc"
components[componentId] -> resolved <component id="componentId" ...>
```

For template access, tools MAY expose this map using the component ID directly or a normalized identifier form, provided the mapping is deterministic and documented consistently.

---

### 7.4 Relationship Between `doc` and `components`

For the component currently being processed, `doc` SHALL be equivalent to the corresponding entry in `components`.

Conceptually:

```text id="d6trda"
doc == components[currentComponentId]
```

`doc` exists as a convenience alias for the current component.

---

### 7.5 Resource Variables

Each declared resource is exposed as:

* a top-level variable
* named by resource ID

Resources:

* are scoped to the current component
* are read-only

---

## 8. Removed Context

The generator SHALL NOT expose:

* project root paths
* filesystem paths
* target metadata
* execution environment

---

## 9. Value Resolution

All values SHALL be resolved before template execution.

Templates SHALL receive:

* only effective values
* no target-specific structures

---

## 10. Generator Pipeline

The generator SHALL execute:

1. load configuration
2. resolve active target
3. resolve component definitions
4. validate feature dependencies
5. build resolved component data
6. for each component:

   * load resources
   * build data model
   * process static payload
   * process templates

---

## 11. Feature Dependencies

The generator SHALL:

* collect provided features
* verify required features
* verify exclusive constraints

Violations SHALL produce warnings.

---

## 12. Write Policies

| Policy | Behavior       |
| ------ | -------------- |
| always | overwrite      |
| once   | skip if exists |

---

## 13. Filesystem Constraints

The generator SHALL:

* write only within project root
* respect output scope rules

---

## 14. Determinism

The generator SHALL be deterministic.

Output depends only on:

* configuration
* components
* selected target

---

## 15. Logging

The generator SHOULD:

* log file writes
* log skipped files
* log warnings
* support verbose mode

---

## 16. Error Handling

Errors SHALL include:

* missing components
* invalid configuration
* template failures
* I/O failures

Errors SHALL abort generation.

---

## 17. Non-Goals

This specification does NOT define:

* UI behavior
* CLI interface
* component schema semantics

---

