package uk.co.httpsmmuminecraftsociety.mainmod.magic;

import java.time.Instant;

public final class MagicFoodEffectsCheck {
    private MagicFoodEffectsCheck() {}

    public static void main(String[] args) {
        assert MagicFoodEffects.canReplace(null, MagicFoodEffects.Effect.SHOP_20);
        assert !MagicFoodEffects.canReplace(MagicFoodEffects.Effect.SHOP_20, MagicFoodEffects.Effect.SHOP_20);
        assert MagicFoodEffects.canReplace(MagicFoodEffects.Effect.SHOP_20, MagicFoodEffects.Effect.SHOP_30);
        assert !MagicFoodEffects.canReplace(MagicFoodEffects.Effect.SHOP_30, MagicFoodEffects.Effect.SHOP_20);
        assert MagicFoodEffects.canReplace(MagicFoodEffects.Effect.SUNDAY_MONEY, MagicFoodEffects.Effect.SHOP_20);
        assert MagicFoodEffects.canReplace(MagicFoodEffects.Effect.FISH_COMMON, MagicFoodEffects.Effect.FISH_RARE);
        assert !MagicFoodEffects.Effect.FISH_LEGENDARY.category.equals(MagicFoodEffects.Effect.FISH_MYTHICAL.category);
        assert !MagicFoodEffects.canReplace(MagicFoodEffects.Effect.FISH_MYTHICAL, MagicFoodEffects.Effect.FISH_MYTHICAL);
        long summer = Instant.parse("2026-06-01T12:00:00Z").toEpochMilli();
        long winter = Instant.parse("2026-11-15T12:00:00Z").toEpochMilli();
        assert MagicFoodEffects.expiresAt(summer) == Instant.parse("2026-06-01T23:00:00Z").toEpochMilli();
        assert MagicFoodEffects.expiresAt(winter) == Instant.parse("2026-11-16T00:00:00Z").toEpochMilli();
    }
}
