# OSM PBF 文件格式与 dsol GIS 库解析技术文档

## 目录

1. [OSM 数据模型概述](#1-osm-数据模型概述)
2. [PBF 文件格式](#2-pbf-文件格式)
3. [dsol GIS 库解析架构](#3-dsol-gis-库解析架构)
4. [核心类详解](#4-核心类详解)
5. [EnhancedOsmLayerSink 实战](#5-enhancedosmlayersink-实战)
6. [完整解析流程](#6-完整解析流程)
7. [性能优化建议](#7-性能优化建议)

---

## 1. OSM 数据模型概述

OpenStreetMap（OSM）使用三种基本**元数据类型**来描述地理信息：

### 1.1 Node（节点）

- **定义**：地球表面的一个点，由经纬度坐标定义
- **存储**：`(id, longitude, latitude, tags)`
- **用途**：
  - 独立 POI（如餐厅 `amenity=restaurant`、公交站 `highway=bus_stop`）
  - 作为 Way 的顶点构成线/面
- **示例**：
  ```
  Node id=123456, lon=115.8645, lat=28.6828
    tags: name=南昌市, place=city, name:zh=南昌市
  ```

### 1.2 Way（路径）

- **定义**：有序的 Node 序列（2 到 2000 个节点）
- **存储**：`(id, [nodeId1, nodeId2, ...], tags)`
- **用途**：
  - **线要素**：非闭合路径（如 `highway=primary`、`waterway=river`）
  - **面要素**：闭合路径，首尾节点 ID 相同（如 `building=yes`、`landuse=forest`）
- **示例**：
  ```
  Way id=789012, nodes=[111, 222, 333, 111]  ← 闭合→面
    tags: building=residential, name=阳光花园
  ```

### 1.3 Relation（关系）

- **定义**：多个元素（Node/Way/Relation）的逻辑组合
- **存储**：`(id, [Member(type, ref, role), ...], tags)`
- **用途**：
  - **multipolygon**：带孔洞的复杂面（outer 边界 + inner 孔洞）
  - **boundary**：行政边界
  - **route**：公交线路、骑行路线等
- **示例**：
  ```
  Relation id=345678, type=multipolygon
    members: Way#1001 role=outer, Way#1002 role=inner
    tags: landuse=residential
  ```

### 1.4 Tag（标签）

所有三种元数据都可携带零个或多个标签，格式为 `key=value`：

```
highway=primary       → 道路等级
name=中山路           → 名称
lanes=4               → 车道数
surface=asphalt       → 路面材质
maxspeed=60           → 限速
```

### 1.5 数据分层模型

在实际应用中，OSM 数据按几何类型分为三个图层：

| 图层 | 几何类型 | 数据来源 | 典型标签 |
|------|---------|---------|---------|
| **点图层** (Point) | 单点 | 独立带标签的 Node | `place=city`, `amenity=restaurant`, `highway=bus_stop` |
| **线图层** (Line) | 折线 | 非闭合 Way | `highway=primary`, `waterway=river`, `railway=rail` |
| **面图层** (Polygon) | 多边形 | 闭合 Way + multipolygon Relation | `building=yes`, `landuse=forest`, `natural=water` |

---

## 2. PBF 文件格式

### 2.1 什么是 PBF

**PBF**（Protocol Buffer Binary Format）是 Google Protocol Buffers 的二进制序列化格式，OSM 社区将其作为 `.osm` XML 的替代品。

相比 XML（`.osm` 文件）：

| 特性 | XML (.osm) | PBF (.osm.pbf) |
|------|-----------|----------------|
| 文件大小 | ~10× | **1×** |
| 解析速度 | 慢（文本解析） | **快**（二进制反序列化） |
| 可读性 | 人类可读 | 需工具解码 |
| 流式处理 | 支持（SAX） | 支持（分块读取） |

### 2.2 PBF 文件结构

OSM PBF 文件由一系列 **Blob** 组成，每个 Blob 包含一个完整的 OSM 数据块：

```
┌─────────────────────────────────────────────────┐
│                  OSM PBF 文件                      │
├─────────────────────────────────────────────────┤
│  BlobHeader (消息头)                              │
│  ├── type: "OSMHeader" 或 "OSMData"              │
│  ├── indexdata: 可选索引                          │
│  └── datasize: 后续 Blob 的字节数                  │
├─────────────────────────────────────────────────┤
│  Blob (数据块)                                    │
│  ├── raw 或 zlib_data (压缩/未压缩的 protobuf)     │
│  └── 反序列化后得到:                               │
│      ├── PrimitiveBlock                          │
│      │   ├── PrimitiveGroup[]                    │
│      │   │   ├── Node[]    (稠密编码)             │
│      │   │   ├── Way[]     (引用 Node ID)         │
│      │   │   └── Relation[](引用成员 ID)          │
│      │   ├── StringTable (全局字符串表)            │
│      │   └── granularity/lat_offset/lon_offset   │
├─────────────────────────────────────────────────┤
│  BlobHeader + Blob ... (重复，直到文件结束)        │
└─────────────────────────────────────────────────┘
```

### 2.3 DenseNodes 稠密编码

为减小文件体积，PBF 对 Node 使用**差分编码 + 字符串表**：

```
Node 传统编码：  每个 Node 独立存储 lon/lat/tags（字符串重复多）
PBF 稠密编码：   存储 delta 值 + 引用 StringTable 中的索引

举例：
  Node 1: lon=115.8645000, lat=28.6828000
  Node 2: lon=115.8645100, lat=28.6828100 （与 Node1 仅差 0.00001°）

PBF 存储：
  lon[0]=115.8645000, lat[0]=28.6828000
  lon[1]=+0.0000100,  lat[1]=+0.0000100  ← 仅存差值（更小的数字→更少的字节）
```

这种编码方式使得包含数百万节点的 PBF 文件依然紧凑高效。

### 2.4 StringTable 字符串表

所有标签的 key 和 value 都存储在 StringTable 中，实体仅引用索引：

```
StringTable[0] = "highway"
StringTable[1] = "primary"
StringTable[2] = "name"
StringTable[3] = "中山路"

Way 引用: keys=[0,2], vals=[1,3]  → 等价于 highway=primary, name=中山路
```

避免了 XML 中 `highway="primary"` 这类字符串在文件中反复出现带来的空间浪费。

---

## 3. dsol GIS 库解析架构

### 3.1 依赖关系

```
本项目 (StreetMapGuide)
  │
  ├── nl.tudelft.simulation:dsol-animation-gis:4.2.2
  │   ├── nl.tudelft.simulation:dsol-base:4.2.2
  │   └── org.djutils:djutils-draw:2.2.1
  │
  ├── org.openstreetmap.pbf:osmpbf:1.5.0       ← PBF 底层解析
  │   └── com.google.protobuf:protobuf-java      ← Protobuf 运行时
  │
  └── org.openstreetmap.osmosis:osmosis-core:0.49.2  ← OSM 数据模型
      └── org.openstreetmap.osmosis:osmosis-pbf       ← PBF 驱动
```

### 3.2 数据流

```
                         ┌──────────────────────┐
  map.osm.pbf ──────────▶│   OsmosisReader       │
  (二进制 PBF 文件)       │  (crosby.binary.osmosis)│
                         └──────────┬───────────┘
                                    │ process()
                                    ▼
                         ┌──────────────────────┐
                         │   Sink 接口            │
                         │  ┌──────────────────┐ │
                         │  │ initialize()     │ │
                         │  │ process(Entity)  │ │ ← 逐实体回调
                         │  │ complete()       │ │
                         │  │ close()          │ │
                         │  └──────────────────┘ │
                         └──────────┬───────────┘
                                    │ 分类处理
                                    ▼
            ┌───────────────────────┼───────────────────────┐
            ▼                       ▼                       ▼
     ┌─────────────┐        ┌─────────────┐        ┌─────────────┐
     │ Point Layer │        │  Line Layer │        │Polygon Layer│
     │  (点要素)    │        │  (线要素)    │        │  (面要素)    │
     │             │        │             │        │             │
     │ Feature[]   │        │ Feature[]   │        │ Feature[]   │
     │  ├─ place   │        │  ├─ highway │        │  ├─ building│
     │  ├─ amenity │        │  ├─ waterway│        │  ├─ landuse │
     │  └─ shop    │        │  └─ railway │        │  └─ natural │
     └─────────────┘        └─────────────┘        └─────────────┘
```

### 3.3 dsol GIS 模型层次

```
GisMap (地图容器，可选)
  └── Layer (图层：Point / Line / Polygon)
        └── Feature (要素分类：如 highway, building, amenity)
              └── GisObject (几何对象)
                    ├── SerializablePath (形状：坐标序列)
                    └── String[] attrs (属性数组：[id, key, tags, nodeCount])
```

---

## 4. 核心类详解

### 4.1 OsmosisReader — PBF 解析器

**全限定名**：`crosby.binary.osmosis.OsmosisReader`

`OsmosisReader` 是 dsol 库内部实际使用的 PBF 解析器，实现了 `RunnableSource` 接口。

```java
// 构造并设置 Sink，然后同步运行
OsmosisReader reader = new OsmosisReader(new File("map.osm.pbf"));
reader.setSink(sink);       // 设置实体接收器
reader.run();                // 阻塞式解析，逐个回调 sink.process()
```

> **⚠️ 常见陷阱**：不要使用不存在的 `OsmosisBinaryParser` 或 `BlockInputStream`。
> 正确的方式是 `OsmosisReader` + `Sink` 接口。

### 4.2 Sink 接口 — 实体接收回调

**全限定名**：`org.openstreetmap.osmosis.core.task.v0_6.Sink`

```java
public interface Sink {
    void initialize(Map<String, Object> metaData);  // 解析开始前
    void process(EntityContainer container);         // 每个实体到达时
    void complete();                                 // 所有实体处理完毕
    void close();                                    // 资源释放
}
```

### 4.3 Layer — 图层容器

**全限定名**：`nl.tudelft.simulation.dsol.animation.gis.map.Layer`

```java
Layer pointLayer = new Layer();
pointLayer.setName("Point");
pointLayer.addFeature(feature);   // 添加要素分类
```

### 4.4 Feature — 要素分类

**全限定名**：`nl.tudelft.simulation.dsol.animation.gis.map.Feature`

```java
Feature feature = new Feature();
feature.setKey("highway");        // 分类键
feature.setValue("primary");      // 分类值（"*" 表示全部）
feature.setFillColor(new Color(...));
feature.setOutlineColor(Color.DARK_GRAY);
feature.getShapes().add(gisObj); // 添加几何对象
```

### 4.5 GisObject — 几何对象

**全限定名**：`nl.tudelft.simulation.dsol.animation.gis.GisObject`

```java
SerializablePath path = ...;      // 形状
String[] attrs = {"123", "highway", "name=G105; lanes=4", "200"};
GisObject obj = new GisObject(path, attrs);
```

### 4.6 SerializablePath — 可序列化路径

**全限定名**：`nl.tudelft.simulation.dsol.animation.gis.SerializablePath`

```java
SerializablePath path = new SerializablePath(Path2D.WIND_NON_ZERO, capacity);
path.moveTo(lon1, lat1);
path.lineTo(lon2, lat2);
// ...
path.closePath();  // 闭合（面要素）
```

---

## 5. EnhancedOsmLayerSink 实战

`EnhancedOsmLayerSink` 是本项目封装的增强版 Sink 实现，提供以下核心能力：

### 5.1 要素定义系统

通过 `OsmFeatureDef` 声明性地定义哪些 OSM 标签匹配哪种几何类型：

```java
List<OsmFeatureDef> defs = EnhancedOsmLayerSink.createStandardFeatureDefs();
// 内部定义了 100+ 种常见 OSM 标签的匹配规则，例如：
//   OsmFeatureDef.all("highway", GeometryType.LINE)       → 所有 highway=* → 线
//   OsmFeatureDef.all("building", GeometryType.POLYGON)   → 所有 building=* → 面
//   OsmFeatureDef.all("place", GeometryType.POINT)        → 所有 place=* → 点
```

### 5.2 三阶段处理流程

```
┌─────────────────────────────────────────────────────────────┐
│ 阶段 1: process() — 逐实体接收                               │
│                                                             │
│  Node → processNode()                                       │
│    ├── 存入 nodeMap / allNodes（坐标查找缓存）                  │
│    ├── 匹配点要素定义 → addPointFeature() → pointLayer        │
│    └── 收集 name 标签 → NamedPlace 列表                      │
│                                                             │
│  Way → processWay()                                         │
│    ├── highway 标签 → highwayWayNodes（路网构建用）             │
│    └── 匹配要素定义 → pendingWays（暂存，等待 complete）        │
│                                                             │
│  Relation → processRelation()                               │
│    └── multipolygon/boundary → relationMembers（暂存）        │
│                                                             │
├─────────────────────────────────────────────────────────────┤
│ 阶段 2: complete() — 分类处理                                 │
│                                                             │
│  ├── 收集 relationWayIds（被 multipolygon 引用的 Way）        │
│  ├── 非 relation Way → classifyAndAddWay()                   │
│  │     ├── 闭合 → polygonLayer                               │
│  │     └── 非闭合 → lineLayer                                │
│  └── multipolygon outer Way → polygonLayer                   │
│                                                             │
├─────────────────────────────────────────────────────────────┤
│ 阶段 3: 数据提取（外部调用 getter）                             │
│                                                             │
│  ├── getNodeMap()           → 全体 Node 坐标                 │
│  ├── getHighwayWayNodeSequences() → 路网节点序列（含 highway 类型）│
│  └── getNamedPlaces()       → 地名标注列表                   │
└─────────────────────────────────────────────────────────────┘
```

### 5.3 关键设计决策

| 决策 | 说明 |
|------|------|
| **延迟分类** | Way 先暂存 `pendingWays`，在 `complete()` 中才按闭合性分到线/面图层，确保所有 Node 坐标都已收集 |
| **AUTO 几何推断** | 若要素定义未指定 `POINT`/`LINE`/`POLYGON`，则根据 Way 首尾节点 ID 是否相同自动判断 |
| **multipolygon 处理** | 被 `multipolygon` Relation 引用的 Way 不再单独分类，而是作为 outer 成员加入面图层 |
| **路网独立收集** | `highwayWayNodes` 独立于图层分类逻辑，确保路网构建不依赖要素匹配规则 |

---

## 6. 完整解析流程

### 6.1 最小化示例

```java
// 1. 创建图层
Layer pointLayer   = new Layer(); pointLayer.setName("Point");
Layer lineLayer    = new Layer(); lineLayer.setName("Line");
Layer polygonLayer = new Layer(); polygonLayer.setName("Polygon");

// 2. 创建 Sink（使用标准要素定义 + 无坐标变换）
EnhancedOsmLayerSink sink = new EnhancedOsmLayerSink(
    pointLayer, lineLayer, polygonLayer,
    EnhancedOsmLayerSink.createStandardFeatureDefs(),
    new CoordinateTransform.NoTransform());

// 3. 创建 OsmosisReader 并运行
OsmosisReader reader = new OsmosisReader(new File("map.osm.pbf"));
reader.setSink(sink);
reader.run();  // 阻塞直到解析完成

// 4. 获取解析结果
System.out.println("点要素: " + sink.getPointCount());
System.out.println("线要素: " + sink.getLineCount());
System.out.println("面要素: " + sink.getPolygonCount());
```

### 6.2 集成路网构建

```java
// 从 Sink 提取路网数据
Map<Long, double[]> nodeMap = sink.getNodeMap();
List<WaySegment> waySegments = sink.getHighwayWayNodeSequences();

// 构建路网图（边长用 Haversine 公式计算）
RoadGraph graph = RoadGraph.build(waySegments, nodeMap);

// 每条边已包含 highwayType，可用于：
//   - PathColorConfig 着色
//   - PathSpeedConfig 权重计算
```

### 6.3 集成地名标注

```java
List<NamedPlace> places = sink.getNamedPlaces();
// 每个 NamedPlace: (lon, lat, name, placeTag, priority)
// priority 1~7: state→city→county→village→hamlet→neighbourhood→unknown
```

### 6.4 启动地图 GUI

```java
// 计算经纬度边界
double[] bounds = computeBounds(nodeMap);

// 创建投影
MapProjection projection = new MapProjection(
    bounds[0], bounds[1], bounds[2], bounds[3], 1000, 800);

// EDT 线程启动 GUI
SwingUtilities.invokeLater(() -> {
    MainFrame frame = new MainFrame(graph, projection, places);
    frame.setVisible(true);
});
```

---

## 7. 性能优化建议

### 7.1 解析阶段

| 优化项 | 说明 |
|--------|------|
| **合理使用 HashMap 初始容量** | `nodeMap` 预估 800 万节点 → `new HashMap<>(8_500_000)` |
| **避免 String.intern()** | OSM 标签 key 重复多但 intern 开销大，用 HashMap 去重即可 |
| **分块处理** | 若内存紧张，可分批解析多个 PBF 分片文件（如 Geofabrik 按省切割） |

### 7.2 图构建阶段

| 优化项 | 说明 |
|--------|------|
| **边去重** | 道路交汇处同一边可能被两个 Way 共享，用 `Set<String>` 去重 |
| **Haversine 精度** | 微观路径规划使用米级精度足够，不需要 Vincenty 公式 |
| **坐标缓存** | `nodeCoords` 统一存储 `double[2]`，避免重复拆箱/装箱 |

### 7.3 渲染阶段

| 优化项 | 说明 |
|--------|------|
| **边缓存重建** | 仅在 zoom/pan 变化时 `rebuildEdgeCache()`，避免 `paintComponent` 中重复计算 |
| **可见性裁剪** | 只遍历画布可视范围内的节点/边（当前未实现，可后续加入） |
| **地名碰撞检测** | 使用 `Rectangle.intersects` 贪心算法，O(n²) 复杂度，每次 paintComponent 实时计算 |

---

## 附录 A：依赖坐标（pom.xml 关键片段）

```xml
<dependency>
    <groupId>nl.tudelft.simulation</groupId>
    <artifactId>dsol-animation-gis</artifactId>
    <version>4.2.2</version>
</dependency>
<dependency>
    <groupId>org.openstreetmap.pbf</groupId>
    <artifactId>osmpbf</artifactId>
    <version>1.5.0</version>
</dependency>
<dependency>
    <groupId>org.openstreetmap.osmosis</groupId>
    <artifactId>osmosis-pbf</artifactId>
    <version>0.49.2</version>
</dependency>
```

## 附录 B：参考资源

- [OSM PBF 格式规范](https://wiki.openstreetmap.org/wiki/PBF_Format) — OSM Wiki 官方文档
- [Protocol Buffers 官方文档](https://protobuf.dev/) — Google 序列化框架
- [Osmosis 项目](https://github.com/openstreetmap/osmosis) — OSM 数据处理工具链
- [dsol 项目](https://github.com/averbraeck/dsol) — TU Delft 仿真与 GIS 库
