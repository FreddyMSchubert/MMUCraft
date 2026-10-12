package uk.co.httpsmmuminecraftsociety.mainmod.enchantment;

import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderOwner;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.CharmorManager;
import uk.co.httpsmmuminecraftsociety.mainmod.modifiers.anvilRework.recipes.ApplyEnchBook;
import uk.co.httpsmmuminecraftsociety.mainmod.modifiers.anvilRework.recipes.RenameItem;

public final class GearEnchantmentCheck {
    public static void main(String[] args) {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        var registries = VanillaRegistries.createWorldLookup();
        BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(registries).forEach(components -> components.apply());
        var enchantments = registries.lookupOrThrow(Registries.ENCHANTMENT);
        var featherFalling = enchantments.getOrThrow(Enchantments.FEATHER_FALLING);
        var sharpness = enchantments.getOrThrow(Enchantments.SHARPNESS);

        ItemStack boots = new ItemStack(Items.DIAMOND_BOOTS);
        ItemEnchantments.Mutable applied = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        applied.set(sharpness, 5);
        boots.set(DataComponents.ENCHANTMENTS, applied.toImmutable());
        assert GearEnchantmentSanitizer.sanitize(boots);
        assert boots.getEnchantments().getLevel(sharpness) == 0;
        assert !GearEnchantmentSanitizer.sanitize(boots);

        assert GearEnchantmentSanitizer.maxAllowedLevel(featherFalling, false) == 3;
        assert GearEnchantmentSanitizer.maxAllowedLevel(featherFalling, true) == 68;

        ItemStack enderite = new ItemStack(Items.NETHERITE_BOOTS);
        CustomData.update(DataComponents.CUSTOM_DATA, enderite,
                tag -> tag.putBoolean(CharmorManager.ENDERITE_MARKER_BOOL, true));
        assert CharmorManager.isEnderite(enderite);

        ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
        applied.set(featherFalling, 4);
        book.set(DataComponents.STORED_ENCHANTMENTS, applied.toImmutable());
        assert !GearEnchantmentSanitizer.sanitize(book);
        assert book.get(DataComponents.STORED_ENCHANTMENTS).getLevel(featherFalling) == 4;

        var traveler = Holder.Reference.createStandAlone(new HolderOwner<Enchantment>() {},
                ResourceKey.create(Registries.ENCHANTMENT,
                        Identifier.fromNamespaceAndPath("nova_structures", "traveler")));
        ItemEnchantments.Mutable forbidden = new ItemEnchantments.Mutable(applied.toImmutable());
        forbidden.set(traveler, 1);
        book.set(DataComponents.STORED_ENCHANTMENTS, forbidden.toImmutable());
        assert GearEnchantmentSanitizer.sanitize(book);
        assert book.get(DataComponents.STORED_ENCHANTMENTS).getLevel(traveler) == 0;
        assert book.get(DataComponents.STORED_ENCHANTMENTS).getLevel(featherFalling) == 4;

        SimpleContainer stash = new SimpleContainer(5);
        int slot = 0;
        for (String id : new String[] {"outreach", "swift_soar", "traveler"}) {
            var blocked = Holder.Reference.createStandAlone(new HolderOwner<Enchantment>() {},
                    ResourceKey.create(Registries.ENCHANTMENT,
                            Identifier.fromNamespaceAndPath("nova_structures", id)));
            ItemEnchantments.Mutable onlyBlocked = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
            onlyBlocked.set(blocked, 1);
            ItemStack blockedBook = new ItemStack(Items.ENCHANTED_BOOK);
            blockedBook.set(DataComponents.STORED_ENCHANTMENTS, onlyBlocked.toImmutable());
            stash.setItem(slot++, blockedBook);
        }
        ItemStack mixedBook = new ItemStack(Items.ENCHANTED_BOOK);
        mixedBook.set(DataComponents.STORED_ENCHANTMENTS, forbidden.toImmutable());
        stash.setItem(3, mixedBook);
        ItemEnchantments.Mutable worn = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        worn.set(traveler, 1);
        worn.set(featherFalling, 2);
        ItemStack wornBoots = new ItemStack(Items.DIAMOND_BOOTS);
        wornBoots.set(DataComponents.ENCHANTMENTS, worn.toImmutable());
        stash.setItem(4, wornBoots);
        GearEnchantmentSanitizer.sanitizeContainer(stash);
        assert stash.getItem(0).isEmpty() && stash.getItem(1).isEmpty() && stash.getItem(2).isEmpty();
        assert stash.getItem(3).get(DataComponents.STORED_ENCHANTMENTS).getLevel(traveler) == 0;
        assert stash.getItem(3).get(DataComponents.STORED_ENCHANTMENTS).getLevel(featherFalling) == 4;
        assert stash.getItem(4).getEnchantments().getLevel(traveler) == 0;

        var unlisted = Holder.direct(sharpness.value());
        ItemEnchantments.Mutable low = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        low.set(unlisted, 1);
        ItemEnchantments.Mutable high = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        high.set(unlisted, 2);
        ItemStack first = new ItemStack(Items.ENCHANTED_BOOK);
        first.set(DataComponents.STORED_ENCHANTMENTS, low.toImmutable());
        ItemStack second = new ItemStack(Items.ENCHANTED_BOOK);
        second.set(DataComponents.STORED_ENCHANTMENTS, high.toImmutable());
        var combined = new ApplyEnchBook().apply(null, first, second, null);
        assert combined.result().get(DataComponents.STORED_ENCHANTMENTS).getLevel(unlisted) == 3;

        ItemEnchantments.Mutable sharpnessOnly = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        sharpnessOnly.set(sharpness, 5);
        ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
        sword.set(DataComponents.ENCHANTMENTS, sharpnessOnly.toImmutable());
        var copied = new ApplyEnchBook().apply(null, new ItemStack(Items.BOOK, 5), sword, null);
        assert copied.result().is(Items.ENCHANTED_BOOK) && copied.result().getCount() == 1;
        assert copied.leftRemainder().is(Items.BOOK) && copied.leftRemainder().getCount() == 4;
        assert copied.rightRemainder().is(Items.DIAMOND_SWORD) && copied.rightRemainder().getCount() == 1;
        assert copied.rightRemainder().getEnchantments().isEmpty();

        ItemStack stackedBooks = new ItemStack(Items.ENCHANTED_BOOK, 3);
        stackedBooks.set(DataComponents.STORED_ENCHANTMENTS, sharpnessOnly.toImmutable());
        var transferred = new ApplyEnchBook().apply(null, new ItemStack(Items.BOOK), stackedBooks, null);
        assert transferred.result().is(Items.ENCHANTED_BOOK);
        assert transferred.rightRemainder().is(Items.ENCHANTED_BOOK) && transferred.rightRemainder().getCount() == 2;

        ItemStack stackedSwords = sword.copyWithCount(2);
        assert new ApplyEnchBook().apply(null, new ItemStack(Items.BOOK), stackedSwords, null).result().isEmpty();

        var renamed = new RenameItem().apply(null, new ItemStack(Items.BOOK, 5), ItemStack.EMPTY, "Named");
        assert renamed.result().getCount() == 5;

        System.out.println("Gear enchantment checks passed.");
    }
}
