package nl.tudelft.simulation.dsol.animation.gis.map;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

import org.djutils.draw.bounds.Bounds2d;
import org.djutils.logger.CategoryLogger;

import nl.tudelft.simulation.dsol.animation.gis.FeatureInterface;
import nl.tudelft.simulation.dsol.animation.gis.GisObject;
import nl.tudelft.simulation.dsol.animation.gis.SerializablePath;
import nl.tudelft.simulation.language.d2.Shape;

/**
 * Feature defines an OSM tag key/value pair to match, with associated shapes and styling.
 * Default key and value are "*" (wildcard). Default outline color is black, no fill.
 */
public class Feature implements FeatureInterface {

    private static final long serialVersionUID = 1L;

    /** The OSM tag key to match (e.g., "highway"). "*" matches all keys. */
    private String key = "*";

    /** The OSM tag value (e.g., "primary"). "*" matches all values. */
    private String value = "*";

    /** The shapes (GisObjects) belonging to this feature. */
    private List<GisObject> shapes = new ArrayList<>();

    /** Fill color (null = no fill). */
    private Color fillColor = null;

    /** Outline color (default black). */
    private Color outlineColor = Color.BLACK;

    /** Whether this feature has been initialized with data. */
    private boolean initialized = false;

    /** Default constructor. */
    public Feature() {
        // fields initialized inline
    }

    @Override
    public final String getKey() {
        return this.key;
    }

    @Override
    public final void setKey(final String key) {
        this.key = key;
    }

    @Override
    public final String getValue() {
        return this.value;
    }

    @Override
    public final void setValue(final String value) {
        this.value = value;
    }

    @Override
    public boolean isInitialized() {
        return this.initialized;
    }

    @Override
    public void setInitialized(final boolean initialized) {
        this.initialized = initialized;
    }

    @Override
    public int getNumShapes() {
        return this.shapes.size();
    }

    @Override
    public GisObject getShape(final int index) throws IndexOutOfBoundsException {
        return this.shapes.get(index);
    }

    @Override
    public List<GisObject> getShapes() {
        return this.shapes;
    }

    @Override
    public List<GisObject> getShapes(final Bounds2d extent) {
        List<GisObject> result = new ArrayList<>();
        java.awt.geom.Rectangle2D rect = extent.toRectangle2D();

        for (GisObject gisObject : this.shapes) {
            if (gisObject.getShape() instanceof SerializablePath) {
                if (Shape.overlaps(rect,
                        ((SerializablePath) gisObject.getShape()).getBounds2D())) {
                    result.add(gisObject);
                }
            } else if (gisObject.getShape() instanceof java.awt.geom.Point2D) {
                if (rect.contains((java.awt.geom.Point2D) gisObject.getShape())) {
                    result.add(gisObject);
                }
            } else {
                CategoryLogger.always().error("Unknown shape type: " + gisObject);
            }
        }
        return result;
    }

    @Override
    public Color getFillColor() {
        return this.fillColor;
    }

    @Override
    public void setFillColor(final Color fillColor) {
        this.fillColor = fillColor;
    }

    @Override
    public Color getOutlineColor() {
        return this.outlineColor;
    }

    @Override
    public void setOutlineColor(final Color outlineColor) {
        this.outlineColor = outlineColor;
    }
}
