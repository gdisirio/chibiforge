# ChibiForge Agent Notes

## Project Overview

ChibiForge is a Java 17 Maven multi-module project for schema-driven configuration and code generation for embedded projects.

- `cli/`: generator engine, component/container loading, config parsing, template/static output generation.
- `ui/`: JavaFX desktop editor that reuses the CLI module as its engine/library.
- `specs/`: project specifications and the authoritative component schema XSD.
- `testdata/` and `cli/src/test/resources/fixtures/`: sample components/configurations used by tests.
- `bin/`: deliverable launchers and packaged JARs.

## Authoritative Contract

For `component/schema.xml`, the authority order is:

1. `specs/chibiforge_schema.xsd`
2. `specs/chibiforge_spec_v11.md`

If the spec and XSD disagree, fix the spec to match the XSD unless the user explicitly asks to change the schema contract.

For presets, the authority order is:

1. `cli/src/main/resources/schemas/chibiforge_preset.xsd`
2. `specs/chibiforge_v11_to_v12.md`
3. `specs/chibiforge-ui_spec_v2.md`

Current preset semantics are patch-based, not full replacement.

Important current implications from the XSD:

- Section-level `<image>` is valid.
- Layout-level `<image>` is valid.
- Component-level `<image>` is not valid.
- `<section>` requires `name`, `expanded`, `editable`, and `visible`.
- `<property>` requires `name`, `type`, `brief`, `required`, `default`, `editable`, and `visible`.
- Supported property types are `bool`, `string`, `text`, `int`, `enum`, `list`.

## Build and Test

Common commands:

- `mvn test -q`
- `mvn package`
- `mvn -pl ui javafx:run`

Build notes:

- Parent POM: `pom.xml`
- CLI shaded JAR output: `bin/chibiforge.jar`
- UI shaded JAR output: `bin/chibiforge-ui.jar`
- UI launcher: `bin/chibiforge-ui`
- CLI uses vendored dependencies in `cli/lib/`
- UI launcher is detached from the terminal using `setsid -f`
- JavaFX cache/log fallback paths are standard user locations, not under `bin/`:
  - cache: `$XDG_CACHE_HOME/chibiforge/openjfx` or `~/.cache/chibiforge/openjfx`
  - log: `$XDG_CACHE_HOME/chibiforge/ui.log` or `~/.cache/chibiforge/ui.log`

## Important Files

- Schema contract: `specs/chibiforge_schema.xsd`
- Current spec: `specs/chibiforge_spec_v11.md`
- UI spec: `specs/chibiforge-ui_spec_v2.md`
- v12 transition spec: `specs/chibiforge_v11_to_v12.md`
- Preset XSD: `cli/src/main/resources/schemas/chibiforge_preset.xsd`
- Parser: `cli/src/main/java/org/chibios/chibiforge/component/ComponentDefinitionParser.java`
- Component model: `cli/src/main/java/org/chibios/chibiforge/component/`
- Config loader: `cli/src/main/java/org/chibios/chibiforge/config/ConfigLoader.java`
- Generator entry point: `cli/src/main/java/org/chibios/chibiforge/generator/GeneratorEngine.java`
- Preset engine: `cli/src/main/java/org/chibios/chibiforge/preset/`
- UI form rendering: `ui/src/main/java/org/chibios/chibiforge/ui/center/ConfigurationForm.java`
- Property widgets / condition handling: `ui/src/main/java/org/chibios/chibiforge/ui/widgets/PropertyWidgetFactory.java`
- XCFG writing: `ui/src/main/java/org/chibios/chibiforge/ui/io/XcfgWriter.java`
- UI settings: `ui/src/main/java/org/chibios/chibiforge/ui/settings/`
- Themes: `ui/src/main/resources/css/light.css`, `ui/src/main/resources/css/dark.css`

## Current Implementation Status

Recent work already moved the codebase substantially toward the schema-authoritative and preset-enabled model:

- `SectionDef` now carries section `editable` and `visible`.
- `ComponentDefinition` no longer carries component-level images.
- The UI can render section images.
- Initial `@cond:` evaluation is applied on component and list-item load.
- Parser strictness and negative coverage were improved to follow the component XSD more closely.
- Component/container identity is enforced for filesystem and JAR-discovered components.
- Presets are implemented end to end:
  - XSD-validated preset loading
  - schema-path indexing
  - patch-based scalar apply
  - structured list replacement
  - preset export
  - bundled preset discovery
  - UI load/save preset flows
  - preset warning surfacing in status/help/log
- The UI now follows the v2 startup/open/save flow more closely:
  - welcome screen
  - recent files
  - `New`, `Open`, `Close`, `Save`, `Save As`
  - source resolution from config location / sidecar / environment
- UI settings are now explicit JSON, not Java `Preferences`.
- Light/dark theme selection exists and is persisted.
- Help menu now has a real `About` dialog.

Known remaining limitation:

- Multi-target list preset apply/export is intentionally unsupported.
  - Reason: `.xcfg` still has no structured target-specific encoding for list items analogous to scalar `<targetValue>`.
  - This limitation is documented in `specs/chibiforge_v11_to_v12.md`.

Known softer gap:

- The dark theme is functional but not exhaustively polished; some minor JavaFX controls may still need styling touch-ups.

Near-term direction captured from recent work:

- The framework/tooling side is in good shape.
- The next major phase is expected to be a separate component-library project containing real components, presets, resources, and sample configurations.
- A future separate tool to import CMSIS packs into draft ChibiForge components is considered feasible, but it is not current work.

## Guidance for Changes

- Prefer updating tests and fixtures alongside parser/model changes.
- When changing schema behavior, inspect both:
  - `specs/chibiforge_schema.xsd`
  - `specs/chibiforge_spec_v11.md`
- Do not assume the spec is right if the XSD says otherwise.
- For parser strictness work, add negative tests for invalid XML constructs rather than only happy-path tests.
- For UI conditional behavior, verify both:
  - initial render state
  - reactive updates after editing
- For `type="text"` handling, reason from schema/property definitions, not just XML content.
- For UI changes, prefer launching `./bin/chibiforge-ui` and asking the user to perform the manual interaction check.
- For settings, use the JSON-backed store under the standard OS config location, not Java `Preferences`.
- For recent files, keep both menu behavior and welcome-screen behavior in sync.
- For preset changes, keep the fixture corpus under `cli/src/test/resources/fixtures/presets/` aligned with the runtime contract.

## Quick Verification Targets

After touching parser/model/UI behavior, check at least:

- `mvn test -q`
- `mvn package -q`
- parser or preset fixture coverage as applicable:
  - `ComponentDefinitionParserTest`
  - preset tests under `cli/src/test/java/org/chibios/chibiforge/preset/`
- one manual UI flow involving the changed surface, for example:
  - section visibility/editability
  - section image rendering
  - text property save/load with XML-sensitive characters
  - preset apply/load/save
  - theme/settings persistence
