package gui;

import java.awt.*;
import java.util.List;
import javax.swing.*;

import pathfinding.DijkstraPathFinder;
import pathfinding.PathFinder;
import pathfinding.PathResult;
import pathfinding.RoadGraph;

/**
 * 主窗口 — 地图显示 + 路径规划控制面板。
 * <p>
 * 布局：中心为 {@link MapPanel}，底部为控制面板（按钮 + 状态标签）。
 */
public class MainFrame extends JFrame {

    private final RoadGraph graph;
    private final MapProjection projection;
    private final MapPanel mapPanel;
    private final PathFinder pathFinder;

    // 控制面板组件
    private final JLabel lblStartInfo;
    private final JLabel lblEndInfo;
    private final JLabel lblDistInfo;
    private final JButton btnSelectStart;
    private final JButton btnSelectEnd;
    private final JButton btnCalcPath;
    private final JButton btnClear;

    // ==================== 构造 ====================

    public MainFrame(RoadGraph graph, MapProjection projection, List<NamedPlace> namedPlaces) {
        super("StreetMapGuide — OSM 地图路径规划");
        this.graph = graph;
        this.projection = projection;
        this.pathFinder = new DijkstraPathFinder();

        // ── 地图面板 ──
        mapPanel = new MapPanel();
        mapPanel.setGraph(graph);
        mapPanel.setProjection(projection);
        mapPanel.setNamedPlaces(namedPlaces);

        // ── 控制面板 ──
        lblStartInfo = new JLabel("起点: 未选择");
        lblEndInfo   = new JLabel("终点: 未选择");
        lblDistInfo  = new JLabel("距离: --");

        btnSelectStart = new JButton("📍 选起点");
        btnSelectEnd   = new JButton("🏁 选终点");
        btnCalcPath    = new JButton("🔍 计算路径");
        btnClear       = new JButton("✕ 清除");

        JPanel controlPanel = buildControlPanel();

        // ── 布局 ──
        setLayout(new BorderLayout());
        add(mapPanel, BorderLayout.CENTER);
        add(controlPanel, BorderLayout.SOUTH);

        // 注册事件
        registerActions();

        // ── 窗口设置 ──
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1100, 900);
        setLocationRelativeTo(null); // 居中
    }

    // ==================== 控制面板构建 ====================

    private JPanel buildControlPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 6));
        panel.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));

        panel.add(btnSelectStart);
        panel.add(btnSelectEnd);
        panel.add(Box.createHorizontalStrut(10));
        panel.add(btnCalcPath);
        panel.add(btnClear);
        panel.add(Box.createHorizontalStrut(15));
        panel.add(lblStartInfo);
        panel.add(new JLabel("  |  "));
        panel.add(lblEndInfo);
        panel.add(new JLabel("  |  "));
        panel.add(lblDistInfo);

        return panel;
    }

    // ==================== 事件绑定 ====================

    private void registerActions() {
        btnSelectStart.addActionListener(e -> {
            mapPanel.setSelectingStart(true);
            updateButtonStyles();
            lblStartInfo.setText("起点: 在地图上点击...");
        });

        btnSelectEnd.addActionListener(e -> {
            mapPanel.setSelectingStart(false);
            updateButtonStyles();
            lblEndInfo.setText("终点: 在地图上点击...");
        });

        btnCalcPath.addActionListener(e -> {
            long startId = mapPanel.getStartNodeId();
            long endId = mapPanel.getEndNodeId();
            if (startId < 0 || endId < 0) {
                JOptionPane.showMessageDialog(this,
                        "请先选择起点和终点。",
                        "提示", JOptionPane.WARNING_MESSAGE);
                return;
            }
            // 执行寻路
            PathResult result = pathFinder.findPath(graph, startId, endId);
            mapPanel.setPathResult(result);

            if (result.isReachable()) {
                lblDistInfo.setText(String.format("距离: %.1f 米", result.getTotalDistance()));
            } else {
                lblDistInfo.setText("距离: 不可达");
                JOptionPane.showMessageDialog(this,
                        "起点与终点之间无可达路径。",
                        "无法到达", JOptionPane.INFORMATION_MESSAGE);
            }
        });

        btnClear.addActionListener(e -> {
            mapPanel.clearSelection();
            lblStartInfo.setText("起点: 未选择");
            lblEndInfo.setText("终点: 未选择");
            lblDistInfo.setText("距离: --");
            updateButtonStyles();
        });

        // 监听 MapPanel 的选点变化（通过重绘后更新标签）
        // 在 MapPanel 的 mouseClicked 后无法直接回调，改用定时轮询方式。
        // 更优方案：让 MapPanel 持有回调或使用 PropertyChangeListener。
        // 这里采用简易方式：按钮点击后通过 invokeLater 更新。
        mapPanel.addPropertyChangeListener("startNodeId", evt -> updateInfoLabels());

        // 简单方案：每次重绘后检查（下策），改用 ComponentListener + 手动刷新
        // 这里直接用 Timer 每秒检查一次状态变化（轻量）
        Timer refreshTimer = new Timer(200, evt -> updateInfoLabels());
        refreshTimer.start();
    }

    /** 更新按钮高亮样式，反映当前选择模式 */
    private void updateButtonStyles() {
        boolean selStart = mapPanel.isSelectingStart();
        btnSelectStart.setBackground(selStart ? new Color(200, 255, 200) : null);
        btnSelectEnd.setBackground(selStart ? null : new Color(255, 200, 200));
    }

    /** 用当前 MapPanel 的节点 ID 刷新信息标签 */
    private void updateInfoLabels() {
        long sid = mapPanel.getStartNodeId();
        long eid = mapPanel.getEndNodeId();

        if (sid >= 0) {
            double[] c = graph.getCoord(sid);
            lblStartInfo.setText(c != null
                    ? String.format("起点: #%d (%.4f, %.4f)", sid, c[1], c[0])
                    : "起点: #" + sid);
        }
        if (eid >= 0) {
            double[] c = graph.getCoord(eid);
            lblEndInfo.setText(c != null
                    ? String.format("终点: #%d (%.4f, %.4f)", eid, c[1], c[0])
                    : "终点: #" + eid);
        }
    }
}
