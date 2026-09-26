package uk.co.httpsmmuminecraftsociety.mainmod.playerpotions;

import com.mojang.authlib.GameProfile;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Team;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import uk.co.httpsmmuminecraftsociety.mainmod.grpc.PlayerStatsSync;
import uk.co.httpsmmuminecraftsociety.mainmod.mixin.playerpotions.PlayerPotionMannequinAccessor;
import uk.co.httpsmmuminecraftsociety.mainmod.toggles.FeatureToggles;

public final class PlayerDisguises {
    private static final String VIEW_TAG = "mainmod_player_disguise_view";
    private static final Map<UUID, State> ACTIVE = new HashMap<>();
    private static final Map<UUID, Saved> PENDING = new HashMap<>();
    private static final Map<PlayerTeam, Boolean> HIDDEN_TEAMS = new HashMap<>();

    private PlayerDisguises() {}

    public static void apply(LivingEntity target, String identity, int ticks) {
        if (!(target instanceof ServerPlayer player) || PlayerPotions.decode(identity) == null
                || ticks <= 0 || !FeatureToggles.isEnabled(FeatureToggles.CIRCUS)) return;
        State current = ACTIVE.get(player.getUUID());
        if (current != null && current.identity.equals(identity)) {
            current.remaining = Math.max(current.remaining, ticks);
            return;
        }
        clear(player);
        State state = new State(identity, ticks);
        ACTIVE.put(player.getUUID(), state);
        create(player, state);
        if (state.view != null) {
            broadcastEquipment(player, true);
            player.sendSystemMessage(Component.literal("You are now visible as "
                    + PlayerPotions.decode(identity).name()
                    + " to others. Only you can still see your true form."));
        }
    }

    public static void tick(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            State state = ACTIVE.get(player.getUUID());
            if (state == null && PENDING.containsKey(player.getUUID())
                    && FeatureToggles.revision() > 0) {
                joined(player);
                state = ACTIVE.get(player.getUUID());
            }
            if (state == null) continue;
            if (!FeatureToggles.isEnabled(FeatureToggles.CIRCUS) || --state.remaining <= 0) {
                clear(player);
                continue;
            }
            if (state.view == null || state.view.isRemoved() || state.view.level() != player.level()) {
                removeView(state);
                create(player, state);
            }
            Mannequin view = state.view;
            if (view == null) continue;
            syncTeam(player, state);
            syncDescription(player, state);
            if (!player.isInvisible()) player.setInvisible(true);
            view.noPhysics = true;
            view.setDeltaMovement(player.getDeltaMovement());
            view.snapTo(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
            view.setYBodyRot(player.yBodyRot);
            view.setYHeadRot(player.getYHeadRot());
            view.setPose(player.getPose());
            view.setShiftKeyDown(player.isShiftKeyDown());
            view.setSprinting(player.isSprinting());
            view.setSwimming(player.isSwimming());
            for (EquipmentSlot slot : EquipmentSlot.VALUES) {
                ItemStack equipment = player.getItemBySlot(slot);
                if (!ItemStack.matches(view.getItemBySlot(slot), equipment)) {
                    view.setItemSlot(slot, equipment.copy());
                }
            }
            if (player.isUsingItem()) {
                if (!view.isUsingItem() || view.getUsedItemHand() != player.getUsedItemHand()) {
                    view.startUsingItem(player.getUsedItemHand());
                }
            } else if (view.isUsingItem()) {
                view.stopUsingItem();
            }
        }
    }

    public static void swing(ServerPlayer player, InteractionHand hand, SwingAnimation animation) {
        State state = ACTIVE.get(player.getUUID());
        if (state != null && state.view != null) state.view.swing(hand, animation, false);
    }

