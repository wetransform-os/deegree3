package org.deegree.gml.schema;

import javax.xml.namespace.QName;

import org.deegree.feature.types.property.ValueRepresentation;

class TargetElement {
    
    private final QName valueElName;
    
    private final ValueRepresentation valueRepresentation;

    TargetElement( QName valueElName, ValueRepresentation valueRepresentation ) {
        this.valueElName = valueElName;
        this.valueRepresentation = valueRepresentation;
    }

    public QName getValueElement() {
        return valueElName;
    }

    public ValueRepresentation getValueRepresentation() {
        return valueRepresentation;
    }
    
}
