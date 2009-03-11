//$HeadURL$
/*----------------    FILE HEADER  ------------------------------------------
 This file is part of deegree.
 Copyright (C) 2001-2009 by:
 Department of Geography, University of Bonn
 http://www.giub.uni-bonn.de/deegree/
 lat/lon GmbH
 http://www.lat-lon.de

 This library is free software; you can redistribute it and/or
 modify it under the terms of the GNU Lesser General Public
 License as published by the Free Software Foundation; either
 version 2.1 of the License, or (at your option) any later version.
 This library is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 Lesser General Public License for more details.
 You should have received a copy of the GNU Lesser General Public
 License along with this library; if not, write to the Free Software
 Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA
 Contact:

 Andreas Poth
 lat/lon GmbH
 Aennchenstr. 19
 53177 Bonn
 Germany
 E-Mail: poth@lat-lon.de

 Prof. Dr. Klaus Greve
 Department of Geography
 University of Bonn
 Meckenheimer Allee 166
 53115 Bonn
 Germany
 E-Mail: greve@giub.uni-bonn.de
 ---------------------------------------------------------------------------*/

package org.deegree.rendering.r3d.opengl.rendering.managers;

import java.util.Comparator;
import java.util.List;

import org.deegree.commons.utils.math.Vectors3f;
import org.deegree.model.geometry.Envelope;
import org.deegree.rendering.r3d.opengl.rendering.WorldRenderableObject;

/**
 * The <code>TreeManager</code> will hold the bill board references.
 * 
 * @author <a href="mailto:bezema@lat-lon.de">Rutger Bezema</a>
 * @author last edited by: $Author$
 * @version $Revision$, $Date$
 * 
 */
public class BuildingManager extends RenderableManager<WorldRenderableObject> {

    /**
     * @param validDomain
     * @param numberOfObjectsInLeaf
     */
    public BuildingManager( Envelope validDomain, int numberOfObjectsInLeaf ) {
        super( validDomain, numberOfObjectsInLeaf );
    }

    /**
     * 
     * @param eye
     * @return an ordered List of trees.
     */
    public List<WorldRenderableObject> getBuildingsForEyePosition( float[] eye ) {
        BuildingComparator comp = new BuildingComparator( eye );
        return getObjects( comp );
    }

    private class BuildingComparator implements Comparator<WorldRenderableObject> {
        private float[] eye;

        /**
         * @param eye
         *            to compare this billboard to.
         * 
         */
        public BuildingComparator( float[] eye ) {
            this.eye = eye;
        }

        @Override
        public int compare( WorldRenderableObject o1, WorldRenderableObject o2 ) {
            float distA = Vectors3f.distance( eye, o1.getPosition() );
            float distB = Vectors3f.distance( eye, o2.getPosition() );
            return Float.compare( distA, distB );
        }

    }
}
