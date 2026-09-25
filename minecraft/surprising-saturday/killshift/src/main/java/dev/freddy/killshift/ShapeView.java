package dev.freddy.killshift;

import java.util.List;
import java.util.UUID;
import dev.freddy.killshift.mixin.AgeableMobAgeAccessor;
import dev.freddy.killshift.mixin.TadpoleAgeAccessor;
import dev.freddy.killshift.mixin.MobFlagsAccessor;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundUpdateAttributesPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.monster.cubemob.AbstractCubeMob;
import net.minecraft.world.entity.animal.frog.Tadpole;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.Team;

public final class ShapeView {
    private static final String VIEW_TEAM = "killshift_views";
    public static final String VIEW_TAG = "killshift_view";
    static final double SELF_VIEW_SCALE = 0.5;

    private ShapeView() {
    }

    static CompoundTag snapshot(LivingEntity source) {
        LivingEntity original = source;
        if (source instanceof ServerPlayer player) {
            Mannequin mannequin = new Mannequin(EntityTypes.MANNEQUIN, player.level());
            mannequin.setComponent(DataComponents.PROFILE, player.getProfile());
            for (EquipmentSlot slot : EquipmentSlot.VALUES) {
                mannequin.setItemSlot(slot, player.getItemBySlot(slot).copy());
            }
            original = mannequin;
        }
        TagValueOutput output = TagValueOutput.createWithContext(
                ProblemReporter.DISCARDING, source.level().registryAccess());
        original.saveWithoutId(output);
        CompoundTag data = output.buildResult();
        if (source instanceof AbstractCubeMob cube) data.putInt("killshiftCubeSize", cube.getSize());
        if (source instanceof AgeableMob ageable) data.putInt("killshiftAge", ageable.getAge());
        return data;
    }

    static boolean create(ServerPlayer player, ShapeState state) {
        Entity entity = state.form.type() == EntityTypes.PLAYER
                ? new Mannequin(EntityTypes.MANNEQUIN, player.level())
                : state.form.type().create(player.level(), EntitySpawnReason.COMMAND);
        if (!(entity instanceof LivingEntity view)) return false;

        if (!state.viewData.isEmpty()) {
            view.load(TagValueInput.create(ProblemReporter.DISCARDING,
                    player.level().registryAccess(), state.viewData.copy()));
        }
        if (view instanceof AbstractCubeMob cube) {
            int size = state.viewData.getIntOr("killshiftCubeSize", cube.getSize());
            cube.setSize(Math.max(1, size), true);
        }
        if (view instanceof AgeableMob ageable) {
            ageable.setAge(state.viewData.getIntOr("killshiftAge", ageable.getAge()));
            ((AgeableMobAgeAccessor) ageable).killshift$setAgeLocked(true);
        }
        if (view instanceof Tadpole tadpole) {
            ((TadpoleAgeAccessor) tadpole).killshift$setAgeLocked(true);
        }
        view.setUUID(UUID.randomUUID());
        prepare(view, player);
        Scoreboard scoreboard = player.level().getScoreboard();
        PlayerTeam team = scoreboard.getPlayerTeam(VIEW_TEAM);
        if (team == null) {
            team = scoreboard.addPlayerTeam(VIEW_TEAM);
        }
        team.setCollisionRule(Team.CollisionRule.NEVER);
        scoreboard.addPlayerToTeam(view.getScoreboardName(), team);
        state.view = view;
        player.level().addFreshEntity(view);
        return true;
    }

