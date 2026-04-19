package uk.co.httpsmmuminecraftsociety.mainmod.mixin.anvilRework;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.ItemCombinerMenu;
import net.minecraft.world.inventory.ResultContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ItemCombinerMenu.class)
public interface ItemCombinerMenuAccessor
{
    @Accessor("inputSlots")
    Container mainmod$getInputSlots();

    @Accessor("resultSlots")
    ResultContainer mainmod$getResultSlots();

    @Accessor("player")
    Player mainmod$getPlayer();

    @Accessor("access")
    ContainerLevelAccess mainmod$getAccess();
}