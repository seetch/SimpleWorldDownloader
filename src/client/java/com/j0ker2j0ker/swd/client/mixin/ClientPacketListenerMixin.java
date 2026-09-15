package com.j0ker2j0ker.swd.client.mixin;

import com.j0ker2j0ker.swd.client.util.SaveManager;
import com.j0ker2j0ker.swd.client.util.ChunkDownloadTracker;
import com.j0ker2j0ker.swd.client.util.WorldSessionTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.protocol.game.ClientboundAwardStatsPacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateAdvancementsPacket;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {

    @Shadow
    private ClientLevel level;

    @Inject(method = "handleLogin", at = @At("HEAD"))
    private void beforeHandleLogin(ClientboundLoginPacket packet, CallbackInfo ci) {
        ClientPacketListener listener = (ClientPacketListener) (Object) this;
        if (WorldSessionTracker.isKnownConnection(listener) && SaveManager.isSaving) {
            SaveManager.stop();
        }
    }

    @Inject(method = "handleLogin", at = @At("TAIL"))
    private void afterHandleLogin(ClientboundLoginPacket packet, CallbackInfo ci) {
        ClientPacketListener listener = (ClientPacketListener) (Object) this;
        WorldSessionTracker.onLogin(listener, packet);
        SaveManager.refreshSelectedWorldTracking();
    }

    @Inject(method = "handleLevelChunkWithLight", at = @At("TAIL"))
    private void handleLevelChunkWithLight(ClientboundLevelChunkWithLightPacket packet, CallbackInfo ci) {
        int chunkX = packet.getX();
        int chunkZ = packet.getZ();
        ChunkDownloadTracker.markLoaded(new ChunkPos(chunkX, chunkZ), this.level.dimension());
        if(!SaveManager.isSaving) return;

        Minecraft mc = Minecraft.getInstance();
        if(mc.isLocalServer() || mc.getCurrentServer() == null) return;
        LevelChunk wc = this.level.getChunkSource().getChunk(chunkX, chunkZ, false);
        if (wc == null || wc.isEmpty() || mc.level == null) return;

        SaveManager.saveChunkToRegion(SaveManager.path, wc, true, mc.level.dimension());
    }

    @Inject(method = "handleAwardStats", at = @At("TAIL"))
    private void handleAwardStats(ClientboundAwardStatsPacket packet, CallbackInfo ci) {
        SaveManager.cacheAwardStatsPacket(packet);
    }

    @Inject(method = "handleUpdateAdvancementsPacket", at = @At("TAIL"))
    private void handleUpdateAdvancementsPacket(ClientboundUpdateAdvancementsPacket packet, CallbackInfo ci) {
        SaveManager.cacheAdvancementPacket(packet);
    }
}
