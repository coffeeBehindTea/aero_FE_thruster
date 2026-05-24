package dev.dada2.aerofethrusters.registry;

import dev.dada2.aerofethrusters.AeroFeThrusters;
import dev.dada2.aerofethrusters.network.UpdateThrusterSettingsPacket;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Network payload registration.
 */
public class AftPackets {
    /**
     * Registers client-to-server packets used by the configuration UI.
     *
     * @param event NeoForge payload registration event
     */
    public static void register(final RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar(AeroFeThrusters.MOD_ID).versioned("1");
        registrar.playToServer(
                UpdateThrusterSettingsPacket.TYPE,
                UpdateThrusterSettingsPacket.STREAM_CODEC,
                UpdateThrusterSettingsPacket::handle);
    }
}
