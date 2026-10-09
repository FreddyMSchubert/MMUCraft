package uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.fakeItemDefs;

import com.google.gson.JsonObject;
import net.minecraft.world.item.ItemStack;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.CosmeticsManager;

public record EquippableCosmeticItemFeature() implements ItemFeature
{
    public static ItemFeature of(JsonObject json)
    {
        return new EquippableCosmeticItemFeature();
    }

    @Override
    public void apply(ItemStack stack)
    {
        CosmeticsManager.normalizeCosmeticHead(stack);
    }

    @Override
    public void validate()
    {

    }
}
