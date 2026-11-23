package com.yuwen.visionspace.rag;

import com.yuwen.visionspace.agent.model.ImageCategoryEnum;
import com.yuwen.visionspace.agent.model.ImageResource;
import com.yuwen.visionspace.mapper.PictureMapper;
import com.yuwen.visionspace.model.entity.Picture;
import com.yuwen.visionspace.model.enums.ResourceTypeEnum;
import com.yuwen.visionspace.service.ResourceIsolationService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 站内检索命中结果的复核。
 *
 * <p>向量库只是候选来源，不是访问授权的依据：审核被撤销、图片被删除或被隔离之后，
 * 旧向量文档仍可能留在库里，检索也会照常召回它。因此命中之后必须回到数据库复核
 * 当前状态，失效的命中直接丢弃，不把它的地址与描述交出去。
 *
 * <p>复核按批进行：候选图片一次查出来，隔离状态一次查出来，逐条判定都在内存里做。
 * 逐张查库会让一次检索放大成 N 次查询，站内有几千张图时这条路径最先撑不住。
 *
 * <p>返回的内容地址取自数据库当前记录，而不是向量文档里的元数据：向量文档是
 * 写入那一刻的快照，图片替换过内容之后它就是旧地址。
 */
@Slf4j
@Component
public class PublicGalleryIndexReader {

    @Resource
    private PictureMapper pictureMapper;

    @Resource
    private ResourceIsolationService resourceIsolationService;

    /**
     * 复核一批向量命中，返回当前仍然成立的公开图库图片。
     *
     * @param documents 向量库返回的候选文档
     * @return 仍然可以对外展示的图片资源
     */
    public List<ImageResource> present(List<Document> documents) {
        if (documents == null || documents.isEmpty()) {
            return List.of();
        }
        Map<Long, Document> candidates = new LinkedHashMap<>();
        for (Document document : documents) {
            Long pictureId = pictureId(document);
            if (pictureId != null) {
                candidates.put(pictureId, document);
            }
        }
        if (candidates.isEmpty()) {
            return List.of();
        }

        List<Picture> current = pictureMapper.selectBatchIds(candidates.keySet());
        Set<Long> quarantined = resourceIsolationService.quarantinedIds(
                ResourceTypeEnum.PICTURE.getValue(), candidates.keySet());

        Map<Long, Picture> byId = new LinkedHashMap<>();
        for (Picture picture : current) {
            byId.put(picture.getId(), picture);
        }

        List<ImageResource> results = new ArrayList<>();
        for (Map.Entry<Long, Document> candidate : candidates.entrySet()) {
            Picture picture = byId.get(candidate.getKey());
            if (picture == null) {
                // 图片已被删除：逻辑删除后查不出来，旧向量不得再对外输出
                continue;
            }
            if (quarantined.contains(picture.getId())) {
                continue;
            }
            if (PictureIndexState.decide(picture, false) != PictureIndexState.Operation.UPSERT) {
                // 审核已撤销或图片已不属于公开图库
                continue;
            }
            if (picture.getUrl() == null || picture.getUrl().isBlank()) {
                // 没有内容地址的图片无法展示，不占检索结果的位置
                continue;
            }
            results.add(ImageResource.builder()
                    .category(ImageCategoryEnum.CONTENT)
                    .url(picture.getUrl())
                    .description(candidate.getValue().getText())
                    .source("internal")
                    .build());
        }
        return results;
    }

    /**
     * 从向量文档里取图片 id。
     *
     * <p>元数据里的数值经 Chroma 往返后可能是 {@code Integer} 而不是 {@code Long}，
     * 因此按 {@link Number} 解析；元数据缺失时回退到文档标识里的后缀。
     */
    private Long pictureId(Document document) {
        if (document == null) {
            return null;
        }
        Object raw = document.getMetadata() == null ? null : document.getMetadata().get(PictureIndex.META_PICTURE_ID);
        if (raw instanceof Number number) {
            return number.longValue();
        }
        if (raw instanceof String text && !text.isBlank()) {
            try {
                return Long.parseLong(text.trim());
            } catch (NumberFormatException ignored) {
                // 落到文档标识解析
            }
        }
        String id = document.getId();
        if (id == null || !id.startsWith(PictureIndex.DOC_ID_PREFIX)) {
            return null;
        }
        try {
            return Long.parseLong(id.substring(PictureIndex.DOC_ID_PREFIX.length()));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
