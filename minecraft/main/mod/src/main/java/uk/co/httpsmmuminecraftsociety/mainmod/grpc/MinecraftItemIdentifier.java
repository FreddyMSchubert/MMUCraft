package uk.co.httpsmmuminecraftsociety.mainmod.grpc;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.FakeItems;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.CharmorManager;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.fakeItemDefs.FakeItem;

final class MinecraftItemIdentifier {
    private MinecraftItemIdentifier() {}

    static String forStack(ItemStack stack) {
        FakeItem fakeItem = FakeItems.getFakeItemFromStack(stack);
        if (fakeItem != null) return "mainmod:" + fakeItem.id();
        String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        if (itemId.equals("minecraft:test_block")) return "mainmod:alien-debris";
        if (itemId.equals("minecraft:test_instance_block")) return "mainmod:enderite-block";
        // Enderite gear shares Netherite item IDs; preserve its marker for web previews.
        if (CharmorManager.isEnderite(stack) && itemId.startsWith("minecraft:netherite_")) {
            return "mainmod:enderite-" + itemId.substring("minecraft:netherite_".length()).replace('_', '-');
        }
        return itemId;
    }
}
