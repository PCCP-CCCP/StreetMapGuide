// ===================================================================================
// 导入声明
// ===================================================================================

// ---- JDK 标准库 ----
import java.awt.Color;
import java.util.*;

// ---- 本项目模块 ----
import gui.NamedPlace;
import pathfinding.WaySegment;

// ---- Osmosis 核心（OSM 数据模型与 Sink 接口） ----
import org.openstreetmap.osmosis.core.container.v0_6.EntityContainer;
import org.openstreetmap.osmosis.core.domain.v0_6.Entity;
import org.openstreetmap.osmosis.core.domain.v0_6.Node;
import org.openstreetmap.osmosis.core.domain.v0_6.Relation;
import org.openstreetmap.osmosis.core.domain.v0_6.RelationMember;
import org.openstreetmap.osmosis.core.domain.v0_6.Tag;
import org.openstreetmap.osmosis.core.domain.v0_6.Way;
import org.openstreetmap.osmosis.core.domain.v0_6.WayNode;
import org.openstreetmap.osmosis.core.task.v0_6.Sink;

// ---- dsol GIS 库（图层、要素、几何对象、坐标变换） ----
import nl.tudelft.simulation.dsol.animation.gis.FeatureInterface;
import nl.tudelft.simulation.dsol.animation.gis.GisObject;
import nl.tudelft.simulation.dsol.animation.gis.SerializablePath;
import nl.tudelft.simulation.dsol.animation.gis.map.Feature;
import nl.tudelft.simulation.dsol.animation.gis.map.Layer;
import nl.tudelft.simulation.dsol.animation.gis.transform.CoordinateTransform;

/**
 * EnhancedOsmLayerSink — 增强版 OSM 实体处理器，实现 Osmosis Sink 接口。
 *
 * <h3>设计思路</h3>
 * <p>
 * 本类作为 {@code crosby.binary.osmosis.OsmosisReader} 的 Sink 接收端，逐实体
 * 接收 PBF 文件解析出的 Node/Way/Relation，按预定义的 {@link OsmFeatureDef} 要素
 * 匹配规则将其分类到三个图层（点/线/面），同时额外提取路网拓扑数据和地名标注。
 *
 * <h3>核心能力</h3>
 * <ul>
 *   <li><b>点要素</b>：独立带标签的 Node（如 amenity=restaurant, place=city）
 *       匹配 GeometryType.POINT 规则</li>
 *   <li><b>线要素</b>：非闭合 Way（如 highway=primary, waterway=river）
 *       匹配 GeometryType.LINE 规则</li>
 *   <li><b>面要素</b>：闭合 Way（首尾节点ID相同）+ multipolygon Relation
 *       （如 building=yes, landuse=forest）匹配 GeometryType.POLYGON 规则；
 *       AUTO 类型根据闭合性自动判断</li>
 *   <li><b>路网提取</b>：highway=* 标签的 Way 存入 highwayWayNodes，
 *       供 RoadGraph.build() 构建寻路拓扑，保留 highway 类型信息</li>
 *   <li><b>地名标注</b>：有 name 标签的 Node 自动收集为 NamedPlace 列表，
 *       供 MapPanel 根据 zoom 级别动态显示</li>
 * </ul>
 *
 * <h3>处理流程（三阶段）</h3>
 * <ol>
 *   <li><b>process() 阶段</b>：逐实体接收
 *     <ul>
 *       <li>Node → 存入 nodeMap/allNodes 坐标缓存，匹配POINT规则后立即添加为点要素，
 *           同时收集 name 标签</li>
 *       <li>Way → highway 标签收集到 highwayWayNodes；其余匹配要素定义后暂存 pendingWays，
 *           等待 complete() 阶段根据闭合性分类</li>
 *       <li>Relation → 仅关注 type=multipolygon/boundary，暂存 relationMembers</li>
 *     </ul>
 *   </li>
 *   <li><b>complete() 阶段</b>：延迟分类
 *     <ul>
 *       <li>收集被 multipolygon 引用的 Way ID → relationWayIds</li>
 *       <li>未被引用的 Way → classifyAndAddWay() 根据闭合性分到线/面图层</li>
 *       <li>multipolygon 的 outer 成员 → 添加到面图层</li>
 *     </ul>
 *   </li>
 *   <li><b>数据提取阶段</b>（外部调用 getter）：
 *     getNodeMap()、getHighwayWayNodeSequences()、getNamedPlaces()</li>
 * </ol>
 *
 * <h3>关键设计决策</h3>
 * <ul>
 *   <li><b>延迟分类</b>：Way 在 process() 中仅暂存，因为分类依赖完整坐标数据（判断闭合性）
 *       和 multipolygon 归属（complete() 阶段才能确定）</li>
 *   <li><b>AUTO 几何推断</b>：若 OsmFeatureDef 未指定 POINT/LINE/POLYGON，
 *       则根据 Way 首尾节点 ID 是否相同自动判断</li>
 *   <li><b>Feature 缓存复用</b>：通过 featureCache（Layer → key → value → Feature）
 *       三级映射避免重复创建，同一 key+value 的所有 GisObject 归入同一个 Feature</li>
 *   <li><b>路网独立收集</b>：highwayWayNodes 独立于图层分类逻辑，即使 highway 不在
 *       要素定义列表中也会收集，确保路网构建不依赖要素匹配规则</li>
 * </ul>
 *
 * <h3>使用方式</h3>
 * <pre>{@code
 * EnhancedOsmLayerSink sink = new EnhancedOsmLayerSink(
 *     pointLayer, lineLayer, polygonLayer,
 *     createStandardFeatureDefs(), new CoordinateTransform.NoTransform());
 * OsmosisReader reader = new OsmosisReader(new File("map.osm.pbf"));
 * reader.setSink(sink);
 * reader.run(); // 同步阻塞，解析完成后图层已填充 + 可提取路网/地名数据
 * }</pre>
 *
 * @see Sink
 * @see OsmFeatureDef
 * @see nl.tudelft.simulation.dsol.animation.gis.map.Layer
 */
public class EnhancedOsmLayerSink implements Sink {

    // ==================== 要素定义 ====================

    /**
     * OSM 要素的几何类型。
     * <p>
     * 决定实体被分类到哪个图层：
     * <ul>
     *   <li>{@link #POINT}   — 点图层（独立带标签的 Node）</li>
     *   <li>{@link #LINE}    — 线图层（非闭合 Way）</li>
     *   <li>{@link #POLYGON} — 面图层（闭合 Way 或 multipolygon Relation）</li>
     *   <li>{@link #AUTO}    — 解析时自动判断：Node→POINT，闭合Way→POLYGON，非闭合Way→LINE</li>
     * </ul>
     *
     * @see OsmFeatureDef
     */
    public enum GeometryType {
        /** 点要素 — 独立带标签的 Node（如 amenity=restaurant） */
        POINT,
        /** 线要素 — 非闭合 Way（如 highway=primary） */
        LINE,
        /** 面要素 — 闭合 Way 或 multipolygon Relation（如 building=yes） */
        POLYGON,
        /**
         * 自动判断 — 解析时根据实体类型和闭合性推断几何类型：
         * Node 视为 POINT，闭合 Way 视为 POLYGON，非闭合 Way 视为 LINE。
         */
        AUTO
    }

