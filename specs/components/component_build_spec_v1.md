# ChibiForge Component Build System — Specification v1

## 1. Purpose

This document specifies the **build system** for ChibiOS ChibiForge component plugins. It covers the Maven/Tycho project structure, the multi-module build layout, the p2 update site project, and the CI pipeline requirements.

The build system produces two artefacts per release:

1. **Individual plugin JARs** — one per component, consumable directly by the ChibiForge CLI and standalone GUI by dropping into `PLUGINS_ROOT`.
2. **A p2 update site** — a self-contained repository from which Eclipse users can install or update components through Eclipse's standard plugin manager (`Help → Install New Software`).

---

## 2. Technology Choices

| Tool | Role |
|---|---|
| **Maven** | Build orchestration, consistent with the ChibiForge tooling build |
| **Tycho** | Eclipse/OSGi-aware Maven extension — handles `MANIFEST.MF`, `build.properties`, `plugin.xml`, produces p2 metadata |
| **Eclipse Target Platform** | Declares the Eclipse version the components are built against |

Tycho is chosen over plain Maven JAR packaging because it:

- Understands `build.properties` natively, using it to determine JAR contents exactly as Eclipse PDE does.
- Produces valid p2 metadata (`.qualifier` version expansion, content.xml, artifacts.xml) required for a functional update site.
- Allows developers to import the projects into Eclipse and build from the IDE using the same project files.
- Requires no duplication between `build.properties` and `pom.xml` for resource inclusion — `build.properties` is the single source of truth for JAR contents.

Since the components contain no Java source, Tycho compilation is a no-op — it only performs packaging and p2 metadata generation, which is fast.

---

## 3. Repository Layout

All component projects live in a single repository alongside the update site project:

```
components/                                    # Repository root
  pom.xml                                      # Parent POM (Tycho configuration)
  target-platform/
    pom.xml
    chibiforge-components.target               # Eclipse target platform definition
  releng/
    org.chibios.chibiforge.components.repository/
      pom.xml                                  # Update site project
      category.xml                             # p2 category definitions
  platform/
    org.chibios.chibiforge.components.platform.stm32g4xx/
      pom.xml
      .project
      plugin.xml
      META-INF/MANIFEST.MF
      build.properties
      component/...
      rsc/...
    org.chibios.chibiforge.components.platform.stm32h7xx/
      ...
  kernel/
    org.chibios.chibiforge.components.kernel.rt/
      ...
    org.chibios.chibiforge.components.kernel.nil/
      ...
  chport/
    org.chibios.chibiforge.components.chport.armv7em/
      ...
    org.chibios.chibiforge.components.chport.armv7m/
      ...
  hal/
    org.chibios.chibiforge.components.hal.osal/
      ...
    org.chibios.chibiforge.components.hal.core/
      ...
  chhalport/
    org.chibios.chibiforge.components.chhalport.stm32g4xx/
      ...
  board/
    org.chibios.chibiforge.components.board.nucleo_g474re/
      ...
  oslib/
    org.chibios.chibiforge.components.oslib/
      ...
```

The top-level grouping by subsystem (`platform/`, `kernel/`, `hal/`, etc.) is a source organisation convention. Each leaf directory is an independent Maven/Tycho module. The parent POM aggregates all modules.

---

## 4. Parent POM

The parent POM at the repository root configures Tycho for all child modules:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
                             http://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>

  <groupId>org.chibios.chibiforge.components</groupId>
  <artifactId>components-parent</artifactId>
  <version>21.11.0-SNAPSHOT</version>
  <packaging>pom</packaging>

  <properties>
    <tycho.version>4.0.10</tycho.version>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
  </properties>

  <modules>
    <module>target-platform</module>
    <!-- Platform -->
    <module>platform/org.chibios.chibiforge.components.platform.stm32g4xx</module>
    <module>platform/org.chibios.chibiforge.components.platform.stm32h7xx</module>
    <!-- Kernel -->
    <module>kernel/org.chibios.chibiforge.components.kernel.rt</module>
    <module>kernel/org.chibios.chibiforge.components.kernel.nil</module>
    <!-- chport -->
    <module>chport/org.chibios.chibiforge.components.chport.armv7em</module>
    <!-- HAL -->
    <module>hal/org.chibios.chibiforge.components.hal.osal</module>
    <module>hal/org.chibios.chibiforge.components.hal.core</module>
    <!-- chhalport -->
    <module>chhalport/org.chibios.chibiforge.components.chhalport.stm32g4xx</module>
    <!-- Board -->
    <module>board/org.chibios.chibiforge.components.board.nucleo_g474re</module>
    <!-- OSLIB -->
    <module>oslib/org.chibios.chibiforge.components.oslib</module>
    <!-- Update site -->
    <module>releng/org.chibios.chibiforge.components.repository</module>
  </modules>

  <build>
    <plugins>
      <plugin>
        <groupId>org.eclipse.tycho</groupId>
        <artifactId>tycho-maven-plugin</artifactId>
        <version>${tycho.version}</version>
        <extensions>true</extensions>
      </plugin>
      <plugin>
        <groupId>org.eclipse.tycho</groupId>
        <artifactId>target-platform-configuration</artifactId>
        <version>${tycho.version}</version>
        <configuration>
          <target>
            <artifact>
              <groupId>org.chibios.chibiforge.components</groupId>
              <artifactId>chibiforge-components-target</artifactId>
              <version>${project.version}</version>
            </artifact>
          </target>
        </configuration>
      </plugin>
    </plugins>
  </build>

