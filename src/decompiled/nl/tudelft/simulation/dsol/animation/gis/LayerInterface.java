package nl.tudelft.simulation.dsol.animation.gis;

import java.io.Serializable;
import java.util.List;

/**
 * LayerInterface defines the contract for a named layer containing features.
 */
public interface LayerInterface extends Serializable {

    /** @return the features in this layer */
    List<FeatureInterface> getFeatures();

    /** @param features the features to set */
    void setFeatures(List<FeatureInterface> features);

    /** @param feature the feature to add */
    void addFeature(FeatureInterface feature);

    /** @return the layer name */
    String getName();

    /** @param name the layer name */
    void setName(String name);

    /** @return whether this layer should be displayed */
    boolean isDisplay();

    /** @param display whether to display this layer */
    void setDisplay(boolean display);

    /** @return whether coordinates should be transformed */
    boolean isTransform();

    /** @param transform whether to transform coordinates */
    void setTransform(boolean transform);
}
