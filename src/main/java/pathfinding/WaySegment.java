package pathfinding;

/**
 * 一条道路的节点序列及其 highway 类型。
 * <p>
 * 用于 {@link RoadGraph#build} 构建路网图时为每条边标注道路类型。
 *
 * @param nodeIds      节点 ID 序列（按道路方向排列）
 * @param highwayType  OSM highway 标签值（如 "residential", "footway"），
 *                     若 way 无 highway 标签则为 "path"
 */
public record WaySegment(long[] nodeIds, String highwayType) {
}
