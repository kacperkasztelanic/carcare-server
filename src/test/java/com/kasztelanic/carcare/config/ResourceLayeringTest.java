package com.kasztelanic.carcare.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the main/test resource trees against classpath shadowing.
 */
class ResourceLayeringTest {

    @Test
    void mainAndTestResourceTreesDoNotShareRelativePaths() throws IOException {
        Set<String> mainResources = relativeFiles(Path.of("src/main/resources"));
        Set<String> testResources = relativeFiles(Path.of("src/test/resources"));
        Set<String> shadowedResources = new HashSet<>(mainResources);
        shadowedResources.retainAll(testResources);

        assertThat(shadowedResources)
            .as("main and test resource trees must not shadow each other")
            .isEmpty();
    }

    private Set<String> relativeFiles(Path root) throws IOException {
        try (Stream<Path> paths = Files.walk(root)) {
            return paths
                .filter(Files::isRegularFile)
                .map(root::relativize)
                .map(Path::toString)
                .collect(Collectors.toSet());
        }
    }
}
