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

package org.chibios.chibiforge;

import org.chibios.chibiforge.cli.GenerateCommand;
import picocli.CommandLine;

@CommandLine.Command(
        name = "chibiforge",
        description = "ChibiForge - Component-based configuration and code generation for embedded projects",
        mixinStandardHelpOptions = true,
        version = "ChibiForge 0.1.0-beta",
        subcommands = {GenerateCommand.class}
)
public class ChibiForgeMain implements Runnable {

    @Override
    public void run() {
        CommandLine.usage(this, System.out);
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new ChibiForgeMain()).execute(args);
        System.exit(exitCode);
    }
}
