package com.kasztelanic.carcare.service.impl;

import com.kasztelanic.carcare.config.ApplicationProperties;
import com.kasztelanic.carcare.fixtures.SessionFixtures;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

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

    private List<String> filesInDataDir() {
        try (Stream<Path> entries = Files.list(dataDir)) {
            return entries.filter(Files::isRegularFile).map(p -> p.getFileName().toString()).toList();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
