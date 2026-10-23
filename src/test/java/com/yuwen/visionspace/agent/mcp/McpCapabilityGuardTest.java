package com.yuwen.visionspace.agent.mcp;

import com.yuwen.visionspace.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 生产环境封禁自定义 STDIO 的判定入口。
 *
 * <p>这里只验证判定本身；「零进程启动」由执行层测试证明——拒绝发生在构造进程参数之前。
 */
class McpCapabilityGuardTest {

    @Test
    void productionDisablesCustomStdio() {
        McpCapabilityGuard guard = guard("prod");
        assertTrue(guard.stdioDisabled());
        assertFalse(guard.isInstallTypeAllowed("STDIO"));
        assertFalse(guard.isInstallTypeAllowed("stdio"));
        assertTrue(guard.isInstallTypeAllowed("SSE"));
        assertTrue(guard.isInstallTypeAllowed("HTTP"));

        BusinessException error = assertThrows(BusinessException.class,
                () -> guard.assertStdioAllowed("test"));
        assertEquals(40300, error.getCode());
    }

    @Test
    void productionAliasIsAlsoTreatedAsProduction() {
        assertTrue(guard("production").stdioDisabled());
        assertTrue(guard("PROD").stdioDisabled());
    }

    @Test
    void nonProductionKeepsStdioAvailable() {
        for (String profile : new String[]{"local", "acceptance", "dev"}) {
            McpCapabilityGuard guard = guard(profile);
            assertFalse(guard.stdioDisabled(), profile);
            assertTrue(guard.isInstallTypeAllowed("STDIO"), profile);
            assertDoesNotThrow(() -> guard.assertStdioAllowed("test"), profile);
        }
    }

    @Test
    void noActiveProfileIsNotProduction() {
        McpCapabilityGuard guard = new McpCapabilityGuard(new MockEnvironment());
        assertFalse(guard.stdioDisabled());
    }

    private McpCapabilityGuard guard(String... profiles) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(profiles);
        return new McpCapabilityGuard(environment);
    }
}
