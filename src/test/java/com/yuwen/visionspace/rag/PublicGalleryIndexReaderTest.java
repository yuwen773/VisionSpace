package com.yuwen.visionspace.rag;

import com.yuwen.visionspace.agent.model.ImageResource;
import com.yuwen.visionspace.mapper.PictureMapper;
import com.yuwen.visionspace.model.entity.Picture;
import com.yuwen.visionspace.model.enums.PictureReviewStatusEnum;
import com.yuwen.visionspace.service.ResourceIsolationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

/**
 * 检索命中的复核：失效的旧向量不得再把图片交出去。
 *
 * <p>向量库只提供候选。图片被撤审、删除或隔离之后，旧文档还留在库里，检索也会
 * 照常召回它——复核就是拦住这一条。
 */
@ExtendWith(MockitoExtension.class)
class PublicGalleryIndexReaderTest {

    @Mock
    private PictureMapper pictureMapper;

    @Mock
    private ResourceIsolationService resourceIsolationService;

    @InjectMocks
    private PublicGalleryIndexReader reader;

    @Test
    void liveApprovedPictureIsReturnedWithTheAddressFromDatabase() {
        givenPictures(approved("蓝色背景", "https://example.com/current.png"));
        givenQuarantined(Set.of());

        List<ImageResource> results = reader.present(List.of(document(7L, "https://example.com/stale.png")));

        assertEquals(1, results.size());
        // 地址取自数据库当前记录，不是向量文档里的旧快照
        assertEquals("https://example.com/current.png", results.get(0).getUrl());
        assertEquals("蓝色渐变背景", results.get(0).getDescription());
    }

    @Test
    void rejectedPictureIsDroppedEvenThoughItsVectorStillExists() {
        Picture rejected = approved("蓝色背景", "https://example.com/current.png");
        rejected.setReviewStatus(PictureReviewStatusEnum.REJECT.getValue());
        givenPictures(rejected);
        givenQuarantined(Set.of());

        List<ImageResource> results = reader.present(List.of(document(7L, "https://example.com/stale.png")));

        assertTrue(results.isEmpty(), "审核被撤销的图片不得再被交出去");
    }

    @Test
    void deletedPictureIsDropped() {
        // 逻辑删除后查不出来
        givenPictures();
        givenQuarantined(Set.of());

        List<ImageResource> results = reader.present(List.of(document(7L, "https://example.com/stale.png")));

        assertTrue(results.isEmpty(), "已删除的图片不得再被交出去");
    }

    @Test
    void quarantinedPictureIsDropped() {
        givenPictures(approved("蓝色背景", "https://example.com/current.png"));
        givenQuarantined(Set.of(7L));

        List<ImageResource> results = reader.present(List.of(document(7L, "https://example.com/stale.png")));

        assertTrue(results.isEmpty(), "隔离中的图片不得再被交出去");
    }

    @Test
    void spacePictureIsDropped() {
        Picture spacePicture = approved("蓝色背景", "https://example.com/current.png");
        spacePicture.setSpaceId(42L);
        givenPictures(spacePicture);
        givenQuarantined(Set.of());

        List<ImageResource> results = reader.present(List.of(document(7L, "https://example.com/stale.png")));

        assertTrue(results.isEmpty(), "空间图片不属于公开图库");
    }

    @Test
    void documentWithoutPictureIdIsDropped() {
        // 无法定位到图片时直接丢弃，不再查库
        List<ImageResource> results = reader.present(List.of(
                new Document("not-a-picture", "无法定位", Map.of())));

        assertTrue(results.isEmpty(), "无法定位到图片的文档不得进入结果");
    }

    @Test
    void pictureIdIsReadFromMetadataEvenWhenItIsNotALong() {
        Picture picture = approved("蓝色背景", "https://example.com/current.png");
        givenPictures(picture);
        givenQuarantined(Set.of());

        List<ImageResource> results = reader.present(List.of(
                new Document("picture_7", "蓝色渐变背景", Map.of(PictureIndex.META_PICTURE_ID, "7"))));

        assertEquals(1, results.size(), "元数据里的图片 id 是字符串时也要能定位");
    }

    @Test
    void pictureIdFallsBackToTheDocumentId() {
        Picture picture = approved("蓝色背景", "https://example.com/current.png");
        givenPictures(picture);
        givenQuarantined(Set.of());

        List<ImageResource> results = reader.present(List.of(
                new Document("picture_7", "蓝色渐变背景", Map.of())));

        assertEquals(1, results.size(), "元数据缺失时按文档标识定位");
    }

    private void givenPictures(Picture... pictures) {
        doReturn(List.of(pictures)).when(pictureMapper).selectBatchIds(anyCollection());
    }

    private void givenQuarantined(Set<Long> quarantined) {
        when(resourceIsolationService.quarantinedIds(anyString(), anyCollection())).thenReturn(quarantined);
    }

    private Picture approved(String name, String url) {
        Picture picture = new Picture();
        picture.setId(7L);
        picture.setName(name);
        picture.setUrl(url);
        picture.setSpaceId(null);
        picture.setReviewStatus(PictureReviewStatusEnum.PASS.getValue());
        return picture;
    }

    private Document document(Long pictureId, String imageUrl) {
        return new Document(PictureIndex.docId(pictureId), "蓝色渐变背景",
                Map.of(PictureIndex.META_PICTURE_ID, pictureId, PictureIndex.META_IMAGE_URL, imageUrl));
    }
}
