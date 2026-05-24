package dev.dada2.aerofethrusters.registry;

import dev.dada2.aerofethrusters.AeroFeThrusters;
import dev.dada2.aerofethrusters.content.ElectricThrusterBlock;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Deferred registration for block instances.
 */
public class AftBlocks {
    private static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(Registries.BLOCK, AeroFeThrusters.MOD_ID);

    /** FE-powered Sable propeller block. */
    public static final DeferredHolder<Block, ElectricThrusterBlock> ELECTRIC_THRUSTER =
            BLOCKS.register("electric_thruster", () -> new ElectricThrusterBlock(
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.METAL)
                            .requiresCorrectToolForDrops()
                            .strength(4.0f, 8.0f)
                            .sound(SoundType.COPPER)
                            .lightLevel(state -> state.getValue(ElectricThrusterBlock.LIT) ? 15 : 0)));

    /**
     * Attaches the deferred register to NeoForge's MOD event bus.
     *
     * @param eventBus MOD event bus
     */
    public static void register(final IEventBus eventBus) {
        BLOCKS.register(eventBus);
    }
}
