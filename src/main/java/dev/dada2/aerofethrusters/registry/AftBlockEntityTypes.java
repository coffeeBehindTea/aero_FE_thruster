package dev.dada2.aerofethrusters.registry;

import dev.dada2.aerofethrusters.AeroFeThrusters;
import dev.dada2.aerofethrusters.content.ElectricThrusterBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Deferred registration for block entity types owned by this mod.
 */
public class AftBlockEntityTypes {
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, AeroFeThrusters.MOD_ID);

    /** Block entity type used by the electric thruster block. */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ElectricThrusterBlockEntity>> ELECTRIC_THRUSTER =
            BLOCK_ENTITY_TYPES.register("electric_thruster", () -> BlockEntityType.Builder.of(
                    ElectricThrusterBlockEntity::new,
                    AftBlocks.ELECTRIC_THRUSTER.get()).build(null));

    /**
     * Attaches the deferred register to NeoForge's MOD event bus.
     *
     * @param eventBus MOD event bus
     */
    public static void register(final IEventBus eventBus) {
        BLOCK_ENTITY_TYPES.register(eventBus);
    }
}
