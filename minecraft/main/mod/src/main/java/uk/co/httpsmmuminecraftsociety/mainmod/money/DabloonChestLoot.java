package uk.co.httpsmmuminecraftsociety.mainmod.money;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import uk.co.httpsmmuminecraftsociety.mainmod.datagen.ModItemTagProvider;
import uk.co.httpsmmuminecraftsociety.mainmod.utils.PlayerCooldown;

import java.time.Duration;
import java.util.List;

public final class DabloonChestLoot {
    private static final double LOW_VALUE_CHANCE = 0.5D;
    private static final double HIGH_VALUE_CHANCE = 0.33D;
    private static final PlayerCooldown LOW_VALUE_COOLDOWN = new PlayerCooldown(Duration.ofMinutes(30));
    private static final PlayerCooldown HIGH_VALUE_COOLDOWN = new PlayerCooldown(Duration.ofHours(1));

    private DabloonChestLoot() {
    }

    public static void addDrops(Identifier tableId, LootContext context, List<ItemStack> drops) {
        if (tableId == null || !tableId.getPath().startsWith("chests/")) return;
        Entity entity = context.getOptionalParameter(LootContextParams.THIS_ENTITY);
        if (!(entity instanceof ServerPlayer player)) return;

        if (context.getRandom().nextDouble() < LOW_VALUE_CHANCE) {
            addIfReady(player, context, drops, 2, 6, LOW_VALUE_COOLDOWN);
        }
        if (tableId.getPath().contains("mansion")) return;

        boolean charmChest = drops.stream()
                .anyMatch(stack -> stack.is(ModItemTagProvider.CHARM_DROPPING_CHESTS_HAVE_ITEMS));
        if (charmChest && context.getRandom().nextDouble() < HIGH_VALUE_CHANCE) {
            addIfReady(player, context, drops, 3, 19, HIGH_VALUE_COOLDOWN);
        }
    }

    private static void addIfReady(
            ServerPlayer player,
            LootContext context,
            List<ItemStack> drops,
            int min,
            int max,
            PlayerCooldown cooldown
    ) {
        if (!cooldown.isReady(player.getUUID())) return;
        int amount = min + context.getRandom().nextInt(max - min + 1);
        List<ItemStack> coins = MoneyHelper.createCoinStacks(amount);
        if (!coins.isEmpty() && cooldown.tryStart(player.getUUID())) drops.addAll(coins);
    }
}
