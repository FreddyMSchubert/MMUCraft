package uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import org.jspecify.annotations.Nullable;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.FakeItems;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.consumable.InvisiCarrotCharm;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.consumable.PotionOfDisplacementCharm;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.consumable.PotionOfInsomniaCharm;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.consumable.PotionOfResonanceCharm;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.consumable.PotionOfReturningCharm;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.def.*;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.equippable.*;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.held.*;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.unlockers.KnowledgeBookCharm;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.unlockers.ShopUnlockBookCharm;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.fakeItemDefs.CharmItemFeature;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.fakeItemDefs.EquippableCharmItemFeature;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.fakeItemDefs.FakeItem;
import uk.co.httpsmmuminecraftsociety.mainmod.utils.Tuple;

import java.util.*;

public class CharmsManager
{
    private static final Map<Integer, Charm> CHARMS_REGISTRY = Map.ofEntries(
            Map.entry(1, new CraftingStaffCharm()),
            Map.entry(2, new EnderChestStaffCharm()),
            Map.entry(3, new HeartCharm()),
            Map.entry(7, new RunningShoesCharm()),
            Map.entry(8, new CandleOfTheDeepCharm()),
            Map.entry(9, new HikingBootsCharm()),
            Map.entry(12, new GiantsBootsCharm()),
            Map.entry(13, new LeprechaunBootsCharm()),
            Map.entry(16, new ExtendoGripCharm()),
            Map.entry(17, new BunnyPajamasCharm()),
            Map.entry(18, new KittyPajamasCharm()),
            Map.entry(19, new SpiderPajamasCharm()),
            Map.entry(20, new CaveSpiderPajamasCharm()),
            Map.entry(21, new GoopHandCharm()),
            Map.entry(22, new WingedShoesCharm()),
            Map.entry(25, new PotionOfReturningCharm()),
            Map.entry(26, new UmbrellaCharm()),
            Map.entry(27, new PotionOfDisplacementCharm()),
            Map.entry(28, new SculkPhialCharm()),
            Map.entry(29, new PotionOfInsomniaCharm()),
            Map.entry(30, new WalletCharm()),
            Map.entry(31, new VeinminerCharm()),
            Map.entry(32, new VitalityMendingCharm()),
            Map.entry(33, new InvisiCarrotCharm()),
            Map.entry(34, new FarmingBootsCharm()),
            Map.entry(35, new SawBeltCharm()),
            Map.entry(36, new KnowledgeBookCharm()),
            Map.entry(37, new LuckyCharm()),
            Map.entry(38, new ScubaTankCharm()),
            Map.entry(39, new EnduranceCharm()),
            Map.entry(40, ShopUnlockBookCharm.magicBook()),
            Map.entry(41, ShopUnlockBookCharm.fashionBook()),
            Map.entry(42, new JokeCharm()),
            Map.entry(43, new PickaxeHeaterCharm()),
            Map.entry(BackpackCharm.Tier.LEATHER.charmId(), new BackpackCharm(BackpackCharm.Tier.LEATHER.rows())),
            Map.entry(BackpackCharm.Tier.INGOT.charmId(), new BackpackCharm(BackpackCharm.Tier.INGOT.rows())),
            Map.entry(BackpackCharm.Tier.MAGIC.charmId(), new BackpackCharm(BackpackCharm.Tier.MAGIC.rows())),
            Map.entry(BackpackCharm.Tier.BEJEWELED.charmId(), new BackpackCharm(BackpackCharm.Tier.BEJEWELED.rows())),
            Map.entry(BackpackCharm.Tier.WITHERED.charmId(), new BackpackCharm(BackpackCharm.Tier.WITHERED.rows())),
            Map.entry(BackpackCharm.Tier.ENDLESS.charmId(), new BackpackCharm(BackpackCharm.Tier.ENDLESS.rows())),
            Map.entry(50, new KangarooBootsCharm()),
            Map.entry(51, new ObamiumPyramidCharm()),
            Map.entry(52, new PotionOfResonanceCharm())
    );
    public static Charm charmFromId(int charmId) {
        return CHARMS_REGISTRY.get(charmId);
    }

