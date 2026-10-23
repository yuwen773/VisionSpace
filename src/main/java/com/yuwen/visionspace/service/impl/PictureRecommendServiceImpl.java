package com.yuwen.visionspace.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.yuwen.visionspace.config.ReorderConfig;
import com.yuwen.visionspace.manager.cache.RecommendCacheManager;
import com.yuwen.visionspace.manager.cache.RecommendCandidateList;
import com.yuwen.visionspace.mapper.PictureMapper;
import com.yuwen.visionspace.model.entity.Picture;
import com.yuwen.visionspace.model.entity.PictureStats;
import com.yuwen.visionspace.model.vo.RecommendPageVO;
import com.yuwen.visionspace.model.vo.RecommendPictureVO;
import com.yuwen.visionspace.service.PictureContentService;
import com.yuwen.visionspace.service.PictureRecommendService;
import com.yuwen.visionspace.service.PictureStatsService;
import com.yuwen.visionspace.utils.HomeRecommendScoreCalculator;
import com.yuwen.visionspace.utils.PictureReorderUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 推荐服务实现。
 *
 * <p>分页规则：
 * <ul>
 *   <li>候选有界：热门与随机先取有界候选池，评分只在候选池内进行；</li>
 *   <li>页切分正确：热门与随机按候选池的有序结果做内存切片，最新与优质用数据库分页；</li>
 *   <li>总数真实：来自实际可翻到的数量，不是写死的常量；越界页只返回空记录，
 *       不把非空集合的总数抹成 0；</li>
 *   <li>分页上下文稳定：同一上下文里的候选顺序固定，翻页不重复、不遗漏；</li>
 *   <li>上下文失效时明确结束：候选被刷新或到期后，旧上下文不会被悄悄绑定到新的
 *       排序上，响应会给出新的候选与 contextValid=false，客户端从新的一份重新开始；</li>
 *   <li>不按分页键缓存全量结果：候选列表按「类型 + 上下文」缓存一份，翻页只切片。</li>
 * </ul>
 */
@Service
public class PictureRecommendServiceImpl implements PictureRecommendService {

    /** 单页最大条数 */
    private static final int MAX_PAGE_SIZE = 100;

    @Resource
    private PictureMapper pictureMapper;

    @Resource
    private HomeRecommendScoreCalculator scoreCalculator;

    @Resource
    private RecommendCacheManager cacheManager;

    @Resource
    private ReorderConfig reorderConfig;

    @Resource
    private PictureStatsService pictureStatsService;

    @Resource
    private PictureContentService pictureContentService;

    /** 候选池上限：候选获取与评分都保持有界 */
    @Value("${vision-space.recommend.candidate-limit:300}")
    private int candidateLimit;

    /** 热门候选池里按互动量预选的名额占比，其余名额留给新内容 */
    @Value("${vision-space.recommend.engagement-candidate-ratio:0.7}")
    private double engagementCandidateRatio;

    /** 优质推荐的像素门槛 */
    @Value("${vision-space.recommend.quality-min-pixels:2000000}")
    private long qualityMinPixels;

    @Override
    public List<Long> getRecommendPictureIds(String type, int page, int size) {
        return getRecommendPictureIds(type, page, size, null);
    }

    @Override
    public List<Long> getRecommendPictureIds(String type, int page, int size, String context) {
        return resolvePage(type, page, size, context).pictureIds();
    }

    @Override
    public RecommendPageVO getRecommendPictures(String type, int page, int size) {
        return getRecommendPictures(type, page, size, null);
    }

    @Override
    public RecommendPageVO getRecommendPictures(String type, int page, int size, String context) {
        PageSlice slice = resolvePage(type, page, size, context);
        // 越界页只有空记录，总数仍然是真实数量
        List<RecommendPictureVO> records = loadRecords(slice.pictureIds());
        return new RecommendPageVO(records, slice.total(), slice.context(), slice.contextValid());
    }

    @Override
    public void refreshRecommendCache() {
        cacheManager.invalidateRecommendCache();
    }

    /**
     * 计算一页的切片结果。
     */
    private PageSlice resolvePage(String type, int page, int size, String context) {
        int safePage = Math.max(1, page);
        int safeSize = Math.min(Math.max(1, size), MAX_PAGE_SIZE);
        if (isDatabasePaged(type)) {
            List<Long> ids = databasePagedIds(type, safePage, safeSize);
            return new PageSlice(ids, countRecommendPictures(type), context, true);
        }
        CandidateResolution resolution = candidateList(type, context);
        RecommendCandidateList candidates = resolution.candidates();
        if (resolution.contextValid()) {
            return new PageSlice(slice(candidates.getPictureIds(), safePage, safeSize),
                    candidates.getTotal(), candidates.getContext(), true);
        }
        // 上下文已经失效：这份候选被刷新或到期，原来的页码不再对应任何候选。
        // 明确结束这份上下文，把当前快照的第一页交回去，客户端从新的一份结果重新开始，
        // 而不是把旧上下文悄悄绑定到新的排序上。
        return new PageSlice(slice(candidates.getPictureIds(), 1, safeSize),
                candidates.getTotal(), candidates.getContext(), false);
    }

