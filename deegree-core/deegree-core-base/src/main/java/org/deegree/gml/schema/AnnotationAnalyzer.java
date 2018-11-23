package org.deegree.gml.schema;

import static org.deegree.commons.xml.CommonNamespaces.GML3_2_NS;
import static org.deegree.commons.xml.CommonNamespaces.XSNS;
import static org.deegree.commons.xml.stax.XMLInputFactoryUtils.newSafeInstance;
import static org.deegree.commons.xml.stax.XMLStreamUtils.closeQuietly;
import static org.deegree.commons.xml.stax.XMLStreamUtils.getElementTextAsQName;
import static org.deegree.feature.types.property.ValueRepresentation.BOTH;
import static org.deegree.feature.types.property.ValueRepresentation.REMOTE;

import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import org.apache.xerces.xs.XSAnnotation;
import org.apache.xerces.xs.XSElementDeclaration;
import org.apache.xerces.xs.XSObjectList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Analyzes the annotations of {@link XSElementDeclaration} of GML properties and extracts the contained information on
 * target elements.
 */
class AnnotationAnalyzer {

    private static final Logger LOG = LoggerFactory.getLogger( AnnotationAnalyzer.class );

    private static final QName XS_APP_INFO = new QName( XSNS, "appinfo" );

    private static final QName GML_TARGET_ELEMENT = new QName( GML3_2_NS, "targetElement" );

    private static final QName ADV_REFERENZIERTES_ELEMENT = new QName( "http://www.adv-online.de/nas",
                                                                       "referenziertesElement" );

    private final Map<XSElementDeclaration, TargetElement> elDeclToTargetEl = new HashMap<>();

    TargetElement determineTargetElement( XSElementDeclaration elDecl ) {
        if ( elDeclToTargetEl.containsKey( elDecl ) ) {
            LOG.debug( "Cache hit for: {" + elDecl.getNamespace() + "}" + elDecl.getName() + ", scope: "
                       + elDecl.getScope() );
            return elDeclToTargetEl.get( elDecl );
        }
        XSObjectList annotations = elDecl.getAnnotations();
        TargetElement targetElement = null;
        if ( annotations.getLength() > 0 ) {
            XSAnnotation annotation = (XSAnnotation) annotations.item( 0 );
            targetElement = determineTargetElement( annotation.getAnnotationString() );
            LOG.debug( "Adding to cache: {" + elDecl.getNamespace() + "}" + elDecl.getName() + ", scope: "
                       + elDecl.getScope() );
            elDeclToTargetEl.put( elDecl, targetElement );
        }
        return targetElement;
    }

    private static TargetElement determineTargetElement( String annotation ) {
        XMLStreamReader xmlReader = null;
        try {
            xmlReader = newSafeInstance().createXMLStreamReader( new StringReader( annotation ) );
            while ( xmlReader.hasNext() ) {
                if ( xmlReader.isStartElement() && XS_APP_INFO.equals( xmlReader.getName() ) ) {
                    TargetElement targetEl = evalAppInfo( xmlReader );
                    if ( targetEl != null ) {
                        return null;
                    }
                }
                xmlReader.next();
            }
        } catch ( XMLStreamException e ) {
            e.printStackTrace();
            LOG.debug( "Error in element annotation: " + e.getMessage() );
        } finally {
            closeQuietly( xmlReader );
        }
        return null;
    }

    private static TargetElement evalAppInfo( XMLStreamReader xmlReader )
                            throws XMLStreamException {
        String sourceAttrValue = xmlReader.getAttributeValue( null, "source" );
        if ( "urn:x-gml:targetElement".equals( sourceAttrValue ) ) {
            LOG.trace( "Identified a target element annotation (urn:x-gml style)." );
            QName valueElName = getElementTextAsQNameSafe( xmlReader );
            if ( valueElName == null ) {
                return null;
            }
            return new TargetElement( valueElName, BOTH );
        }
        while ( !( xmlReader.isEndElement() && XS_APP_INFO.equals( xmlReader.getName() ) ) ) {
            if ( xmlReader.isStartElement() ) {
                if ( GML_TARGET_ELEMENT.equals( xmlReader.getName() ) ) {
                    LOG.trace( "Identified a target element annotation (GML 3.2 style)." );
                    QName valueElName = getElementTextAsQNameSafe( xmlReader );
                    if ( valueElName == null ) {
                        return null;
                    }
                    return new TargetElement( valueElName, REMOTE );
                } else if ( ADV_REFERENZIERTES_ELEMENT.equals( xmlReader.getName() ) ) {
                    LOG.trace( "Identified a target element annotation (adv style)." );
                    QName valueElName = getElementTextAsQNameSafe( xmlReader );
                    if ( valueElName == null ) {
                        return null;
                    }
                    return new TargetElement( valueElName, BOTH );
                }
            }
            xmlReader.next();
        }
        return null;
    }

    private static QName getElementTextAsQNameSafe( XMLStreamReader xmlReader ) {
        try {
            return getElementTextAsQName( xmlReader );
        } catch ( Exception e ) {
            // happens because of broken namespace bindings
        }
        return null;
    }
}
