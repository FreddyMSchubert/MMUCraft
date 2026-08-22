package uk.co.httpsmmuminecraftsociety.mainmod.grpc;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.FakeItems;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.fakeItemDefs.FakeItem;

final class MinecraftItemIdentifier {
    private MinecraftItemIdentifier() {}

    static String forStack(ItemStack stack) {
        FakeItem fakeItem = FakeItems.getFakeItemFromStack(stack);
        return fakeItem == null
                ? BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()
                : "mainmod:" + fakeItem.id();
    }
}
