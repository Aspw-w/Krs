package com.instrumentalist.mixin.injector;

import net.minecraft.client.gui.components.ChatComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Mixin(ChatComponent.class)
public interface ChatComponentAccessor {
    @Accessor("trimmedMessages")
    List<?> krs$getTrimmedMessages();

    @Accessor("chatScrollbarPos")
    int krs$getChatScrollbarPos();

    @Accessor("chatScrollbarPos")
    void krs$setChatScrollbarPos(int pos);

    @Accessor("newMessageSinceScroll")
    void krs$setNewMessageSinceScroll(boolean value);
}
