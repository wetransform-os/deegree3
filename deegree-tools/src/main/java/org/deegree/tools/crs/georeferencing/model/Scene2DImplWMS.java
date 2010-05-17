//$HeadURL$
/*----------------------------------------------------------------------------
 This file is part of deegree, http://deegree.org/
 Copyright (C) 2001-2009 by:
 - Department of Geography, University of Bonn -
 and
 - lat/lon GmbH -

 This library is free software; you can redistribute it and/or modify it under
 the terms of the GNU Lesser General Public License as published by the Free
 Software Foundation; either version 2.1 of the License, or (at your option)
 any later version.
 This library is distributed in the hope that it will be useful, but WITHOUT
 ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 details.
 You should have received a copy of the GNU Lesser General Public License
 along with this library; if not, write to the Free Software Foundation, Inc.,
 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA

 Contact information:

 lat/lon GmbH
 Aennchenstr. 19, 53177 Bonn
 Germany
 http://lat-lon.de/

 Department of Geography, University of Bonn
 Prof. Dr. Klaus Greve
 Postfach 1147, 53001 Bonn
 Germany
 http://www.geographie.uni-bonn.de/deegree/

 e-mail: info@deegree.org
 ----------------------------------------------------------------------------*/
package org.deegree.tools.crs.georeferencing.model;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URL;
import java.util.Collections;
import java.util.List;

import javax.vecmath.Point2d;

import org.deegree.cs.CRS;
import org.deegree.geometry.Envelope;
import org.deegree.geometry.GeometryFactory;
import org.deegree.protocol.wms.client.WMSClient111;

/**
 * 
 * Generates a 2D BufferedImage from a WMS request.
 * 
 * @author <a href="mailto:thomas@lat-lon.de">Steffen Thomas</a>
 * @author last edited by: $Author$
 * 
 * @version $Revision$, $Date$
 */
public class Scene2DImplWMS implements Scene2D {

    private WMSClient111 wmsClient;

    private CRS srs;

    private List<String> lays;

    private List<String> formatList;

    private Envelope imageBoundingbox;

    private Envelope predictionBoundingbox;

    private Envelope holeRequestBoundingbox;

    private Point2d onePixel;

    private GeometryFactory geometryFactory;

    private BufferedImage requestedImage;

    private double minX, minY, maxX, maxY;

    // private double predictionMinX, predictionMinY, predictionMaxX, predictionMaxY;

    /**
     * The GetMap()-request to a WMSClient.
     * 
     * @param panelWidth
     * @param panelHeight
     * @param minX
     * @param maxX
     * @param minY
     * @param maxY
     * @return
     */
    private BufferedImage generateMap( double panelWidth, double panelHeight, Envelope imageBoundingbox ) {
        BufferedImage image = null;

        try {
            image = wmsClient.getMap( lays, (int) panelWidth, (int) panelHeight, imageBoundingbox, srs,
                                      formatList.get( 0 ), true, false, 20, false, null ).first;
        } catch ( IOException e ) {
            e.printStackTrace();
        }

        return image;
    }

    /**
     * Determines the ratio the boundingbox has to orient on. If there is a mismatch between the width and height this
     * should influence the display of the image returned by the WMS to prevent any deformation. <li>pos - orientation
     * on width because width is larger</li> <li>neg - orientation on hight because hight is larger</li> <li>other -
     * orientation on width/hight because they are even</li>
     * 
     * @param panelBounds
     *            the rectangle bounds, not <Code>null</Code>
     * @return an positive, negative or even integer
     */
    private int determineProportion( Rectangle panelBounds ) {
        double w = panelBounds.getWidth();
        double h = panelBounds.getHeight();

        double ratio = w / h;

        if ( ratio < 1 ) {
            // if < 1 then do orientation on h
            return -1;
        } else if ( ratio > 1 ) {
            // if > 1 then do orientation on w
            return 1;
        }
        // if w = h then return 0
        return 0;
    }

    @Override
    public BufferedImage generateImage( Rectangle sceneBounds ) {

        double panelWidth = sceneBounds.getWidth();
        double panelHeight = sceneBounds.getHeight();

        geometryFactory = new GeometryFactory();
        if ( imageBoundingbox == null ) {
            requestedImage = generateMap( panelWidth, panelHeight, generateImageBoundingbox( sceneBounds ) );
        } else {
            requestedImage = generateMap( panelWidth, panelHeight, imageBoundingbox );
        }
        return requestedImage;

    }

    @Override
    public Envelope determineRequestBoundingbox( URL imageRequestUrl ) {

        wmsClient = new WMSClient111( imageRequestUrl );
        lays = Collections.singletonList( "dem" );
        srs = new CRS( "EPSG:32618" );
        formatList = Collections.singletonList( "image/jpeg" );
        // lays = Collections.singletonList( "root" );
        // srs = new CRS( "EPSG:4326" );

        if ( wmsClient.hasLayer( lays.get( 0 ) ) ) {
            return holeRequestBoundingbox = wmsClient.getBoundingBox( srs.getName(), lays );
        } else {
            return null;
        }

    }

