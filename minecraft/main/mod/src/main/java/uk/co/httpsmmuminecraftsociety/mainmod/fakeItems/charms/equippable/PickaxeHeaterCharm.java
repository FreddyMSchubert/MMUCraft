package uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.equippable;

import net.minecraft.core.Holder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import uk.co.httpsmmuminecraftsociety.mainmod.MainMod;
import uk.co.httpsmmuminecraftsociety.mainmod.datagen.ModItemTagProvider;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.CharmsManager;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.def.Charm;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.fakeItemDefs.EquippableCharmItemFeature;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class PickaxeHeaterCharm implements Charm
{
    private record SmeltedDrop(ItemStack stack, float experience) {}

    private static volatile Map<Item, SmeltedDrop> smeltedDropsByItem = Map.of();

    @SuppressWarnings("deprecation")
    public static void rebuildSmeltedDropMap(MinecraftServer server)
    {
        Map<Item, SmeltedDrop> next = new HashMap<>();

        for (RecipeHolder<?> holder : server.getRecipeManager().getRecipes()) {
            Recipe<?> recipe = holder.value();
            if (!(recipe instanceof AbstractCookingRecipe cookingRecipe)) continue;
            if (cookingRecipe.getType() != RecipeType.SMELTING) continue;

            List<Holder<Item>> inputItems = cookingRecipe.input().items().toList();
            if (inputItems.isEmpty()) continue;

            ItemStack result = cookingRecipe.assemble(new SingleRecipeInput(new ItemStack(inputItems.getFirst())));
            if (result.isEmpty()) continue;

            ItemStack template = result.copy();
            template.setCount(Math.max(1, template.getCount()));
            SmeltedDrop smelted = new SmeltedDrop(template, cookingRecipe.experience());

            for (Holder<Item> inputItem : inputItems) {
                next.putIfAbsent(inputItem.value(), smelted);
            }
        }

        smeltedDropsByItem = Map.copyOf(next);
        MainMod.LOGGER.info("[PickaxeHeaterCharm] Cached {} furnace drop conversions.", smeltedDropsByItem.size());
    }

    public static void heatMinedDrops(LootContext lootContext, List<ItemStack> itemStacks)
    {
        if (itemStacks.isEmpty()) return;
        if (!lootContext.hasParameter(LootContextParams.BLOCK_STATE)) return;

        ServerPlayer player = getMiningPlayer(lootContext);
        if (player == null) return;
        int level = CharmsManager.getPlayerCharmLevel(player, PickaxeHeaterCharm.class);

        ItemStack tool = getToolStack(lootContext);
        if (tool.isEmpty()) return;

        Map<Item, SmeltedDrop> conversions = smeltedDropsByItem;
        if (conversions.isEmpty()) return;
        Vec3 origin = lootContext.getOptional(LootContextParams.ORIGIN);

        for (int i = 0; i < itemStacks.size(); i++) {
            ItemStack original = itemStacks.get(i);
            if (original.isEmpty()) continue;

            if (level <= 0) return;
            if (level <= 1 && !original.is(ModItemTagProvider.PICKAXE_HEATER_LEVEL1_SMELTABLE))
                continue;
            if (level <= 2 && !original.is(ModItemTagProvider.PICKAXE_HEATER_LEVEL2_SMELTABLE) && !original.is(ModItemTagProvider.PICKAXE_HEATER_LEVEL1_SMELTABLE))
                continue;

            SmeltedDrop smelted = conversions.get(original.getItem());
            if (smelted == null) continue;

            ItemStack replacement = smelted.stack().copy();
            replacement.setCount(original.getCount() * Math.max(1, smelted.stack().getCount()));
            itemStacks.set(i, replacement);

            if (origin != null && smelted.experience() > 0) {
                float experience = original.getCount() * smelted.experience();
                int xp = Mth.floor(experience);
                float fraction = Mth.frac(experience);
                if (fraction > 0 && lootContext.getLevel().getRandom().nextFloat() < fraction) xp++;
                ExperienceOrb.award(lootContext.getLevel(), origin, xp);
            }
        }
    }

    private static ServerPlayer getMiningPlayer(LootContext lootContext)
    {
        if (!lootContext.hasParameter(LootContextParams.THIS_ENTITY)) return null;
        if (!(lootContext.getOptional(LootContextParams.THIS_ENTITY) instanceof ServerPlayer player)) return null;
        return player;
    }

    private static ItemStack getToolStack(LootContext lootContext)
    {
        if (!lootContext.hasParameter(LootContextParams.TOOL)) return ItemStack.EMPTY;

        ItemInstance tool = lootContext.getOptional(LootContextParams.TOOL);
        if (!(tool instanceof ItemStack stack)) return ItemStack.EMPTY;

        return stack;
    }

    private static boolean hasActiveHeaterCharm(ServerPlayer player)
    {
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack stack = player.getItemBySlot(slot);
            if (stack.isEmpty()) continue;

            for (CharmsManager.CharmInstance instance : CharmsManager.getCharmInstances(stack)) {
                if (instance.isBroken()) continue;
                if (!(instance.charm() instanceof PickaxeHeaterCharm)) continue;

                EquippableCharmItemFeature equippable = instance.fakeItem().getFeature(EquippableCharmItemFeature.class);
                if (equippable != null && equippable.equippable().slot() != slot) continue;

                return true;
            }
        }

        return false;
    }
}
