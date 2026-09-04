package uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.glider;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.fabricmc.fabric.api.item.v1.EnchantingContext;
import net.fabricmc.fabric.api.util.TriState;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import uk.co.httpsmmuminecraftsociety.mainmod.MainMod;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.FakeItems;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.CharmLevelDefinition;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.fakeItemDefs.CharmItemFeature;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.fakeItemDefs.EquippableCharmItemFeature;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.fakeItemDefs.FakeItem;
import uk.co.httpsmmuminecraftsociety.mainmod.modifiers.anvilRework.recipes.RepairSameItem;
import uk.co.httpsmmuminecraftsociety.mainmod.recipe.CombineCharmorRecipe;
import uk.co.httpsmmuminecraftsociety.mainmod.recipe.FakeShapedCraftingRecipe;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class GliderCheck {
    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        var registries = VanillaRegistries.createLookup();
        var registryField = MainMod.class.getDeclaredField("registries");
        registryField.setAccessible(true);
        registryField.set(null, registries);
        BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(registries).forEach(components -> components.apply());
        Path project = Path.of(args[0]);
        var definition = JsonParser.parseString(Files.readString(project.resolve("../data/data/items/charm/glider/item.json"))).getAsJsonObject();
        var charm = definition.remove("charm").getAsJsonObject();
        var feature = new CharmItemFeature(new GliderCharm(), charm.get("charmId").getAsInt(),
                charm.get("minLevel").getAsInt(), charm.get("maxLevel").getAsInt(), definition.get("title").getAsString(),
                Map.of(1, CharmLevelDefinition.of(charm.getAsJsonArray("levels").get(0).getAsJsonObject(), "glider")));
        FakeItem base = FakeItem.fromJson(definition, "glider/item.json");
        FakeItem item = new FakeItem(base.title(), base.id(), base.rarity(), base.maxStackSize(), base.fireproof(),
                base.tooltip(), base.baseItem(), List.of(feature));
        FakeItems.ID_MAP = Map.of(item.id(), item);
        FakeItems.CHARM_ID_MAP = Map.of(GliderCharm.CHARM_ID, item);
        ItemStack glider = item.createItemStack();
        ItemStack elytra = new ItemStack(Items.ELYTRA);
        assert GliderCharm.isGlider(glider) && !GliderCharm.isGlider(elytra);
        assert glider.getMaxDamage() == 432 && glider.getMaxDamage() == elytra.getMaxDamage();
        assert glider.get(DataComponents.EQUIPPABLE).equals(elytra.get(DataComponents.EQUIPPABLE));
        assert glider.get(DataComponents.EQUIPPABLE).swappable();
        assert glider.get(DataComponents.REPAIRABLE).isValidRepairItem(new ItemStack(Items.PHANTOM_MEMBRANE));
        assert item.getFeature(EquippableCharmItemFeature.class) == null;
        assert !item.getFeature(CharmItemFeature.class).hasNextLevel(1);
        assert LivingEntity.canGlideUsing(glider, EquipmentSlot.CHEST);
        glider.setDamageValue(431);
        assert !LivingEntity.canGlideUsing(glider, EquipmentSlot.CHEST);
        glider.setDamageValue(0);
        var repair = new RepairSameItem();
        assert repair.matches(glider, glider.copy());
        assert !repair.matches(glider, elytra) && !repair.matches(elytra, glider);
        assert !new CombineCharmorRecipe().matches(CraftingInput.of(2, 1,
                List.of(new ItemStack(Items.DIAMOND_CHESTPLATE), glider)), null);
        var enchantments = registries.lookupOrThrow(Registries.ENCHANTMENT);
        assert GliderCharm.allowEnchanting(enchantments.getOrThrow(Enchantments.MENDING), glider, EnchantingContext.ACCEPTABLE) == TriState.TRUE;
        assert GliderCharm.allowEnchanting(enchantments.getOrThrow(Enchantments.UNBREAKING), glider, EnchantingContext.ACCEPTABLE) == TriState.FALSE;
        assert GliderCharm.allowEnchanting(enchantments.getOrThrow(Enchantments.BINDING_CURSE), glider, EnchantingContext.ACCEPTABLE) == TriState.FALSE;
        assert GliderCharm.allowEnchanting(enchantments.getOrThrow(Enchantments.UNBREAKING), elytra, EnchantingContext.ACCEPTABLE) == TriState.DEFAULT;
        var recipe = FakeShapedCraftingRecipe.CODEC.codec().parse(JsonOps.INSTANCE, JsonParser.parseString(
                Files.readString(project.resolve("src/main/resources/data/mainmod/recipe/glider.json")))).getOrThrow();
        var grid = CraftingInput.of(3, 2, List.of(new ItemStack(Items.PHANTOM_MEMBRANE), new ItemStack(Items.PHANTOM_MEMBRANE),
                new ItemStack(Items.PHANTOM_MEMBRANE), new ItemStack(Items.STICK), ItemStack.EMPTY, new ItemStack(Items.STICK)));
        assert recipe.matches(grid, null) && GliderCharm.isGlider(recipe.assemble(grid));
        double limit = 20;
        for (int tick = 0; tick < 200; tick++) {
            assert Math.abs(limit - Math.max(17, 20 - tick * 0.02)) < 1.0E-9;
            limit = GliderFlight.decaySpeedLimit(limit);
        }
        assert Math.abs(GliderFlight.clampSpeed(new Vec3(2, 3, 4), 17).length() * 20 - 17) < 1.0E-9;
        assert GliderFlight.clampSpeed(Vec3.ZERO, 17).equals(Vec3.ZERO);
        assert Updrafts.heatRange(Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, false)) == 0;
        assert Updrafts.heatRange(Blocks.FIRE.defaultBlockState()) == 20;
        assert Updrafts.heatRange(Blocks.SOUL_CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true)) == 50;
        assert Updrafts.LAVA_RANGE == 35;
        var caught = new Updrafts.Updraft(64, 84, 100 + Updrafts.CARRY_TICKS);
        assert Math.abs(caught.liftAt(64, 100) - 0.15) < 1.0E-9;
        assert Math.abs(caught.liftAt(74, 100) - 0.0275) < 1.0E-9;
        assert Math.abs(caught.liftAt(82, 100) - 0.01014) < 1.0E-9;
        assert Math.abs(caught.liftAt(83.999, 100) - 0.01) < 1.0E-9;
        assert caught.liftAt(63, 100) <= Updrafts.SOURCE_ACCELERATION;
        assert caught.liftAt(74, 119) > 0;
        assert caught.liftAt(74, 120) == 0;
        assert caught.liftAt(84, 101) == 0;
        assert caught.liftAt(85, 101) == 0;
        assert caught.liftAt(80, 101) < caught.liftAt(74, 100);
        assert new Updrafts.Updraft(64, 80, 120).liftAt(80, 101) == 0;
        var blocks = new HashMap<BlockPos, BlockState>();
        BlockGetter level = new BlockGetter() {
            public BlockEntity getBlockEntity(BlockPos pos) { return null; }
            public BlockState getBlockState(BlockPos pos) { return blocks.getOrDefault(pos, Blocks.AIR.defaultBlockState()); }
            public FluidState getFluidState(BlockPos pos) { return getBlockState(pos).getFluidState(); }
            public int getHeight() { return 384; }
            public int getMinY() { return -64; }
        };
        for (var source : Map.of(Blocks.FIRE, 20, Blocks.CAMPFIRE, 20, Blocks.SOUL_FIRE, 50, Blocks.SOUL_CAMPFIRE, 50).entrySet()) {
            for (int sourceY : new int[]{-32, 64}) {
                blocks.clear();
                blocks.put(new BlockPos(0, sourceY, 0), source.getKey().defaultBlockState());
                double ceilingY = sourceY + source.getValue();
                var low = Updrafts.findAt(level, new Vec3(0.5, sourceY + 1, 0.5), 0.6, 100);
                var high = Updrafts.findAt(level, new Vec3(0.5, ceilingY - 0.2, 0.5), 0.6, 110);
                assert low != null && high != null;
                assert low.sourceY() == sourceY && low.ceilingY() == ceilingY && high.ceilingY() == ceilingY;
                assert high.expiresAt() == 130;
                assert Updrafts.findAt(level, new Vec3(0.5, ceilingY, 0.5), 0.6, 110) == null;
                assert Updrafts.findAt(level, new Vec3(1.5, sourceY + 1, 0.5), 0.6, 110) == null;
                blocks.put(new BlockPos(0, sourceY + 10, 0), Blocks.STONE.defaultBlockState());
                assert Math.abs(Updrafts.findAt(level, new Vec3(0.5, sourceY + 1, 0.5), 0.6, 100).ceilingY() - (sourceY + 9.4)) < 1.0E-9;
            }
        }
        var state = new GliderFlight.FlightState();
        Vec3 rising = new Vec3(0.3, 0.8, 0.4);
        GliderFlight.applyUpdraft(state, caught, rising, 74, 100);
        assert state.ascentGraceUntil == 0;
        assert GliderFlight.applyUpdraft(state, null, rising, 74, 119).y > rising.y;
        assert Math.abs(GliderFlight.applyUpdraft(state, null, rising, 74, 120).y - rising.y * GliderFlight.UPWARD_DAMPING) < 1.0E-9;
        assert state.updraft == null;
        Vec3 nearTop = GliderFlight.applyUpdraft(state, caught, rising, 83.8, 101);
        assert Math.abs(nearTop.y - 0.2) < 1.0E-9 && nearTop.x == rising.x && nearTop.z == rising.z;
        assert GliderFlight.applyUpdraft(state, null, rising, 84, 102).y == 0 && state.updraft == null;
        assert GliderFlight.applyUpdraft(state, caught, new Vec3(0, -0.2, 0), 85, 103).y == -0.2;
        state.ascentGraceUntil = 150;
        assert GliderFlight.applyUpdraft(state, caught, rising, 84, 104).y == rising.y;
        System.out.println("Glider checks passed: item, recipe, repairs, enchantments, speed, and heat sources.");
    }
}
