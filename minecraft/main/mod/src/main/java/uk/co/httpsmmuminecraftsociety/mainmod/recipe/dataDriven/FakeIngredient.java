package uk.co.httpsmmuminecraftsociety.mainmod.recipe.dataDriven;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public sealed interface FakeIngredient permits FakeIngredient.ItemIngredient, FakeIngredient.TagIngredient, FakeIngredient.FakeItemIngredient {
    Codec<FakeIngredient> CODEC = Codec.xor(
            ItemIngredient.CODEC,
            Codec.xor(TagIngredient.CODEC, FakeItemIngredient.CODEC)
    ).xmap(
            either -> either.map(
                    item -> (FakeIngredient) item,
                    inner -> inner.map(
                            tag -> (FakeIngredient) tag,
                            fake -> (FakeIngredient) fake
                    )
            ),
            ingredient -> {
                if (ingredient instanceof ItemIngredient item) {
                    return Either.left(item);
                }
                if (ingredient instanceof TagIngredient tag) {
                    return Either.right(Either.left(tag));
                }
                return Either.right(Either.right((FakeItemIngredient) ingredient));
            }
    );

    boolean matches(ItemStack stack);

    int specificity();

    record ItemIngredient(Item item) implements FakeIngredient {
        public static final Codec<ItemIngredient> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                BuiltInRegistries.ITEM.byNameCodec().fieldOf("item").forGetter(ItemIngredient::item)
        ).apply(instance, ItemIngredient::new));

        @Override
        public boolean matches(ItemStack stack) {
            return !stack.isEmpty() && stack.getItem() == item;
        }

        @Override
        public int specificity() {
            return 2;
        }
    }

    record TagIngredient(TagKey<Item> tag) implements FakeIngredient {
        public static final Codec<TagIngredient> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                TagKey.codec(Registries.ITEM).fieldOf("tag").forGetter(TagIngredient::tag)
        ).apply(instance, TagIngredient::new));

        @Override
        public boolean matches(ItemStack stack) {
            return !stack.isEmpty() && stack.is(tag);
        }

        @Override
        public int specificity() {
            return 1;
        }
    }

    record FakeItemIngredient(String fakeitem) implements FakeIngredient {
        public static final Codec<FakeItemIngredient> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("fakeitem").forGetter(FakeItemIngredient::fakeitem)
        ).apply(instance, FakeItemIngredient::new));

        public FakeItemIngredient {
            FakeRecipeUtil.requireFakeItem(fakeitem);
        }

        @Override
        public boolean matches(ItemStack stack) {
            return !stack.isEmpty() && FakeRecipeUtil.isFakeItem(stack, fakeitem);
        }

        @Override
        public int specificity() {
            return 3;
        }
    }
}
