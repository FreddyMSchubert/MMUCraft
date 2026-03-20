package uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.fakeItemDefs;

import com.google.gson.JsonObject;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.CharmsManager;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.def.BaseItemChangeCallbackCharm;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.def.Charm;

public record CharmItemFeature (
        Charm charm,
        int charmId
) implements ItemFeature
{
    public static ItemFeature of(JsonObject json)
    {
        int charmId = json.get("charmId").getAsInt();
        return new CharmItemFeature(CharmsManager.charmFromId(charmId), charmId);
    }

    @Override
    public void apply(ItemStack stack)
    {
        // enable charm
        if (charm instanceof BaseItemChangeCallbackCharm baseItemChangeCallbackCharm) {
            baseItemChangeCallbackCharm.enableEffectForItem(stack);
        }

        // store reference to charm
        CompoundTag nbt = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        int[] arr = { charmId };
        nbt.putIntArray(CharmsManager.CHARM_ABILITES_COMPOUND_ID, arr);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(nbt));
    }
}
