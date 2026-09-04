package dev.modulithforge.storage.r2;

import dev.modulithforge.identity.model.Admin;
import dev.modulithforge.identity.model.Role;
import dev.modulithforge.identity.model.User;

import dev.modulithforge.storage.r2.CloudflareR2Config;
import dev.modulithforge.profile.ProfileImageStorage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(prefix = "app.modules.image-storage", name = "enabled", havingValue = "true")
public class ImageUploadService implements ProfileImageStorage {

    private final S3Client cloudflareR2Client;
    private final CloudflareR2Config r2Config;
    private static final byte[] MAGIC_JPEG = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] MAGIC_PNG = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
    private static final byte[] MAGIC_GIF87 = new byte[]{0x47, 0x49, 0x46, 0x38, 0x37, 0x61}; // GIF87a
    private static final byte[] MAGIC_GIF89 = new byte[]{0x47, 0x49, 0x46, 0x38, 0x39, 0x61}; // GIF89a
    private static final byte[] MAGIC_BMP = new byte[]{0x42, 0x4D};                             // BM
    private static final byte[] MAGIC_RIFF = new byte[]{0x52, 0x49, 0x46, 0x46};                // RIFF (WebP container)
    private static final byte[] MAGIC_WEBP = new byte[]{0x57, 0x45, 0x42, 0x50};                // WEBP (at offset 8)
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
        "jpg", "jpeg", "png", "gif", "bmp", "webp"
    );
    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024;
    public String uploadProfileImage(MultipartFile file, String role, Long userId) throws IOException {
        validateImage(file);
        String normalizedRole = normalizeRole(role);

        String fileName = generateFileName(file, "profile", normalizedRole, userId);
        String key = "profiles/" + normalizedRole + "/" + fileName;

        uploadToR2(file, key);

        return getPublicUrl(key);
    }
    public void deleteImage(String imageUrl) {
        if (imageUrl == null || imageUrl.isEmpty()) {
            return;
        }

        try {
            String key = extractKeyFromUrl(imageUrl);

            DeleteObjectRequest deleteRequest = DeleteObjectRequest.builder()
                    .bucket(r2Config.getBucketName())
                    .key(key)
                    .build();

            cloudflareR2Client.deleteObject(deleteRequest);
            log.info("Deleted image from R2: {}", key);
        } catch (Exception e) {
            log.error("Error deleting image from R2: {}", imageUrl, e);
            throw new IllegalStateException("Image could not be deleted", e);
        }
    }

    @Override
    public void delete(String imageUrl) {
        deleteImage(imageUrl);
    }
    public String updateProfileImage(MultipartFile file, String role, Long userId, String oldImageUrl) throws IOException {
        if (oldImageUrl != null && !oldImageUrl.isEmpty()) {
            deleteImage(oldImageUrl);
        }
        String normalizedRole = normalizeRole(role);
        return uploadProfileImage(file, normalizedRole, userId);
    }
    private void validateImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Dosya boş olamaz");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("Dosya boyutu 5MB'dan büyük olamaz");
        }
        String detectedMimeType = detectFileType(file);
        if (detectedMimeType == null) {
            String filename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "unknown";
            log.warn("File upload rejected: Invalid or disallowed file type for '{}'", filename);
            throw new IllegalArgumentException("Geçersiz dosya formatı. Sadece JPG, PNG, GIF, BMP ve WEBP formatları desteklenir");
        }

        log.debug("File validated successfully. Detected type: {}, File: {}",
                 detectedMimeType, file.getOriginalFilename());
    }
    private String detectFileType(MultipartFile file) {
        String filename = file.getOriginalFilename();
        if (filename != null) {
            String extension = getFileExtension(filename).toLowerCase();
            if (!ALLOWED_EXTENSIONS.contains(extension)) {
                log.warn("Rejected file upload: disallowed extension '{}' for file '{}'", extension, filename);
                return null;
            }
        }
        try (InputStream is = file.getInputStream()) {
            byte[] header = new byte[12];
            int bytesRead = is.read(header);
            if (bytesRead < 2) {
                log.warn("Rejected file upload: file too small (less than 2 bytes)");
                return null; // Too small to be a valid file
            }
            if (bytesRead >= 8 && startsWith(header, MAGIC_PNG)) {
                return "image/png";
            }
            if (bytesRead >= 3 && startsWith(header, MAGIC_JPEG)) {
                return "image/jpeg";
            }
            if (bytesRead >= 6 && (startsWith(header, MAGIC_GIF87) || startsWith(header, MAGIC_GIF89))) {
                return "image/gif";
            }
            if (bytesRead >= 12 && startsWith(header, MAGIC_RIFF) && regionMatches(header, 8, MAGIC_WEBP)) {
                return "image/webp";
            }
            if (bytesRead >= 2 && startsWith(header, MAGIC_BMP)) {
                return "image/bmp";
            }

            log.warn("Rejected file upload: magic bytes did not match any allowed type for file '{}'", filename);
            return null;

        } catch (IOException e) {
            log.error("Failed to read file content for type detection: {}", e.getMessage(), e);
            return null;
        }
    }
    private boolean startsWith(byte[] data, byte[] prefix) {
        return data.length >= prefix.length && Arrays.equals(
            Arrays.copyOfRange(data, 0, prefix.length), prefix
        );
    }
    private boolean regionMatches(byte[] data, int offset, byte[] target) {
        if (data.length < offset + target.length) {
            return false;
        }
        return Arrays.equals(
            Arrays.copyOfRange(data, offset, offset + target.length), target
        );
    }
    private String getFileExtension(String filename) {
        int lastDot = filename.lastIndexOf('.');
        if (lastDot < 0 || lastDot == filename.length() - 1) {
            return "";
        }
        return filename.substring(lastDot + 1);
    }
    private void uploadToR2(MultipartFile file, String key) throws IOException {
        try {
            String contentType = detectFileType(file);
            if (contentType == null) {
                contentType = "application/octet-stream"; // Fallback (should never happen after validation)
            }

            PutObjectRequest putRequest = PutObjectRequest.builder()
                    .bucket(r2Config.getBucketName())
                    .key(key)
                    .contentType(contentType)
                    .contentLength(file.getSize())
                    .build();

            cloudflareR2Client.putObject(putRequest,
                    RequestBody.fromInputStream(file.getInputStream(), file.getSize()));

            log.info("Uploaded image to R2: {} with content type: {}", key, contentType);
        } catch (S3Exception e) {
            log.error("Error uploading to R2: {}", key, e);
            throw new IllegalStateException("Image could not be uploaded", e);
        }
    }
    private String generateFileName(MultipartFile file, String prefix, String role, Long entityId) {
        String originalFileName = file.getOriginalFilename();
        String extension = "";

        if (originalFileName != null && originalFileName.contains(".")) {
            extension = originalFileName.substring(originalFileName.lastIndexOf("."));
        }

        String uuid = UUID.randomUUID().toString();

        if (role != null) {
            return String.format("%s_%s_%d_%s%s", prefix, role, entityId, uuid, extension);
        } else {
            return String.format("%s_%d_%s%s", prefix, entityId, uuid, extension);
        }
    }
    private String getPublicUrl(String key) {
        return r2Config.getPublicDomain() + "/" + key;
    }
    private String extractKeyFromUrl(String url) {
        try {
            URI base = new URI(r2Config.getPublicDomain()).normalize();
            URI candidate = new URI(url).normalize();
            if (!sameOrigin(base, candidate) || candidate.getRawQuery() != null || candidate.getRawFragment() != null) {
                throw new IllegalArgumentException("Image URL does not belong to the configured storage origin");
            }

            String basePath = normalizeBasePath(base.getPath());
            String candidatePath = candidate.getPath();
            if (!candidatePath.startsWith(basePath)) {
                throw new IllegalArgumentException("Image URL is outside the configured storage path");
            }
            String key = candidatePath.substring(basePath.length());
            if (key.startsWith("/")) {
                key = key.substring(1);
            }
            if (!key.matches("^profiles/(user|admin)/[A-Za-z0-9._-]+$")) {
                throw new IllegalArgumentException("Image URL has an invalid object key");
            }
            return key;
        } catch (URISyntaxException exception) {
            throw new IllegalArgumentException("Image URL is invalid", exception);
        }
    }

    private boolean sameOrigin(URI first, URI second) {
        return first.getScheme() != null
                && first.getScheme().equalsIgnoreCase(second.getScheme())
                && first.getHost() != null
                && first.getHost().equalsIgnoreCase(second.getHost())
                && effectivePort(first) == effectivePort(second);
    }

    private int effectivePort(URI uri) {
        if (uri.getPort() >= 0) {
            return uri.getPort();
        }
        return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }

    private String normalizeBasePath(String path) {
        if (path == null || path.isBlank() || "/".equals(path)) {
            return "/";
        }
        return path.endsWith("/") ? path : path + "/";
    }
    private String normalizeRole(String role) {
        if (role == null) {
            return null;
        }
        return role.toLowerCase(java.util.Locale.ENGLISH);
    }
}
