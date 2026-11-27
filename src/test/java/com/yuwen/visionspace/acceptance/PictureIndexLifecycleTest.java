package com.yuwen.visionspace.acceptance;

import com.yuwen.visionspace.agent.model.ImageResource;
import com.yuwen.visionspace.exception.BusinessException;
import com.yuwen.visionspace.model.dto.picture.PictureEditRequest;
import com.yuwen.visionspace.model.dto.picture.PictureReviewRequest;
import com.yuwen.visionspace.model.entity.User;
import com.yuwen.visionspace.rag.PictureIndex;
import com.yuwen.visionspace.rag.PublicGalleryIndexReader;
import com.yuwen.visionspace.service.PictureDeletionService;
import com.yuwen.visionspace.service.PictureEmbeddingService;
import com.yuwen.visionspace.service.PictureService;
import com.yuwen.visionspace.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

/**
 * 公开图库索引的生命周期与读侧复核。
 *
 * <p>读侧：向量库里的旧文档不构成访问授权。审核被撤销、图片被删除或被隔离之后，
 * 旧向量仍在库里，检索也会照常召回它——复核必须拦住这一条。
 *
 * <p>写侧：编辑、审核、删除都要按图片 id 触发一次索引同步。同步时以数据库当前状态
 * 为准，不再拿改动前的对象快照去重建索引。
 *
 * <p>写侧断言直接走服务层，以便精确锁定「以数据库当前状态为准」的同步调用；
 * HTTP 层的注册/登录与鉴权由既有接口用例（{@code HttpPublicGalleryTest} 等）覆盖，
 * 这里不重复建登录态。
 */
@IsolatedSpringBootTest
class PictureIndexLifecycleTest {

    private static final long APPROVED_PICTURE = 87810;
    private static final long REJECTED_PICTURE = 87811;
    private static final long DELETED_PICTURE = 87812;
    private static final long QUARANTINED_PICTURE = 87813;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PublicGalleryIndexReader reader;

    @Autowired
    private PictureService pictureService;

    @Autowired
    private PictureDeletionService pictureDeletionService;

    @Autowired
    private UserService userService;

    /**
     * 索引服务替身：这里要证明的是「谁在什么时候触发了同步」，不是向量库本身。
     * 真正的写入语义由单元测试与索引往返测试覆盖。
     */
    @MockitoBean
    private PictureEmbeddingService embeddings;

    private long ownerId;
    private long adminId;

    @BeforeEach
    void fixture() {
        jdbc.update("DELETE FROM picture_index_task WHERE picture_id IN (?, ?, ?, ?)",
                APPROVED_PICTURE, REJECTED_PICTURE, DELETED_PICTURE, QUARANTINED_PICTURE);
        jdbc.update("DELETE FROM resource_isolation WHERE resource_type = 'picture' AND resource_id IN (?, ?, ?, ?)",
                APPROVED_PICTURE, REJECTED_PICTURE, DELETED_PICTURE, QUARANTINED_PICTURE);
        jdbc.update("DELETE FROM picture WHERE id IN (?, ?, ?, ?)",
                APPROVED_PICTURE, REJECTED_PICTURE, DELETED_PICTURE, QUARANTINED_PICTURE);

        ownerId = insertUser("index_owner_" + System.nanoTime(), "user");
        adminId = insertUser("index_admin_" + System.nanoTime(), "admin");

        seedPicture(APPROVED_PICTURE, 1, "https://origin.invalid/approved.jpg");
        seedPicture(REJECTED_PICTURE, 2, "https://origin.invalid/rejected.jpg");
        seedPicture(DELETED_PICTURE, 1, "https://origin.invalid/deleted.jpg");
        seedPicture(QUARANTINED_PICTURE, 1, "https://origin.invalid/quarantined.jpg");
        // 已删除的图片：记录仍在，但逻辑删除标记已置位
        jdbc.update("UPDATE picture SET is_delete = 1 WHERE id = ?", DELETED_PICTURE);
        // 隔离中的图片：审核通过，但处置流程已把它隔离
        jdbc.update("""
                INSERT INTO resource_isolation (resource_type, resource_id, status, active_key, operator_id, reason)
                VALUES ('picture', ?, 1, ?, ?, 'index-acceptance')
                """, QUARANTINED_PICTURE, "picture:" + QUARANTINED_PICTURE, adminId);
        reset(embeddings);
    }

