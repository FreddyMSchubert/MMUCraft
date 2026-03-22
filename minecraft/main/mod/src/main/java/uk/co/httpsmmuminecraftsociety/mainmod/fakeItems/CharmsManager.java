package uk.co.httpsmmuminecraftsociety.mainmod.fakeItems;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.CraftingStaffCharm;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.EnderChestStaffCharm;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.SculkPhialCharm;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.consumable.PotionOfDisplacementCharm;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.consumable.PotionOfReturningCharm;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.UmbrellaCharm;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.def.*;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.equippable.*;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.fakeItemDefs.CharmItemFeature;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.fakeItemDefs.EquippableCharmItemFeature;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.fakeItemDefs.FakeItem;

import java.util.*;

public class CharmsManager
{
    private static final Map<Integer, Charm> CHARMS_REGISTRY = Map.ofEntries(
            Map.entry(1, new CraftingStaffCharm()),
            Map.entry(2, new EnderChestStaffCharm()),
            Map.entry(3, new HeartCharm(0)),
            Map.entry(4, new HeartCharm(1)),
            Map.entry(5, new HeartCharm(2)),
            Map.entry(6, new HeartCharm(3)),
            Map.entry(7, new RunningShoesCharm()),
            Map.entry(8, new CandleOfTheDeepCharm()),
            Map.entry(9, new HikingBootsCharm(0)),
            Map.entry(10, new HikingBootsCharm(1)),
            Map.entry(11, new HikingBootsCharm(2)),
            Map.entry(12, new GiantsBootsCharm()),
            Map.entry(13, new LeprechaunBootsCharm()),
            Map.entry(14, new MermaidScalesCharm()),
            Map.entry(15, new StriderShalesCharm()),
            Map.entry(16, new ExtendoGripCharm()),
            Map.entry(17, new BunnyPajamasCharm()),
            Map.entry(18, new KittyPajamasCharm()),
            Map.entry(19, new SpiderPajamasCharm()),
            Map.entry(20, new CaveSpiderPajamasCharm()),
            Map.entry(21, new GoopHandCharm()),
            Map.entry(22, new WingedShoesCharm(0)),
            Map.entry(23, new WingedShoesCharm(1)),
            Map.entry(24, new WingedShoesCharm(2)),
            Map.entry(25, new PotionOfReturningCharm()),
            Map.entry(26, new UmbrellaCharm()),
            Map.entry(27, new PotionOfDisplacementCharm()),
            Map.entry(28, new SculkPhialCharm())
    );
    public static Charm charmFromId(int charmId) {
        if (!CHARMS_REGISTRY.containsKey(charmId)) return null;
        return CHARMS_REGISTRY.get(charmId);
    }

    public static final String CHARM_ABILITES_COMPOUND_ID = "charm_abilities";

    public static List<FakeItem> getAbilitiesFromItemStack(ItemStack stack) {
        if (!stack.has(DataComponents.CUSTOM_DATA)) return List.of();

        CompoundTag nbt = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        Optional<int[]> abilities = nbt.getIntArray(CHARM_ABILITES_COMPOUND_ID);

        return abilities
                .map(ints -> Arrays.stream(ints)
                        .mapToObj(FakeItems.CHARM_EFFECT_ID_MAP::get)
                        .filter(Objects::nonNull)
                        .filter(item -> item.getFeature(CharmItemFeature.class) != null)
                        .toList())
                .orElse(List.of());
    }
    public static boolean hasAbility(ItemStack stack, Class<? extends Charm> charmClass) {
        List<FakeItem> abilities = getAbilitiesFromItemStack(stack);
        for (FakeItem ability : abilities) {
            CharmItemFeature cif = ability.getFeature(CharmItemFeature.class);
            if (cif.charm().getClass() == charmClass) return true;
        }
        return false;
    }

