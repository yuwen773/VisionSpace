package com.yuwen.visionspace.acceptance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #46 维护窗口编排清单的完整性：清单是这份演练的唯一编排事实来源。
 *
 * <p>各业务票已经交付 {@code sql/migrations/*.sql}，本票不补写迁移，只把它们编排成
 * 备份 → 停写 → 迁移 → 核对 → 恢复 → 回滚演练 → 重新部署的有序窗口。清单必须覆盖
 * 磁盘上全部脚本、给出每步的领域归属与回滚配对，并显式列出人工批准点；否则编排会
 * 悄悄漏掉某一张票，或把只读清单当成可执行迁移。
 *
 * <p>本类不依赖 Spring / 隔离 MySQL，可用 {@code mvn -Dtest=MaintenanceWindowManifestTest test}
 * 单独运行；真正的执行证据在 {@link MaintenanceWindowDrillTest}。
 */
class MaintenanceWindowManifestTest {

    private static final Path MANIFEST = Path.of("sql/migrations/maintenance-window.manifest.json");
    private static final Path MIGRATIONS = Path.of("sql/migrations");

    private static JsonNode manifest() throws IOException {
        assertTrue(Files.isRegularFile(MANIFEST), "维护窗口编排清单必须存在: " + MANIFEST);
        return new ObjectMapper().readTree(Files.readString(MANIFEST, StandardCharsets.UTF_8));
    }

    private static List<String> sqlFilesOnDisk() throws IOException {
        try (Stream<Path> paths = Files.list(MIGRATIONS)) {
            return paths.filter(p -> p.getFileName().toString().endsWith(".sql"))
                    .map(p -> p.getFileName().toString())
                    .sorted()
                    .toList();
        }
    }

    @Test
    void manifestDeclaresTheTicketItsIntegrationBaseAndTheWindowPhases() throws Exception {
        JsonNode root = manifest();
        assertEquals("#46", root.path("ticket").asText());
        assertEquals("#26", root.path("parent").asText());
        assertTrue(root.path("snapshot").path("desensitized").asBoolean(),
                "演练必须在脱敏快照上进行");

        List<String> phases = textList(root.path("phases"));
        assertEquals(List.of("BACKUP", "PAUSE_WRITES", "MIGRATE", "RECONCILE", "RESUME",
                "ROLLBACK_REHEARSAL", "RE_DEPLOY"), phases,
                "窗口阶段顺序必须与父规格 34.1 一致");
    }

    @Test
    void everyDeliveredMigrationScriptIsAccountedForExactlyOnce() throws Exception {
        JsonNode root = manifest();
        List<String> declared = new ArrayList<>();
        for (JsonNode step : root.path("steps")) {
            declared.add(step.path("file").asText());
        }
        List<String> onDisk = sqlFilesOnDisk();
        Set<String> declaredSet = new HashSet<>(declared);

        assertEquals(new HashSet<>(onDisk), declaredSet,
                "清单必须恰好覆盖磁盘上每一份迁移脚本，既不遗漏也不虚构: " + onDisk);
        assertEquals(onDisk.size(), declared.size(),
                "同一份脚本不得在清单里重复登记");
        // 前向步骤声明的回滚脚本必须也是清单里的一步（在回滚演练阶段执行），不能悬空引用。
        for (JsonNode step : root.path("steps")) {
            String rollback = step.path("rollback").asText("");
            if (!rollback.isEmpty()) {
                assertTrue(declaredSet.contains(rollback),
                        step.path("file").asText() + " 引用的回滚脚本必须也编排进演练: " + rollback);
                assertTrue(step.path("phase").asText().equals("MIGRATE"),
                        "只有迁移步骤才声明回滚配对: " + step.path("file").asText());
            }
        }
        assertFalse(onDisk.isEmpty());
    }

    @Test
    void rollbackPairingsPointToARealStepInTheRehearsalPhase() throws Exception {
        JsonNode root = manifest();
        Set<String> rehearsalFiles = new HashSet<>();
        for (JsonNode step : root.path("steps")) {
            if (step.path("phase").asText().equals("ROLLBACK_REHEARSAL")) {
                rehearsalFiles.add(step.path("file").asText());
            }
        }
        // 一个回滚脚本可以被多步共用（#37 的回填与前向迁移由同一 -rollback.sql 撤销），
        // 所以这里比的是去重后的配对集合，而不是步骤条数。
        Set<String> paired = new HashSet<>();
        for (JsonNode step : root.path("steps")) {
            String rollback = step.path("rollback").asText("");
            if (!rollback.isEmpty()) {
                paired.add(rollback);
                assertTrue(rehearsalFiles.contains(rollback),
                        step.path("file").asText() + " 的回滚脚本必须在 ROLLBACK_REHEARSAL 阶段执行");
            }
        }
        assertEquals(rehearsalFiles, paired,
                "每份回滚脚本恰好被前向步骤配对，且演练阶段没有无人配对的额外回滚脚本");
    }

    @Test
    void eachStepDeclaresItsDomainPhaseReplayabilityAndRollbackSemantics() throws Exception {
        JsonNode root = manifest();
        Set<String> kinds = Set.of("forward", "inventory", "backfill", "rollback-note");
        for (JsonNode step : root.path("steps")) {
            String file = step.path("file").asText();
            assertTrue(step.path("order").asInt() > 0, file + " 必须有顺序");
            assertTrue(step.path("ticket").asText().startsWith("#"), file + " 必须标注领域工单");
            assertTrue(step.path("phase").asText().matches("BACKUP|PAUSE_WRITES|MIGRATE|RECONCILE|RESUME|ROLLBACK_REHEARSAL|RE_DEPLOY"),
                    file + " 的阶段必须来自窗口阶段: " + step.path("phase").asText());
            assertTrue(kinds.contains(step.path("kind").asText()),
                    file + " 的 kind 必须可识别: " + step.path("kind").asText());
            assertFalse(step.path("replayable").isMissingNode(), file + " 必须声明是否可在已迁移结构上重放");
            assertFalse(step.path("writesPaused").isMissingNode(), file + " 必须声明是否要求停写");
            assertTrue(Files.isRegularFile(MIGRATIONS.resolve(file)), "清单引用的脚本不存在: " + file);
            String rollback = step.path("rollback").asText("");
            if (!rollback.isEmpty()) {
                assertTrue(Files.isRegularFile(MIGRATIONS.resolve(rollback)),
                        "清单引用的回滚脚本不存在: " + rollback);
            }
        }
    }

    @Test
    void readOnlyInventoryStepsNeverWriteAndRollbackStepsAreDeclaredExplicitly() throws Exception {
        JsonNode root = manifest();
        int inventoryCount = 0;
        int rollbackCount = 0;
        for (JsonNode step : root.path("steps")) {
            String kind = step.path("kind").asText();
            if ("inventory".equals(kind)) {
                inventoryCount++;
                assertFalse(step.path("writesData").asBoolean(true),
                        step.path("file").asText() + " 是只读清单，不得写数据");
                assertTrue(step.path("desensitized").asBoolean(false),
                        step.path("file").asText() + " 只读清单必须声明脱敏");
            }
            if (step.path("phase").asText().equals("ROLLBACK_REHEARSAL")) {
                rollbackCount++;
                assertTrue(step.path("riskReopenGuard").asText("").length() > 0,
                        step.path("file").asText() + " 回滚步骤必须写明不重新开放风险的依据");
            }
        }
        assertTrue(inventoryCount >= 4, "四份只读盘点/阻断清单都要纳入编排，实际 " + inventoryCount);
        assertTrue(rollbackCount >= 8, "各领域的回滚脚本都要纳入演练，实际 " + rollbackCount);
    }

    @Test
    void inventoryDimensionsCoverEveryStateNamedInAcceptanceCriteria() throws Exception {
        JsonNode root = manifest();
        List<String> dimensions = new ArrayList<>();
        for (JsonNode dim : root.path("inventoryDimensions")) {
            dimensions.add(dim.path("id").asText());
            assertTrue(dim.path("tables").size() > 0, dim.path("id").asText() + " 必须给出核对表");
        }
        for (String required : List.of("pictureOwnership", "attachmentOwnership", "originalPlatform",
                "spaceQuota", "validSpaces", "actionEvents", "vipEntitlement", "runState",
                "userPreference")) {
            assertTrue(dimensions.contains(required), "盘点维度缺少 " + required);
        }
    }

    @Test
    void manualApprovalPointsCoverTheUnverifiableRealEnvironmentGates() throws Exception {
        JsonNode root = manifest();
        List<String> ids = new ArrayList<>();
        for (JsonNode gate : root.path("manualApprovalPoints")) {
            ids.add(gate.path("id").asText());
            assertTrue(gate.path("check").asText().length() > 0, gate.path("id").asText() + " 必须给出精确检查项");
            assertTrue(gate.path("evidence").asText().length() > 0, gate.path("id").asText() + " 必须给出留痕要求");
            assertTrue(gate.path("approver").asText().length() > 0, gate.path("id").asText() + " 必须指定批准角色");
        }
        assertEquals(ids.size(), new HashSet<>(ids).size(), "人工批准点编号不得重复");
        assertTrue(ids.size() >= 6, "真实环境的精确检查清单至少覆盖桶 ACL、CDN、缓存、旧地址、凭据与回滚放行");
        assertTrue(root.path("productionAcceptance").asText("").equals("NOT_RUN"),
                "本票不得把隔离演练表述为生产验收完成");
    }

    private static List<String> textList(JsonNode array) {
        List<String> result = new ArrayList<>();
        for (JsonNode node : array) {
            result.add(node.asText());
        }
        return result;
    }
}
