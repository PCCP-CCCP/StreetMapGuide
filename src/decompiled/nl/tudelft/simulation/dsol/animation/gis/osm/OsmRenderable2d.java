package nl.tudelft.simulation.dsol.animation.gis.osm;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.ImageObserver;
import java.rmi.RemoteException;

import javax.naming.NamingException;

import org.djutils.draw.bounds.Bounds2d;
import org.djutils.draw.bounds.Bounds3d;
import org.djutils.draw.point.OrientedPoint3d;
import org.djutils.draw.point.Point2d;
import org.djutils.logger.CategoryLogger;

import nl.tudelft.simulation.dsol.animation.d2.RenderableScale;
import nl.tudelft.simulation.dsol.animation.gis.GisMapInterface;
import nl.tudelft.simulation.dsol.animation.gis.GisRenderable2d;
import nl.tudelft.simulation.dsol.animation.gis.transform.CoordinateTransform;
import nl.tudelft.simulation.naming.context.ContextInterface;
import nl.tudelft.simulation.naming.context.Contextualized;
import nl.tudelft.simulation.naming.context.util.ContextUtil;

/**
 * OsmRenderable2d is a renderable wrapper around a GisMapInterface that caches the rendered map
 * as a BufferedImage for efficient repainting. It binds itself to the naming context under "animation/2D".
 */
public class OsmRenderable2d implements GisRenderable2d {

    private static final long serialVersionUID = 1L;

    /** The underlying GIS map. */
    protected GisMapInterface map;

    /** Cached rendered image of the map. */
    protected BufferedImage cachedImage;

    /** The extent for which the cached image was rendered. */
    protected Bounds2d cachedExtent = new Bounds2d(0, 0, 0, 0);

    /** The screen size for which the cached image was rendered. */
    protected Dimension cachedScreenSize = new Dimension();

    /** Current location (midpoint of extent). */
    protected OrientedPoint3d location;

    /** Current bounds. */
    protected Bounds3d bounds;

    /**
     * Construct with NoTransform.
     * 
     * @param contextualized the context
     * @param map the GIS map
     */
    public OsmRenderable2d(final Contextualized contextualized, final GisMapInterface map) {
        this(contextualized, map, new CoordinateTransform.NoTransform());
    }

    /**
     * Construct with a coordinate transform and default z=Double.MIN_VALUE.
     * 
     * @param contextualized the context
     * @param map the GIS map
     * @param coordinateTransform the coordinate transform
     */
    public OsmRenderable2d(final Contextualized contextualized, final GisMapInterface map,
            final CoordinateTransform coordinateTransform) {
        this(contextualized, map, coordinateTransform, Double.MIN_VALUE);
    }

    /**
     * Full constructor.
     * 
     * @param contextualized the context
     * @param map the GIS map
     * @param coordinateTransform the coordinate transform
     * @param z the z-coordinate
     */
    public OsmRenderable2d(final Contextualized contextualized, final GisMapInterface map,
            final CoordinateTransform coordinateTransform, final double z) {
        this.map = map;
        this.location = new OrientedPoint3d(
                this.cachedExtent.midPoint().getX(),
                this.cachedExtent.midPoint().getY(), z);
        this.bounds = new Bounds3d(
                this.cachedExtent.getDeltaX(),
                this.cachedExtent.getDeltaY(), 0.0);
        try {
            bind2Context(contextualized);
        } catch (Exception e) {
            CategoryLogger.always().warn(e, "<init>");
        }
    }

    /**
     * Bind this renderable to the naming context.
     * 
     * @param contextualized the context provider
     */
    protected void bind2Context(final Contextualized contextualized) {
        try {
            ContextInterface context = ContextUtil.lookupOrCreateSubContext(
                    contextualized.getContext(), "animation/2D");
            context.bindObject(Integer.toString(System.identityHashCode(this)), this);
        } catch (NamingException | RemoteException e) {
            CategoryLogger.always().warn(e, "<init>");
        }
    }

    @Override
    public void paintComponent(final Graphics2D graphics, final Bounds2d extent,
            final Dimension screenSize, final RenderableScale scale,
            final ImageObserver observer) {
        try {
            this.map.setDrawBackground(false);

            // If cache is still valid, just draw cached image
            if (extent.equals(this.cachedExtent)
                    && screenSize.equals(this.cachedScreenSize)
                    && this.map.isSame()) {
                graphics.drawImage(this.cachedImage, 0, 0, null);
                return;
            }

            // Re-render
            this.map.setExtent(extent);
            this.map.getImage().setSize(screenSize);
            cacheImage();
            paintComponent(graphics, extent, screenSize, scale, observer);
        } catch (Exception e) {
            CategoryLogger.always().warn(e, "paint");
        }
    }

    /**
     * Render the map to a cached BufferedImage.
     */
    private void cacheImage() throws Exception {
        this.cachedImage = new BufferedImage(
                (int) this.map.getImage().getSize().getWidth(),
                (int) this.map.getImage().getSize().getHeight(),
                BufferedImage.TYPE_INT_RGB);

        Graphics2D g2d = this.cachedImage.createGraphics();
        this.map.drawMap(g2d);
        g2d.dispose();

        this.cachedScreenSize = (Dimension) this.map.getImage().getSize().clone();
        this.cachedExtent = this.map.getExtent();

        this.location = new OrientedPoint3d(
                this.cachedExtent.midPoint().getX(),
                this.cachedExtent.midPoint().getY(), Double.MIN_VALUE);
        this.bounds = new Bounds3d(
                this.cachedExtent.getDeltaX(),
                this.cachedExtent.getDeltaY(), 0.0);
    }

    @Override
    public void destroy(final Contextualized contextualized) {
        try {
            ContextInterface context = ContextUtil.lookupOrCreateSubContext(
                    contextualized.getContext(), "animation/2D");
            context.unbindObject(Integer.toString(System.identityHashCode(this)));
        } catch (Throwable e) {
            CategoryLogger.always().warn(e, "finalize");
        }
    }

    @Override
    public boolean contains(final Point2d point, final Bounds2d extent) {
        return false;
    }

    @Override
    public long getId() {
        return -1L;
    }

    @Override
    public OsmRenderable2d getSource() {
        return this;
    }

    @Override
    public Bounds3d getBounds() {
        return this.bounds;
    }

    @Override
    public OrientedPoint3d getLocation() {
        return this.location;
    }

    public GisMapInterface getMap() {
        return this.map;
    }

    // --- Bridge methods ---

    @Override
    public GisRenderable2d getSource() {
        return getSource();
    }

    @Override
    public Locatable getSource() {
        return getSource();
    }

    @Override
    public org.djutils.draw.bounds.Bounds getBounds() throws RemoteException {
        return getBounds();
    }

    @Override
    public org.djutils.draw.point.Point getLocation() throws RemoteException {
        return getLocation();
    }
}
