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
 * with a {@link BatchReactorCasingBlock.PortType}; the port face is that cell's front.
 */
public final class FluidTransport {
    /** Cap the pipe walk so a pathological layout can never loop the server. */
    private static final int MAX_PATH = 64;

    private FluidTransport() {}

    /** True if (pos, face) is a reactor inlet or outlet — lets pipes connect to reactor ports. */
    public static boolean isReactorPort(LevelReader level, BlockPos pos, Direction face) {
        return portAt(level, pos, face) != BatchReactorCasingBlock.PortType.NONE;
    }

    public static boolean isInletPort(LevelReader level, BlockPos pos, Direction face) {
        return portAt(level, pos, face) == BatchReactorCasingBlock.PortType.INLET;
    }

    public static boolean isOutletPort(LevelReader level, BlockPos pos, Direction face) {
        return portAt(level, pos, face) == BatchReactorCasingBlock.PortType.OUTLET;
    }

    /** The port kind exposed by the casing at {@code pos} on {@code face} (its outward face), or NONE. */
    private static BatchReactorCasingBlock.PortType portAt(LevelReader level, BlockPos pos, Direction face) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof BatchReactorCasingBlock)) {
            return BatchReactorCasingBlock.PortType.NONE;
        }
        if (face != state.getValue(BlockStateProperties.HORIZONTAL_FACING)) {
            return BatchReactorCasingBlock.PortType.NONE; // a port only on the face it points
        }
        return state.getValue(BatchReactorCasingBlock.PORT);
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
            // Left the pipe run: valid only if we arrived at a DIFFERENT reactor's inlet.
            if (isInletPort(level, pos, entryFace)) {
                BlockPos controller = BatchReactorBlock.findController(level, pos);
                if (controller != null
                        && level.getBlockEntity(controller) instanceof ReactorBlockEntity dest
                        && dest != source) {
                    return dest;
                }
            }
            return null;
        }
        return null;
    }
}
