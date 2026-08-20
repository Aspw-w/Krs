package com.instrumentalist.mixin.injector;

import com.mojang.authlib.minecraft.UserApiService;
import com.mojang.authlib.yggdrasil.ProfileResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.gui.screens.social.PlayerSocialManager;
import net.minecraft.client.multiplayer.ProfileKeyPairManager;
import net.minecraft.client.multiplayer.chat.report.ReportingContext;

@Mixin(Minecraft.class)
public interface IMinecraftClient {
    @Mutable
    @Accessor("user")
    void setSession(User session);

    @Mutable
    @Accessor("playerSocialManager")
    void setSocialInteractionsManager(PlayerSocialManager socialInteractionsManager);

    @Mutable
    @Accessor("reportingContext")
    void setAbuseReportContext(ReportingContext abuseReportContext);

    @Mutable
    @Accessor("profileFuture")
    void setGameProfileFuture(CompletableFuture<ProfileResult> future);

    @Mutable
    @Accessor("profileKeyPairManager")
    void setProfileKeys(ProfileKeyPairManager keys);

    @Mutable
    @Accessor("userApiService")
    void setUserApiService(UserApiService apiService);

    @Accessor("rightClickDelay")
    int krs$getRightClickDelay();

    @Mutable
    @Accessor("rightClickDelay")
    void krs$setRightClickDelay(int rightClickDelay);
}
