package nl.tudelft.simulation.dsol.animation.gis.map;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.geom.NoninvertibleTransformException;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.djutils.draw.bounds.Bounds2d;
import org.djutils.immutablecollections.ImmutableArrayList;
import org.djutils.immutablecollections.ImmutableHashMap;
import org.djutils.immutablecollections.ImmutableList;
import org.djutils.immutablecollections.ImmutableMap;
import org.djutils.logger.CategoryLogger;

import nl.tudelft.simulation.dsol.animation.gis.DsolGisException;
import nl.tudelft.simulation.dsol.animation.gis.FeatureInterface;
import nl.tudelft.simulation.dsol.animation.gis.GisMapInterface;
import nl.tudelft.simulation.dsol.animation.gis.GisObject;
import nl.tudelft.simulation.dsol.animation.gis.LayerInterface;
import nl.tudelft.simulation.dsol.animation.gis.MapImageInterface;
import nl.tudelft.simulation.dsol.animation.gis.MapUnits;
import nl.tudelft.simulation.dsol.animation.gis.SerializableRectangle2d;
import nl.tudelft.simulation.dsol.animation.gis.SerializablePath;

/**
 * GisMap is the core map implementation. It manages layers, renders them with an affine
 * coordinate transform, and supports zoom/pan operations.
 */
public class GisMap implements GisMapInterface {

    private static final long serialVersionUID = 1L;

    /** The current geographic extent (world coordinates). */
    private Bounds2d extent;

    /** Layer map keyed by name. */
    private Map<String, LayerInterface> layerMap = new LinkedHashMap<>();

    /** All layers in order. */
    private List<LayerInterface> allLayers = new ArrayList<>();

    /** Currently visible layers. */
    private List<LayerInterface> visibleLayers = new ArrayList<>();

    /** Whether the map has changed since last render. */
    private boolean same = false;

    /** The map image (size & background color). */
    private MapImageInterface image = new MapImage();

    /** The map name. */
    private String name;

    /** The map units. */
    private MapUnits units = MapUnits.METERS;

    /** Whether to draw the background. */
    private boolean drawBackground = true;

    /** Resolution for rendering (unused in this implementation). */
    private static final int RESOLUTION = 200;

    /** Default constructor. */
    public GisMap() {
        // fields initialized inline
    }

    @Override
    public void addLayer(final LayerInterface layer) {
        this.visibleLayers.add(layer);
        this.allLayers.add(layer);
        this.layerMap.put(layer.getName(), layer);
        this.same = false;
    }

    @Override
    public void setLayers(final List<LayerInterface> layers) {
        this.allLayers = new ArrayList<>(layers);
        this.visibleLayers = new ArrayList<>(layers);
        this.layerMap.clear();
        for (LayerInterface layer : layers) {
            this.layerMap.put(layer.getName(), layer);
        }
        this.same = false;
    }

    @Override
    public void setLayer(final int index, final LayerInterface layer) {
        this.allLayers.set(index, layer);
        if (this.allLayers.size() == this.visibleLayers.size()) {
            this.visibleLayers.add(index, layer);
        } else {
            this.visibleLayers.add(layer);
        }
        this.layerMap.put(layer.getName(), layer);
        this.same = false;
    }

    @Override
    public void hideLayer(final LayerInterface layer) {
        this.visibleLayers.remove(layer);
        this.same = false;
    }

    @Override
    public void hideLayer(final String layerName) throws RemoteException {
        if (this.layerMap.keySet().contains(layerName)) {
            hideLayer(this.layerMap.get(layerName));
        }
        this.same = false;
    }

    @Override
    public void showLayer(final LayerInterface layer) {
        this.visibleLayers.add(layer);
        this.same = false;
    }

    @Override
    public void showLayer(final String layerName) throws RemoteException {
        if (this.layerMap.keySet().contains(layerName)) {
            showLayer(this.layerMap.get(layerName));
        }
        this.same = false;
    }

    @Override
    public boolean isSame() throws RemoteException {
        boolean result = this.same;
        this.same = true;
        return result;
    }

    @Override
    public Graphics2D drawMap(final Graphics2D graphics) throws DsolGisException {
        // Draw background if enabled
        if (this.drawBackground) {
            graphics.setColor(this.image.getBackgroundColor());
            graphics.fillRect(0, 0,
                    (int) this.image.getSize().getWidth(),
                    (int) this.image.getSize().getHeight());
        }

        // Build the affine transform: world -> screen
        AffineTransform worldToScreen = new AffineTransform();
        worldToScreen.scale(
                this.image.getSize().getWidth() / this.extent.getDeltaX(),
                -this.image.getSize().getHeight() / this.extent.getDeltaY());
        worldToScreen.translate(-this.extent.getMinX(), -this.extent.getMinY() - this.extent.getDeltaY());

        AffineTransform screenToWorld = null;
        try {
            screenToWorld = worldToScreen.createInverse();
        } catch (NoninvertibleTransformException e) {
            CategoryLogger.always().error(e);
        }

        double scale = getScale();

        // Rendering hints
        graphics.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING,
                java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        for (LayerInterface layerIter : this.visibleLayers) {
            Layer layer = (Layer) layerIter;
            if (!layer.isDisplay()) {
                continue;
            }

            for (FeatureInterface feature : layer.getFeatures()) {
                List<GisObject> shapes = feature.getShapes(this.extent);
                SerializablePath path = null;

                for (GisObject gisObject : shapes) {
                    path = (SerializablePath) gisObject.getShape();

                    if (layer.isTransform()) {
                        path.transform(worldToScreen);
                    }

                    // Fill
                    if (feature.getFillColor() != null) {
                        graphics.setColor(feature.getFillColor());
                        graphics.fill(path);
                    }
                    // Outline
                    if (feature.getOutlineColor() != null) {
                        graphics.setColor(feature.getOutlineColor());
                        graphics.draw(path);
                    }

                    if (layer.isTransform()) {
                        path.transform(screenToWorld);
                    }
                }
            }
        }

        return graphics;
    }

