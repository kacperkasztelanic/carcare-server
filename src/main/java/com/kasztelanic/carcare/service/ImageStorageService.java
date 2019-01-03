package com.kasztelanic.carcare.service;

public interface ImageStorageService {

    String save(byte[] image, String fileType);

    byte[] load(String name);
    
    boolean delete(String name);
}
