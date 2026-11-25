package com.example.cube.storage;

/**
 * Where attachment bytes live. The database keeps only the returned storage key.
 * Select the implementation with cube.storage.type (github | local).
 */
public interface FileStorageService {

    /**
     * Stores the content under a path such as "knowledge/42/3f2a..._report.pdf".
     * @return the storage key to persist and later pass to {@link #load} / {@link #delete}
     */
    String store(String path, byte[] content, String contentType);

    byte[] load(String storageKey);

    /** Deletes the object. Missing objects are ignored. */
    void delete(String storageKey);
}
