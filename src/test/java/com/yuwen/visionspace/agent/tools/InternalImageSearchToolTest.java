package com.yuwen.visionspace.agent.tools;

import com.yuwen.visionspace.agent.model.ImageCategoryEnum;
import com.yuwen.visionspace.agent.model.ImageResource;
import com.yuwen.visionspace.rag.PictureIndex;
import com.yuwen.visionspace.rag.PublicGalleryIndexReader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 站内检索的可见范围只由索引契约决定，模型参数无法扩大它。
 *
 * <p>向量命中之后必须复核：复核逻辑在 {@link PublicGalleryIndexReader} 里，
 * 这里只断言工具把候选交给复核，并把复核结果原样返回。
 */
@ExtendWith(MockitoExtension.class)
class InternalImageSearchToolTest {

    @Mock
    private VectorStore vectorStore;

    @Mock
    private PublicGalleryIndexReader reader;

    @InjectMocks
    private InternalImageSearchTool tool;

    @Test
    void everySearchIsScopedToThePublicGallery() {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

        tool.searchInternalImages("科技感蓝色背景图", null);

        ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(vectorStore).similaritySearch(captor.capture());
        SearchRequest request = captor.getValue();

        assertEquals(PictureIndex.publicGalleryFilter(), request.getFilterExpression(),
                "查询必须带上公开图库过滤，模型无法去掉它");
        assertEquals(8, request.getTopK(), "未指定时默认返回 8 条");
    }

    @Test
    void topKIsClampedToTheDocumentedMaximum() {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

        tool.searchInternalImages("温暖的自然风光", 50);

        ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(vectorStore).similaritySearch(captor.capture());
        assertEquals(8, captor.getValue().getTopK(), "超过上限的 topK 回落到默认值");
    }

    @Test
    void vectorCandidatesAreRecheckedBeforeTheyAreReturned() {
        Document stale = new Document("picture_1", "蓝色渐变背景",
                Map.of("imageUrl", "https://example.com/a.png"));
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(stale));
        ImageResource verified = ImageResource.builder()
                .category(ImageCategoryEnum.CONTENT)
                .url("https://example.com/a.png")
                .description("蓝色渐变背景")
                .source("internal")
                .build();
        when(reader.present(List.of(stale))).thenReturn(List.of(verified));

        List<ImageResource> results = tool.searchInternalImages("蓝色", 3);

        verify(reader).present(List.of(stale));
        assertEquals(1, results.size());
        assertEquals("https://example.com/a.png", results.get(0).getUrl());
        assertEquals("internal", results.get(0).getSource());
    }

    @Test
    void candidatesDroppedByTheRecheckAreNotReturned() {
        Document stale = new Document("picture_1", "蓝色渐变背景",
                Map.of("imageUrl", "https://example.com/a.png"));
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(stale));
        when(reader.present(List.of(stale))).thenReturn(List.of());

        assertEquals(0, tool.searchInternalImages("蓝色", 3).size(),
                "复核不通过的候选不得进入结果");
    }
}
