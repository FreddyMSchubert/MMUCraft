package dev.freddy.killshift;

import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.UseCooldown;

public final class AbilitySlot {
    private static final int SLOT = 8;
    private static final String MARKER = "killshift_slot";
    private static final String FORM = "killshift_form";
    private static final Identifier COOLDOWN_GROUP = Identifier.fromNamespaceAndPath(Killshift.MOD_ID, "ability");
    private static final UseCooldown COOLDOWN = new UseCooldown(1.0F, Optional.of(COOLDOWN_GROUP));

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

    public static ItemStack arrowFor(Player player, ItemStack weapon) {
        if (!weapon.is(Items.BOW)) return ItemStack.EMPTY;
        CustomData data = player.getInventory().getItem(SLOT).get(DataComponents.CUSTOM_DATA);
        if (data == null) return ItemStack.EMPTY;
        CompoundTag tag = data.copyTag();
        if (!tag.getStringOr(MARKER, "").startsWith("killshift:")) return ItemStack.EMPTY;
        return switch (tag.getStringOr(FORM, "")) {
            case "minecraft:bogged" -> PotionContents.createItemStack(Items.TIPPED_ARROW, Potions.POISON);
            case "minecraft:stray" -> PotionContents.createItemStack(Items.TIPPED_ARROW, Potions.SLOWNESS);
            case "minecraft:parched" -> parchedArrow();
            case "minecraft:skeleton" -> new ItemStack(Items.ARROW);
            default -> ItemStack.EMPTY;
        };
    }

    private static ItemStack parchedArrow() {
        ItemStack arrow = PotionContents.createItemStack(Items.TIPPED_ARROW, Potions.WEAKNESS);
        arrow.set(DataComponents.POTION_DURATION_SCALE, 1.0F / 3.0F);
        return arrow;
    }

    public static boolean blocksSprint(Player player) {
        EntityType<?> type = markedForm(player);
        return type != null && type != EntityTypes.PLAYER
                && type != EntityTypes.CAMEL && type != EntityTypes.CAMEL_HUSK
                && !(MobRegistry.traits(type).aquatic() && player.isInWater());
    }

    public static boolean blocksSwim(Player player) {
        EntityType<?> type = markedForm(player);
        return type != null && type != EntityTypes.PLAYER
                && !(MobRegistry.traits(type).aquatic() && player.isInWater());
    }

    private static EntityType<?> markedForm(Player player) {
        ItemStack stack = player.getInventory().getItem(SLOT);
        if (!locked(stack)) return null;
        Identifier id = Identifier.tryParse(formId(stack));
        return id == null ? null : BuiltInRegistries.ENTITY_TYPE.getOptional(id).orElse(null);
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
        String form = state == null ? "" : BuiltInRegistries.ENTITY_TYPE.getKey(state.form.type()).toString();
        Identifier model = BuiltInRegistries.ITEM.getKey(hasAbility ? ability : Items.BARRIER);
        Component name = hasAbility ? Component.literal(MobAbilities.abilityDescription(state.form.type()))
                : Component.literal("No Mob Ability");
        if (form.equals(formId(current)) && name.equals(current.get(DataComponents.CUSTOM_NAME))
                && current.is(Items.ECHO_SHARD)
                && model.equals(current.get(DataComponents.ITEM_MODEL))
                && (hasAbility ? trigger(current)
                && COOLDOWN.equals(current.get(DataComponents.USE_COOLDOWN))
                : barrier(current))) return;

        ItemStack replacement = new ItemStack(Items.ECHO_SHARD);
        replacement.set(DataComponents.CUSTOM_NAME, name);
        CompoundTag tag = new CompoundTag();
        tag.putString(MARKER, hasAbility ? "killshift:ability" : "killshift:barrier");
        tag.putString(FORM, form);
        replacement.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        replacement.set(DataComponents.ITEM_MODEL, model);
        if (hasAbility) {
            replacement.set(DataComponents.USE_COOLDOWN, COOLDOWN);
        }
        inventory.setItem(SLOT, replacement);
        if (!current.isEmpty() && !locked(current) && !inventory.add(current)) {
            player.spawnAtLocation(player.level(), current);
        }
    }

    private static String formId(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null ? "" : data.copyTag().getStringOr(FORM, "");
    }

    static void clear(ServerPlayer player) {
        player.getCooldowns().removeCooldown(COOLDOWN_GROUP);
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (locked(inventory.getItem(slot))) inventory.setItem(slot, ItemStack.EMPTY);
        }
    }

    static void startCooldown(ServerPlayer player, ShapeState state) {
        if (ShapeManager.get(player) == state && state.abilityCooldown > 0) {
            player.getCooldowns().addCooldown(COOLDOWN_GROUP, state.abilityCooldown);
        }
    }

}
