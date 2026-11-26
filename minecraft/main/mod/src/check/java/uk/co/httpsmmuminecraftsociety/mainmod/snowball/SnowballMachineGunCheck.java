package uk.co.httpsmmuminecraftsociety.mainmod.snowball;

import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.CustomModelData;

import java.util.List;

public final class SnowballMachineGunCheck {
    public static void main(String[] args) {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        var registries = VanillaRegistries.createWorldLookup();
        BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(registries).forEach(components -> components.apply());

        ItemStack gun = new ItemStack(Items.COMMAND_BLOCK);
        SnowballMachineGun.configure(gun);
        assert gun.getUseAnimation() == ItemUseAnimation.CROSSBOW;
        assert gun.getUseDuration(null) == 72000;
        gun.set(DataComponents.CUSTOM_MODEL_DATA,
                new CustomModelData(List.of(), List.of(false, true), List.of("snowball-machine-gun"), List.of()));
        assert SnowballMachineGun.isGun(gun);
        assert !SnowballMachineGun.isGun(new ItemStack(Items.CROSSBOW));
        assert !SnowballMachineGun.isGun(new ItemStack(Items.COMMAND_BLOCK));
        assert SnowballMachineGun.loadFromOffhand(gun, new ItemStack(Items.DIRT, 64)) == 0;

        ItemStack partial = new ItemStack(Items.SNOWBALL, 7);
        assert SnowballMachineGun.loadFromOffhand(gun, partial) == 7;
        assert partial.isEmpty();
        for (int i = 0; i < 7; i++)
            assert SnowballMachineGun.loadFromOffhand(gun, new ItemStack(Items.SNOWBALL, 16)) == 16;

        ItemStack last = new ItemStack(Items.SNOWBALL, 16);
        assert SnowballMachineGun.loadFromOffhand(gun, last) == 9;
        assert last.getCount() == 7;
        assert gun.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)
                .copyTag().getIntOr("mainmod_snowball_ammo", 0) == 128;
        assert SnowballMachineGun.loadFromOffhand(gun, last) == 0;
        assert Boolean.TRUE.equals(gun.get(DataComponents.CUSTOM_MODEL_DATA).getBoolean(1));
        SnowballMachineGun.release(gun, null, null, 0);
        assert Boolean.FALSE.equals(gun.get(DataComponents.CUSTOM_MODEL_DATA).getBoolean(1));
    }
}
