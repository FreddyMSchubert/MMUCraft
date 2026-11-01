package uk.co.httpsmmuminecraftsociety.mainmod.fishing;

import net.minecraft.core.BlockPos;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.monster.hoglin.Hoglin;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
import net.minecraft.world.entity.monster.cubemob.MagmaCube;
import net.minecraft.world.entity.monster.zombie.Drowned;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.entity.projectile.hurtingprojectile.LargeFireball;
import net.minecraft.world.entity.projectile.hurtingprojectile.SmallFireball;
import net.minecraft.world.entity.projectile.hurtingprojectile.windcharge.WindCharge;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.phys.Vec3;

public final class FishingJumpScares {
    public static final float CHANCE = 0.01F;
    private static final int PEACEFUL_CHOICES = 9;
    private static final int VIOLENT_CHOICES = 9;
    private static final int NETHER_MOB_CHOICES = 10;
    private static final int NETHER_CHOICES = 13;
    private static final int END_MOB_CHOICES = 3;
    private static final int END_CHOICES = 5;

    private FishingJumpScares() {
    }

    public static boolean shouldTrigger(RandomSource random, ResourceKey<Level> dimension) {
        return random.nextFloat() < CHANCE * (dimension == Level.NETHER ? 10.0F
                : dimension == Level.END ? 5.0F : 1.0F);
    }

    public static boolean spawn(ServerLevel level, FishingHook hook, Player player) {
        RandomSource random = hook.getRandom();
        boolean nether = level.dimension() == Level.NETHER;
        boolean end = level.dimension() == Level.END;
        boolean violent = nether || end || random.nextBoolean();
        int choices = nether ? NETHER_CHOICES : end ? END_CHOICES
                : violent ? VIOLENT_CHOICES : PEACEFUL_CHOICES;
        int choice = random.nextInt(choices);
        if (nether && choice >= NETHER_MOB_CHOICES) {
            return spawnProjectile(level, hook, player, choice - NETHER_MOB_CHOICES);
        }
        if (end && choice >= END_MOB_CHOICES) {
            return spawnProjectile(level, hook, player, choice - END_MOB_CHOICES);
        }
        if (!nether && !end && violent && choice == VIOLENT_CHOICES - 1) {
            return spawnProjectile(level, hook, player, 0);
        }
        EntityType<? extends Mob> type = nether
                ? netherType(choice)
                : end ? endType(choice)
                : violent ? violentType(choice) : peacefulType(choice);
        BlockPos pos = hook.blockPosition();
        Mob mob = type.create(level, null, pos, EntitySpawnReason.EVENT, false, false);
        if (mob == null) {
            return false;
        }

        if (!nether && !end && mob instanceof AgeableMob ageable) {
            ageable.setBaby(!violent && random.nextBoolean());
        }
        if (mob instanceof Drowned drowned) {
            configureDrowned(drowned, choice);
        }
        if (mob instanceof MagmaCube cube) {
            cube.setSize(1 << random.nextInt(3), true);
        }
        if (violent) {
            mob.setTarget(player);
            if (nether || end) {
                if (mob instanceof AbstractPiglin piglin) {
                    piglin.getBrain().setMemoryWithExpiry(MemoryModuleType.ANGRY_AT, player.getUUID(), 600L);
                } else if (mob instanceof Hoglin hoglin) {
                    hoglin.getBrain().setMemoryWithExpiry(MemoryModuleType.ATTACK_TARGET, player, 200L);
                } else if (mob instanceof NeutralMob neutral) {
                    neutral.setPersistentAngerTarget(EntityReference.of(player));
                    neutral.startPersistentAngerTimer();
                }
            }
        }

        mob.setPos(hook.getX(), surfaceY(level, hook), hook.getZ());
        if (!level.noBlockCollision(mob, mob.getBoundingBox())) return false;
		int flightTicks = Mth.clamp(
				Mth.ceil(Math.hypot(player.getX() - hook.getX(), player.getZ() - hook.getZ()) / 0.55D),
				8,
				16
		);
		boolean originalNoGravity = mob.isNoGravity();
		mob.setNoAi(true);
        mob.setNoGravity(true);
        mob.setDeltaMovement(Vec3.ZERO);
        if (!level.addFreshEntity(mob)) return false;
        scheduleFlight(level, mob, player, mob.position(), flightTicks, 1, originalNoGravity);
        return true;
    }

