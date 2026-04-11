package uk.co.httpsmmuminecraftsociety.mainmod.mixin.anvilRework;

import net.minecraft.world.inventory.AbstractContainerMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(AbstractContainerMenu.class)
public interface AbstractContainerMenuInvoker {
    @Invoker("broadcastChanges")
    void mainmod$broadcastChanges();

    @Invoker("broadcastFullState")
    void mainmod$broadcastFullState();

    @Invoker("sendAllDataToRemote")
    void mainmod$sendAllDataToRemote();
}
