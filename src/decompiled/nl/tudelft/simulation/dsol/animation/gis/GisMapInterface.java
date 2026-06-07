package nl.tudelft.simulation.dsol.animation.gis;

import java.io.Serializable;
import java.rmi.RemoteException;
import java.util.List;

import org.djutils.draw.bounds.Bounds2d;
import org.djutils.immutablecollections.ImmutableList;
import org.djutils.immutablecollections.ImmutableMap;

/**
 * GisMapInterface defines the contract for a GIS map that manages layers
 * and provides rendering and zooming capabilities.
 */
public interface GisMapInterface extends Serializable {

    byte TEXT = 0;
    byte ANGLEDEG = 1;
    byte ANGLERAD = 2;
    byte IMAGE = 3;
    byte AIRPHOTO = 4;
    byte POLYGON = 5;
    byte POINT = 6;
    byte LINE = 7;

    double FEET_TO_METER = 0.3048006096012192;
    double INCH_TO_METER = 0.0254000508001016;
    double KILOMETER_TO_METER = 1000.0;
    double MILES_TO_METER = 1609.344;
    double DD_TO_METER = 111319.5;
    double CENTIMETER_PER_INCH = 2.54;

    /** Render the map to the given graphics context. */
    Graphics2D drawMap(Graphics2D graphics) throws DsolGisException, RemoteException;

    Bounds2d getExtent() throws RemoteException;

    MapImageInterface getImage() throws RemoteException;

    ImmutableMap<String, LayerInterface> getLayerMap() throws RemoteException;

    ImmutableList<LayerInterface> getAllLayers() throws RemoteException;

    ImmutableList<LayerInterface> getVisibleLayers() throws RemoteException;

    boolean isSame() throws RemoteException;

    String getName() throws RemoteException;

    double getScale() throws RemoteException;

    double getUnitImageRatio() throws RemoteException;

    MapUnits getUnits() throws RemoteException;

    void setExtent(Bounds2d extent) throws RemoteException;

    void setImage(MapImageInterface image) throws RemoteException;

    void setLayers(List<LayerInterface> layers) throws RemoteException;

    void setLayer(int index, LayerInterface layer) throws RemoteException;

    void addLayer(LayerInterface layer) throws RemoteException;

    void hideLayer(LayerInterface layer) throws RemoteException;

    void showLayer(LayerInterface layer) throws RemoteException;

    void hideLayer(String layerName) throws RemoteException;

    void showLayer(String layerName) throws RemoteException;

    void setName(String name) throws RemoteException;

    void setUnits(MapUnits units) throws RemoteException;

    void zoom(double factor) throws RemoteException;

    void zoomPoint(java.awt.geom.Point2D point, double factor) throws RemoteException;

    void zoomRectangle(SerializableRectangle2d rectangle) throws RemoteException;

    boolean isDrawBackground();

    void setDrawBackground(boolean drawBackground);
}