</project>
```

### Key points

- `<packaging>pom</packaging>` on the parent — it is an aggregator only.
- Tycho version is centralised here; all child modules inherit it.
- The target platform module is listed first so it resolves before any plugin module is built.

---

## 5. Target Platform

The target platform defines the Eclipse release the components are built against. Since components have no Java code, the only real requirement is a minimal Eclipse platform that provides the `org.eclipse.osgi` bundle (needed for OSGi metadata validation).

```xml
<!-- target-platform/pom.xml -->
<project>
  <modelVersion>4.0.0</modelVersion>
  <parent>
    <groupId>org.chibios.chibiforge.components</groupId>
    <artifactId>components-parent</artifactId>
    <version>21.11.0-SNAPSHOT</version>
    <relativePath>../pom.xml</relativePath>
  </parent>
  <artifactId>chibiforge-components-target</artifactId>
  <packaging>eclipse-target-definition</packaging>
</project>
```

```xml
<!-- target-platform/chibiforge-components.target -->
<?xml version="1.0" encoding="UTF-8" standalone="no"?>
<?pde version="3.8"?>
<target name="ChibiForge Components Target" sequenceNumber="1">
  <locations>
    <location includeAllPlatforms="false"
              includeConfigurePhase="true"
              includeMode="planner"
              includeSource="true"
              type="InstallableUnit">
      <unit id="org.eclipse.sdk.feature.group"
            version="0.0.0"/>
      <repository location="https://download.eclipse.org/releases/2024-09"/>
    </location>
  </locations>
</target>
```

The target platform pins the Eclipse release. Updating `repository location` and rerunning `mvn package` rebuilds all components against the new Eclipse release.

---

## 6. Component Plugin POM

Each component module has a minimal `pom.xml`. Because Tycho reads `MANIFEST.MF` and `build.properties` directly, the POM carries almost no configuration:

```xml
<!-- Example: kernel/org.chibios.chibiforge.components.kernel.rt/pom.xml -->
<project>
  <modelVersion>4.0.0</modelVersion>
  <parent>
    <groupId>org.chibios.chibiforge.components</groupId>
    <artifactId>components-parent</artifactId>
    <version>21.11.0-SNAPSHOT</version>
    <relativePath>../../pom.xml</relativePath>
  </parent>
  <artifactId>org.chibios.chibiforge.components.kernel.rt</artifactId>
  <packaging>eclipse-plugin</packaging>
</project>
```

`<packaging>eclipse-plugin</packaging>` is the only Tycho-specific declaration needed. Tycho reads the version from `META-INF/MANIFEST.MF` (`Bundle-Version`), the included resources from `build.properties` (`bin.includes`), and the plugin marker from `plugin.xml`. The `pom.xml` is a thin delegation to those files.

**Version synchronisation**: the `Bundle-Version` in `MANIFEST.MF` uses the OSGi qualifier convention: `21.11.0.qualifier`. Tycho replaces `.qualifier` with a timestamp or build ID at package time. The parent POM version uses Maven's `-SNAPSHOT` convention. The two are kept in sync by convention — both represent the same ChibiOS release version.

---

## 7. Update Site Project

The update site (p2 repository) project aggregates all component plugins into a single installable repository:

```xml
<!-- releng/org.chibios.chibiforge.components.repository/pom.xml -->
<project>
  <modelVersion>4.0.0</modelVersion>
  <parent>
    <groupId>org.chibios.chibiforge.components</groupId>
    <artifactId>components-parent</artifactId>
    <version>21.11.0-SNAPSHOT</version>
    <relativePath>../../pom.xml</relativePath>
  </parent>
  <artifactId>org.chibios.chibiforge.components.repository</artifactId>
  <packaging>eclipse-repository</packaging>
