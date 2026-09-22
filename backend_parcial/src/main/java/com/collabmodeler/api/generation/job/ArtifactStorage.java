package com.collabmodeler.api.generation.job;

public interface ArtifactStorage {
    void put(String key, byte[] contents, String contentType);
    StoredArtifact get(String key);
    void delete(String key);
    record StoredArtifact(byte[] contents, String contentType) {}
}