    static void tick(ServerPlayer player, ShapeState state) {
        LivingEntity view = state.view;
        if (view == null || view.isRemoved() || view.level() != player.level()) {
            remove(state);
            create(player, state);
            view = state.view;
            if (view != null) ShapeManager.fitCameraToViewEyes(player, view);
        }
        if (view == null) {
            return;
        }

        view.noPhysics = true;
        view.setDeltaMovement(player.getDeltaMovement());
        view.snapTo(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
        view.yBodyRotO = view.yBodyRot;
        view.setYBodyRot(player.getYRot());
        view.setYHeadRot(player.getYHeadRot());
        if (state.form.type() == EntityTypes.PLAYER) view.setPose(player.getPose());
        view.setShiftKeyDown(player.isShiftKeyDown());
        view.setSprinting(player.isSprinting());
        view.setSwimming(player.isSwimming());
        if (view instanceof AgeableMob ageable) {
            int age = state.viewData.getIntOr("killshiftAge", state.viewData.getIntOr("Age", 0));
            if (ageable.getAge() != age) ageable.setAge(age);
        }

        // ponytail: Vanilla can overwrite this scale. Filter attribute packets if traffic grows.
        sendSelfScale(player, view);
        if (view instanceof EnderDragon dragon) {
            sendAnimatedDragon(dragon, player);
            for (ServerPlayer observer : PlayerLookup.tracking(dragon)) {
                if (observer != player) sendAnimatedDragon(dragon, observer);
            }
        }
    }

    static void onStartTracking(Entity entity, ServerPlayer player) {
        ShapeState state = ShapeManager.get(player);
        if (state != null && entity == state.view) {
            sendSelfScale(player, state.view);
        }
    }

    static void onEntityLoad(Entity entity, net.minecraft.server.level.ServerLevel level) {
        if (!(entity instanceof LivingEntity view)) return;
        PlayerTeam team = level.getScoreboard().getPlayerTeam(VIEW_TEAM);
        boolean legacy = team != null && level.getScoreboard().getPlayersTeam(view.getScoreboardName()) == team;
        if ((view.entityTags().contains(VIEW_TAG) || legacy) && !ShapeManager.isActiveView(view)) {
            view.discard();
        }
    }

    static void remove(ShapeState state) {
        if (state.view != null) {
            LivingEntity view = state.view;
            removeFromTeam(view);
            view.remove(Entity.RemovalReason.DISCARDED);
            state.view = null;
        }
    }

    static void release(ServerPlayer player, ShapeState state) {
        if (state == null || state.view == null || state.view.isRemoved()) return;
        LivingEntity view = state.view;
        state.view = null;
        removeFromTeam(view);
        view.removeTag(VIEW_TAG);
        view.noPhysics = false;
        view.setNoGravity(false);
        view.setPermanentlyInvulnerable(false);
        view.setSilent(false);
        if (view instanceof Tadpole tadpole) {
            ((TadpoleAgeAccessor) tadpole).killshift$setAgeLocked(
                    state.viewData.getBooleanOr("AgeLocked", false));
        }
        if (view instanceof AgeableMob ageable) {
            ((AgeableMobAgeAccessor) ageable).killshift$setAgeLocked(
                    state.viewData.getBooleanOr("AgeLocked", false));
        }
        view.setDeltaMovement(player.getDeltaMovement());
        view.removeAllEffects();
        for (MobEffectInstance effect : player.getActiveEffects()) {
            view.addEffect(new MobEffectInstance(effect));
        }
        AttributeInstance health = view.getAttribute(Attributes.MAX_HEALTH);
        if (health != null) {
            health.setBaseValue(health.getBaseValue() + player.getMaxHealth() - view.getMaxHealth());
        }
        view.setHealth(Math.min(player.getHealth(), view.getMaxHealth()));
        if (view instanceof Mob mob) mob.setNoAi(false);
        AttributeInstance scale = view.getAttribute(Attributes.SCALE);
        if (scale != null && !player.hasDisconnected()) {
            player.connection.send(new ClientboundUpdateAttributesPacket(view.getId(), List.of(scale)));
        }
    }

    private static void removeFromTeam(LivingEntity view) {
        Scoreboard scoreboard = view.level().getScoreboard();
        if (scoreboard.getPlayersTeam(view.getScoreboardName()) == scoreboard.getPlayerTeam(VIEW_TEAM)) {
            scoreboard.removePlayerFromTeam(view.getScoreboardName());
        }
    }

    static void playAmbientSound(ShapeState state) {
        if (state.view == null) {
            return;
        }
        if (state.view instanceof Mob mob) {
            mob.setSilent(false);
            mob.playAmbientSound();
            mob.setSilent(true);
        }
    }

    private static void prepare(LivingEntity view, ServerPlayer player) {
        view.addTag(VIEW_TAG);
        view.deathTime = 0;
        view.setHealth(view.getMaxHealth());
        if (view instanceof Mob mob) {
            mob.setNoAi(true);
            mob.setPersistenceRequired();
        }
        view.setNoGravity(true);
        view.setPermanentlyInvulnerable(true);
        view.setSilent(true);
        view.noPhysics = true;
        view.snapTo(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
    }

    private static void sendSelfScale(ServerPlayer player, LivingEntity view) {
        if (player.hasDisconnected()) {
            return;
        }
        AttributeInstance original = view.getAttribute(Attributes.SCALE);
        if (original == null) {
            return;
        }
        AttributeInstance self = new AttributeInstance(Attributes.SCALE, ignored -> {});
        self.replaceFrom(original);
        self.removeModifiers();
        self.setBaseValue(Math.max(0.0625, original.getValue() * SELF_VIEW_SCALE));
        player.connection.send(new ClientboundUpdateAttributesPacket(view.getId(), List.of(self)));
    }

    private static void sendAnimatedDragon(EnderDragon dragon, ServerPlayer observer) {
        if (observer.hasDisconnected()) return;
        EntityDataAccessor<Byte> flags = MobFlagsAccessor.killshift$mobFlags();
        byte animated = (byte) (dragon.getEntityData().get(flags) & ~1);
        observer.connection.send(new ClientboundSetEntityDataPacket(dragon.getId(),
                List.of(SynchedEntityData.DataValue.create(flags, animated))));
    }

}
