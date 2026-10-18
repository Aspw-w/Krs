package com.instrumentalist.mixin.injector;

import com.instrumentalist.krs.utils.IMinecraft;
import com.instrumentalist.krs.hacks.ModuleManager;
import com.instrumentalist.krs.hacks.features.render.Capes;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.ClientAsset;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerInfo.class)
public abstract class PlayerListEntryMixin implements IMinecraft {

    @Shadow
    @Final
    private GameProfile profile;

    @Inject(method = "getSkin", at = @At("RETURN"), cancellable = true)
    private void getSkinTextures(CallbackInfoReturnable<PlayerSkin> ci) {
        var textures = ci.getReturnValue();
        if (!ModuleManager.getModuleState(Capes.class) || !Capes.customCape.get() || mc.player != null && !profile.id().equals(mc.player.getGameProfile().id()) || textures.cape() != null && !Capes.capeOverride.get()) return;
        Identifier capeId = Identifier.fromNamespaceAndPath("krs", "cape");
        Identifier capeFile = Identifier.fromNamespaceAndPath("krs", "cape.png");
        ClientAsset.Texture cape = new ClientAsset.ResourceTexture(capeId, capeFile);
        ci.setReturnValue(new PlayerSkin(textures.body(), cape, cape, textures.model(), textures.secure()));
    }
}
