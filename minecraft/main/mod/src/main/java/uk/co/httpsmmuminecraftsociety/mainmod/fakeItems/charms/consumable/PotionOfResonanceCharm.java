package uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.consumable;

import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.FakeItems;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.def.Charm;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.def.ConsumableCallbacksCharm;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.fakeItemDefs.FakeItem;
import uk.co.httpsmmuminecraftsociety.mainmod.utils.TeleportPotionUtils;

import java.util.List;

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
            player.sendSystemMessage(Component.literal("Hold the item to resonate with in your offhand."));
            return false;
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
        Holder<MobEffect> sharedEffect = RESONANCE_EFFECTS.get(level.getRandom().nextInt(RESONANCE_EFFECTS.size()));
        int sharedEffectDurationTicks = (10 + level.getRandom().nextInt(21)) * 20;
        player.addEffect(new MobEffectInstance(sharedEffect, sharedEffectDurationTicks, RESONANCE_EFFECT_AMPLIFIER));
        target.addEffect(new MobEffectInstance(sharedEffect, sharedEffectDurationTicks, RESONANCE_EFFECT_AMPLIFIER));

        player.sendSystemMessage(Component.literal("You resonated with " + target.getName().getString() + "."));
        target.sendSystemMessage(Component.literal(player.getName().getString() + " resonated with you."));
        stack.consume(1, player);
        return true;
    }

    private static boolean holdsMatchingItem(ServerPlayer player, ItemStack key) {
        return matchesItemType(player.getMainHandItem(), key) || matchesItemType(player.getOffhandItem(), key);
    }

    private static boolean matchesItemType(ItemStack candidate, ItemStack key) {
        FakeItem candidateFakeItem = FakeItems.getFakeItemFromStack(candidate);
        FakeItem keyFakeItem = FakeItems.getFakeItemFromStack(key);
        if (candidateFakeItem != null || keyFakeItem != null) {
            return candidateFakeItem != null && keyFakeItem != null && candidateFakeItem.id().equals(keyFakeItem.id());
        }
        return candidate.is(key.getItem());
    }
}
