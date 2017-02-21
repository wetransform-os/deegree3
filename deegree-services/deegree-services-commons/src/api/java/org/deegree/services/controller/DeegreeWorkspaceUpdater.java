package org.deegree.services.controller;

import static org.slf4j.LoggerFactory.getLogger;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.deegree.commons.config.AbstractBasicResourceManager;
import org.deegree.commons.config.AbstractResourceManager;
import org.deegree.commons.config.DeegreeWorkspace;
import org.deegree.commons.config.ResourceManager;
import org.deegree.commons.config.ResourceState;
import org.deegree.commons.jdbc.ConnectionManager;
import org.deegree.commons.jdbc.param.JDBCParams;
import org.deegree.commons.jdbc.param.JDBCParamsManager;
import org.deegree.services.metadata.OWSMetadataProviderManager;
import org.slf4j.Logger;

public class DeegreeWorkspaceUpdater {

    private static final Logger LOG = getLogger( DeegreeWorkspaceUpdater.class );

    public final static DeegreeWorkspaceUpdater INSTANCE = new DeegreeWorkspaceUpdater();

    private List<File> filesRemoved = new ArrayList<File>();

    private List<File> filesModified = new ArrayList<File>();

    private List<File> filesUnmodified = new ArrayList<File>();

    private List<File> filesAdded = new ArrayList<File>();

    private Map<File, Long> fileStatusMap = new HashMap<File, Long>();

    private File lastWorkspaceLocation = null;

    private DeegreeWorkspaceUpdater() {
    }

    public void init( DeegreeWorkspace workspace ) {
        // remember workspace and file status
        updateFileStatusMap( workspace.getLocation() );
        lastWorkspaceLocation = workspace.getLocation();
    }

    public boolean isWorkspaceChange( DeegreeWorkspace newWorkspace ) {
        final File newLocation = newWorkspace.getLocation();
        if ( newLocation.equals( lastWorkspaceLocation ) )
            return false;
        lastWorkspaceLocation = newLocation;
        return true;
    }

    public void notifyWorkspaceChange( DeegreeWorkspace workspace ) {
        // remember file status
        updateFileStatusMap( workspace.getLocation() );
    }

    public void updateWorkspace( DeegreeWorkspace workspace ) {
        synchronized ( this ) {
            analyzeChanges( workspace.getLocation() );
            applyChanges( workspace );
            // remember new file status
            updateFileStatusMap( workspace.getLocation() );
        }
    }

    private void analyzeChanges( File wsDir ) {
        filesRemoved.clear();
        filesAdded.clear();
        filesModified.clear();

        final List<File> allFiles = collectFiles( wsDir, null );

        for ( File file : allFiles ) {
            if ( fileStatusMap.containsKey( file ) ) { // existing
                final long lastTimeStamp = fileStatusMap.get( file );
                final long lastModified = file.lastModified();
                if ( lastTimeStamp != lastModified )
                    filesModified.add( file );
                else
                    filesUnmodified.add( file );
            } else {
                filesAdded.add( file );
            }
        }
        // check removed
        for ( File file : fileStatusMap.keySet() ) {
            if ( !allFiles.contains( file ) ) {
                filesRemoved.add( file );
            }
        }

        logChange( "new", filesAdded, true );
        logChange( "removed", filesRemoved, true );
        logChange( "modified", filesModified, true );
        logChange( "unmodified", filesUnmodified, false );
    }

    private void updateFileStatusMap( File wsDir ) {
        fileStatusMap.clear();
        final List<File> allFiles = new ArrayList<File>();
        collectFiles( wsDir, allFiles );
        for ( File file : allFiles ) {
            if ( "50ec_data_feature.xml".equals( file.getName() ) )
                System.out.println( "check!" );
            fileStatusMap.put( file, file.lastModified() );
        }
    }

    private void logChange( String message, List<File> files, boolean verbose ) {
        LOG.debug( message + ": " + files.size() );
        if ( verbose ) {
            for ( File file : files ) {
                LOG.debug( message + ": " + file.getName() );
            }
        }
    }

    private List<File> collectFiles( File file, List<File> collector ) {
        if ( collector == null )
            collector = new ArrayList<File>();
        if ( file.isFile() ) {
            if ( !isFileToIgnore( file ) )
                collector.add( file );
            return collector;
        } else if ( file.isDirectory() ) {
            for ( File child : file.listFiles() ) {
                collectFiles( child, collector );
            }
        }
        return collector;
    }

    private boolean isFileToIgnore( File file ) {
        // these files should not be managed
        final String path = file.getAbsolutePath();
        if ( path.contains( "appschemas" ) )
            return true;
        if ( "bbox_cache.properties".equals( file.getName() ) )
            return true;
        if ( "main.xml".equals( file.getName() ) )
            return true;
        return false;
    }

