package dev.dada2.aerofethrusters.network;

import dev.dada2.aerofethrusters.AeroFeThrusters;
import dev.dada2.aerofethrusters.content.ElectricThrusterMenu;
import dev.dada2.aerofethrusters.content.RedstoneControlMode;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client-to-server packet sent when the thruster configuration UI changes.
 *
 * @param pos thruster block position echoed by the client
 * @param maxThrust requested max thrust in pN, rounded server-side to 4 decimals
 * @param redstoneMode requested {@link RedstoneControlMode#id()} value
 */
public record UpdateThrusterSettingsPacket(BlockPos pos, double maxThrust, int redstoneMode)
        implements CustomPacketPayload {
    public static final Type<UpdateThrusterSettingsPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AeroFeThrusters.MOD_ID, "update_thruster_settings"));
    public static final StreamCodec<RegistryFriendlyByteBuf, UpdateThrusterSettingsPacket> STREAM_CODEC =
            StreamCodec.ofMember(UpdateThrusterSettingsPacket::write, UpdateThrusterSettingsPacket::read);

    /**
     * Decodes a packet from the network buffer.
     *
     * @param buffer source network buffer
     * @return decoded packet
     */
    private static UpdateThrusterSettingsPacket read(final RegistryFriendlyByteBuf buffer) {
        return new UpdateThrusterSettingsPacket(buffer.readBlockPos(), buffer.readDouble(), buffer.readVarInt());
    }

    /**
     * Encodes this packet into the network buffer.
     *
     * @param buffer destination network buffer
     */
    private void write(final RegistryFriendlyByteBuf buffer) {
        buffer.writeBlockPos(this.pos);
        buffer.writeDouble(this.maxThrust);
        buffer.writeVarInt(this.redstoneMode);
    }

    /**
     * Applies the packet on the server main thread.
     *
     * <p>The current open menu is used as the authority. This prevents a client
     * from applying settings to arbitrary positions it is not actually editing.</p>
     *
     * @param packet decoded packet
     * @param context NeoForge packet handling context
     */
    public static void handle(final UpdateThrusterSettingsPacket packet, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }

            if (!(player.containerMenu instanceof ElectricThrusterMenu menu)) {
                return;
            }

            menu.applyServerSettings(packet.pos, packet.maxThrust, RedstoneControlMode.byId(packet.redstoneMode));
        });
    }

    /** @return custom payload type id */
    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
