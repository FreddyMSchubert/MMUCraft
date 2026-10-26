package uk.co.httpsmmuminecraftsociety.mainmod.advancements;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.advancements.predicates.SingleComponentItemPredicate;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.component.predicates.DataComponentPredicate;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ChargedProjectiles;

public record FireworkLoadedCrossbowPredicate() implements SingleComponentItemPredicate<ChargedProjectiles> {
    private static final Codec<FireworkLoadedCrossbowPredicate> CODEC =
            MapCodec.unit(new FireworkLoadedCrossbowPredicate()).codec();
    static final DataComponentPredicate.Type<FireworkLoadedCrossbowPredicate> TYPE =
            new DataComponentPredicate.ConcreteType<>(CODEC);

    public static void register() {
        Registry.register(BuiltInRegistries.DATA_COMPONENT_PREDICATE_TYPE,
                Identifier.fromNamespaceAndPath("mainmod", "charged_firework"),
                TYPE);
    }

    @Override
    public DataComponentType<ChargedProjectiles> componentType() {
        return DataComponents.CHARGED_PROJECTILES;
    }

    @Override
    public boolean matches(ChargedProjectiles projectiles) {
        return projectiles.contains(Items.FIREWORK_ROCKET);
    }
}
