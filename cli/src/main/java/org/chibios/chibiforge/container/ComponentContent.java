package org.chibios.chibiforge.container;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * Abstracts file listing and opening within the component/ subtree.
 * Implementations handle filesystem and JAR sources transparently.
 */
public interface ComponentContent {

    /**
     * Opens a file relative to the component/ directory.
     * @param relativePath e.g. "cfg/mcuconf.h.ftl", "resources/limits.xml"
     * @return an input stream for the file
     */
    InputStream open(String relativePath) throws IOException;

    /**
     * Lists files under a prefix relative to the component/ directory.
     * @param prefix e.g. "cfg/", "source/", "resources/"
     * @return list of relative paths (relative to component/)
     */
    List<String> list(String prefix) throws IOException;

    /**
     * Checks if a file exists relative to the component/ directory.
     */
    boolean exists(String relativePath);
}
