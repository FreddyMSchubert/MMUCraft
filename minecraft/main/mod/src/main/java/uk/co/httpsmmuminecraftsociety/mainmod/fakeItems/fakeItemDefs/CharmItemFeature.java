package uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.fakeItemDefs;

import com.google.gson.JsonObject;
import net.minecraft.world.item.ItemStack;

public record CharmItemFeature (
        int effectId
) implements ItemFeature
{
    public static ItemFeature of(JsonObject json)
    {
        if (!json.has("charm"))
            return null;
        return new CharmItemFeature(json.get("charm").getAsJsonObject().get("effectId").getAsInt());
    }

    @Override
    public boolean isValid()
    {
        return false;
    }

    @Override
    public void apply(ItemStack stack)
    {

    }
}
