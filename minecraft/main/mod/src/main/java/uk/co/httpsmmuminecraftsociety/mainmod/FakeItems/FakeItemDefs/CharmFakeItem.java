package uk.co.httpsmmuminecraftsociety.mainmod.FakeItems.FakeItemDefs;

import com.google.gson.JsonObject;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.CustomData;
import uk.co.httpsmmuminecraftsociety.mainmod.FakeItems.CharmsManager;
import uk.co.httpsmmuminecraftsociety.mainmod.FakeItems.charms.def.BaseItemChangeCallbackCharm;
import uk.co.httpsmmuminecraftsociety.mainmod.FakeItems.charms.def.Charm;
import uk.co.httpsmmuminecraftsociety.mainmod.itemdata.CharmCodeRegistry;

public class CharmFakeItem extends FakeItem
{
    private final Charm charm;
    private final int effectId;

    public CharmFakeItem(int effectId, String model_id, String title, Rarity rarity, Charm charm, String... tooltip)
    {
        super(Items.COMMAND_BLOCK, model_id, title, rarity, 1, tooltip);

        this.charm = charm;
        this.effectId = effectId;
    }

    public Charm getCharm() {
        return charm;
    }
    public int getEffectId() {
        return effectId;
    }

    @Override
    public ItemStack createItemStack()
    {
        ItemStack stack = super.createItemStack();

        if (charm instanceof BaseItemChangeCallbackCharm baseItemChangeCallbackCharm) {
            stack = baseItemChangeCallbackCharm.enableEffectForItem(stack);
        }

        CompoundTag nbt = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        int[] arr = { effectId };
        nbt.putIntArray(CharmsManager.CHARM_ABILITES_COMPOUND_ID, arr);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(nbt));

        return stack;
    }

    public static CharmFakeItem fromJson(JsonObject root, String sourcePath) {
        CommonFields common = parseCommon(root, sourcePath, 1);
        JsonObject behaviour = getBehaviourObject(root);

        int effectId = requiredInt(behaviour, root, "effectId", sourcePath);
        Charm charm = CharmCodeRegistry.getRequired(effectId, sourcePath);

        return new CharmFakeItem(effectId, common.modelId(), common.title(), common.rarity(), charm, common.tooltip());
    }
}
