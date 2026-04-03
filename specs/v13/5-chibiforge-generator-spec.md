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

### 4.2 build/

```text
component/build/
```

→ `<projectRoot>/generated/<normalizedComponentId>/build/`

* always overwritten

---

### 4.3 Other Unspecified Directories

Any top-level directory under `component/` not explicitly defined by the container specification SHALL be treated as static payload.

Files under such a directory are copied to:

```text
<projectRoot>/generated/<normalizedComponentId>/<directoryName>/
```

* always overwritten

---

### 4.4 _root_wa/

```text id="mucy0o"
component/_root_wa/
```

→ project root

* always overwritten

---

### 4.5 _root_wo/

```text id="2slvzg"
component/_root_wo/
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

The generator SHALL provide templates with a structured data model derived from the resolved configuration.

The data model is constructed from the `<components>` section of `chibiforge.xcfg` **after full resolution**.

All values exposed in the data model are:

* fully resolved
* target-specific values already selected
* free of any schema-level expressions (`@xxx`)

The data model SHALL NOT expose any intermediate or unresolved representation.

---

### 7.1 Top-Level Variables

The following variables SHALL be available to templates:

* `doc`
* `components`
* `global`
* resource variables

No additional variables SHALL be exposed.

This set SHALL be:

* deterministic
* stable across executions
* independent of tool implementation details

---

### 7.2 `doc`

`doc` SHALL contain exactly one resolved `<component>` element corresponding to the component currently being processed.

Semantics:

* represents a single `<component>` element from `chibiforge.xcfg`
* corresponds to the component whose container is currently being executed
* contains only resolved values for the active target
* contains no target-specific structures (e.g. override maps)
* contains no schema-level expressions (`@xxx`)

`doc` SHALL preserve the structural shape of the configuration payload, with all values materialized.

---

### 7.3 `components`

`components` SHALL contain the full set of resolved `<component>` elements from `chibiforge.xcfg`, indexed by component ID.

Semantics:

* `components` is a mapping:

```text
componentId -> resolved component data
```

* each entry corresponds to one `<component>` element in the configuration file
* each entry contains only resolved values
* no target-specific structures SHALL be exposed
* no `@xxx` expressions SHALL be present

Access to other components through this map SHALL be read-only.

---

### 7.4 Relationship Between `doc` and `components`

For the component currently being processed, `doc` SHALL be identical to the corresponding entry in `components`.

```text
doc == components[currentComponentId]
```

`doc` exists as a convenience alias to simplify template authoring.

---

### 7.5 `global`

`global` SHALL provide generation path metadata.

The following child values SHALL be available:

* `absolute_configuration_path`
* `component_path`
* `component_paths`

Semantics:

* `absolute_configuration_path` is the absolute path of the project/configuration root directory, with a trailing `/`
* `component_path` is the current component generated-output root relative to project root
* `component_paths` is the ordered collection of all configured component generated-output roots relative to project root
* all values in `global` SHALL use `/` as separator and SHALL conventionally terminate directory paths with `/`

---

### 7.6 Resource Variables

Each resource declared in the component schema SHALL be exposed as a top-level variable.

Semantics:

* variable name SHALL match the resource ID
* value SHALL contain the parsed resource content
* resources SHALL be read-only
* resources SHALL be scoped to the current component
* no `@ref:` expressions SHALL be present

Resources are resolved prior to exposure to the template engine.

---

## 8. Explicit Exclusions

The generator SHALL NOT expose any of the following to templates:

* schema-level expressions (`@xxx`)
* unresolved configuration structures
* target metadata or override structures
* filesystem paths other than the values explicitly exposed through `global`
* execution environment details

This enforces strict separation between:

* UI / editing logic
* generation logic

---

## 9. Value Resolution

All values SHALL be fully resolved before generator execution.

Resolution is performed by the tooling layer prior to invoking the generator.

Resolution includes:

* selection of the active target
* application of schema default values
* resolution of inherited values
* evaluation of `@ref:` expressions
* evaluation of `@cond:` expressions (including visibility and editability effects)

After resolution:

* each property SHALL have a single effective value
* no conditional or reference expressions SHALL remain
* the configuration is fully materialized

The generator SHALL receive only this final state.

---

### 9.1 Prohibited Constructs

The generator SHALL NOT evaluate or interpret any schema-level expressions.

This includes, but is not limited to:

* `@ref:`
* `@cond:`
* any other `@xxx` construct

Such constructs SHALL NOT appear in the generator data model.

Any occurrence of unresolved expressions at this stage SHALL be treated as an error.

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

* treat output locations as project-root-relative
* respect output scope rules

The generator does not currently perform mandatory runtime enforcement that every computed output remains within the project root.

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
