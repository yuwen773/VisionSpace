package com.yuwen.visionspace.rag;

import com.yuwen.visionspace.agent.tools.InternalImageSearchTool;
import com.yuwen.visionspace.model.entity.Picture;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 索引写入与检索读取跨过同一条契约：用内存 VectorStore 往返一次。
 *
 * <p>这组测试的价值不在「搜索能返回结果」，而在写入方的元数据键与读取方的过滤
 * 条件必须对得上：任何一侧改名，这里会先变红，而不是线上检索悄悄返回空。
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

        List<com.yuwen.visionspace.agent.model.ImageResource> results = search(store, "蓝色");

        assertEquals(1, results.size(), "公开图库图片应能被检索到");
        assertEquals("https://example.com/11.png", results.get(0).getUrl());
    }

    @Test
    void privateSpaceDocumentsNeverReachTheSearchTool() {
        InMemoryVectorStore store = new InMemoryVectorStore();
        store.add(List.of(
                PictureIndex.document(picture(11L, null, 7L), "公开图库 蓝色"),
                PictureIndex.document(picture(12L, 42L, 7L), "私有空间 蓝色")));

        List<com.yuwen.visionspace.agent.model.ImageResource> results = search(store, "蓝色");

        assertEquals(1, results.size(), "私有空间内容没有索引途径，检索也不应返回它");
        assertEquals("https://example.com/11.png", results.get(0).getUrl());
    }

    @Test
    void searchAlwaysCarriesTheGalleryFilter() {
        InMemoryVectorStore store = new InMemoryVectorStore();

        search(store, "任意");

        assertEquals(1, store.searches.size());
        assertEquals(PictureIndex.publicGalleryFilter(),
                store.searches.get(0).getFilterExpression());
    }

    private List<com.yuwen.visionspace.agent.model.ImageResource> search(
            InMemoryVectorStore store, String query) {
        InternalImageSearchTool tool = new InternalImageSearchTool();
        ReflectionTestUtils.setField(tool, "vectorStore", store);
        return tool.searchInternalImages(query, 5);
    }

    private Picture picture(Long id, Long spaceId, Long userId) {
        Picture picture = new Picture();
        picture.setId(id);
        picture.setSpaceId(spaceId);
        picture.setUserId(userId);
        picture.setUrl("https://example.com/" + id + ".png");
        picture.setName("图片" + id);
        return picture;
    }

    /** 只实现测试需要的部分：按 spaceId 等值条件过滤。 */
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