    /**
     * OSM 要素匹配规则。
     * <p>
     * 每条规则定义了 {key, value, geometryType} 三元组：当实体标签中某条
     * tag 的 key 匹配此规则的 key、value 匹配此规则的 value 时，该实体将被
     * 按 geometryType 分类到对应图层。
     * <ul>
     *   <li>key="*" 表示匹配所有 key</li>
     *   <li>value="*" 表示匹配所有 value</li>
     *   <li>key 和 value 的匹配是精确字符串比较，非通配符/正则</li>
     * </ul>
     * 示例：{@code OsmFeatureDef.all("highway", GeometryType.LINE)}
     * 匹配所有 highway=* 标签的实体。
     *
     * @param key          标签键名，"*" 表示匹配所有
     * @param value        标签值，"*" 表示匹配所有
     * @param geometryType 要素几何类型
     */
    public record OsmFeatureDef(String key, String value, GeometryType geometryType) {
        /**
         * 检查给定的 tag 是否匹配此规则。
         * <p>
         * 匹配逻辑：
         * <ol>
         *   <li>若 key 不是 "*" 且不等于 tagKey → 不匹配</li>
         *   <li>若 value 是 "*" 或等于 tagValue → 匹配</li>
         * </ol>
         *
         * @param tagKey   待匹配的标签键
         * @param tagValue 待匹配的标签值
         * @return true 当且仅当 key 和 value 均匹配
         */
        public boolean matches(String tagKey, String tagValue) {
            if (!"*".equals(key) && !key.equals(tagKey)) return false;
            return "*".equals(value) || value.equals(tagValue);
        }

        /**
         * 创建匹配所有 value 的规则（value="*"）。
         *
         * @param key  标签键名
         * @param type 几何类型
         * @return 新规则实例
         */
        public static OsmFeatureDef all(String key, GeometryType type) {
            return new OsmFeatureDef(key, "*", type);
        }

        /**
         * 创建匹配特定 value 的规则。
         *
         * @param key   标签键名
         * @param value 精确匹配的标签值
         * @param type  几何类型
         * @return 新规则实例
         */
        public static OsmFeatureDef of(String key, String value, GeometryType type) {
            return new OsmFeatureDef(key, value, type);
        }
    }

    // ==================== 内部数据类 ====================

    /**
     * 精简节点：仅存储经纬度坐标（float 精度，节省内存）。
     * <p>
     * OSM 原始 Node 携带大量元数据（id、tags、version 等），但本类仅需要坐标
     * 用于路径构建和坐标查找。使用 float 而非 double 可节省约 50% 内存，
     * 对于百万级节点而言差异显著。float 的 ~0.5m 精度对地图渲染完全足够。
     */
    private static class MiniNode {
        /** 经度（度） */
        final float lon;
        /** 纬度（度） */
        final float lat;

        /** @param lon 经度  @param lat 纬度 */
        MiniNode(float lon, float lat) { this.lon = lon; this.lat = lat; }
    }

    /**
     * 待处理的 Way：在 process() 中暂存，在 complete() 中根据闭合性和
     * multipolygon 归属决定分类为线或面图层。
     *
     * @param wayId    OSM Way ID
     * @param def      匹配到的要素定义（key、value、geometryType）
     * @param wayNodes Way 的节点序列（有序）
     * @param tags     Way 的标签键值对（用于 buildPath 时生成 attr 摘要）
     */
    private record PendingWay(long wayId, OsmFeatureDef def, List<WayNode> wayNodes, Map<String, String> tags) {}

    /**
     * Relation 成员引用：记录被 multipolygon/boundary 引用的 Way ID 及其角色。
     *
     * @param wayId 被引用的 Way ID
     * @param role  角色名（"outer"=外边界, "inner"=孔洞）
     */
    private record MemberRef(long wayId, String role) {}

    // ==================== 实例字段 ====================

    /** 点要素图层（输出：独立带标签的 Node） */
    private final Layer pointLayer;
    /** 线要素图层（输出：非闭合 Way + 线型要素） */
    private final Layer lineLayer;
    /** 面要素图层（输出：闭合 Way + multipolygon Relation） */
    private final Layer polygonLayer;
    /** 要素匹配规则列表（构造函数传入，决定如何对实体分类） */
    private final List<OsmFeatureDef> featureDefs;
    /** 坐标变换器（通常为 NoTransform，保留原始经纬度） */
    private final CoordinateTransform coordTransform;

    /**
     * OSM 节点坐标缓存（用于构建路径时的坐标查找）。
     * Key = OSM Node ID, Value = 精简节点（float lon/lat）。
     */
    private final Map<Long, MiniNode> nodeMap = new HashMap<>();

    /**
     * OSM 节点坐标外露 Map（供外部路网构建器使用）。
     * Key = OSM Node ID, Value = [lon, lat] 双精度数组。
     */
    private final Map<Long, double[]> allNodes = new HashMap<>();

    /**
     * 待处理 Way 暂存列表：在 process() 中匹配到要素定义的 Way 暂存于此，
     * 在 complete() 中根据闭合性和 multipolygon 归属决定最终分类。
     */
    private final List<PendingWay> pendingWays = new ArrayList<>();

    /**
     * 可步行道路的节点序列列表（含 highway 类型）。
     * 每条记录是一个 Way 的完整节点 ID 序列及其 highway 标签值，
     * 供 RoadGraph.build() 构建路网拓扑。
     */
    private final List<WaySegment> highwayWayNodes = new ArrayList<>();

    /**
     * 地名标注列表：从带 name 标签的 Node 提取。
     * 每个 NamedPlace 包含经纬度、名称、地点类型和显示优先级，供 MapPanel 绘制。
     */
    private final List<NamedPlace> namedPlaces = new ArrayList<>();

    /**
     * multipolygon/boundary Relation → 成员 Way 列表的映射。
     * Key = Relation ID, Value = 该 Relation 引用的 Way（MemberRef 列表）。
     */
    private final Map<Long, List<MemberRef>> relationMembers = new HashMap<>();

    /**
     * 被 multipolygon/boundary Relation 引用的所有 Way ID 集合。
     * 在 complete() 中根据此集合判断哪些 Way 不应单独分类。
     */
    private final Set<Long> relationWayIds = new HashSet<>();

    /**
     * 不可步行的 highway 类型集合。
     * <p>
     * 这些类型即使有 highway 标签也不纳入路网构建，因为它们是
     * 机动车专用或未建成道路，步行不可通行。
     */
    private static final Set<String> NON_WALKABLE_HIGHWAY = Set.of(
            "motorway",          // 高速公路（行人禁止进入）
            "motorway_link",     // 高速匝道
            "raceway",           // 赛道
            "proposed",          // 规划中（尚未建成）
            "construction"       // 施工中（暂不可用）
    );

