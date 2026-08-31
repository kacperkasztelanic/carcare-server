package com.kasztelanic.carcare.web.rest;

import org.apache.commons.io.FileUtils;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * Base class for the filesystem-touching integration tests. Points
 * {@code application.data-directory.location} at a single JVM-wide scratch root so no test writes
 * into the repo's shared {@code data/} directory or the volume a developer running {@code dev} is
 * using.
 * <p>
 * The root is created once in a static initializer and removed by a shutdown hook rather than via a
 * {@code static @TempDir} field: {@code @TempDir} resolves per concrete subclass, so each subclass
 * would register a different property value — a different context-cache key, and therefore a
 * separate Spring context per subclass. One shared root keeps the registered value identical across
 * subclasses so they share a single context; per-class file collisions are avoided by resolving
 * each class's files under the shared root, not by varying the property.
 */
public abstract class AbstractImageIT extends AbstractSessionIT {

    private static final Path SCRATCH_ROOT;

    static {
        try {
            SCRATCH_ROOT = Files.createTempDirectory("carcare-image-it-");
        } catch (IOException e) {
            throw new UncheckedIOException("Could not create the image-IT scratch root", e);
        }
        Runtime.getRuntime().addShutdownHook(
            new Thread(() -> FileUtils.deleteQuietly(SCRATCH_ROOT.toFile()), "carcare-image-it-cleanup"));
    }

    @DynamicPropertySource
    static void dataDirectory(DynamicPropertyRegistry registry) {
        registry.add("application.data-directory.location", SCRATCH_ROOT::toString);
    }

    /**
     * Resolves {@code fileName} against the shared scratch root, mirroring what the production path
     * helper produces for a contained name — without duplicating its expression or referencing the
     * data-directory property.
     */
    protected Path imagePath(String fileName) {
        return SCRATCH_ROOT.resolve(fileName);
    }

    /** Regular-file count anywhere under the scratch root — for before/after "nothing was written" checks. */
    protected long scratchFileCount() {
        try (Stream<Path> entries = Files.walk(SCRATCH_ROOT)) {
            return entries.filter(Files::isRegularFile).count();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
