package uk.co.httpsmmuminecraftsociety.mainmod.enchantment;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.enchantment.Enchantment;

import java.util.Set;

/** DnT enchantments that overlap with this server's mechanics. */
public final class DisabledDnTEnchantments {
    private static final Set<ResourceKey<Enchantment>> DISABLED = Set.of(
            key("outreach"),
            key("swift_soar"),
            key("traveler")
    );

    private DisabledDnTEnchantments() {
    }

    public static boolean contains(Holder<Enchantment> enchantment) {
        return enchantment.unwrapKey().map(DISABLED::contains).orElse(false);
    }

    private static ResourceKey<Enchantment> key(String path) {
        return ResourceKey.create(Registries.ENCHANTMENT,
                Identifier.fromNamespaceAndPath("nova_structures", path));
    }
}
