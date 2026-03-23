package uk.co.httpsmmuminecraftsociety.mainmod.recipe.dataDriven;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.item.ItemStack;

public sealed interface FakeResult permits FakeResult.ItemResult, FakeResult.FakeItemResult {
    Codec<FakeResult> CODEC = Codec.xor(
            ItemResult.CODEC,
            FakeItemResult.CODEC
    ).xmap(
            either -> either.map(
                    item -> item,
                    fake -> (FakeResult) fake
            ),
            result -> {
                if (result instanceof ItemResult item) {
                    return Either.left(item);
                }
                return Either.right((FakeItemResult) result);
            }
    );

    ItemStack createStack();

    record ItemResult(ComponentAwareItem item, int count) implements FakeResult {
        public static final Codec<ItemResult> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                ComponentAwareItem.CODEC.fieldOf("item").forGetter(ItemResult::item),
                Codec.intRange(1, Integer.MAX_VALUE).fieldOf("count").forGetter(ItemResult::count)
        ).apply(instance, ItemResult::new));

        public ItemResult {
            item.createStack(count);
        }

        @Override
        public ItemStack createStack() {
            return item.createStack(count);
        }
    }

    record FakeItemResult(String fakeitem, int count) implements FakeResult {
        public static final Codec<FakeItemResult> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("fakeitem").forGetter(FakeItemResult::fakeitem),
                Codec.intRange(1, Integer.MAX_VALUE).fieldOf("count").forGetter(FakeItemResult::count)
        ).apply(instance, FakeItemResult::new));

        public FakeItemResult {
            FakeRecipeUtil.requireFakeItem(fakeitem);
        }

        @Override
        public ItemStack createStack() {
            return FakeRecipeUtil.createFakeItemStack(fakeitem, count);
        }
    }
}