    private static void tickActiveUseCharms(ServerPlayer player, ServerLevel level) {
        if (!player.isUsingItem()) {
            return;
        }

        ItemStack activeStack = player.getActiveItem();
        if (activeStack.isEmpty()) {
            return;
        }

        ConsumableCallbacksCharm activeCharm = null;
        for (FakeItem fi : getAbilitiesFromItemStack(activeStack)) {
            Charm charm = fi.getFeature(CharmItemFeature.class).charm();
            if (charm instanceof ConsumableCallbacksCharm callbacksCharm) {
                activeCharm = callbacksCharm;
                break;
            }
        }
        if (activeCharm == null) {
            return;
        }

        Consumable consumable = activeStack.get(DataComponents.CONSUMABLE);
        if (consumable == null) {
            return;
        }

        int elapsedTicks = consumable.consumeTicks() - player.getUseItemRemainingTicks();

        if (player.getUseItemRemainingTicks() <= 1) {
            ItemStack result = activeCharm.onConsumeFinished(activeStack.copy(), player, level, elapsedTicks);
            if (result == null) {
                result = ItemStack.EMPTY;
            }

            player.setItemInHand(player.getUsedItemHand(), result);
            player.stopUsingItem();
            return;
        }

        activeCharm.onConsumeTick(activeStack, player, level, elapsedTicks);
    }
    private static ItemStack triggerEquippedTickCallbacks(ItemStack stack, ServerPlayer player, ServerLevel level, EquipmentSlot slot) {
        List<FakeItem> charmFakeItems = getAbilitiesFromItemStack(stack);
        for (FakeItem fi : charmFakeItems) {
            EquippableCharmItemFeature ecif = fi.getFeature(EquippableCharmItemFeature.class);
            if (ecif != null && ecif.equippable().slot() != slot) continue;

            CharmItemFeature cif = fi.getFeature(CharmItemFeature.class);
            if (cif == null || !(cif.charm() instanceof EquippedTickCallbackCharm equippedCharm)) continue;
            stack = equippedCharm.equippedTick(stack, player, level);
        }
        return stack;
    }
    public static void onPlayerTick(ServerLevel server) {
        for (ServerPlayer player : server.players()) {
            // pretick even if unequipped
            for (Map.Entry<Integer, FakeItem> def : FakeItems.CHARM_EFFECT_ID_MAP.entrySet()) {
                CharmItemFeature cif = def.getValue().getFeature(CharmItemFeature.class);
                if (cif == null || !(cif.charm() instanceof TickCallbackCharm tickCallbackCharm)) continue;
                tickCallbackCharm.onTick(player, server);
            }

            // tick actually present stuff
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                ItemStack current = player.getItemBySlot(slot);
                if (current.isEmpty()) continue;

                ItemStack updated = triggerEquippedTickCallbacks(current, player, server, slot);
                if (updated == null) updated = ItemStack.EMPTY;

                if (updated != current && !ItemStack.isSameItemSameComponents(current, updated)) {
                    player.setItemSlot(slot, updated);
                }
            }

            tickActiveUseCharms(player, server);
        }
    }

    public static InteractionResult onItemUse(Level level, Player player, InteractionHand interactionHand) {
        ItemStack stack = player.getItemInHand(interactionHand);
        List<FakeItem> charmFakeItems = getAbilitiesFromItemStack(stack);
        if (charmFakeItems.isEmpty()) return null;

        ItemStack newStack = stack.copy();
        for (FakeItem cfi : charmFakeItems) {
            CharmItemFeature charm = cfi.getFeature(CharmItemFeature.class);
            if (charm == null || !(charm.charm() instanceof UseCallbackCharm useCallbackCharm)) continue;
            newStack = useCallbackCharm.onUse(newStack, (ServerPlayer) player, (ServerLevel) level);
        }

        if (!ItemStack.matches(stack, newStack))
            return InteractionResult.SUCCESS.heldItemTransformedTo(newStack);
        else
            return null;
    }
}
