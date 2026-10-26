package uk.co.httpsmmuminecraftsociety.mainmod.advancements;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.SharedConstants;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.advancements.predicates.SlotsPredicate;
import net.minecraft.advancements.triggers.InventoryChangeTrigger;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderOwner;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EntityEquipment;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.SlotProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerListener;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.SlotRanges;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import uk.co.httpsmmuminecraftsociety.mainmod.enchantment.GearEnchantmentSanitizer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

public final class ExplosiveFireCheck {
    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        // Standalone checks boot vanilla before the mod initializer; Fabric leaves this registry writable during mod init.
        var frozen = MappedRegistry.class.getDeclaredField("frozen");
        frozen.setAccessible(true);
        frozen.setBoolean(BuiltInRegistries.DATA_COMPONENT_PREDICATE_TYPE, false);
        FireworkLoadedCrossbowPredicate.register();
        Identifier predicateId = Identifier.fromNamespaceAndPath("mainmod", "charged_firework");
        var bind = Holder.Reference.class.getDeclaredMethod("bindValue", Object.class);
        bind.setAccessible(true);
        bind.invoke(BuiltInRegistries.DATA_COMPONENT_PREDICATE_TYPE.get(predicateId).orElseThrow(),
                FireworkLoadedCrossbowPredicate.TYPE);
        var registries = VanillaRegistries.createWorldLookup();
        BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(registries).forEach(components -> components.apply());
        JsonObject json;
        try (var reader = Files.newBufferedReader(Path.of(args[0]))) {
            json = JsonParser.parseReader(reader).getAsJsonObject();
        }
        var ops = RegistryOps.create(JsonOps.INSTANCE, registries);
        Advancement advancement = Advancement.CODEC.parse(ops, json).getOrThrow();
        assert advancement.criteria().size() == 3;
        assert advancement.requirements().requirements().equals(List.of(List.of("crossbow", "inventory", "offhand")));

        Map<String, SlotsPredicate> checks = new HashMap<>();
        for (String name : List.of("crossbow", "inventory", "offhand")) {
            AdvancementProgress progress = new AdvancementProgress();
            progress.update(advancement.requirements());
            assert !progress.isDone();
            assert progress.grantProgress(name) && progress.isDone();
            var criterion = (InventoryChangeTrigger.TriggerInstance) advancement.criteria().get(name).triggerInstance();
            assert criterion.items().isEmpty(); // An unrelated changed stack must still check the held crossbow.
            assert criterion.matches(null, new ItemStack(Items.DIRT), 0, 0, 0);
            JsonObject slots = json.getAsJsonObject("criteria").getAsJsonObject(name)
                    .getAsJsonObject("conditions").getAsJsonObject("player")
                    .getAsJsonObject("predicate").getAsJsonObject("slots");
            checks.put(name, SlotsPredicate.CODEC.parse(ops, slots).getOrThrow());
        }

        ItemStack unloaded = new ItemStack(Items.CROSSBOW);
        ItemStack arrow = crossbow(new ItemStack(Items.ARROW));
        ItemStack rocket = new ItemStack(Items.FIREWORK_ROCKET);
        rocket.set(DataComponents.CUSTOM_NAME, Component.literal("custom rocket"));
        ItemStack firework = crossbow(rocket);
        ItemStack multishot = new ItemStack(Items.CROSSBOW);
        multishot.set(DataComponents.CHARGED_PROJECTILES,
                ChargedProjectiles.ofNonEmpty(List.of(new ItemStack(Items.ARROW), rocket, new ItemStack(Items.ARROW))));
        for (int position : new int[] {0, 9, SlotRanges.nameToIds("weapon.offhand").slots().getInt(0)}) {
            assert !matches(checks, unloaded, position);
            assert !matches(checks, arrow, position);
            assert matches(checks, firework, position);
            assert matches(checks, multishot, position);
        }

        SimpleContainer contents = new SimpleContainer(2);
        contents.setItem(0, firework);
        for (int opening = 0; opening < 3; opening++) {
            TestMenu menu = new TestMenu(contents);
            menu.broadcastChanges(); // The menu-opening baseline before the listener is added.
            Counter listener = new Counter();
            menu.addSlotListener(listener);
            assert listener.calls == 0;
            menu.broadcastChanges();
            assert listener.calls == 0;
            contents.setItem(1, new ItemStack(Items.DIRT));
            menu.broadcastChanges();
            assert listener.calls == 1;
            contents.setItem(1, ItemStack.EMPTY);
        }
        TestMenu inventoryMenu = new TestMenu(contents);
        Counter loginListener = new Counter();
        inventoryMenu.addSlotListener(loginListener); // Initial inventory loading has no baseline.
        assert loginListener.calls == 1;

