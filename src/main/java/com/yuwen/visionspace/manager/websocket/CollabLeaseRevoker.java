package com.yuwen.visionspace.manager.websocket;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.yuwen.visionspace.manager.websocket.lease.PictureEditLeaseManager;
import com.yuwen.visionspace.model.entity.Picture;
import com.yuwen.visionspace.service.PictureService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.util.List;

/**
 * 协作租约主动收回器。
 *
 * <p>成员被降权或移出空间时，按空间查出其可能持有租约的图片并逐个收回。
 * 收回条件做双重归属验证（租约 value 同时记录了 userId），
 * 不会误伤其他用户持有的租约。跨实例生效：租约状态在 Redis，
 * 管理操作落在任何一个实例都会让所有实例上的旧持有者失效。
 */
@Component
@Slf4j
public class CollabLeaseRevoker {

    private static final String KEY_PREFIX = "picture:edit:lease:";

    @Resource
    private PictureService pictureService;

    @Resource
    private PictureEditLeaseManager leaseManager;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 收回某空间某成员当前持有的全部编辑租约。
     */
    public void revokeSpaceUserLeases(long spaceId, long userId) {
        List<Long> pictureIds = pictureService.listObjs(new QueryWrapper<Picture>()
                        .select("id").eq("spaceId", spaceId).eq("isDelete", 0),
                id -> (Long) id);
        for (Long pictureId : pictureIds) {
            String key = KEY_PREFIX + pictureId;
            String value = stringRedisTemplate.opsForValue().get(key);
            if (value == null) {
                continue;
            }
            int sep = value.indexOf('|');
            if (sep < 0 || !value.substring(sep + 1).equals(String.valueOf(userId))) {
                // 不是该成员持有的租约，不动
                continue;
            }
            String sessionId = value.substring(0, sep);
            leaseManager.forceRevoke(pictureId, sessionId);
            log.info("成员权限变更，收回编辑租约: pictureId={}, userId={}", pictureId, userId);
        }
    }
}
