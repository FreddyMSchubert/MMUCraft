package uk.co.httpsmmuminecraftsociety.mainmod.enchantment;

import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.CharmorManager;

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
        System.out.println("Gear enchantment checks passed.");
    }
}
