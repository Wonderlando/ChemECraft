package com.wonderlando.chemecraft.block;

import java.util.EnumMap;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A thin fluid pipe limited to ONE inlet and ONE outlet (a 2-port conduit; branching/merging needs a
 * mixer/splitter). {@code FACING} is the flow direction shown by an arrow (on top for horizontal pipes,
 * on the sides for vertical ones). Both ports may face any of the six directions, so pipes run up and down
 * as well as side to side.
 *
 * <p>Connections grow as you build: a placed pipe claims a free port toward each adjacent pipe or reactor,
 * forming straights and elbows (including horizontal-to-vertical), and orients the arrows along the flow.
 * Three rules keep it sane: it never takes more than {@link #MAX_PORTS} ports, it never connects to a
 * PARALLEL pipe (one sharing its flow axis but sitting off to the side, so parallel runs stay separate),
 * and it never connects to a pipe that is already FULL (so finished runs aren't rerouted by new pipes).
 */
public class PipeBlock extends Block {
    /** Outlet/flow direction (the arrow). Any of the six directions. */
    public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING;

    /** One connection flag per side; at most {@link #MAX_PORTS} are ever true (1 inlet + 1 outlet). */
    private static final Map<Direction, BooleanProperty> PORT = Map.of(
            Direction.NORTH, BlockStateProperties.NORTH,
            Direction.EAST, BlockStateProperties.EAST,
            Direction.SOUTH, BlockStateProperties.SOUTH,
            Direction.WEST, BlockStateProperties.WEST,
            Direction.UP, BlockStateProperties.UP,
            Direction.DOWN, BlockStateProperties.DOWN);

    private static final int MAX_PORTS = 2;

    private static final VoxelShape CORE = Shapes.box(0.25, 0.25, 0.25, 0.75, 0.75, 0.75);
    private static final Map<Direction, VoxelShape> ARM = armShapes();

    public PipeBlock(BlockBehaviour.Properties properties) {
        super(properties);
        BlockState state = this.stateDefinition.any().setValue(FACING, Direction.NORTH);
        for (BooleanProperty port : PORT.values()) {
            state = state.setValue(port, false);
        }
        this.registerDefaultState(state);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
        for (BooleanProperty port : PORT.values()) {
            builder.add(port);
        }
    }

    /** On placement: outlet = the way you're looking; then claim up to two ports toward eligible neighbours. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        LevelReader level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Direction facing = context.getNearestLookingDirection();
        BlockState state = this.defaultBlockState().setValue(FACING, facing);
        int ports = 0;
        for (Direction dir : placementOrder(facing)) {
            if (ports >= MAX_PORTS) {
                break;
            }
            if (canConnectTo(level, pos.relative(dir), facing, dir)) {
                state = state.setValue(PORT.get(dir), true);
                ports++;
            }
        }
        return state;
    }

    /** Write the reciprocal port onto each connected pipe, orienting its arrow along the flow. */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide()) {
            return;
        }
        Direction facing = state.getValue(FACING);
        for (Direction dir : Direction.values()) {
            if (!state.getValue(PORT.get(dir))) {
                continue;
            }
            BlockPos neighborPos = pos.relative(dir);
            BlockState neighbor = level.getBlockState(neighborPos);
            if (!(neighbor.getBlock() instanceof PipeBlock)) {
                continue; // reactors don't track connections
            }
            BlockState updated = neighbor.setValue(PORT.get(dir.getOpposite()), true);
            // If we don't output toward the neighbour, then it feeds us — point its arrow at us.
            if (facing != dir) {
                updated = updated.setValue(FACING, dir.getOpposite());
            }
            level.setBlock(neighborPos, updated, Block.UPDATE_ALL);
        }
    }

    /**
     * Wrench interaction. Plain right-click rotates the flow direction (the arrow); sneak-right-click on a
     * face toggles the connection (port) on that side, keeping the neighbour pipe's reciprocal port in sync.
     */
    public void wrench(Level level, BlockPos pos, BlockState state, Player player, Direction face, boolean sneaking) {
        if (sneaking) {
            toggleConnection(level, pos, state, face, player);
            return;
        }
        Direction[] all = Direction.values();
        Direction next = all[(state.getValue(FACING).ordinal() + 1) % all.length];
        level.setBlockAndUpdate(pos, state.setValue(FACING, next));
        tell(player, "Pipe flow: " + next.getName());
    }

    private void toggleConnection(Level level, BlockPos pos, BlockState state, Direction face, Player player) {
        BooleanProperty port = PORT.get(face);
        BooleanProperty back = PORT.get(face.getOpposite());
        BlockPos neighborPos = pos.relative(face);
        BlockState neighbor = level.getBlockState(neighborPos);
        boolean neighborIsPipe = neighbor.getBlock() instanceof PipeBlock;
        if (state.getValue(port)) {
            level.setBlockAndUpdate(pos, state.setValue(port, false));
            if (neighborIsPipe && neighbor.getValue(back)) {
                level.setBlockAndUpdate(neighborPos, neighbor.setValue(back, false));
            }
            tell(player, "Pipe disconnected: " + face.getName());
        } else {
            if (portCount(state) >= MAX_PORTS) {
                tell(player, "Pipe already has an inlet and outlet");
                return;
            }
            level.setBlockAndUpdate(pos, state.setValue(port, true));
            if (neighborIsPipe && portCount(neighbor) < MAX_PORTS) {
                level.setBlockAndUpdate(neighborPos, neighbor.setValue(back, true));
            }
            tell(player, "Pipe connected: " + face.getName());
        }
    }

    private static void tell(Player player, String message) {
        if (player != null) {
            player.sendOverlayMessage(Component.literal(message));
        }
    }

    /** On removal, free the reciprocal port on each connected pipe so it can accept new connections again. */
    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        for (Direction dir : Direction.values()) {
            if (!state.getValue(PORT.get(dir))) {
                continue;
            }
            BlockPos neighborPos = pos.relative(dir);
            BlockState neighbor = level.getBlockState(neighborPos);
            BooleanProperty back = PORT.get(dir.getOpposite());
            if (neighbor.getBlock() instanceof PipeBlock && neighbor.getValue(back)) {
                level.setBlock(neighborPos, neighbor.setValue(back, false), Block.UPDATE_ALL);
            }
        }
    }

    /**
     * Whether a pipe with outlet {@code facing} may claim a port toward {@code neighborPos} in {@code dir}:
     * a reactor always accepts; a pipe accepts only while it still has a free port (not FULL) and is not
     * PARALLEL (sharing this pipe's flow axis while sitting off to the side).
     */
    private static boolean canConnectTo(LevelReader level, BlockPos neighborPos, Direction facing, Direction dir) {
        BlockState neighbor = level.getBlockState(neighborPos);
        if (neighbor.getBlock() instanceof PipeBlock) {
            return portCount(neighbor) < MAX_PORTS && !isParallel(facing, neighbor.getValue(FACING), dir);
        }
        if (neighbor.getBlock() instanceof PipeConnectable) {
            return true; // mixers/splitters accept a connection on any face — that's where runs branch/merge
        }
        // Reactors only connect at a port: the controller's front (outlet) or the cell above it (inlet).
        return FluidTransport.isReactorPort(level, neighborPos, dir.getOpposite());
    }

    /**
     * For pipe-run tracing: the OTHER active port of this pipe given the face we entered through, or null if
     * we did not actually enter through a port or there is no single continuing port.
     */
    public static Direction otherPort(BlockState state, Direction entryFace) {
        if (!(state.getBlock() instanceof PipeBlock) || !state.getValue(PORT.get(entryFace))) {
            return null;
        }
        Direction other = null;
        for (Map.Entry<Direction, BooleanProperty> entry : PORT.entrySet()) {
            if (entry.getKey() != entryFace && state.getValue(entry.getValue())) {
                if (other != null) {
                    return null; // more than one other port (should not happen with MAX_PORTS = 2)
                }
                other = entry.getKey();
            }
        }
        return other;
    }

    /**
     * Grow a connection on the pipe at {@code pipePos} toward {@code face} (e.g. an adjacent hub that was
     * just placed next to it), if that pipe still has a free port. A no-op for non-pipe blocks.
     */
    public static void connect(Level level, BlockPos pipePos, Direction face) {
        BlockState state = level.getBlockState(pipePos);
        if (!(state.getBlock() instanceof PipeBlock)) {
            return;
        }
        BooleanProperty port = PORT.get(face);
        if (state.getValue(port) || portCount(state) >= MAX_PORTS) {
            return;
        }
        level.setBlock(pipePos, state.setValue(port, true), Block.UPDATE_ALL);
    }

    /** Drop the pipe's connection on {@code face} (e.g. when the adjacent hub it pointed at is removed). */
    public static void disconnect(Level level, BlockPos pipePos, Direction face) {
        BlockState state = level.getBlockState(pipePos);
        if (!(state.getBlock() instanceof PipeBlock)) {
            return;
        }
        BooleanProperty port = PORT.get(face);
        if (state.getValue(port)) {
            level.setBlock(pipePos, state.setValue(port, false), Block.UPDATE_ALL);
        }
    }

    /** Two pipes are parallel (and must not connect) when they share a flow axis but are offset across it. */
    private static boolean isParallel(Direction facing, Direction neighborFacing, Direction dir) {
        return facing.getAxis() == neighborFacing.getAxis() && dir.getAxis() != facing.getAxis();
    }

    private static int portCount(BlockState state) {
        int count = 0;
        for (BooleanProperty port : PORT.values()) {
            if (state.getValue(port)) {
                count++;
            }
        }
        return count;
    }

    /** Prefer the flow axis (behind = inlet, ahead = outlet), then the remaining sides. */
    private static Direction[] placementOrder(Direction facing) {
        Direction[] order = new Direction[Direction.values().length];
        order[0] = facing.getOpposite();
        order[1] = facing;
        int i = 2;
        for (Direction dir : Direction.values()) {
            if (dir != facing && dir != facing.getOpposite()) {
                order[i++] = dir;
            }
        }
        return order;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        VoxelShape shape = CORE;
        for (Map.Entry<Direction, BooleanProperty> entry : PORT.entrySet()) {
            if (state.getValue(entry.getValue())) {
                shape = Shapes.or(shape, ARM.get(entry.getKey()));
            }
        }
        return shape;
    }

    private static Map<Direction, VoxelShape> armShapes() {
        EnumMap<Direction, VoxelShape> arms = new EnumMap<>(Direction.class);
        arms.put(Direction.NORTH, Shapes.box(0.25, 0.25, 0.0, 0.75, 0.75, 0.25));
        arms.put(Direction.SOUTH, Shapes.box(0.25, 0.25, 0.75, 0.75, 0.75, 1.0));
        arms.put(Direction.WEST, Shapes.box(0.0, 0.25, 0.25, 0.25, 0.75, 0.75));
        arms.put(Direction.EAST, Shapes.box(0.75, 0.25, 0.25, 1.0, 0.75, 0.75));
        arms.put(Direction.UP, Shapes.box(0.25, 0.75, 0.25, 0.75, 1.0, 0.75));
        arms.put(Direction.DOWN, Shapes.box(0.25, 0.0, 0.25, 0.75, 0.25, 0.75));
        return arms;
    }
}