    @Override
    public Bounds2d getExtent() {
        return this.extent;
    }

    @Override
    public MapImageInterface getImage() {
        return this.image;
    }

    @Override
    public ImmutableList<LayerInterface> getAllLayers() {
        return new ImmutableArrayList<>(this.allLayers);
    }

    @Override
    public ImmutableList<LayerInterface> getVisibleLayers() {
        return new ImmutableArrayList<>(this.visibleLayers);
    }

    @Override
    public ImmutableMap<String, LayerInterface> getLayerMap() throws RemoteException {
        return new ImmutableHashMap<>(this.layerMap);
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public double getScale() {
        return this.image.getSize().getWidth() / 182.88 * this.extent.getDeltaX();
    }

    @Override
    public double getUnitImageRatio() {
        return Math.min(
                this.extent.getDeltaX() / this.image.getSize().getWidth(),
                this.extent.getDeltaY() / this.image.getSize().getHeight());
    }

    @Override
    public MapUnits getUnits() {
        return this.units;
    }

    @Override
    public void setExtent(final Bounds2d extent) {
        this.extent = extent;
    }

    @Override
    public void setImage(final MapImageInterface image) {
        this.image = image;
    }

    @Override
    public void setName(final String name) {
        this.name = name;
    }

    @Override
    public void setUnits(final MapUnits units) {
        this.units = units;
    }

    @Override
    public void zoom(final double factor) {
        double f = (factor == 0.0) ? 1.0 : factor;

        double mx = getUnitImageRatio() * this.image.getSize().getWidth() + this.extent.getMinX();
        double my = getUnitImageRatio() * this.image.getSize().getHeight() + this.extent.getMinY();

        double cx = (mx - this.extent.getMinX()) / 2.0 + this.extent.getMinX();
        double cy = (my - this.extent.getMinY()) / 2.0 + this.extent.getMinY();

        double dx = (1.0 / f) * (mx - this.extent.getMinX());
        double dy = (1.0 / f) * (my - this.extent.getMinY());

        this.extent = new Bounds2d(
                cx - 0.5 * dx, cx + 0.5 * dx,
                cy - 0.5 * dy, cy + 0.5 * dy);
    }

    @Override
    public void zoomPoint(final java.awt.geom.Point2D point, final double factor) {
        double f = (factor == 0.0) ? 1.0 : factor;

        double mx = getUnitImageRatio() * this.image.getSize().getWidth() + this.extent.getMinX();
        double my = getUnitImageRatio() * this.image.getSize().getHeight() + this.extent.getMinY();

        double px = point.getX() / this.image.getSize().getWidth()
                * (mx - this.extent.getMinX()) + this.extent.getMinX();
        double py = my - point.getY() / this.image.getSize().getHeight()
                * (my - this.extent.getMinY());

        double dx = (1.0 / f) * (mx - this.extent.getMinX());
        double dy = (1.0 / f) * (my - getExtent().getMinY());

        this.extent = new Bounds2d(
                px - 0.5 * dx, px + 0.5 * dx,
                py - 0.5 * dy, py + 0.5 * dy);
    }

    @Override
    public void zoomRectangle(final SerializableRectangle2d rect) {
        double mx = getUnitImageRatio() * this.image.getSize().getWidth() + this.extent.getMinX();
        double my = getUnitImageRatio() * this.image.getSize().getHeight() + this.extent.getMinY();

        double dx = mx - this.extent.getMinX();
        double dy = my - this.extent.getMinY();

        double newMinX = this.extent.getMinX() + rect.getMinX() / this.image.getSize().getWidth() * dx;
        double newMinY = this.extent.getMinY()
                + (this.image.getSize().getHeight() - rect.getMaxY()) / this.image.getSize().getHeight() * dy;

        mx = newMinX + rect.getWidth() / this.image.getSize().getWidth() * dx;
        my = newMinY + (this.image.getSize().getHeight() - rect.getHeight()) / this.image.getSize().getHeight() * dy;

        this.extent = new Bounds2d(newMinX, mx, newMinY, my);
    }

    @Override
    public boolean isDrawBackground() {
        return this.drawBackground;
    }

    @Override
    public void setDrawBackground(final boolean drawBackground) {
        this.drawBackground = drawBackground;
    }
}
