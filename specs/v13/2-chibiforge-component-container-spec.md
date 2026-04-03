# ChibiForge Component Container Specification

*(chibiforge-component-container-spec.md)*

---

## 1. Purpose and Scope

This document defines the **structure, identity, and validation rules** for ChibiForge component containers.

It establishes:

* how components are packaged and distributed
* the required filesystem and JAR layouts
* component identity and naming rules
* validation constraints for containers

This document does **not** define:

* schema semantics (see Component Schema Specification)
* configuration file structure (see Configuration Specification)
* generator behavior (see Generator Specification)
* UI behavior (see UI Specification)

---

## 2. Component Container Concept

A **component container** is a self-contained unit that provides:

* a component definition (`schema.xml`)
* templates
* static payload
* resources

A container SHALL encapsulate everything required for a component to function.

A container MAY be:

* a filesystem directory
* a plugin JAR

---

## 3. Container Types

### 3.1 Filesystem Container

A filesystem container is a directory whose name equals the component ID.

### 3.2 Plugin JAR Container

A plugin container is a JAR file containing:

* a `component/` subtree
* a `plugin.xml` marker
* `META-INF/MANIFEST.MF`

Plugin JARs are a **portable distribution format** and SHALL NOT require Eclipse at runtime.

---

## 4. Container Layout

All component content SHALL reside under:

```text
<containerRoot>/component/
```

### 4.1 Required Structure

```text
component/
  schema.xml

  cfg/
  cfg_root_wa/
  cfg_root_wo/

  source/
  source_root_wa/
  source_root_wo/

  resources/
```

### 4.2 Optional Structure

```text
rsc/
  icon.png

other/
```

---

## 5. Directory Semantics

### 5.1 schema.xml

* Defines the component (see Component Schema Specification)
* MUST exist

---

### 5.2 cfg/

* Contains templates generating files under `generated/`
* Files are always overwritten

---

### 5.3 cfg_root_wa/

* Templates generating files in project root
* Always overwritten

---

### 5.4 cfg_root_wo/

* Templates generating files in project root
* Written only if target file does not exist

---

### 5.5 source/

* Static files copied to:

```text
<projectRoot>/generated/<normalizedComponentId>/
```

* Always overwritten

---

### 5.6 source_root_wa/

* Static files copied to project root
* Always overwritten

---

### 5.7 source_root_wo/

* Static files copied to project root
* Written only if file does not exist

---

### 5.8 resources/

* Contains XML or JSON resource files
* Used by schema and templates
* Loaded into the data model

---

### 5.9 rsc/

* Optional UI assets
* Example: `icon.png`

---

## 6. Component Identity

Each component has a **component ID** defined in `schema.xml`.

This ID SHALL be the authoritative identity of the component.

---

## 7. Identity Consistency Rules

The following identifiers MUST be identical:

| Location                              | Requirement    |
| ------------------------------------- | -------------- |
| Directory name (filesystem container) | = component ID |
| schema.xml `<component id>`           | = component ID |
| JAR filename prefix                   | = component ID |
| MANIFEST `Bundle-SymbolicName`        | = component ID |

---

## 8. Naming Convention

Component IDs SHOULD follow reverse-domain naming:

```text
<domain>.<project>.components.<category>.<name>
```

Examples:

* `org.chibios.chibiforge.components.hal.stm32f4xx`
* `com.vendor.components.sensor.bme280`

Third-party components MUST use their own domain.

---

## 9. Plugin Container Requirements

A plugin JAR MUST contain:

### 9.1 plugin.xml

```xml
<plugin>
  <extension point="org.chibios.chibiforge.component"/>
</plugin>
```

Purpose:

* marks the JAR as a ChibiForge component container
* contains no configuration metadata

---

### 9.2 MANIFEST.MF

* MUST define `Bundle-SymbolicName`
* MUST match component ID

---

### 9.3 JAR Naming

```text
<componentId>_<version>.jar
```

---

## 10. Component Discovery

A container is valid if:

* `component/schema.xml` exists
* identity rules are satisfied

For plugin containers:

* `plugin.xml` marker MUST be present

---

## 11. Validation Rules

Tools MUST reject a container if:

* `schema.xml` is missing
* component ID mismatch occurs
* directory/JAR name does not match component ID
* MANIFEST or plugin metadata mismatch occurs

---

## 12. Resource Resolution Scope

Resources declared in a component:

* are scoped to that component only
* MUST NOT be globally visible to other components

---

## 13. Container Independence

Component containers SHALL be:

* self-contained
* independent of external state
* reusable across tools

They MUST NOT:

* depend on IDE-specific runtime
* require external configuration to function

---

## 14. Filesystem Constraints

All paths inside the container:

* are relative to `component/`
* MUST NOT reference external filesystem locations

---

## 15. Non-Goals

This specification does NOT define:

* schema semantics
* template behavior
* configuration structure
* UI rendering

---

