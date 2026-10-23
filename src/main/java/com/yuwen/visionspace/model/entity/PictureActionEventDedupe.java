package com.yuwen.visionspace.model.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 行为事件的幂等台账。
 *
 * <p>事件载荷（{@link UserPictureAction}）按保留期清理，清理之后载荷里就没有
 * 事件幂等键了。台账把「这个事件已经被受理过」这条依据单独保存下来：
 * 载荷删掉，台账还在，同一个事件重放仍然会被挡住，不会二次入账。
 *
 * <p>台账只增不减：它是可靠去重依据的一部分，不随载荷保留期一起清理。
 */
@Data
public class PictureActionEventDedupe {

    /**
     * 事件幂等键：与事件载荷上的 eventId 相同
     */
    private String eventId;

    /**
     * 首次受理该事件的时间
     */
    private LocalDateTime createdTime;
}
