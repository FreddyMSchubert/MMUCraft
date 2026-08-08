package uk.co.httpsmmuminecraftsociety.mainmod.dailies.tasks;

import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.enchantment.Enchantment;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailyTaskEvent;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailyTargetId;

public final class EnchantWithEnchantmentTask extends CountedTask {
    private final String enchantment;
    private final String enchantmentName;

    public EnchantWithEnchantmentTask(ResourceKey<Enchantment> enchantment, String enchantmentName) {
        this(enchantment, enchantmentName, "📖", 7.0D);
    }

    public EnchantWithEnchantmentTask(ResourceKey<Enchantment> enchantment, String enchantmentName, double reward) {
        this(enchantment, enchantmentName, "📖", reward);
    }

    public EnchantWithEnchantmentTask(ResourceKey<Enchantment> enchantment, String enchantmentName, String emoji, double reward) {
        super("enchant:" + DailyTargetId.of(enchantment), emoji, 1, 1, reward, "Enchanted", "item");
        this.enchantment = DailyTargetId.of(enchantment);
        this.enchantmentName = enchantmentName;
    }

    @Override protected String name(int count) { return enchantmentName + " Enthusiast"; }
    @Override protected String description(int count) { return "Enchant " + count + " items with " + enchantmentName + " at an enchanting table."; }
    @Override protected boolean matches(JsonObject task, DailyTaskEvent event) {
        return event.type() == DailyTaskEvent.Type.ENCHANT_AT_TABLE && event.subject().equals(enchantment);
    }
    @Override protected void addTaskData(JsonObject task, java.util.Random random) { task.addProperty("enchantment", enchantment); }
}
