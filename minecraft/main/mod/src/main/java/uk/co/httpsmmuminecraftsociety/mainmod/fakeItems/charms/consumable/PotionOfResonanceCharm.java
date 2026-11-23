package uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.consumable;

import net.minecraft.core.Holder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.def.Charm;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.def.ConsumableCallbacksCharm;
import uk.co.httpsmmuminecraftsociety.mainmod.hopper.HopperFilter;
import uk.co.httpsmmuminecraftsociety.mainmod.utils.TeleportPotionUtils;
import uk.co.httpsmmuminecraftsociety.mainmod.bigboss.BigBossManager;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.FakeItems;
import uk.co.httpsmmuminecraftsociety.mainmod.grpc.GameplayGrpcService;
import uk.co.httpsmmuminecraftsociety.mainmod.mixin.respawn.RespawnPositionFinder;
import uk.co.httpsmmuminecraftsociety.mainmod.toggles.FeatureToggles;

import java.util.List;
import java.util.concurrent.TimeUnit;

public class PotionOfResonanceCharm implements Charm, ConsumableCallbacksCharm
{
    private static final int RESONANCE_EFFECT_AMPLIFIER = 2;
    private static final List<Holder<MobEffect>> RESONANCE_EFFECTS = List.of(
            MobEffects.SPEED,
            MobEffects.JUMP_BOOST,
            MobEffects.HASTE,
            MobEffects.NIGHT_VISION,
            MobEffects.INVISIBILITY,
            MobEffects.SLOW_FALLING
    );

    @Override
    public void onConsumeTick(ItemStack stack, ServerPlayer player, ServerLevel level, int elapsedTicks, int charmLevel) {}

    @Override
    public boolean onConsumeFinished(ItemStack stack, ServerPlayer player, ServerLevel level, int elapsedTicks, int charmLevel)
    {
        String teleportPossibleTest = TeleportPotionUtils.checkTeleportable(player, level, 20, 16);
        if (!teleportPossibleTest.isEmpty()) {
            player.sendSystemMessage(Component.literal(teleportPossibleTest));
            return false;
        }

        ItemStack key = player.getOffhandItem();
        if (key.isEmpty()) {
            player.sendSystemMessage(Component.literal("Hold an item to resonate with in your offhand."));
            return false;
        }

        if (FeatureToggles.isEnabled(FeatureToggles.VOID)
                && FakeItems.isSpecificFakeItem(key, "charm-potion-resonance")) {
            var destinationInfo = getBigBossTarget(player);
            if (destinationInfo == null) return false;
            if (destinationInfo.getHolderUsername().isBlank()) {
                player.sendSystemMessage(BigBossManager.linked("There is no Big Boss to find right now."));
                return false;
            }
            ServerPlayer target = level.getServer().getPlayerList().getPlayerByName(destinationInfo.getHolderUsername());
            if (target == player) {
                player.sendSystemMessage(BigBossManager.linked("You are the Big Boss."));
                return false;
            }
            Identifier dimensionId = Identifier.tryParse(destinationInfo.getDimension());
            ServerLevel destination = target != null ? target.level() : dimensionId == null ? null
                    : level.getServer().getLevel(ResourceKey.create(Registries.DIMENSION, dimensionId));
            if (destination == null || (target == null && !destinationInfo.getAvailable())) {
                player.sendSystemMessage(BigBossManager.linked("The Big Boss has no known location yet."));
                return false;
            }
            BlockPos origin = target != null ? target.blockPosition()
                    : new BlockPos(destinationInfo.getX(), destinationInfo.getY(), destinationInfo.getZ());
            BlockPos landing = RespawnPositionFinder.find(player, destination, origin);
            if (landing == null) {
                player.sendSystemMessage(BigBossManager.linked("No safe position near the Big Boss was found."));
                return false;
            }
            if (!TeleportPotionUtils.teleportWithCompanions("resonance", player, destination,
                    landing.getX() + 0.5, landing.getY() + 0.1, landing.getZ() + 0.5,
                    player.getYRot(), player.getXRot())) return false;
            player.fallDistance = 0;
            shareEffect(player, target, level);
            player.sendSystemMessage(BigBossManager.linked("You resonated near the Big Boss."));
            if (target != null) target.sendSystemMessage(BigBossManager.linked(player.getName().getString() + " resonated nearby."));
            stack.consume(1, player);
            key.consume(1, player);
            return true;
        }

        List<ServerPlayer> candidates = level.getServer().getPlayerList().getPlayers().stream()
                .filter(candidate -> candidate != player)
                .filter(candidate -> holdsMatchingItem(candidate, key))
                .toList();

        if (candidates.isEmpty()) {
            player.sendSystemMessage(Component.literal("No other player is resonating with that item."));
            return false;
        }

        ServerPlayer target = candidates.get(level.getRandom().nextInt(candidates.size()));

        TeleportPotionUtils.teleportWithCompanions(
                "resonance",
                player,
                (ServerLevel) target.level(),
                target.getX(),
                target.getY(),
                target.getZ(),
                player.getYRot(),
                player.getXRot()
        );

        player.fallDistance = 0.0F;
        shareEffect(player, target, level);

        player.sendSystemMessage(Component.literal("You resonated with " + target.getName().getString() + "."));
        target.sendSystemMessage(Component.literal(player.getName().getString() + " resonated with you."));
        stack.consume(1, player);
        return true;
    }

    private static uk.co.httpsmmuminecraftsociety.mainmod.grpc.BigBossTargetResponse getBigBossTarget(ServerPlayer player) {
        try {
            return GameplayGrpcService.getBigBossTarget().get(6, TimeUnit.SECONDS);
        } catch (Exception error) {
            player.sendSystemMessage(BigBossManager.linked("The Big Boss cannot be located right now. Try again soon."));
            return null;
        }
    }

    private static boolean holdsMatchingItem(ServerPlayer player, ItemStack key) {
        return matchesItemType(player.getMainHandItem(), key) || matchesItemType(player.getOffhandItem(), key);
    }

    private static void shareEffect(ServerPlayer player, ServerPlayer target, ServerLevel level) {
        Holder<MobEffect> effect = RESONANCE_EFFECTS.get(level.getRandom().nextInt(RESONANCE_EFFECTS.size()));
        int duration = (10 + level.getRandom().nextInt(21)) * 20;
        player.addEffect(new MobEffectInstance(effect, duration, RESONANCE_EFFECT_AMPLIFIER));
        if (target != null) target.addEffect(new MobEffectInstance(effect, duration, RESONANCE_EFFECT_AMPLIFIER));
    }

    public static boolean matchesItemType(ItemStack candidate, ItemStack key) {
        return !candidate.isEmpty() && HopperFilter.baseItemId(candidate).equals(HopperFilter.baseItemId(key));
    }
}
