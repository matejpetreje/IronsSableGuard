package cz.matej.ironssableguard.mixin;

import io.redspace.ironsspellbooks.effect.AbyssalShroudEffect;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = AbyssalShroudEffect.class, remap = false)
public abstract class AbyssalShroudEffectMixin {

    @Inject(method = "doEffect", at = @At("HEAD"), cancellable = true)
    private static void ironssableguard$disableAbyssalShroudCompletely(
            LivingEntity entity,
            DamageSource damageSource,
            CallbackInfoReturnable<Boolean> cir
    ) {
        cir.setReturnValue(false);
    }
}