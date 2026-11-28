package uk.co.httpsmmuminecraftsociety.mainmod.size;

import net.fabricmc.fabric.api.event.player.ItemEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.EnderDragonPart;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import org.jspecify.annotations.Nullable;
import uk.co.httpsmmuminecraftsociety.mainmod.claims.ClaimsManager;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.FakeItems;

import java.util.Map;
import java.util.WeakHashMap;

public final class SizeDiscs {
    private static final String SHRINK = "shrinking-disc";
    private static final String GROW = "growing-disc";
    private static final Map<ServerPlayer, Long> DIRECT_USES = new WeakHashMap<>();

    private SizeDiscs() {}

    public static void init() {
        UseEntityCallback.EVENT.register(SizeDiscs::onUseEntity);
        ItemEvents.USE.register(SizeDiscs::onUse);
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (entity instanceof EnderDragon dragon)
                for (EnderDragonPart part : dragon.getSubEntities()) part.refreshDimensions();
        });
    }

    public static boolean isProjectile(Projectile projectile) {
        return projectile instanceof DiscProjectile;
    }

    private static boolean isDisc(ItemStack stack) {
        return FakeItems.isSpecificFakeItem(stack, SHRINK)
                || FakeItems.isSpecificFakeItem(stack, GROW);
    }

    private static InteractionResult onUseEntity(Player player, Level level, InteractionHand hand,
                                                  Entity entity, @Nullable EntityHitResult hit) {
        ItemStack stack = player.getItemInHand(hand);
        if (!isDisc(stack)) return InteractionResult.PASS;
        Mob mob = mobFrom(entity);
        if (!(player instanceof ServerPlayer serverPlayer) || mob == null)
            return InteractionResult.PASS;
        if (!ClaimsManager.checkDirect(player, level, entity.blockPosition(), ClaimsManager.MOB_INTERACTION))
            return InteractionResult.FAIL;
        DIRECT_USES.put(serverPlayer, level.getGameTime());
        if (!resize(mob, FakeItems.isSpecificFakeItem(stack, GROW))) return InteractionResult.FAIL;
        stack.consume(1, player);
        level.playSound(null, mob.getX(), mob.getY(), mob.getZ(),
                SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL, 0.7F, 1.0F);
        return InteractionResult.SUCCESS_SERVER;
    }

    private static InteractionResult onUse(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!isDisc(stack) || !(level instanceof ServerLevel serverLevel)
                || !(player instanceof ServerPlayer serverPlayer)) return null;
        // Client prediction can send an item-use packet after the entity-use packet.
        Long directUseTick = DIRECT_USES.remove(serverPlayer);
        if (directUseTick != null && directUseTick == level.getGameTime())
            return InteractionResult.SUCCESS_SERVER;
        Projectile.spawnProjectileFromRotation(DiscProjectile::new, serverLevel, stack.copyWithCount(1),
                serverPlayer, 0.0F, 1.5F, 1.0F);
        serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.SNOWBALL_THROW, SoundSource.PLAYERS, 0.5F, 1.0F);
        stack.consume(1, player);
        return InteractionResult.SUCCESS_SERVER;
    }

    private static boolean resize(Mob mob, boolean grow) {
        AttributeInstance scale = mob.getAttribute(Attributes.SCALE);
        if (scale == null) return false;
        double current = scale.getValue();
        if (current <= 0.0D) return false;
        double width = mob.getBbWidth() / current;
        double height = mob.getBbHeight() / current;
        double next = nextScale(current, width, height, grow);
        if (Math.abs(next - current) < 0.0001D) return false;
        scale.setBaseValue(scale.getBaseValue() * next / current);
        mob.refreshDimensions();
        if (mob instanceof EnderDragon dragon)
            for (EnderDragonPart part : dragon.getSubEntities()) part.refreshDimensions();
        return true;
    }

    private static Mob mobFrom(Entity entity) {
        if (entity instanceof EnderDragonPart part) return part.parentMob;
        return entity instanceof Mob mob ? mob : null;
    }

    public static double nextScale(double current, double width, double height, boolean grow) {
        double smallestSide = Math.min(width, height);
        // Very narrow mobs may still reach half their normal size.
        double minimum = Math.min(0.5D, 0.25D / smallestSide);
        double maximum = 12.0D / height;
        // The uncapped sizes are powers of two. Snap back to that ladder after a capped step.
        double lower = Math.scalb(1.0D, Math.getExponent(current));
        double upper = lower * 2.0D;
        if (Math.abs(current - upper) < upper * 0.000001D) lower = upper;
        boolean onStep = Math.abs(current - lower) < lower * 0.000001D;
        double desired = grow ? lower * 2.0D : onStep ? lower / 2.0D : lower;
        return grow ? Math.min(desired, maximum) : Math.max(desired, minimum);
    }

    public static final class DiscProjectile extends Snowball {
        public DiscProjectile(Level level, net.minecraft.world.entity.LivingEntity owner, ItemStack stack) {
            super(level, owner, stack);
        }

        @Override
        protected void onHitEntity(EntityHitResult hit) {
            Mob mob = mobFrom(hit.getEntity());
            if (!(level() instanceof ServerLevel) || !(getOwner() instanceof ServerPlayer player)
                    || mob == null) return;
            if (!ClaimsManager.canPerform(player, level(), hit.getEntity().blockPosition(), ClaimsManager.MOB_INTERACTION))
                return;
            if (resize(mob, FakeItems.isSpecificFakeItem(getItem(), GROW)))
                level().playSound(null, mob.getX(), mob.getY(), mob.getZ(),
                        SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL, 0.7F, 1.0F);
        }
    }
}
