package gui;

import java.awt.Color;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * 道路类型 → 绘制颜色配置（单例，线程安全）。
 * <p>
 * 从 classpath 下的 {@code path_colors.txt} 加载，
 * 若文件不存在或缺少类型则回退到 default → #D0D0D0。
 * <p>
 * 使用方式：
 * <pre>{@code
 * Color c = PathColorConfig.getInstance().getColor("residential");
 * }</pre>
 */
public class PathColorConfig {

    private static final PathColorConfig INSTANCE = new PathColorConfig();

    /** 默认颜色（当 default 类型也未配置时的最终回退） */
    public static final Color FALLBACK_COLOR = new Color(0xD0, 0xD0, 0xD0);

    /** default 类型的键名 */
    private static final String DEFAULT_KEY = "default";

    /** highway 类型 → 颜色 */
    private final Map<String, Color> colors;

    /** 默认颜色（缓存，避免每次查 Map） */
    private final Color defaultColor;

    // ── 单例构造 ──

    private PathColorConfig() {
        Map<String, Color> map = new LinkedHashMap<>();
        boolean loaded = false;

        try (InputStream is = getClass().getClassLoader().getResourceAsStream("path_colors.txt")) {
            if (is != null) {
                loadFromStream(is, map);
                loaded = true;
                System.out.println("[PathColorConfig] 从 path_colors.txt 加载了 "
                        + map.size() + " 种道路颜色");
            }
        } catch (IOException e) {
            System.err.println("[PathColorConfig] 读取 path_colors.txt 失败: " + e.getMessage());
        }

        if (!loaded) {
            System.out.println("[PathColorConfig] 使用内置默认颜色");
            loadDefaults(map);
        }

        this.colors = Collections.unmodifiableMap(map);
        this.defaultColor = colors.getOrDefault(DEFAULT_KEY, FALLBACK_COLOR);
    }

    // ── 公共 API ──

    /** @return 全局唯一实例 */
    public static PathColorConfig getInstance() {
        return INSTANCE;
    }

    /**
     * @param highwayType OSM highway 标签值（如 "residential", "footway"），null 视为默认
     * @return 对应颜色，未匹配时返回 default 颜色
     */
    public Color getColor(String highwayType) {
        if (highwayType == null) return defaultColor;
        return colors.getOrDefault(highwayType, defaultColor);
    }

    /** @return default 类型的颜色 */
    public Color getDefaultColor() {
        return defaultColor;
    }

    /** @return 当前配置的不可修改映射 */
    public Map<String, Color> getColors() {
        return colors;
    }

    // ── 内部加载 ──

    /** 从输入流逐行解析 #RRGGBB */
    private static void loadFromStream(InputStream is, Map<String, Color> map) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            int lineNo = 0;
            while ((line = reader.readLine()) != null) {
                lineNo++;
                line = line.trim();

                if (line.isEmpty() || line.startsWith("#")) continue;

                int eq = line.indexOf('=');
                if (eq < 0) {
                    System.err.println("[PathColorConfig] 第" + lineNo + "行格式错误(缺少'='): " + line);
                    continue;
                }

                String key = line.substring(0, eq).trim();
                String val = line.substring(eq + 1).trim();

                int comment = val.indexOf('#');
                if (comment >= 0) val = val.substring(0, comment).trim();

                try {
                    Color color = Color.decode(val);
                    map.put(key, color);
                } catch (NumberFormatException e) {
                    System.err.println("[PathColorConfig] 第" + lineNo + "行颜色解析失败: " + val);
                }
            }
        }
    }

    /** 内置默认颜色（与 path_colors.txt 一致） */
    private static void loadDefaults(Map<String, Color> map) {
        map.put(DEFAULT_KEY,  new Color(0xD0, 0xD0, 0xD0));
        map.put("motorway",       new Color(0xE8, 0x92, 0xA2));
        map.put("motorway_link",  new Color(0xE8, 0x92, 0xA2));
        map.put("trunk",          new Color(0xF9, 0xB2, 0x9C));
        map.put("trunk_link",     new Color(0xF9, 0xB2, 0x9C));
        map.put("primary",        new Color(0xFC, 0xD6, 0xA4));
        map.put("primary_link",   new Color(0xFC, 0xD6, 0xA4));
        map.put("secondary",      new Color(0xFE, 0xFE, 0xBE));
        map.put("secondary_link", new Color(0xFE, 0xFE, 0xBE));
        map.put("tertiary",       new Color(0xF7, 0xF7, 0xF7));
        map.put("tertiary_link",  new Color(0xF7, 0xF7, 0xF7));
        map.put("residential",    new Color(0xE8, 0xE8, 0xE8));
        map.put("living_street",  new Color(0xCC, 0xE8, 0xCC));
        map.put("pedestrian",     new Color(0xA5, 0xD6, 0xA5));
        map.put("footway",        new Color(0xB0, 0xC4, 0xDE));
        map.put("path",           new Color(0xC8, 0xB8, 0x9A));
        map.put("cycleway",       new Color(0xAD, 0xD8, 0xE6));
        map.put("steps",          new Color(0xA0, 0xA0, 0xA0));
        map.put("bridleway",      new Color(0xC4, 0xB8, 0x9A));
        map.put("track",          new Color(0xC8, 0xB8, 0x98));
        map.put("service",        new Color(0xD8, 0xD8, 0xD8));
        map.put("unclassified",   new Color(0xE0, 0xE0, 0xE0));
        map.put("road",           new Color(0xDD, 0xDD, 0xDD));
    }
}
