package org.deegree.commons.xml.schema;

import static org.apache.xerces.xni.grammars.XMLGrammarDescription.XML_SCHEMA;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.stream.XMLStreamException;

import org.apache.commons.io.IOUtils;
import org.apache.xerces.impl.Constants;
import org.apache.xerces.impl.xs.SchemaGrammar;
import org.apache.xerces.parsers.XMLGrammarPreparser;
import org.apache.xerces.util.SymbolTable;
import org.apache.xerces.util.XMLCatalogResolver;
import org.apache.xerces.util.XMLGrammarPoolImpl;
import org.apache.xerces.xni.XNIException;
import org.apache.xerces.xni.grammars.XMLGrammarPool;
import org.apache.xerces.xni.parser.XMLInputSource;
import org.apache.xerces.xs.StringList;
import org.apache.xerces.xs.XSModel;
import org.apache.xerces.xs.XSNamespaceItem;
import org.apache.xerces.xs.XSNamespaceItemList;
import org.deegree.commons.xml.stax.XMLStreamUtils;
import org.deegree.workspace.Initializable;
import org.deegree.workspace.Workspace;
import org.deegree.workspace.standard.DefaultWorkspace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link Initializable} that deals with the configuration of well-known XSDs.
 * <p>
 * Overall goal of this component is to reduce loading / processing time for XSDs:
 * <ul>
 * <li>Use a configurable XML catalog file ($WORKSPACE/appschemas/catalog.xml) for resolving/redirecting XSD locations</li>
 * <li>Configure pre-parsing of XSDs to a Xerces GrammarPool ($WORKSPACE/appschemas/preparsing.txt)</li>
 * <li>Pre-parsed XSDs are also analyzed for Namespace bindings to help the {@link XMLSchemaInfoSet} with this well</li>
 * </ul>
 * </p>
 */
public class WellKnownSchemaManager implements Initializable {

    private static final Logger LOG = LoggerFactory.getLogger( WellKnownSchemaManager.class );

    /** Property identifier: grammar pool. */
    private static final String GRAMMAR_POOL = Constants.XERCES_PROPERTY_PREFIX + Constants.XMLGRAMMAR_POOL_PROPERTY;

    /** Namespaces feature id (http://xml.org/sax/features/namespaces). */
    private static final String NAMESPACES_FEATURE_ID = "http://xml.org/sax/features/namespaces";

    /** Validation feature id (http://xml.org/sax/features/validation). */
    private static final String VALIDATION_FEATURE_ID = "http://xml.org/sax/features/validation";

    /** Schema validation feature id (http://apache.org/xml/features/validation/schema). */
    private static final String SCHEMA_VALIDATION_FEATURE_ID = "http://apache.org/xml/features/validation/schema";

    /** Schema full checking feature id (http://apache.org/xml/features/validation/schema-full-checking). */
    private static final String SCHEMA_FULL_CHECKING_FEATURE_ID = "http://apache.org/xml/features/validation/schema-full-checking";

    /** Honour all schema locations feature id (http://apache.org/xml/features/honour-all-schemaLocations). */
    private static final String HONOUR_ALL_SCHEMA_LOCATIONS_ID = "http://apache.org/xml/features/honour-all-schemaLocations";

    // a larg(ish) prime to use for a symbol table to be shared among potentially man parsers. Start one as close to 2K
    // (20 times larger than normal) and see what happens...
    private static final int BIG_PRIME = 2039;

    private static WellKnownSchemaManager INSTANCE;

    private XmlCatalogResolver resolver;

    private XMLGrammarPool grammarPool;

    private final Map<String, Map<String, String>> xsdUrlToNsToPrefix = new HashMap<>();

    /**
     * Creates a new instance.
     * <p>
     * Should only be invoked during workspace initialization, or via {@see #getInstance()}.
     * </p>
     */
    public WellKnownSchemaManager() {
        URL catalogUrl = XMLSchemaInfoSet.class.getResource( "/appschemas/catalog.xml" );
        resolver = new XmlCatalogResolver( new XMLCatalogResolver( new String[] { catalogUrl.toString() } ) );
    }

    /**
     * Retrieves the single instance of this.
     * <p>
     * If no workspace has been initialized, then a default catalog created from "/schemas/catalog.xml" will be
     * returned.
     * </p>
     *
     * @return single instance, never null
     */
    public static synchronized WellKnownSchemaManager getInstance() {
        if ( INSTANCE == null ) {
            INSTANCE = new WellKnownSchemaManager();
        }
        return INSTANCE;
    }

    /**
     * Redirects the given XSD URL, returning a local URL if available.
     * 
     * @param systemId
     *                     XSD URL, must not be <code>null</code>
     * @return redirected URL, identical to input if it cannot be redirected, never <code>null</code>
     */
    public static String redirect( String systemId ) {
        String resolved;
        try {
            resolved = getInstance().getCatalogResolver().resolveSystem( systemId );
        } catch ( IOException e ) {
            return systemId;
        }
        if ( resolved == null ) {
            return systemId;
        }
        return resolved;
    }

