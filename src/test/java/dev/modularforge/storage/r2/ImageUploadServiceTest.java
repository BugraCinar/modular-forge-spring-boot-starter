package dev.modularforge.storage.r2;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class ImageUploadServiceTest {

    @Mock S3Client s3Client;
    @Mock CloudflareR2Config config;

    private ImageUploadService service;

    @BeforeEach
    void setUp() {
        service = new ImageUploadService(s3Client, config);
        lenient().when(config.getBucketName()).thenReturn("profiles-bucket");
        lenient().when(config.getPublicDomain()).thenReturn("https://cdn.example.com");
    }

    @ParameterizedTest
    @MethodSource("validImages")
    void uploadsEverySupportedImageType(String filename, byte[] content, String mimeType) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", filename, "ignored", content);

        String url = service.uploadProfileImage(file, "USER", 42L);

        ArgumentCaptor<PutObjectRequest> request = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(request.capture(), any(RequestBody.class));
        assertThat(request.getValue().bucket()).isEqualTo("profiles-bucket");
        assertThat(request.getValue().key()).startsWith("profiles/user/profile_user_42_");
        assertThat(request.getValue().contentType()).isEqualTo(mimeType);
        assertThat(url).startsWith("https://cdn.example.com/profiles/user/profile_user_42_");
    }

    static Stream<Arguments> validImages() {
        return Stream.of(
                Arguments.of("photo.png", bytes(0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 0, 0, 0, 0), "image/png"),
                Arguments.of("photo.jpg", bytes(0xff, 0xd8, 0xff, 0, 0, 0, 0, 0, 0, 0, 0, 0), "image/jpeg"),
                Arguments.of("photo.gif", "GIF87a......".getBytes(), "image/gif"),
                Arguments.of("photo.gif", "GIF89a......".getBytes(), "image/gif"),
                Arguments.of("photo.bmp", bytes(0x42, 0x4d, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0), "image/bmp"),
                Arguments.of("photo.webp", bytes(0x52, 0x49, 0x46, 0x46, 0, 0, 0, 0, 0x57, 0x45, 0x42, 0x50), "image/webp")
        );
    }

    @Test
    void acceptsNullFilenameAndNullRole() throws Exception {
        MultipartFile file = org.mockito.Mockito.mock(MultipartFile.class);
        byte[] jpeg = bytes(0xff, 0xd8, 0xff, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        when(file.isEmpty()).thenReturn(false);
        when(file.getSize()).thenReturn((long) jpeg.length);
        when(file.getOriginalFilename()).thenReturn(null);
        when(file.getBytes()).thenReturn(jpeg);
        when(file.getInputStream()).thenAnswer(call -> new ByteArrayInputStream(jpeg));

        String url = service.uploadProfileImage(file, null, 7L);

        assertThat(url).contains("profiles/null/profile_7_");
    }

    @Test
    void rejectsMissingEmptyOversizedAndInvalidFiles() throws Exception {
        assertThatThrownBy(() -> service.uploadProfileImage(null, "USER", 1L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.uploadProfileImage(
                new MockMultipartFile("file", "empty.png", "image/png", new byte[0]), "USER", 1L))
                .isInstanceOf(IllegalArgumentException.class);

        MultipartFile oversized = org.mockito.Mockito.mock(MultipartFile.class);
        when(oversized.getSize()).thenReturn(5L * 1024 * 1024 + 1);
        assertThatThrownBy(() -> service.uploadProfileImage(oversized, "USER", 1L))
                .isInstanceOf(IllegalArgumentException.class);

        assertRejected(new MockMultipartFile("file", "shell.php", "image/jpeg",
                bytes(0xff, 0xd8, 0xff)));
        assertRejected(new MockMultipartFile("file", "photo", "image/jpeg", new byte[]{1}));
        assertRejected(new MockMultipartFile("file", "photo.jpg", "image/jpeg", new byte[12]));

        MultipartFile unreadable = org.mockito.Mockito.mock(MultipartFile.class);
        when(unreadable.getOriginalFilename()).thenReturn("photo.jpg");
        when(unreadable.getBytes()).thenThrow(new IOException("cannot read"));
        assertThatThrownBy(() -> service.uploadProfileImage(unreadable, "USER", 1L))
                .isInstanceOf(IllegalArgumentException.class);

        MultipartFile namelessInvalid = org.mockito.Mockito.mock(MultipartFile.class);
        when(namelessInvalid.getOriginalFilename()).thenReturn(null);
        when(namelessInvalid.getBytes()).thenReturn(new byte[]{1});
        assertRejected(namelessInvalid);
    }

    @Test
    void fallsBackWhenTheSecondTypeReadChangesAndWrapsS3Failures() throws Exception {
        MultipartFile changing = org.mockito.Mockito.mock(MultipartFile.class);
        byte[] jpeg = bytes(0xff, 0xd8, 0xff, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        when(changing.isEmpty()).thenReturn(false);
        when(changing.getSize()).thenReturn((long) jpeg.length);
        when(changing.getOriginalFilename()).thenReturn("photo.jpg");
        when(changing.getBytes()).thenReturn(jpeg, new byte[12]);
        when(changing.getInputStream()).thenReturn(new ByteArrayInputStream(jpeg));

        service.uploadProfileImage(changing, "ADMIN", 2L);

        ArgumentCaptor<PutObjectRequest> request = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(request.capture(), any(RequestBody.class));
        assertThat(request.getValue().contentType()).isEqualTo("application/octet-stream");

        org.mockito.Mockito.reset(s3Client);
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenThrow(S3Exception.builder().message("R2 down").build());
        assertThatThrownBy(() -> service.uploadProfileImage(
                new MockMultipartFile("file", "photo.jpg", "image/jpeg", jpeg), "USER", 1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("uploaded");
    }

    @Test
    void deletesOnlyObjectsOwnedByTheConfiguredOrigin() {
        service.deleteImage(null);
        service.deleteImage("");
        verify(s3Client, never()).deleteObject(any(DeleteObjectRequest.class));

        service.delete("https://cdn.example.com/profiles/user/photo.jpg");
        ArgumentCaptor<DeleteObjectRequest> request = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3Client).deleteObject(request.capture());
        assertThat(request.getValue().key()).isEqualTo("profiles/user/photo.jpg");

        for (String invalid : new String[]{
                "https://evil.example/profiles/user/photo.jpg",
                "https://cdn.example.com/profiles/user/photo.jpg?x=1",
                "https://cdn.example.com/profiles/user/photo.jpg#x",
                "https://cdn.example.com/outside/photo.jpg",
                "https://cdn.example.com/profiles/root/photo.jpg",
                "https://cdn.example.com:444/profiles/user/photo.jpg",
                "not a url"
        }) {
            assertThatThrownBy(() -> service.deleteImage(invalid))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("deleted");
        }
    }

    @Test
    void handlesConfiguredBasePathsExplicitPortsAndDeletionFailures() {
        when(config.getPublicDomain()).thenReturn("https://cdn.example.com:443/assets");
        service.deleteImage("https://cdn.example.com/assets/profiles/admin/photo.png");
        assertThatThrownBy(() -> service.deleteImage("https://cdn.example.com:443/asset-store/profiles/admin/photo.png"))
                .isInstanceOf(IllegalStateException.class);

        when(config.getPublicDomain()).thenReturn("http://cdn.example.com/");
        service.deleteImage("http://cdn.example.com/profiles/user/photo.png");

        org.mockito.Mockito.doThrow(new RuntimeException("delete failed"))
                .when(s3Client).deleteObject(any(DeleteObjectRequest.class));
        assertThatThrownBy(() -> service.deleteImage("http://cdn.example.com/profiles/user/photo.png"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void replacesOldImagesAndCanKeepTheCurrentImage() throws Exception {
        byte[] jpeg = bytes(0xff, 0xd8, 0xff, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", jpeg);
        String oldUrl = "https://cdn.example.com/profiles/user/old.jpg";

        assertThat(service.updateProfileImage(file, "USER", 1L, oldUrl)).contains("profile_user_1_");
        verify(s3Client).deleteObject(any(DeleteObjectRequest.class));

        service.updateProfileImage(file, "USER", 1L, "");
        service.updateProfileImage(file, "USER", 1L, null);
    }

    @Test
    void privateByteAndFilenameHelpersHandleBoundaryInputs() {
        assertThat((boolean) ReflectionTestUtils.invokeMethod(service, "startsWith", new byte[1], new byte[2])).isFalse();
        assertThat((boolean) ReflectionTestUtils.invokeMethod(service, "regionMatches", new byte[2], 2, new byte[2])).isFalse();
        assertThat((String) ReflectionTestUtils.invokeMethod(service, "getFileExtension", "photo")).isEmpty();
        assertThat((String) ReflectionTestUtils.invokeMethod(service, "getFileExtension", "photo.")).isEmpty();
        assertThat((String) ReflectionTestUtils.invokeMethod(service, "normalizeBasePath", (Object) null)).isEqualTo("/");
        assertThat((String) ReflectionTestUtils.invokeMethod(service, "normalizeBasePath", " ")).isEqualTo("/");
        assertThat((String) ReflectionTestUtils.invokeMethod(service, "normalizeBasePath", "/assets/")).isEqualTo("/assets/");
    }

    @Test
    void typeDetectionExercisesEveryHeaderLengthAndPartialWebpSignature() {
        for (byte[] content : new byte[][]{
                bytes(1, 2),
                bytes(1, 2, 3),
                bytes(1, 2, 3, 4, 5, 6),
                bytes(1, 2, 3, 4, 5, 6, 7, 8),
                bytes(0x52, 0x49, 0x46, 0x46, 0, 0, 0, 0, 1, 2, 3, 4)
        }) {
            MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", content);
            assertThat(ReflectionTestUtils.<String>invokeMethod(service, "detectFileType", file)).isNull();
        }

        MockMultipartFile validWithoutExtension = new MockMultipartFile("file", "photo", "image/jpeg",
                bytes(0xff, 0xd8, 0xff));
        assertThat(ReflectionTestUtils.<String>invokeMethod(service, "generateFileName",
                validWithoutExtension, "profile", "user", 1L)).doesNotEndWith(".jpg");
    }

    @Test
    void originComparisonRejectsMissingAndDifferentSchemesOrHosts() throws Exception {
        assertThat(ReflectionTestUtils.<Boolean>invokeMethod(service, "sameOrigin",
                new URI("/assets"), new URI("/assets"))).isFalse();
        assertThat(ReflectionTestUtils.<Boolean>invokeMethod(service, "sameOrigin",
                new URI("https://cdn.example.com"), new URI("http://cdn.example.com"))).isFalse();
        assertThat(ReflectionTestUtils.<Boolean>invokeMethod(service, "sameOrigin",
                new URI("https:/assets"), new URI("https:/assets"))).isFalse();
    }

    @Test
    void rejectsFilesShorterThanAValidMagicHeader() {
        assertRejected(new MockMultipartFile("file", "photo.jpg", "image/jpeg", new byte[]{1}));
    }

    private void assertRejected(MultipartFile file) {
        assertThatThrownBy(() -> service.uploadProfileImage(file, "USER", 1L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static byte[] bytes(int... values) {
        byte[] result = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            result[i] = (byte) values[i];
        }
        return result;
    }
}
