package uk.co.httpsmmuminecraftsociety.mainmod.hopper;

import com.mojang.math.Transformation;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import uk.co.httpsmmuminecraftsociety.mainmod.mixin.fishing.DisplayEntityAccessor;

public final class UpsideDownHoppers {
    private static final String MARKER_TAG = "mainmod_upside_down_hopper_marker";

    private UpsideDownHoppers() {}

    public static void ensureMarker(ServerLevel level, BlockPos pos) {
        if (!markers(level, pos).isEmpty()) return;

        Display.ItemDisplay marker = new Display.ItemDisplay(EntityTypes.ITEM_DISPLAY, level);
        marker.setPos(pos.getX() + 0.5, pos.getY() + 0.95, pos.getZ() + 0.5);
        marker.setNoGravity(true);
        marker.setSilent(true);
        marker.setPermanentlyInvulnerable(true);
        marker.addTag(MARKER_TAG);
        marker.setItemStack(new ItemStack(Items.HOPPER));
        marker.setItemTransform(ItemDisplayContext.FIXED);

        DisplayEntityAccessor display = (DisplayEntityAccessor) marker;
        display.mainmod$setTransformation(new Transformation(
                new Vector3f(),
                new Quaternionf().rotateX((float) Math.PI),
                new Vector3f(0.45F, 0.45F, 0.45F),
                new Quaternionf()
        ));
        display.mainmod$setShadowRadius(0.0F);
        display.mainmod$setWidth(1.0F);
        display.mainmod$setHeight(1.0F);
        level.addFreshEntity(marker);
    }

    public static void removeMarker(ServerLevel level, BlockPos pos) {
        markers(level, pos).forEach(Display.ItemDisplay::discard);
    }

    private static java.util.List<Display.ItemDisplay> markers(ServerLevel level, BlockPos pos) {
        return level.getEntitiesOfClass(Display.ItemDisplay.class, new AABB(pos),
                display -> display.blockPosition().equals(pos)
                        && display.entityTags().contains(MARKER_TAG));
    }
}