        int oldCallbacks = 0;
        int newCallbacks = 0;
        Inventory playerInventory = new Inventory(null, new EntityEquipment());
        for (int slot = 0; slot < 36; slot++) {
            playerInventory.getNonEquipmentItems().set(slot, new ItemStack(Items.DIRT));
        }
        playerInventory.getNonEquipmentItems().set(0, firework);
        for (boolean furnace : new boolean[] {false, true}) {
            for (int opening = 0; opening < 3; opening++) {
                AbstractContainerMenu oldMenu = openMenu(furnace, playerInventory);
                Counter oldListener = new Counter(playerInventory);
                oldMenu.addSlotListener(oldListener);
                assert oldListener.calls == 36;
                oldCallbacks += oldListener.calls;

                AbstractContainerMenu newMenu = openMenu(furnace, playerInventory);
                newMenu.broadcastChanges();
                Counter newListener = new Counter(playerInventory);
                newMenu.addSlotListener(newListener);
                assert newListener.calls == 0;
                newCallbacks += newListener.calls;
                playerInventory.getNonEquipmentItems().set(35, new ItemStack(Items.STONE));
                newMenu.broadcastChanges();
                assert newListener.calls == 1;
                playerInventory.getNonEquipmentItems().set(35, new ItemStack(Items.DIRT));
            }
        }
        AbstractContainerMenu componentMenu = openMenu(false, playerInventory);
        componentMenu.broadcastChanges();
        Counter componentListener = new Counter(playerInventory);
        componentMenu.addSlotListener(componentListener);
        playerInventory.getNonEquipmentItems().get(0).set(DataComponents.CHARGED_PROJECTILES,
                ChargedProjectiles.ofNonEmpty(List.of(new ItemStack(Items.ARROW))));
        componentMenu.broadcastChanges();
        assert componentListener.calls == 1;

        AbstractContainerMenu sanitizedMenu = openMenu(true, playerInventory);
        ItemStack disabledBook = new ItemStack(Items.ENCHANTED_BOOK);
        var disabled = Holder.Reference.createStandAlone(new HolderOwner<Enchantment>() {},
                ResourceKey.create(Registries.ENCHANTMENT, Identifier.fromNamespaceAndPath("nova_structures", "outreach")));
        ItemEnchantments.Mutable enchantments = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        enchantments.set(disabled, 1);
        disabledBook.set(DataComponents.STORED_ENCHANTMENTS, enchantments.toImmutable());
        playerInventory.getNonEquipmentItems().set(0, disabledBook);
        sanitizedMenu.broadcastChanges();
        GearEnchantmentSanitizer.sanitizeMenu(sanitizedMenu);
        Counter sanitizedListener = new Counter(playerInventory);
        sanitizedMenu.addSlotListener(sanitizedListener);
        assert sanitizedListener.calls == 1;
        System.out.printf("Explosive Fire checks passed. Unchanged chest/furnace-layout inventory callbacks: %d before, %d after.%n",
                oldCallbacks, newCallbacks);
    }

    private static ItemStack crossbow(ItemStack projectile) {
        ItemStack crossbow = new ItemStack(Items.CROSSBOW);
        crossbow.set(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.ofNonEmpty(List.of(projectile)));
        return crossbow;
    }

    private static boolean matches(Map<String, SlotsPredicate> checks, ItemStack stack, int position) {
        SlotProvider provider = slot -> SlotAccess.of(
                () -> slot == position ? stack : ItemStack.EMPTY, ignored -> {});
        return checks.values().stream().anyMatch(check -> check.matches(provider));
    }

    private static AbstractContainerMenu openMenu(boolean furnace, Inventory inventory) {
        return furnace
                ? new TestMenu(new SimpleContainer(3), inventory)
                : ChestMenu.threeRows(1, inventory, new SimpleContainer(27));
    }

    private static final class Counter implements ContainerListener {
        int calls;
        private final Inventory inventory;

        Counter() {
            this(null);
        }

        Counter(Inventory inventory) {
            this.inventory = inventory;
        }

        @Override
        public void slotChanged(AbstractContainerMenu menu, int slot, ItemStack stack) {
            if (inventory == null || menu.getSlot(slot).container == inventory) calls++;
        }

        @Override
        public void dataChanged(AbstractContainerMenu menu, int slot, int value) {}
    }

    private static final class TestMenu extends AbstractContainerMenu {
        TestMenu(SimpleContainer contents) {
            this(contents, null);
        }

        TestMenu(SimpleContainer contents, Inventory inventory) {
            super(null, 1);
            for (int slot = 0; slot < contents.getContainerSize(); slot++) {
                addSlot(new Slot(contents, slot, 0, 0));
            }
            if (inventory != null) {
                for (int slot = 0; slot < 36; slot++) addSlot(new Slot(inventory, slot, 0, 0));
            }
        }

        @Override
        public ItemStack quickMoveStack(Player player, int slot) {
            return ItemStack.EMPTY;
        }

        @Override
        public boolean stillValid(Player player) {
            return true;
        }
    }
}