    public record CharmInstance(
            FakeItem fakeItem,
            CharmItemFeature feature,
            int level
    ) {
        public boolean isBroken() {
            return level <= 0;
        }

        public Charm charm() {
            return feature.charm();
        }

        public int charmId() {
            return feature.charmId();
        }
    }

    // Some generic utils

    public static List<CharmInstance> getCharmInstances(ItemStack stack) {
        return CharmStackData.getStoredCharms(stack).stream()
                .map(CharmsManager::resolveCharmInstance)
                .filter(Objects::nonNull)
                .toList();
    }
    public static List<Tuple<ItemStack, CharmInstance>> getPlayerCharmInstances(ServerPlayer player) {
        List<Tuple<ItemStack, CharmInstance>> charms = new ArrayList<>();
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack stack = player.getItemBySlot(slot);
            if (stack.isEmpty()) {
                continue;
            }
            for (CharmInstance instance : getCharmInstances(stack))
                charms.add(new Tuple<>(stack, instance));
        }
        return charms;
    }
    private static CharmInstance resolveCharmInstance(StoredCharmData storedCharm) {
        FakeItem fakeItem = FakeItems.CHARM_ID_MAP.get(storedCharm.charmId());
        if (fakeItem == null) {
            return null;
        }

        CharmItemFeature feature = fakeItem.getFeature(CharmItemFeature.class);
        if (feature == null) {
            return null;
        }

        return new CharmInstance(fakeItem, feature, storedCharm.level());
    }
    public static boolean hasAbility(ItemStack stack, Class<? extends Charm> charmClass) {
        for (CharmInstance ability : getCharmInstances(stack)) {
            if (ability.feature().charm().getClass() == charmClass) return true;
        }
        return false;
    }
    public static int getPlayerCharmLevel(ServerPlayer player, Class<? extends Charm> charmClass) {
        for (Tuple<ItemStack, CharmInstance> ability : getPlayerCharmInstances(player)) {
            if (ability.getB().feature().charm().getClass() == charmClass) return ability.getB().level();
        }
        return 0;
    }

    private static void tickActiveUseCharms(ServerPlayer player, ServerLevel level) {
        if (!player.isUsingItem()) return;

        ItemStack activeStack = player.getActiveItem();
        if (activeStack.isEmpty()) return;

        CharmInstance activeCharm = null;
        for (CharmInstance instance : getCharmInstances(activeStack)) {
            if (instance.isBroken()) continue;
            if (instance.charm() instanceof ConsumableCallbacksCharm) {
                activeCharm = instance;
                break;
            }
        }
        if (activeCharm == null) return;

        Consumable consumable = activeStack.get(DataComponents.CONSUMABLE);
        if (consumable == null) return;

        ConsumableCallbacksCharm callbacksCharm = (ConsumableCallbacksCharm) activeCharm.charm();
        int elapsedTicks = consumable.consumeTicks() - player.getUseItemRemainingTicks();

        if (player.getUseItemRemainingTicks() <= 1) {
            callbacksCharm.onConsumeFinished(
                    activeStack,
                    player,
                    level,
                    elapsedTicks,
                    activeCharm.level()
            );

            player.stopUsingItem();
            return;
        }

        callbacksCharm.onConsumeTick(activeStack, player, level, elapsedTicks, activeCharm.level());
    }
    public static void onPlayerTick(ServerLevel server) {
        for (ServerPlayer player : server.players()) {
            // uniquipped tick
            for (Map.Entry<Integer, FakeItem> entry : FakeItems.CHARM_ID_MAP.entrySet()) {
                CharmItemFeature feature = entry.getValue().getFeature(CharmItemFeature.class);
                if (feature == null || !(feature.charm() instanceof TickCallbackCharm tickCallbackCharm)) {
                    continue;
                }
                tickCallbackCharm.onTick(player, server);
            }

            // equipped tick
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                ItemStack stack = player.getItemBySlot(slot);
                if (stack.isEmpty()) {
                    continue;
                }

                for (CharmInstance instance : getCharmInstances(stack)) {
                    if (instance.isBroken()) continue;
                    EquippableCharmItemFeature equippable = instance.fakeItem().getFeature(EquippableCharmItemFeature.class);
                    if (equippable != null && equippable.equippable().slot() != slot) continue;
                    if (instance.charm() instanceof EquippedTickCallbackCharm equippedCharm) {
                        equippedCharm.equippedTick(stack, player, server, instance.level());
                    }
                }
            }

            // actively used tick
            tickActiveUseCharms(player, server);
        }
    }

    // redirect buncha callbacks into charms

    public static InteractionResult onItemUse(Level level, Player player, InteractionHand interactionHand) {
        ItemStack stack = player.getItemInHand(interactionHand);
        List<CharmInstance> instances = getCharmInstances(stack);
        if (instances.isEmpty()) return null;

        for (CharmInstance instance : instances) {
            if (instance.isBroken()) continue;
            if (!(instance.charm() instanceof UseCallbackCharm useCallbackCharm)) continue;

            return useCallbackCharm.onUse(
                    stack,
                    (ServerPlayer) player,
                    (ServerLevel) level,
                    instance.level()
            );
        }
        return null;
    }

    public static void onAfterBlockBreak(Level level, Player player, BlockPos blockPos, BlockState blockState, @Nullable BlockEntity blockEntity)
    {
        if (!(player instanceof ServerPlayer)) return;
        if (!(level instanceof ServerLevel)) return;
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack stack = player.getItemBySlot(slot);
            if (stack.isEmpty()) {
                continue;
            }

            for (CharmInstance instance : getCharmInstances(stack))
            {
                if (instance.isBroken()) continue;
                EquippableCharmItemFeature equippable = instance.fakeItem().getFeature(EquippableCharmItemFeature.class);
                if (equippable != null && equippable.equippable().slot() != slot) continue;
                if (instance.charm() instanceof AfterBlockBreakCallbackCharm afterBreakCharm) {
                    afterBreakCharm.afterBlockBreak(stack, (ServerPlayer) player, (ServerLevel) level, blockPos, blockState, instance.level());
                }
            }
        }
    }
    public static InteractionResult onUseEntity(Player player, Level level, InteractionHand interactionHand, Entity entity, @Nullable EntityHitResult entityHitResult)
    {
        ItemStack stack = player.getItemInHand(interactionHand);
        List<CharmInstance> instances = getCharmInstances(stack);
        if (instances.isEmpty()) return InteractionResult.PASS;

        for (CharmInstance instance : instances) {
            if (instance.isBroken()) continue;
            if (!(instance.charm() instanceof UseEntityCallbackCharm useEntityCallbackCharm)) continue;

            InteractionResult result =  useEntityCallbackCharm.onUseEntity(
                    stack,
                    player,
                    level,
                    interactionHand,
                    entity,
                    entityHitResult,
                    instance.level()
            );

            if (result == null || result == InteractionResult.PASS) {
                continue;
            }

            return result;
        }

        return InteractionResult.PASS;
    }
    public static InteractionResult onUseBlock(Player player, Level level, InteractionHand interactionHand, BlockHitResult blockHitResult)
    {
        if (!(player instanceof ServerPlayer)) return InteractionResult.PASS;
        if (!(level instanceof ServerLevel)) return InteractionResult.PASS;
        for (Tuple<ItemStack, CharmInstance> instance : getPlayerCharmInstances((ServerPlayer) player)) {
            if (instance.getB().isBroken()) continue;
            if (!(instance.getB().charm() instanceof UseOnBlockCallbackCharm useOnBlockCallbackCharm)) continue;

            InteractionResult result =  useOnBlockCallbackCharm.onUseOnBlock(
                    instance.getA(),
                    (ServerPlayer) player,
                    (ServerLevel) level,
                    interactionHand,
                    blockHitResult,
                    instance.getB().level()
            );

            if (result == null || result == InteractionResult.PASS) {
                continue;
            }

            return result;
        }

        return InteractionResult.PASS;
    }
}
