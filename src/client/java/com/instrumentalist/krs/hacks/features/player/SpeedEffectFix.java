package com.instrumentalist.krs.hacks.features.player;

import com.instrumentalist.krs.events.features.UpdateEvent;
import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import com.instrumentalist.krs.utils.input.Keys;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

public class SpeedEffectFix extends Module {
    private static final AttributeModifier SPRINT_MODIFIER = new AttributeModifier(
            Identifier.withDefaultNamespace("sprinting"), 0.3F, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
    );

    public SpeedEffectFix() {
        super("Speed Effect Fix", ModuleCategory.Player, Keys.KEY_UNKNOWN, false, false);
    }

    @Override
    public void onEnable() {
    }

    @Override
    public void onDisable() {
    }

    @Override
    public void onUpdate(UpdateEvent event) {
        var player = mc.player;
        if (player == null)
            return;

        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null)
            return;

        boolean sprinting = player.isSprinting();
        AttributeModifier sprintModifier = speed.getModifier(SPRINT_MODIFIER.id());
        if (sprinting && !SPRINT_MODIFIER.equals(sprintModifier)) {
            speed.removeModifier(SPRINT_MODIFIER.id());
            speed.addTransientModifier(SPRINT_MODIFIER);
        } else if (!sprinting && sprintModifier != null) {
            speed.removeModifier(SPRINT_MODIFIER.id());
        }

        MobEffectInstance effect = player.getEffect(MobEffects.SPEED);
        if (effect == null)
            return;

        MobEffects.SPEED.value().createModifiers(effect.getAmplifier(), (attribute, modifier) -> {
            if (!attribute.equals(Attributes.MOVEMENT_SPEED))
                return;
            if (modifier.equals(speed.getModifier(modifier.id())))
                return;
            speed.removeModifier(modifier.id());
            speed.addTransientModifier(modifier);
        });
    }
}
