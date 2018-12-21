package org.deegree.filter.function.other;

import static org.deegree.commons.tom.primitive.BaseType.STRING;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.deegree.commons.tom.TypedObjectNode;
import org.deegree.commons.tom.primitive.PrimitiveType;
import org.deegree.commons.tom.primitive.PrimitiveValue;
import org.deegree.filter.Expression;
import org.deegree.filter.FilterEvaluationException;
import org.deegree.filter.expression.Function;
import org.deegree.filter.expression.Literal;
import org.deegree.filter.function.FunctionProvider;
import org.deegree.filter.function.ParameterType;
import org.deegree.workspace.Workspace;

/**
 * Transforms a set of discrete values for an attribute into another set of values.
 * <p>
 * Same approach as used in GeoServer, see https://docs.geoserver.org/2.13.2/user/styling/css/examples/transformation.html#recode}.
 * </p>
 */
public class Recode implements FunctionProvider {

    private static final String NAME = "Recode";

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public List<ParameterType> getArgs() {
        List<ParameterType> args = new ArrayList<>();
        args.add( ParameterType.STRING );
        return args;
    }

    @Override
    public ParameterType getReturnType() {
        return ParameterType.STRING;
    }

    @Override
    public Function create( List<Expression> params ) {
        if ( params.size() % 2 == 0 ) {
            throw new IllegalArgumentException("Number of input parameters must be odd.");
        }
        final Map<String, String> inputToOutput = new HashMap<>();
        for ( int i = 1; i < params.size(); i += 2 ) {
            final Literal<?> mapInput = (Literal<?>) params.get( i );
            final Literal<?> mapOutput = (Literal<?>) params.get( i + 1 );
            inputToOutput.put( mapInput.getValue().toString(), mapOutput.getValue().toString() );
        }
        return new Function( NAME, params ) {
            @Override
            public TypedObjectNode[] evaluate( List<TypedObjectNode[]> args )
                                    throws FilterEvaluationException {
                TypedObjectNode[] inputs = args.get( 0 );
                List<TypedObjectNode> outputs = new ArrayList<TypedObjectNode>( inputs.length );
                for ( TypedObjectNode input : inputs ) {
                    String s = null;
                    if ( input instanceof PrimitiveValue ) {
                        s = ( (PrimitiveValue) input ).getAsText();
                    } else {
                        s = input.toString();
                    }
                    String mappedValue = inputToOutput.get( s );
                    if ( mappedValue != null ) {
                        outputs.add( new PrimitiveValue( mappedValue, new PrimitiveType( STRING ) ) );
                    }
                }
                return outputs.toArray( new TypedObjectNode[outputs.size()] );
            }
        };
    }

    @Override
    public void init( Workspace ws ) {
        // nothing to do
    }

    @Override
    public void destroy() {
        // nothing to do
    }

}