    @Override
    public void init( Workspace workspace ) {
        xsdUrlToNsToPrefix.clear();
        File schemasBaseDir = new File( ( (DefaultWorkspace) workspace ).getLocation(), "appschemas" );
        File catalogFile = new File( schemasBaseDir, "catalog.xml" );
        if ( catalogFile.exists() ) {
            LOG.info( "Loading XML catalog from " + catalogFile );
            try {
                resolver = new XmlCatalogResolver( new XMLCatalogResolver( new String[] { catalogFile.toURI().toURL().toString() } ) );
            } catch ( MalformedURLException e ) {
                LOG.error( e.getMessage(), e );
            }
        }
        File preparsingFile = new File( schemasBaseDir, "preparsing.txt" );
        if ( preparsingFile.exists() ) {
            LOG.info( "Loading XSDs for pre-parsing from " + preparsingFile );
            try {
                List<String> relativeXsdPaths = IOUtils.readLines( new FileInputStream( preparsingFile ) );
                grammarPool = createGrammarPool( schemasBaseDir.toURI().toURL(), relativeXsdPaths );
            } catch ( IOException e ) {
                LOG.error( e.getMessage(), e );
            }
        }
        INSTANCE = this;
    }

    /**
     * Returns a resolver that redirects references of well-known schemas to local copies.
     * 
     * @return resolver, never null
     */
    public XmlCatalogResolver getCatalogResolver() {
        return resolver;
    }

    /**
     * Returns a Xerces GrammarPool with preloaded XSDs.
     * 
     * @return grammar pool, never null
     */
    public XMLGrammarPool getPreloadedGrammarPool() {
        return grammarPool;
    }

    /**
     * Returns the prefix to namespace bindings for the given (pre-parsed) XSD.
     *
     * @param xsdUrl
     *                   URL of the XSD, must not be null
     * @return bindings, can be null (not a pre-parsed XSD)
     * @throws IOException
     * @throws XMLStreamException
     */
    public Map<String, String> getNamespacePrefixes( String xsdUrl )
                            throws IOException,
                            XMLStreamException {
        String resolvedUrl = resolver.resolveSystem( xsdUrl );
        if ( resolvedUrl != null ) {
            xsdUrl = resolvedUrl;
        }
        return xsdUrlToNsToPrefix.get( xsdUrl );
    }

    private XMLGrammarPool createGrammarPool( URL baseUrl, List<String> relativeXsdPaths )
                            throws XNIException,
                            IOException {

        XMLGrammarPreparser preparser = new XMLGrammarPreparser( new SymbolTable( BIG_PRIME ) );
        XMLGrammarPool grammarPool = new XMLGrammarPoolImpl();
        preparser.registerPreparser( XML_SCHEMA, null );
        preparser.setProperty( GRAMMAR_POOL, grammarPool );
        preparser.setEntityResolver( resolver );
        preparser.setFeature( NAMESPACES_FEATURE_ID, true );
        preparser.setFeature( VALIDATION_FEATURE_ID, true );
        preparser.setFeature( SCHEMA_VALIDATION_FEATURE_ID, true );
        preparser.setFeature( SCHEMA_FULL_CHECKING_FEATURE_ID, true );
        // NOTE: don't set to true, or validation of WFS GetFeature responses will fail (Xerces error?)!
        preparser.setFeature( HONOUR_ALL_SCHEMA_LOCATIONS_ID, false );

        // populate the pool with all schemaUris
        for ( String relativeXsdPath : relativeXsdPaths ) {
            URL schemaUrl = new URL( baseUrl, relativeXsdPath );
            String resolvedUrl = resolver.resolveSystem( schemaUrl.toString() );
            SchemaGrammar grammar = null;
            if ( resolvedUrl != null ) {
                LOG.info( "Preparsing: " + resolvedUrl + " " );
                grammar = (SchemaGrammar) preparser.preparseGrammar( XML_SCHEMA,
                                                                     new XMLInputSource( null, resolvedUrl, null ) );
            } else {
                LOG.info( "Preparsing: " + schemaUrl + " " );
                grammar = (SchemaGrammar) preparser.preparseGrammar( XML_SCHEMA,
                                                                     new XMLInputSource( null, schemaUrl.toString(),
                                                                                         null ) );
            }
            addNsToPrefixMappings( grammar.toXSModel() );
        }

        // prevent any more adds to the pool
        grammarPool.lockPool();
        return grammarPool;
    }

    private void addNsToPrefixMappings( XSModel xsModel ) {
        for ( String xsdUrl : getComponentUrls( xsModel ) ) {
            Map<String, String> nsToPrefix = xsdUrlToNsToPrefix.get( xsdUrl );
            if ( nsToPrefix == null ) {
                try {
                    xsdUrlToNsToPrefix.put( xsdUrl, XMLStreamUtils.getNamespacePrefixes( xsdUrl ) );
                } catch ( Exception e ) {
                    LOG.error( e.getMessage(), e );
                }
            }
        }
    }

    private List<String> getComponentUrls( XSModel xsModel ) {
        List<String> documentLocations = new ArrayList<>();
        XSNamespaceItemList nsItems = xsModel.getNamespaceItems();
        for ( int i = 0; i < nsItems.getLength(); i++ ) {
            XSNamespaceItem nsItem = nsItems.item( i );
            StringList locations = nsItem.getDocumentLocations();
            for ( int j = 0; j < locations.getLength(); j++ ) {
                documentLocations.add( locations.item( j ) );
            }
        }
        return documentLocations;
    }

}
