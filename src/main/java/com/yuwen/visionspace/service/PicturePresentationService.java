package com.yuwen.visionspace.service;

import com.yuwen.visionspace.model.entity.Picture;
import com.yuwen.visionspace.model.vo.PictureVO;

import java.util.List;

/**
 * 「一行图片」对外呈现的唯一组装点。
 *
 * <p>ADR-0003 要求：内容地址只走受控入口，隔离中的图片不给地址。此前列表与详情
 * 各自实现了一遍（内容入口、隔离遮蔽、所有者、统计），而按颜色检索与最近图片
 * 两条路径只填了内容入口、漏了隔离遮蔽——同一条被隔离的记录，在不同页面上会得到
 * 不同结论。
 */
public interface PicturePresentationService {

    /** 单条呈现：内容入口 + 隔离遮蔽 + 所有者 + 统计。 */
    PictureVO present(Picture picture);

    /** 批量呈现：一次查询完成隔离判定、内容入口、所有者与统计。 */
    List<PictureVO> present(List<Picture> pictures);
}
