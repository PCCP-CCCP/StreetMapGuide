package gui;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.util.*;
import java.util.List;

import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import pathfinding.PathResult;
import pathfinding.RoadGraph;

/**
 * 地图绘制面板 — 显示路网、支持鼠标点选起点/终点。
 * <p>
 * 绘制层次（从底到顶）：
 * <ol>
 *   <li>背景（浅灰）</li>
 *   <li>所有道路边（浅灰细线）</li>
 *   <li>最短路径边（粗红色）</li>
 *   <li>起点标记（绿色实心圆）</li>
 *   <li>终点标记（红色实心圆）</li>
 * </ol>
 */
public class MapPanel extends JPanel {

    /** 当前道路图 */
    private RoadGraph graph;

    /** 经纬度→像素投影 */
    private MapProjection projection;

    /** 起点 OSM 节点 ID，-1 表示未选 */
    private long startNodeId = -1;

    /** 终点 OSM 节点 ID，-1 表示未选 */
    private long endNodeId = -1;

    /** 当前路径结果，null 表示无路径 */
    private PathResult pathResult;

    /** true=正在选起点，false=正在选终点 */
    private boolean selectingStart = true;

    /** 预计算的边线段缓存（像素坐标 + highway 类型），projection 变化时重建 */
    private List<CachedSegment> cachedEdgeSegments;

    /** 边缓存条目：屏幕像素起终点 + highway 类型 */
    private record CachedSegment(int x1, int y1, int x2, int y2, String highwayType) {}

    /** 地名标注列表（从 EnhancedOsmLayerSink 获取） */
    private List<NamedPlace> namedPlaces = Collections.emptyList();

    // ==================== 颜色常量 ====================

    private static final Color BG_COLOR        = new Color(245, 245, 245);
    private static final Color EDGE_COLOR      = new Color(200, 200, 200);
    private static final Color PATH_COLOR      = new Color(220, 40, 40);
    private static final Color START_COLOR     = new Color(40, 180, 40);
    private static final Color END_COLOR       = new Color(220, 40, 40);
    private static final Color NODE_DOT_COLOR  = new Color(160, 160, 160);

    private static final float EDGE_STROKE_WIDTH = 0.5f;
    private static final float PATH_STROKE_WIDTH = 3.0f;
    private static final int   MARKER_RADIUS     = 7;
    private static final int   CLICK_THRESHOLD   = 8; // 鼠标点击最近节点搜索半径（像素）

    // 地名标注样式
    private static final Font  PLACE_NAME_FONT         = new Font("Microsoft YaHei", Font.PLAIN, 10);
    private static final Color PLACE_NAME_COLOR        = new Color(80, 80, 80, 180);
    private static final Color PLACE_NAME_OUTLINE      = new Color(255, 255, 255, 160);

    // ==================== 构造 ====================

