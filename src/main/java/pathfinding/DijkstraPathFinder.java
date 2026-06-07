package pathfinding;

import java.util.*;

/**
 * 基于 Dijkstra 算法的最短路径搜索实现。
 * <p>
 * 算法流程：
 * <ol>
 *   <li>将起点距离设为 0，其余节点距离设为无穷大。</li>
 *   <li>使用优先队列，每次取出距离最小的节点。</li>
 *   <li>松弛该节点的所有邻边，更新邻接节点的最短距离和前驱。</li>
 *   <li>到达终点节点时提前终止。</li>
 *   <li>通过前驱 Map 反向重建完整路径。</li>
 * </ol>
 * <p>
 * 时间复杂度：O((V + E) log V)，适合大规模路网（数百万节点）。
 * <p>
 * 边权重 = {@link RoadGraph.Edge#lengthMeters()} × {@link PathSpeedConfig#getFactor(String)}，
 * 通过 highway 类型的耗时乘数实现对不同道路的偏好选择。
 */
public class DijkstraPathFinder implements PathFinder {

    /**
     * 优先队列元素：（节点 ID，从起点到该节点的当前最短距离）。
     */
    private record NodeDist(long nodeId, double dist) implements Comparable<NodeDist> {
        @Override
        public int compareTo(NodeDist other) {
            return Double.compare(this.dist, other.dist);
        }
    }

    @Override
    public PathResult findPath(RoadGraph graph, long startNodeId, long endNodeId) {
        // 起点或终点不在图中
        if (!graph.getNodeIds().contains(startNodeId) || !graph.getNodeIds().contains(endNodeId)) {
            return PathResult.unreachable();
        }

        // 起点 == 终点：距离为 0，路径仅包含该节点
        if (startNodeId == endNodeId) {
            return new PathResult(List.of(startNodeId), 0.0);
        }

        PathSpeedConfig speedConfig = PathSpeedConfig.getInstance();

        // ── Dijkstra 核心数据结构 ──
        Map<Long, Double> dist = new HashMap<>();
        Map<Long, Long> prev = new HashMap<>();
        PriorityQueue<NodeDist> pq = new PriorityQueue<>();
        Set<Long> settled = new HashSet<>();

        dist.put(startNodeId, 0.0);
        pq.add(new NodeDist(startNodeId, 0.0));

        // ── 主循环 ──
        while (!pq.isEmpty()) {
            NodeDist current = pq.poll();
            long u = current.nodeId;

            if (settled.contains(u)) continue;
            if (u == endNodeId) break;

            settled.add(u);

            for (RoadGraph.Edge edge : graph.getEdges(u)) {
                long v = edge.targetId();
                if (settled.contains(v)) continue;

                double weight = edge.lengthMeters() * speedConfig.getFactor(edge.highwayType());
                double alt = dist.get(u) + weight;
                double oldDist = dist.getOrDefault(v, Double.POSITIVE_INFINITY);

                if (alt < oldDist) {
                    dist.put(v, alt);
                    prev.put(v, u);
                    pq.add(new NodeDist(v, alt));
                }
            }
        }

        // ── 检查终点是否可达 ──
        if (!dist.containsKey(endNodeId)) {
            return PathResult.unreachable();
        }

        // ── 反向重建路径 ──
        double totalDist = dist.get(endNodeId);
        List<Long> path = new ArrayList<>();
        for (Long node = endNodeId; node != null; node = prev.get(node)) {
            path.add(node);
        }
        Collections.reverse(path);

        return new PathResult(path, totalDist);
    }
}
