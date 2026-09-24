package dev.freddy.killshift;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateAttributesPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.Team;

final class ShapeView {
    private static final String VIEW_TEAM = "killshift_views";

    private ShapeView() {
    }

    static void create(ServerPlayer player, ShapeState state, Mob source) {
        Mob view = state.form.type().create(player.level(), EntitySpawnReason.COMMAND);
        if (view == null) {
            return;
        }

        if (source != null) {
            view.restoreFrom(source);
            view.setUUID(UUID.randomUUID());
        }
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
    }

    static void tick(ServerPlayer player, ShapeState state) {
        Mob view = state.view;
        if (view == null || view.isRemoved() || view.level() != player.level()) {
            remove(state);
            create(player, state, null);
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

        PositionMoveRotation movement = new PositionMoveRotation(
                view.position(), view.getDeltaMovement(), view.getYRot(), view.getXRot());
        var packet = ClientboundTeleportEntityPacket.teleport(view.getId(), movement, Set.of(), player.onGround());
        for (ServerPlayer observer : PlayerLookup.tracking(view)) {
            observer.connection.send(packet);
        }
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
            Mob view = state.view;
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
        state.view.setSilent(false);
        state.view.playAmbientSound();
        state.view.setSilent(true);
    }

    private static void prepare(Mob view, ServerPlayer player) {
        view.deathTime = 0;
        view.setHealth(view.getMaxHealth());
        view.setNoAi(true);
        view.setNoGravity(true);
        view.setPermanentlyInvulnerable(true);
        view.setCanPickUpLoot(false);
        view.setPersistenceRequired();
        view.setSilent(true);
        view.noPhysics = true;
        view.snapTo(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
    }

    private static void sendSelfScale(ServerPlayer player, Mob view) {
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
