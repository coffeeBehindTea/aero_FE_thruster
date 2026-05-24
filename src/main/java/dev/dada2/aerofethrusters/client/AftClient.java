package dev.dada2.aerofethrusters.client;

import dev.dada2.aerofethrusters.AeroFeThrusters;
import dev.dada2.aerofethrusters.registry.AftMenus;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/**
 * Client-only registration hooks.
 */
@EventBusSubscriber(modid = AeroFeThrusters.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class AftClient {
    /**
     * Binds menu types to their client screen factories.
     *
     * @param event NeoForge screen registration event
     */
    @SubscribeEvent
    public static void registerMenuScreens(final RegisterMenuScreensEvent event) {
        event.register(AftMenus.ELECTRIC_THRUSTER.get(), ElectricThrusterScreen::new);
    }
}
