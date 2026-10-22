package com.yuwen.visionspace.acceptance;

import com.yuwen.visionspace.exception.BusinessException;
import com.yuwen.visionspace.model.dto.picture.PictureQueryRequest;
import com.yuwen.visionspace.model.dto.space.SpaceQueryRequest;
import com.yuwen.visionspace.model.dto.user.UserQueryRequest;
import com.yuwen.visionspace.service.PictureService;
import com.yuwen.visionspace.service.SpaceService;
import com.yuwen.visionspace.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 排序键白名单的真实 Mapper 验证：合法键按预期排序，非法输入在进入 SQL 之前被拒绝。
 *
 * <p>断言的是数据库返回的最终顺序，而不是清洗函数的返回值。
 */
@IsolatedSpringBootTest
class SortKeyWhitelistIntegrationTest {

    private static final long SPACE_ID = 87201;

    @Autowired
    private PictureService pictureService;

    @Autowired
    private SpaceService spaceService;

    @Autowired
    private UserService userService;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void fixture() {
        jdbc.update("DELETE FROM picture WHERE id BETWEEN 87210 AND 87213");
        jdbc.update("DELETE FROM space WHERE id = ?", SPACE_ID);
        jdbc.update("INSERT INTO space (id, spaceName, userId, totalSize, totalCount) VALUES (?, 'sort space', 87202, 5, 5)", SPACE_ID);
        // 名称顺序与体积顺序相反，便于区分实际生效的排序键
        insertPicture(87210, "alpha", 300);
        insertPicture(87211, "bravo", 200);
        insertPicture(87212, "charlie", 100);
        insertPicture(87213, "delta", 400);
    }

    @Test
    void allowedKeySortsByTheMappedColumn() {
        var byName = pictureService.list(pictureService.getQueryWrapper(query("name", "ascend", null)));
        assertEquals(List.of("alpha", "bravo", "charlie", "delta"),
                byName.stream().map(p -> p.getName()).toList());

        var bySizeDesc = pictureService.list(pictureService.getQueryWrapper(query("picSize", "descend", null)));
        assertEquals(List.of(400L, 300L, 200L, 100L),
                bySizeDesc.stream().map(p -> p.getPicSize()).toList());
    }

    @Test
    void missingDirectionDefaultsToDescendingAndUnknownDirectionIsRejected() {
        var defaulted = pictureService.list(pictureService.getQueryWrapper(query("picSize", null, null)));
        assertEquals(400L, defaulted.get(0).getPicSize(), "missing order must default to descending");

        // 未知方向按明确业务错误拒绝，不静默改成别的顺序
        var error = assertThrows(BusinessException.class,
                () -> pictureService.list(pictureService.getQueryWrapper(query("picSize", "sideways", null))));
        assertEquals(40000, error.getCode());
    }

    @Test
    void unknownKeyIsRejectedBeforeAnySql() {
        var request = query("createTime; DROP TABLE picture; --", "ascend", null);
        var error = assertThrows(BusinessException.class, () -> pictureService.list(pictureService.getQueryWrapper(request)));
        assertEquals(40000, error.getCode());
        // 错误信息不回显客户端输入
        assertFalse(error.getMessage().contains("DROP"), error.getMessage());
        // 表仍然存在且数据完整
        assertEquals(4, jdbc.queryForObject(
                "SELECT COUNT(*) FROM picture WHERE id BETWEEN 87210 AND 87213", Integer.class));
    }

    @Test
    void internalColumnsAreNotSortableKeys() {
        for (String internal : List.of("isDelete", "userPassword", "reviewStatus", "spaceId")) {
            assertThrows(BusinessException.class,
                    () -> pictureService.list(pictureService.getQueryWrapper(query(internal, "ascend", null))), internal);
        }
    }

    @Test
    void spaceAndUserQueriesUseTheSameRule() {
        var spaceRequest = new SpaceQueryRequest();
        spaceRequest.setSortField("isDelete");
        assertThrows(BusinessException.class, () -> spaceService.list(spaceService.getQueryWrapper(spaceRequest)));

        var userRequest = new UserQueryRequest();
        userRequest.setSortField("userPassword");
        assertThrows(BusinessException.class, () -> userService.list(userService.getQueryWrapper(userRequest)));

        var okSpace = new SpaceQueryRequest();
        okSpace.setSortField("createTime");
        okSpace.setSortOrder("ascend");
        assertTrue(spaceService.list(spaceService.getQueryWrapper(okSpace)).stream().anyMatch(s -> s.getId().equals(SPACE_ID)));
    }

    private void insertPicture(long id, String name, long size) {
        jdbc.update("""
                INSERT INTO picture (id, url, name, userId, picSize, spaceId, reviewStatus)
                VALUES (?, ?, ?, 87202, ?, NULL, 1)
                """, id, "sort-" + id, name, size);
    }

    private PictureQueryRequest query(String sortField, String sortOrder, Long spaceId) {
        var request = new PictureQueryRequest();
        request.setSortField(sortField);
        request.setSortOrder(sortOrder);
        request.setSpaceId(spaceId);
        request.setUserId(87202L);
        return request;
    }
}