    // ===================================================================================
    // 统计计数器
    // ===================================================================================

    /** 解析的原始 Node 总数 */
    private int totalNodes;
    /** 解析的原始 Way 总数 */
    private int totalWays;
    /** 解析的原始 Relation 总数 */
    private int totalRelations;
    /** 成功添加到点图层的要素数 */
    private int pointCount;
    /** 成功添加到线图层的要素数 */
    private int lineCount;
    /** 成功添加到面图层的要素数 */
    private int polygonCount;

    /**
     * Feature 缓存：三级映射实现 Feature 复用。
     * <p>
     * 结构：Layer → (key → (value → Feature))。
     * 当多个实体匹配到相同的 key+value 时，它们的 GisObject 共享同一个 Feature，
     * 避免重复创建 Feature 对象，同时保证图层中同一要素类型的实例聚集在一起。
     */
    private final Map<Layer, Map<String, Map<String, Feature>>> featureCache = new HashMap<>();

    // ==================== 构造方法 ====================

    /**
     * 构造 EnhancedOsmLayerSink。
     * <p>
     * 初始化三个图层、要素定义和坐标变换器，同时为每个图层创建空的 Feature 缓存。
     * 构造完成后即可直接传给 OsmosisReader 使用。
     *
     * @param pointLayer      点要素图层（输出：独立带标签的 Node → POINT 要素）
     * @param lineLayer       线要素图层（输出：非闭合 Way → LINE 要素）
     * @param polygonLayer    面要素图层（输出：闭合 Way + multipolygon → POLYGON 要素）
     * @param featureDefs     要素匹配规则列表（通常由 createStandardFeatureDefs() 生成）
     * @param coordTransform  坐标变换器（若无需变换，传入 CoordinateTransform.NoTransform()）
     */
    public EnhancedOsmLayerSink(Layer pointLayer, Layer lineLayer, Layer polygonLayer,
                                List<OsmFeatureDef> featureDefs,
                                CoordinateTransform coordTransform) {
        this.pointLayer = pointLayer;
        this.lineLayer = lineLayer;
        this.polygonLayer = polygonLayer;
        this.featureDefs = featureDefs;
        this.coordTransform = coordTransform;

        // 为三个图层分别初始化 Feature 缓存（空 Map）
        for (Layer layer : List.of(pointLayer, lineLayer, polygonLayer)) {
            featureCache.put(layer, new HashMap<>());
        }
    }

    // ==================== Sink 接口实现 ====================

    /**
     * 解析开始前的初始化回调。
     * <p>
     * 由 OsmosisReader 在开始解析 PBF 数据之前调用，收到 OSM 文件头元数据
     * （如 bounding box、required features 等）。本实现仅打印日志，无额外初始化需求。
     *
     * @param metaData 文件头元数据 Map（key 如 "timestamp", "osm_replication_timestamp" 等）
     */
    @Override
    public void initialize(Map<String, Object> metaData) {
        System.out.println("[EnhancedOsmLayerSink] 开始解析 OSM PBF 数据...");
    }

    /**
     * 核心处理回调：每遇到一个 OSM 实体（Node/Way/Relation）被调用一次。
     * <p>
     * 通过 {@code instanceof} 分发到对应的处理子方法：
     * <ul>
     *   <li>Node   → {@link #processNode(Node)}   — 存储坐标 + 匹配点要素 + 收集地名</li>
     *   <li>Way    → {@link #processWay(Way)}     — 收集路网 + 暂存用于延迟分类</li>
     *   <li>Relation → {@link #processRelation(Relation)} — 暂存 multipolygon 成员</li>
     * </ul>
     * 此方法是整个解析流水线的入口，被 OsmosisReader 在解析线程中同步调用。
     *
     * @param container 封装了一个 OSM 实体的容器（通过 getEntity() 获取实体）
     */
    @Override
    public void process(EntityContainer container) {
        Entity entity = container.getEntity();

        if (entity instanceof Node node) {
            processNode(node);
        } else if (entity instanceof Way way) {
            processWay(way);
        } else if (entity instanceof Relation relation) {
            processRelation(relation);
        }
    }

    /**
     * 所有实体处理完毕后的完成回调。
     * <p>
     * 执行延迟分类逻辑（四阶段）：
     * <ol>
     *   <li>从 relationMembers 收集所有被 multipolygon 引用的 Way ID → relationWayIds</li>
     *   <li>构建 Way ID → PendingWay 快速查找 Map（避免 O(n²) 遍历）</li>
     *   <li>未被 multipolygon 引用的 Way → classifyAndAddWay() 根据闭合性分类</li>
     *   <li>multipolygon 的 outer 成员 → addWayToLayer() 添加到面图层</li>
     * </ol>
     * 最终打印分类统计信息。
     */
    @Override
    public void complete() {
        System.out.println("[EnhancedOsmLayerSink] 原始数据读取完成, 开始分类...");

        // 第一阶段：从 relationMembers 收集所有被引用的 Way ID
        for (var entry : relationMembers.entrySet()) {
            for (MemberRef ref : entry.getValue()) {
                relationWayIds.add(ref.wayId);
            }
        }

        // 第二阶段：构建 Way ID → PendingWay O(1) 查找 Map
        Map<Long, PendingWay> pwMap = new HashMap<>();
        for (PendingWay pw : pendingWays) {
            pwMap.put(pw.wayId, pw);
        }

        // 第三阶段：非 multipolygon 的 Way → 根据闭合性分到线/面图层
        for (PendingWay pw : pendingWays) {
            if (!relationWayIds.contains(pw.wayId)) {
                classifyAndAddWay(pw);
            }
        }

        // 第四阶段：multipolygon Relation 的 outer 成员 → 添加到面图层
        for (var entry : relationMembers.entrySet()) {
            for (MemberRef ref : entry.getValue()) {
                if ("inner".equals(ref.role)) {
                    continue; // 内环暂时跳过（后续可扩展为孔洞支持）
                }
                PendingWay pw = pwMap.get(ref.wayId);
                if (pw != null) {
                    addWayToLayer(polygonLayer, pw);
                    polygonCount++;
                }
            }
        }

        System.out.println("[EnhancedOsmLayerSink] 分类完成: 点=" + pointCount
                + ", 线=" + lineCount + ", 面=" + polygonCount);
    }

    /**
     * 资源释放回调。
     * <p>
     * 由 OsmosisReader 在完全结束解析后调用。本实现无需要清理的外部资源（I/O、DB、Socket 等），
     * 所有数据都在内存中由 GC 管理。
     */
    @Override
    public void close() {
        // 无需清理
    }

    // ==================== 实体处理方法 ====================

