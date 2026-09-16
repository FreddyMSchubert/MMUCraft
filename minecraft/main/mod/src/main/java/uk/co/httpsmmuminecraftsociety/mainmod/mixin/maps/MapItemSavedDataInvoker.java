package uk.co.httpsmmuminecraftsociety.mainmod.mixin.maps;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(MapItemSavedData.class)
public interface MapItemSavedDataInvoker {
    @Invoker("<init>")
    static MapItemSavedData mainmod$create(int centerX, int centerZ, byte scale, boolean trackingPosition,
                                           boolean unlimitedTracking, boolean locked, ResourceKey<Level> dimension) {
        throw new AssertionError();
    }
}
