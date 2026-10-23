package com.yuwen.visionspace.agent.service;

import com.yuwen.visionspace.exception.BusinessException;
import com.yuwen.visionspace.exception.ErrorCode;
import com.yuwen.visionspace.exception.ThrowUtils;
import com.yuwen.visionspace.manager.storage.PictureStorageService;
import com.yuwen.visionspace.mapper.AgentSessionAttachmentMapper;
import com.yuwen.visionspace.mapper.AgentSessionMapper;
import com.yuwen.visionspace.model.entity.PictureFile;
import com.yuwen.visionspace.model.enums.PictureFilePurposeEnum;
import com.yuwen.visionspace.model.enums.PictureFileStatusEnum;
import com.yuwen.visionspace.service.PictureFileService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.Resource;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Agent 会话附件的生命周期。
 *
 * <p>临时上传的图片在绑定到会话之前是「未绑定」状态：它可以被回收任务清理。
 * 绑定之后图片保留到会话或图片被明确删除；SSE 成功、异常、取消与断开都不删除
 * 已绑定的图片。
 *
 * <p>「是否已绑定」只看 {@code agent_session_attachment}，不看资源表上的状态位：
 * 绑定与回收并发时双方都先拿资源行锁再复核，判定与写入之间不留窗口，锁由数据库
 * 提供，跨应用实例有效。
 */
@Slf4j
@Service
public class AgentAttachmentService {

    /** 未绑定临时图片的保留期：二十四小时。 */
    public static final Duration TEMP_RETENTION = Duration.ofHours(24);

    /** 单批回收与重试的上限，避免一次任务持有过多行锁。 */
    public static final int DEFAULT_BATCH_SIZE = 200;

    /** 删除重试的初始退避时间。 */
    private static final Duration INITIAL_RETRY_DELAY = Duration.ofMinutes(5);

    /** 删除重试的退避上限。 */
    private static final Duration MAX_RETRY_DELAY = Duration.ofHours(24);

    @Resource
    private PictureFileService pictureFileService;

    @Resource
    private PictureStorageService pictureStorageService;

    @Resource
    private AgentSessionAttachmentMapper attachmentMapper;

    @Resource
    private AgentSessionMapper sessionMapper;

    @Resource
    private AgentSessionGuard sessionGuard;

    /**
     * 绑定后的附件引用。
     *
     * @param resourceId 资源标识
     * @param url        服务端记录的可信地址，只交给模型与工具，不交给浏览器
     * @param fileSize   字节数
     */
    public record BoundAttachment(Long resourceId, String url, Long fileSize) {
    }

    // ==================== 绑定 ====================

    /**
     * 解析客户端提交的附件，判定归属。
     *
     * <p>只接受本人登记的 Agent 会话资源：未登记、属于他人或已删除的一律拒绝，
     * 不区分三者，避免用错误信息探测他人资源。
     *
     * <p>旧客户端提交的是地址而不是资源标识：地址必须与本人已有记录精确匹配，
     * 不做前缀或域名匹配。
     *
     * <p>资源标识按十进制字符串接收：它是雪花值，超出 JavaScript 的安全整数
     * 范围，客户端转成 number 会丢精度，落到别的资源上。
     *
     * @return 资源标识列表（去重，保持提交顺序）
     */
    public List<Long> resolveOwnedIds(List<String> resourceIds, List<String> imageUrls, Long userId) {
        ThrowUtils.throwIf(userId == null, ErrorCode.NOT_LOGIN_ERROR);
        Set<Long> resolved = new LinkedHashSet<>();
        if (resourceIds != null) {
            for (String resourceId : resourceIds) {
                Long id = parseResourceId(resourceId);
                PictureFile file = pictureFileService.getById(id);
                assertUsableAgentResource(file, userId);
                resolved.add(id);
            }
        }
        if (imageUrls != null) {
            for (String url : imageUrls) {
                ThrowUtils.throwIf(url == null || url.isBlank(), ErrorCode.PARAMS_ERROR, "附件地址不能为空");
                PictureFile file = pictureFileService.findByExactUrl(url.trim());
                assertUsableAgentResource(file, userId);
                resolved.add(file.getId());
            }
        }
        return new ArrayList<>(resolved);
    }

