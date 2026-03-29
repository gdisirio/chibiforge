/*
    ChibiOS - Copyright (C) 2025-2026 Giovanni Di Sirio.

    This file is part of ChibiOS.

    ChibiOS is free software; you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation version 3 of the License.

    ChibiOS is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package org.chibios.chibiforge.generator;

import java.nio.file.Path;

/**
 * Runtime context for a generation run.
 */
public class GenerationContext {
    private final Path configFile;
    private final Path configRoot;
    private final Path generatedRoot;
    private final String target;
    private final boolean dryRun;
    private final boolean verbose;

    public GenerationContext(Path configFile, Path configRoot, String target, boolean dryRun, boolean verbose) {
        this.configFile = configFile;
        this.configRoot = configRoot;
        this.generatedRoot = configRoot.resolve("generated");
        this.target = target;
        this.dryRun = dryRun;
        this.verbose = verbose;
    }

    public Path getConfigFile() { return configFile; }
    public Path getConfigRoot() { return configRoot; }
    public Path getGeneratedRoot() { return generatedRoot; }
    public String getTarget() { return target; }
    public boolean isDryRun() { return dryRun; }
    public boolean isVerbose() { return verbose; }
}
