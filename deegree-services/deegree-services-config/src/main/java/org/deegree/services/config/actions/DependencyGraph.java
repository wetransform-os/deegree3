package org.deegree.services.config.actions;

import static java.util.Collections.sort;
import static org.deegree.services.config.actions.Utils.getWorkspaceAndPath;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import javax.servlet.http.HttpServletResponse;

import org.deegree.commons.config.DeegreeWorkspace;
import org.deegree.commons.utils.Pair;
import org.deegree.workspace.Resource;
import org.deegree.workspace.ResourceIdentifier;
import org.deegree.workspace.ResourceMetadata;
import org.deegree.workspace.ResourceStates.ResourceState;
import org.deegree.workspace.Workspace;
import org.deegree.workspace.graph.ResourceNode;

public class DependencyGraph {

    public static void dependencyGraph( String path, HttpServletResponse resp )
                            throws IOException {
        Pair<DeegreeWorkspace, String> p = getWorkspaceAndPath( path );

        resp.setContentType( "text/plain" );
        Workspace ws = p.first.getNewWorkspace();

        // collect root nodes
        List<ResourceIdentifier<?>> roots = new ArrayList<>();
        for ( ResourceMetadata<? extends Resource> resourceMetadata : ws.getDependencyGraph().toSortedList() ) {
            if ( resourceMetadata.getDependencies().isEmpty() && resourceMetadata.getSoftDependencies().isEmpty() ) {
                roots.add( (ResourceIdentifier<?>) resourceMetadata.getIdentifier() );
            }
        }

        PrintWriter writer = resp.getWriter();
        sort( roots, new Comparator<ResourceIdentifier<?>>() {
            @Override
            public int compare( ResourceIdentifier<?> arg0, ResourceIdentifier<?> arg1 ) {
                return arg0.toString().compareTo( arg1.toString() );
            }
        } );
        for ( ResourceIdentifier<?> root : roots ) {
            print( writer, root, "", ws );
            writer.println();
        }
    }

    private static void print( PrintWriter writer, ResourceIdentifier<?> id, String indent, Workspace ws ) {
        writer.println( indent + toString( id, ws ) );
        ResourceNode<?> node = ws.getDependencyGraph().getNode( id );
        List<ResourceNode<? extends Resource>> dependents = node.getDependents();
        List<ResourceIdentifier<?>> dependentIds = dependents.stream().map( d -> d.getMetadata().getIdentifier() ).collect( Collectors.toList() );
        sort( dependentIds, new Comparator<ResourceIdentifier<?>>() {
            @Override
            public int compare( ResourceIdentifier<?> arg0, ResourceIdentifier<?> arg1 ) {
                return arg0.toString().compareTo( arg1.toString() );
            }
        } );
        dependentIds.forEach( x -> {
            print( writer, x, indent + "  ", ws );
        } );
    }

    private static String toString( ResourceIdentifier<?> node, Workspace ws ) {
        ResourceState state = ws.getStates().getState( node );
        return node + " [" + state + "]";
    }

}
