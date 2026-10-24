package com.yuwen.visionspace.tools.vip;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/**
 * 历史 classpath JSON 兑换码的离线导入与补发工具（#39）。
 *
 * <p>背景：旧实现把兑换码明文存在 {@code src/main/resources/biz/vipCode.json}
 * 并随源码进入 Git 历史，属于已暴露凭据。本工具是该文件唯一的合规出口：
 *
 * <ul>
 *   <li>输入旧 JSON，输出 {@code vip_redemption_code} 的 INSERT（只含 sha-256 摘要）；
 *       明文码不写进任何输出。</li>
 *   <li>已用码 → {@code status = 1}：保留「已被核销」的事实，权益以流水为准，
 *       不因迁移丢状态、也不凭历史推定任何用户身份。</li>
 *   <li>未用码 → {@code status = 2}（作废）：码已随 Git 历史暴露，
 *       不允许重新激活；同时输出不含码值的脱敏补发清单，供人工核验后补发。</li>
 *   <li>{@code --issue N}：生成 N 个新码用于人工补发。明文只写入指定的补发文件
 *       （该文件不得入库），stdout 只输出对应摘要 INSERT。</li>
 * </ul>
 *
 * <p>该工具是独立 main 程序，不经由 Web 入口暴露，不连接数据库——
 * DBA 拿到 INSERT 后在维护窗口自行执行，权限边界与迁移脚本一致。
 * 输出固定 UTF-8（重定向到文件是主要使用方式），Windows 控制台直接查看
 * 中文注释可能出现乱码，但不影响 INSERT 语句本身。
 */
public final class VipCodeImportTool {

    private VipCodeImportTool() {
    }

    public static void main(String[] args) throws Exception {
        System.setOut(new PrintStream(new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(new FileOutputStream(FileDescriptor.err), true, StandardCharsets.UTF_8));
        if (args.length == 0) {
            System.err.println("用法: VipCodeImportTool <旧vipCode.json路径> [--issue <数量> --issue-out <补发明文文件>]");
            System.exit(2);
        }
        Path input = Path.of(args[0]);
        int issueCount = 0;
        Path issueOut = null;
        for (int i = 1; i < args.length - 1; i++) {
            if ("--issue".equals(args[i])) {
                issueCount = Integer.parseInt(args[i + 1]);
            } else if ("--issue-out".equals(args[i])) {
                issueOut = Path.of(args[i + 1]);
            }
        }
        if (issueCount > 0 && issueOut == null) {
            System.err.println("--issue 必须搭配 --issue-out 指定补发明文输出文件");
            System.exit(2);
        }

        List<LegacyCode> legacy = parseLegacy(input);

        // --- 台账 INSERT（只有摘要与状态；已用码不带用户归属，不推定身份） ---
        System.out.println("-- #39 存量兑换码导入：只含摘要，不含明文。在维护窗口由人工执行。");
        System.out.println("-- 前提：vip_redemption_code 已由 2026-10-24-vip-redemption.sql 建表。");
        List<LegacyCode> invalidated = new ArrayList<>();
        for (LegacyCode code : legacy) {
            if (code.used()) {
                System.out.println("INSERT INTO `vip_redemption_code` (`codeHash`, `status`) VALUES ('"
                        + sha256(code.maskedKey()) + "', 1); -- used: " + code.mask());
            } else {
                invalidated.add(code);
                System.out.println("INSERT INTO `vip_redemption_code` (`codeHash`, `status`) VALUES ('"
                        + sha256(code.maskedKey()) + "', 2); -- voided: exposed-in-history");
            }
        }

        // --- 脱敏补发清单：不含码值，只有人工核验所需的计数与线索 ---
        System.out.println();
        System.out.println("-- 脱敏补发清单（不含码值）：以下历史未用码已作废，需人工核验后补发。");
        System.out.println("-- 待补发数量: " + invalidated.size());
        for (LegacyCode code : invalidated) {
            System.out.println("-- voided " + code.mask() + " (摘要 " + sha256(code.maskedKey()) + ")");
        }

        // --- 补发新码 ---
        if (issueCount > 0) {
            List<String> fresh = new ArrayList<>();
            List<String> freshHashes = new ArrayList<>();
            SecureRandom random = new SecureRandom();
            for (int i = 0; i < issueCount; i++) {
                String code = generateCode(random);
                fresh.add(code);
                freshHashes.add(sha256(code));
            }
            Files.write(issueOut, fresh, StandardCharsets.UTF_8);
            System.out.println();
            System.out.println("-- 补发新码 " + freshHashes.size() + " 个：明文已写入 "
                    + issueOut + "（不得入库、不得进入 Git）；摘要 INSERT 如下。");
            for (String hash : freshHashes) {
                System.out.println("INSERT INTO `vip_redemption_code` (`codeHash`, `status`) VALUES ('" + hash + "', 0);");
            }
        }
    }

    private record LegacyCode(String maskedKey, boolean used) {
        /** 短前缀 + 长度线索：足够人工对账，不足以还原或碰撞明文。 */
        String mask() {
            return maskedKey.substring(0, Math.min(3, maskedKey.length())) + "***(" + maskedKey.length() + ")";
        }
    }

    /**
     * 旧文件格式固定为 {@code [{"code":"..","hasUsed":false}]}；
     * 只提取 code 与 hasUsed，避免引入 JSON 依赖。
     */
    private static List<LegacyCode> parseLegacy(Path input) throws Exception {
        String content = Files.readString(input, StandardCharsets.UTF_8);
        List<LegacyCode> result = new ArrayList<>();
        int index = 0;
        while ((index = content.indexOf("\"code\"", index)) >= 0) {
            int colon = content.indexOf(':', index);
            int open = content.indexOf('"', colon);
            int close = content.indexOf('"', open + 1);
            String code = content.substring(open + 1, close);
            int objEnd = content.indexOf('}', index);
            boolean used = content.substring(index, objEnd).contains("\"hasUsed\": true")
                    || content.substring(index, objEnd).contains("\"hasUsed\":true");
            result.add(new LegacyCode(code, used));
            index = objEnd;
        }
        if (result.isEmpty()) {
            throw new IllegalArgumentException("输入文件不是历史 vipCode.json：未找到任何兑换码");
        }
        return result;
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    /** 无易混字符的 16 位码：2 的 79 次方组合空间，离线暴力枚举不可行。 */
    private static String generateCode(SecureRandom random) {
        String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        StringBuilder sb = new StringBuilder(16);
        for (int i = 0; i < 16; i++) {
            sb.append(alphabet.charAt(random.nextInt(alphabet.length())));
        }
        return sb.toString();
    }
}