    /**
     * 处理单个 OSM Node 实体。
     * <p>
     * 三件事：
     * <ol>
     *   <li>存入两个坐标缓存 Map（nodeMap 用于路径构建，allNodes 供外部路网使用）</li>
     *   <li>匹配 POINT 类型的要素定义 → 命中则调用 addPointFeature() 添加到点图层</li>
     *   <li>检查 name 标签 → 有则创建 NamedPlace 添加到地名标注列表</li>
     * </ol>
     *
     * @param node Osmosis 解析出的 Node 实体
     */
    private void processNode(Node node) {
        totalNodes++;  // 统计原始 Node 总数
        double lon = node.getLongitude();
        double lat = node.getLatitude();

        // 存入坐标缓存（双层 Map：MiniNode 内部用，double[] 外部用）
        nodeMap.put(node.getId(), new MiniNode((float) lon, (float) lat));
        allNodes.put(node.getId(), new double[]{lon, lat});

        Map<String, String> tags = tagsToMap(node.getTags());

        // 匹配 POINT 类型要素定义 → 添加为点要素
        OsmFeatureDef matched = matchTags(node.getTags(), GeometryType.POINT);
        if (matched != null) {
            addPointFeature(node.getId(), (float) lon, (float) lat, matched, tags);
        }

        // 收集地名标注：Node 有 name 标签 → 创建 NamedPlace
        NamedPlace place = NamedPlace.fromTags(lon, lat, tags);
        if (place != null) {
            namedPlaces.add(place);
        }
    }

    /**
     * 处理单个 OSM Way 实体。
     * <p>
     * 两件事：
     * <ol>
     *   <li>检查 highway 标签 → 若存在且非不可步行类型，则将完整节点序列
     *       包装为 WaySegment 存入 highwayWayNodes（供路网构建使用）</li>
     *   <li>匹配要素定义（不限几何类型）→ 命中则创建 PendingWay 暂存，
     *       等待 complete() 中的延迟分类</li>
     * </ol>
     * <p>
     * 注意：highway 路网收集独立于要素匹配逻辑。即使某 highway=* 不在要素定义列表中，
     * 也会被收集用于路网构建；反之如果 highway 在列表中则还会被添加到线图层。
     *
     * @param way Osmosis 解析出的 Way 实体
     */
    private void processWay(Way way) {
        totalWays++;  // 统计原始 Way 总数

        // ── 1. 路网收集：有 highway 标签且非不可步行 → 提取节点序列 ──
        String highwayVal = getTagValue(way, "highway");
        if (highwayVal != null && !NON_WALKABLE_HIGHWAY.contains(highwayVal)
                && !way.getWayNodes().isEmpty()) {
            List<WayNode> wnList = way.getWayNodes();
            long[] ids = new long[wnList.size()];
            for (int i = 0; i < wnList.size(); i++) {
                ids[i] = wnList.get(i).getNodeId();
            }
            highwayWayNodes.add(new WaySegment(ids, highwayVal));
        }

        // ── 2. 要素匹配：匹配要素定义 → 暂存 PendingWay ──
        OsmFeatureDef matched = matchTags(way.getTags(), null);
        if (matched != null && !way.getWayNodes().isEmpty()) {
            pendingWays.add(new PendingWay(way.getId(), matched, way.getWayNodes(),
                    tagsToMap(way.getTags())));
        }
    }

    /**
     * 处理单个 OSM Relation 实体。
     * <p>
     * 仅关注 type=multipolygon 或 type=boundary 的 Relation：
     * <ol>
     *   <li>过滤：跳过非 multipolygon/boundary 类型</li>
     *   <li>提取成员：遍历所有成员，仅收集 memberType=way 的成员（忽略 node/relation 成员）</li>
     *   <li>暂存到 relationMembers Map，Key=Relation ID，Value=MemberRef 列表</li>
     * </ol>
     * <p>
     * 注意：成员的角色（"outer" / "inner"）在 complete() 阶段使用：
     * outer 成员被添加到面图层，inner 成员当前跳过（未来可扩展为孔洞渲染）。
     *
     * @param relation Osmosis 解析出的 Relation 实体
     */
    private void processRelation(Relation relation) {
        totalRelations++;  // 统计原始 Relation 总数

        // 仅处理 multipolygon 和 boundary 类型
        String type = getTagValue(relation, "type");
        if (!"multipolygon".equals(type) && !"boundary".equals(type)) {
            return;
        }

        // 提取所有 Way 类型的成员
        List<MemberRef> members = new ArrayList<>();
        for (RelationMember member : relation.getMembers()) {
            String memberType = member.getMemberType().name().toLowerCase();
            if ("way".equals(memberType)) {
                members.add(new MemberRef(member.getMemberId(), member.getMemberRole()));
            }
        }
        if (!members.isEmpty()) {
            relationMembers.put(relation.getId(), members);
        }
    }

    // ==================== 分类与添加方法 ====================

    /**
     * 根据闭合性和要素定义类型将 PendingWay 分类到线或面图层。
     * <p>
     * 分类策略（按优先级）：
     * <ol>
     *   <li>POLYGON — 强制为面（无论是否闭合）</li>
     *   <li>LINE    — 强制为线（无论是否闭合）</li>
     *   <li>POINT   — 跳过（POINT 类型不处理 Way，点要素由 Node 单独处理）</li>
     *   <li>AUTO    — 根据闭合性自动判断：首尾节点 ID 相同 → 面，否则 → 线</li>
     * </ol>
     *
     * @param pw 待分类的 PendingWay
     */
    private void classifyAndAddWay(PendingWay pw) {
        List<WayNode> nodes = pw.wayNodes;
        // 判断闭合性：Way 首节点 ID == 尾节点 ID
        boolean isClosed = nodes.get(0).getNodeId() == nodes.get(nodes.size() - 1).getNodeId();

        GeometryType defType = pw.def.geometryType;
        boolean asPolygon;

        if (defType == GeometryType.POLYGON) {
            asPolygon = true;   // 强制面要素
        } else if (defType == GeometryType.LINE) {
            asPolygon = false;  // 强制线要素
        } else if (defType == GeometryType.POINT) {
            return;             // POINT 类型不处理 Way
        } else { // AUTO：根据闭合性自动推断
            asPolygon = isClosed;
        }

        if (asPolygon) {
            addWayToLayer(polygonLayer, pw);
            polygonCount++;
        } else {
            addWayToLayer(lineLayer, pw);
            lineCount++;
        }
    }

