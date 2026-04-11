package uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.held;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.component.ItemLore;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.def.Charm;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.def.ConsumableCallbacksCharm;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SculkPhialCharm implements Charm, ConsumableCallbacksCharm
{
    public static final String XP_STORED_ID = "sculk_phial_stored_xp";

    private static final int MAX_XP_STORABLE = 1395;

    @Override
    public void onConsumeTick(ItemStack stack, ServerPlayer player, ServerLevel level, int elapsedTicks, int charmLevel) {}

    @Override
    public void onConsumeFinished(ItemStack stack, ServerPlayer player, ServerLevel level, int elapsedTicks, int charmLevel)
    {
        CompoundTag nbt = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        int xpStored = nbt.getIntOr(XP_STORED_ID, 0);

        if (xpStored >= MAX_XP_STORABLE) return;
        int xpStorable = MAX_XP_STORABLE - xpStored;

        int intoLevel = (int) Math.floor(player.experienceProgress * player.getXpNeededForNextLevel());

        int xpToSiphon = Math.min(xpStorable, intoLevel);

        if (xpToSiphon <= 0) {
            if (player.experienceLevel == 0) return;

            int previousLevelCost =
                    determinePlayerXPAtLevelStart(player.experienceLevel)
                            - determinePlayerXPAtLevelStart(player.experienceLevel - 1);

            xpToSiphon = Math.min(xpStorable, previousLevelCost);
            if (xpToSiphon <= 0) return;
        }

        player.giveExperiencePoints(-xpToSiphon);
        xpStored += xpToSiphon;

        nbt.putInt(XP_STORED_ID, xpStored);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(nbt));

        updateStoredXpVisuals(stack, xpStored);
    }

    private static double xpToLevels(int totalXp) {
        int level = 0;
        while (determinePlayerXPAtLevelStart(level + 1) <= totalXp) {
            level++;
        }
        int xpIntoLevel = totalXp - determinePlayerXPAtLevelStart(level);
        int xpForNextLevel = determinePlayerXPAtLevelStart(level + 1) - determinePlayerXPAtLevelStart(level);
        if (xpForNextLevel <= 0) {
            return level;
        }
        return level + ((double) xpIntoLevel / xpForNextLevel);
    }

    public static void updateStoredXpVisuals(ItemStack stack, int xpStored)
    {
        CustomModelData cmd = stack.getOrDefault(DataComponents.CUSTOM_MODEL_DATA, CustomModelData.EMPTY);
        stack.set(
                DataComponents.CUSTOM_MODEL_DATA,
                new CustomModelData(List.of((float) xpStored), cmd.flags(), cmd.strings(), cmd.colors())
        );

        ItemLore existingLore = stack.getOrDefault(DataComponents.LORE, ItemLore.EMPTY);
        List<Component> lines = new ArrayList<>(existingLore.lines());

        double levelsStored = xpToLevels(xpStored);
        String levelsText = String.format(Locale.ROOT, "%.2f", levelsStored);

        Component firstLine = Component.literal(
                "Currently stored: " + xpStored + " xp / " + levelsText + " levels."
        );

        if (lines.isEmpty()) {
            lines.add(firstLine);
        } else {
            lines.set(0, firstLine);
        }

        stack.set(DataComponents.LORE, new ItemLore(lines, List.of()));
    }

    private static int determinePlayerXPAtLevelStart(int level) {
        if (level <= 16) {
            return level * level + 6 * level;
        }
        if (level <= 31) {
            return (int)(2.5D * level * level - 40.5D * level + 360.0D);
        }
        return (int)(4.5D * level * level - 162.5D * level + 2220.0D);
    }
}