    @Test
    void staleVectorsOfInvalidPicturesAreNotReturned() {
        List<ImageResource> results = reader.present(List.of(
                document(APPROVED_PICTURE), document(REJECTED_PICTURE),
                document(DELETED_PICTURE), document(QUARANTINED_PICTURE)));

        assertEquals(1, results.size(), "只有当前仍然有效的公开图片可以被交出去");
        assertEquals("https://origin.invalid/approved.jpg", results.get(0).getUrl(),
                "内容地址取自数据库当前记录，而不是向量文档里的旧快照");
    }

    @Test
    void editingAPictureTriggersIndexSyncByPictureId() {
        pictureService.editPicture(editRequest(APPROVED_PICTURE, "改过的标题"), user(ownerId));

        verify(embeddings, atLeastOnce()).syncPictureIndex(APPROVED_PICTURE);
    }

    @Test
    void revokingApprovalTriggersIndexSync() {
        pictureService.doPictureReview(reviewRequest(APPROVED_PICTURE, 2), user(adminId));

        verify(embeddings, atLeastOnce()).syncPictureIndex(APPROVED_PICTURE);
    }

    @Test
    void deletingAPictureTriggersIndexSync() {
        pictureDeletionService.deletePicture(APPROVED_PICTURE, user(ownerId));

        verify(embeddings, atLeastOnce()).syncPictureIndex(APPROVED_PICTURE);
    }

    @Test
    void rejectedEditKeepsNoIndexWrite() {
        User stranger = user(insertUser("index_stranger_" + System.nanoTime(), "user"));

        assertThrows(BusinessException.class,
                () -> pictureService.editPicture(editRequest(APPROVED_PICTURE, "越权改名"), stranger),
                "越权编辑必须被拒绝");

        verify(embeddings, never()).syncPictureIndex(any());
    }

    // ---- fixtures ----

    private long insertUser(String account, String role) {
        long id = System.nanoTime() & 0x7fffffffL;
        jdbc.update("""
                INSERT INTO app_user (id, user_account, user_password, user_name, user_role, is_delete)
                VALUES (?, ?, 'acceptance-hash', ?, ?, 0)
                """, id, account, account, role);
        return id;
    }

    private void seedPicture(long id, int reviewStatus, String url) {
        jdbc.update("""
                INSERT INTO picture (id, url, name, user_id, pic_size, space_id, review_status, is_delete)
                VALUES (?, ?, ?, ?, 10, NULL, ?, 0)
                """, id, url, "图片" + id, ownerId, reviewStatus);
    }

    private Document document(long pictureId) {
        return new Document(PictureIndex.docId(pictureId), "蓝色渐变背景",
                Map.of(PictureIndex.META_PICTURE_ID, pictureId,
                        PictureIndex.META_IMAGE_URL, "https://origin.invalid/stale-" + pictureId + ".jpg"));
    }

    private PictureEditRequest editRequest(long pictureId, String name) {
        PictureEditRequest request = new PictureEditRequest();
        request.setId(pictureId);
        request.setName(name);
        request.setTags(List.of("蓝色"));
        return request;
    }

    private PictureReviewRequest reviewRequest(long pictureId, int reviewStatus) {
        PictureReviewRequest request = new PictureReviewRequest();
        request.setId(pictureId);
        request.setReviewStatus(reviewStatus);
        request.setReviewMessage("内容不合规");
        return request;
    }

    private User user(long userId) {
        return userService.getById(userId);
    }
}
