package dev.freddy.killshift;

import com.mojang.datafixers.util.Pair;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

final class ShapeView {
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
        state.view = view;
        player.level().addFreshEntity(view);
        spawnSelfPuppet(player, view);
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
        moveSelfPuppet(player, view);
    }

    static void remove(ShapeState state) {
        if (state.view != null) {
            state.view.remove(Entity.RemovalReason.DISCARDED);
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

    private static void spawnSelfPuppet(ServerPlayer player, Mob view) {
        if (player.hasDisconnected()) {
            return;
        }

        Vec3 pos = selfPosition(player, view);
        player.connection.send(new ClientboundRemoveEntitiesPacket(view.getId()));
        player.connection.send(new ClientboundAddEntityPacket(
                view.getId(),
                view.getUUID(),
                pos.x,
                pos.y,
                pos.z,
                player.getXRot(),
                player.getYRot(),
                view.getType(),
                0,
                player.getDeltaMovement(),
                player.getYHeadRot()
        ));

        var metadata = view.getEntityData().getNonDefaultValues();
        if (metadata != null) {
            player.connection.send(new ClientboundSetEntityDataPacket(view.getId(), metadata));
        }

        List<Pair<EquipmentSlot, ItemStack>> equipment = new ArrayList<>();
        for (EquipmentSlot slot : EquipmentSlot.VALUES) {
            ItemStack stack = view.getItemBySlot(slot);
            if (!stack.isEmpty()) {
                equipment.add(Pair.of(slot, stack.copy()));
            }
        }
        if (!equipment.isEmpty()) {
            player.connection.send(new ClientboundSetEquipmentPacket(view.getId(), equipment));
        }
    }

    private static void moveSelfPuppet(ServerPlayer player, Mob view) {
        if (player.hasDisconnected()) {
            return;
        }
        PositionMoveRotation movement = new PositionMoveRotation(
                selfPosition(player, view),
                player.getDeltaMovement(),
                player.getYRot(),
                player.getXRot()
        );
        player.connection.send(ClientboundTeleportEntityPacket.teleport(view.getId(), movement, Set.of(), player.onGround()));
    }

    private static Vec3 selfPosition(ServerPlayer player, Mob view) {
        Vec3 look = player.getLookAngle();
        Vec3 horizontal = new Vec3(look.x, 0.0, look.z);
        if (horizontal.lengthSqr() < 0.001) {
            horizontal = new Vec3(0.0, 0.0, 1.0);
        } else {
            horizontal = horizontal.normalize();
        }
        double offset = Math.max(0.9, view.getBbWidth() * 0.55 + 0.35);
        return player.position().subtract(horizontal.scale(offset));
    }
}
