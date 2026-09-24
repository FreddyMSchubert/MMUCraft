package dev.freddy.killshift;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;

final class ShapeState {
    final MobForm form;
    final CompoundTag viewData;
    LivingEntity view;
    int landAir;
    int abilityCooldown;
    int angryTicks;
    boolean wasSneaking;
    boolean ownsPostEffect;

    ShapeState(MobForm form, CompoundTag viewData) {
        this.form = form;
        this.viewData = viewData.copy();
        this.landAir = 300;
    }

    CompoundTag save() {
        CompoundTag data = new CompoundTag();
        data.put("form", form.save());
        data.put("view", viewData.copy());
        data.putInt("landAir", landAir);
        data.putInt("abilityCooldown", abilityCooldown);
        data.putInt("angryTicks", angryTicks);
        data.putBoolean("ownsPostEffect", ownsPostEffect);
        return data;
    }

    static ShapeState load(CompoundTag data) {
        MobForm form = MobForm.load(data.getCompoundOrEmpty("form"));
        if (form == null) return null;
        ShapeState state = new ShapeState(form, data.getCompoundOrEmpty("view"));
        state.landAir = data.getIntOr("landAir", 300);
        state.abilityCooldown = data.getIntOr("abilityCooldown", 0);
        state.angryTicks = data.getIntOr("angryTicks", 0);
        state.ownsPostEffect = data.getBooleanOr("ownsPostEffect", false);
        return state;
    }
}
