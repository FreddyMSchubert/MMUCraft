package uk.co.httpsmmuminecraftsociety.mainmod.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.level.block.CrafterBlock;
import net.minecraft.world.level.block.entity.CrafterBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import uk.co.httpsmmuminecraftsociety.mainmod.money.DabloonInteractionLog;

@Mixin(CrafterBlock.class)
public abstract class DabloonCrafterMixin {
    @Inject(method = "dispenseFrom", at = @At("HEAD"))
    private void mainmod$recordCurrencyCraft(BlockState state, ServerLevel level, BlockPos pos, CallbackInfo ci) {
        if (!(level.getBlockEntity(pos) instanceof CrafterBlockEntity crafter)) return;
        CraftingInput input = crafter.asCraftInput();
        var recipe = CrafterBlock.getPotentialResults(level, input);
        if (recipe.isEmpty()) return;
        ItemStack output = recipe.get().value().assemble(input);
        if (output.isEmpty()) return;

        int inputCoins = 0;
        boolean coin = DabloonInteractionLog.kind(output).equals("coin");
        boolean wallet = DabloonInteractionLog.kind(output).equals("wallet");
        StringBuilder ingredients = new StringBuilder();
        for (ItemStack stack : input.items()) {
            String kind = DabloonInteractionLog.kind(stack);
            if (kind.isEmpty()) continue;
            if (kind.equals("coin")) {
                coin = true;
                inputCoins += DabloonInteractionLog.value(stack);
            } else wallet = true;
            if (!ingredients.isEmpty()) ingredients.append(", ");
            ingredients.append(DabloonInteractionLog.itemId(stack)).append(" x").append(stack.getCount());
            if (kind.equals("wallet")) ingredients.append(" (balance ").append(DabloonInteractionLog.value(stack)).append(")");
        }
        if (!coin && !wallet) return;
        int amount = Math.max(inputCoins,
                DabloonInteractionLog.kind(output).equals("coin") ? DabloonInteractionLog.value(output) : 0);
        DabloonInteractionLog.recordWorld("craft", coin && wallet ? "mixed" : coin ? "coin" : "wallet",
                DabloonInteractionLog.itemId(output), output.getCount(), amount,
                level.dimension().identifier().toString(), pos.getX(), pos.getY(), pos.getZ(),
                "Crafter recipe=" + recipe.get().id() + "; inputs: " + ingredients + "; output: "
                        + DabloonInteractionLog.itemId(output) + " x" + output.getCount()
                        + " (value " + DabloonInteractionLog.value(output) + ")");
    }
}
