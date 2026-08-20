package uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.fakeItemDefs;

import com.google.gson.JsonObject;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;

public record DecoBlockItemFeature(
        boolean ground,
        boolean wall,
        boolean ceiling,
        boolean glowing,
        int lightLevel
) implements ItemFeature
{
    public static ItemFeature of(JsonObject json)
    {
        return new DecoBlockItemFeature(
                json.get("ground").getAsBoolean(),
                json.get("wall").getAsBoolean(),
                json.get("ceiling").getAsBoolean(),
                json.get("glowing").getAsBoolean(),
                json.get("lightLevel").getAsInt()
        );
    }

    public boolean canPlaceOn(Direction face)
    {
        return switch (face) {
            case UP -> ground;
            case DOWN -> ceiling;
            case NORTH, SOUTH, EAST, WEST -> wall;
        };
    }

    @Override
    public void apply(ItemStack stack)
    {

    }

    @Override
    public void validate()
    {
        if (!ground && !wall && !ceiling) {
            throw new IllegalStateException("Deco block must be placeable on at least one surface.");
        }
        if (lightLevel < 0 || lightLevel > 15) {
            throw new IllegalStateException("Deco block light level must be between 0 and 15.");
        }
    }
}
