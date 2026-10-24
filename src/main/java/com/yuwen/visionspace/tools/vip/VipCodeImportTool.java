package com.yuwen.visionspace.tools.vip;

import com.yuwen.visionspace.common.CodeHashes;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.ArrayList;
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

        // --- 跨记录重复检查（在任何 SQL / 补发码输出之前） ---
        // 同一明文出现多次时，状态归属（已用/未用）无法自洽：按未用作废会同时
        // 生成同摘要的已用 INSERT，执行时撞 uk_codeHash 且先落库的那条状态随机，
        // 已用事实可能丢失。重复即整体失败，交人工核查源文件。
        java.util.Set<String> seenHashes = new java.util.HashSet<>();
        for (LegacyCode code : legacy) {
            String hash = CodeHashes.sha256(code.rawCode());
            if (!seenHashes.add(hash)) {
                System.err.println("FAIL: 输入中存在重复码（脱敏 " + code.mask()
                        + "，摘要 " + hash.substring(0, 8) + "...）；重复码的状态归属无法确定，"
                        + "请人工核查源文件后去重再导入。未输出任何 SQL。");
                System.exit(3);
            }
        }

        // --- 台账 INSERT（只有摘要与状态；已用码不带用户归属，不推定身份） ---
        System.out.println("-- #39 存量兑换码导入：只含摘要，不含明文。在维护窗口由人工执行。");
        System.out.println("-- 前提：vip_redemption_code 已由 2026-10-24-vip-redemption.sql 建表。");
        List<LegacyCode> invalidated = new ArrayList<>();
        for (LegacyCode code : legacy) {
            if (code.used()) {
                System.out.println("INSERT INTO `vip_redemption_code` (`codeHash`, `status`) VALUES ('"
                        + CodeHashes.sha256(code.rawCode()) + "', 1); -- used: " + code.mask());
            } else {
                invalidated.add(code);
                System.out.println("INSERT INTO `vip_redemption_code` (`codeHash`, `status`) VALUES ('"
                        + CodeHashes.sha256(code.rawCode()) + "', 2); -- voided: exposed-in-history");
            }
        }

        // --- 脱敏补发清单：不含码值，只有人工核验所需的计数与线索 ---
        System.out.println();
        System.out.println("-- 脱敏补发清单（不含码值）：以下历史未用码已作废，需人工核验后补发。");
        System.out.println("-- 待补发数量: " + invalidated.size());
        for (LegacyCode code : invalidated) {
            System.out.println("-- voided " + code.mask() + " (摘要 " + CodeHashes.sha256(code.rawCode()) + ")");
        }

        // --- 补发新码 ---
        if (issueCount > 0) {
            List<String> fresh = new ArrayList<>();
            List<String> freshHashes = new ArrayList<>();
            SecureRandom random = new SecureRandom();
            for (int i = 0; i < issueCount; i++) {
                String code = generateCode(random);
                fresh.add(code);
                freshHashes.add(CodeHashes.sha256(code));
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

    private record LegacyCode(String rawCode, boolean used) {
        /** 短前缀 + 长度线索：足够人工对账，不足以还原或碰撞明文。rawCode 只用于哈希，不得输出。 */
        String mask() {
            return rawCode.substring(0, Math.min(3, rawCode.length())) + "***(" + rawCode.length() + ")";
        }
    }

    /**
     * 结构化解析历史文件 {@code [{"code":"..","hasUsed":false}, ...]}。
     *
     * <p>已用状态是回滚安全与补发范围的关键输入，绝不能靠文本匹配猜：
     * 属性顺序、空白或字段缺失都会把已用码误判为未用并错误列入补发。
     * 这里用一个极小的 JSON 解析器做字段校验，要求每个元素是对象且
     * 同时含有字符串 {@code code} 与布尔 {@code hasUsed}，否则整个导入失败——
     * 宁可让人工重查输入，也不能带着错误状态出码。
     */
    private static List<LegacyCode> parseLegacy(Path input) throws Exception {
        String content = Files.readString(input, StandardCharsets.UTF_8);
        LegacyJsonParser parser = new LegacyJsonParser(content);
        List<LegacyCode> result = parser.parseArray();
        if (result.isEmpty()) {
            throw new IllegalArgumentException("输入文件不是历史 vipCode.json：未找到任何兑换码");
        }
        return result;
    }

    /**
     * 手写的最小 JSON 解析器（对象数组，字符串/布尔两种值）。
     * 工具以独立 main 运行，不引入运行时 JSON 依赖；解析错误一律抛出，
     * 不做任何静默回退。
     */
    private static final class LegacyJsonParser {
        private final String src;
        private int pos;

        LegacyJsonParser(String src) {
            this.src = src;
        }

        List<LegacyCode> parseArray() {
            List<LegacyCode> result = new ArrayList<>();
            skipWhitespace();
            expect('[');
            skipWhitespace();
            if (peek() == ']') {
                pos++;
                return result;
            }
            while (true) {
                result.add(parseObject());
                skipWhitespace();
                char c = next();
                if (c == ']') {
                    return result;
                }
                if (c != ',') {
                    throw error("数组元素之间应为 ',' 或 ']'");
                }
            }
        }

        private LegacyCode parseObject() {
            skipWhitespace();
            expect('{');
            String code = null;
            Boolean used = null;
            skipWhitespace();
            if (peek() == '}') {
                pos++;
            } else {
                while (true) {
                    skipWhitespace();
                    String key = parseString();
                    skipWhitespace();
                    expect(':');
                    skipWhitespace();
                    if ("code".equals(key)) {
                        // 关键字段重复说明文件状态可疑（可能是手改产物）：
                        // 状态判定绝不取「第一个」或「最后一个」，整体失败交人工核查。
                        if (code != null) {
                            throw error("关键字段重复：code");
                        }
                        code = parseString();
                    } else if ("hasUsed".equals(key)) {
                        if (used != null) {
                            throw error("关键字段重复：hasUsed");
                        }
                        used = parseBoolean();
                    } else {
                        // 未知字段：跳过其值，不参与判定
                        parseValue();
                    }
                    skipWhitespace();
                    char c = next();
                    if (c == '}') {
                        break;
                    }
                    if (c != ',') {
                        throw error("对象成员之间应为 ',' 或 '}'");
                    }
                }
            }
            if (code == null || used == null) {
                throw error("每个元素必须同时含有字符串 code 与布尔 hasUsed");
            }
            return new LegacyCode(code, used);
        }

        /**
         * 字符串值按 RFC 8259 转义规则解码后再返回：码值参与 sha-256 摘要，
         * 语义相同的 {@code "A"} 与反斜杠u0041 写法的 {@code "A"} 必须产出同一摘要，
         * 否则已用状态会关联到错误的码值。仅接受短转义与反斜杠uXXXX；
         * 控制字符直接拒绝（旧文件是工具生成的紧凑 JSON，不该出现）。
         */
        private String parseString() {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (true) {
                char c = next();
                if (c == '"') {
                    return sb.toString();
                }
                if (c < 0x20) {
                    throw error("字符串中含未转义控制字符");
                }
                if (c == '\\') {
                    char esc = next();
                    switch (esc) {
                        case '"' -> sb.append('"');
                        case '\\' -> sb.append('\\');
                        case '/' -> sb.append('/');
                        case 'b' -> sb.append('\b');
                        case 'f' -> sb.append('\f');
                        case 'n' -> sb.append('\n');
                        case 'r' -> sb.append('\r');
                        case 't' -> sb.append('\t');
                        case 'u' -> {
                            if (pos + 4 > src.length()) {
                                throw error("反斜杠u 转义不完整");
                            }
                            String hex = src.substring(pos, pos + 4);
                            try {
                                sb.append((char) Integer.parseInt(hex, 16));
                            } catch (NumberFormatException e) {
                                throw error("反斜杠u 后必须跟 4 位十六进制");
                            }
                            pos += 4;
                        }
                        default -> throw error("非法转义 \\" + esc);
                    }
                } else {
                    sb.append(c);
                }
            }
        }

        private boolean parseBoolean() {
            if (match("true")) {
                return true;
            }
            if (match("false")) {
                return false;
            }
            throw error("hasUsed 必须是布尔值");
        }

        /** 未知字段的值：字符串/布尔/数字/null，逐一消费。 */
        private void parseValue() {
            skipWhitespace();
            char c = peek();
            if (c == '"') {
                parseString();
            } else if (c == 't' || c == 'f') {
                parseBoolean();
            } else if (c == 'n') {
                if (!match("null")) {
                    throw error("非法字面量");
                }
            } else if (c == '-' || Character.isDigit(c)) {
                while (pos < src.length() && (Character.isDigit(peek()) || "+-.eE".indexOf(peek()) >= 0)) {
                    pos++;
                }
            } else {
                throw error("不支持的值类型");
            }
        }

        private void skipWhitespace() {
            while (pos < src.length() && Character.isWhitespace(src.charAt(pos))) {
                pos++;
            }
        }

        private char peek() {
            if (pos >= src.length()) {
                throw error("意外的输入结束");
            }
            return src.charAt(pos);
        }

        private char next() {
            char c = peek();
            pos++;
            return c;
        }

        private void expect(char c) {
            if (next() != c) {
                throw error("期望 '" + c + "'");
            }
        }

        private boolean match(String word) {
            if (src.startsWith(word, pos)) {
                pos += word.length();
                return true;
            }
            return false;
        }

        private IllegalArgumentException error(String message) {
            return new IllegalArgumentException("输入文件解析失败（位置 " + pos + "）：" + message);
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
