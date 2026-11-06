package com.clouddocs.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Service
public class LocalFileStorageService {
    private final Path root;

    public LocalFileStorageService(
            @Value("${app.local-storage.directory:./storage}") String directory) {
        this.root = Paths.get(directory).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new IllegalStateException("Không thể tạo thư mục lưu file local: " + root, e);
        }
    }

    public void store(String key, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File tải lên không được để trống");
        }
        Path target = safePath(key);
        try {
            Files.createDirectories(target.getParent());
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Không thể lưu file local", e);
        }
    }

    public Resource load(String key) {
        Path target = safePath(key);
        try {
            if (!Files.exists(target) || !Files.isRegularFile(target)) {
                return null;
            }
            Resource resource = new UrlResource(target.toUri());
            return resource.exists() && resource.isReadable() ? resource : null;
        } catch (IOException e) {
            throw new IllegalStateException("Không thể đọc file local", e);
        }
    }

    public void deleteIfExists(String key) {
        try {
            Files.deleteIfExists(safePath(key));
        } catch (IOException ignored) {
            // Không để việc dọn file local làm hỏng thao tác xóa metadata.
        }
    }

    private Path safePath(String key) {
        String normalizedKey = key == null ? "" : key.replace("\\", "/");
        Path target = root.resolve(normalizedKey).normalize();
        if (!target.startsWith(root)) {
            throw new IllegalArgumentException("Storage key không hợp lệ");
        }
        return target;
    }
}
