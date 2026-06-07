package pathfinding;

import java.util.*;

/**
 * 道路网络图，供 PathFinder 进行最短路径搜索。
 * <p>
 * 图的边是<b>双向</b>的：addEdge(a, b, len) 会自动在两个方向上都添加邻接关系。
 * <p>
 * 数据来源：从 EnhancedOsmLayerSink 收集的 {@code highwayWayNodes} 和 {@code allNodes}
 * 构建而成。
 */
public class RoadGraph {

    /**
     * 图的一条边，包含目标节点 ID、边长（米）以及所属道路的 highway 类型。
     */
    public record Edge(long targetId, double lengthMeters, String highwayType) {
        @Override
        public String toString() {
            return "->" + targetId + "(" + String.format("%.1f", lengthMeters) + "m, " + highwayType + ")";
        }
    }

    /** 邻接表：节点 ID → 出边列表 */
    private final Map<Long, List<Edge>> adjacency = new HashMap<>();

    /** 节点坐标：节点 ID → [lon, lat]（可选，若未提供则为 null） */
    private final Map<Long, double[]> nodeCoords;

    // ==================== 构造 ====================

    public RoadGraph() {
        this.nodeCoords = new HashMap<>();
    }

    /**
     * @param nodeCoords 节点坐标映射（来自 EnhancedOsmLayerSink.getNodeMap()），
     *                   若仅用于寻路可不传，传 null 等价于调用无参构造。
     */
    public RoadGraph(Map<Long, double[]> nodeCoords) {
        this.nodeCoords = nodeCoords != null ? new HashMap<>(nodeCoords) : new HashMap<>();
    }

    // ==================== 图操作 ====================

    /**
     * 添加一条<b>双向</b>边，默认为 "path" 类型。
     *
     * @param fromId       起点节点 ID
     * @param toId         终点节点 ID
     * @param lengthMeters 边长（单位：米）
     */
    public void addEdge(long fromId, long toId, double lengthMeters) {
        addEdge(fromId, toId, lengthMeters, "path");
    }

    /**
     * 添加一条<b>双向</b>边，并指定 highway 类型。
     *
     * @param fromId       起点节点 ID
     * @param toId         终点节点 ID
     * @param lengthMeters 边长（单位：米）
     * @param highwayType  OSM highway 标签值（如 "residential", "footway"）
     */
    public void addEdge(long fromId, long toId, double lengthMeters, String highwayType) {
        adjacency.computeIfAbsent(fromId, k -> new ArrayList<>()).add(new Edge(toId, lengthMeters, highwayType));
        adjacency.computeIfAbsent(toId, k -> new ArrayList<>()).add(new Edge(fromId, lengthMeters, highwayType));
    }

    /**
     * 获取从指定节点出发的所有边。
     *
     * @param nodeId 节点 ID
     * @return 出边列表（不可修改），若节点不存在则返回空列表
     */
    public List<Edge> getEdges(long nodeId) {
        return Collections.unmodifiableList(adjacency.getOrDefault(nodeId, List.of()));
    }

    /**
     * @return 图中所有节点 ID（不可修改的 Set 视图）
     */
    public Set<Long> getNodeIds() {
        return Collections.unmodifiableSet(adjacency.keySet());
    }

    /** @return 图中节点总数 */
    public int getNodeCount() {
        return adjacency.size();
    }

    /** @return 图中边的总数（无向边计为一条，此处计为有向边数的一半） */
    public int getEdgeCount() {
        int total = 0;
        for (List<Edge> edges : adjacency.values()) {
            total += edges.size();
        }
        return total / 2;
    }

    /**
     * @param nodeId 节点 ID
     * @return 该节点的坐标 [lon, lat]，若未存储则返回 null
     */
    public double[] getCoord(long nodeId) {
        return nodeCoords.get(nodeId);
    }

    // ==================== 构建工具 ====================

    /**
     * 通过 WaySegment 列表批量构建图，边带上 highway 类型。
     * <p>
     * 每个 {@link WaySegment} 是一条道路的节点序列及 highway 类型，相邻两两组成一条边，
     * 边长通过 Haversine 公式根据坐标计算。
     *
     * @param waySegments 道路段列表（来自 EnhancedOsmLayerSink.getHighwayWayNodeSequences()）
     * @param nodeMap     全体节点坐标（来自 EnhancedOsmLayerSink.getNodeMap()）
     * @return 构建好的 RoadGraph
     */
    public static RoadGraph fromWayNodeSequences(List<WaySegment> waySegments,
                                                  Map<Long, double[]> nodeMap) {
        RoadGraph graph = new RoadGraph(nodeMap);
        for (WaySegment seg : waySegments) {
            long[] ids = seg.nodeIds();
            String hwType = seg.highwayType();
            for (int i = 0; i < ids.length - 1; i++) {
                long a = ids[i];
                long b = ids[i + 1];
                double[] ca = nodeMap.get(a);
                double[] cb = nodeMap.get(b);
                if (ca == null || cb == null) continue;
                double dist = haversine(ca[0], ca[1], cb[0], cb[1]);
                graph.addEdge(a, b, dist, hwType);
            }
        }
        return graph;
    }

    /**
     * {@link #fromWayNodeSequences(List, Map)} 的别名，方便 Main 调用。
     */
    public static RoadGraph build(List<WaySegment> waySegments,
                                  Map<Long, double[]> nodeMap) {
        return fromWayNodeSequences(waySegments, nodeMap);
    }

    /**
     * Haversine 公式计算两点间地表距离。
     *
     * @return 距离，单位：米
     */
    private static double haversine(double lon1, double lat1, double lon2, double lat2) {
        double R = 6_371_000.0; // 地球半径（米）
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                        * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }

    @Override
    public String toString() {
        return "RoadGraph{nodes=" + getNodeCount() + ", edges=" + getEdgeCount() + "}";
    }
}
