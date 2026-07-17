package uk.co.httpsmmuminecraftsociety.mainmod.fishing;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Drowned;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.minecraft.server.level.ServerLevel;

public final class FishingJumpScares {
    public static final float CHANCE = 0.02F;
    private static final int PEACEFUL_CHOICES = 9;
    private static final int VIOLENT_CHOICES = 8;

    private FishingJumpScares() {
    }

    public static boolean shouldTrigger(RandomSource random) {
        return random.nextFloat() < CHANCE;
    }

    public static void spawn(ServerLevel level, FishingHook hook, Player player) {
        RandomSource random = hook.getRandom();
        boolean violent = random.nextBoolean();
        int choice = random.nextInt(violent ? VIOLENT_CHOICES : PEACEFUL_CHOICES);
        EntityType<? extends Mob> type = violent ? violentType(choice) : peacefulType(choice);
        BlockPos pos = hook.blockPosition();
        Mob mob = type.create(level, null, pos, EntitySpawnReason.EVENT, false, false);
        if (mob == null) {
            return;
        }

        if (mob instanceof AgeableMob ageable) {
            ageable.setBaby(!violent && random.nextBoolean());
        }
        if (mob instanceof Drowned drowned) {
            configureDrowned(drowned, choice);
        }
        if (violent) {
            mob.setTarget(player);
        }

        double startY = hook.getY() + 0.25D;
        mob.setPos(hook.getX(), startY, hook.getZ());
        mob.setDeltaMovement(launchVelocity(
                hook.getX(), startY, hook.getZ(),
                player.getX(), player.getY() + player.getBbHeight() * 0.65D, player.getZ()
        ));
        level.addFreshEntity(mob);
    }

    private static EntityType<? extends Mob> peacefulType(int choice) {
        return switch (choice) {
            case 0 -> EntityTypes.AXOLOTL;
            case 1 -> EntityTypes.COD;
            case 2 -> EntityTypes.SALMON;
            case 3 -> EntityTypes.SQUID;
            case 4 -> EntityTypes.GLOW_SQUID;
            case 5 -> EntityTypes.TROPICAL_FISH;
            case 6 -> EntityTypes.TADPOLE;
            case 7 -> EntityTypes.DOLPHIN;
            default -> EntityTypes.NAUTILUS;
        };
    }

    private static EntityType<? extends Mob> violentType(int choice) {
        return switch (choice) {
            case 0 -> EntityTypes.ZOMBIE_NAUTILUS;
            case 1 -> EntityTypes.GUARDIAN;
            case 2 -> EntityTypes.ELDER_GUARDIAN;
            default -> EntityTypes.DROWNED;
        };
    }

    private static void configureDrowned(Drowned drowned, int choice) {
        for (EquipmentSlot slot : new EquipmentSlot[]{
                EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND, EquipmentSlot.HEAD,
                EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
        }) {
            drowned.setItemSlot(slot, ItemStack.EMPTY);
        }
        drowned.setBaby(false);

        switch (choice) {
            case 3 -> equip(drowned, EquipmentSlot.HEAD, Items.LEATHER_HELMET);
            case 4 -> {
                equip(drowned, EquipmentSlot.HEAD, Items.LEATHER_HELMET);
                equip(drowned, EquipmentSlot.MAINHAND, Items.IRON_SWORD);
            }
            case 5 -> {
                equip(drowned, EquipmentSlot.HEAD, Items.LEATHER_HELMET);
                equip(drowned, EquipmentSlot.MAINHAND, Items.TRIDENT);
            }
            case 6 -> equipIronArmor(drowned);
            case 7 -> {
                equipIronArmor(drowned);
                equip(drowned, EquipmentSlot.MAINHAND, Items.IRON_SWORD);
                var maxHealth = drowned.getAttribute(Attributes.MAX_HEALTH);
                if (maxHealth != null) {
                    maxHealth.setBaseValue(100.0D);
                    drowned.setHealth(100.0F);
                }
                drowned.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, MobEffectInstance.INFINITE_DURATION));
            }
            default -> throw new IllegalArgumentException("Not a drowned jump-scare choice: " + choice);
        }
    }

    private static void equipIronArmor(Drowned drowned) {
        equip(drowned, EquipmentSlot.HEAD, Items.IRON_HELMET);
        equip(drowned, EquipmentSlot.CHEST, Items.IRON_CHESTPLATE);
        equip(drowned, EquipmentSlot.LEGS, Items.IRON_LEGGINGS);
        equip(drowned, EquipmentSlot.FEET, Items.IRON_BOOTS);
    }

    private static void equip(Drowned drowned, EquipmentSlot slot, net.minecraft.world.item.Item item) {
        drowned.setItemSlot(slot, new ItemStack(item));
        drowned.setDropChance(slot, 0.0F);
    }

    static Vec3 launchVelocity(
            double startX, double startY, double startZ,
            double targetX, double targetY, double targetZ
    ) {
        double dx = targetX - startX;
        double dz = targetZ - startZ;
        double flightTicks = Mth.clamp(Math.hypot(dx, dz) / 0.65D, 8.0D, 24.0D);
        // Compensate for ordinary entity gravity so the arc meets the player's torso.
        double vy = (targetY - startY) / flightTicks + 0.04D * flightTicks;
        return new Vec3(dx / flightTicks, vy, dz / flightTicks);
    }
}
