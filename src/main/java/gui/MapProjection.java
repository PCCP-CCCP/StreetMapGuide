package gui;

import java.awt.Point;

/**
 * 经纬度 ↔ 屏幕像素 坐标转换工具。
 * <p>
 * 经度线性映射到 x（从左到右），纬度<b>反转</b>映射到 y（从上到下 = 北在上）。
 * 四周留有 5% 内边距，避免要素紧贴画布边缘。
 */
public class MapProjection {

    /** 经纬度边界（原始数据范围） */
    private final double minLon, maxLon, minLat, maxLat;

    /** 地图区域中心点（经纬度），缩放以该点为中心保持不变 */
    private final double centerLon, centerLat;

    /** 画布尺寸（像素），可通过 {@link #setCanvasSize} 更新 */
    private int width, height;

    /** 基础缩放因子（像素/度），不含 zoom */
    private double scaleX, scaleY;

    /** 用户缩放因子：1.0=适合画布, >1 放大, <1 缩小。范围 [0.1, 50.0] */
    private double zoom = 1.0;

    /** 拖拽平移偏移量（像素）。正=向右/下，负=向左/上 */
    private double offsetX = 0, offsetY = 0;

    /** 带 padding 的边界（实际映射起点） */
    private final double originLon, originLatMax;

    /** 内边距比例 */
    private static final double PADDING = 0.05;

    /**
     * @param minLon 最小经度
     * @param maxLon 最大经度
     * @param minLat 最小纬度
     * @param maxLat 最大纬度
     * @param width  画布宽度（像素）
     * @param height 画布高度（像素）
     */
    public MapProjection(double minLon, double maxLon, double minLat, double maxLat,
                         int width, int height) {
        this.minLon = minLon;
        this.maxLon = maxLon;
        this.minLat = minLat;
        this.maxLat = maxLat;
        this.width = width;
        this.height = height;

        // 地图区域中心点（缩放时该点屏幕位置不变）
        this.centerLon = (minLon + maxLon) / 2.0;
        this.centerLat = (minLat + maxLat) / 2.0;

        double lonRange = maxLon - minLon;
        double latRange = maxLat - minLat;

        // 单点数据：避免除以零
        if (lonRange < 1e-9) lonRange = 0.01;
        if (latRange < 1e-9) latRange = 0.01;

        double padLon = lonRange * PADDING;
        double padLat = latRange * PADDING;

        this.originLon = minLon - padLon;
        this.originLatMax = maxLat + padLat;

        recomputeScales();
    }

    // ==================== 坐标转换 ====================

    /**
     * 经纬度 → 屏幕像素。
     * <p>
     * 以地图中心 (centerLon, centerLat) 为缩放锚点，叠加拖拽偏移：
     * <pre>
     *   pixelX = width/2  + (lon - centerLon) * scaleX * zoom + offsetX
     *   pixelY = height/2 + (centerLat - lat) * scaleY * zoom + offsetY
     * </pre>
     *
     * @param lon 经度
     * @param lat 纬度
     * @return 屏幕坐标
     */
    public Point toPixel(double lon, double lat) {
        int x = (int) Math.round(width / 2.0 + (lon - centerLon) * scaleX * zoom + offsetX);
        int y = (int) Math.round(height / 2.0 + (centerLat - lat) * scaleY * zoom + offsetY);
        return new Point(x, y);
    }

    /**
     * 屏幕像素 → 经纬度（用于鼠标点选反向推算）。
     *
     * @param x 屏幕 x 坐标
     * @param y 屏幕 y 坐标
     * @return [lon, lat]
     */
    public double[] toGeo(int x, int y) {
        double lon = centerLon + (x - offsetX - width / 2.0) / (scaleX * zoom);
        double lat = centerLat - (y - offsetY - height / 2.0) / (scaleY * zoom);
        return new double[]{lon, lat};
    }

    // ==================== 查询 ====================

    public double getMinLon()  { return minLon; }
    public double getMaxLon()  { return maxLon; }
    public double getMinLat()  { return minLat; }
    public double getMaxLat()  { return maxLat; }
    public int    getWidth()   { return width; }
    public int    getHeight()  { return height; }
    public double getCenterLon() { return centerLon; }
    public double getCenterLat() { return centerLat; }

    /** @return 当前用户缩放倍数 */
    public double getZoom() { return zoom; }

    /**
     * 设置用户缩放倍数（自动钳位到 [0.1, 50.0]）。
     * 调用方需在设置后触发重绘（如 {@code rebuildEdgeCache() + repaint()}）。
     */
    public void setZoom(double zoom) {
        if (zoom < 0.1) zoom = 0.1;
        if (zoom > 50.0) zoom = 50.0;
        this.zoom = zoom;
    }

    // ── 拖拽偏移 ──

    /** @return X 方向拖拽偏移（像素，正=右移） */
    public double getOffsetX() { return offsetX; }
    /** @return Y 方向拖拽偏移（像素，正=下移） */
    public double getOffsetY() { return offsetY; }

    /** 直接设置拖拽偏移 */
    public void setOffset(double offsetX, double offsetY) {
        this.offsetX = offsetX;
        this.offsetY = offsetY;
    }

    /** 累加拖拽偏移（用于增量平移） */
    public void addOffset(double dx, double dy) {
        this.offsetX += dx;
        this.offsetY += dy;
    }

    /** 将拖拽偏移归零 */
    public void resetOffset() {
        this.offsetX = 0;
        this.offsetY = 0;
    }

    /**
     * 更新画布尺寸并重新计算基础缩放因子。
     * 用于窗口大小变化时同步更新投影参数。
     */
    public void setCanvasSize(int width, int height) {
        this.width = width;
        this.height = height;
        recomputeScales();
    }

    /** @return 每个像素对应的经度度数（考虑 zoom） */
    public double getDegreesPerPixelX() { return 1.0 / (scaleX * zoom); }
    /** @return 每个像素对应的纬度度数（考虑 zoom） */
    public double getDegreesPerPixelY() { return 1.0 / (scaleY * zoom); }

    // ==================== 内部工具 ====================

    /** 根据当前画布尺寸重新计算 scaleX / scaleY */
    private void recomputeScales() {
        double lonRange = maxLon - minLon;
        double latRange = maxLat - minLat;
        if (lonRange < 1e-9) lonRange = 0.01;
        if (latRange < 1e-9) latRange = 0.01;
        double padLon = lonRange * PADDING;
        double padLat = latRange * PADDING;
        this.scaleX = width / (lonRange + 2 * padLon);
        this.scaleY = height / (latRange + 2 * padLat);
    }
}
