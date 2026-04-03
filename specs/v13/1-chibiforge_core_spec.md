# ChibiForge Core Specification

*(chibiforge-core-spec.md)*

---

## 1. Purpose and Scope

This document defines the **core architectural model, terminology, and global invariants** of ChibiForge.

It establishes:

* the fundamental concepts used across all ChibiForge specifications
* the meaning of key terms
* system-wide invariants and constraints

This document does **not** define:

* XML schema structure (see Component Schema Specification)
* configuration file encoding (see Configuration Specification)
* generator behavior (see Generator Specification)
* UI behavior (see UI Specification)

---

## 2. Design Principles

### 2.1 Deterministic Generation

ChibiForge generation SHALL be deterministic.

Given identical:

* configuration data
* component containers
* selected target

the system SHALL produce identical outputs.

No behavior may depend on:

* current working directory
* system time
* host-specific paths
* implicit environment state

---

### 2.2 Schema-Driven Configuration

All configuration structure SHALL be defined by component schemas.

* Tools MUST NOT require hardcoded knowledge of specific components
* Validation and editing MUST derive from schema definitions

---

### 2.3 Separation of Concerns

ChibiForge is structured into independent layers:

| Layer              | Responsibility                                                              |
| ------------------ | --------------------------------------------------------------------------- |
| Component Schema   | Defines configuration structure and schema-level expressions                |
| UI / Editing Tools | Evaluate schema-level expressions and produce resolved configuration values |
| Configuration File | Stores configuration values                                                 |
| Generator          | Produces outputs from resolved values                                       |

Each layer SHALL operate strictly within its defined responsibility.

---

### 2.4 No Hidden Behavior

All system behavior SHALL be explicit.

The system MUST NOT:

* automatically add or remove components
* implicitly resolve dependencies
* introduce hidden inheritance
* apply undeclared defaults

Warnings MAY be emitted, but execution SHALL remain under user control.

---

### 2.5 Project-Scoped Operation

ChibiForge operates on a **single configuration per project**.

* The **project root and configuration root are the same directory**
* There is exactly **one configuration file per project**

Tools and generators MUST NOT:

* support multiple configurations within the same project
* treat subdirectories as independent configuration roots
* operate outside the project root

---

### 2.6 Pre-Generation Resolution

All schema-level `@xxx` constructs are UI-only and SHALL be resolved before generation.

This includes, but is not limited to:

* `@ref:` expressions
* `@cond:` expressions

The generator SHALL operate exclusively on a fully materialized configuration state.

---

## 3. Core Concepts

### 3.1 Project Root

The **project root** is the top-level directory of a project.

It is defined as:

* the directory containing `chibiforge.xcfg`

All filesystem operations are relative to this directory.

---

### 3.2 Generated Root

The **generated root** is a fixed directory:

```text id="729qed"
<projectRoot>/generated/
```

This directory SHALL:

* contain generated outputs
* be managed exclusively by the generator

---

### 3.3 Component

A **component** is a unit of:

* configuration
* generation
* feature declaration

Each component is defined by:

* a schema
* templates and/or static payload
* optional resources

Each component has a unique **component ID**.

---

### 3.4 Component Container

A **component container** is a distribution unit for a component.

It may be:

* a filesystem directory
* a plugin JAR

Its structure is defined in the Component Container Specification.

---

### 3.5 Configuration File

The configuration file defines:

* selected components
* configuration values
* targets

It SHALL be located at:

```text id="4802cs"
<projectRoot>/chibiforge.xcfg
```

There is exactly one configuration file per project.

---

### 3.6 Target

A **target** is a named configuration variant within a project.

Examples:

* `default`
* `debug`
* `release`

Rules:

* A `"default"` target SHALL always exist
* Additional targets MAY be defined
* Property values MAY vary per target

---

### 3.7 Write Policy

ChibiForge defines two write policies:

| Policy | Behavior                                  |
| ------ | ----------------------------------------- |
| always | file is overwritten on each generation    |
| once   | file is written only if it does not exist |

---

### 3.8 Scope

Outputs are categorized by scope:

| Scope      | Location                    |
| ---------- | --------------------------- |
| generated  | inside `generated/`         |
| root-scope | directly under project root |

---

### 3.9 Resource

A **resource** is structured data (XML or JSON) declared by a component.

Resources are used for:

* schema-level references during editing
* generator input after resolution, when applicable

Resources are scoped to the owning component.

---

## 4. Identifier Normalization

Identifiers SHALL be normalized when used in:

* filesystem paths
* data model paths

Normalization rules:

1. convert to lowercase
2. replace any non `[a-z0-9_]` character with `_`
3. collapse consecutive `_`

Examples:

```text id="601gnm"
HAL-Core_v2 → hal_core_v2
org.chibios.foo → org_chibios_foo
my..id → my_id
```

This rule MUST be applied consistently across all tools.

---

## 5. Data Model Concept

ChibiForge operates on a structured data model representing:

* resolved component configurations
* resource-derived values when needed by generation

Key properties:

* all values are resolved before generation
* templates operate only on resolved values
* no schema-level `@xxx` expressions are present in the generator input model

The exact structure is defined in the Generator Specification.

---

## 6. Component Identity

Each component is uniquely identified by a **component ID**.

Rules:

* IDs SHALL be unique within a configuration
* IDs SHOULD follow reverse-domain naming:

```text id="3261t1"
<domain>.<project>.components.<category>.<name>
```

Third-party components MUST use their own domain.

Consistency rules across container, schema, and packaging are defined elsewhere.

---

## 7. System Boundaries

### 7.1 Filesystem Boundary

All outputs SHALL remain within the project root.

Generation outside this boundary is invalid.

---

### 7.2 Execution Boundary

Templates SHALL operate only on:

* resolved configuration data
* generation-visible resources

Templates SHALL NOT depend on:

* schema-level `@xxx` expressions
* execution context
* environment-specific state

---

## 8. Error and Warning Model

ChibiForge distinguishes between:

| Type    | Effect             |
| ------- | ------------------ |
| Error   | abort operation    |
| Warning | continue execution |

Examples:

* missing component → error
* unresolved feature → warning
* validation failure during editing → error or UI rejection

Exact behavior is defined in other specifications.

---

## 9. Specification Relationships

This document defines shared concepts and invariants.

Other documents define specific domains:

* Component Container Specification
* Component Schema Specification
* Configuration Specification
* Generator Specification
* Preset Specification
* Tooling Specification

Precedence rules:

1. XML structure → governed by XSD
2. semantics → governed by the relevant specification
3. this document defines global invariants

The user interface behavior of ChibiForge tools is defined in a separate document: `chibiforge-ui_spec_v2.md`.

---

## 10. Non-Goals

ChibiForge does NOT define:

* build systems
* IDE behavior
* runtime behavior of generated code
* automatic dependency resolution

---

