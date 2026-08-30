package litematica.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.network.play.server.SPacketChunkData;
import net.minecraft.network.play.server.SPacketCustomPayload;

import litematica.config.Configs;
import litematica.schematic.verifier.SchematicVerifierManager;
import litematica.shared.MinecraftPluginChannelTransport;
import litematica.world.SchematicWorldRenderingNotifier;

@Mixin(NetHandlerPlayClient.class)
public abstract class MixinNetHandlerPlayClient
{
    @Inject(method = "handleChunkData", at = @At("RETURN"))
    private void onChunkData(SPacketChunkData packetIn, CallbackInfo ci)
    {
        if (Configs.Visuals.MAIN_RENDERING_TOGGLE.getBooleanValue() &&
            Configs.Visuals.SCHEMATIC_RENDERING.getBooleanValue())
        {
            SchematicWorldRenderingNotifier.markSchematicChunksForRenderUpdate(packetIn.getChunkX(), packetIn.getChunkZ());
        }

        SchematicVerifierManager.INSTANCE.onChunkChanged(packetIn.getChunkX(), packetIn.getChunkZ());
    }

    @Inject(method = "handleCustomPayload", at = @At("HEAD"), cancellable = true)
    private void onCustomPayload(SPacketCustomPayload packetIn, CallbackInfo ci)
    {
        MinecraftPluginChannelTransport transport = MinecraftPluginChannelTransport.INSTANCE;

        if (transport.handles(packetIn))
        {
            transport.handlePayload(packetIn);
            ci.cancel();
        }
    }
}