    private static double surfaceY(ServerLevel level, FishingHook hook) {
        BlockPos pos = hook.blockPosition();
        var fluid = level.getFluidState(pos);
        if (fluid.isEmpty()) {
            pos = pos.below();
            fluid = level.getFluidState(pos);
        }
        return Math.max(hook.getY() + 0.1D, pos.getY() + fluid.getHeight(level, pos) + 0.1D);
    }

    private static boolean spawnProjectile(ServerLevel level, FishingHook hook, Player player, int choice) {
        Projectile projectile = switch (choice) {
            case 0 -> new Arrow(EntityTypes.ARROW, level);
            case 1 -> level.dimension() == Level.END
                    ? new WindCharge(EntityTypes.WIND_CHARGE, level)
                    : new SmallFireball(EntityTypes.SMALL_FIREBALL, level);
            default -> new LargeFireball(EntityTypes.FIREBALL, level);
        };
        double startY = surfaceY(level, hook) + 0.4D;
        projectile.setPos(hook.getX(), startY, hook.getZ());
        if (!level.noBlockCollision(projectile, projectile.getBoundingBox())) return false;
        Vec3 direction = new Vec3(
                player.getX() - hook.getX(),
                player.getY() + player.getBbHeight() * 0.55D - startY,
                player.getZ() - hook.getZ()
        );
        projectile.shoot(direction.x, direction.y, direction.z, 0.9F, 0.0F);
        return level.addFreshEntity(projectile);
    }

    private static void scheduleFlight(ServerLevel level, Mob mob, Player player, Vec3 start,
                                       int flightTicks, int tick, boolean originalNoGravity) {
        level.getServer().schedule(new TickTask(level.getServer().getTickCount() + 1, () -> {
            if (!mob.isAlive()) return;
            if (!player.isAlive() || player.level() != level) {
                finishFlight(mob, originalNoGravity);
                return;
            }
            double progress = tick / (double) flightTicks;
            Vec3 target = new Vec3(player.getX(),
                    player.getY() + (player.getBbHeight() - mob.getBbHeight()) * 0.5D,
                    player.getZ());
            double arch = Math.max(1.2D, Math.hypot(target.x - start.x, target.z - start.z) * 0.2D);
            Vec3 next = start.lerp(target, progress)
                    .add(0.0D, 4.0D * arch * progress * (1.0D - progress), 0.0D);
            mob.move(MoverType.SELF, next.subtract(mob.position()));
            mob.setDeltaMovement(Vec3.ZERO);
            if (mob.getBoundingBox().inflate(0.1D).intersects(player.getBoundingBox())) {
                Vec3 impact = player.position().subtract(start).multiply(1.0D, 0.0D, 1.0D).normalize();
                player.push(impact.x * 0.6D, 0.2D, impact.z * 0.6D);
                finishFlight(mob, originalNoGravity);
            } else if (tick < flightTicks) {
                scheduleFlight(level, mob, player, start, flightTicks, tick + 1, originalNoGravity);
            } else {
                finishFlight(mob, originalNoGravity);
            }
        }));
    }

    private static void finishFlight(Mob mob, boolean originalNoGravity) {
        mob.setNoAi(false);
        mob.setNoGravity(originalNoGravity);
    }

    private static EntityType<? extends Mob> netherType(int choice) {
        return switch (choice) {
            case 0 -> EntityTypes.BLAZE;
            case 1 -> EntityTypes.WITHER_SKELETON;
            case 2 -> EntityTypes.SKELETON;
            case 3 -> EntityTypes.GHAST;
            case 4 -> EntityTypes.HOGLIN;
            case 5 -> EntityTypes.MAGMA_CUBE;
            case 6 -> EntityTypes.PIGLIN;
            case 7 -> EntityTypes.PIGLIN_BRUTE;
            case 8 -> EntityTypes.ZOMBIFIED_PIGLIN;
            case 9 -> EntityTypes.ENDERMAN;
            default -> throw new IllegalArgumentException("Not a Nether jump-scare choice: " + choice);
        };
    }

    private static EntityType<? extends Mob> endType(int choice) {
        return switch (choice) {
            case 0 -> EntityTypes.ENDERMITE;
            case 1 -> EntityTypes.ENDERMAN;
            case 2 -> EntityTypes.SHULKER;
            default -> throw new IllegalArgumentException("Not an End jump-scare choice: " + choice);
        };
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
}
