package com.wonderlando.chemecraft.block;

import java.util.HashSet;
import java.util.Set;

import com.wonderlando.chemecraft.block.entity.ReactorBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * Pure-geometry helpers for fluid transport. No fluid is buffered in pipes — a pipe run is just a route (and
 * a permission check), traced on demand. A reactor's inlet/outlet are two casing cells the controller tags
 * with a {@link ReactorCasingBlock.PortType}; the port face is that cell's front.
 */
public final class FluidTransport {
    /** Cap the pipe walk so a pathological layout can never loop the server. */
    private static final int MAX_PATH = 64;

    private FluidTransport() {}

    /** True if a pipe may connect to (pos, face): a reactor port, or any face of a single-block node. */
    public static boolean isPort(LevelReader level, BlockPos pos, Direction face) {
        var block = level.getBlockState(pos).getBlock();
        if (block instanceof SinkBlock || block instanceof ReservoirBlock) {
            return true; // single-block testing nodes connect on any face
        }
        return isReactorPort(level, pos, face);
    }

    /** True if (pos, face) is a reactor inlet or outlet casing port. */
    public static boolean isReactorPort(LevelReader level, BlockPos pos, Direction face) {
        return portAt(level, pos, face) != ReactorCasingBlock.PortType.NONE;
    }

    /**
     * The fluid node a pipe run ARRIVES AT as an inlet at (pos, face): a sink (any face) or a reactor inlet
     * casing (resolved to its controller block entity). Null if (pos, face) is not an inlet.
     */
    public static ReactorBlockEntity inletNodeAt(Level level, BlockPos pos, Direction face) {
        if (level.getBlockState(pos).getBlock() instanceof SinkBlock
                && level.getBlockEntity(pos) instanceof ReactorBlockEntity sink) {
            return sink; // a sink accepts on any face
        }
        if (isInletPort(level, pos, face)) {
            BlockPos controller = ReactorBlock.findController(level, pos);
            if (controller != null && level.getBlockEntity(controller) instanceof ReactorBlockEntity be) {
                return be;
            }
        }
        return null;
    }

    public static boolean isInletPort(LevelReader level, BlockPos pos, Direction face) {
        return portAt(level, pos, face) == ReactorCasingBlock.PortType.INLET;
    }

    public static boolean isOutletPort(LevelReader level, BlockPos pos, Direction face) {
        return portAt(level, pos, face) == ReactorCasingBlock.PortType.OUTLET;
    }

    /** The port kind exposed by the casing at {@code pos} on {@code face} (its outward face), or NONE. */
    private static ReactorCasingBlock.PortType portAt(LevelReader level, BlockPos pos, Direction face) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof ReactorCasingBlock)) {
            return ReactorCasingBlock.PortType.NONE;
        }
        if (face != state.getValue(BlockStateProperties.HORIZONTAL_FACING)) {
            return ReactorCasingBlock.PortType.NONE; // a port only on the face it points
        }
        return state.getValue(ReactorCasingBlock.PORT);
    }

    /**
     * Follow the pipe run leaving {@code fromPos} in {@code exitDir} and return the reactor whose INLET it
     * arrives at — or null if it dead-ends, loops, leaves the network somewhere that is not an inlet, or
     * comes back to {@code source}.
     */
    public static ReactorBlockEntity traceToInlet(Level level, BlockPos fromPos, Direction exitDir,
                                                  ReactorBlockEntity source) {
        BlockPos pos = fromPos.relative(exitDir);
        Direction entryFace = exitDir.getOpposite(); // the face of the block ahead that points back at us
        Set<BlockPos> visited = new HashSet<>();
        for (int step = 0; step < MAX_PATH; step++) {
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof PipeBlock) {
                if (!visited.add(pos)) {
                    return null; // loop
                }
                Direction exit = PipeBlock.otherPort(state, entryFace);
                if (exit == null) {
                    return null; // dead end / pipe does not conduct this way
                }
                pos = pos.relative(exit);
                entryFace = exit.getOpposite();
                continue;
            }
            // A mixer is an ideal zero-holdup junction: flow enters any inlet face and leaves the outlet face.
            // Passing the trace straight through lets several sources converge here onto one downstream inlet,
            // so that destination receives the sum (per species, out = Sigma in).
            if (state.getBlock() instanceof MixerBlock) {
                if (!visited.add(pos)) {
                    return null; // loop
                }
                Direction outlet = state.getValue(MixerBlock.FACING);
                if (entryFace == outlet) {
                    return null; // arrived at the outlet face — flow can't run backward into a mixer
                }
                pos = pos.relative(outlet);
                entryFace = outlet.getOpposite();
                continue;
            }
            // Left the pipe run: valid only if we arrived at a DIFFERENT node's inlet (reactor inlet or a sink).
            ReactorBlockEntity dest = inletNodeAt(level, pos, entryFace);
            if (dest != null && dest != source) {
                return dest;
            }
            return null;
        }
        return null;
    }
}
