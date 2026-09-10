package com.docpipeline.storage;

import java.io.InputStream;
import java.time.Duration;

public interface StorageService {
    String generatePresignedPutUrl(String key, String contentType, Duration expiration);
    String generatePresignedGetUrl(String key, Duration expiration);
    boolean doesObjectExist(String key);
    long getObjectSize(String key);
    InputStream openObject(String key);
    void putString(String key, String contentType, String value);
    void deleteObject(String key);
}
