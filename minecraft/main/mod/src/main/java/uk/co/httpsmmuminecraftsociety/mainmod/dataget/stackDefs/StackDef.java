package uk.co.httpsmmuminecraftsociety.mainmod.dataget.stackDefs;

import net.minecraft.world.item.ItemStack;

public sealed interface StackDef permits VanillaStackDef, FakeStackDef, TagStackDef
{
    String raw();

    boolean matches(ItemStack stack);

    ItemStack createStack();

    String getDisplayName();

    String displayNameOverride();

    default boolean hasDisplayNameOverride() {
        return !displayNameOverride().isBlank();
    }

    default boolean canCreateStack() {
        return true;
    }

    default int specificity() {
        return 0;
    }
}
