package dev.dada2.aerofethrusters.content;

import com.simibubi.create.content.equipment.wrench.IWrenchable;
import dev.dada2.aerofethrusters.registry.AftBlockEntityTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * Placed block for the electric thruster.
 *
 * <p>The block exposes two state properties: {@link #FACING} defines the thrust
 * axis and nozzle direction, while {@link #LIT} mirrors actual thrust output for
 * the lit nozzle texture and block light.</p>
 */
public class ElectricThrusterBlock extends Block implements EntityBlock, IWrenchable {
    /** Direction the nozzle points toward. */
    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    /** True while the block entity is outputting thrust. */
    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    private static final VoxelShape SHAPE = Shapes.block();

    /**
     * Creates the block with supplied registry properties.
     *
     * @param properties block behaviour properties configured during registration
     */
    public ElectricThrusterBlock(final Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.SOUTH)
                .setValue(LIT, false));
    }

    /**
     * Places the nozzle facing opposite the player's nearest look direction.
     *
     * @param context placement context from the item use
     * @return initial block state for placement
     */
    @Override
    public BlockState getStateForPlacement(final BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getNearestLookingDirection().getOpposite());
    }

    /** @return model render shape for JSON block models */
    @Override
    public RenderShape getRenderShape(final BlockState state) {
        return RenderShape.MODEL;
    }

    /**
     * Adds blockstate properties used by model variants and runtime lighting.
     *
     * @param builder mutable state definition builder
     */
    @Override
    protected void createBlockStateDefinition(final StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LIT);
    }

    /**
     * Supports Create wrench rotation and structure transforms.
     *
     * @param state current block state
     * @param rotation rotation to apply
     * @return rotated block state
     */
    @Override
    public BlockState rotate(final BlockState state, final Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    /**
     * Supports mirror transforms by delegating to the matching rotation.
     *
     * @param state current block state
     * @param mirror mirror operation to apply
     * @return mirrored block state
     */
    @Override
    public BlockState mirror(final BlockState state, final Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    /**
     * Lets held items such as the Create wrench handle their own right-click logic.
     *
     * @param stack item used on the block
     * @param state current block state
     * @param level world level
     * @param pos block position
     * @param player interacting player
     * @param hand interaction hand
     * @param hitResult block hit information
     * @return skip result for non-empty hands, otherwise pass to empty-hand logic
     */
    @Override
    protected ItemInteractionResult useItemOn(final ItemStack stack, final BlockState state, final Level level,
                                             final BlockPos pos, final Player player, final InteractionHand hand,
                                             final BlockHitResult hitResult) {
        if (!stack.isEmpty()) {
            return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    /**
     * Opens the configuration UI when the player right-clicks with an empty hand.
     *
     * @param state current block state
     * @param level world level
     * @param pos block position
     * @param player interacting player
     * @param hitResult block hit information
     * @return success when a thruster menu was opened
     */
    @Override
    protected InteractionResult useWithoutItem(final BlockState state, final Level level, final BlockPos pos,
                                               final Player player, final BlockHitResult hitResult) {
        if (!(level.getBlockEntity(pos) instanceof ElectricThrusterBlockEntity thruster)) {
            return InteractionResult.PASS;
        }

        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            thruster.openMenu(serverPlayer);
        }

        return InteractionResult.SUCCESS;
    }

    /**
     * Returns the collision and outline shape.
     *
     * @param state current block state
     * @param level block getter
     * @param pos block position
     * @param context collision context
     * @return full cube shape
     */
    @Override
    public VoxelShape getShape(final BlockState state, final BlockGetter level, final BlockPos pos,
                               final CollisionContext context) {
        return SHAPE;
    }

    /**
     * Creates the block entity that stores energy and physics state.
     *
     * @param pos block position
     * @param state current block state
     * @return new thruster block entity
     */
    @Nullable
    @Override
    public BlockEntity newBlockEntity(final BlockPos pos, final BlockState state) {
        return new ElectricThrusterBlockEntity(pos, state);
    }

    /**
     * Provides the server/client ticker for the registered thruster block entity type.
     *
     * @param level world level
     * @param state current block state
     * @param type queried block entity type
     * @param <T> block entity type parameter
     * @return ticker for electric thrusters, or {@code null} for other types
     */
    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(final Level level, final BlockState state,
                                                                  final BlockEntityType<T> type) {
        if (type != AftBlockEntityTypes.ELECTRIC_THRUSTER.get()) {
            return null;
        }
        return (tickerLevel, pos, tickerState, blockEntity) ->
                ((ElectricThrusterBlockEntity) blockEntity).tick();
    }
}
