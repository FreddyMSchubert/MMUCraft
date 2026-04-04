package uk.co.httpsmmuminecraftsociety.mainmod.dataRead.stackDefs;

import net.minecraft.world.item.ItemStack;

public sealed interface StackDef permits VanillaStackDef, FakeStackDef, TagStackDef
{
    String raw();

    boolean matches(ItemStack stack);

    ItemStack createStack();

    int specificity();

    default boolean canCreateStack() {
        return true;
    }
}
