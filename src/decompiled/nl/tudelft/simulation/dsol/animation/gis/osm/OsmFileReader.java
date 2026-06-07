package nl.tudelft.simulation.dsol.animation.gis.osm;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.List;

import org.openstreetmap.osmosis.xml.common.CompressionMethod;

import crosby.binary.osmosis.OsmosisReader;
import nl.tudelft.simulation.dsol.animation.gis.DataSourceInterface;
import nl.tudelft.simulation.dsol.animation.gis.FeatureInterface;
import nl.tudelft.simulation.dsol.animation.gis.transform.CoordinateTransform;

/**
 * OsmFileReader reads OSM data from a URL (file or web). Supports .pbf, .osm.gz, .osm.bz2, and .osm formats.
 * <p>
 * This class implements DataSourceInterface and can be used as a data source for OSM layers.
 * When populateShapes() is called, it creates an OsmLayerSink, sets up the appropriate reader
 * (OsmosisReader for PBF, XmlStreamReader for XML), and runs the parsing in a separate thread.
 */
public class OsmFileReader implements DataSourceInterface {

    private static final long serialVersionUID = 1L;

    private URL osmURL;

    private final CoordinateTransform coordinateTransform;

    private final List<FeatureInterface> featuresToRead;

    /**
     * Construct a new OsmFileReader.
     * 
     * @param osmURL the URL pointing to the OSM file (supports file:, http:, etc.)
     * @param coordinateTransform the coordinate transform to apply (e.g., NoTransform for WGS84)
     * @param featuresToRead list of FeatureInterface definitions describing which OSM tags to extract
     * @throws IOException if the URL cannot be accessed
     */
    public OsmFileReader(final URL osmURL, final CoordinateTransform coordinateTransform,
            final List<FeatureInterface> featuresToRead) throws IOException {
        this.osmURL = osmURL;
        this.coordinateTransform = coordinateTransform;
        this.featuresToRead = featuresToRead;
    }

    @Override
    public List<FeatureInterface> getFeatures() {
        return this.featuresToRead;
    }

    @Override
    public void populateShapes() throws IOException {
        String urlLower = this.osmURL.toString().toLowerCase();
        File file = new File(this.osmURL.getPath());

        OsmLayerSink sink = new OsmLayerSink(this.featuresToRead, this.coordinateTransform);
        CompressionMethod compression = CompressionMethod.None;
        boolean isPbf = false;

        if (urlLower.endsWith(".pbf")) {
            isPbf = true;
        } else if (urlLower.endsWith(".gz")) {
            compression = CompressionMethod.GZip;
        } else if (urlLower.endsWith(".bz2")) {
            compression = CompressionMethod.BZip2;
        }

        RunnableSource source;
        if (isPbf) {
            source = new OsmosisReader(file);
            System.out.println("Reading PBF OSM file: " + urlLower);
        } else {
            InputStream in = this.osmURL.openStream();
            source = new XmlStreamReader(in, false, compression);
            System.out.println("Reading XML OSM file: " + urlLower);
        }

        source.setSink(sink);
        Thread readerThread = new Thread(source);
        readerThread.start();

        try {
            while (readerThread.isAlive()) {
                readerThread.join();
            }
        } catch (InterruptedException e) {
            System.err.println("The map reader thread got a problem!");
            throw new IOException(e);
        }

        System.out.println("OSM layer has been read");
    }

    @Override
    public URL getURL() {
        return this.osmURL;
    }

    @Override
    public boolean isDynamic() {
        return false;
    }
}
