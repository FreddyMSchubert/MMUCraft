package uk.co.httpsmmuminecraftsociety.mainmod.mixin;

import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.FakeItems;

/** Fake items must never stand in for the heart of the sea in a conduit. */
@Mixin(ShapedRecipe.class)
public abstract class ConduitRecipeMixin {
    @Shadow @Final private ItemStackTemplate result;

    @Inject(method = "matches(Lnet/minecraft/world/item/crafting/CraftingInput;Lnet/minecraft/world/level/Level;)Z",
            at = @At("HEAD"), cancellable = true)
    private void mainmod$rejectFakeHeartForConduit(
            CraftingInput input, Level level, CallbackInfoReturnable<Boolean> cir) {
        if (result.item().value() != Items.CONDUIT) return;
        if (input.items().stream().anyMatch(stack -> stack.is(Items.HEART_OF_THE_SEA)
                && FakeItems.hasKnownFakeItemId(stack))) {
            cir.setReturnValue(false);
        }
    }
}