    /**
     * 把十进制字符串的资源标识解析成标识。
     *
     * <p>解析失败一律按参数不合法拒绝，不做部分解析：附件标识不合法时
     * 整批请求都不该继续。
     */
    private Long parseResourceId(String resourceId) {
        ThrowUtils.throwIf(resourceId == null || resourceId.isBlank(), ErrorCode.PARAMS_ERROR, "附件标识不合法");
        Long id;
        try {
            id = Long.valueOf(resourceId.trim());
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "附件标识不合法");
        }
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR, "附件标识不合法");
        return id;
    }

    /**
     * 把附件绑定到会话。
     *
     * <p>同一会话重复绑定同一资源是幂等的：不会产生第二条绑定，也不会把已解除的
     * 绑定重新变成有效——明确删除过的图片不会因为再次提交同一个资源标识而复活。
     *
     * @return 绑定成功后的附件引用，交给模型与工具使用
     */
    @Transactional
    public List<BoundAttachment> bind(String sessionId, Long userId, List<Long> resourceIds) {
        if (resourceIds == null || resourceIds.isEmpty()) {
            return List.of();
        }
        // 临时资源只能绑定到本人有权的会话：归属判定写在这里，
        // 任何直接调用方也不能把附件挂到别人的会话上
        sessionGuard.requireOwned(sessionId, userId);
        List<BoundAttachment> bound = new ArrayList<>();
        for (Long resourceId : resourceIds) {
            // 先拿资源行锁：绑定与回收此时互斥，判定与写入之间不留窗口
            PictureFile file = pictureFileService.lockById(resourceId);
            assertUsableAgentResource(file, userId);
            attachmentMapper.insertIfAbsent(sessionId, resourceId, userId, 0);
            // 绑定后不再是「无人引用的临时资源」，回收任务不会把它当成候选
            pictureFileService.markActive(resourceId);
            bound.add(new BoundAttachment(resourceId, file.getUrl(), file.getFileSize()));
        }
        return bound;
    }

    // ==================== 读取 ====================

    /**
     * 附件内容。
     *
     * @param bytes      附件字节内容
     * @param contentType 内容类型
     */
    public record AttachmentContent(byte[] bytes, String contentType) {
    }

    /**
     * 读取附件内容。
     *
     * <p>会话与资源双重授权：绑定必须存在于指定会话且处于有效状态，会话必须属于
     * 当前用户，资源必须属于当前用户且未被删除。
     *
     * @param resourceId 资源标识
     * @param sessionId  会话标识
     * @param userId     当前登录用户
     * @return 附件内容
     */
    public AttachmentContent readContent(Long resourceId, String sessionId, Long userId) {
        ThrowUtils.throwIf(resourceId == null || resourceId <= 0, ErrorCode.PARAMS_ERROR, "附件标识不合法");
        ThrowUtils.throwIf(userId == null, ErrorCode.NOT_LOGIN_ERROR);
        // 会话归属只认服务端登录身份
        sessionGuard.requireOwned(sessionId, userId);
        ThrowUtils.throwIf(attachmentMapper.countActive(sessionId, resourceId) == 0,
                ErrorCode.NO_AUTH_ERROR, "无权读取该附件");
        PictureFile file = pictureFileService.getById(resourceId);
        assertUsableAgentResource(file, userId);
        byte[] bytes = pictureStorageService.readObject(file.getPlatform(), file.getObjectKey());
        return new AttachmentContent(bytes, PictureStorageService.contentTypeOf(file.getObjectKey()));
    }

    // ==================== 删除 ====================

    /**
     * 丢弃尚未绑定的临时上传。
     *
     * <p>已经绑定到会话的资源不在这里删除：绑定的图片保留到会话或图片被明确
     * 删除，前端的失败清理不会把已经进入会话的图片删掉。
     *
     * @return 是否执行了删除
     */
    @Transactional
    public boolean discardUnboundUpload(Long resourceId, Long userId) {
        ThrowUtils.throwIf(userId == null, ErrorCode.NOT_LOGIN_ERROR);
        ThrowUtils.throwIf(resourceId == null || resourceId <= 0, ErrorCode.PARAMS_ERROR, "资源标识不合法");
        PictureFile file = pictureFileService.lockById(resourceId);
        assertOwnedAgentResource(file, userId);
        if (file.getStatus() == PictureFileStatusEnum.DELETED.getValue()) {
            // 已经删除：重复删除按成功处理，不产生额外副作用
            return false;
        }
        if (attachmentMapper.countActiveByResource(resourceId) > 0) {
            // 已绑定：明确删除请走附件删除入口，这里不动它
            return false;
        }
        return removeObject(file);
    }

    /**
     * 明确删除图片。
     *
     * <p>先停止访问（解除全部有效绑定），再清理对象；存储删除未确认时保留可重试
     * 状态，由清理任务重试，不静默丢弃。
     *
     * @return 是否确认删除了对象
     */
    @Transactional
    public boolean deleteImage(Long resourceId, Long userId) {
        ThrowUtils.throwIf(userId == null, ErrorCode.NOT_LOGIN_ERROR);
        ThrowUtils.throwIf(resourceId == null || resourceId <= 0, ErrorCode.PARAMS_ERROR, "资源标识不合法");
        PictureFile file = pictureFileService.lockById(resourceId);
        assertOwnedAgentResource(file, userId);
        if (file.getStatus() == PictureFileStatusEnum.DELETED.getValue()) {
            // 已经删除：重复删除按成功处理
            return false;
        }
        // 先停访问：绑定解除后任何会话都读不到这张图
        attachmentMapper.releaseByResource(resourceId);
        return removeObject(file);
    }

    /**
     * 明确删除会话。
     *
     * <p>会话被删除后历史与附件都不再可访问；会话引用但没有其他引用者的图片随后
     * 被清理，被别的会话共享的图片保留。
     *
     * @return 受影响的资源数量
     */
    @Transactional
    public int deleteSession(String sessionId, Long userId) {
        sessionGuard.requireOwned(sessionId, userId);
        List<Long> affected = new ArrayList<>();
        for (var attachment : attachmentMapper.selectActiveBySession(sessionId)) {
            affected.add(attachment.getResourceId());
        }
        // 先停访问：会话软删除 + 绑定解除
        sessionMapper.markDeleted(sessionId, userId);
        attachmentMapper.releaseBySession(sessionId);
        // 再清理没有其他有效引用的对象
        for (Long resourceId : affected) {
            cleanupIfUnreferenced(resourceId);
        }
        return affected.size();
    }

    // ==================== 回收与重试 ====================

    /**
     * 回收候选列表。是否真的可回收由逐条回收时锁定复核决定。
     *
     * <p>保留期以小时数交给数据库计算：{@code createTime} 是数据库写的，
     * 截止点必须用同一个时钟，否则时区或时钟漂移会让过期判定整体偏移。
     */
    public List<PictureFile> listReclaimCandidates(int limit) {
        return pictureFileService.listReclaimCandidates((int) TEMP_RETENTION.toHours(), limit);
    }

    /**
     * 回收一条未绑定的临时图片。
     *
     * <p>锁定资源行后复核绑定状态：绑定与回收此时互斥，绑定先提交则本次跳过，
     * 回收先提交则绑定在行锁之后看到资源已删除并拒绝。
     *
     * @return 是否回收了这条资源
     */
    @Transactional
    public boolean reclaimOne(Long resourceId) {
        PictureFile file = pictureFileService.lockById(resourceId);
        if (file == null || file.getStatus() != PictureFileStatusEnum.TEMP.getValue()) {
            return false;
        }
        if (attachmentMapper.countActiveByResource(resourceId) > 0) {
            // 已经被绑定：保留
            return false;
        }
        removeObject(file);
        return true;
    }

    /**
     * 重试一次未确认的删除。
     *
     * @return 是否这次确认删除
     */
    @Transactional
    public boolean retryDeleteOne(Long resourceId) {
        PictureFile file = pictureFileService.lockById(resourceId);
        if (file == null || file.getStatus() == PictureFileStatusEnum.DELETED.getValue()) {
            return false;
        }
        // 删除请求之后资源又被绑定：新的有效引用优先，不删对象也不解除绑定
        if (attachmentMapper.countActiveByResource(resourceId) > 0) {
            return false;
        }
        return removeObject(file);
    }

    /**
     * 待重试删除的资源列表。
     *
     * <p>到期判定在数据库时钟内完成：重试时间也是数据库写的。
     */
    public List<PictureFile> listPendingDeletes(int limit) {
        return pictureFileService.listPendingDeletes(limit);
    }

    // ==================== 内部 ====================

    /**
     * 没有其他有效引用时才清理对象。
     */
    private void cleanupIfUnreferenced(Long resourceId) {
        PictureFile file = pictureFileService.lockById(resourceId);
        if (file == null || file.getStatus() == PictureFileStatusEnum.DELETED.getValue()) {
            return;
        }
        if (attachmentMapper.countActiveByResource(resourceId) > 0) {
            // 别的会话还在引用：保留对象
            return;
        }
        removeObject(file);
    }

    /**
     * 删除对象并记录最终状态。
     *
     * <p>删除未确认时保留记录与可重试状态：既不谎报成功，也不静默丢弃。
     */
    private boolean removeObject(PictureFile file) {
        boolean removed = pictureStorageService.deleteObject(file.getPlatform(), file.getObjectKey());
        if (!removed) {
            int retryCount = (file.getDeleteRetryCount() == null ? 0 : file.getDeleteRetryCount()) + 1;
            // 下次重试时间由数据库时钟推算，与应用侧时钟无关
            pictureFileService.markDeletePending(file.getId(), retryCount, retryDelay(retryCount).toSeconds());
            log.warn("附件对象删除未确认，保留重试状态: id={}, retry={}", file.getId(), retryCount);
            return false;
        }
        pictureFileService.markDeleteConfirmed(file.getId(),
                file.getDeleteRetryCount() == null ? 0 : file.getDeleteRetryCount());
        return true;
    }

    private Duration retryDelay(int retryCount) {
        Duration delay = INITIAL_RETRY_DELAY;
        for (int i = 1; i < retryCount && delay.compareTo(MAX_RETRY_DELAY) < 0; i++) {
            delay = delay.multipliedBy(2);
        }
        return delay.compareTo(MAX_RETRY_DELAY) > 0 ? MAX_RETRY_DELAY : delay;
    }

    /**
     * 附件资源必须属于当前用户且用途是 Agent 会话。
     *
     * <p>不区分「不存在」与「不属于你」：两者都返回同一个拒绝原因，
     * 避免用错误信息探测他人的资源。是否已删除由调用方按各自的语义判定。
     */
    private void assertOwnedAgentResource(PictureFile file, Long userId) {
        if (file == null
                || !userId.equals(file.getOwnerUserId())
                || !PictureFilePurposeEnum.AGENT_CHAT.getValue().equals(file.getPurpose())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "附件不存在或无权访问");
        }
    }

    /**
     * 附件资源必须存在、属于当前用户、用途是 Agent 会话且未被删除。
     */
    private void assertUsableAgentResource(PictureFile file, Long userId) {
        assertOwnedAgentResource(file, userId);
        if (file.getStatus() == PictureFileStatusEnum.DELETED.getValue()) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "附件不存在或无权访问");
        }
    }
}
