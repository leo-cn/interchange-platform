package com.interchange.platform.service;

import com.interchange.platform.common.BizException;
import com.interchange.platform.common.Utils;
import com.interchange.platform.config.AppProps;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;

/**
 * 接口级日志：每个接口一个目录、每天一个日志文件、跨天自动压缩归档。
 *
 * <p>为什么单独做一套：数据库里的 task_log / receive_log 用于"查询与对账"，
 * 而排查时更习惯直接翻某个接口的当天日志（类似 SAP PO 按接口看消息日志）。二者互补。
 *
 * <p>落盘方式：借助 logback 的 SiftingAppender，按 MDC 变量 {@code iface} 分流到
 * {@code logs/interfaces/{接口编码}/{接口编码}.log}；滚动与压缩由 logback 负责
 * （见 logback-spring.xml 的 SizeAndTimeBasedRollingPolicy）。
 *
 * <p>本类只负责"写什么、读什么、清什么"，不自己管文件锁与滚动。
 *
 * <p><b>文件里只有过程日志</b>（stage 输出），不再在收尾额外落一行 JSON 汇总——
 * 同样的字段在数据库 task_log / receive_log 里都有，且页面可导出 CSV / 文本，
 * 在文件里打第二遍只会让每次执行的边界变模糊。
 * 需要按行机器解析时，把 {@code app.iface-log.stage-format} 设成 json 即可
 * （过程日志本身就变成一行一条 JSON）。
 */
@Service
public class InterfaceLogService {

    private static final Logger log = LoggerFactory.getLogger(InterfaceLogService.class);

    /** 与 logback-spring.xml 中配置的 logger 名一致 */
    private static final Logger IFACE_LOG = LoggerFactory.getLogger("INTERFACE_LOG");

    /** 下载时的体积上限（解压后），防止一次性把超大文件读进内存 */
    private static final long MAX_DOWNLOAD_BYTES = 50L * 1024 * 1024;

    /**
     * 这些阶段把主行放到最后输出：先摊开报文/结论，再用主行收尾该阶段
     * （读起来是"对方回了什么 → 判定成功/失败 → 这条推送到此结束"）。
     */
    private static final Set<String> MAIN_LAST_STAGES = Set.of("SEND_END");

    private final Path dir;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AppProps appProps;

