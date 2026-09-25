package dev.freddy.killshift;

import java.util.Comparator;
import java.util.Map;
import dev.freddy.killshift.mixin.GuardianAttackAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Guardian;
import net.minecraft.world.Difficulty;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.entity.projectile.LlamaSpit;
import net.minecraft.world.entity.projectile.EvokerFangs;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.hurtingprojectile.DragonFireball;
import net.minecraft.world.entity.projectile.hurtingprojectile.LargeFireball;
import net.minecraft.world.entity.projectile.hurtingprojectile.SmallFireball;
import net.minecraft.world.entity.projectile.hurtingprojectile.WitherSkull;
import net.minecraft.world.entity.projectile.hurtingprojectile.windcharge.WindCharge;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownSplashPotion;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.InfestedBlock;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

final class MobAbilities {
    private static final Map<EntityType<?>, Item> ABILITY_ITEMS = Map.ofEntries(
            Map.entry(EntityTypes.BEE, Items.HONEYCOMB),
            Map.entry(EntityTypes.BLAZE, Items.FIRE_CHARGE),
            Map.entry(EntityTypes.BREEZE, Items.WIND_CHARGE),
            Map.entry(EntityTypes.CREEPER, Items.TNT),
            Map.entry(EntityTypes.ENDER_DRAGON, Items.DRAGON_BREATH),
            Map.entry(EntityTypes.ENDERMAN, Items.ENDER_PEARL),
            Map.entry(EntityTypes.EVOKER, Items.TOTEM_OF_UNDYING),
            Map.entry(EntityTypes.GHAST, Items.GHAST_TEAR),
            Map.entry(EntityTypes.GLOW_SQUID, Items.GLOW_INK_SAC),
            Map.entry(EntityTypes.GUARDIAN, Items.PRISMARINE_SHARD),
            Map.entry(EntityTypes.ELDER_GUARDIAN, Items.PRISMARINE_CRYSTALS),
            Map.entry(EntityTypes.LLAMA, Items.SNOWBALL),
            Map.entry(EntityTypes.MOOSHROOM, Items.BOWL),
            Map.entry(EntityTypes.SHULKER, Items.CHORUS_FRUIT),
            Map.entry(EntityTypes.SILVERFISH, Items.INFESTED_STONE_BRICKS),
            Map.entry(EntityTypes.SNIFFER, Items.TORCHFLOWER_SEEDS),
            Map.entry(EntityTypes.SQUID, Items.INK_SAC),
            Map.entry(EntityTypes.TRADER_LLAMA, Items.SNOWBALL),
            Map.entry(EntityTypes.WARDEN, Items.ECHO_SHARD),
            Map.entry(EntityTypes.WITCH, Items.GLASS_BOTTLE),
            Map.entry(EntityTypes.WITHER, Items.WITHER_SKELETON_SKULL)
    );

    private MobAbilities() {
    }

    static Item abilityItem(EntityType<?> type) {
        return ABILITY_ITEMS.get(type);
    }

