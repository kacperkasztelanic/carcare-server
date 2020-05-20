package com.kasztelanic.carcare.service.impl;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.tika.mime.MimeTypeException;
import org.apache.tika.mime.MimeTypes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.kasztelanic.carcare.config.ApplicationProperties;
import com.kasztelanic.carcare.service.ImageStorageService;
import com.kasztelanic.carcare.util.UuidProvider;

//TODO refactor to be like in hrtool
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
        } catch (IOException e) {
            log.error("Could not save file.");
        }
        return "";
    }

    @Override
    public byte[] load(String name) {
        try {
            if (name == null || name.isEmpty() || !prepareImagePath(name).toFile().isFile()) {
                return IOUtils.resourceToByteArray(DEFAULT, getClass().getClassLoader());
            }
            return FileUtils.readFileToByteArray(prepareImagePath(name).toFile());
        } catch (IOException e) {
            log.error("Could not load file.");
            return new byte[] {};
        }
    }

    @Override
    public boolean delete(String name) {
        if (name == null || name.isEmpty()) {
            return false;
        }
        return FileUtils.deleteQuietly(prepareImagePath(name).toFile());
    }

    private Path prepareImagePath(String fileName) {
        return Paths.get(applicationProperties.getDataDirectory().getLocation()).normalize().resolve(fileName)
                .normalize().toAbsolutePath();
    }
}