    public InterfaceLogService(AppProps appProps) {
        this.appProps = appProps;
        this.dir = Paths.get("logs", "interfaces").toAbsolutePath().normalize();
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            log.warn("接口日志目录创建失败: {} -> {}", dir, e.getMessage());
        }
    }

    /** 启动时把当前的输出样式打一行，方便确认配置是否生效 */
    @jakarta.annotation.PostConstruct
    public void printConfig() {
        AppProps.IfaceLog cfg = appProps.getIfaceLog();
        log.info("接口日志输出样式: pretty={}, sqlPretty={}（可用 --app.iface-log.pretty=true 覆盖）",
                cfg.isPretty(), cfg.isSqlPretty());
    }

    /** 日志目录绝对路径，页面展示用 */
    public String dirPath() {
        return dir.toString();
    }

    /* ============================ 写入 ============================ */

    /**
     * 报文能解析成 JSON 就返回 JsonNode（序列化后是嵌套对象，data 保持结构化）；
     * 否则原样返回字符串（例如 XML、纯文本报文）。
     */
    private Object jsonOrText(String text) {
        return Utils.jsonToNode(text);
    }

    /**
     * 序列化：默认单行（JSON Lines），配 app.iface-log.pretty=true 时缩进美化。
     * 失败时退化为 key=value，保证不丢记录。
     */
    private String toJson(Map<String, Object> m) {
        try {
            if (appProps.getIfaceLog().isPretty()) {
                return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(m);
            }
            return objectMapper.writeValueAsString(m);
        } catch (Exception e) {
            log.warn("接口日志 JSON 序列化失败，退化为文本: {}", e.getMessage());
            StringBuilder sb = new StringBuilder();
            m.forEach((k, v) -> sb.append(k).append('=').append(v).append(" | "));
            return sb.toString();
        }
    }

    /**
     * 执行过程日志（阶段明细）：一条一个阶段，**边执行边落盘**。
     *
     * <p>典型序列（推送）：
     * START → FETCHED → SEND_START → SEND_END →（逐条模式反复）→ END。
     * 这样手动点"立即执行"后能马上在日志文件里看到进度，而不是等整次跑完才有记录。
     *
     * @param stage   阶段标记，如 START / FETCHED / SEND_START / SEND_END / END / RECEIVED
     * @param message 人话描述
     * @param extra   附加字段（total、index、status、costMs、request、response 等），可为 null
     */
    public void stage(String direction, String iface, String ifaceName, String traceId,
                      String stage, String message, Map<String, Object> extra) {
        // 过程日志固定打印，没有开关：排查故障时它就是唯一依据，不能出现"没开所以没有"
        // 纯文本模式不需要拼 JSON，直接出一行（默认样式）
        if (!"json".equalsIgnoreCase(appProps.getIfaceLog().getStageFormat())) {
            write(iface, stageText(direction, iface, traceId, stage, message, extra));
            return;
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("time", Utils.format(LocalDateTime.now()));
        m.put("direction", direction);
        m.put("iface", iface);
        if (ifaceName != null && !ifaceName.isBlank()) {
            m.put("ifaceName", ifaceName);
        }
        if (traceId != null && !traceId.isBlank()) {
            m.put("traceId", traceId);
        }
        m.put("stage", stage);
        m.put("message", message);
        if (extra != null) {
            m.putAll(extra);
        }
        write(iface, toJson(m));
    }

    /**
     * 过程日志的纯文本拼装。
     *
     * <p>主行只允许一条，报文与结论各占一行（缩进两个空格）。默认顺序：
     * 主行 → 请求报文 → 响应报文 → 执行结果 → 错误原因。
     * 原因：报文往往很长，夹在句子中间会挡住阶段信息；而且只有先看到响应内容，
     * 才能判断"成功/失败"这个结论是否可信。各段都带中文行首，便于 grep 单独提取。
     *
     * <p><b>SEND_END 反过来看</b>（{@link #MAIN_LAST_STAGES}）：先把响应报文、执行结果、
     * 错误原因铺开，最后才用主行"第 X 条推送结束"收尾，读起来是
     * "对方回了什么 → 判定成功还是失败 → 这条数据到此为止"：
     * <pre>
     *   响应报文：{"code":0,"message":"成功",...}
     *   执行结果：成功
     * 2026-09-22 07:50:12 | PUSH | OaUserTask | SEND_END | traceId=a43e... | 第 1 条推送结束，耗时 29ms | costMs=29 | httpStatus=200
     * </pre>
     *
     * <p>「执行结果」只在**单次调用**收尾时输出（PUSH 的 SEND_END）。整个任务的 END
     * 汇总行把成败直接写进主文案，不再重复来一遍 —— 完整而结构化的记录以数据库
     * task_log / receive_log 为准，文件只是给人翻的过程流水。
     */
    private String stageText(String direction, String iface, String traceId,
                             String stage, String message, Map<String, Object> extra) {
        StringBuilder main = new StringBuilder(256);
        main.append(Utils.format(LocalDateTime.now()))
                .append(" | ").append(nvl(direction))
                .append(" | ").append(nvl(iface))
                .append(" | ").append(nvl(stage))
                .append(" | traceId=").append(nvl(traceId))
                .append(" | ").append(oneLine(message));

        List<String> tail = new ArrayList<>(4);
        if (extra != null) {
            // 第一轮：主行只留轻量字段，报文与结论先收集起来
            for (Map.Entry<String, Object> e : extra.entrySet()) {
                Object v = e.getValue();
                if (v == null) {
                    continue;
                }
                String key = e.getKey();
                if ("request".equals(key) || "response".equals(key)
                        || "status".equals(key) || "errorMsg".equals(key)) {
                    continue;
                }
                if ("sql".equals(key)) {
                    main.append(" | sql=").append(compact(v));
                } else {
                    main.append(" | ").append(key).append('=').append(compact(v));
                }
            }
            // 第二轮：严格按 请求报文 → 响应报文 → 执行结果 → 错误原因 收集
            Object request = extra.get("request");
            if (request != null) {
                tail.add("  请求报文：" + compact(request));
            }
            Object response = extra.get("response");
            if (response != null) {
                tail.add("  响应报文：" + compact(response));
            }
            Object status = extra.get("status");
            if (status != null) {
                // 结论永远排在报文之后：先看对方回了什么，再看平台判定成功还是失败
                tail.add("  执行结果：" + statusZn(compact(status)));
            }
            Object errorMsg = extra.get("errorMsg");
            if (errorMsg != null) {
                tail.add("  错误原因：" + compact(errorMsg));
            }
        }
        if (tail.isEmpty()) {
            return main.toString();
        }
        String detail = String.join("\n", tail);
        return MAIN_LAST_STAGES.contains(stage) ? detail + "\n" + main : main + "\n" + detail;
    }

    /** 执行结果英文 → 人话 */
    private static String statusZn(String status) {
        if (status == null) {
            return "-";
        }
        return switch (status.trim().toUpperCase()) {
            case "SUCCESS" -> "成功";
            case "FAIL" -> "失败";
            case "PARTIAL" -> "部分成功（部分数据推送失败）";
            default -> status;
        };
    }

    private static String nvl(String s) {
        return s == null || s.isBlank() ? "-" : s;
    }

    /** 任意值压成单行字符串：JSON 用紧凑写法，其它取 toString，并把换行/制表符换成空格 */
    private String compact(Object v) {
        if (v instanceof String s) {
            return oneLine(s);
        }
        try {
            return oneLine(objectMapper.writeValueAsString(v));
        } catch (Exception e) {
            return oneLine(String.valueOf(v));
        }
    }

    /**
     * 在本次执行的日志开头插一行空行，用来把相邻两次执行在视觉上隔开。
     *
     * <p>接口日志是追加写的，多次执行的记录会首尾相连；没有空行时，
     * "上一次的结束报文"和"这一次的任务开始"粘在一起，很难一眼看出从哪开始是一次新执行。
     * IFACE appender 的 pattern 就是 {@code %msg%n}，所以空消息正好输出一个空行。
     */
    public void blankLine(String iface) {
        write(iface, "");
    }

    /** 换行、回车、制表符全部换成空格，保证一条日志严格占一行 */
    private static String oneLine(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\r", " ").replace("\n", " ").replace("\t", " ").trim();
    }

    /** 是否写了报文正文（受 verbose-payload 控制），供调用方决定要不要传报文 */
    public boolean withPayload() {
        return appProps.getIfaceLog().isVerbosePayload();
    }

    /** 报文体按落库上限截断，避免过程日志过大 */
    public Object payload(String body) {
        if (!appProps.getIfaceLog().isVerbosePayload()) {
            return null;
        }
        return jsonOrText(Utils.truncate(body, appProps.getPush().getLogBodyLimit()));
    }

    /** 非空才写入：避免 JSON 里出现一堆 null 字段 */
    public static void putIf(Map<String, Object> m, String key, Object value) {
        if (value != null) {
            m.put(key, value);
        }
    }

    private void write(String iface, String text) {
        String key = safeKey(iface);
        try {
            MDC.put("iface", key);
            IFACE_LOG.info(text);
        } catch (Exception e) {
            // 日志写入失败绝不能影响主流程
            log.warn("接口日志写入失败 iface={}: {}", key, e.getMessage());
        } finally {
            MDC.remove("iface");
        }
    }

    /** 接口编码 → 合法目录名 */
    private String safeKey(String iface) {
        if (iface == null || iface.isBlank()) {
            return "_unknown";
        }
        String s = iface.trim().replaceAll("[\\\\/:*?\"<>|\\s]+", "_");
        if (s.length() > 80) {
            s = s.substring(0, 80);
        }
        return s.isEmpty() ? "_unknown" : s;
    }

    /* ============================ 查询 ============================ */

    /**
     * 列出所有接口日志文件（含已压缩的历史归档），按最后修改时间倒序。
     * file 字段是相对日志目录的路径，形如 {@code order-receive/order-receive.log}。
     */
    public List<Map<String, Object>> listFiles() {
        List<Map<String, Object>> list = new ArrayList<>();
        if (!Files.isDirectory(dir)) {
            return list;
        }
        try (Stream<Path> stream = Files.walk(dir, 3)) {
            List<Path> paths = stream.filter(Files::isRegularFile)
                    .filter(this::isLogFile)
                    .sorted(Comparator.comparingLong(InterfaceLogService::mtime).reversed())
                    .toList();
            for (Path p : paths) {
                Map<String, Object> m = new LinkedHashMap<>();
                String rel = dir.relativize(p).toString().replace('\\', '/');
                String name = p.getFileName().toString();
                boolean archived = name.endsWith(".gz");
                long size = sizeOf(p);
                m.put("file", rel);
                m.put("name", name);
                m.put("iface", ifaceOf(p, name));
                m.put("archived", archived);
                m.put("size", size);
                m.put("sizeText", humanSize(size));
                m.put("lastModified", Utils.format(LocalDateTime.ofInstant(
                        Instant.ofEpochMilli(mtime(p)), ZoneId.systemDefault())));
                list.add(m);
            }
        } catch (IOException e) {
            log.warn("读取接口日志目录失败: {}", e.getMessage());
        }
        return list;
    }

    /**
     * 按 traceId 从接口日志里捞出“这一次执行”的完整记录（含请求/响应报文）。
     *
     * <p>为什么需要它：数据库里的 task_log 报文列可以被关掉或截断，而排查问题时
     * 往往只想要"这一条"的报文。日志文件里每次调用都打了「请求报文 / 响应报文」两行，
     * 只是没有按 traceId 定位的入口——人肉翻文件不现实，这个方法就是补上这个入口。
     *
     * <p>怎么定位：主行里都带 {@code traceId=xxx}，缩进两空格的行是它附属的报文/结论行。
     * 注意 SEND_END 的 tail 在主行<b>之前</b>（见 {@link #MAIN_LAST_STAGES}），
     * 所以这里用一个 pending 缓冲先把尾行攒着，等确认下一条主行属于本 traceId 再一起收。
     * 空行是每次执行之间的分隔（{@link #blankLine}），遇到就清空缓冲。
     *
     * <p>找不到时会继续往回翻历史归档（.log.gz，按时间倒序），最多翻到保留期为止。
     *
     * @param date 可选，yyyy-MM-dd：指定只看某一天，省得翻全部归档
     */
    public Map<String, Object> findByTraceId(String iface, String traceId, String date) {
        if (traceId == null || traceId.isBlank()) {
            throw new BizException(400, "请提供 traceId");
        }
        String tid = traceId.trim();
        // 没指定接口时跨接口查找：手里只有一个 traceId 也能定位到那次执行
        if (iface == null || iface.isBlank()) {
            return searchAllByTraceId(tid, date);
        }
        String key = safeKey(iface);
        for (Path p : candidateFiles(key, date)) {
            if (sizeOf(p) > MAX_DOWNLOAD_BYTES) {
                continue;
            }
            Map<String, Object> hit = extractByTraceId(new String(readBytes(p), StandardCharsets.UTF_8), tid);
            if (Boolean.TRUE.equals(hit.get("found"))) {
                hit.put("iface", key);
                hit.put("traceId", tid);
                hit.put("file", dir.relativize(p).toString().replace('\\', '/'));
                hit.put("archived", isGzip(p));
                return hit;
            }
        }
        return notFound(key, tid);
    }

    /**
     * 跨接口按 traceId 查找：遍历所有接口目录，按文件修改时间倒序（最近执行的先试）。
     * 用于只知道 traceId、不确定它属于哪个接口的场景。
     */
    private Map<String, Object> searchAllByTraceId(String tid, String date) {
        if (!Files.isDirectory(dir)) {
            return notFound(null, tid);
        }
        List<Path> all = new ArrayList<>();
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir)) {
            for (Path sub : ds) {
                if (Files.isDirectory(sub)) {
                    all.addAll(candidateFiles(sub.getFileName().toString(), date));
                }
            }
        } catch (IOException ignored) {
            return notFound(null, tid);
        }
        all.sort(Comparator.comparingLong(InterfaceLogService::mtime).reversed());
        // 上限保护：目录多、归档多时别把整个磁盘翻一遍
        final int maxScan = 200;
        int scanned = 0;
        for (Path p : all) {
            if (scanned++ >= maxScan) {
                break;
            }
            if (sizeOf(p) > MAX_DOWNLOAD_BYTES) {
                continue;
            }
            Map<String, Object> hit = extractByTraceId(new String(readBytes(p), StandardCharsets.UTF_8), tid);
            if (Boolean.TRUE.equals(hit.get("found"))) {
                hit.put("iface", p.getParent().getFileName().toString());
                hit.put("traceId", tid);
                hit.put("file", dir.relativize(p).toString().replace('\\', '/'));
                hit.put("archived", isGzip(p));
                return hit;
            }
        }
        return notFound(null, tid);
    }

    /** 没找到时的返回。key 为空表示跨接口查找 */
    private Map<String, Object> notFound(String key, String tid) {
        Map<String, Object> none = new LinkedHashMap<>();
        none.put("found", false);
        none.put("iface", key);
        none.put("traceId", tid);
        none.put("requests", List.of());
        none.put("responses", List.of());
        none.put("message", "接口日志里没有这个 traceId：日志可能已超过保留期（默认 30 天），"
                + "或当时 app.iface-log.verbose-payload 被关掉、报文没进文件");
        return none;
    }

    /** 按 traceId 切出本次执行的原始行，并解析出请求/响应报文 */
    private Map<String, Object> extractByTraceId(String text, String traceId) {
        String marker = "traceId=" + traceId;
        List<String> hit = new ArrayList<>();
        List<String> pending = new ArrayList<>();
        boolean collecting = false;
        for (String raw : text.split("\n", -1)) {
            String line = raw.endsWith("\r") ? raw.substring(0, raw.length() - 1) : raw;
            if (line.isBlank()) {
                pending.clear();
                collecting = false;
                continue;
            }
            if (line.startsWith(" ") || line.startsWith("\t")) {
                if (collecting) {
                    hit.add(line);
                } else {
                    pending.add(line);
                }
                continue;
            }
            if (line.contains(marker)) {
                hit.addAll(pending);
                pending.clear();
                hit.add(line);
                collecting = true;
            } else {
                pending.clear();
                collecting = false;
            }
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("found", !hit.isEmpty());
        if (hit.isEmpty()) {
            return m;
        }
        List<String> requests = new ArrayList<>();
        List<String> responses = new ArrayList<>();
        for (String l : hit) {
            String t = l.trim();
            if (t.startsWith("请求报文：")) {
                requests.add(Utils.prettyJson(t.substring(5).trim()));
            } else if (t.startsWith("响应报文：")) {
                responses.add(Utils.prettyJson(t.substring(5).trim()));
            }
        }
        m.put("lineCount", hit.size());
        m.put("lines", hit);
        m.put("requests", requests);
        m.put("responses", responses);
        m.put("raw", String.join("\n", hit));
        return m;
    }

    /** 待查文件：当天的 .log 优先，其次按时间倒序的历史归档 */
    private List<Path> candidateFiles(String key, String date) {
        List<Path> list = new ArrayList<>();
        Path sub = dir.resolve(key);
        if (!Files.isDirectory(sub)) {
            return list;
        }
        String want = (date == null || date.isBlank()) ? null : date.trim();
        // 当天文件恒为候选：它未必只装今天的内容。
        // 日志只在"新的一天首次写入"时才滚动压缩，前一天跑的记录可能还躺在这个文件里；
        // 若因为传了历史 date 就把它排除，昨天那次执行反而查不到。
        Path current = sub.resolve(key + ".log");
        if (Files.isRegularFile(current)) {
            list.add(current);
        }
        List<Path> archived = new ArrayList<>();
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(sub, key + ".*.log.gz")) {
            for (Path p : ds) {
                if (want == null || p.getFileName().toString().contains(want)) {
                    archived.add(p);
                }
            }
        } catch (IOException ignored) {
            // 目录读不到就只查当天文件
        }
        archived.sort(Comparator.comparingLong(InterfaceLogService::mtime).reversed());
        list.addAll(archived);
        return list;
    }

    /**
     * 读取文件末尾若干行（默认 300 行）。
     * 已压缩的归档先解压再取尾部——归档是历史数据，量可控，取全量更稳妥。
     */
    public String tail(String file, int lines) {
        Path p = resolve(file);
        if (!Files.isRegularFile(p)) {
            throw new BizException(404, "接口日志文件不存在: " + file);
        }
        int max = lines <= 0 ? 300 : Math.min(lines, 2000);
        if (isGzip(p)) {
            String all = new String(readBytes(p), StandardCharsets.UTF_8);
            List<String> allLines = List.of(all.split("\n", -1));
            int from = Math.max(0, allLines.size() - max);
            return String.join("\n", allLines.subList(from, allLines.size()));
        }
        List<String> result = new ArrayList<>();
        try (RandomAccessFile raf = new RandomAccessFile(p.toFile(), "r")) {
            long pos = raf.length();
            ByteArrayBuilder buf = new ByteArrayBuilder();
            while (pos > 0 && result.size() < max) {
                pos--;
                raf.seek(pos);
                int b = raf.read();
                if (b == '\n') {
                    if (buf.length() > 0) {
                        result.add(0, buf.toString());
                        buf.reset();
                    }
                } else if (b != '\r') {
                    buf.append((byte) b);
                }
            }
            if (buf.length() > 0 && result.size() < max) {
                result.add(0, buf.toString());
            }
        } catch (IOException e) {
            throw new BizException(500, "读取接口日志失败: " + e.getMessage());
        }
        return String.join("\n", result);
    }

    /** 读取完整文件内容（下载用，有体积上限保护） */
    public byte[] readAll(String file) {
        Path p = resolve(file);
        if (!Files.isRegularFile(p)) {
            throw new BizException(404, "接口日志文件不存在: " + file);
        }
        byte[] bytes = readBytes(p);
        if (bytes.length > MAX_DOWNLOAD_BYTES) {
            throw new BizException(400, "日志文件过大（" + humanSize(bytes.length) + "），请到服务器目录直接查看");
        }
        return bytes;
    }

    /**
     * 清空日志。
     * <ul>
     *   <li>当天文件（.log）：用"截断"而不是删除——logback 的 appender 持有句柄，
     *       在 Windows 上删不掉；截断不影响后续写入。同时删掉当天切分出来的分片。</li>
     *   <li>历史归档（.log.gz）：没有句柄占用，直接删除。</li>
     * </ul>
     */
    public String clear(String file) {
        Path p = resolve(file);
        String name = p.getFileName().toString();

        if (isGzip(p)) {
            try {
                Files.deleteIfExists(p);
                return "已删除归档 " + name;
            } catch (IOException e) {
                throw new BizException(500, "删除归档失败: " + e.getMessage());
            }
        }

        String base = name.replaceAll("\\.log$", "");
        Path parent = p.getParent();
        int cleared = 0;
        try (FileOutputStream fos = new FileOutputStream(p.toFile(), false)) {
            // 以覆盖方式打开即实现截断
            cleared++;
        } catch (IOException e) {
            throw new BizException(500, "清空失败，文件可能被占用: " + e.getMessage());
        }

        // 同一天因超过体积而切分出来的分片，一并清掉
        String today = LocalDate.now().toString();
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(parent, base + "." + today + ".*.log.gz")) {
            for (Path q : ds) {
                Files.deleteIfExists(q);
                cleared++;
            }
        } catch (IOException ignored) {
            // 分片清不掉不影响主文件已清空
        }
        return "已清空 " + base + " 当天接口日志";
    }

    /* ============================ 工具 ============================ */

    private boolean isLogFile(Path p) {
        String n = p.getFileName().toString();
        return n.endsWith(".log") || n.endsWith(".log.gz");
    }

    private boolean isGzip(Path p) {
        return p.getFileName().toString().endsWith(".gz");
    }

    /** 接口编码：优先取所在子目录名，兼容早期直接放在根目录的旧文件 */
    private String ifaceOf(Path p, String name) {
        Path parent = p.getParent();
        if (parent != null && !parent.equals(dir)) {
            String dirName = parent.getFileName().toString();
            if (!dirName.isBlank()) {
                return dirName;
            }
        }
        return name.replaceAll("\\.log(\\.gz)?$", "").replaceAll("\\.\\d{4}-\\d{2}-\\d{2}.*$", "");
    }

    private byte[] readBytes(Path p) {
        try (InputStream in = isGzip(p) ? new GZIPInputStream(Files.newInputStream(p))
                : Files.newInputStream(p);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            in.transferTo(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new BizException(500, "读取接口日志失败: " + e.getMessage());
        }
    }

    /** 只允许访问日志目录内的日志文件，杜绝路径穿越 */
    private Path resolve(String file) {
        if (file == null || file.isBlank()) {
            throw new BizException(400, "请指定日志文件");
        }
        String rel = file.replace('\\', '/');
        Path p = dir.resolve(rel).normalize();
        if (!p.startsWith(dir)) {
            throw new BizException(400, "日志文件名不合法: " + file);
        }
        if (!isLogFile(p)) {
            throw new BizException(400, "日志文件名不合法: " + file);
        }
        return p;
    }

    private static long mtime(Path p) {
        try {
            return Files.getLastModifiedTime(p).toMillis();
        } catch (IOException e) {
            return 0L;
        }
    }

    private static long sizeOf(Path p) {
        try {
            return Files.size(p);
        } catch (IOException e) {
            return 0L;
        }
    }

    private static String humanSize(long size) {
        if (size < 1024) {
            return size + " B";
        }
        if (size < 1024 * 1024) {
            return String.format("%.1f KB", size / 1024.0);
        }
        return String.format("%.2f MB", size / (1024.0 * 1024.0));
    }

    /** 简易字节缓冲，用于从文件尾部倒序读行 */
    private static class ByteArrayBuilder {
        private final byte[] buf = new byte[64 * 1024];
        private int len;

        void append(byte b) {
            if (len < buf.length) {
                buf[len++] = b;
            }
        }

        void reset() {
            len = 0;
        }

        int length() {
            return len;
        }

        /**
         * 从文件尾部倒着读，字节是逆序收集的，这里必须翻转回来再解码，
         * 否则整行内容会是反的（多字节的中文尤其明显）。
         */
        @Override
        public String toString() {
            byte[] out = new byte[len];
            for (int i = 0; i < len; i++) {
                out[i] = buf[len - 1 - i];
            }
            return new String(out, StandardCharsets.UTF_8);
        }
    }
}