    public static InteractionResult attack(Player attacker, Level level, InteractionHand hand,
                                           Entity target, EntityHitResult hit) {
        if (!(attacker instanceof ServerPlayer player) || !(target instanceof Mannequin view)
                || !isView(view)) return InteractionResult.PASS;
        ServerPlayer owner = ownerOf(view);
        if (owner != null && owner != player) player.attack(owner);
        else if (hitBehindView(player, view) instanceof EntityHitResult behind) {
            ServerPlayer behindOwner = behind.getEntity() instanceof Mannequin other
                    ? ownerOf(other) : null;
            player.attack(behindOwner == null ? behind.getEntity() : behindOwner);
        }
        return InteractionResult.SUCCESS_SERVER;
    }

    public static InteractionResult use(Player user, Level level, InteractionHand hand,
                                        Entity target, EntityHitResult hit) {
        if (!(user instanceof ServerPlayer player) || !(target instanceof Mannequin view)
                || !isView(view)) return InteractionResult.PASS;
        HitResult behind = hitBehindView(player, view);
        if (behind instanceof EntityHitResult entityHit) {
            player.interactOn(entityHit.getEntity(), hand,
                    entityHit.getLocation().subtract(entityHit.getEntity().position()));
        } else if (behind instanceof BlockHitResult block && behind.getType() == HitResult.Type.BLOCK
                && !block.isWorldBorderHit()
                && player.level().getWorldBorder().isWithinBounds(block.getBlockPos())
                && player.mayInteract(player.level(), block.getBlockPos())) {
            player.gameMode.useItemOn(player, player.level(), player.getItemInHand(hand), hand, block);
        } else {
            if (!player.isUsingItem()) {
                player.gameMode.useItem(player, player.level(), player.getItemInHand(hand), hand);
            }
        }
        return InteractionResult.SUCCESS_SERVER;
    }

    private static ServerPlayer ownerOf(Mannequin view) {
        for (Map.Entry<UUID, State> entry : ACTIVE.entrySet()) {
            if (entry.getValue().view == view && view.level().getServer() != null) {
                return view.level().getServer().getPlayerList().getPlayer(entry.getKey());
            }
        }
        return null;
    }

    private static HitResult hitBehindView(ServerPlayer player, Mannequin view) {
        double blockRange = player.blockInteractionRange();
        HitResult block = player.pick(blockRange, 1.0F, false);
        Vec3 eye = player.getEyePosition();
        double entityRange = Math.min(player.entityInteractionRange(),
                block.getType() == HitResult.Type.BLOCK
                        ? eye.distanceTo(block.getLocation()) : player.entityInteractionRange());
        Vec3 end = eye.add(player.getLookAngle().scale(entityRange));
        EntityHitResult entity = ProjectileUtil.getEntityHitResult(player, eye, end,
                player.getBoundingBox().expandTowards(end.subtract(eye)).inflate(1.0),
                candidate -> candidate != view && candidate.isPickable() && !candidate.isSpectator(),
                entityRange * entityRange);
        return entity == null ? block : entity;
    }

    public static boolean isOwnerView(Entity entity, ServerPlayer observer) {
        State state = ACTIVE.get(observer.getUUID());
        return state != null && state.view == entity;
    }

    public static boolean active(ServerPlayer player) {
        return ACTIVE.containsKey(player.getUUID());
    }

    public static boolean anyActive() {
        return !ACTIVE.isEmpty();
    }

    public static void loaded(Entity entity, ServerLevel level) {
        if (!entity.entityTags().contains(VIEW_TAG)) return;
        for (State state : ACTIVE.values()) if (state.view == entity) return;
        if (entity.getTeam() != null) {
            PlayerTeam team = entity.getTeam();
            level.getScoreboard().removePlayerFromTeam(entity.getScoreboardName());
            if (team.getName().startsWith("mpv") && team.getPlayers().isEmpty()) {
                level.getScoreboard().removePlayerTeam(team);
            }
        }
        entity.discard();
    }

    public static boolean isView(Entity entity) {
        return entity.entityTags().contains(VIEW_TAG);
    }

    public static void read(ServerPlayer player, ValueInput input) {
        String identity = input.read("mainmod_player_disguise", Codec.STRING).orElse(null);
        int remaining = input.read("mainmod_player_disguise_ticks", Codec.INT).orElse(0);
        if (PlayerPotions.decode(identity) != null && remaining > 0 && remaining <= PlayerPotions.EXTENDED_TICKS) {
            PENDING.put(player.getUUID(), new Saved(identity, remaining));
        }
    }