</project>
```

The `category.xml` defines how components are grouped in the Eclipse Install New Software dialog:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<site>

  <category-def name="org.chibios.chibiforge.components.category.platform"
                label="Platform Components">
    <description>
      MCU platform components: startup, linker, CMSIS headers.
    </description>
  </category-def>

  <category-def name="org.chibios.chibiforge.components.category.kernel"
                label="Kernel Components">
    <description>
      ChibiOS/RT, ChibiOS/NIL, port layers, and kernel extensions.
    </description>
  </category-def>

  <category-def name="org.chibios.chibiforge.components.category.hal"
                label="HAL Components">
    <description>
      HAL core, OSAL, HAL port layers, and peripheral drivers.
    </description>
  </category-def>

  <category-def name="org.chibios.chibiforge.components.category.board"
                label="Board Components">
    <description>
      Board support components for known hardware.
    </description>
  </category-def>

  <feature url="features/org.chibios.chibiforge.components.platform.stm32g4xx.jar"
           id="org.chibios.chibiforge.components.platform.stm32g4xx"
           version="0.0.0">
    <category name="org.chibios.chibiforge.components.category.platform"/>
  </feature>

  <feature url="features/org.chibios.chibiforge.components.kernel.rt.jar"
           id="org.chibios.chibiforge.components.kernel.rt"
           version="0.0.0">
    <category name="org.chibios.chibiforge.components.category.kernel"/>
  </feature>

  <!-- ... one <feature> entry per component ... -->

</site>
```

`<packaging>eclipse-repository</packaging>` tells Tycho to generate the full p2 repository structure: `content.xml`, `artifacts.xml`, and the `plugins/` directory containing all component JARs.

---

## 8. Build Output

Running `mvn package` from the repository root produces:

```
releng/org.chibios.chibiforge.components.repository/target/repository/
  artifacts.xml                    # p2 artifact metadata
  content.xml                      # p2 content metadata (installable units)
  plugins/
    org.chibios.chibiforge.components.platform.stm32g4xx_21.11.0.20241015.jar
    org.chibios.chibiforge.components.kernel.rt_21.11.0.20241015.jar
    org.chibios.chibiforge.components.kernel.nil_21.11.0.20241015.jar
    org.chibios.chibiforge.components.chport.armv7em_21.11.0.20241015.jar
    org.chibios.chibiforge.components.hal.osal_21.11.0.20241015.jar
    org.chibios.chibiforge.components.hal.core_21.11.0.20241015.jar
    org.chibios.chibiforge.components.chhalport.stm32g4xx_21.11.0.20241015.jar
    org.chibios.chibiforge.components.board.nucleo_g474re_21.11.0.20241015.jar
    org.chibios.chibiforge.components.oslib_21.11.0.20241015.jar
```

The `plugins/` directory is also the `PLUGINS_ROOT` for the ChibiForge CLI and standalone GUI — point the tools at this directory and all built components are immediately available without any additional installation step.

### Dual use of the build output

| Consumer | How it uses the output |
|---|---|
| Eclipse users | Point `Help → Install New Software` at the `repository/` directory or a hosted URL |
| ChibiForge CLI | Set `CHIBIFORGE_PLUGINS_ROOT` to `repository/plugins/` |
| ChibiForge GUI | Configure plugins directory to `repository/plugins/` |

---

## 9. Adding a New Component to the Build

1. Create the component project directory under the appropriate subsystem folder.
2. Add the four required files: `.project`, `plugin.xml`, `META-INF/MANIFEST.MF`, `build.properties`, and the `component/` subtree.
3. Add a minimal `pom.xml` with `<packaging>eclipse-plugin</packaging>` pointing to the parent POM.
4. Add a `<module>` entry in the parent `pom.xml`.
5. Add a `<feature>` entry in `releng/.../category.xml` assigning the component to a category.
6. Run `mvn package` — the new component JAR appears in `repository/plugins/`.

---

## 10. CI Pipeline

The recommended CI pipeline (GitHub Actions, GitLab CI, or equivalent):

```
trigger: push to main, pull request

steps:
  1. Checkout repository
  2. Set up JDK 17+
  3. mvn -B package
  4. Archive repository/target/repository/ as build artefact
  5. On tagged release: publish repository/ to the ChibiOS update site host
```

The `-B` flag (batch mode) suppresses interactive prompts. No Eclipse installation is required on the CI runner — Tycho downloads the target platform from the configured p2 URL on first build and caches it.

---

## 11. Open Items

1. **Feature projects vs plain plugins**: strictly speaking, p2 best practice groups plugins into Eclipse *feature* projects (`eclipse-feature` packaging) which are then included in the update site. For simplicity this spec treats each component plugin as a directly installable unit. Adding feature projects is a future enhancement — it enables grouping (e.g. "all STM32G4xx components") for one-click installation of a related set.

2. **Signed JARs**: for a production update site, plugin JARs should be signed with a code-signing certificate. Tycho supports JAR signing via the `tycho-gpg-plugin` or `jarsigner-maven-plugin`. Deferred to the release engineering phase.

3. **Update site hosting**: the physical hosting of the p2 update site (GitHub Pages, a dedicated server, etc.) is outside the scope of this document.

4. **Version qualifier strategy**: the `.qualifier` in `Bundle-Version` is replaced by Tycho with a build timestamp by default. Adopting a `forceContextQualifier` or a Git-commit-hash-based qualifier for reproducible builds is a CI configuration decision to be made during implementation.
