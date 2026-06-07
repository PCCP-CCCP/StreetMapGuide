import java.io.File;
import java.util.*;

import javax.swing.SwingUtilities;

import crosby.binary.osmosis.OsmosisReader;

import gui.MapProjection;
import gui.MainFrame;
import gui.NamedPlace;
import nl.tudelft.simulation.dsol.animation.gis.transform.CoordinateTransform;
import nl.tudelft.simulation.dsol.animation.gis.map.Layer;
import pathfinding.PathSpeedConfig;
import pathfinding.RoadGraph;
import pathfinding.WaySegment;

/**
 * 使用 nl.tudelft.simulation.dsol.animation.gis.osm 库解析 OSM PBF 文件，
 * 将地图要素按图层分类：点要素图层（Nodes）、线要素图层（非闭合Ways）、面要素图层（闭合Ways + 多边形Relations）。
 * <p>
 * 核心模型（均为 dsol GIS 库提供的类）：
 * - {@link Layer}    — 图层（点图层/线图层/面图层），包含多个要素
 * - {@link Feature}  — 要素分类（如 highway、building、amenity），包含多个几何对象
 * - {@link GisObject} — 几何对象（封装 {@link SerializablePath} 形状 + 属性数组）
 */
public class Main {

    /**
     * 主入口：解析 PBF → 构建路网图 → 启动 GUI。
     */
    public static void main(String[] args) throws Exception {
        String pbfPath = "src/main/resources/jiangxi-map.osm.pbf";

        System.out.println("╔══════════════════════════════════════════════╗");
        System.out.println("║   StreetMapGuide — OSM 地图路径规划           ║");
        System.out.println("╚══════════════════════════════════════════════╝");
        System.out.println("\n文件: " + pbfPath);

        long startTime = System.currentTimeMillis();

        // ── 1. 使用 EnhancedOsmLayerSink 解析 PBF ──
        Layer pointLayer   = new Layer();
        pointLayer.setName("Point");
        Layer lineLayer    = new Layer();
        lineLayer.setName("Line");
        Layer polygonLayer = new Layer();
        polygonLayer.setName("Polygon");

        EnhancedOsmLayerSink sink = new EnhancedOsmLayerSink(
                pointLayer, lineLayer, polygonLayer,
                EnhancedOsmLayerSink.createStandardFeatureDefs(),
                new CoordinateTransform.NoTransform());

        OsmosisReader reader = new OsmosisReader(new File(pbfPath));
        reader.setSink(sink);
        reader.run(); // 同步解析

        long parseTime = System.currentTimeMillis() - startTime;
        System.out.println("\n[解析完成] 耗时: " + parseTime + " ms");
        System.out.println("  Node 总数:     " + fmt(sink.getTotalNodes()));
        System.out.println("  Way 总数:      " + fmt(sink.getTotalWays()));
        System.out.println("  Relation 总数: " + fmt(sink.getTotalRelations()));
        System.out.println("  点要素:        " + fmt(sink.getPointCount()) + " (图层中)");
        System.out.println("  线要素:        " + fmt(sink.getLineCount()) + " (图层中)");
        System.out.println("  面要素:        " + fmt(sink.getPolygonCount()) + " (图层中)");

        // ── 2. 获取路网数据 ──
        Map<Long, double[]> nodeMap = sink.getNodeMap();
        List<WaySegment> highwaySeqs = sink.getHighwayWayNodeSequences();
        List<NamedPlace> namedPlaces = sink.getNamedPlaces();
        System.out.println("\n[路网] 全部节点: " + fmt(nodeMap.size())
                + ", 可步行道路段: " + fmt(highwaySeqs.size())
                + ", 地名标注: " + fmt(namedPlaces.size()));

        // ── 触发 PathSpeedConfig 初始化（首次调用加载配置） ──
        PathSpeedConfig speedCfg = PathSpeedConfig.getInstance();
        System.out.println("[速度因子] 已加载 " + speedCfg.getFactors().size() + " 种道路类型");

        // ── 3. 构建路网图 ──
        long buildStart = System.currentTimeMillis();
        RoadGraph graph = RoadGraph.build(highwaySeqs, nodeMap);
        long buildTime = System.currentTimeMillis() - buildStart;
        System.out.println("[路网] 构建完成: " + graph + ", 耗时: " + buildTime + " ms");

        // ── 4. 计算经纬度边界 ──
        double[] bounds = computeBounds(nodeMap);
        System.out.printf("[边界] lon=[%.4f, %.4f], lat=[%.4f, %.4f]%n",
                bounds[0], bounds[1], bounds[2], bounds[3]);

        // ── 5. 创建投影 ──
        MapProjection projection = new MapProjection(
                bounds[0], bounds[1], bounds[2], bounds[3], 1000, 800);

        // ── 6. 启动 GUI（EDT 线程） ──
        System.out.println("\n[GUI] 启动中...");
        SwingUtilities.invokeLater(() -> {
            MainFrame frame = new MainFrame(graph, projection, namedPlaces);
            frame.setVisible(true);
        });

        long totalTime = System.currentTimeMillis() - startTime;
        System.out.println("[总耗时] " + totalTime + " ms");
    }

    /** 遍历全部节点坐标，计算 min/max 经纬度。 */
    private static double[] computeBounds(Map<Long, double[]> nodeMap) {
        double minLon = Double.POSITIVE_INFINITY, maxLon = Double.NEGATIVE_INFINITY;
        double minLat = Double.POSITIVE_INFINITY, maxLat = Double.NEGATIVE_INFINITY;

        for (double[] coord : nodeMap.values()) {
            if (coord[0] < minLon) minLon = coord[0];
            if (coord[0] > maxLon) maxLon = coord[0];
            if (coord[1] < minLat) minLat = coord[1];
            if (coord[1] > maxLat) maxLat = coord[1];
        }
        return new double[]{minLon, maxLon, minLat, maxLat};
    }

    private static String fmt(int n) {
        return String.format("%,d", n);
    }
}
