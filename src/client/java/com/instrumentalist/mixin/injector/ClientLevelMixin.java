package com.instrumentalist.mixin.injector;

import com.instrumentalist.krs.hacks.ModuleManager;
import com.instrumentalist.krs.hacks.features.render.TrueSight;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Collections;
import java.util.Iterator;
import java.util.NoSuchElementException;

@Mixin(ClientLevel.class)
public abstract class ClientLevelMixin {

    @ModifyReturnValue(method = "getMarkerParticleTarget", at = @At("RETURN"))
    private Block krs$trueSightBarriers(Block original) {
        if (ModuleManager.getModuleState(TrueSight.class) && TrueSight.barriers.get())
            return Blocks.BARRIER;

        return original;
    }

    @ModifyReturnValue(method = "entitiesForRendering", at = @At("RETURN"))
    private Iterable<Entity> krs$skipNullRenderEntities(Iterable<Entity> original) {
        if (original == null)
            return Collections::emptyIterator;

        return () -> new Iterator<>() {
            private final Iterator<Entity> delegate = original.iterator();
            private Entity next;
            private boolean hasBufferedNext;

            @Override
            public boolean hasNext() {
                if (hasBufferedNext)
                    return true;

                while (delegate.hasNext()) {
                    Entity entity = delegate.next();
                    if (entity == null)
                        continue;

                    next = entity;
                    hasBufferedNext = true;
                    return true;
                }

                return false;
            }

            @Override
            public Entity next() {
                if (!hasNext())
                    throw new NoSuchElementException();

                Entity entity = next;
                next = null;
                hasBufferedNext = false;
                return entity;
            }
        };
    }
}
