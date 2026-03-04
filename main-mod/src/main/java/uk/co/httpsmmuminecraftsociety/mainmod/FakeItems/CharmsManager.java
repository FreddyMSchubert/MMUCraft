package uk.co.httpsmmuminecraftsociety.mainmod.FakeItems;

import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.Level;
import uk.co.httpsmmuminecraftsociety.mainmod.FakeItems.FakeItemDefs.CharmFakeItem;
import uk.co.httpsmmuminecraftsociety.mainmod.FakeItems.FakeItemDefs.FakeItem;
import uk.co.httpsmmuminecraftsociety.mainmod.FakeItems.charms.def.Charm;
import uk.co.httpsmmuminecraftsociety.mainmod.FakeItems.charms.def.EquippedTickCallbackCharm;
import uk.co.httpsmmuminecraftsociety.mainmod.FakeItems.charms.def.TickCallbackCharm;
import uk.co.httpsmmuminecraftsociety.mainmod.FakeItems.charms.def.UseCallbackCharm;
import uk.co.httpsmmuminecraftsociety.mainmod.MainMod;

import java.util.List;

public class CharmsManager
{
    private static ItemStack triggerEquippedTickCallbacks(ItemStack stack, ServerPlayer player, ServerLevel level) {
        if (!stack.has(DataComponents.CUSTOM_MODEL_DATA)) return stack;
        CustomModelData cmd = stack.getOrDefault(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(List.of(), List.of(), List.of(), List.of()));
        if (cmd.strings().isEmpty() || cmd.strings().getFirst().isEmpty()) return stack;
        for (FakeItem def : FakeItems.ALL)
        {
            if (!(def instanceof CharmFakeItem charmFakeItem)) continue;
            Charm charm = charmFakeItem.getCharm();
            if (!(charm instanceof EquippedTickCallbackCharm equippedCharm)) return stack;
            if (!cmd.strings().getFirst().startsWith(charm.id())) continue;
            stack = equippedCharm.equippedTick(stack, player, level);
        }

        return stack;
    }
    public static void onPlayerTick(ServerLevel server) {
        for (ServerPlayer player : server.players()) {
            // pretick even if unequipped
            for (FakeItem def : FakeItems.ALL) {
                if (!(def instanceof CharmFakeItem charmFakeItem)) continue;
                if (!(charmFakeItem.getCharm() instanceof TickCallbackCharm tickCharm)) continue;
                tickCharm.onTick(player, server);
            }

            // tick actually present stuff
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                ItemStack current = player.getItemBySlot(slot);
                if (current.isEmpty()) continue;

                ItemStack updated = triggerEquippedTickCallbacks(current, player, server);
                if (updated == null) updated = ItemStack.EMPTY;

                if (updated != current && !ItemStack.isSameItemSameComponents(current, updated)) {
                    player.setItemSlot(slot, updated);
                }
            }
        }
    }

    public static InteractionResult onItemUse(Level level, Player player, InteractionHand interactionHand) {
        ItemStack stack = player.getItemInHand(interactionHand);
        if (!stack.has(DataComponents.CUSTOM_MODEL_DATA)) return InteractionResult.PASS;
        CustomModelData cmd = stack.getOrDefault(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(List.of(), List.of(), List.of(), List.of()));
        if (cmd.strings().isEmpty() || cmd.strings().getFirst().isEmpty()) return InteractionResult.PASS;
        for (FakeItem def : FakeItems.ALL)
        {
            if (!(def instanceof CharmFakeItem charmFakeItem)) continue;
            Charm charm = charmFakeItem.getCharm();
            if (!cmd.strings().getFirst().startsWith(charm.id())) continue;
            if (!(charm instanceof UseCallbackCharm useCharm)) continue;
            stack = useCharm.onUse(stack, (ServerPlayer) player, (ServerLevel) level);
            player.setItemInHand(interactionHand, stack);
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }
}
