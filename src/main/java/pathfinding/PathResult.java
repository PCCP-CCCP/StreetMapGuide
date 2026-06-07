package pathfinding;

import java.util.Collections;
import java.util.List;

/**
 * 最短路径搜索结果。
 * <ul>
 *   <li>若存在可达路径：{@code nodeIds} 包含从起点到终点的完整节点 ID 序列，
 *       {@code totalDistance} 为总距离（米）。</li>
 *   <li>若无可达路径：{@code nodeIds} 为空列表，{@code totalDistance} 为 -1.0。</li>
 * </ul>
 */
public class PathResult {

    /** 从起点到终点的节点 ID 序列，无法到达时为空列表 */
    private final List<Long> nodeIds;

    /** 总距离（米），无法到达时为 -1.0 */
    private final double totalDistance;

    // ==================== 构造 ====================

    /** 构造一条可达路径 */
    public PathResult(List<Long> nodeIds, double totalDistance) {
        this.nodeIds = Collections.unmodifiableList(nodeIds);
        this.totalDistance = totalDistance;
    }

    /** 构造一条不可达结果 */
    public static PathResult unreachable() {
        return new PathResult(List.of(), -1.0);
    }

    // ==================== 查询 ====================

    /** @return 从起点到终点的节点 ID 序列，无法到达时为空列表 */
    public List<Long> getNodeIds() {
        return nodeIds;
    }

    /** @return 总距离（米），无法到达时为 -1.0 */
    public double getTotalDistance() {
        return totalDistance;
    }

    /** @return 是否可达 */
    public boolean isReachable() {
        return totalDistance >= 0;
    }

    /** @return 路径上的节点数量 */
    public int getNodeCount() {
        return nodeIds.size();
    }

    // ==================== 输出 ====================

    @Override
    public String toString() {
        if (!isReachable()) {
            return "PathResult{unreachable}";
        }
        return String.format("PathResult{nodes=%d, distance=%.1f m, ids=%s}",
                nodeIds.size(), totalDistance, nodeIds);
    }
}
