package com.yuwen.visionspace.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yuwen.visionspace.model.entity.FeedbackAttachment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 反馈附件记录的数据访问。
 */
@Mapper
public interface FeedbackAttachmentMapper extends BaseMapper<FeedbackAttachment> {

    /**
     * 锁定附件行：SELECT ... FOR UPDATE。
     *
     * <p>与图片资源用同一套行锁顺序，跨实例串行化对同一资源的处置与改动。
     *
     * @param id 附件标识
     * @return 附件存在时返回其标识，不存在时返回 null
     */
    @Select("SELECT `id` FROM `feedback_attachment` WHERE `id` = #{id} FOR UPDATE")
    Long selectIdForUpdate(@Param("id") Long id);
}
