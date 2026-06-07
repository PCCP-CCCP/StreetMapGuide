package nl.tudelft.simulation.dsol.animation.gis.map;

import java.util.ArrayList;
import java.util.List;

import nl.tudelft.simulation.dsol.animation.gis.FeatureInterface;
import nl.tudelft.simulation.dsol.animation.gis.LayerInterface;

/**
 * Layer is a named collection of Features with display and transform flags.
 */
public class Layer implements LayerInterface {

    private static final long serialVersionUID = 1L;

    /** Layer name. */
    private String name;

    /** Whether this layer is displayed. */
    private boolean display = true;

    /** Whether coordinates should be transformed to screen space. */
    private boolean transform = false;

    /** Features in this layer. */
    private List<FeatureInterface> features = new ArrayList<>();

    /** Default constructor. */
    public Layer() {
        // fields initialized inline
    }

    @Override
    public List<FeatureInterface> getFeatures() {
        return this.features;
    }

    @Override
    public void setFeatures(final List<FeatureInterface> features) {
        this.features = features;
    }

    @Override
    public void addFeature(final FeatureInterface feature) {
        this.features.add(feature);
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public void setName(final String name) {
        this.name = name;
    }

    @Override
    public boolean isDisplay() {
        return this.display;
    }

    @Override
    public void setDisplay(final boolean display) {
        this.display = display;
    }

    @Override
    public boolean isTransform() {
        return this.transform;
    }

    @Override
    public void setTransform(final boolean transform) {
        this.transform = transform;
    }
}
