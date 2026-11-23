package com.yuwen.visionspace.rag;

import com.yuwen.visionspace.agent.tools.InternalImageSearchTool;
import com.yuwen.visionspace.mapper.PictureMapper;
import com.yuwen.visionspace.model.entity.Picture;
import com.yuwen.visionspace.model.enums.PictureReviewStatusEnum;
import com.yuwen.visionspace.service.ResourceIsolationService;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 索引写入与检索读取跨过同一条契约：用内存 VectorStore 往返一次。
 *
 * <p>这组测试的价值不在「搜索能返回结果」，而在写入方的元数据键与读取方的过滤
 * 条件必须对得上：任何一侧改名，这里会先变红，而不是线上检索悄悄返回空。
 *
 * <p>内存 VectorStore 按文档标识覆盖，与 Chroma 的写入语义一致；读取侧还要经过
 * 数据库复核，索引里残留的旧文档不会直接变成结果。
 */
class PictureIndexRoundTripTest {

    @Test
    void galleryDocumentSurvivesTheRoundTripWithItsContractKeys() {
        InMemoryVectorStore store = new InMemoryVectorStore();
        Picture gallery = picture(11L, null, 7L);
        Document written = PictureIndex.document(gallery, "蓝色渐变背景 科技感");
        store.add(List.of(written));

        assertEquals("picture_11", written.getId(), "文档标识由契约生成");
        assertEquals(0L, written.getMetadata().get(PictureIndex.META_SPACE_ID),
                "公开图片在元数据里记为图库空间");
        assertEquals("https://example.com/11.png",
                written.getMetadata().get(PictureIndex.META_IMAGE_URL));

        List<com.yuwen.visionspace.agent.model.ImageResource> results =
                search(store, "蓝色", List.of(gallery));

        assertEquals(1, results.size(), "公开图库图片应能被检索到");
        assertEquals("https://example.com/11.png", results.get(0).getUrl());
    }

    @Test
    void privateSpaceDocumentsNeverReachTheSearchTool() {
        InMemoryVectorStore store = new InMemoryVectorStore();
        store.add(List.of(
                PictureIndex.document(picture(11L, null, 7L), "公开图库 蓝色"),
                PictureIndex.document(picture(12L, 42L, 7L), "私有空间 蓝色")));

        List<com.yuwen.visionspace.agent.model.ImageResource> results =
                search(store, "蓝色", List.of(picture(11L, null, 7L)));

        assertEquals(1, results.size(), "私有空间内容没有索引途径，检索也不应返回它");
        assertEquals("https://example.com/11.png", results.get(0).getUrl());
    }

    @Test
    void searchAlwaysCarriesTheGalleryFilter() {
        InMemoryVectorStore store = new InMemoryVectorStore();

        search(store, "任意", List.of());

        assertEquals(1, store.searches.size());
        assertEquals(PictureIndex.publicGalleryFilter(),
                store.searches.get(0).getFilterExpression());
    }

    @Test
    void staleDocumentOfARevokedPictureIsDroppedByTheRecheck() {
        InMemoryVectorStore store = new InMemoryVectorStore();
        Picture revoked = picture(11L, null, 7L);
        store.add(List.of(PictureIndex.document(revoked, "蓝色渐变背景 科技感")));

        // 索引里还留着旧文档，但数据库里这张图片已经被拒绝
        Picture rejected = picture(11L, null, 7L);
        rejected.setReviewStatus(PictureReviewStatusEnum.REJECT.getValue());

        List<com.yuwen.visionspace.agent.model.ImageResource> results =
                search(store, "蓝色", List.of(rejected));

        assertEquals(0, results.size(), "审核被撤销的图片不该因为索引里还有旧文档就被交出去");
    }

    private List<com.yuwen.visionspace.agent.model.ImageResource> search(
            InMemoryVectorStore store, String query, List<Picture> livePictures) {
        InternalImageSearchTool tool = new InternalImageSearchTool();
        ReflectionTestUtils.setField(tool, "vectorStore", store);
        ReflectionTestUtils.setField(tool, "reader", reader(livePictures));
        return tool.searchInternalImages(query, 5);
    }

    /**
     * 复核用的读侧：只把给定的图片当作当前有效的记录。
     */
    private PublicGalleryIndexReader reader(List<Picture> livePictures) {
        PictureMapper pictureMapper = mock(PictureMapper.class);
        ResourceIsolationService isolation = mock(ResourceIsolationService.class);
        doAnswer(invocation -> {
            Collection<?> ids = invocation.getArgument(0);
            List<Picture> found = new ArrayList<>();
            for (Picture candidate : livePictures) {
                if (ids.contains(candidate.getId())) {
                    found.add(candidate);
                }
            }
            return found;
        }).when(pictureMapper).selectBatchIds(anyCollection());
        when(isolation.quarantinedIds(anyString(), anyCollection())).thenReturn(Set.of());
        PublicGalleryIndexReader reader = new PublicGalleryIndexReader();
        ReflectionTestUtils.setField(reader, "pictureMapper", pictureMapper);
        ReflectionTestUtils.setField(reader, "resourceIsolationService", isolation);
        return reader;
    }

    private Picture picture(Long id, Long spaceId, Long userId) {
        Picture picture = new Picture();
        picture.setId(id);
        picture.setSpaceId(spaceId);
        picture.setUserId(userId);
        picture.setUrl("https://example.com/" + id + ".png");
        picture.setName("图片" + id);
        picture.setReviewStatus(PictureReviewStatusEnum.PASS.getValue());
        return picture;
    }

    /** 只实现测试需要的部分：按 spaceId 等值条件过滤，写入按文档标识覆盖。 */
    private static final class InMemoryVectorStore implements VectorStore {

        private final Map<String, Document> documents = new LinkedHashMap<>();
        private final List<SearchRequest> searches = new ArrayList<>();

        @Override
        public void add(List<Document> docs) {
            docs.forEach(doc -> documents.put(doc.getId(), doc));
        }

        @Override
        public void delete(List<String> idList) {
            idList.forEach(documents::remove);
        }

        @Override
        public void delete(Filter.Expression filterExpression) {
            throw new UnsupportedOperationException("测试不需要按条件删除");
        }

        @Override
        public List<Document> similaritySearch(SearchRequest request) {
            searches.add(request);
            Filter.Expression filter = request.getFilterExpression();
            if (filter == null) {
                return new ArrayList<>(documents.values());
            }
            assertTrue(filter.type() == Filter.ExpressionType.EQ,
                    "检索条件只应是最简单的等值过滤");
            Filter.Key key = (Filter.Key) filter.left();
            Filter.Value value = (Filter.Value) filter.right();
            return documents.values().stream()
                    .filter(doc -> matches(doc.getMetadata().get(key.key()), value.value()))
                    .toList();
        }

        private boolean matches(Object stored, Object expected) {
            if (stored == null || expected == null) {
                return false;
            }
            return stored.toString().equals(expected.toString());
        }
    }
}
