package uk.co.httpsmmuminecraftsociety.mainmod.mixin;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.ChiseledBookShelfBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.FakeItems;

@Mixin(ChiseledBookShelfBlockEntity.class)
public abstract class BookshelfBookStorageMixin {
    @Inject(method = "acceptsItemType", at = @At("RETURN"), cancellable = true)
    private void mainmod$acceptCustomBooks(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValue()) cir.setReturnValue(FakeItems.isBookshelfBook(stack));
    }
}
