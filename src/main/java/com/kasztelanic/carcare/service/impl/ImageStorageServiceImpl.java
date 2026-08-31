package com.kasztelanic.carcare.service.impl;

import com.kasztelanic.carcare.config.ApplicationProperties;
import com.kasztelanic.carcare.service.ImageStorageService;
import com.kasztelanic.carcare.service.exception.ImagePathNotContainedException;
import com.kasztelanic.carcare.util.UuidProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.tika.mime.MimeTypeException;
import org.apache.tika.mime.MimeTypes;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

//TODO refactor
@Slf4j
@Service
@RequiredArgsConstructor
public class ImageStorageServiceImpl implements ImageStorageService {

    private static final String DEFAULT = "default.png";

    private final ApplicationProperties applicationProperties;

    @Override
    public String save(byte[] image, String fileType) {
        if (image == null) {
            return "";
        }
        try {
            String extension = MimeTypes.getDefaultMimeTypes().forName(fileType).getExtension();
            String fileName = UuidProvider.newUuid() + extension;
            Path path = prepareImagePath(fileName);
            FileUtils.writeByteArrayToFile(path.toFile(), image);
            return fileName;
        } catch (MimeTypeException e) {
            log.error("Illegal content type: {}", fileType);
        } catch (ImagePathNotContainedException e) {
            log.error("Refused to save image outside the data directory: {}", e.getName());
        } catch (IOException e) {
            log.error("Could not save file.");
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
            if (!path.toFile().isFile()) {
                return defaultImage();
            }
            return FileUtils.readFileToByteArray(path.toFile());
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
            return FileUtils.deleteQuietly(prepareImagePath(name).toFile());
        } catch (ImagePathNotContainedException e) {
            log.error("Refused to delete image outside the data directory: {}", e.getName());
            return false;
        }
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
        return candidate;
    }
}
