package uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.equippable;

import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect;
import net.minecraft.world.item.consume_effects.PlaySoundConsumeEffect;
import net.minecraft.world.item.consume_effects.RemoveStatusEffectsConsumeEffect;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.FakeItems;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.fakeItemDefs.CharmItemFeature;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.fakeItemDefs.FakeItem;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.def.Charm;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.def.EquippedTickCallbackCharm;

import java.util.Set;

public final class AutoEatingCharm implements Charm, EquippedTickCallbackCharm {
    public static final int CHARM_ID = 62;

    private static final int[] INTERVAL_TICKS = {0, 600, 600, 600, 600, 400, 300, 200, 160, 120, 80, 40, 1};
    private static final int MAX_CONTAINER_DEPTH = 64;
    private static final Set<Item> RAW = Set.of(
            Items.BEETROOT, Items.CARROT, Items.POTATO, Items.BEEF, Items.PORKCHOP,
            Items.MUTTON, Items.RABBIT, Items.CHICKEN, Items.COD, Items.SALMON, Items.TROPICAL_FISH
    );
    private static final Set<Item> PREMIUM = Set.of(Items.GOLDEN_CARROT, Items.COOKED_BEEF, Items.COOKED_PORKCHOP);
    private static final Set<Item> STEWS = Set.of(Items.BEETROOT_SOUP, Items.MUSHROOM_STEW, Items.RABBIT_STEW);
    private static final Set<Item> FORBIDDEN = Set.of(
            Items.GOLDEN_APPLE, Items.ENCHANTED_GOLDEN_APPLE, Items.CHORUS_FRUIT,
            Items.SUSPICIOUS_STEW, Items.CAKE, Items.PUFFERFISH, Items.ROTTEN_FLESH,
            Items.SPIDER_EYE, Items.POISONOUS_POTATO
    );

    @Override
    public void equippedTick(ItemStack stack, ServerPlayer player, ServerLevel level, int charmLevel) {
        if (charmLevel < 1 || charmLevel >= INTERVAL_TICKS.length
                || player.tickCount % INTERVAL_TICKS[charmLevel] != 0
                || !player.canEat(false) || player.isUsingItem()) return;

        if (eatFrom(player.getInventory(), player, level, charmLevel, 0)) return;
        if (charmLevel >= 4) eatFrom(player.getEnderChestInventory(), player, level, charmLevel, 0);
    }

    private static boolean eatFrom(Container container, ServerPlayer player, ServerLevel level, int charmLevel, int depth) {
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack item = container.getItem(slot);
            if (canAutoEat(item, charmLevel)) {
                container.setItem(slot, item.finishUsingItem(level, player));
                return true;
            }
            // shortcut: Search stops after 64 nested containers. Raise this limit if valid items can nest deeper.
            if (charmLevel < 4 || depth >= MAX_CONTAINER_DEPTH || item.isEmpty()) continue;

            ItemContainerContents contents = item.get(DataComponents.CONTAINER);
            if (contents != null && contents.size() > 0) {
                SimpleContainer nested = new SimpleContainer(contents.size());
                contents.copyInto(nested.items);
                if (eatFrom(nested, player, level, charmLevel, depth + 1)) {
                    item.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(nested.items));
                    container.setItem(slot, item);
                    return true;
                }
            }

            BundleContents bundle = item.get(DataComponents.BUNDLE_CONTENTS);
            if (bundle != null && !bundle.isEmpty()) {
                SimpleContainer nested = new SimpleContainer(bundle.itemCopies().toArray(ItemStack[]::new));
                if (eatFrom(nested, player, level, charmLevel, depth + 1)) {
                    item.set(DataComponents.BUNDLE_CONTENTS,
                            bundle.copyWithContents(nested.items.stream().filter(stack -> !stack.isEmpty())));
                    container.setItem(slot, item);
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean canAutoEat(ItemStack stack, int charmLevel) {
        if (stack.isEmpty() || FORBIDDEN.contains(stack.getItem()) || stack.get(DataComponents.FOOD) == null) return false;
        FakeItem fakeItem = FakeItems.getFakeItemFromStack(stack);
        if (fakeItem != null && (fakeItem.magicEffect() != null || fakeItem.getFeature(CharmItemFeature.class) != null)) return false;

        Consumable consumable = stack.get(DataComponents.CONSUMABLE);
        if (consumable == null || consumable.onConsumeEffects().stream().anyMatch(effect ->
                effect instanceof ApplyStatusEffectsConsumeEffect applied
                        ? applied.effects().stream().anyMatch(status -> status.getEffect().value().getCategory() == MobEffectCategory.HARMFUL)
                        : !(effect instanceof RemoveStatusEffectsConsumeEffect || effect instanceof PlaySoundConsumeEffect))) return false;

        Item item = stack.getItem();
        if (charmLevel == 1) return RAW.contains(item);
        if (charmLevel < 3 && PREMIUM.contains(item)) return false;
        return charmLevel >= 4 || !STEWS.contains(item);
    }
}