    /**
     * 将 PendingWay 转换为 GisObject 并添加到目标图层。
     * <p>
     * 步骤：
     * <ol>
     *   <li>调用 buildPath() 将 Way 节点序列构建为 SerializablePath</li>
     *   <li>通过 findOrCreateFeature() 在缓存中查找或创建对应的 Feature</li>
     *   <li>组装 attrs 数组（wayId, key, tagSummary, nodeCount）</li>
     *   <li>创建 GisObject 并添加到 Feature 的 shapes 列表</li>
     * </ol>
     *
     * @param layer 目标图层（pointLayer / lineLayer / polygonLayer）
     * @param pw    待添加的 PendingWay
     */
    private void addWayToLayer(Layer layer, PendingWay pw) {
        // 构建几何路径（坐标通过 nodeMap 查找，经 coordTransform 变换）
        SerializablePath path = buildPath(pw.wayNodes);
        if (path == null) return;

        // 查找或创建 Feature（通过缓存复用）
        Feature feature = findOrCreateFeature(layer, pw.def);

        // 组装 attrs 属性数组：供渲染时鼠标悬停/点击显示信息
        String tagSummary = summarizeTags(pw.tags);
        String[] attrs = new String[]{
                String.valueOf(pw.wayId),   // [0] OSM Way ID
                pw.def.key,                 // [1] 匹配的标签 key
                tagSummary,                 // [2] 标签摘要（供工具提示显示）
                String.valueOf(pw.wayNodes.size())  // [3] 节点数
        };
        feature.getShapes().add(new GisObject(path, attrs));
    }

    /**
     * 添加独立点要素到点图层。
     * <p>
     * 将 Node 构建为一个微小的线段路径（因 SerializablePath 不支持真正的"点"几何，
     * 用 moveTo + lineTo 到同一坐标模拟），创建 GisObject 后添加到对应 Feature。
     *
     * @param nodeId OSM Node ID
     * @param lon    经度
     * @param lat    纬度
     * @param def    匹配到的要素定义
     * @param tags   Node 的标签键值对
     */
    private void addPointFeature(long nodeId, float lon, float lat,
                                  OsmFeatureDef def, Map<String, String> tags) {
        // 构建双点路径（起点=终点，模拟单点要素）
        SerializablePath pointPath = new SerializablePath(java.awt.geom.Path2D.WIND_NON_ZERO, 2);
        pointPath.moveTo(lon, lat);
        pointPath.lineTo(lon, lat);  // 同一坐标 — 单点要素

        // 查找或创建 Feature
        Feature feature = findOrCreateFeature(pointLayer, def);

        // 组装 attrs
        String tagSummary = summarizeTags(tags);
        String[] attrs = new String[]{
                String.valueOf(nodeId),    // [0] OSM Node ID
                def.key,                   // [1] 匹配的标签 key
                tagSummary,                // [2] 标签摘要
                "1"                        // [3] 节点数（固定为 1）
        };
        feature.getShapes().add(new GisObject(pointPath, attrs));
        pointCount++;
    }

    // ==================== 标签匹配 ====================

    /**
     * 在实体标签中查找匹配的要素定义。
     * <p>
     * 遍历实体的所有标签，对每条标签检查所有 featureDefs 规则：
     * <ol>
     *   <li>如果指定了 onlyType，则仅考虑几何类型匹配的规则
     *       （允许 AUTO 类型参与匹配，因为 AUTO 对 Node 等同于 POINT）</li>
     *   <li>调用 OsmFeatureDef.matches() 检查 key 和 value 是否匹配</li>
     *   <li>找到第一个匹配的规则即返回（贪心策略，靠前的规则优先级更高）</li>
     * </ol>
     * <p>
     * 若实体无标签或没有规则匹配，返回 null。
     *
     * @param tags     实体的标签集合（来自 node.getTags() / way.getTags()）
     * @param onlyType 仅匹配指定几何类型（null 表示不限，匹配所有类型）
     * @return 匹配的 OsmFeatureDef，无匹配返回 null
     */
    private OsmFeatureDef matchTags(Collection<Tag> tags, GeometryType onlyType) {
        if (tags.isEmpty()) return null;

        for (Tag tag : tags) {
            for (OsmFeatureDef def : featureDefs) {
                // 类型过滤：若指定了 onlyType，跳过不匹配的类型
                // AUTO 总是参与匹配（对 Node 视作 POINT，对 Way 根据闭合性判断）
                if (onlyType != null && def.geometryType != onlyType && def.geometryType != GeometryType.AUTO) {
                    continue;
                }
                if (def.matches(tag.getKey(), tag.getValue())) {
                    return def;  // 贪心：返回第一个匹配的规则
                }
            }
        }
        return null;
    }

    // ==================== Feature 查找/创建 ====================

    /**
     * 在指定图层中查找或创建 Feature。
     * <p>
     * 通过三级缓存映射实现 Feature 复用：
     * {@code featureCache[layer][def.key][def.value] = Feature}
     * <ol>
     *   <li>查找 layer 对应的 keyMap（第一级）</li>
     *   <li>查找 def.key 对应的 valueMap（第二级，不存在则创建）</li>
     *   <li>查找 def.value 对应的 Feature（第三级，不存在则创建）</li>
     * </ol>
     * <p>
     * 新创建的 Feature 会自动设置 key/value，根据 def.key 选择填充颜色，
     * 并添加到图层中。同一 key+value 的所有 GisObject 共享同一个 Feature。
     *
     * @param layer 目标图层
     * @param def   要素匹配规则
     * @return 已存在或新创建的 Feature
     */
    private Feature findOrCreateFeature(Layer layer, OsmFeatureDef def) {
        // 第一级缓存：Layer → keyMap
        Map<String, Map<String, Feature>> keyMap = featureCache.get(layer);

        // 第二级缓存：key → valueMap（不存在则创建）
        Map<String, Feature> valueMap = keyMap.computeIfAbsent(def.key, k -> new LinkedHashMap<>());

        // 第三级缓存：value → Feature（不存在则创建）
        Feature feature = valueMap.get(def.value);
        if (feature != null) return feature;

        // ------- 创建新 Feature -------
        feature = new Feature();
        feature.setKey(def.key);              // 标签键（如 "highway"）
        feature.setValue(def.value);          // 标签值（如 "primary" 或 "*"）
        feature.setFillColor(pickColor(def.key));  // 根据 key 选择颜色
        feature.setOutlineColor(Color.DARK_GRAY);  // 边框统一深灰
        layer.addFeature(feature);            // 注册到图层
        valueMap.put(def.value, feature);     // 加入缓存
        return feature;
    }

    // ==================== 路径构建 ====================

    /**
     * 根据 Way 的节点 ID 序列构建 SerializablePath。
     * <p>
     * 实现流程：
     * <ol>
     *   <li>遍历 WayNode 序列，通过 nodeMap 查找每个节点的坐标</li>
     *   <li>对坐标应用 coordTransform.floatTransform() 变换</li>
     *   <li>第一个节点调用 moveTo()，后续节点调用 lineTo()</li>
     *   <li>若首尾节点 ID 相同（闭合路径），调用 closePath() 闭合</li>
     * </ol>
     * <p>
     * 若路径中所有节点的坐标都无法解析（nodeMap 中不存在），返回 null。
     *
     * @param wayNodes Way 的有序节点列表
     * @return 构建好的路径，若路径无效则返回 null
     */
    private SerializablePath buildPath(List<WayNode> wayNodes) {
        // WIND_NON_ZERO 为填充规则，capacity = wayNodes.size() 预分配容量
        SerializablePath path = new SerializablePath(1, wayNodes.size());
        boolean first = true;

        for (WayNode wn : wayNodes) {
            MiniNode mn = nodeMap.get(wn.getNodeId());
            if (mn == null) continue;  // 节点坐标未找到，跳过

            float[] xy = coordTransform.floatTransform(mn.lon, mn.lat);
            if (first) {
                path.moveTo(xy[0], xy[1]);  // 起点
                first = false;
            } else {
                path.lineTo(xy[0], xy[1]);  // 中间点
            }
        }

        // 闭合路径：首尾节点 ID 相同 → 调用 closePath()
        if (!first && wayNodes.size() > 2) {
            long firstId = wayNodes.get(0).getNodeId();
            long lastId = wayNodes.get(wayNodes.size() - 1).getNodeId();
            if (firstId == lastId) {
                path.closePath();
            }
        }

        return first ? null : path;  // first 仍为 true → 没有有效节点
    }

