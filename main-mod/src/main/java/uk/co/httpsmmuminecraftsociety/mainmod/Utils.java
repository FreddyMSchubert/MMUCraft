package uk.co.httpsmmuminecraftsociety.mainmod;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;

import java.util.List;
import java.util.UUID;

public class Utils
{
    public static UUID UUIDfromString(String s)
    {
        return UUID.nameUUIDFromBytes(s.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}
