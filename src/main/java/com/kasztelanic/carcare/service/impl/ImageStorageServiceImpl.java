package com.kasztelanic.carcare.service.impl;

import com.kasztelanic.carcare.config.ApplicationProperties;
import com.kasztelanic.carcare.service.ImageStorageService;
import com.kasztelanic.carcare.service.exception.ImagePathNotContainedException;
import com.kasztelanic.carcare.service.exception.ImageStorageException;
import com.kasztelanic.carcare.service.exception.UnsupportedImageFormatException;
import com.kasztelanic.carcare.util.UuidProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.tika.Tika;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Locale;
import java.util.Map;

//TODO refactor
@Slf4j
@Service
@RequiredArgsConstructor
public class ImageStorageServiceImpl implements ImageStorageService {

    private static final String DEFAULT = "default.png";

    /** The only types written to the volume, mapped to the extension the stored file gets. */
    private static final Map<String, String> ALLOWED_TYPES = Map.of(
        "image/png", ".png",
        "image/jpeg", ".jpg");

    private final ApplicationProperties applicationProperties;
    private final Tika tika = new Tika();

    @Override
    public String save(byte[] image, String fileType) {
        if (image == null) {
            return "";
        }
        Path path = null;
        try {
            String extension = allowedExtension(image, fileType);
            String fileName = UuidProvider.newUuid() + extension;
            path = prepareImagePath(fileName);
            Files.write(path, image, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            return fileName;
        } catch (ImagePathNotContainedException e) {
            log.error("Refused to save image outside the data directory: {}", e.getName());
        } catch (IOException e) {
            deletePartialFile(path, e);
            throw new ImageStorageException("Could not save image.", e);
        }
        return "";
    }

    @Override
    public byte[] load(String name) {
        if (name == null || name.isEmpty()) {
            return defaultImage();
        }
        try {
            Path path = prepareImagePath(name);
            if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                return defaultImage();
            }
            try (InputStream input = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)) {
                return input.readAllBytes();
            }
        } catch (ImagePathNotContainedException e) {
            log.error("Refused to load image outside the data directory: {}", e.getName());
            return defaultImage();
        } catch (IOException e) {
            log.error("Could not load file.");
            return new byte[]{};
        }
    }

    @Override
    public boolean delete(String name) {
        if (name == null || name.isEmpty()) {
            return false;
        }
        try {
            return Files.deleteIfExists(prepareImagePath(name));
        } catch (ImagePathNotContainedException e) {
            log.error("Refused to delete image outside the data directory: {}", e.getName());
            return false;
        } catch (IOException e) {
            log.warn("Could not delete image file.", e);
            return false;
        }
    }

    private void deletePartialFile(Path path, IOException writeFailure) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException cleanupFailure) {
            writeFailure.addSuppressed(cleanupFailure);
            log.warn("Could not clean up failed image write: {}", path, cleanupFailure);
        }
    }

    /**
     * Determines the stored file's extension from the actual bytes, never the client-declared type.
     * Accepts only byte-verified PNG and JPEG. A client that declares a specific {@code image/*}
     * type contradicted by the bytes is rejected; {@code null}, blank, {@code application/octet-stream}
     * and any non-{@code image/*} declaration are treated as "no claim" and accepted — that is the
     * behaviour that produced the legacy {@code *.bin} files and the one write-path client behaviour
     * with production evidence.
     */
    private String allowedExtension(byte[] image, String declaredType) {
        String detected = tika.detect(image);
        String extension = ALLOWED_TYPES.get(detected);
        if (extension == null) {
            throw new UnsupportedImageFormatException(detected);
        }
        String declared = bareType(declaredType);
        if (declared != null && declared.startsWith("image/") && !declared.equals(detected)) {
            throw new UnsupportedImageFormatException(declared, detected);
        }
        return extension;
    }

    private static String bareType(String contentType) {
        if (contentType == null) {
            return null;
        }
        int semicolon = contentType.indexOf(';');
        String bare = (semicolon >= 0 ? contentType.substring(0, semicolon) : contentType)
            .trim().toLowerCase(Locale.ROOT);
        return bare.isEmpty() ? null : bare;
    }

    private byte[] defaultImage() {
        try {
            return IOUtils.resourceToByteArray(DEFAULT, getClass().getClassLoader());
        } catch (IOException e) {
            log.error("Could not load default image.");
            return new byte[]{};
        }
    }

    /**
     * Resolves {@code fileName} under the configured data directory and refuses anything that
     * escapes it. {@code toAbsolutePath()} must precede {@code normalize()}: the reverse order
     * cannot collapse leading {@code ..} segments, leaving the containment check inspecting an
     * unnormalised path. The {@code equals(root)} clause refuses a name that resolves to the
     * directory itself (today only {@code ""}, which {@code load}/{@code delete} already
     * short-circuit) so the rule lives in one place.
     */
    private Path prepareImagePath(String fileName) {
        Path root = Paths.get(applicationProperties.getDataDirectory().getLocation())
            .toAbsolutePath().normalize();
        Path candidate = root.resolve(fileName).toAbsolutePath().normalize();
        if (!candidate.startsWith(root) || candidate.equals(root)) {
            throw new ImagePathNotContainedException(fileName);
        }
        Path current = root;
        for (Path component : root.relativize(candidate)) {
            current = current.resolve(component);
            if (Files.isSymbolicLink(current)) {
                throw new ImagePathNotContainedException(fileName);
            }
        }
        return candidate;
    }
}
