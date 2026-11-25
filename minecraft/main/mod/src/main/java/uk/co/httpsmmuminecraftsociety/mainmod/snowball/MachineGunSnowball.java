package uk.co.httpsmmuminecraftsociety.mainmod.snowball;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;

final class MachineGunSnowball extends Snowball {
    private static final ResourceKey<DamageType> DAMAGE_TYPE = ResourceKey.create(Registries.DAMAGE_TYPE,
            Identifier.fromNamespaceAndPath("mainmod", "snowball_machine_gun"));

    MachineGunSnowball(Level level, LivingEntity owner, ItemStack stack) {
        super(level, owner, stack);
    }

    @Override
    protected void onHitEntity(EntityHitResult hit) {
        if (!(level() instanceof ServerLevel serverLevel) || !(hit.getEntity() instanceof LivingEntity target)) return;

        DamageSource source = new DamageSource(serverLevel.registryAccess()
                .lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(DAMAGE_TYPE), this, getOwner());
        if (random.nextInt(8) == 0)
            target.hurtServer(serverLevel, source, 1.0F);
        if (random.nextInt(4) == 0)
            target.knockback(0.2D, getX() - target.getX(), getZ() - target.getZ(),
                    source, 1.0F);
    }
}