    /**
     * 最新与优质走数据库分页：每页只取一页数据，候选天然有界。
     */
    private boolean isDatabasePaged(String type) {
        return "latest".equals(type) || "quality".equals(type);
    }

    private List<Long> databasePagedIds(String type, int page, int size) {
        QueryWrapper<Picture> queryWrapper = new QueryWrapper<>();
        // 公开推荐只包含公共图库：空间图片不进入匿名可见的推荐流
        queryWrapper.lambda().eq(Picture::getReviewStatus, 1).isNull(Picture::getSpaceId);
        if ("quality".equals(type)) {
            queryWrapper.apply("picWidth * picHeight > " + qualityMinPixels);
            queryWrapper.orderByDesc("picWidth", "picHeight");
        } else {
            queryWrapper.orderByDesc("createTime");
        }
        long offset = (long) (page - 1) * size;
        queryWrapper.last("LIMIT " + offset + ", " + size);
        return pictureMapper.selectList(queryWrapper).stream().map(Picture::getId).collect(Collectors.toList());
    }

    /**
     * 真实总数：来自实际可翻到的数量。
     */
    private long countRecommendPictures(String type) {
        Long cached = cacheManager.getCachedTotal(type);
        if (cached != null) {
            return cached;
        }
        long total = "quality".equals(type)
                ? pictureMapper.countQualityCandidates(qualityMinPixels)
                : pictureMapper.countPublicCandidates();
        cacheManager.setCachedTotal(type, total);
        return total;
    }

    /**
     * 有界候选列表：热门共用一份默认槽位的候选，随机按上下文分槽。
     *
     * <p>上下文与候选快照绑定：候选的顺序依赖统计快照，重建之后顺序可能变化，
     * 因此客户端带来的上下文必须与当前快照一致。不一致说明这份上下文已经失效
     * （候选被刷新或到期），此时明确结束上下文，而不是把旧上下文悄悄绑定到
     * 新的排序上让客户端从旧页码继续。
     *
     * <p>缓存分槽规则：候选顺序依赖上下文的类型（随机）按上下文分槽，
     * 顺序与上下文无关的类型（热门）固定用默认槽位，候选份数因此保持有界。
     */
    private CandidateResolution candidateList(String type, String context) {
        String requested = (context == null || context.isBlank()) ? null : context.trim();
        boolean contextBound = orderDependsOnContext(type);
        // 随机的槽位就是种子上下文；热门固定用默认槽位
        String slot = contextBound ? (requested != null ? requested : newContextId()) : null;
        RecommendCandidateList cached = cacheManager.getCandidateList(type, slot);
        if (cached != null) {
            // 随机：缓存键就是上下文，命中即同一份顺序；
            // 热门：命中的快照必须与客户端带来的上下文一致，否则这份上下文已经失效
            boolean valid = contextBound || requested == null || requested.equals(cached.getContext());
            return new CandidateResolution(cached, valid);
        }
        // 未命中：随机的顺序由种子决定，按同一种子重建仍是同一份；
        // 热门的顺序依赖统计快照，重建就是新的一份快照，旧上下文到此结束。
        String identity = contextBound ? slot : newContextId();
        RecommendCandidateList built = buildCandidateList(type, identity);
        cacheManager.setCandidateList(type, slot, built);
        boolean valid = contextBound || requested == null || requested.equals(built.getContext());
        return new CandidateResolution(built, valid);
    }

    /**
     * 新的上下文标识：热门快照的身份由服务端给出，不接受客户端指定。
     */
    private String newContextId() {
        return UUID.randomUUID().toString();
    }

    /**
     * 候选顺序是否依赖分页上下文。
     *
     * <p>随机的顺序由上下文决定，同一个上下文必须给出同一个顺序；
     * 热门按互动量与热度排序，与上下文无关。
     */
    private boolean orderDependsOnContext(String type) {
        return "random".equals(type);
    }

    private RecommendCandidateList buildCandidateList(String type, String resolvedContext) {
        List<Long> ids = switch (type) {
            case "hot" -> hotCandidateIds();
            // 随机：同一个上下文对同一份数据给出同一个顺序
            case "random" -> pictureMapper.selectRandomCandidateIds(resolvedContext, candidateLimit);
            default -> Collections.emptyList();
        };
        return new RecommendCandidateList(resolvedContext, ids, ids.size());
    }

