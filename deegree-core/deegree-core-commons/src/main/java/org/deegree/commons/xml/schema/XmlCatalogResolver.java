package org.deegree.commons.xml.schema;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.apache.xerces.util.XMLCatalogResolver;
import org.apache.xerces.xni.XMLResourceIdentifier;
import org.apache.xerces.xni.XNIException;
import org.apache.xerces.xni.parser.XMLEntityResolver;
import org.apache.xerces.xni.parser.XMLInputSource;

/**
 * Wraps an {@link XMLCatalogResolver} to add support for the custom "ocgschemas:" protocol.
 * <p>
 * In order to supersede the functionality of the old <code>RedirectingEntityResolver</code>, this resolver adds another
 * layer of redirection. In addition to the resolving of the underlying catalog resolver, it replaces the String
 * "ogcschemas:" (in resolved URLs) with the URL of "/META-INF/SCHEMAS_OPENGIS_NET" on the classpath, enabling access to
 * the files in the ogcschemas JAR.
 * </p>
 */
public class XmlCatalogResolver implements XMLEntityResolver {

    private static final String OGCSCHEMAS_PROTOCOL = "ogcschemas:";

    private final String ogcSchemasBaseUrl;

    private final XMLCatalogResolver resolver;

    private Map<String,String> redirectedSystemIdToUnresolvedSystemId = new HashMap<>() ;

    XmlCatalogResolver( XMLCatalogResolver resolver ) {
        this.resolver = resolver;
        String ogcSchemasBaseUrl = XMLCatalogResolver.class.getResource( "/META-INF/SCHEMAS_OPENGIS_NET" ).toString();
        if ( ogcSchemasBaseUrl.endsWith( "/" ) ) {
            // depending on the classloader, a trailing "/" may be present or not
            ogcSchemasBaseUrl = ogcSchemasBaseUrl.substring( 0, ogcSchemasBaseUrl.length() - 1 );
        }
        this.ogcSchemasBaseUrl = ogcSchemasBaseUrl;
    }

    @Override
    public XMLInputSource resolveEntity( XMLResourceIdentifier resourceIdentifier )
                            throws XNIException,
                            IOException {
        String resolvedId = resolver.resolveIdentifier( resourceIdentifier );
        if ( resolvedId != null ) {
            resolvedId = resolvedId.replace( OGCSCHEMAS_PROTOCOL, ogcSchemasBaseUrl );
            redirectedSystemIdToUnresolvedSystemId.put( resolvedId, resourceIdentifier.getLiteralSystemId() );
            return new XMLInputSource( resourceIdentifier.getPublicId(), resolvedId,
                                       resourceIdentifier.getBaseSystemId() );
        }
        return null;
    }

    /**
     * @see XMLCatalogResolver#resolveSystem(String)
     * @param systemId
     * @return
     * @throws IOException
     */
    public String resolveSystem( String systemId )
                            throws IOException {
        String resolvedId = resolver.resolveSystem( systemId );
        if ( resolvedId != null ) {
            resolvedId = resolvedId.replace( OGCSCHEMAS_PROTOCOL, ogcSchemasBaseUrl );
            redirectedSystemIdToUnresolvedSystemId.put( resolvedId, systemId );
        }
        return resolvedId;
    }

    /**
     * Returns the original system id for the given resolved system id.
     * <p>
     * This only works for system ids that have been resolved by this instance, i.e. that have either been returned by
     * {@link #resolveEntity(XMLResourceIdentifier)} or by {@link #resolveSystem(String)}.
     * </p>
     *
     * @param resolvedSystemId
     *                             resolved system id
     * @return original (unresolved) system id
     */
    public String unresolveSystem( String resolvedSystemId ) {
        return redirectedSystemIdToUnresolvedSystemId.get(resolvedSystemId);
    }

}
