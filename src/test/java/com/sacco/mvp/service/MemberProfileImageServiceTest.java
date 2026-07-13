package com.sacco.mvp.service;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MemberProfileImageServiceTest {

    @Test
    void storesNormalizedProfileJpeg() throws Exception {
        StoredUploadStorageService storageService = mock(StoredUploadStorageService.class);
        MemberProfileImageService service = new MemberProfileImageService(storageService);
        UUID memberId = UUID.randomUUID();

        service.store(memberId, imageFile("photo.png", "image/png", 900, 900));

        org.mockito.ArgumentCaptor<byte[]> contentCaptor = org.mockito.ArgumentCaptor.forClass(byte[].class);
        verify(storageService).replaceCategory(
            eq(StoredUploadStorageService.OWNER_MEMBER),
            eq(memberId.toString()),
            eq(StoredUploadStorageService.CATEGORY_MEMBER_PROFILE_PHOTO),
            eq("photo.png"),
            eq("image/jpeg"),
            contentCaptor.capture()
        );
        BufferedImage normalized = ImageIO.read(new ByteArrayInputStream(contentCaptor.getValue()));
        assertThat(normalized.getWidth()).isEqualTo(600);
        assertThat(normalized.getHeight()).isEqualTo(600);
    }

    @Test
    void cropsWideProfileImageToSquare() throws Exception {
        StoredUploadStorageService storageService = mock(StoredUploadStorageService.class);
        MemberProfileImageService service = new MemberProfileImageService(storageService);

        service.store(UUID.randomUUID(), imageFile("wide.png", "image/png", 900, 300));

        org.mockito.ArgumentCaptor<byte[]> contentCaptor = org.mockito.ArgumentCaptor.forClass(byte[].class);
        verify(storageService).replaceCategory(
            eq(StoredUploadStorageService.OWNER_MEMBER),
            any(),
            eq(StoredUploadStorageService.CATEGORY_MEMBER_PROFILE_PHOTO),
            eq("wide.png"),
            eq("image/jpeg"),
            contentCaptor.capture()
        );
        BufferedImage normalized = ImageIO.read(new ByteArrayInputStream(contentCaptor.getValue()));
        assertThat(normalized.getWidth()).isEqualTo(600);
        assertThat(normalized.getHeight()).isEqualTo(600);
    }

    @Test
    void rejectsOversizedProfileImageDimensions() throws Exception {
        StoredUploadStorageService storageService = mock(StoredUploadStorageService.class);
        MemberProfileImageService service = new MemberProfileImageService(storageService);

        assertThatThrownBy(() -> service.store(UUID.randomUUID(), imageFile("huge.png", "image/png", 3001, 3000)))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("3000x3000");
    }

    @Test
    void rejectsUnsupportedProfileImageTypes() {
        StoredUploadStorageService storageService = mock(StoredUploadStorageService.class);
        MemberProfileImageService service = new MemberProfileImageService(storageService);

        MockMultipartFile file = new MockMultipartFile(
            "profileImage",
            "photo.gif",
            "image/gif",
            new byte[] {1, 2, 3}
        );

        assertThatThrownBy(() -> service.store(UUID.randomUUID(), file))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("PNG or JPEG");
    }

    @Test
    void reportsWhetherProfileImageExists() {
        StoredUploadStorageService storageService = mock(StoredUploadStorageService.class);
        MemberProfileImageService service = new MemberProfileImageService(storageService);
        UUID memberId = UUID.randomUUID();
        when(storageService.exists(
            StoredUploadStorageService.OWNER_MEMBER,
            memberId.toString(),
            StoredUploadStorageService.CATEGORY_MEMBER_PROFILE_PHOTO
        )).thenReturn(true);

        assertThat(service.hasImage(memberId)).isTrue();
    }

    @Test
    void deletesProfileImageCategory() {
        StoredUploadStorageService storageService = mock(StoredUploadStorageService.class);
        MemberProfileImageService service = new MemberProfileImageService(storageService);
        UUID memberId = UUID.randomUUID();

        service.delete(memberId);

        verify(storageService).deleteCategory(
            StoredUploadStorageService.OWNER_MEMBER,
            memberId.toString(),
            StoredUploadStorageService.CATEGORY_MEMBER_PROFILE_PHOTO
        );
    }

    private MockMultipartFile imageFile(String name, String contentType, int width, int height) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, width, height);
            graphics.setColor(Color.BLUE);
            graphics.fillRect(width / 4, height / 4, width / 2, height / 2);
        } finally {
            graphics.dispose();
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return new MockMultipartFile("profileImage", name, contentType, output.toByteArray());
    }
}
