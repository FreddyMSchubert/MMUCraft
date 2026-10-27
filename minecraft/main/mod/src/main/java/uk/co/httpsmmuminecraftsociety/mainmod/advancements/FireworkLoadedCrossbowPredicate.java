package uk.co.httpsmmuminecraftsociety.mainmod.advancements;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ChargedProjectiles;

public final class FireworkLoadedCrossbowPredicate {
    private FireworkLoadedCrossbowPredicate() {}

    public static boolean matches(Inventory inventory) {
        return inventory.contains(stack -> stack.is(Items.CROSSBOW)
                && stack.getOrDefault(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.EMPTY)
                        .contains(Items.FIREWORK_ROCKET));
    }
}