    public static void write(ServerPlayer player, ValueOutput output) {
        State state = ACTIVE.get(player.getUUID());
        Saved saved = state == null ? PENDING.get(player.getUUID())
                : new Saved(state.identity, state.remaining);
        if (saved != null && saved.remaining > 0
                && (FeatureToggles.revision() == 0 || FeatureToggles.isEnabled(FeatureToggles.CIRCUS))) {
            output.store("mainmod_player_disguise", Codec.STRING, saved.identity);
            output.store("mainmod_player_disguise_ticks", Codec.INT, saved.remaining);
        }
    }

    public static void joined(ServerPlayer player) {
        State old = ACTIVE.remove(player.getUUID());
        if (old != null) {
            removeView(old);
            releaseTeam(old);
        }
        Saved saved = PENDING.get(player.getUUID());
        if (saved != null && FeatureToggles.revision() == 0) {
            player.setInvisible(player.hasEffect(MobEffects.INVISIBILITY));
            return;
        }
        PENDING.remove(player.getUUID());
        if (saved != null && FeatureToggles.isEnabled(FeatureToggles.CIRCUS)) {
            State state = new State(saved.identity, saved.remaining);
            ACTIVE.put(player.getUUID(), state);
            create(player, state);
            if (state.view != null) broadcastEquipment(player, true);
        } else if (saved != null) {
            player.setInvisible(player.hasEffect(MobEffects.INVISIBILITY));
        }
    }

    public static void disconnected(ServerPlayer player) {
        State state = ACTIVE.remove(player.getUUID());
        if (state != null) {
            PENDING.put(player.getUUID(), new Saved(state.identity, state.remaining));
            removeView(state);
            releaseTeam(state);
        }
    }

    public static void stopping() {
        for (State state : ACTIVE.values()) removeView(state);
        for (State state : ACTIVE.values()) releaseTeam(state);
        ACTIVE.clear();
        HIDDEN_TEAMS.forEach(PlayerTeam::setSeeFriendlyInvisibles);
        HIDDEN_TEAMS.clear();
    }

    public static void respawned(ServerPlayer oldPlayer, ServerPlayer player, boolean alive) {
        State state = ACTIVE.get(player.getUUID());
        if (state == null) return;
        removeView(state);
        if (!alive || !FeatureToggles.isEnabled(FeatureToggles.CIRCUS)) clear(player);
        else {
            create(player, state);
            if (state.view != null) broadcastEquipment(player, true);
        }
    }

    public static void died(LivingEntity entity, net.minecraft.world.damagesource.DamageSource source) {
        if (entity instanceof ServerPlayer player) clear(player);
    }

