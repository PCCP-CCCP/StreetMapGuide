package nl.tudelft.simulation.dsol.animation.gis.osm;

import java.io.InputStream;
import java.util.Collections;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.xml.parsers.SAXParser;

import org.openstreetmap.osmosis.core.OsmosisRuntimeException;
import org.openstreetmap.osmosis.core.task.v0_6.RunnableSource;
import org.openstreetmap.osmosis.core.task.v0_6.Sink;
import org.openstreetmap.osmosis.xml.common.CompressionActivator;
import org.openstreetmap.osmosis.xml.common.CompressionMethod;
import org.openstreetmap.osmosis.xml.v0_6.impl.OsmHandler;

/**
 * XmlStreamReader reads an XML OSM file from an InputStream using SAX parsing.
 * It wraps the InputStream with optional decompression (GZip, BZip2) and
 * feeds the parsed entities to the registered Sink.
 */
public class XmlStreamReader implements RunnableSource {

    private static Logger log = Logger.getLogger(XmlStreamReader.class.getName());

    private Sink sink;

    private InputStream inputStream;

    private final boolean enableDateParsing;

    private final CompressionMethod compressionMethod;

    /**
     * Construct a new XmlStreamReader.
     * 
     * @param inputStream the input stream to read from
     * @param enableDateParsing whether to parse OSM timestamps
     * @param compressionMethod the compression method (None, GZip, BZip2)
     */
    public XmlStreamReader(final InputStream inputStream, final boolean enableDateParsing,
            final CompressionMethod compressionMethod) {
        this.inputStream = inputStream;
        this.enableDateParsing = enableDateParsing;
        this.compressionMethod = compressionMethod;
    }

    @Override
    public void setSink(final Sink sink) {
        this.sink = sink;
    }

    @Override
    public void run() {
        try {
            this.sink.initialize(Collections.emptyMap());

            // Decompress if needed
            this.inputStream = new CompressionActivator(this.compressionMethod)
                    .createCompressionInputStream(this.inputStream);

            SAXParser parser = org.openstreetmap.osmosis.xml.common.SaxParserFactory.createParser();
            parser.parse(this.inputStream,
                    new OsmHandler(this.sink, this.enableDateParsing));

            this.sink.complete();
        } catch (SAXParseException e) {
            throw new OsmosisRuntimeException(
                    "Unable to parse XML at line " + e.getLineNumber() + " col " + e.getColumnNumber(), e);
        } catch (SAXException e) {
            throw new OsmosisRuntimeException("Unable to parse XML.", e);
        } catch (IOException e) {
            throw new OsmosisRuntimeException("Unable to read XML file from input stream.", e);
        } finally {
            this.sink.close();
            if (this.inputStream != null) {
                try {
                    this.inputStream.close();
                } catch (IOException e) {
                    log.log(Level.SEVERE, "Unable to close input stream.", e);
                }
                this.inputStream = null;
            }
        }
    }
}
