package com.kasztelanic.carcare.service.impl;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.kasztelanic.carcare.config.ApplicationProperties;
import com.kasztelanic.carcare.service.ImageStorageService;
import com.kasztelanic.carcare.util.UuidProvider;

@Service
public class ImageStorageServiceImpl implements ImageStorageService {

    private final Logger log = LoggerFactory.getLogger(ImageStorageServiceImpl.class);

    private static final String DEFAULT = "default.png";

    private final ApplicationProperties applicationProperties;

    @Autowired
    public ImageStorageServiceImpl(ApplicationProperties applicationProperties) {
        this.applicationProperties = applicationProperties;
    }

    @Override
    public String save(byte[] image, String fileType) {
        String fileName = UuidProvider.newUuid() + "." + fileType;
        Path path = prepareImagePath(fileName);
        try {
            FileUtils.writeByteArrayToFile(path.toFile(), image);
        } catch (IOException e) {
            log.error("Could not save file {}", path);
        }
        return fileName;
    }

    @Override
    public byte[] load(String name) {
        Path path = prepareImagePath(name);
        try {
            if (!path.toFile().isFile()) {
                return IOUtils.resourceToByteArray(DEFAULT, getClass().getClassLoader());
            }
            return FileUtils.readFileToByteArray(path.toFile());
        } catch (IOException e) {
            log.error("Could not load file {}", path);
            return new byte[] {};
        }
    }

    @Override
    public boolean delete(String name) {
        return FileUtils.deleteQuietly(prepareImagePath(name).toFile());
    }

    private Path prepareImagePath(String fileName) {
        return Paths.get(applicationProperties.getDataDirectory().getLocation()).normalize().resolve(fileName)
                .normalize().toAbsolutePath();
    }
}
