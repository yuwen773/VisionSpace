package com.yuwen.visionspace.service.impl;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * 「摘要升级失败可以吞掉」的前提：调用链不在事务里（计划 §3.2）。
 *
 * <p>PostgreSQL 里任何语句报错都会把当前事务置为 aborted，后续语句一律 25P02，
 * 直到回滚或回滚到 SAVEPOINT。{@code UserServiceImpl.upgradeStoredPassword}
 * 捕获写库异常后继续返回，只有「不在事务里」才是安全的——单条 UPDATE 失败
 * 只回滚这一条语句，用户仍可用旧摘要登录。这个前提原来只写在注释里，
 * 没有测试锁定；这里用注解检查钉住：谁给登录链加上 {@code @Transactional}，
 * 这条用例立刻失败，并指出要改成 REQUIRES_NEW 或去掉 catch。
 */
class LoginPasswordUpgradeTransactionBoundaryTest {

    @Test
    void loginChainStaysOutsideTransactions() {
        assertNotTransactional(UserServiceImpl.class, "authenticateForPassword", String.class, String.class);
        assertNotTransactional(UserServiceImpl.class, "userLogin",
                String.class, String.class, HttpServletRequest.class);
        assertNotTransactional(MiniProgramAuthServiceImpl.class, "bind",
                String.class, String.class, String.class);
    }

    private static void assertNotTransactional(Class<?> type, String name, Class<?>... parameters) {
        assertNull(type.getAnnotation(Transactional.class),
                type.getSimpleName() + " 不能整类加 @Transactional：登录链上的摘要升级是「容错后继续」的，"
                        + "事务里会变成 25P02");
        Method method;
        try {
            method = type.getDeclaredMethod(name, parameters);
        } catch (NoSuchMethodException e) {
            fail(type.getSimpleName() + " 里找不到 " + name + "：登录链改名了就要同步这条边界检查");
            return;
        }
        assertNull(method.getAnnotation(Transactional.class),
                type.getSimpleName() + "." + name + " 不能加 @Transactional："
                        + "upgradeStoredPassword 捕获异常后还要继续用同一条连接，PG 下事务已 abort");
    }
}
