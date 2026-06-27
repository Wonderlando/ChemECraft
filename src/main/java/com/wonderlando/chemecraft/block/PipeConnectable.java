package com.wonderlando.chemecraft.block;

/**
 * Marker for blocks (besides other pipes and reactors) that a {@link PipeBlock} may grow a connection
 * toward. Mixers and splitters implement this so pipes plug into them on any face — those hubs are the
 * only place a pipe run is allowed to branch or merge.
 */
public interface PipeConnectable {
}
