package com.instrumentalist.mixin.injector;

import com.instrumentalist.mixin.oringo.IEntityRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(EntityRenderState.class)
public abstract class EntityRenderStateMixin implements IEntityRenderState {
    @Unique
    private Entity entity;

    @Unique
    private boolean client$applyLocalAnim;

    @Override
    public Entity client$getEntity() {
        return entity;
    }

    @Override
    public void client$setEntity(Entity entity) {
        this.entity = entity;
    }

    @Override
    public void client$setApplyLocalAnim(boolean value) { this.client$applyLocalAnim = value; }

    @Override
    public boolean client$shouldApplyLocalAnim() { return this.client$applyLocalAnim; }
}