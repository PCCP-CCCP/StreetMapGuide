package gui;

import java.util.Map;

/**
 * OSM 地名标注数据 — 从带 name 标签的 Node 提取。
 *
 * @param lon       经度
 * @param lat       纬度
 * @param name      地名文本（优先中文）
 * @param placeTag  OSM place 标签值（如 city, town, village），null 表示无标签
 * @param priority  显示优先级（数值越小越优先显示，用于 zoom 级别筛选）
 */
public record NamedPlace(double lon, double lat, String name, String placeTag, int priority) {

    /** place 标签值 → 显示优先级 */
    private static final Map<String, Integer> PLACE_PRIORITY = Map.ofEntries(
            Map.entry("state",        1),
            Map.entry("province",     1),
            Map.entry("region",       1),
            Map.entry("city",         2),
            Map.entry("town",         2),
            Map.entry("county",       3),
            Map.entry("district",     3),
            Map.entry("municipality", 3),
            Map.entry("village",      4),
            Map.entry("hamlet",       5),
            Map.entry("suburb",       5),
            Map.entry("quarter",      5),
            Map.entry("neighbourhood",6),
            Map.entry("locality",     6)
    );

    /** 无 place 标签或未知类型的默认优先级 */
    private static final int DEFAULT_PRIORITY = 7;

    /** 最大优先级（用于 zoom 筛选：priority <= threshold 才显示） */
    public static final int MAX_PRIORITY = DEFAULT_PRIORITY;

    // ── 工厂方法 ──

    /**
     * 从 OSM 标签创建 NamedPlace。
     * @param lon  经度
     * @param lat  纬度
     * @param tags OSM 标签映射（key → value）
     * @return NamedPlace 或 null（无 name 标签时）
     */
    public static NamedPlace fromTags(double lon, double lat, Map<String, String> tags) {
        String name = tags.get("name");
        if (name == null || name.isBlank()) return null;

        // 优先使用中文名，fallback 到 name
        String nameZh = tags.get("name:zh");
        if (nameZh != null && !nameZh.isBlank()) {
            name = nameZh;
        }

        String place = tags.get("place");
        int priority = place != null
                ? PLACE_PRIORITY.getOrDefault(place, DEFAULT_PRIORITY)
                : DEFAULT_PRIORITY;

        return new NamedPlace(lon, lat, name, place, priority);
    }
}
