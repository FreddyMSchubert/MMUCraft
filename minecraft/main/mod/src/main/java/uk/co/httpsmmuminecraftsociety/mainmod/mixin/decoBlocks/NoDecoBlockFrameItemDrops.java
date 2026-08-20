package uk.co.httpsmmuminecraftsociety.mainmod.mixin.decoBlocks;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.DecoBlocksManager;

@Mixin(ItemFrame.class)
public class NoDecoBlockFrameItemDrops {
    @Redirect(
            method = "dropItem(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/Entity;Z)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/decoration/ItemFrame;spawnAtLocation(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/world/entity/item/ItemEntity;",
                    ordinal = 0
            )
    )
    private ItemEntity mainmod$skipDecoBlockFrameDrop(ItemFrame frame, ServerLevel level, ItemStack stack) {
        if (frame.entityTags().contains(DecoBlocksManager.DECO_BLOCK_FRAME_TAG)
                && (stack.is(Items.ITEM_FRAME) || stack.is(Items.GLOW_ITEM_FRAME))) {
            return null;
        }

        return frame.spawnAtLocation(level, stack);
    }

    @Redirect(
            method = "dropItem(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/Entity;Z)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/decoration/ItemFrame;spawnAtLocation(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/world/entity/item/ItemEntity;",
                    ordinal = 1
            )
    )
    private ItemEntity mainmod$restoreDecoBlockFrameItemDrop(ItemFrame frame, ServerLevel level, ItemStack stack) {
        if (frame.entityTags().contains(DecoBlocksManager.DECO_BLOCK_FRAME_TAG)) {
            DecoBlocksManager.restoreCustomNameFromFrame(stack);
        }

        return frame.spawnAtLocation(level, stack);
    }
}