    /**
     * 热门候选：先按互动量预选，再补入最新图片，保证新内容仍有机会进入候选池。
     * 候选池有界，评分只在候选池内进行。
     */
    private List<Long> hotCandidateIds() {
        int limit = Math.max(1, candidateLimit);
        int engagementQuota = Math.max(1, (int) Math.round(limit * engagementCandidateRatio));
        LinkedHashSet<Long> pool = new LinkedHashSet<>(pictureMapper.selectEngagementCandidateIds(engagementQuota));
        int remaining = limit - pool.size();
        if (remaining > 0) {
            for (Long id : pictureMapper.selectRecentCandidateIds(limit)) {
                if (pool.size() >= limit) {
                    break;
                }
                pool.add(id);
            }
        }
        if (pool.isEmpty()) {
            return Collections.emptyList();
        }
        List<Picture> pictures = pictureMapper.selectBatchIds(pool);
        pictures = sortByScore(pictures);
        pictures = reorderPictures(pictures);
        return pictures.stream().map(Picture::getId).collect(Collectors.toList());
    }

    /**
     * 从有序候选里切出当前页。
     *
     * <p>偏移用 long 计算：页码是外部输入，可以很大，int 乘法会溢出成负数，
     * 把越界页变成一次越界访问而不是一个空结果。
     */
    private List<Long> slice(List<Long> ids, int page, int size) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        long offset = (long) (page - 1) * (long) size;
        if (offset < 0 || offset >= ids.size()) {
            return Collections.emptyList();
        }
        int from = (int) offset;
        int to = (int) Math.min((long) ids.size(), offset + size);
        if (from >= to) {
            return Collections.emptyList();
        }
        return new ArrayList<>(ids.subList(from, to));
    }

    /**
     * 按 id 顺序加载推荐记录，图片内容一律走受控入口。
     */
    private List<RecommendPictureVO> loadRecords(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        QueryWrapper<Picture> queryWrapper = new QueryWrapper<>();
        queryWrapper.lambda().in(Picture::getId, ids);
        List<Picture> pictures = pictureMapper.selectList(queryWrapper);

        // 按ID顺序排序
        Map<Long, Integer> orderMap = new java.util.HashMap<>();
        for (int i = 0; i < ids.size(); i++) {
            orderMap.put(ids.get(i), i);
        }
        pictures.sort(java.util.Comparator.comparingInt(p -> orderMap.getOrDefault(p.getId(), Integer.MAX_VALUE)));

        List<RecommendPictureVO> records = pictures.stream()
                .map(RecommendPictureVO::objToVo)
                .collect(Collectors.toList());
        // 推荐列表同样只返回受控内容入口
        for (int i = 0; i < records.size(); i++) {
            records.get(i).setThumbnailEndpoint(
                    pictureContentService.contentUrl(pictures.get(i), PictureContentService.ContentKind.THUMBNAIL));
        }
        return records;
    }

    private List<Picture> reorderPictures(List<Picture> pictures) {
        pictures = PictureReorderUtils.reorderByAuthor(
                pictures,
                reorderConfig.getAuthorWindowSize(),
                reorderConfig.getAuthorMaxCount()
        );
        return PictureReorderUtils.reorderByCategory(
                pictures,
                reorderConfig.getCategoryMaxConsecutive()
        );
    }

    private List<Picture> sortByScore(List<Picture> pictures) {
        if (pictures == null || pictures.isEmpty()) {
            return pictures;
        }

        // 批量获取统计数据（按 pictureId 关联键，不能用统计主键）
        List<Long> pictureIds = pictures.stream().map(Picture::getId).collect(Collectors.toList());
        Map<Long, PictureStats> statsMap = pictureStatsService.mapByPictureIds(pictureIds);

        // 按评分排序
        pictures.sort((a, b) -> {
            PictureStats statsA = statsMap.get(a.getId());
            PictureStats statsB = statsMap.get(b.getId());
            return Double.compare(scoreCalculator.calculateScore(b, statsB), scoreCalculator.calculateScore(a, statsA));
        });

        return pictures;
    }

    /**
     * 一页的切片结果。
     *
     * @param pictureIds   当前页的图片 id
     * @param total        真实总数
     * @param context      实际使用的分页上下文
     * @param contextValid 客户端带来的上下文是否仍然对应当前这份候选
     */
    private record PageSlice(List<Long> pictureIds, long total, String context, boolean contextValid) {
    }

    /**
     * 候选列表与上下文是否仍然有效的判定结果。
     *
     * @param candidates   当前这份候选
     * @param contextValid 客户端带来的上下文是否仍然对应这份候选
     */
    private record CandidateResolution(RecommendCandidateList candidates, boolean contextValid) {
    }
}
