package com.yuwen.visionspace.acceptance;

import com.yuwen.visionspace.service.PasswordHasher;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** 在 frontend-build 之后显式执行；与后端单元/HTTP 套件分列为浏览器门禁。 */
@IsolatedSpringBootTest
@Import(HttpAssetSearchTest.ModelFixture.class)
class AssetBoardBrowserIT {
    @LocalServerPort private int port;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PasswordHasher passwords;
    @Autowired private ExternalCallRecorder recorder;

    @Test
    void browserEditsSavesRecoversConflictsAndRevokesPersonalBoards() throws Exception {
        assertTrue(Files.exists(Path.of("frontend/dist/index.html")), "Run frontend build-only before the browser gate");
        long owner = com.baomidou.mybatisplus.core.toolkit.IdWorker.getId();
        long viewer = com.baomidou.mybatisplus.core.toolkit.IdWorker.getId();
        long space = com.baomidou.mybatisplus.core.toolkit.IdWorker.getId();
        long picture = com.baomidou.mybatisplus.core.toolkit.IdWorker.getId();
        long member = com.baomidou.mybatisplus.core.toolkit.IdWorker.getId();
        String ownerAccount = "asset_browser_owner_" + owner;
        String viewerAccount = "asset_browser_viewer_" + viewer;
        String passwordHash = passwords.hash("acceptance-password");
        jdbc.update("INSERT INTO user (id, userAccount, userPassword, userName) VALUES (?, ?, ?, '素材所有者'), (?, ?, ?, '素材查看者')",
                owner, ownerAccount, passwordHash, viewer, viewerAccount, passwordHash);
        jdbc.update("INSERT INTO space (id, spaceName, userId, spaceType, totalSize, totalCount) VALUES (?, '横图素材库', ?, 1, 10, 1)", space, owner);
        jdbc.update("INSERT INTO space_user (id, spaceId, userId, spaceRole) VALUES (?, ?, ?, 'viewer'), (?, ?, ?, 'admin')",
                member, space, viewer, com.baomidou.mybatisplus.core.toolkit.IdWorker.getId(), space, owner);
        jdbc.update("INSERT INTO picture (id, userId, spaceId, name, url, picWidth, picHeight, picSize, picColor) VALUES (?, ?, ?, '横图预览', 'https://origin.invalid/browser.png', 1920, 1080, 10, '#0000FF')",
                picture, owner, space);
        String key = "/asset-browser/" + picture + ".png";
        jdbc.update("INSERT INTO picture_file (ownerUserId, spaceId, pictureId, purpose, kind, platform, objectKey, url, fileSize, status) VALUES (?, ?, ?, 'gallery', 'original', ?, ?, 'https://acceptance.invalid/browser.png', 10, 1)",
                owner, space, picture, RecordingStorageConfiguration.PLATFORM, key);
        RecordingStorageConfiguration.SEEDED_CONTENT.put(key, Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII="));
        Path artifacts = Path.of("target/asset-board-browser").toAbsolutePath();
        Files.createDirectories(artifacts);
        Path log = artifacts.resolve("browser.log");
        jdbc.update("UPDATE user SET userRole='moderator' WHERE id=?", owner);
        int calls = recorder.calls().size();
        Process process = new ProcessBuilder("node", "frontend/e2e/asset-board.mjs", String.valueOf(port),
                viewerAccount, ownerAccount, String.valueOf(space), String.valueOf(member), artifacts.toString(), String.valueOf(picture))
                .redirectErrorStream(true).redirectOutput(log.toFile()).start();
        try {
            assertTrue(process.waitFor(120, TimeUnit.SECONDS), "Browser gate timed out: " + log);
            assertEquals(0, process.exitValue(), Files.readString(log));
            assertEquals(1L, jdbc.queryForObject("SELECT totalCount FROM space WHERE id=?", Long.class, space));
            assertEquals(0, recorder.calls().subList(calls, recorder.calls().size()).stream().filter(call -> !call.method().equals("READ")).count(), "Board browser flows must only preview objects, never mutate storage");
        } finally {
            process.destroyForcibly();
            RecordingStorageConfiguration.SEEDED_CONTENT.remove(key);
        }
    }
}
