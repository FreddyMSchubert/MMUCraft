package dev.freddy.killshift;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;

public final class AbilitySlot {
    private static final int SLOT = 8;
    private static final String MARKER = "killshift_slot";

    private AbilitySlot() { }

    public static boolean locked(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null && data.copyTag().getStringOr(MARKER, "").startsWith("killshift:");
    }

    static boolean trigger(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null && data.copyTag().getStringOr(MARKER, "").equals("killshift:ability");
    }

    static boolean barrier(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null && data.copyTag().getStringOr(MARKER, "").equals("killshift:barrier");
    }

    static void sync(ServerPlayer player) {
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (slot != SLOT && locked(inventory.getItem(slot))) {
                inventory.setItem(slot, ItemStack.EMPTY);
            }
        }
        ShapeState state = ShapeManager.get(player);
        Item ability = state == null ? null : MobAbilities.abilityItem(state.form.type());
        boolean hasAbility = ability != null;
        ItemStack current = inventory.getItem(SLOT);
        if (hasAbility ? trigger(current) && current.is(ability)
                : barrier(current) && current.is(Items.BARRIER)) return;

        ItemStack replacement = new ItemStack(hasAbility ? ability : Items.BARRIER);
        replacement.set(DataComponents.CUSTOM_NAME, hasAbility
                ? state.form.type().getDescription().copy().append(" Ability - Right Click")
                : Component.literal("No Mob Ability"));
        CompoundTag tag = new CompoundTag();
        tag.putString(MARKER, hasAbility ? "killshift:ability" : "killshift:barrier");
        replacement.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        inventory.setItem(SLOT, replacement);
        if (!current.isEmpty() && !locked(current) && !inventory.add(current)) {
            player.spawnAtLocation(player.level(), current);
        }
    }

    static void clear(ServerPlayer player) {
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (locked(inventory.getItem(slot))) inventory.setItem(slot, ItemStack.EMPTY);
        }
    }
}
