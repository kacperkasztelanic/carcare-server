package com.kasztelanic.carcare.service.impl;

import com.kasztelanic.carcare.config.ApplicationProperties;
import com.kasztelanic.carcare.fixtures.SessionFixtures;
import com.kasztelanic.carcare.service.exception.ImagePathNotContainedException;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Raw-unit coverage for the storage service — constructed directly with a stub
 * {@link ApplicationProperties} pointing at a {@link TempDir}, no Spring context. Pins the current
 * behaviour so later phases have a baseline that visibly moves.
 */
class ImageStorageServiceImplTest {

    @TempDir
    Path dataDir;

    private ImageStorageServiceImpl service;

    @BeforeEach
    void setUp() {
        ApplicationProperties properties = new ApplicationProperties();
        properties.getDataDirectory().setLocation(dataDir.toString());
        service = new ImageStorageServiceImpl(properties);
    }

    @Test
    void savesAndLoadsPngRoundTrip() {
        byte[] png = SessionFixtures.pngBytes();

        String name = service.save(png, "image/png");

        assertThat(name).endsWith(".png");
        assertThat(service.load(name)).isEqualTo(png);
    }

    @Test
    void savesAndLoadsJpegRoundTrip() {
        byte[] jpeg = SessionFixtures.jpegBytes();

        String name = service.save(jpeg, "image/jpeg");

        assertThat(name).endsWith(".jpg");
        assertThat(service.load(name)).isEqualTo(jpeg);
    }

    @Test
    void saveWithNullImageReturnsEmptyString() {
        assertThat(service.save(null, "image/png")).isEmpty();
        assertThat(filesInDataDir()).isEmpty();
    }

    @Test
    void saveWithUnparseableContentTypeReturnsEmptyStringAndWritesNothing() {
        assertThat(service.save(SessionFixtures.pngBytes(), "not a mime type")).isEmpty();
        assertThat(filesInDataDir()).isEmpty();
    }

    @Test
    void loadOfNullEmptyOrMissingReturnsDefaultPng() throws IOException {
        byte[] defaultPng = IOUtils.resourceToByteArray("default.png", getClass().getClassLoader());

        assertThat(service.load(null)).isEqualTo(defaultPng);
        assertThat(service.load("")).isEqualTo(defaultPng);
        assertThat(service.load("missing.png")).isEqualTo(defaultPng);
    }

    @Test
    void deleteOfNullOrEmptyReturnsFalse() {
        assertThat(service.delete(null)).isFalse();
        assertThat(service.delete("")).isFalse();
    }

    @Test
    void deleteOfExistingFileReturnsTrueAndRemovesIt() throws IOException {
        String name = service.save(SessionFixtures.pngBytes(), "image/png");
        assertThat(Files.exists(dataDir.resolve(name))).isTrue();

        assertThat(service.delete(name)).isTrue();

        assertThat(Files.exists(dataDir.resolve(name))).isFalse();
        byte[] defaultPng = IOUtils.resourceToByteArray("default.png", getClass().getClassLoader());
        assertThat(service.load(name)).isEqualTo(defaultPng);
    }

    // --- S-05 containment (Phase 3) ---------------------------------------------------------------

    /** The measured escape table — none of these is reachable through REST, so pin it directly. */
    private static final List<String> ESCAPING_NAMES = List.of(
        "../../../../etc/passwd", "/etc/passwd", "..", "");

    @Test
    void prepareImagePathRefusesNamesThatEscapeTheDataDirectory() {
        for (String escaping : ESCAPING_NAMES) {
            assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(service, "prepareImagePath", escaping))
                .as("name %s", escaping)
                .isInstanceOf(ImagePathNotContainedException.class);
        }
    }

    @Test
    void escapingNameMakesEachPublicMethodReturnItsSentinelRatherThanThrow() throws IOException {
        byte[] defaultPng = IOUtils.resourceToByteArray("default.png", getClass().getClassLoader());
        for (String escaping : List.of("../../../../etc/passwd", "/etc/passwd", "..")) {
            assertThat(service.load(escaping)).as("load(%s)", escaping).isEqualTo(defaultPng);
            assertThat(service.delete(escaping)).as("delete(%s)", escaping).isFalse();
        }
        assertThat(filesInDataDir()).isEmpty();
    }

    @Test
    void containedNameThatNormalisesInsideTheRootIsAccepted() throws IOException {
        byte[] png = SessionFixtures.pngBytes();
        Files.write(dataDir.resolve("ok.png"), png);

        // "sub/../ok.png" normalises to <root>/ok.png — contained, must be served, not refused.
        assertThat(service.load("sub/../ok.png")).isEqualTo(png);
    }

    @Test
    void resolvedPathForAContainedNameIsAbsoluteAndNormalised() {
        Path expected = Paths.get(dataDir.toString()).toAbsolutePath().normalize().resolve("abc.png");

        Path resolved = ReflectionTestUtils.invokeMethod(service, "prepareImagePath", "sub/../abc.png");

        assertThat(resolved).isAbsolute().isEqualTo(resolved.normalize()).isEqualTo(expected);
    }

    private List<String> filesInDataDir() {
        try (Stream<Path> entries = Files.list(dataDir)) {
            return entries.filter(Files::isRegularFile).map(p -> p.getFileName().toString()).toList();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
