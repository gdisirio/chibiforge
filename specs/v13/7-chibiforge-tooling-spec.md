# ChibiForge Tooling and Component Source Resolution Specification

*(chibiforge-tooling-spec.md)*

---

## 1. Purpose and Scope

This document defines:

* how ChibiForge tools discover component containers
* how multiple component sources are combined
* how conflicts are resolved
* the responsibilities of tooling (CLI, GUI, integrations)

This document governs **component discovery and execution orchestration**.

This document does **not** define:

* component schema semantics
* configuration file structure
* generator behavior
* UI interaction details

---

## 2. Tooling Overview

ChibiForge tools are responsible for:

* locating component containers
* loading component definitions
* invoking the generator
* applying presets
* managing configuration files

Tools MAY include:

* command-line interfaces (CLI)
* standalone GUI applications
* IDE integrations

---

## 3. Component Sources

A **component source** is a location from which component containers are discovered.

Supported source types:

* filesystem directories
* plugin JAR directories

---

## 4. Source Categories

Component sources are grouped into categories:

| Category | Description                        |
| -------- | ---------------------------------- |
| preferred| explicitly supplied by the tool or caller |
| project  | project-local components           |
| user     | user-defined external sources      |
| system   | system-wide or environment sources |

---

## 5. Default Source Locations

### 5.1 Project Source

```text id="c0k7y8"
<projectRoot>/components/
```

* highest priority
* intended for project-specific components

---

### 5.2 User Sources

Defined via configuration file:

```text id="v9bnhg"
<projectRoot>/chibiforge_sources.json
```

This file MAY define additional component source paths.

---

### 5.3 System Sources

System-level sources MAY be defined via:

* environment variables
* tool configuration

---

### 5.4 Preferred Sources

Tools MAY accept explicitly supplied preferred roots, for example:

* CLI `--components`
* CLI `--plugins`
* equivalent GUI or integration settings

Preferred roots take precedence over auto-discovered roots.

---

## 6. Source Definition File

### 6.1 File Location

```text id="dl8r4l"
<projectRoot>/chibiforge_sources.json
```

---

### 6.2 Structure

The file SHALL define a list of source paths.

Example:

```json id="kcc6rp"
{
  "sources": [
    "../shared/components",
    "/opt/chibiforge/components"
  ]
}
```

---

### 6.3 Rules

* paths MAY be relative or absolute
* relative paths are resolved against project root
* non-existing paths SHALL be ignored with warning

---

## 7. Source Resolution Order

Sources SHALL be processed in the following order:

1. preferred sources
2. project sources
3. user sources (in declared order)
4. system sources

Later sources SHALL NOT override earlier ones.

---

## 8. Component Discovery

### 8.1 Filesystem Containers

A directory SHALL be considered a component container if:

```text id="m1x3nk"
<dir>/component/schema.xml
```

exists.

---

### 8.2 Plugin JAR Containers

A JAR SHALL be considered a component container if it contains:

```text id="8x2rmv"
component/schema.xml
plugin.xml
```

---

## 9. Component Registry

Tools SHALL build a registry of components.

The registry SHALL:

* index components by component ID
* associate each ID with exactly one container

---

## 10. Conflict Resolution

If multiple containers provide the same component ID:

* the first occurrence in resolution order SHALL be used
* subsequent occurrences SHALL be ignored
* a warning SHALL be logged

---

## 11. Component Loading

For each selected component:

* the container SHALL be loaded
* schema SHALL be parsed
* resources SHALL be prepared
* templates SHALL be made available

Failure to load a component SHALL be treated as an error.

---

## 12. Generator Invocation

Tools SHALL:

* load configuration
* resolve components via registry
* invoke generator with selected target

---

## 13. Preset Integration

Tools SHALL:

* discover presets from component containers
* allow loading external preset files
* apply presets according to Preset Specification

---

## 14. Environment Variables

Tools MAY support environment variables for defining system sources.

Primary example:

```text id="i7pxy7"
CHIBIFORGE_COMPONENTS=/opt/chibiforge/components:/srv/chibiforge/components
```

Legacy tool-specific defaults MAY also be supported, for example:

```text
CHIBIFORGE_COMPONENTS_ROOT=/opt/chibiforge/components
CHIBIFORGE_PLUGINS_ROOT=/opt/chibiforge/plugins
```

Multiple paths in `CHIBIFORGE_COMPONENTS` MAY be separated by platform-specific separators.

---

## 15. Error Handling

Tools SHALL report:

* missing component sources
* invalid containers
* duplicate component IDs
* failed component loading

Critical errors SHALL abort execution.

---

## 16. Logging

Tools SHOULD log:

* resolved source list
* discovered components
* ignored duplicates
* applied precedence

---

## 17. Determinism

Component discovery SHALL be deterministic.

Given identical:

* source lists
* filesystem state

the resolved component registry SHALL be identical.

---

## 18. Non-Goals

This specification does NOT define:

* UI layout or workflows
* generator internals
* schema semantics

---
