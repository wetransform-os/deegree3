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
package org.deegree.feature.persistence.postgis;

import java.io.File;
import java.net.URL;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;

import org.deegree.commons.xml.XMLAdapter;
import org.deegree.cs.CRS;
import org.deegree.feature.i18n.Messages;
import org.deegree.feature.persistence.FeatureStore;
import org.deegree.feature.persistence.FeatureStoreException;
import org.deegree.feature.persistence.FeatureStoreProvider;
import org.deegree.feature.persistence.mapping.MappedApplicationSchema;
import org.deegree.feature.persistence.postgis.jaxb.GMLVersionType;
import org.deegree.feature.persistence.postgis.jaxb.PostGISFeatureStoreConfig;
import org.deegree.feature.persistence.postgis.jaxb.PostGISFeatureStoreConfig.GMLSchema;
import org.deegree.feature.persistence.postgis.jaxb.PostGISFeatureStoreConfig.NamespaceHint;
import org.deegree.feature.types.ApplicationSchema;
import org.deegree.gml.GMLVersion;
import org.deegree.gml.feature.schema.ApplicationSchemaXSDDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link FeatureStoreProvider} for the {@link PostGISFeatureStore}.
 * 
 * @author <a href="mailto:schneider@lat-lon.de">Markus Schneider</a>
 * @author last edited by: $Author$
 * 
 * @version $Revision$, $Date$
 */
public class PostGISFeatureStoreProvider implements FeatureStoreProvider {

    private static final Logger LOG = LoggerFactory.getLogger( PostGISFeatureStoreProvider.class );

    @Override
    public String getConfigNamespace() {
        return "http://www.deegree.org/datasource/feature/postgis";
    }

    @Override
    public URL getConfigSchema() {
        return PostGISFeatureStoreProvider.class.getResource( "/META-INF/schemas/datasource/feature/postgis/0.6.0/postgis.xsd" );
    }

    @Override
    public URL getConfigTemplate() {
        return PostGISFeatureStoreProvider.class.getResource( "/META-INF/schemas/datasource/feature/postgis/0.6.0/example.xml" );
    }

    @Override
    public FeatureStore getFeatureStore( URL configURL )
                            throws FeatureStoreException {
        PostGISFeatureStoreConfig config = parseConfig( configURL );
        MappedApplicationSchema schema = getSchema( configURL.toString(), config );
        return new PostGISFeatureStore( schema, config.getJDBCConnId() );
    }

    public String[] getDDL( URL configURL )
                            throws FeatureStoreException {
        PostGISFeatureStoreConfig config = parseConfig( configURL );
        MappedApplicationSchema schema = getSchema( configURL.toString(), config );
        return new PostGISDDLCreator( schema ).getDDL();
    }

    private MappedApplicationSchema getSchema( String configURL, PostGISFeatureStoreConfig config )
                            throws FeatureStoreException {

        ApplicationSchema appSchema = null;
        CRS storageSRS = new CRS( config.getStorageCRS() );
        if ( !config.getGMLSchema().isEmpty() ) {
            XMLAdapter resolver = new XMLAdapter();
            resolver.setSystemId( configURL );
            try {
                String[] schemaURLs = new String[config.getGMLSchema().size()];
                int i = 0;
                GMLVersionType gmlVersionType = null;
                for ( GMLSchema jaxbSchemaURL : config.getGMLSchema() ) {
                    schemaURLs[i++] = resolver.resolve( jaxbSchemaURL.getValue().trim() ).toString();
                    // TODO what about different versions at the same time?
                    gmlVersionType = jaxbSchemaURL.getVersion();
                }

                ApplicationSchemaXSDDecoder decoder = null;
                if ( schemaURLs.length == 1 && schemaURLs[0].startsWith( "file:" ) ) {
                    File file = new File( new URL( schemaURLs[0] ).toURI() );
                    decoder = new ApplicationSchemaXSDDecoder( GMLVersion.valueOf( gmlVersionType.name() ),
                                                               getHintMap( config.getNamespaceHint() ), file );
                } else {
                    decoder = new ApplicationSchemaXSDDecoder( GMLVersion.valueOf( gmlVersionType.name() ),
                                                               getHintMap( config.getNamespaceHint() ), schemaURLs );
                }
                appSchema = decoder.extractFeatureTypeSchema();
            } catch ( Exception e ) {
                String msg = Messages.getMessage( "STORE_MANAGER_STORE_SETUP_ERROR", e.getMessage() );
                LOG.error( msg, e );
                throw new FeatureStoreException( msg, e );
            }
        }

        MappedApplicationSchema schema = null;
        try {
            schema = PostGISApplicationSchemaBuilder.build( appSchema, config.getFeatureType(), config.getJDBCConnId(),
                                                            config.getDBSchemaQualifier(), storageSRS );
        } catch ( SQLException e ) {
            String msg = Messages.getMessage( "STORE_MANAGER_STORE_SETUP_ERROR", e.getMessage() );
            LOG.error( msg, e );
            throw new FeatureStoreException( msg, e );
        }
        return schema;
    }

    private PostGISFeatureStoreConfig parseConfig( URL configURL )
                            throws FeatureStoreException {

        PostGISFeatureStoreConfig config = null;
        try {
            JAXBContext jc = JAXBContext.newInstance( "org.deegree.feature.persistence.postgis.jaxb" );
            Unmarshaller u = jc.createUnmarshaller();
            config = (PostGISFeatureStoreConfig) u.unmarshal( configURL );
        } catch ( JAXBException e ) {
            String msg = Messages.getMessage( "STORE_MANAGER_STORE_SETUP_ERROR", e.getMessage() );
            LOG.error( msg, e );
            throw new FeatureStoreException( msg, e );
        }
        return config;
    }

    private static Map<String, String> getHintMap( List<NamespaceHint> hints ) {
        Map<String, String> prefixToNs = new HashMap<String, String>();
        for ( NamespaceHint namespaceHint : hints ) {
            prefixToNs.put( namespaceHint.getPrefix(), namespaceHint.getNamespaceURI() );
        }
        return prefixToNs;
    }
}