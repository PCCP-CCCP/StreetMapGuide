package pathfinding;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * 道路类型耗时乘数配置（单例，线程安全）。
 * <p>
 * 从 classpath 下的 {@code speed_factors.txt} 加载，
 * 若文件不存在则使用内置默认映射。
 * <p>
 * 使用方式：
 * <pre>{@code
 * double factor = PathSpeedConfig.getInstance().getFactor("footway"); // 0.9
 * }</pre>
 */
public class PathSpeedConfig {

    private static final PathSpeedConfig INSTANCE = new PathSpeedConfig();

    /** 默认乘数（未匹配到的 highway 类型） */
    public static final double DEFAULT_FACTOR = 1.0;

    /** highway 类型 → 耗时乘数 */
    private final Map<String, Double> factors;

    // ── 单例构造 ──

    private PathSpeedConfig() {
        Map<String, Double> map = new LinkedHashMap<>();
        boolean loaded = false;

        // 尝试从 classpath 加载
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("speed_factors.txt")) {
            if (is != null) {
                loadFromStream(is, map);
                loaded = true;
                System.out.println("[PathSpeedConfig] 从 speed_factors.txt 加载了 "
                        + map.size() + " 条速度因子");
            }
        } catch (IOException e) {
            System.err.println("[PathSpeedConfig] 读取 speed_factors.txt 失败: " + e.getMessage());
        }

        if (!loaded) {
            System.out.println("[PathSpeedConfig] 使用内置默认速度因子");
            loadDefaults(map);
        }

        this.factors = Collections.unmodifiableMap(map);
    }

    // ── 公共 API ──

    /** @return 全局唯一实例 */
    public static PathSpeedConfig getInstance() {
        return INSTANCE;
    }

    /**
     * @param highwayType OSM highway 标签值（如 "footway", "primary"）
     * @return 耗时乘数，未匹配时返回 {@link #DEFAULT_FACTOR}
     */
    public double getFactor(String highwayType) {
        if (highwayType == null) return DEFAULT_FACTOR;
        return factors.getOrDefault(highwayType, DEFAULT_FACTOR);
    }

    /** @return 当前配置的不可修改映射副本 */
    public Map<String, Double> getFactors() {
        return factors; // already unmodifiable
    }

    // ── 内部加载 ──

    /** 从输入流逐行解析 */
    private static void loadFromStream(InputStream is, Map<String, Double> map) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            int lineNo = 0;
            while ((line = reader.readLine()) != null) {
                lineNo++;
                line = line.trim();

                // 跳过空行和注释
                if (line.isEmpty() || line.startsWith("#")) continue;

                int eq = line.indexOf('=');
                if (eq < 0) {
                    System.err.println("[PathSpeedConfig] 第" + lineNo + "行格式错误(缺少'='): " + line);
                    continue;
                }

                String key = line.substring(0, eq).trim();
                String val = line.substring(eq + 1).trim();

                // 去除行尾注释
                int comment = val.indexOf('#');
                if (comment >= 0) val = val.substring(0, comment).trim();

                try {
                    double factor = Double.parseDouble(val);
                    if (factor <= 0) {
                        System.err.println("[PathSpeedConfig] 第" + lineNo + "行乘数无效(需>0): " + factor);
                        continue;
                    }
                    map.put(key, factor);
                } catch (NumberFormatException e) {
                    System.err.println("[PathSpeedConfig] 第" + lineNo + "行数值解析失败: " + val);
                }
            }
        }
    }

    /** 内置默认映射（与 speed_factors.txt 保持一致） */
    private static void loadDefaults(Map<String, Double> map) {
        map.put("motorway",       10.0);
        map.put("motorway_link",  10.0);
        map.put("trunk",          5.0);
        map.put("trunk_link",     5.0);
        map.put("primary",        1.5);
        map.put("primary_link",   1.5);
        map.put("secondary",      1.2);
        map.put("secondary_link", 1.2);
        map.put("tertiary",       1.1);
        map.put("tertiary_link",  1.1);
        map.put("residential",    1.0);
        map.put("living_street",  0.9);
        map.put("pedestrian",     0.8);
        map.put("footway",        0.9);
        map.put("path",           1.0);
        map.put("cycleway",       1.0);
        map.put("steps",          2.0);
        map.put("bridleway",      1.3);
        map.put("track",          1.2);
        map.put("service",        1.0);
        map.put("unclassified",   1.0);
        map.put("road",           1.0);
    }
}