    public MapPanel() {
        setBackground(BG_COLOR);
        setPreferredSize(new Dimension(1000, 800));

        // ── 鼠标处理器（点击选点 + 左键拖拽平移） ──
        MouseAdapter mouseHandler = new MouseAdapter() {
            private int dragStartX, dragStartY;

            @Override
            public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e)) {
                    dragStartX = e.getX();
                    dragStartY = e.getY();
                }
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (!SwingUtilities.isLeftMouseButton(e)) return;
                if (projection == null) return;
                int dx = e.getX() - dragStartX;
                int dy = e.getY() - dragStartY;
                if (dx != 0 || dy != 0) {
                    projection.addOffset(dx, dy);
                    dragStartX = e.getX();
                    dragStartY = e.getY();
                    rebuildEdgeCache();
                    repaint();
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                // 拖拽结束，无额外操作
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                // Swing 保证 mouseClicked 仅在无显著拖拽时触发
                handleMouseClick(e.getX(), e.getY());
            }
        };

        addMouseListener(mouseHandler);
        addMouseMotionListener(mouseHandler);

        // ── 鼠标滚轮缩放 ──
        addMouseWheelListener(this::handleMouseWheel);
    }

    // ==================== 数据设置 ====================

    public void setGraph(RoadGraph graph) {
        this.graph = graph;
        rebuildEdgeCache();
        repaint();
    }

    public void setProjection(MapProjection projection) {
        this.projection = projection;
        rebuildEdgeCache();
        repaint();
    }

    /** 设置地名标注数据 */
    public void setNamedPlaces(List<NamedPlace> places) {
        this.namedPlaces = places != null ? places : Collections.emptyList();
        repaint();
    }

    public void setStartNode(long nodeId) {
        this.startNodeId = nodeId;
        repaint();
    }

    public void setEndNode(long nodeId) {
        this.endNodeId = nodeId;
        repaint();
    }

    public long getStartNodeId() { return startNodeId; }
    public long getEndNodeId()   { return endNodeId; }

    public void setPathResult(PathResult result) {
        this.pathResult = result;
        repaint();
    }

    /** 清除起点、终点和路径 */
    public void clearSelection() {
        this.startNodeId = -1;
        this.endNodeId = -1;
        this.pathResult = null;
        repaint();
    }

    /** 切换选择模式 */
    public void setSelectingStart(boolean b) { this.selectingStart = b; }
    public boolean isSelectingStart()         { return selectingStart; }

    /**
     * @return 当前选择模式对应的节点 ID（选起点→startNodeId，选终点→endNodeId）
     */
    public long getCurrentSelectionNodeId() {
        return selectingStart ? startNodeId : endNodeId;
    }

    // ==================== 鼠标交互 ====================

    /**
     * 鼠标点击 → 反算经纬度 → 在 RoadGraph 中查找最近节点 → 设为起点/终点。
     */
    private void handleMouseClick(int px, int py) {
        if (graph == null || projection == null) return;

        // 遍历所有节点，找像素距离最近的
        long bestId = -1;
        double bestDistSq = Double.POSITIVE_INFINITY;

        for (long nodeId : graph.getNodeIds()) {
            double[] coord = graph.getCoord(nodeId);
            if (coord == null) continue;
            Point pp = projection.toPixel(coord[0], coord[1]);
            double dx = pp.x - px;
            double dy = pp.y - py;
            double distSq = dx * dx + dy * dy;
            if (distSq < bestDistSq) {
                bestDistSq = distSq;
                bestId = nodeId;
            }
        }

        if (bestId >= 0 && Math.sqrt(bestDistSq) <= CLICK_THRESHOLD) {
            if (selectingStart) {
                startNodeId = bestId;
            } else {
                endNodeId = bestId;
            }
            // 选点后清除旧路径
            pathResult = null;
            repaint();
        }
    }

    /**
     * 鼠标滚轮缩放：向上滚动（<0）放大，向下滚动（>0）缩小。
     * 缩放以地图中心为锚点，每次步进 1.1 倍，范围 [0.1, 50.0]。
     */
    private void handleMouseWheel(MouseWheelEvent e) {
        if (projection == null) return;

        double newZoom = projection.getZoom();
        if (e.getWheelRotation() < 0) {
            newZoom *= 1.1;  // 放大
        } else {
            newZoom /= 1.1;  // 缩小
        }
        // 钳位
        if (newZoom < 0.1) newZoom = 0.1;
        if (newZoom > 50.0) newZoom = 50.0;

        if (Math.abs(newZoom - projection.getZoom()) > 1e-9) {
            projection.setZoom(newZoom);
            rebuildEdgeCache();
            repaint();
        }
    }

    // ==================== 绘制 ====================

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        drawEdges(g2);
        drawPath(g2);
        drawPlaceNames(g2);
        drawMarkers(g2);
    }

    /**
     * 绘制所有道路边：根据 highway 类型使用 PathColorConfig 配置的颜色。
     */
    private void drawEdges(Graphics2D g2) {
        if (cachedEdgeSegments == null || cachedEdgeSegments.isEmpty()) return;

        PathColorConfig colorCfg = PathColorConfig.getInstance();
        g2.setStroke(new BasicStroke(EDGE_STROKE_WIDTH));

        for (CachedSegment seg : cachedEdgeSegments) {
            g2.setColor(colorCfg.getColor(seg.highwayType));
            g2.drawLine(seg.x1, seg.y1, seg.x2, seg.y2);
        }
    }

    /** 绘制最短路径（粗红色） */
    private void drawPath(Graphics2D g2) {
        if (pathResult == null || !pathResult.isReachable()
                || graph == null || projection == null) return;

        List<Long> nodeIds = pathResult.getNodeIds();
        if (nodeIds.size() < 2) return;

        g2.setColor(PATH_COLOR);
        g2.setStroke(new BasicStroke(PATH_STROKE_WIDTH,
                BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

        for (int i = 0; i < nodeIds.size() - 1; i++) {
            double[] ca = graph.getCoord(nodeIds.get(i));
            double[] cb = graph.getCoord(nodeIds.get(i + 1));
            if (ca == null || cb == null) continue;
            Point pa = projection.toPixel(ca[0], ca[1]);
            Point pb = projection.toPixel(cb[0], cb[1]);
            g2.drawLine(pa.x, pa.y, pb.x, pb.y);
        }
    }

    /** 绘制起点/终点标记 */
    private void drawMarkers(Graphics2D g2) {
        if (graph == null || projection == null) return;

        if (startNodeId >= 0) {
            drawMarker(g2, startNodeId, START_COLOR);
        }
        if (endNodeId >= 0) {
            drawMarker(g2, endNodeId, END_COLOR);
        }
    }

    private void drawMarker(Graphics2D g2, long nodeId, Color color) {
        double[] coord = graph.getCoord(nodeId);
        if (coord == null) return;
        Point p = projection.toPixel(coord[0], coord[1]);

        g2.setColor(color);
        g2.fillOval(p.x - MARKER_RADIUS, p.y - MARKER_RADIUS,
                MARKER_RADIUS * 2, MARKER_RADIUS * 2);
        g2.setColor(Color.DARK_GRAY);
        g2.drawOval(p.x - MARKER_RADIUS, p.y - MARKER_RADIUS,
                MARKER_RADIUS * 2, MARKER_RADIUS * 2);
    }

    /**
     * 绘制地名标注：根据 zoom 级别筛选优先级，贪心碰撞检测避免文字重叠。
     */
    private void drawPlaceNames(Graphics2D g2) {
        if (namedPlaces.isEmpty() || projection == null) return;

        double zoom = projection.getZoom();
        int maxPriority = zoomToMaxPriority(zoom);

        g2.setFont(PLACE_NAME_FONT);
        FontMetrics fm = g2.getFontMetrics();

        List<Rectangle> drawnBounds = new ArrayList<>();
        int padding = 3; // 文字矩形额外留白

        for (NamedPlace place : namedPlaces) {
            if (place.priority() > maxPriority) continue;

            Point p = projection.toPixel(place.lon(), place.lat());

            // 过滤完全在画布外的点
            if (p.x < -200 || p.x > getWidth() + 200
                    || p.y < -200 || p.y > getHeight() + 200) {
                continue;
            }

            String text = place.name();
            int textW = fm.stringWidth(text);
            int textH = fm.getAscent();

            // 文字左下角坐标（使文字居中于定位点上方）
            int tx = p.x - textW / 2;
            int ty = p.y - 2;

            Rectangle bounds = new Rectangle(tx - padding, ty - textH - padding,
                    textW + 2 * padding, textH + 2 * padding);

            // 贪心碰撞检测
            boolean collides = false;
            for (Rectangle drawn : drawnBounds) {
                if (drawn.intersects(bounds)) {
                    collides = true;
                    break;
                }
            }
            if (collides) continue;

            drawnBounds.add(bounds);

            // 绘制白色描边（提高可读性）
            g2.setColor(PLACE_NAME_OUTLINE);
            g2.drawString(text, tx - 1, ty);
            g2.drawString(text, tx + 1, ty);
            g2.drawString(text, tx, ty - 1);
            g2.drawString(text, tx, ty + 1);

            // 绘制半透明灰色文字
            g2.setColor(PLACE_NAME_COLOR);
            g2.drawString(text, tx, ty);
        }
    }

    /**
     * 根据 zoom 级别返回可显示的最大优先级（priority <= 返回值才绘制）。
     */
    private static int zoomToMaxPriority(double zoom) {
        if (zoom < 0.3)  return 1;
        if (zoom < 0.8)  return 2;
        if (zoom < 2.0)  return 3;
        if (zoom < 5.0)  return 4;
        if (zoom < 12.0) return 5;
        return NamedPlace.MAX_PRIORITY;
    }

    // ==================== 边缓存 ====================

    /**
     * 预计算所有边的像素坐标，避免 paintComponent 中重复转换。
     * 在 projection 或 graph 变更时调用。
     */
    private void rebuildEdgeCache() {
        if (graph == null || projection == null) {
            cachedEdgeSegments = null;
            return;
        }

        List<CachedSegment> segments = new ArrayList<>();
        Set<String> drawn = new HashSet<>(); // 去重（无向边只画一次）

        for (long fromId : graph.getNodeIds()) {
            double[] cf = graph.getCoord(fromId);
            if (cf == null) continue;
            Point pf = projection.toPixel(cf[0], cf[1]);

            for (RoadGraph.Edge edge : graph.getEdges(fromId)) {
                long toId = edge.targetId();
                // 去重：只画 fromId < toId 的边
                if (fromId >= toId) continue;

                double[] ct = graph.getCoord(toId);
                if (ct == null) continue;
                Point pt = projection.toPixel(ct[0], ct[1]);

                segments.add(new CachedSegment(pf.x, pf.y, pt.x, pt.y, edge.highwayType()));
            }
        }

        this.cachedEdgeSegments = segments;
    }
}
