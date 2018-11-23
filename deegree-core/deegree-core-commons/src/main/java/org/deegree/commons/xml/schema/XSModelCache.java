package org.deegree.commons.xml.schema;

import static java.util.Arrays.copyOf;
import static org.slf4j.LoggerFactory.getLogger;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.xerces.xs.XSModel;
import org.slf4j.Logger;

/**
 * Minimalistic approach for caching XSModels created from schema URLs.
 */
public class XSModelCache {

    private static final int MAX_ENTRIES = 100;

    private static final Logger LOG = getLogger( XSModelCache.class );

    private static XSModelCache INSTANCE;

    private Map<String, XSModel> idsToSchemas = new LinkedHashMap<String, XSModel>() {

        private static final long serialVersionUID = -2954560460595272023L;

        @Override
        protected boolean removeEldestEntry( Map.Entry<String, XSModel> eldest ) {
            return size() > MAX_ENTRIES;
        }
    };

    public static synchronized XSModelCache getInstance() {
        if ( INSTANCE == null ) {
            INSTANCE = new XSModelCache();
        }
        return INSTANCE;
    }

    public void clear() {
        synchronized ( this ) {
            LOG.info( "Clearing cache." );
            idsToSchemas.clear();
        }
    }

    public XSModel get( String... schemaUrls )
                            throws ClassCastException,
                            ClassNotFoundException,
                            InstantiationException,
                            IllegalAccessException {
        synchronized ( this ) {
            String id = getId( copyOf( schemaUrls, schemaUrls.length ) );
            LOG.info( "Lookup: " + id );
            XSModel schema = idsToSchemas.get( id );
            if ( schema == null ) {
                LOG.info( "Creating." );
                schema = XMLSchemaInfoSet.loadModel( schemaUrls );
                idsToSchemas.put( id, schema );
                LOG.info( "Added to cache. Entries: " + idsToSchemas.size() );
            } else {
                LOG.info( "From cache." );
            }
            return schema;
        }
    }

    private String getId( String... schemaUrls ) {
        Arrays.sort( schemaUrls );
        StringBuilder sb = new StringBuilder();
        for ( String schemaUrl : schemaUrls ) {
            sb.append( schemaUrl );
            sb.append( ';' );
        }
        return sb.toString();
    }

}
