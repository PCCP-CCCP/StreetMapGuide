package nl.tudelft.simulation.dsol.animation.gis;

import java.awt.Shape;
import java.awt.geom.Path2D;
import java.awt.geom.PathIterator;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

/**
 * SerializablePath extends Path2D.Float with custom serialization.
 * Path2D.Float is not serializable by default; this class provides
 * writeObject/readObject methods that serialize the path segments manually.
 */
public class SerializablePath extends Path2D.Float implements Serializable, Shape, Cloneable {

    private static final long serialVersionUID = 1L;

    /** Default constructor. */
    public SerializablePath() {
        super();
    }

    /**
     * Construct with the given winding rule.
     * 
     * @param rule the winding rule (Path2D.WIND_EVEN_ODD or WIND_NON_ZERO)
     */
    public SerializablePath(final int rule) {
        super(rule);
    }

    /**
     * Construct with the given initial capacity.
     * 
     * @param rule the winding rule
     * @param initialCapacity the initial capacity
     */
    public SerializablePath(final int rule, final int initialCapacity) {
        super(rule, initialCapacity);
    }

    /**
     * Construct a copy of the given shape.
     * 
     * @param s the shape to copy
     */
    public SerializablePath(final Shape s) {
        super(s);
    }

    /**
     * Custom serialization: write a float array to the stream.
     * 
     * @param out the output stream
     * @param array the float array
     * @param count number of elements to write
     * @throws IOException on I/O error
     */
    private void writeFloatArray(final ObjectOutputStream out, final float[] array, final int count) throws IOException {
        for (int i = 0; i < count; i++) {
            out.writeFloat(array[i]);
        }
    }

    /**
     * Custom serialization: write the path as segment type + coords.
     * 
     * @param out the output stream
     * @throws IOException on I/O error
     */
    private void writeObject(final ObjectOutputStream out) throws IOException {
        out.writeInt(getWindingRule());
        float[] coords = new float[6];
        PathIterator iter = getPathIterator(null);

        while (!iter.isDone()) {
            int type = iter.currentSegment(coords);
            out.writeInt(type);
            switch (type) {
                case PathIterator.SEG_CLOSE:
                    break;
                case PathIterator.SEG_CUBICTO:
                    writeFloatArray(out, coords, 6);
                    break;
                case PathIterator.SEG_QUADTO:
                    writeFloatArray(out, coords, 4);
                    break;
                case PathIterator.SEG_LINETO:
                    writeFloatArray(out, coords, 2);
                    break;
                case PathIterator.SEG_MOVETO:
                    writeFloatArray(out, coords, 2);
                    break;
                default:
                    throw new RuntimeException("unknown segment");
            }
            iter.next();
        }
        out.writeInt(-1); // end marker
    }

    /**
     * Custom deserialization: reconstruct the path from segment type + coords.
     * 
     * @param in the input stream
     * @throws IOException on I/O error
     */
    private void readObject(final ObjectInputStream in) throws IOException {
        int rule = in.readInt();
        // Note: winding rule is consumed but applied via moveTo/lineTo etc.

        int type;
        while ((type = in.readInt()) != -1) {
            switch (type) {
                case PathIterator.SEG_CLOSE:
                    closePath();
                    break;
                case PathIterator.SEG_CUBICTO:
                    curveTo(in.readFloat(), in.readFloat(), in.readFloat(),
                            in.readFloat(), in.readFloat(), in.readFloat());
                    break;
                case PathIterator.SEG_LINETO:
                    lineTo(in.readFloat(), in.readFloat());
                    break;
                case PathIterator.SEG_MOVETO:
                    moveTo(in.readFloat(), in.readFloat());
                    break;
                case PathIterator.SEG_QUADTO:
                    quadTo(in.readFloat(), in.readFloat(), in.readFloat(), in.readFloat());
                    break;
                default:
                    throw new RuntimeException("unknown segment");
            }
        }
    }
}
