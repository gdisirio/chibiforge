# ChibiForge Specification Addendum — Implementation Decisions

This document records implementation decisions, clarifications, and deviations from the specifications. It is maintained during development and fed back into the specification process.

---

## CLI / Generator Engine

### Config file naming
The `--config` CLI option accepts any filename, not just `chibiforge.xcfg`. The configuration root is derived from the parent directory of the specified file. The engine uses the exact path provided — it does not hardcode `chibiforge.xcfg`.

### FMPP integration
The generator uses FMPP's `Engine` class directly (not `fmpp.Settings`). This gives full control over source/output roots, data injection, and extension handling. FreeMarker is the freemarker-codegen fork (`org.freemarker:freemarker:2.3.35`) which adds code-first mode (`.ftlc`).

### Output boundary enforcement
The spec (§8.2) requires all output to stay within the configuration root. FMPP's `pp.changeOutputFile` operates relative to the output root, which is `generated/` for `cfg/` templates and the configuration root for `cfg_root_wa/`/`cfg_root_wo/` templates. There is no runtime enforcement of the boundary — it is a convention that component authors must follow. Patching FMPP to enforce it is not practical.

### `pp.dropOutputFile` / `pp.changeOutputFile`
Templates in `cfg/`, `cfg_root_wa/`, and `cfg_root_wo/` should NOT use `pp.dropOutputFile` + `pp.changeOutputFile` for their primary output — the convention-based directory routing handles this automatically. These FMPP directives remain available for edge cases (e.g., a single template producing multiple output files).

### Dependencies
All FMPP and freemarker-codegen JARs are vendored in `cli/lib/` as a Maven file-based repository. The build is fully self-contained — no external setup or `publishToMavenLocal` step needed.

### Build output
The fat JAR (`chibiforge.jar`) and wrapper script are placed in the top-level `bin/` directory, parallel to `cli/`. This directory will also hold future UI binaries.

---

## UI Decisions (from UI spec v1 review)

### Undo/Redo scope
Per-field scope, not a full command stack. Each field tracks its own previous value.

### Save behavior with invalid fields
Save writes the DOM to disk. If a field contains invalid text, the DOM retains the last valid value (per §9.2 of the UI spec — DOM is only updated with valid values). The invalid text remains visible in the widget with an error indicator, but the saved `chibiforge.xcfg` reflects the last valid state.

### Component ordering
Components appear in the Components view in creation order (the order they were added to the configuration). This matches the order in `chibiforge.xcfg`. Reorder buttons may be added in a future version.

### New Configuration flow
Suspended — not implementing in the initial UI release. The application opens existing `.xcfg` files only.

### Engine reuse
The UI module depends on the CLI module as a library for the generator engine, component loading, data model building, etc. There is no separate `engine/` package in the UI — the suggested package structure in §15.1 of the UI spec should remove the `engine/` entry.

### Build system
All Maven. The UI module will be a Maven module alongside `cli/`, not a separate Gradle project. This avoids mixing build systems.

### Files tab (Inspector)
Shows a simple file listing of the configuration root directory tree. No cross-referencing with component containers to determine which component produced which file. Color-coding and badges described in §5.3 of the UI spec are deferred — the initial implementation shows a plain file tree.
