package dev.freddy.killshift;

import java.util.List;
import java.util.UUID;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundUpdateAttributesPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.Team;

final class ShapeView {
    private static final String VIEW_TEAM = "killshift_views";

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
        return output.buildResult();
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
        }
        if (view == null) {
            return;
        }

        view.noPhysics = true;
        view.setDeltaMovement(player.getDeltaMovement());
        view.snapTo(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
        view.setYHeadRot(player.getYHeadRot());
        view.setPose(player.getPose());
        view.setShiftKeyDown(player.isShiftKeyDown());
        view.setSprinting(player.isSprinting());
        view.setSwimming(player.isSwimming());

        // ponytail: Vanilla can overwrite this scale. Filter attribute packets if traffic grows.
        sendSelfScale(player, view);
    }

    static void onStartTracking(Entity entity, ServerPlayer player) {
        ShapeState state = ShapeManager.get(player);
        if (state != null && entity == state.view) {
            sendSelfScale(player, state.view);
        }
    }

    static void remove(ShapeState state) {
        if (state.view != null) {
            LivingEntity view = state.view;
            Scoreboard scoreboard = view.level().getScoreboard();
            if (scoreboard.getPlayersTeam(view.getScoreboardName()) == scoreboard.getPlayerTeam(VIEW_TEAM)) {
                scoreboard.removePlayerFromTeam(view.getScoreboardName());
            }
            view.remove(Entity.RemovalReason.DISCARDED);
            state.view = null;
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
        view.deathTime = 0;
        view.setHealth(view.getMaxHealth());
        if (view instanceof Mob mob) {
            mob.setNoAi(true);
            mob.setCanPickUpLoot(false);
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
        double factor = Math.min(0.5, player.getEyeHeight() * 0.75 / view.getBbHeight());
        self.setBaseValue(Math.max(0.0625, original.getValue() * factor));
        player.connection.send(new ClientboundUpdateAttributesPacket(view.getId(), List.of(self)));
    }
}