    /**
     * 
     * 
     * @param sceneBounds
     * @return the boundingbox of the image that should be displayed
     */
    private Envelope generateImageBoundingbox( Rectangle sceneBounds ) {
        int proportion = determineProportion( sceneBounds );

        double panelWidth = sceneBounds.getWidth();
        double panelHeight = sceneBounds.getHeight();
        if ( minX != maxX && minY != maxY ) {

            if ( proportion == 0 ) {
                // do nothing

            } else if ( proportion < 0 ) {

                double newWidth = ( panelWidth / panelHeight ) * ( maxY - minY );

                imageBoundingbox = geometryFactory.createEnvelope( minX, minY, ( minX + newWidth ), maxY, srs );
                // predictionBoundingbox = geometryFactory.createEnvelope( predictionMinX, predictionMinY,
                // ( predictionMinX + newWidth ), predictionMaxY,
                // srs );
            } else {
                double newHeight = ( panelHeight / panelWidth ) * ( maxX - minX );

                imageBoundingbox = geometryFactory.createEnvelope( minX, minY, maxX, ( minY + newHeight ), srs );
                // predictionBoundingbox = geometryFactory.createEnvelope( predictionMinX, predictionMinY,
                // predictionMaxX,
                // ( predictionMinY + newHeight ), srs );
            }
            onePixel = normalizeImageBoundingbox( sceneBounds, imageBoundingbox );

        }
        return imageBoundingbox;

    }

    /**
     * Based on bounds from an upper component this method normalizes the bounds of the upper component regarding to an
     * envelope to one pixel.
     * <p>
     * Sets the relation between panelBounds as the rectangle of the panel and bbox as the envelope of the requested
     * image.
     * 
     * @param panelBounds
     *            the rectangle bounds, not <Code>null</Code>
     * @param bbox
     *            the boundingbox, not <Code>null</Code>
     * @return a point, not <Code>null</Code>
     */
    private Point2d normalizeImageBoundingbox( Rectangle panelBounds, Envelope bbox ) {

        double w = panelBounds.getWidth();
        double h = panelBounds.getHeight();
        double oneX = bbox.getSpan0() / w;
        double oneY = bbox.getSpan1() / h;
        System.out.println( "one Pixel is: " + oneX + " - " + oneY );
        return new Point2d( oneX, oneY );
    }

    @Override
    public void changeImageBoundingbox( Point2d change ) {
        double envStartPosX = imageBoundingbox.getMin().get0() + change.getX() * onePixel.getX();
        double envStartPosY = imageBoundingbox.getMin().get1() - change.getY() * onePixel.getY();

        double envEndPosX = imageBoundingbox.getMax().get0() + change.getX() * onePixel.getX();
        double envEndPosY = imageBoundingbox.getMax().get1() - change.getY() * onePixel.getY();

        System.out.println( "OnePixel: " + onePixel + " -- Change: " + change );
        System.out.println( "  start: " + envStartPosX + ", " + envStartPosY + " end: " + envEndPosX + ", "
                            + envEndPosY );

        imageBoundingbox = geometryFactory.createEnvelope( envStartPosX, envStartPosY, envEndPosX, envEndPosY, srs );

    }

    // public void changePredictionBoundingbox( Point2d change ) {
    // double envStartPosX = predictionBoundingbox.getMin().get0() + change.getX() * onePixel.getX();
    // double envStartPosY = predictionBoundingbox.getMin().get1() - change.getY() * onePixel.getY();
    //
    // double envEndPosX = predictionBoundingbox.getMax().get0() + change.getX() * onePixel.getX();
    // double envEndPosY = predictionBoundingbox.getMax().get1() - change.getY() * onePixel.getY();
    //
    // System.out.println( "OnePixel: " + onePixel + " -- Change: " + change );
    // System.out.println( "  start: " + envStartPosX + ", " + envStartPosY + " end: " + envEndPosX + ", "
    // + envEndPosY );
    //
    // predictionBoundingbox = geometryFactory.createEnvelope( envStartPosX, envStartPosY, envEndPosX, envEndPosY, srs
    // );
    //
    // }

    @Override
    public Envelope getImageBoundingbox() {
        return imageBoundingbox;
    }

    @Override
    public void reset() {
        imageBoundingbox = null;
        predictionBoundingbox = null;
        minX = 0.0;
        minY = 0.0;
        maxX = 0.0;
        maxY = 0.0;
        // predictionMinX = 0.0;
        // predictionMinY = 0.0;
        // predictionMaxX = 0.0;
        // predictionMaxY = 0.0;
        onePixel = null;
        requestedImage = null;
    }

    @Override
    public void setSightWindowMinX( double minX ) {
        this.minX = minX;
        // this.predictionMinX = predictionMinX;
    }

    @Override
    public void setSightWindowMinY( double minY ) {
        this.minY = minY;
        // this.predictionMinY = predictionMinY;
    }

    @Override
    public void setSightWindowMaxX( double maxX ) {
        this.maxX = maxX;
        // this.predictionMaxX = predictionMaxX;
    }

    @Override
    public void setSightWindowMaxY( double maxY ) {
        this.maxY = maxY;
        // this.predictionMaxY = predictionMaxY;
    }

    @Override
    public Envelope getRequestBoundingbox() {

        return holeRequestBoundingbox;
    }

}
