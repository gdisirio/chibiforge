# ChibiForge Agent Notes

## Project Overview

ChibiForge is a Java 17 Maven multi-module project for schema-driven configuration and code generation for embedded projects.

- `cli/`: generator engine, component/container loading, config parsing, template/static output generation.
- `ui/`: JavaFX desktop editor that reuses the CLI module as its engine/library.
- `specs/`: project specifications and the authoritative component schema XSD.
- `testdata/` and `cli/src/test/resources/fixtures/`: sample components/configurations used by tests.
- `bin/`: build output target for the shaded CLI JAR.

## Authoritative Contract

For `component/schema.xml`, the authority order is:

1. `specs/chibiforge_schema.xsd`
2. `specs/chibiforge_spec_v11.md`

If the spec and XSD disagree, fix the spec to match the XSD unless the user explicitly asks to change the schema contract.

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
- CLI uses vendored dependencies in `cli/lib/`

## Important Files

- Schema contract: `specs/chibiforge_schema.xsd`
- Current spec: `specs/chibiforge_spec_v11.md`
- UI spec: `specs/chibiforge-ui_spec_v1.md`
- Parser: `cli/src/main/java/org/chibios/chibiforge/component/ComponentDefinitionParser.java`
- Component model: `cli/src/main/java/org/chibios/chibiforge/component/`
- Config loader: `cli/src/main/java/org/chibios/chibiforge/config/ConfigLoader.java`
- Generator entry point: `cli/src/main/java/org/chibios/chibiforge/generator/GeneratorEngine.java`
- UI form rendering: `ui/src/main/java/org/chibios/chibiforge/ui/center/ConfigurationForm.java`
- Property widgets / condition handling: `ui/src/main/java/org/chibios/chibiforge/ui/widgets/PropertyWidgetFactory.java`
- XCFG writing: `ui/src/main/java/org/chibios/chibiforge/ui/io/XcfgWriter.java`

## Current Implementation Status

Recent work already moved part of the codebase toward the schema-authoritative model:

- `SectionDef` now carries section `editable` and `visible`.
- `ComponentDefinition` no longer carries component-level images.
- The UI can render section images.
- `XcfgWriter` has CDATA-related logic for text-like values.

Known gaps that still matter:

- The parser is still permissive where the XSD is strict:
  - section `editable` / `visible` are defaulted instead of required
  - property `visible` is defaulted instead of required
  - invalid top-level component children are silently ignored instead of rejected
- UI condition evaluation is wired, but initial `@cond:` state may not be applied until a DOM update occurs.
- `XcfgWriter` currently decides CDATA by content, not by property type, so it is not yet a precise implementation of the `type="text"` serialization rule.
- Parser fixtures/tests still reflect some older permissive behavior.
- CLI/UI wording still says `chibiforge.xcfg` in places even though the engine accepts any config filename.

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

## Quick Verification Targets

After touching parser/model/UI behavior, check at least:

- `mvn test -q`
- parser fixture coverage in `ComponentDefinitionParserTest`
- one manual UI flow involving:
  - section visibility/editability
  - section image rendering
  - text property save/load with XML-sensitive characters
