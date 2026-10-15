package com.instrumentalist.mixin.injector;

import com.instrumentalist.krs.Client;
import com.instrumentalist.krs.events.EventManager;
import com.instrumentalist.krs.events.features.ModifyPacketEvent;
import com.instrumentalist.krs.events.features.SendPacketEvent;
import com.instrumentalist.krs.utils.packet.MovePlayerPacketFixer;
import com.instrumentalist.krs.utils.packet.PacketUtil;
import com.instrumentalist.krs.utils.network.IConnection;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.netty.channel.ChannelFutureListener;
import net.minecraft.client.Minecraft;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketUtils;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.crypto.Cipher;

@Mixin(Connection.class)
public abstract class ClientConnectionMixin implements IConnection {

    @Unique
    private boolean krs$encrypted;

    @Shadow
    public abstract void send(Packet<?> packet, @Nullable ChannelFutureListener listener);

    @Inject(method = "setEncryptionKey", at = @At("RETURN"))
    private void krs$trackEncryption(Cipher decryptingCipher, Cipher encryptingCipher, CallbackInfo ci) {
        krs$encrypted = true;
    }

    @Override
    public boolean krs$isEncrypted() {
        return krs$encrypted;
    }

    @WrapMethod(method = "send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;)V")
    private void modifyPacket(Packet<?> packet, @Nullable ChannelFutureListener listener, Operation<Void> original) {
        EventManager eventManager = Client.eventManager;
        if (eventManager == null || packet == null || !eventManager.hasListeners(ModifyPacketEvent.class)) {
            if (packet != null && Client.rotationManager != null)
                Client.rotationManager.recordOutgoingRotation(packet);
            if (packet != null) {
                MovePlayerPacketFixer.beforeSend(this, packet, extra -> original.call(extra, null));
                original.call(packet, listener);
            }
            return;
        }

        final ModifyPacketEvent event = new ModifyPacketEvent(packet);
        eventManager.call(event);
        if (event.isCancelled() || event.packet == null)
            return;

        if (Client.rotationManager != null)
            Client.rotationManager.recordOutgoingRotation(event.packet);

        MovePlayerPacketFixer.beforeSend(this, event.packet, extra -> original.call(extra, null));
        original.call(event.packet, listener);
    }

    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;)V", at = @At("HEAD"), cancellable = true)
    private void sendPacketEvent(Packet<?> packet, final CallbackInfo ci) {
        if (PacketUtil.consumeSilentPacket(packet)) {
            return;
        }

        EventManager eventManager = Client.eventManager;
        if (eventManager == null || packet == null || !eventManager.hasListeners(SendPacketEvent.class))
            return;

        final SendPacketEvent event = new SendPacketEvent(packet);
        eventManager.call(event);
        ci.cancel();

        if (!event.isCancelled() && event.packet != null) {
            this.send(event.packet, (ChannelFutureListener) null);
        }
    }

    @Inject(method = "genericsFtw", at = @At("HEAD"))
    @SuppressWarnings("unchecked")
    private static void scheduleClientboundPacket(Packet<?> packet, PacketListener listener, CallbackInfo ci) {
        if (!(listener instanceof ClientGamePacketListener) || packet == null)
            return;

        Packet<PacketListener> typedPacket = (Packet<PacketListener>) packet;
        PacketUtils.ensureRunningOnSameThread(typedPacket, listener, Minecraft.getInstance().packetProcessor());
    }

}
