package nl.tudelft.simulation.dsol.animation.gis;

import java.awt.Color;
import java.io.Serializable;
import java.util.List;

import org.djutils.draw.bounds.Bounds2d;

/**
 * FeatureInterface defines the contract for OSM tag-based feature definitions.
 * A feature matches OSM ways by key/value pairs and holds the resulting shapes.
 */
public interface FeatureInterface extends Serializable {

    /** @return the OSM tag key to match (e.g., "highway") */
    String getKey();

    /** @param key the OSM tag key */
    void setKey(String key);

    /** @return the OSM tag value to match (e.g., "primary"), or "*" for any */
    String getValue();

    /** @return whether this feature has been populated with data */
    boolean isInitialized();

    /** @param initialized whether this feature has been populated */
    void setInitialized(boolean initialized);

    /** @return the number of shapes */
    int getNumShapes();

    /**
     * @param index the shape index
     * @return the GisObject at that index
     * @throws IndexOutOfBoundsException if index out of range
     */
    GisObject getShape(int index) throws IndexOutOfBoundsException;

    /** @return the list of all GisObjects */
    List<GisObject> getShapes();

    /**
     * @param extent the bounding box to filter by
     * @return shapes that overlap the given extent
     */
    List<GisObject> getShapes(Bounds2d extent);

    /** @param value the OSM tag value */
    void setValue(String value);

    /** @return the fill color, or null for no fill */
    Color getFillColor();

    /** @param fillColor the fill color */
    void setFillColor(Color fillColor);

    /** @return the outline color */
    Color getOutlineColor();

    /** @param outlineColor the outline color */
    void setOutlineColor(Color outlineColor);
}