    // ==================== 辅助方法 ====================

    /**
     * 将 Osmosis Tag 集合转换为不可修改的 Map。
     * <p>
     * 使用 LinkedHashMap 保留插入顺序，便于调试和序列化一致性。
     *
     * @param tags 实体的 Tag 集合
     * @return key→value 的不可修改 Map
     */
    private static Map<String, String> tagsToMap(Collection<Tag> tags) {
        Map<String, String> map = new LinkedHashMap<>();
        for (Tag tag : tags) map.put(tag.getKey(), tag.getValue());
        return Collections.unmodifiableMap(map);
    }

    /**
     * 从实体标签中获取指定 key 的值。
     * <p>
     * 线性遍历实体标签列表（OSM 每个实体通常只有几个标签，性能足够）。
     *
     * @param entity OSM 实体（Node/Way/Relation）
     * @param key    待查找的标签键
     * @return 标签值，若不存在则返回 null
     */
    private static String getTagValue(Entity entity, String key) {
        for (Tag tag : entity.getTags()) {
            if (key.equals(tag.getKey())) return tag.getValue();
        }
        return null;
    }

    /**
     * 生成标签摘要字符串，用于 GisObject attrs 中的属性展示。
     * <p>
     * 按预设的优先级键列表提取标签值，格式为 {@code highway=primary; name=G105}。
     * 若实体没有匹配优先级键的任何标签，则回退到 tags.toString() 原始格式。
     *
     * @param tags 标签键值对
     * @return 格式化的摘要字符串
     */
    private static String summarizeTags(Map<String, String> tags) {
        if (tags.isEmpty()) return "-";
        StringBuilder sb = new StringBuilder();
        // 按优先级排列的展示键：地图上常用且重要的标签优先显示
        String[] priorityKeys = {"highway", "name", "building", "waterway", "landuse",
                "natural", "amenity", "leisure", "railway", "place"};
        for (String key : priorityKeys) {
            String val = tags.get(key);
            if (val != null) {
                if (!sb.isEmpty()) sb.append("; ");
                sb.append(key).append("=").append(val);
            }
        }
        return sb.isEmpty() ? tags.toString() : sb.toString();
    }

    /**
     * 根据要素主 key 选择 Feature 的填充颜色。
     * <p>
     * 颜色方案设计：
     * <ul>
     *   <li>highway  → 橙色（道路）</li>
     *   <li>building → 棕褐（建筑）</li>
     *   <li>waterway → 蓝色（河流）</li>
     *   <li>landuse  → 浅绿（土地利用）</li>
     *   <li>natural  → 中绿（自然区域）</li>
     *   <li>amenity  → 粉红（设施）</li>
     *   <li>others   → 浅灰（默认）</li>
     * </ul>
     * 注意：此颜色仅影响 dsol GIS 图层的默认渲染，MapPanel 中道路着色由 PathColorConfig 独立控制。
     *
     * @param mainKey 要素主标签键（如 "highway", "building"）
     * @return 对应的 Color 对象
     */
    private static Color pickColor(String mainKey) {
        return switch (mainKey) {
            case "highway"   -> new Color(255, 200, 120);  // 橙色（道路）
            case "building"  -> new Color(210, 180, 140);  // 棕褐（建筑）
            case "waterway"  -> new Color(100, 150, 255);  // 蓝色（河流）
            case "landuse"   -> new Color(180, 220, 150);  // 浅绿（土地利用）
            case "natural"   -> new Color(140, 200, 120);  // 中绿（自然）
            case "railway"   -> new Color(180, 180, 180);  // 灰色（铁路）
            case "amenity"   -> new Color(255, 160, 160);  // 粉红（设施）
            case "leisure"   -> new Color(170, 230, 170);  // 淡绿（休闲）
            case "boundary"  -> new Color(200, 160, 200);  // 紫色（边界）
            case "power"     -> new Color(255, 255, 150);  // 黄色（电力）
            case "barrier"   -> new Color(160, 160, 160);  // 深灰（障碍）
            case "place"     -> new Color(255, 220, 180);  // 浅橙（地名）
            default          -> new Color(200, 200, 200);  // 浅灰（未知）
        };
    }

    // ==================== 统计信息与数据提取 ====================

    /** @return 解析的原始 Node 总数 */
    public int getTotalNodes()      { return totalNodes; }
    /** @return 解析的原始 Way 总数 */
    public int getTotalWays()       { return totalWays; }
    /** @return 解析的原始 Relation 总数 */
    public int getTotalRelations()  { return totalRelations; }
    /** @return 成功添加到点图层的要素数 */
    public int getPointCount()      { return pointCount; }
    /** @return 成功添加到线图层的要素数 */
    public int getLineCount()       { return lineCount; }
    /** @return 成功添加到面图层的要素数 */
    public int getPolygonCount()    { return polygonCount; }

    /**
     * @return 全体 OSM 节点坐标（Map 视图，key=OSM Node ID，value=[lon, lat] 双精度数组）。
     *         由 processNode() 逐步填充，供 RoadGraph.build() 使用。
     */
    public Map<Long, double[]> getNodeMap() { return allNodes; }

    /**
     * @return 可步行道路的节点序列列表（含 highway 类型）。
     *         由 processWay() 逐步填充，供 RoadGraph.build() 构建路网拓扑。
     *         每个 WaySegment 包含完整节点 ID 序列和 highway 标签值。
     */
    public List<WaySegment> getHighwayWayNodeSequences() { return highwayWayNodes; }

    /**
     * @return 带名称的地名标注列表（NamedPlace），由 processNode() 从 name 标签的 Node 提取，
     *         供 MapPanel 根据 zoom 级别动态绘制。
     */
    public List<NamedPlace> getNamedPlaces() { return namedPlaces; }

    // ==================== 预定义的要素定义集合 ====================

