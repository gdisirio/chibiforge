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
