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

package org.chibios.chibiforge.container;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * ComponentContent backed by a filesystem directory (the component/ directory).
 */
public class FilesystemContent implements ComponentContent {

    private final Path root;

    /**
     * @param root the component/ directory path
     */
    public FilesystemContent(Path root) {
        this.root = root;
    }

    @Override
    public InputStream open(String relativePath) throws IOException {
        Path resolved = root.resolve(relativePath);
        return Files.newInputStream(resolved);
    }

    @Override
    public List<String> list(String prefix) throws IOException {
        Path dir = root.resolve(prefix);
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.filter(Files::isRegularFile)
                .forEach(p -> result.add(root.relativize(p).toString()));
        }
        return result;
    }

    @Override
    public boolean exists(String relativePath) {
        return Files.exists(root.resolve(relativePath));
    }

    public Path getRoot() { return root; }
}
