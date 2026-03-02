package uk.co.httpsmmuminecraftsociety.mainmod.FakeItems.charms;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import uk.co.httpsmmuminecraftsociety.mainmod.MainMod;
import uk.co.httpsmmuminecraftsociety.mainmod.Utils;

public class HikingBootsCharm implements Charm
{
    public static final String HIKING_BOOTS_CHARM_ID_BEGINNING = "cosmetic-charm-hiking-boots-";
    private static final Identifier STEP_ID = Identifier.fromNamespaceAndPath(MainMod.MOD_ID, "hiking_boots_step_id");

    private static final String TAG_LEVEL = "hb_level";

    private int level = 0;

    public HikingBootsCharm(int level) {
        this.level = level;
    }

    @Override
    public String id()
    {
        return HIKING_BOOTS_CHARM_ID_BEGINNING + level;
    }

    @Override
    public ItemStack onCreation(ItemStack stack)
    {
        CustomData cd = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag tag = cd.copyTag();
        tag.putInt(TAG_LEVEL, level);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return stack;
    }

    @Override
    public boolean subcribeToOnTick()
    {
        return false;
    }

    public static float getStepHeightForLevel(int level) {
        return switch (level)
        {
            case 0 -> 0.5f;
            case 1 -> 1f;
            case 2 -> 1.5f;
            default -> 105f;
        };
    }

    @Override
    public ItemStack onTick(ItemStack stack, ServerPlayer player, ServerLevel level)
    {
        return null;
    }

    @Override
    public ItemStack onEquipmentSlotChange(ServerPlayer player, ItemStack stack, int from, int to)
    {
        // check if its in a relevant slot. if not, remove
        boolean shouldApplyEffect = false;
        for (EquipmentSlot slot : EquipmentSlot.values())
        {
            if (player.getItemBySlot(slot) == stack){
                shouldApplyEffect = true;
                break;
            }
        }

        if (shouldApplyEffect) {
            int itemLevel = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getIntOr(TAG_LEVEL, 0);
            Utils.applyModifier(player, Attributes.STEP_HEIGHT, STEP_ID, getStepHeightForLevel(itemLevel), AttributeModifier.Operation.ADD_VALUE);
        } else {
            Utils.removeModifier(player, Attributes.STEP_HEIGHT, STEP_ID);
        }

        return null;
    }
}
