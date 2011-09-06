//$HeadURL: svn+ssh://mschneider@svn.wald.intevation.org/deegree/base/trunk/src/org/deegree/ogcwebservices/wfs/operation/DescribeFeatureType.java $
/*----------------------------------------------------------------------------
 This file is part of deegree, http://deegree.org/
 Copyright (C) 2001-2009 by:
 Department of Geography, University of Bonn
 and
 lat/lon GmbH

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
package org.deegree.protocol.wfs.query;

import org.deegree.cs.coordinatesystems.ICRS;
import org.deegree.filter.sort.SortProperty;
import org.deegree.protocol.wfs.getfeature.TypeName;

/**
 * An {@link AdHocQuery} that selects a feature by id.
 * <p>
 * NOTE: Only KVP-based queries can be of this type. For XML-requests its only possible to use a filter constraint.
 * 
 * @author <a href="mailto:schneider@lat-lon.de">Markus Schneider</a>
 * @author last edited by: $Author: schneider $
 * 
 * @version $Revision: $, $Date: $
 */
public class FeatureIdQuery extends AdHocQuery {

    private final String featureId;

    /**
     * Creates a new {@link FeatureIdQuery} instance.
     * 
     * @param handle
     *            client-generated query identifier, may be <code>null</code>
     * @param typeNames
     *            requested feature types (with optional aliases), must not be <code>null</code>, but can be empty
     * @param featureVersion
     *            version of the feature instances to be retrieved, may be <code>null</code>
     * @param srsName
     *            WFS-supported SRS that should be used for returned feature geometries, may be <code>null</code>
     * @param projectionClauses
     *            limits the properties of the features that should be retrieved, may be <code>null</code>
     * @param sortBy
     *            properties whose values should be used to order the set of feature instances that satisfy the query,
     *            may be <code>null</code>
     * @param featureId
     *            requested feature id, must not be <code>null</code>
     */
    public FeatureIdQuery( String handle, TypeName[] typeNames, String featureVersion, ICRS srsName,
                           ProjectionClause[] projectionClauses, SortProperty[] sortBy, String featureId ) {
        super( handle, typeNames, featureVersion, srsName, projectionClauses, sortBy );
        if ( featureId == null ) {
            throw new IllegalArgumentException();
        }
        this.featureId = featureId;
    }

    /**
     * Returns the requested feature id.
     * 
     * @return the requested feature id, never <code>null</code>
     */
    public String getFeatureId() {
        return featureId;
    }
}