    private static void create(ServerPlayer player, State state) {
        GameProfile profile = PlayerPotions.decode(state.identity);
        if (profile == null) { clear(player); return; }
        Mannequin view = new Mannequin(EntityTypes.MANNEQUIN, player.level());
        view.setComponent(DataComponents.PROFILE, ResolvableProfile.createResolved(profile));
        view.setCustomName(Component.literal(player.getGameProfile().name()));
        view.setCustomNameVisible(true);
        ((PlayerPotionMannequinAccessor) view).mainmod$setDescription(
                Component.literal(PlayerStatsSync.belowNameText(player.getUUID())));
        view.addTag(VIEW_TAG);
        view.setNoGravity(true);
        view.setPermanentlyInvulnerable(true);
        view.setSilent(true);
        view.noPhysics = true;
        view.snapTo(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
        for (EquipmentSlot slot : EquipmentSlot.VALUES) view.setItemSlot(slot, player.getItemBySlot(slot).copy());
        state.view = view;
        if (player.level().addFreshEntity(view)) {
            syncTeam(player, state);
            player.setInvisible(true);
        } else {
            state.view = null;
            clear(player);
        }
    }

    private static void clear(ServerPlayer player) {
        State state = ACTIVE.remove(player.getUUID());
        PENDING.remove(player.getUUID());
        if (state != null) {
            removeView(state);
            releaseTeam(state);
            player.setInvisible(player.hasEffect(MobEffects.INVISIBILITY));
            broadcastEquipment(player, false);
        }
    }

    private static void removeView(State state) {
        if (state.view != null) {
            PlayerTeam team = state.view.getTeam();
            if (team != null) state.view.level().getScoreboard()
                    .removePlayerFromTeam(state.view.getScoreboardName());
            state.view.remove(Entity.RemovalReason.DISCARDED);
            state.view = null;
        }
    }

    private static void syncTeam(ServerPlayer player, State state) {
        PlayerTeam team = player.getTeam();
        if (state.team != team) {
            releaseTeam(state);
            state.team = team;
            if (team != null) HIDDEN_TEAMS.putIfAbsent(team, team.canSeeFriendlyInvisibles());
        }
        if (team != null) {
            if (team.canSeeFriendlyInvisibles()) team.setSeeFriendlyInvisibles(false);
        }
        if (state.view == null) return;
        if (state.viewTeam == null) {
            String name = "mpv" + player.getUUID().toString().replace("-", "").substring(0, 13);
            state.viewTeam = player.level().getScoreboard().getPlayerTeam(name);
            if (state.viewTeam == null) state.viewTeam = player.level().getScoreboard().addPlayerTeam(name);
            state.viewTeam.setCollisionRule(Team.CollisionRule.NEVER);
        }
        if (state.view.getTeam() != state.viewTeam) {
            player.level().getScoreboard().addPlayerToTeam(state.view.getScoreboardName(), state.viewTeam);
        }
        Component prefix = team == null ? Component.empty() : team.getPlayerPrefix();
        Component suffix = team == null ? Component.empty() : team.getPlayerSuffix();
        if (!state.viewTeam.getPlayerPrefix().equals(prefix)) state.viewTeam.setPlayerPrefix(prefix);
        if (!state.viewTeam.getPlayerSuffix().equals(suffix)) state.viewTeam.setPlayerSuffix(suffix);
        if (state.viewTeam.getNameTagVisibility() != Team.Visibility.ALWAYS) {
            state.viewTeam.setNameTagVisibility(Team.Visibility.ALWAYS);
        }
    }

    private static void releaseTeam(State state) {
        PlayerTeam team = state.team;
        state.team = null;
        if (state.viewTeam != null) {
            state.viewTeam.getScoreboard().removePlayerTeam(state.viewTeam);
            state.viewTeam = null;
        }
        if (team != null && ACTIVE.values().stream().noneMatch(other -> other.team == team)) {
            Boolean original = HIDDEN_TEAMS.remove(team);
            if (original != null) team.setSeeFriendlyInvisibles(original);
        }
    }

    private static void syncDescription(ServerPlayer player, State state) {
        String text = PlayerStatsSync.belowNameText(player.getUUID());
        if (!text.equals(state.description)) {
            state.description = text;
            ((PlayerPotionMannequinAccessor) state.view).mainmod$setDescription(Component.literal(text));
        }
    }

    private static void broadcastEquipment(ServerPlayer player, boolean hidden) {
        if (!(player.level() instanceof ServerLevel level)) return;
        var slots = new java.util.ArrayList<Pair<EquipmentSlot, ItemStack>>();
        for (EquipmentSlot slot : EquipmentSlot.VALUES) {
            slots.add(Pair.of(slot, hidden ? ItemStack.EMPTY : player.getItemBySlot(slot).copy()));
        }
        level.getChunkSource().sendToTrackingPlayers(player,
                new ClientboundSetEquipmentPacket(player.getId(), slots));
    }

    private static final class State {
        final String identity;
        int remaining;
        Mannequin view;
        PlayerTeam team;
        PlayerTeam viewTeam;
        String description;

        State(String identity, int remaining) {
            this.identity = identity;
            this.remaining = remaining;
        }
    }

    private record Saved(String identity, int remaining) {}

    public static void milk(LivingEntity entity) {
        if (entity instanceof ServerPlayer player) clear(player);
    }
}
