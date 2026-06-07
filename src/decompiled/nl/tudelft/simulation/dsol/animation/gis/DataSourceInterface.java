package nl.tudelft.simulation.dsol.animation.gis;

import java.io.Serializable;
import java.rmi.RemoteException;
import java.util.List;

import org.djutils.draw.bounds.Bounds2d;
import org.djutils.immutablecollections.ImmutableList;
import org.djutils.immutablecollections.ImmutableMap;

/**
 * DataSourceInterface is the base interface for OSM data sources.
 */
public interface DataSourceInterface extends Serializable {

    /** @return the URL of the data source */
    URL getURL();

    /** @return the list of features to read */
    List<FeatureInterface> getFeatures();

    /** Populate shapes by reading the data source. */
    void populateShapes() throws IOException;

    /** @return whether the data source is dynamic (changes over time) */
    boolean isDynamic();
}
