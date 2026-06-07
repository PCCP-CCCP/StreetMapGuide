package pathfinding;

/**
 * 最短路径搜索器接口。
 * <p>
 * 实现类需要在给定的 {@link RoadGraph} 中计算从起点到终点的最短路径。
 */
@FunctionalInterface
public interface PathFinder {

    /**
     * 在道路图中寻找从起点到终点的最短路径。
     *
     * @param graph       道路网络图
     * @param startNodeId 起点 OSM 节点 ID
     * @param endNodeId   终点 OSM 节点 ID
     * @return 路径结果，若不可达则 {@link PathResult#isReachable()} 为 false
     */
    PathResult findPath(RoadGraph graph, long startNodeId, long endNodeId);
}
