package com.yuwen.visionspace.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yuwen.visionspace.model.entity.VipRedemptionCode;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Date;

/**
 * 兑换码台账数据库操作
 */
@Mapper
public interface VipRedemptionCodeMapper extends BaseMapper<VipRedemptionCode> {

    /**
     * 单语句原子核销：只有仍是可用状态的码会被标记已用。
     * 返回影响行数：1 表示本实例抢到，0 表示码不存在、已用或已作废。
     */
    int markUsed(@Param("codeHash") String codeHash, @Param("userId") Long userId, @Param("usedTime") Date usedTime);

    /** 按摘要取记录 id。 */
    Long selectIdByHash(@Param("codeHash") String codeHash);
}
