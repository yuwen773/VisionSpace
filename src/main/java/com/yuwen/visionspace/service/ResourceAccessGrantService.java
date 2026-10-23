package com.yuwen.visionspace.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.yuwen.visionspace.model.entity.Picture;
import com.yuwen.visionspace.model.entity.ResourceAccessGrant;
import com.yuwen.visionspace.model.entity.User;
import com.yuwen.visionspace.model.vo.ResourceGrantVO;

import java.time.Duration;
import java.util.List;

/**
 * 单资源限时只读授权。
 *
 * <p>个人资源（个人空间与公共图库）由所有者授权，团队资源由团队管理员授权；
 * 授权绑定指定查看者与指定资源，必须设置到期时间，可随时撤销。
 * 平台管理员身份不构成授权主体，也不能据此读取私人内容。
 */
public interface ResourceAccessGrantService extends IService<ResourceAccessGrant> {

    /** 单次授权最长有效期的上限，避免"事实上的无限期授权"。 */
    Duration MAX_VALIDITY = Duration.ofDays(30);

    /**
     * 被授权人当前是否有读取该图片的有效授权。每次调用都按数据库当前状态判定。
     */
    boolean hasActiveGrant(Long pictureId, Long viewerUserId);

    /**
     * 授予限时只读访问。
     *
     * @param picture   目标图片
     * @param grantor   授权主体（由服务端会话得出）
     * @param granteeId 被授权用户 id
     * @param expiresAt 到期时间，必填且必须在有效期内
     * @param reason    授权理由，可为空
     * @return 新建的授权
     */
    ResourceAccessGrant grant(Picture picture, User grantor, Long granteeId,
                              java.util.Date expiresAt, String reason);

    /**
     * 撤销授权。只有当前有效的授权主体（个人资源所有者或该团队空间的团队管理员）可撤销。
     */
    ResourceAccessGrant revoke(Long grantId, User operator, String reason);

    /**
     * 列出某资源的授权记录（含已撤销与已到期），按创建时间倒序。
     * 仅资源所有者、该团队空间的团队管理员或平台管理员（只读审计）可见。
     */
    List<ResourceGrantVO> listByPicture(Picture picture, User viewer);

    /**
     * 列出指定查看者的有效授权，供"我的授权"视图。
     */
    List<ResourceGrantVO> listActiveByGrantee(Long granteeUserId);

    /**
     * 判断用户是否可以对该资源授权：个人资源为所有者，团队资源为团队管理员。
     */
    boolean canGrant(Picture picture, User user);

    /**
     * 把已到期的授权落库为 EXPIRED 并释放唯一键。
     *
     * <p>到期是状态迁移而不是展示状态：唯一键不释放，同一资源与被授权人就无法再次授权。
     *
     * @return 本次迁移为过期状态的授权条数
     */
    int expireOutdatedGrants();
}
