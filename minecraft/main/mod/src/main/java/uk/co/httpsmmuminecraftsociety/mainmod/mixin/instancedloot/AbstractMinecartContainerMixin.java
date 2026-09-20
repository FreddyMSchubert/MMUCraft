package uk.co.httpsmmuminecraftsociety.mainmod.mixin.instancedloot;

import uk.co.httpsmmuminecraftsociety.mainmod.instancedloot.InstancedLootData;
import uk.co.httpsmmuminecraftsociety.mainmod.instancedloot.InstancedLootMenus;
import uk.co.httpsmmuminecraftsociety.mainmod.instancedloot.InstancedLootNbt;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecartContainer;
import net.minecraft.world.entity.vehicle.minecart.MinecartChest;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.loot.LootTable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractMinecartContainer.class)
public abstract class AbstractMinecartContainerMixin implements InstancedLootData {
    @Unique
    private boolean pil$instancedLoot;

    @Unique
    private ResourceKey<LootTable> pil$instancedLootTable;

    @Unique
    private long pil$instancedLootSeed;

    @Unique
    private final Map<UUID, NonNullList<ItemStack>> pil$playerLoot = new LinkedHashMap<>();

    @Inject(method = "createMenu(ILnet/minecraft/world/entity/player/Inventory;Lnet/minecraft/world/entity/player/Player;)Lnet/minecraft/world/inventory/AbstractContainerMenu;", at = @At("HEAD"), cancellable = true)
    private void pil$createInstancedMenu(int syncId, Inventory inventory, Player player, CallbackInfoReturnable<AbstractContainerMenu> cir) {
        if (!((Object) this instanceof MinecartChest)) {
            return;
        }

        AbstractMinecartContainer minecart = (AbstractMinecartContainer) (Object) this;
        if (InstancedLootMenus.isLootCandidate(minecart)) {
            cir.setReturnValue(InstancedLootMenus.minecartMenu(minecart, syncId, inventory, player));
        }
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void pil$loadInstancedLoot(ValueInput input, CallbackInfo ci) {
        AbstractMinecartContainer minecart = (AbstractMinecartContainer) (Object) this;
        InstancedLootNbt.load(input, this, minecart.getContainerSize());
        if (this.pil$isInstancedLoot()) {
            minecart.setContainerLootTable(null);
            minecart.setContainerLootTableSeed(0L);
            minecart.clearItemStacks();
        }
    }

    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void pil$saveInstancedLoot(ValueOutput output, CallbackInfo ci) {
        InstancedLootNbt.save(output, this);
    }

    @Override
    public boolean pil$isInstancedLoot() {
        return this.pil$instancedLoot;
    }

    @Override
    public void pil$setInstancedLoot(boolean instanced) {
        this.pil$instancedLoot = instanced;
    }

    @Override
    public ResourceKey<LootTable> pil$getInstancedLootTable() {
        return this.pil$instancedLootTable;
    }

    @Override
    public void pil$setInstancedLootTable(ResourceKey<LootTable> lootTable) {
        this.pil$instancedLootTable = lootTable;
    }

    @Override
    public long pil$getInstancedLootSeed() {
        return this.pil$instancedLootSeed;
    }

    @Override
    public void pil$setInstancedLootSeed(long seed) {
        this.pil$instancedLootSeed = seed;
    }

    @Override
    public Map<UUID, NonNullList<ItemStack>> pil$getPlayerLoot() {
        return this.pil$playerLoot;
    }

    @Override
    public void pil$markInstancedLootDirty() {
    }
}
