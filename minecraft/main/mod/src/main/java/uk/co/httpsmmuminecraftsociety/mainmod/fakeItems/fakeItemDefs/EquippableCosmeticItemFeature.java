package uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.fakeItemDefs;

import com.google.gson.JsonObject;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.waypoints.Waypoint;
import uk.co.httpsmmuminecraftsociety.mainmod.utils.Utils;

public record EquippableCosmeticItemFeature() implements ItemFeature
{
    public static ItemFeature of(JsonObject json)
    {
        return new EquippableCosmeticItemFeature();
    }

    @Override
    public void apply(ItemStack stack)
    {
        Utils.removeItemAttrModifier(
                stack,
                Waypoint.WAYPOINT_TRANSMIT_RANGE_HIDE_MODIFIER.id(),
                Attributes.WAYPOINT_TRANSMIT_RANGE);
    }

    @Override
    public void validate()
    {

    }
}
