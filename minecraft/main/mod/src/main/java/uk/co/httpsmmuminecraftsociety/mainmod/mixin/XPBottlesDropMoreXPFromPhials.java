package uk.co.httpsmmuminecraftsociety.mainmod.mixin;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownExperienceBottle;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.SculkPhialCharm;

@Mixin(ThrownExperienceBottle.class)
public abstract class XPBottlesDropMoreXPFromPhials
{
    @Inject(method = "onHit", at = @At("HEAD"), cancellable = true)
    private void overrideCustomXpBottle(HitResult hitResult, CallbackInfo ci)
    {
        ThrownExperienceBottle self = (ThrownExperienceBottle)(Object)this;

        if (!(self.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        ItemStack thrownStack = self.getItem();
        if (thrownStack.isEmpty()) {
            return;
        }

        CustomData customData = thrownStack.get(DataComponents.CUSTOM_DATA);
        if (customData == null) {
            return;
        }

        CompoundTag nbt = customData.copyTag();
        if (!nbt.contains(SculkPhialCharm.XP_STORED_ID)) {
            return;
        }

        int xp = Math.max(nbt.getIntOr(SculkPhialCharm.XP_STORED_ID, 0), 0);
        if (xp <= 0) {
            return;
        }

        // trigger bottle break visual effect
        serverLevel.levelEvent(2002, self.blockPosition(), -13083194);

        Vec3 direction;
        if (hitResult instanceof BlockHitResult blockHitResult) {
            direction = blockHitResult.getDirection().getUnitVec3();
        } else {
            direction = self.getDeltaMovement().scale(-1.0D);
        }

        ExperienceOrb.awardWithDirection(serverLevel, hitResult.getLocation(), direction, xp);
        self.discard();
        ci.cancel();
    }
}