    /**
     * 创建覆盖常见 OSM 标签的完整要素定义列表。
     * <p>
     * 按点、线、面三类组织：
     * <ul>
     *   <li><b>POINT 点要素</b>：place、amenity、shop、tourism、historic、
     *       highway=bus_stop 等独立 Node 标签</li>
     *   <li><b>LINE 线要素</b>：highway、waterway、railway、power、barrier 等
     *       非闭合 Way 标签</li>
     *   <li><b>POLYGON 面要素</b>：building、landuse、natural、leisure、
     *       boundary、amenity（校园/医院/停车场）等闭合 Way/Relation 标签</li>
     * </ul>
     * <p>
     * 规则按定义顺序匹配（贪心策略）：后定义的规则不会覆盖前面已匹配的。
     * 因此特定值规则（如 amenity=restaurant）应放在 all 规则（如 amenity=*）之前。
     *
     * @return 包含 100+ 条规则的要素定义列表
     */
    public static List<OsmFeatureDef> createStandardFeatureDefs() {
        List<OsmFeatureDef> defs = new ArrayList<>();

        // ── 点要素 (POINT) ──
        defs.add(OsmFeatureDef.all("place", GeometryType.POINT));           // city, town, village, hamlet...
        defs.add(OsmFeatureDef.of("amenity", "restaurant", GeometryType.POINT));
        defs.add(OsmFeatureDef.of("amenity", "cafe", GeometryType.POINT));
        defs.add(OsmFeatureDef.of("amenity", "fast_food", GeometryType.POINT));
        defs.add(OsmFeatureDef.of("amenity", "pub", GeometryType.POINT));
        defs.add(OsmFeatureDef.of("amenity", "bar", GeometryType.POINT));
        defs.add(OsmFeatureDef.of("amenity", "pharmacy", GeometryType.POINT));
        defs.add(OsmFeatureDef.of("amenity", "bank", GeometryType.POINT));
        defs.add(OsmFeatureDef.of("amenity", "atm", GeometryType.POINT));
        defs.add(OsmFeatureDef.of("amenity", "post_office", GeometryType.POINT));
        defs.add(OsmFeatureDef.of("amenity", "police", GeometryType.POINT));
        defs.add(OsmFeatureDef.of("amenity", "fire_station", GeometryType.POINT));
        defs.add(OsmFeatureDef.of("amenity", "hospital", GeometryType.POINT));
        defs.add(OsmFeatureDef.of("amenity", "clinic", GeometryType.POINT));
        defs.add(OsmFeatureDef.of("amenity", "doctors", GeometryType.POINT));
        defs.add(OsmFeatureDef.of("amenity", "dentist", GeometryType.POINT));
        defs.add(OsmFeatureDef.of("amenity", "school", GeometryType.POINT));
        defs.add(OsmFeatureDef.of("amenity", "university", GeometryType.POINT));
        defs.add(OsmFeatureDef.of("amenity", "library", GeometryType.POINT));
        defs.add(OsmFeatureDef.of("amenity", "place_of_worship", GeometryType.POINT));
        defs.add(OsmFeatureDef.of("amenity", "toilets", GeometryType.POINT));
        defs.add(OsmFeatureDef.of("amenity", "fuel", GeometryType.POINT));
        defs.add(OsmFeatureDef.of("amenity", "parking", GeometryType.POINT));
        defs.add(OsmFeatureDef.of("amenity", "bicycle_parking", GeometryType.POINT));
        defs.add(OsmFeatureDef.of("amenity", "bench", GeometryType.POINT));
        defs.add(OsmFeatureDef.of("amenity", "waste_basket", GeometryType.POINT));
        defs.add(OsmFeatureDef.of("amenity", "fountain", GeometryType.POINT));

        defs.add(OsmFeatureDef.of("highway", "bus_stop", GeometryType.POINT));
        defs.add(OsmFeatureDef.of("highway", "traffic_signals", GeometryType.POINT));
        defs.add(OsmFeatureDef.of("highway", "crossing", GeometryType.POINT));

        defs.add(OsmFeatureDef.of("railway", "station", GeometryType.POINT));
        defs.add(OsmFeatureDef.of("railway", "halt", GeometryType.POINT));
        defs.add(OsmFeatureDef.of("railway", "tram_stop", GeometryType.POINT));

        defs.add(OsmFeatureDef.of("shop", "supermarket", GeometryType.POINT));
        defs.add(OsmFeatureDef.of("shop", "convenience", GeometryType.POINT));
        defs.add(OsmFeatureDef.of("shop", "mall", GeometryType.POINT));
        defs.add(OsmFeatureDef.all("shop", GeometryType.POINT));

        defs.add(OsmFeatureDef.of("tourism", "hotel", GeometryType.POINT));
        defs.add(OsmFeatureDef.of("tourism", "museum", GeometryType.POINT));
        defs.add(OsmFeatureDef.of("tourism", "attraction", GeometryType.POINT));
        defs.add(OsmFeatureDef.of("tourism", "viewpoint", GeometryType.POINT));
        defs.add(OsmFeatureDef.all("tourism", GeometryType.POINT));

        defs.add(OsmFeatureDef.of("historic", "monument", GeometryType.POINT));
        defs.add(OsmFeatureDef.of("historic", "memorial", GeometryType.POINT));
        defs.add(OsmFeatureDef.of("historic", "castle", GeometryType.POINT));
        defs.add(OsmFeatureDef.all("historic", GeometryType.POINT));

        defs.add(OsmFeatureDef.of("man_made", "tower", GeometryType.POINT));
        defs.add(OsmFeatureDef.of("man_made", "water_tower", GeometryType.POINT));
        defs.add(OsmFeatureDef.of("man_made", "lighthouse", GeometryType.POINT));
        defs.add(OsmFeatureDef.of("man_made", "windmill", GeometryType.POINT));

        defs.add(OsmFeatureDef.of("natural", "peak", GeometryType.POINT));
        defs.add(OsmFeatureDef.of("natural", "tree", GeometryType.POINT));
        defs.add(OsmFeatureDef.of("natural", "spring", GeometryType.POINT));
        defs.add(OsmFeatureDef.of("natural", "cave_entrance", GeometryType.POINT));

        defs.add(OsmFeatureDef.of("power", "tower", GeometryType.POINT));
        defs.add(OsmFeatureDef.of("power", "pole", GeometryType.POINT));
        defs.add(OsmFeatureDef.of("power", "substation", GeometryType.POINT));

        defs.add(OsmFeatureDef.of("barrier", "gate", GeometryType.POINT));
        defs.add(OsmFeatureDef.of("barrier", "bollard", GeometryType.POINT));
        defs.add(OsmFeatureDef.of("barrier", "lift_gate", GeometryType.POINT));
        defs.add(OsmFeatureDef.of("barrier", "toll_booth", GeometryType.POINT));

        defs.add(OsmFeatureDef.of("emergency", "phone", GeometryType.POINT));
        defs.add(OsmFeatureDef.of("emergency", "fire_hydrant", GeometryType.POINT));

        defs.add(OsmFeatureDef.of("leisure", "playground", GeometryType.POINT));
        defs.add(OsmFeatureDef.of("leisure", "picnic_table", GeometryType.POINT));

        // ── 线要素 (LINE) ──
        defs.add(OsmFeatureDef.all("highway", GeometryType.LINE));          // 所有道路
        defs.add(OsmFeatureDef.all("waterway", GeometryType.LINE));         // 河流、溪流、沟渠
        defs.add(OsmFeatureDef.all("railway", GeometryType.LINE));          // 铁路
        defs.add(OsmFeatureDef.all("power", GeometryType.LINE));            // 电力线
        defs.add(OsmFeatureDef.of("barrier", "wall", GeometryType.LINE));
        defs.add(OsmFeatureDef.of("barrier", "fence", GeometryType.LINE));
        defs.add(OsmFeatureDef.of("barrier", "hedge", GeometryType.LINE));
        defs.add(OsmFeatureDef.of("barrier", "city_wall", GeometryType.LINE));
        defs.add(OsmFeatureDef.of("barrier", "retaining_wall", GeometryType.LINE));
        defs.add(OsmFeatureDef.of("natural", "coastline", GeometryType.LINE));
        defs.add(OsmFeatureDef.of("natural", "cliff", GeometryType.LINE));
        defs.add(OsmFeatureDef.of("natural", "ridge", GeometryType.LINE));
        defs.add(OsmFeatureDef.of("natural", "arete", GeometryType.LINE));
        defs.add(OsmFeatureDef.of("man_made", "pipeline", GeometryType.LINE));
        defs.add(OsmFeatureDef.of("man_made", "cutline", GeometryType.LINE));
        defs.add(OsmFeatureDef.of("man_made", "embankment", GeometryType.LINE));
        defs.add(OsmFeatureDef.all("aeroway", GeometryType.LINE));          // 跑道、滑行道
        defs.add(OsmFeatureDef.of("route", "ferry", GeometryType.LINE));

        // ── 面要素 (POLYGON) ──
        defs.add(OsmFeatureDef.all("building", GeometryType.POLYGON));      // 所有建筑
        defs.add(OsmFeatureDef.of("building", "yes", GeometryType.POLYGON));
        defs.add(OsmFeatureDef.of("building", "residential", GeometryType.POLYGON));
        defs.add(OsmFeatureDef.of("building", "apartments", GeometryType.POLYGON));
        defs.add(OsmFeatureDef.of("building", "commercial", GeometryType.POLYGON));
        defs.add(OsmFeatureDef.of("building", "industrial", GeometryType.POLYGON));
        defs.add(OsmFeatureDef.of("building", "school", GeometryType.POLYGON));
        defs.add(OsmFeatureDef.of("building", "hospital", GeometryType.POLYGON));
        defs.add(OsmFeatureDef.of("building", "church", GeometryType.POLYGON));
        defs.add(OsmFeatureDef.of("building", "garage", GeometryType.POLYGON));
        defs.add(OsmFeatureDef.of("building", "shed", GeometryType.POLYGON));
        defs.add(OsmFeatureDef.of("building", "roof", GeometryType.POLYGON));
        defs.add(OsmFeatureDef.of("building", "house", GeometryType.POLYGON));
        defs.add(OsmFeatureDef.of("building", "warehouse", GeometryType.POLYGON));

        defs.add(OsmFeatureDef.all("landuse", GeometryType.POLYGON));       // 所有土地利用
        defs.add(OsmFeatureDef.all("natural", GeometryType.POLYGON));       // 自然区域（水、森林等）
        defs.add(OsmFeatureDef.of("leisure", "park", GeometryType.POLYGON));
        defs.add(OsmFeatureDef.of("leisure", "garden", GeometryType.POLYGON));
        defs.add(OsmFeatureDef.of("leisure", "golf_course", GeometryType.POLYGON));
        defs.add(OsmFeatureDef.of("leisure", "sports_centre", GeometryType.POLYGON));
        defs.add(OsmFeatureDef.of("leisure", "stadium", GeometryType.POLYGON));
        defs.add(OsmFeatureDef.of("leisure", "pitch", GeometryType.POLYGON));
        defs.add(OsmFeatureDef.of("leisure", "track", GeometryType.POLYGON));
        defs.add(OsmFeatureDef.of("leisure", "swimming_pool", GeometryType.POLYGON));
        defs.add(OsmFeatureDef.of("leisure", "marina", GeometryType.POLYGON));
        defs.add(OsmFeatureDef.of("leisure", "nature_reserve", GeometryType.POLYGON));
        defs.add(OsmFeatureDef.all("leisure", GeometryType.POLYGON));

        defs.add(OsmFeatureDef.of("amenity", "school", GeometryType.POLYGON));
        defs.add(OsmFeatureDef.of("amenity", "university", GeometryType.POLYGON));
        defs.add(OsmFeatureDef.of("amenity", "college", GeometryType.POLYGON));
        defs.add(OsmFeatureDef.of("amenity", "hospital", GeometryType.POLYGON));
        defs.add(OsmFeatureDef.of("amenity", "parking", GeometryType.POLYGON));
        defs.add(OsmFeatureDef.of("amenity", "marketplace", GeometryType.POLYGON));
        defs.add(OsmFeatureDef.of("amenity", "grave_yard", GeometryType.POLYGON));

        defs.add(OsmFeatureDef.of("waterway", "riverbank", GeometryType.POLYGON));
        defs.add(OsmFeatureDef.of("waterway", "dock", GeometryType.POLYGON));
        defs.add(OsmFeatureDef.of("waterway", "boatyard", GeometryType.POLYGON));
        defs.add(OsmFeatureDef.of("waterway", "dam", GeometryType.POLYGON));

        defs.add(OsmFeatureDef.all("boundary", GeometryType.POLYGON));      // 行政边界

        defs.add(OsmFeatureDef.of("aeroway", "aerodrome", GeometryType.POLYGON));
        defs.add(OsmFeatureDef.of("aeroway", "terminal", GeometryType.POLYGON));
        defs.add(OsmFeatureDef.of("aeroway", "helipad", GeometryType.POLYGON));

        defs.add(OsmFeatureDef.of("man_made", "reservoir_covered", GeometryType.POLYGON));
        defs.add(OsmFeatureDef.of("man_made", "wastewater_plant", GeometryType.POLYGON));

        defs.add(OsmFeatureDef.of("military", "airfield", GeometryType.POLYGON));
        defs.add(OsmFeatureDef.of("military", "barracks", GeometryType.POLYGON));
        defs.add(OsmFeatureDef.of("military", "danger_area", GeometryType.POLYGON));
        defs.add(OsmFeatureDef.all("military", GeometryType.POLYGON));

        defs.add(OsmFeatureDef.of("place", "island", GeometryType.POLYGON));
        defs.add(OsmFeatureDef.of("place", "islet", GeometryType.POLYGON));

        defs.add(OsmFeatureDef.of("tourism", "zoo", GeometryType.POLYGON));
        defs.add(OsmFeatureDef.of("tourism", "theme_park", GeometryType.POLYGON));
        defs.add(OsmFeatureDef.of("tourism", "camp_site", GeometryType.POLYGON));
        defs.add(OsmFeatureDef.of("tourism", "caravan_site", GeometryType.POLYGON));

        return defs;
    }
}
