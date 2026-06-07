package nl.tudelft.simulation.dsol.animation.gis.osm;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.openstreetmap.osmosis.core.container.v0_6.EntityContainer;
import org.openstreetmap.osmosis.core.domain.v0_6.Entity;
import org.openstreetmap.osmosis.core.domain.v0_6.Node;
import org.openstreetmap.osmosis.core.domain.v0_6.Relation;
import org.openstreetmap.osmosis.core.domain.v0_6.Tag;
import org.openstreetmap.osmosis.core.domain.v0_6.Way;
import org.openstreetmap.osmosis.core.domain.v0_6.WayNode;
import org.openstreetmap.osmosis.core.task.v0_6.Sink;

import nl.tudelft.simulation.dsol.animation.gis.FeatureInterface;
import nl.tudelft.simulation.dsol.animation.gis.GisObject;
import nl.tudelft.simulation.dsol.animation.gis.SerializablePath;
import nl.tudelft.simulation.dsol.animation.gis.transform.CoordinateTransform;

/**
 * OsmLayerSink implements the Osmosis Sink interface to process OSM entities.
 * It collects nodes, matches way tags against a list of requested features,
 * and converts matching ways into SerializablePath shapes wrapped in GisObjects.
 */
public class OsmLayerSink implements Sink {

    /** Collected ways (tag-matched). */
    private Map<Long, MiniWay> ways = new HashMap<>();

    /** Collected nodes (all nodes seen). */
    private Map<Long, MiniNode> nodes = new HashMap<>();

    /** Feature definitions to match against OSM tags. */
    private final List<FeatureInterface> featuresToRead;

    /** Coordinate transform (e.g., WGS84 to screen). */
    private final CoordinateTransform coordinateTransform;

    /**
     * Construct a new OsmLayerSink.
     * 
     * @param featuresToRead list of feature definitions to match
     * @param coordinateTransform coordinate transformation
     */
    public OsmLayerSink(final List<FeatureInterface> featuresToRead,
            final CoordinateTransform coordinateTransform) {
        this.featuresToRead = featuresToRead;
        this.coordinateTransform = coordinateTransform;
    }

    @Override
    public void process(final EntityContainer entityContainer) {
        Entity entity = entityContainer.getEntity();

        if (entity instanceof Node) {
            Node node = (Node) entity;
            MiniNode mini = new MiniNode(node.getId(), (float) node.getLatitude(), (float) node.getLongitude());
            this.nodes.put(mini.id, mini);
        } else if (entity instanceof Way) {
            boolean found = false;
            FeatureInterface matchedFeature = null;

            Iterator<Tag> tagIter = entity.getTags().iterator();
            while (tagIter.hasNext() && !found) {
                Tag tag = tagIter.next();
                String key = tag.getKey();
                String value = tag.getValue();

                for (FeatureInterface feature : this.featuresToRead) {
                    if ("*".equals(feature.getKey())) {
                        matchedFeature = feature;
                        found = true;
                        break;
                    }
                    if (feature.getKey().equals(key)) {
                        if ("*".equals(feature.getValue()) || feature.getValue().equals(value)) {
                            matchedFeature = feature;
                            found = true;
                            break;
                        }
                    }
                }
            }

            if (found) {
                Way way = (Way) entity;
                MiniWay mini = new MiniWay(way.getId(), matchedFeature, way.getWayNodes());
                this.ways.put(mini.id, mini);
            }
        } else if (entity instanceof Relation) {
            // Relations are currently ignored (only iterates tags without action)
            Iterator<Tag> tagIter = entity.getTags().iterator();
            while (tagIter.hasNext()) {
                Tag tag = tagIter.next();
                // no-op: relations skipped
            }
        }
    }

    @Override
    public void initialize(final Map<String, Object> metaData) {
        // no initialization needed
    }

    @Override
    public void complete() {
        for (MiniWay way : this.ways.values()) {
            addWay(way);
        }
    }

    /**
     * Convert a MiniWay into a SerializablePath and add it to the feature's shape list.
     * 
     * @param way the mini way to add
     */
    private void addWay(final MiniWay way) {
        SerializablePath path = new SerializablePath(1, way.wayNodesLat.length);
        boolean moved = false;

        for (int i = 0; i < way.wayNodesLat.length; i++) {
            float[] xy;
            if (way.wayNodesId[i] != 0L) {
                MiniNode node = this.nodes.get(way.wayNodesId[i]);
                xy = this.coordinateTransform.floatTransform(node.lon, node.lat);
            } else {
                xy = this.coordinateTransform.floatTransform(way.wayNodesLon[i], way.wayNodesLat[i]);
            }

            if (!moved) {
                path.moveTo(xy[0], xy[1]);
                moved = true;
            }
            path.lineTo(xy[0], xy[1]);
        }

        String[] attributes = new String[0];
        way.feature.getShapes().add(new GisObject(path, attributes));
    }

    @Override
    public void close() {
        // no cleanup needed
    }

    // ============= INNER CLASSES =============

    /**
     * Lightweight node representation storing id, lat, lon.
     */
    protected static class MiniNode {
        protected long id;
        protected float lat;
        protected float lon;

        public MiniNode(final long id, final float lat, final float lon) {
            this.id = id;
            this.lat = lat;
            this.lon = lon;
        }
    }

    /**
     * Lightweight way representation storing id, matched feature, and way node arrays.
     */
    protected static class MiniWay {
        protected long id;
        protected FeatureInterface feature;
        protected float[] wayNodesLat;
        protected float[] wayNodesLon;
        protected long[] wayNodesId;

        public MiniWay(final long id, final FeatureInterface feature,
                final Collection<WayNode> wayNodes) {
            this.id = id;
            this.feature = feature;
            this.wayNodesLat = new float[wayNodes.size()];
            this.wayNodesLon = new float[wayNodes.size()];
            this.wayNodesId = new long[wayNodes.size()];

            int i = 0;
            for (WayNode wn : wayNodes) {
                this.wayNodesLat[i] = (float) wn.getLatitude();
                this.wayNodesLon[i] = (float) wn.getLongitude();
                this.wayNodesId[i] = wn.getNodeId();
                i++;
            }
        }
    }
}