    private void applyChanges( DeegreeWorkspace workspace ) {
        final HashSet<File> modified = new HashSet<File>( filesModified );
        final HashSet<File> removed = new HashSet<File>( filesRemoved );
        final HashSet<File> added = new HashSet<File>( filesAdded );

        final HashSet<ResourceState<JDBCParams>> poolsToAdd = new HashSet<ResourceState<JDBCParams>>();
        final HashSet<String> poolsToRemove = new HashSet<String>();

        final StringBuffer debugLog = new StringBuffer();

        try {
            for ( ResourceManager m : workspace.getResourceManagers() ) {
                if ( m instanceof AbstractResourceManager ) {
                    final AbstractResourceManager<?> manager = (AbstractResourceManager<?>) m;
                    debugLog.append( "Manager: " + manager.getClass() + "\n" );

                    // handle remove and modify
                    for ( ResourceState<?> state : manager.getStates() ) {
                        if ( state == null ) {
                            // ignore null-state
                        } else {
                            final String id = state.getId();
                            final File configLocation = state.getConfigLocation();
                            // handle remove
                            if ( filesRemoved.contains( configLocation ) ) {
                                debugLog.append( "  remove: " + state.getId() + "\n" );
                                manager.updateResourceConfig( id, configLocation );
                                if ( m instanceof JDBCParamsManager ) {
                                    poolsToRemove.add( id );
                                }
                                // mark as done
                                removed.remove( configLocation );
                            }
                            // handle modify
                            if ( filesModified.contains( configLocation ) ) {
                                debugLog.append( "  update: " + id + "\n" );
                                manager.updateResourceConfig( id, configLocation );

                                if ( m instanceof JDBCParamsManager ) {
                                    poolsToRemove.add( id );
                                    final ResourceState<JDBCParams> jdbcState = ( (JDBCParamsManager) manager ).getState( id );
                                    poolsToAdd.add( jdbcState );
                                }
                                // mark as done
                                modified.remove( configLocation );
                            }
                        }
                    }

                    // handle new additions
                    // note: addition must be handled after removals to ensure that ignore-switch is processed correctly
                    final File[] addedArray = (File[]) added.toArray( new File[added.size()] );
                    for ( File file : addedArray ) {
                        if ( isManagedBy( manager, file ) ) {
                            final String id = getId( file );
                            debugLog.append( "  add: " + id + "\n" );
                            manager.updateResourceConfig( id, file );
                            if ( m instanceof JDBCParamsManager ) {
                                final ResourceState<JDBCParams> state = ( (JDBCParamsManager) manager ).getState( id );
                                poolsToAdd.add( state );
                            }
                            // mark as done
                            added.remove( file );
                        }
                    }
                }
                if ( m instanceof AbstractBasicResourceManager ) {
                    final AbstractBasicResourceManager manager = (AbstractBasicResourceManager) m;
                    {

                        // handle manager specific behavoir
                        if ( m instanceof ConnectionManager ) {
                            final ConnectionManager cm = (ConnectionManager) m;
                            // handle removed
                            for ( String id : poolsToRemove ) {
                                ConnectionManager.destroy( id );
                            }
                            // handle added
                            for ( ResourceState<JDBCParams> rState : poolsToAdd ) {
                                final String id = rState.getId();
                                final JDBCParams params = rState.getResource();
                                cm.addPool( id, params, workspace );
                            }
                        }
                    }
                }
            }
            if ( added.size() + removed.size() + modified.size() > 0 ) {
                debugLog.append( "WARNING:" + "\n" );
                for ( File file : added ) {
                    debugLog.append( "NOT added: " + file + "\n" );
                }
                for ( File file : removed ) {
                    debugLog.append( "NOT removed: " + file + "\n" );
                }
                for ( File file : modified ) {
                    debugLog.append( "NOT updated: " + file + "\n" );
                }
            }

        } finally {
            LOG.debug( debugLog.toString() );
        }
    }

    private boolean isManagedBy( AbstractBasicResourceManager manager, File file ) {
        if ( !file.getParentFile().equals( manager.getBaseDir() ) )
            return false;
        final String fileName = file.getName();
        if ( manager instanceof OWSMetadataProviderManager ) {
            return fileName.endsWith( "_metadata.xml" );
        }
        if ( manager instanceof WebServicesConfiguration ) {
            return !fileName.endsWith( "_metadata.xml" );
        }
        return true;
    }

    private static final Pattern FILE_PATTERN_BASE = Pattern.compile( "(.*)\\.(xml|ignored)" );

    private static final Pattern FILE_PATTERN_METADATA = Pattern.compile( "(.*)_metadata\\.(xml|ignored)" );

    private String getId( File file ) {
        final String fileName = file.getName();
        final Matcher matcherMd = FILE_PATTERN_METADATA.matcher( fileName );
        if ( matcherMd.find() ) { //
            return matcherMd.group( 1 );
        }
        final Matcher matcherBase = FILE_PATTERN_BASE.matcher( fileName );
        if ( matcherBase.find() ) { //
            return matcherBase.group( 1 );
        }
        return fileName;
    }

}