    static void tick(ServerPlayer player, ShapeState state) {
        if (state.guardianChargeTicks <= 0) return;
        LivingEntity target = state.guardianTarget;
        if (!(state.view instanceof Guardian guardian) || target == null || !target.isAlive()
                || target.level() != player.level() || player.distanceToSqr(target) > 256.0
                || !player.hasLineOfSight(target)) {
            stopGuardianBeam(state);
            return;
        }
        if (--state.guardianChargeTicks == 0) {
            LivingEntity victim = ShapeManager.combatTarget(target);
            float magic = 1.0F + (player.level().getDifficulty() == Difficulty.HARD ? 2.0F : 0.0F)
                    + (state.form.type() == EntityTypes.ELDER_GUARDIAN ? 2.0F : 0.0F);
            float damage = magic + (float) guardian.getAttributeValue(
                    net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
            victim.hurtServer(player.level(), player.damageSources().indirectMagic(player, player), damage);
            stopGuardianBeam(state);
        }
    }

    private static void stopGuardianBeam(ShapeState state) {
        state.guardianChargeTicks = 0;
        state.guardianTarget = null;
        if (state.view instanceof Guardian guardian) {
            ((GuardianAttackAccessor) guardian).killshift$setActiveAttackTarget(0);
        }
    }

    static InteractionResult onUseItem(
            net.minecraft.world.entity.player.Player user,
            Level level,
            InteractionHand hand
    ) {
        if (!(user instanceof ServerPlayer player) || !(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.PASS;
        }

        ItemStack held = player.getItemInHand(hand);
        if (AbilitySlot.barrier(held)) return InteractionResult.FAIL;
        if (!AbilitySlot.trigger(held)) return InteractionResult.PASS;

        ShapeState state = ShapeManager.get(player);
        if (state == null || state.abilityCooldown > 0 || !activate(serverLevel, player, state)) {
            return InteractionResult.FAIL;
        }
        return InteractionResult.SUCCESS_SERVER.withoutItem();
    }

    static InteractionResult onUseBlock(
            net.minecraft.world.entity.player.Player user,
            Level level,
            InteractionHand hand,
            BlockHitResult hit
    ) {
        if (!(user instanceof ServerPlayer player) || !(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.PASS;
        }
        ItemStack held = player.getItemInHand(hand);
        if (AbilitySlot.barrier(held)) return InteractionResult.FAIL;
        if (!AbilitySlot.trigger(held)) return InteractionResult.PASS;
        ShapeState state = ShapeManager.get(player);
        if (state == null || state.abilityCooldown > 0) return InteractionResult.FAIL;
        EntityType<?> type = state.form.type();
        BlockPos pos = hit.getBlockPos();
        if (type == EntityTypes.SNIFFER && level.getBlockState(pos).is(Blocks.GRASS_BLOCK)) {
            giveAncientSeed(player);
            state.abilityCooldown = 200;
            return InteractionResult.SUCCESS_SERVER.withoutItem();
        }
        if (type == EntityTypes.BEE && level.getBlockState(pos).is(BlockTags.FLOWERS)
                && player.getHealth() < player.getMaxHealth()) {
            player.heal(4.0F);
            state.abilityCooldown = 20;
            return InteractionResult.SUCCESS_SERVER.withoutItem();
        }
        if (type == EntityTypes.SILVERFISH
                && level.mayInteract(player, pos)
                && InfestedBlock.isCompatibleHostBlock(level.getBlockState(pos))) {
            var ally = EntityTypes.SILVERFISH.create(level, EntitySpawnReason.MOB_SUMMONED);
            if (ally == null || !level.removeBlock(pos, false)) return InteractionResult.PASS;
            ally.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0, 0);
            ally.addTag("killshift_ally_" + player.getUUID());
            level.addFreshEntity(ally);
            state.abilityCooldown = 100;
            return InteractionResult.SUCCESS_SERVER.withoutItem();
        }
        return activate(serverLevel, player, state)
                ? InteractionResult.SUCCESS_SERVER.withoutItem() : InteractionResult.FAIL;
    }

    static InteractionResult onUseEntity(
            net.minecraft.world.entity.player.Player user,
            Level level,
            InteractionHand hand,
            Entity target,
            EntityHitResult hit
    ) {
        if (!(user instanceof ServerPlayer player) || !(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.PASS;
        }
        ItemStack held = player.getItemInHand(hand);
        if (AbilitySlot.barrier(held)) return InteractionResult.FAIL;
        if (!AbilitySlot.trigger(held)) return InteractionResult.PASS;
        ShapeState state = ShapeManager.get(player);
        if (state == null || state.abilityCooldown > 0) return InteractionResult.FAIL;
        return activate(serverLevel, player, state)
                ? InteractionResult.SUCCESS_SERVER.withoutItem() : InteractionResult.FAIL;
    }

    static void onDamage(
            LivingEntity victim,
            DamageSource source,
            float baseDamage,
            float damageTaken,
            boolean blocked
    ) {
        if (victim instanceof ServerPlayer hurtPlayer) {
            ShapeState hurtState = ShapeManager.get(hurtPlayer);
            if (hurtState != null && hurtState.form.type() == EntityTypes.BEE && damageTaken > 0.0F) {
                hurtState.angryTicks = 200;
            }
            if (hurtState != null && damageTaken > 0.0F
                    && (hurtState.form.type() == EntityTypes.SQUID
                    || hurtState.form.type() == EntityTypes.GLOW_SQUID)) {
                hurtState.squidFleeTicks = 100;
            }
        }

        if (!(source.getEntity() instanceof ServerPlayer attacker) || blocked || damageTaken <= 0.0F) {
            return;
        }
        ShapeState state = ShapeManager.get(attacker);
        if (state == null) {
            return;
        }

        EntityType<?> type = state.form.type();
        if (type == EntityTypes.CAVE_SPIDER || type == EntityTypes.BEE || type == EntityTypes.PUFFERFISH) {
            victim.addEffect(new MobEffectInstance(MobEffects.POISON, 100, 0), attacker);
        } else if (type == EntityTypes.GUARDIAN || type == EntityTypes.ELDER_GUARDIAN) {
            victim.addEffect(new MobEffectInstance(MobEffects.MINING_FATIGUE, 200, 0), attacker);
        } else if (type == EntityTypes.WITHER_SKELETON || type == EntityTypes.WITHER) {
            victim.addEffect(new MobEffectInstance(MobEffects.WITHER, 200, 0), attacker);
        }
    }

    private static boolean activate(ServerLevel level, ServerPlayer player, ShapeState state) {
        EntityType<?> type = state.form.type();
        Vec3 direction = player.getLookAngle().normalize();
        state.abilityCooldown = 20;

        if (type == EntityTypes.GHAST) {
            level.addFreshEntity(new LargeFireball(level, player, direction, 1));
        } else if (type == EntityTypes.BLAZE) {
            level.addFreshEntity(new SmallFireball(level, player, direction));
        } else if (type == EntityTypes.BREEZE) {
            WindCharge charge = new WindCharge(player, level, player.getX(), player.getEyeY(), player.getZ());
            charge.shoot(direction.x, direction.y, direction.z, 1.5F, 0.0F);
            level.addFreshEntity(charge);
        } else if (type == EntityTypes.WITHER) {
            level.addFreshEntity(new WitherSkull(level, player, direction));
        } else if (type == EntityTypes.ENDER_DRAGON) {
            level.addFreshEntity(new DragonFireball(level, player, direction));
        } else if (type == EntityTypes.ENDERMAN) {
            ItemStack pearl = new ItemStack(Items.ENDER_PEARL);
            Projectile.spawnProjectileFromRotation(ThrownEnderpearl::new, level, pearl, player, 0.0F, 1.5F, 1.0F);
        } else if (type == EntityTypes.GUARDIAN || type == EntityTypes.ELDER_GUARDIAN) {
            LivingEntity target = targetInSight(player, 15.0);
            if (target == null || !(state.view instanceof Guardian guardian)) {
                state.abilityCooldown = 0;
                return false;
            }
            state.guardianTarget = target;
            state.guardianChargeTicks = guardian.getAttackDuration();
            state.abilityCooldown = state.guardianChargeTicks + 20;
            ((GuardianAttackAccessor) guardian).killshift$setActiveAttackTarget(target.getId());
        } else if (type == EntityTypes.SHULKER) {
            if (!teleport(player)) {
                state.abilityCooldown = 0;
                return false;
            }
        } else if (type == EntityTypes.WITCH) {
            var potionType = switch (player.getRandom().nextInt(4)) {
                case 0 -> Potions.POISON;
                case 1 -> Potions.SLOWNESS;
                case 2 -> Potions.WEAKNESS;
                default -> Potions.HARMING;
            };
            ItemStack potion = PotionContents.createItemStack(Items.SPLASH_POTION, potionType);
            Projectile.spawnProjectileFromRotation(ThrownSplashPotion::new, level, potion, player, -20.0F, 0.75F, 8.0F);
        } else if (type == EntityTypes.EVOKER) {
            summonFangs(level, player, direction);
        } else if (type == EntityTypes.CREEPER) {
            float radius = state.view instanceof net.minecraft.world.entity.monster.Creeper creeper
                    && creeper.isPowered() ? 6.0F : 3.0F;
            AbilitySlot.clear(player);
            level.explode(player, player.getX(), player.getY(), player.getZ(), radius,
                    false, Level.ExplosionInteraction.MOB);
            if (player.isAlive()) player.kill(level);
        } else if (type == EntityTypes.WARDEN) {
            state.abilityCooldown = 80;
            sonicBoom(level, player);
        } else if (type == EntityTypes.LLAMA || type == EntityTypes.TRADER_LLAMA) {
            LlamaSpit spit = new LlamaSpit(EntityTypes.LLAMA_SPIT, level);
            spit.setOwner(player);
            spit.setPos(player.getX(), player.getEyeY() - 0.1, player.getZ());
            spit.shoot(direction.x, direction.y, direction.z, 1.5F, 4.0F);
            level.addFreshEntity(spit);
        } else if (type == EntityTypes.SQUID || type == EntityTypes.GLOW_SQUID) {
            level.sendParticles(ParticleTypes.SQUID_INK, player.getX(), player.getEyeY(), player.getZ(),
                    80, 1.2, 0.8, 1.2, 0.08);
        } else if (type == EntityTypes.SNIFFER) {
            if (!digSeed(player)) {
                state.abilityCooldown = 0;
                return false;
            }
            state.abilityCooldown = 200;
        } else if (type == EntityTypes.MOOSHROOM) {
            player.getFoodData().setFoodLevel(Math.min(20, player.getFoodData().getFoodLevel() + 6));
            player.getFoodData().setSaturation(Math.min(20.0F, player.getFoodData().getSaturationLevel() + 7.2F));
            player.getInventory().add(new ItemStack(Items.BOWL));
        } else {
            state.abilityCooldown = 0;
            return false;
        }

        if (state.view != null) {
            state.view.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, false);
        }
        return true;
    }

    private static boolean digSeed(ServerPlayer player) {
        if (!player.level().getBlockState(player.blockPosition().below()).is(Blocks.GRASS_BLOCK)) {
            return false;
        }
        giveAncientSeed(player);
        return true;
    }

    private static void giveAncientSeed(ServerPlayer player) {
        ItemStack seed = new ItemStack(player.getRandom().nextBoolean() ? Items.TORCHFLOWER_SEEDS : Items.PITCHER_POD);
        if (!player.getInventory().add(seed)) {
            player.spawnAtLocation(player.level(), seed);
        }
    }

    private static boolean teleport(ServerPlayer player) {
        for (int attempt = 0; attempt < 32; attempt++) {
            double x = player.getX() + (player.getRandom().nextDouble() - 0.5) * 32.0;
            double y = player.getY() + player.getRandom().nextInt(-8, 9);
            double z = player.getZ() + (player.getRandom().nextDouble() - 0.5) * 32.0;
            if (player.randomTeleport(x, y, z, true, blockState -> false)) {
                return true;
            }
        }
        return false;
    }

    private static void summonFangs(ServerLevel level, ServerPlayer player, Vec3 direction) {
        Vec3 forward = new Vec3(direction.x, 0.0, direction.z).normalize();
        if (forward.lengthSqr() < 0.01) forward = Vec3.directionFromRotation(0.0F, player.getYRot());
        for (int distance = 1; distance <= 8; distance++) {
            double x = player.getX() + forward.x * distance;
            double z = player.getZ() + forward.z * distance;
            BlockPos at = BlockPos.containing(x, player.getY(), z);
            for (int shift = 2; shift >= -2; shift--) {
                BlockPos floor = at.offset(0, shift - 1, 0);
                BlockPos space = floor.above();
                if (level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP)
                        && level.getBlockState(space).getCollisionShape(level, space).isEmpty()) {
                    level.addFreshEntity(new EvokerFangs(level, x, space.getY(), z,
                            (float) Math.atan2(forward.z, forward.x), distance, player));
                    break;
                }
            }
        }
    }

    private static void sonicBoom(ServerLevel level, ServerPlayer player) {
        LivingEntity target = targetInSight(player, 20.0);
        if (target == null) {
            return;
        }
        Vec3 source = player.getEyePosition();
        Vec3 delta = target.getEyePosition().subtract(source);
        Vec3 direction = delta.normalize();
        for (int step = 1; step < (int) delta.length() + 7; step++) {
            Vec3 particle = source.add(direction.scale(step));
            level.sendParticles(ParticleTypes.SONIC_BOOM, particle.x, particle.y, particle.z,
                    1, 0.0, 0.0, 0.0, 0.0);
        }
        level.playSound(null, player.blockPosition(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 3.0F, 1.0F);
        if (target.hurtServer(level, level.damageSources().sonicBoom(player), 10.0F)) {
            target.push(direction.x * 2.5, direction.y * 0.5, direction.z * 2.5);
        }
    }

    private static LivingEntity targetInSight(ServerPlayer player, double range) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle().normalize();
        AABB search = player.getBoundingBox().expandTowards(look.scale(range)).inflate(2.0);
        return player.level().getEntitiesOfClass(
                        LivingEntity.class,
                        search,
                        entity -> entity != player && entity.isAlive() && !(entity == ShapeManager.get(player).view)
                ).stream()
                .filter(entity -> {
                    Vec3 delta = entity.getEyePosition().subtract(eye);
                    double projection = delta.dot(look);
                    if (projection <= 0.0 || projection > range) {
                        return false;
                    }
                    double miss = delta.subtract(look.scale(projection)).length();
                    return miss <= Math.max(1.0, entity.getBbWidth()) && player.hasLineOfSight(entity);
                })
                .min(Comparator.comparingDouble(player::distanceToSqr))
                .orElse(null);
    }
}
