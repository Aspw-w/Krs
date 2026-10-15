package com.instrumentalist.mixin.oringo;

import net.minecraft.world.entity.Entity;

public interface IEntityRenderState {
    Entity client$getEntity();

    void client$setEntity(Entity entity);

    void client$setApplyLocalAnim(boolean val);

    boolean client$shouldApplyLocalAnim();
}
