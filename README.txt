ChibiForge
==========

ChibiForge is a Java 17 toolset for schema-driven configuration and code generation
for embedded projects.

Project layout
--------------

- cli/
  Generator engine, component discovery, schema/config parsing, preset handling,
  and code/static output generation.
- ui/
  JavaFX desktop editor built on top of the CLI module.
- specs/
  Project specifications and authoritative component schema documents.
- testdata/
  Sample configurations, components, and presets.
- bin/
  Deliverable launchers and packaged JARs.

Current version
---------------

0.1.1-beta

Current status
--------------

The framework/tooling side is in good shape and is now feature-complete for the
current preset scope.

Implemented:

- schema-authoritative component loading
- filesystem and JAR component discovery with identity validation
- UI and CLI component source discovery aligned to the same model
- JavaFX editor with:
  - welcome screen
  - new/open/close/save/save as
  - recent files
  - component palette
  - inspector/help/log/files views
  - light/dark theme setting
  - JSON-backed UI settings
- preset support end to end:
  - XSD-validated preset loading
  - patch-based preset apply
  - structured list replacement
  - preset export
  - bundled preset discovery
  - UI load/save preset actions
  - warning surfacing in status/help/log

Build and run
-------------

Common commands:

- mvn test -q
- mvn package

Deliverables:

- bin/chibiforge
- bin/chibiforge-ui
- bin/chibiforge.jar
- bin/chibiforge-ui.jar

Notes:

- The UI launcher is detached from the terminal on Linux/macOS.
- UI cache/log files use standard user cache locations, not bin/.
- Windows batch launchers are being added separately.

Authority and specs
-------------------

Component schema authority order:

1. specs/chibiforge_schema.xsd
2. specs/chibiforge_spec_v11.md

Preset authority order:

1. cli/src/main/resources/schemas/chibiforge_preset.xsd
2. specs/chibiforge_v11_to_v12.md
3. specs/chibiforge-ui_spec_v2.md

If the component spec and XSD disagree, the XSD is authoritative.

Open points
-----------

Known limitation:

- Multi-target list preset apply/export is intentionally unsupported.
  The .xcfg format still has no structured target-specific encoding for list items.

Known softer gaps:

- Dark theme polish is good enough but not exhaustive; some minor JavaFX controls
  may still need styling refinement.
- Windows launchers should be validated on a real Windows machine.
- Native packaging via jpackage is a likely next packaging step.

Next likely phase
-----------------

The next major effort is expected to be a separate component-library project with
real components, presets, resources, and sample configurations that exercise the
tool on production-like content.

