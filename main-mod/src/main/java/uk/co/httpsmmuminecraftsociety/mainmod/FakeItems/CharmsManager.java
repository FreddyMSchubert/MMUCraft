package uk.co.httpsmmuminecraftsociety.mainmod.FakeItems;

import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.CustomModelData;
import uk.co.httpsmmuminecraftsociety.mainmod.Utils;

import java.util.List;

public class CharmsManager
{
    private static ItemStack tickItemStackIfIsCharm(ItemStack stack, ServerPlayer player, ServerLevel level) {
        if (!stack.has(DataComponents.CUSTOM_DATA)) return stack;
        CustomData cd = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        if (!cd.copyTag().getBooleanOr(Utils.TAG_TICK, false)) return stack;

        if (!stack.has(DataComponents.CUSTOM_MODEL_DATA)) return stack;
        CustomModelData cmd = stack.getOrDefault(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(List.of(), List.of(), List.of(), List.of()));
        if (cmd.strings().isEmpty() || cmd.strings().getFirst().isEmpty()) return stack;
        for (FakeItems.FakeItemDef def : FakeItems.ALL)
        {
            if (def.charm().isEmpty()) continue;
            if (!cmd.strings().getFirst().startsWith(def.charm().get().id())) continue;
            stack = def.charm().get().equippedTick(stack, player, level);
        }

        return stack;
    }
    public static void onPlayerTick(ServerLevel server) {
        for (ServerPlayer player : server.players()) {
            // pretick even if unequipped
            for (FakeItems.FakeItemDef def : FakeItems.ABILITY_ITEMS) {
                def.charm().get().tick(player, server);
            }

            // tick actually present stuff
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                ItemStack current = player.getItemBySlot(slot);
                if (current.isEmpty()) continue;

                ItemStack updated = tickItemStackIfIsCharm(current, player, server);
                if (updated == null) updated = ItemStack.EMPTY;

                if (updated != current && !ItemStack.isSameItemSameComponents(current, updated)) {
                    player.setItemSlot(slot, updated);
                }
            }
        }
    }
}
