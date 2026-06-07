package nl.tudelft.simulation.dsol.animation.gis;

import java.io.Serializable;

/**
 * GisObject wraps a shape (SerializablePath or Point2D) with optional string attributes.
 * It is the fundamental unit of GIS data in the DSOL animation framework.
 */
public class GisObject implements Serializable {

    private static final long serialVersionUID = 1L;

    /** The shape (SerializablePath, Point2D, etc.). */
    private Object shape;

    /** Array of attribute values. */
    private String[] attributes;

    /**
     * Construct a new GisObject.
     * 
     * @param shape the shape object
     * @param attributes string attributes
     */
    public GisObject(final Object shape, final String[] attributes) {
        this.shape = shape;
        this.attributes = attributes;
    }

    /**
     * @return the shape object
     */
    public Object getShape() {
        return this.shape;
    }

    /**
     * @return the attribute values
     */
    public String[] getAttributeValues() {
        return this.attributes;
    }
}
