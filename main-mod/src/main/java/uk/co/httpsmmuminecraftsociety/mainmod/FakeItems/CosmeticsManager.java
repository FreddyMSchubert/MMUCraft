package uk.co.httpsmmuminecraftsociety.mainmod.FakeItems;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.component.ItemLore;
import uk.co.httpsmmuminecraftsociety.mainmod.datagen.ModItemTagProvider;

import java.util.List;
import java.util.Optional;

public final class CosmeticsManager {
    private CosmeticsManager() {}

    private static final String ORIGINAL_ITEM_ID = "original_item_id";
    private static final String HELMET_DYED_COLOR_ID = "helmet_dyed_color";

    public static ItemStack helmetToPumpkinReplica(ItemStack helmet) {
        if (helmet.isEmpty() || !helmet.is(ModItemTagProvider.COSMETIC_COMBINABLE_ARMOR_ITEMS)) {
            return ItemStack.EMPTY;
        }

        ItemStack replica = helmet.transmuteCopy(Items.CARVED_PUMPKIN, helmet.getCount());

        // store original item id
        CompoundTag nbt = new CompoundTag();
        nbt.putString(ORIGINAL_ITEM_ID, BuiltInRegistries.ITEM.getKey(helmet.getItem()).toString());

        // component time
        replica.set(DataComponents.MAX_STACK_SIZE, 1);
        replica.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, false);
        replica.set(DataComponents.LORE, new ItemLore(List.of(Component.literal("Cosmetic reskin of " + helmet.getItem().getName().getString() + "."))));

        replica.set(DataComponents.MAX_DAMAGE, helmet.getMaxDamage());
        replica.set(DataComponents.DAMAGE, helmet.getDamageValue());
        replica.set(DataComponents.ENCHANTMENTS, helmet.getEnchantments());

        replica.set(DataComponents.ITEM_NAME, helmet.getItemName());
        replica.set(DataComponents.CUSTOM_NAME, helmet.getCustomName());

        if (helmet.has(DataComponents.DYED_COLOR)) {
            nbt.putInt(HELMET_DYED_COLOR_ID, helmet.get(DataComponents.DYED_COLOR).rgb());
        }

        replica.set(DataComponents.CUSTOM_DATA, CustomData.of(nbt));
        return replica;
    }

    public static ItemStack pumpkinReplicaToHelmet(ItemStack replica) {
        if (replica.isEmpty()) {
            return ItemStack.EMPTY;
        }

        // reconstruct original helmet item
        CompoundTag nbt = replica.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        Optional<String> originalId = nbt.getString(ORIGINAL_ITEM_ID);
        if (originalId.isEmpty() || originalId.get().isEmpty()) {
            return ItemStack.EMPTY;
        }
        Identifier itemKey = Identifier.tryParse(originalId.get());
        if (itemKey == null) {
            return ItemStack.EMPTY;
        }
        Optional<Item> originalItemOpt = BuiltInRegistries.ITEM.getOptional(itemKey);
        if (originalItemOpt.isEmpty()) {
            return ItemStack.EMPTY;
        }
        Item originalItem = originalItemOpt.get();
        ItemStack helmet = new ItemStack(originalItem);

        // copy relevant fields back again
        helmet.set(DataComponents.DAMAGE, replica.get(DataComponents.DAMAGE));
        helmet.set(DataComponents.ENCHANTMENTS, replica.get(DataComponents.ENCHANTMENTS));

        helmet.set(DataComponents.ITEM_NAME, replica.get(DataComponents.ITEM_NAME));
        helmet.set(DataComponents.CUSTOM_NAME, replica.get(DataComponents.CUSTOM_NAME));

        if (nbt.contains(HELMET_DYED_COLOR_ID)) {
            helmet.set(DataComponents.DYED_COLOR, new DyedItemColor(nbt.getInt(HELMET_DYED_COLOR_ID).get()));
        }

        return helmet;
    }
}
