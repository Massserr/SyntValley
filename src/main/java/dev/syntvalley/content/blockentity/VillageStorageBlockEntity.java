package dev.syntvalley.content.blockentity;

import dev.syntvalley.application.port.ResourceSource;
import dev.syntvalley.bootstrap.ServerRuntimeManager;
import dev.syntvalley.bootstrap.SyntValleyServerRuntime;
import dev.syntvalley.domain.identity.VillageId;
import dev.syntvalley.domain.resource.ResourceKey;
import dev.syntvalley.registry.ModBlockEntities;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** A village-owned storage. Only explicitly linked, loaded storages count toward the resource ledger. */
public final class VillageStorageBlockEntity extends BlockEntity implements ResourceSource {
    private static final int SLOTS = 27;
    private static final String TAG_VILLAGE_ID = "village_id";
    private static final String TAG_ITEMS = "items";

    private final SimpleContainer container = new SimpleContainer(SLOTS);
    private VillageId villageId;
    private boolean registered;

    public VillageStorageBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.VILLAGE_STORAGE.get(), pos, state);
        container.addListener(changed -> setChanged());
    }

    public Optional<VillageId> villageId() {
        return Optional.ofNullable(villageId);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel serverLevel) {
            ensureRegistered(serverLevel);
        }
    }

    @Override
    public void setRemoved() {
        if (registered && villageId != null && level instanceof ServerLevel serverLevel) {
            ServerRuntimeManager.find(serverLevel.getServer())
                    .ifPresent(runtime -> runtime.unregisterStorage(villageId, this));
            registered = false;
        }
        super.setRemoved();
    }

    /** Consumes the player's pending Core selection and binds this storage to it. */
    public boolean tryLink(ServerLevel level, Player player) {
        SyntValleyServerRuntime runtime = ServerRuntimeManager.getOrCreate(level.getServer());
        Optional<VillageId> bound = runtime.bindConsole(player.getUUID(), level.getGameTime());
        if (bound.isEmpty()) {
            return false;
        }
        bindToVillage(level, bound.orElseThrow());
        return true;
    }

    /** Server-side link of this storage to a village, shared by the pending-selection flow. */
    public void bindToVillage(ServerLevel level, VillageId villageId) {
        this.villageId = Objects.requireNonNull(villageId, "villageId");
        setChanged();
        ensureRegistered(level);
    }

    /** Adds items to the store programmatically (stocking / staging); returns whatever did not fit. */
    public ItemStack deposit(ItemStack stack) {
        return container.addItem(stack);
    }

    public void openStorage(ServerPlayer player) {
        player.openMenu(new SimpleMenuProvider(
                (containerId, inventory, opener) -> ChestMenu.threeRows(containerId, inventory, container),
                Component.translatable("block.syntvalley.village_storage")));
    }

    public void dropContents(ServerLevel level) {
        Containers.dropContents(level, worldPosition, container);
    }

    @Override
    public Map<ResourceKey, Integer> snapshotCounts() {
        Map<ResourceKey, Integer> counts = new LinkedHashMap<>();
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
            counts.merge(new ResourceKey(id.toString()), stack.getCount(), Integer::sum);
        }
        return counts;
    }

    @Override
    public int withdraw(ResourceKey key, int amount) {
        if (amount <= 0) {
            return 0;
        }
        int removed = 0;
        for (int slot = 0; slot < container.getContainerSize() && removed < amount; slot++) {
            ItemStack stack = container.getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
            if (!new ResourceKey(id.toString()).equals(key)) {
                continue;
            }
            removed += container.removeItem(slot, amount - removed).getCount();
        }
        return removed;
    }

    private void ensureRegistered(ServerLevel level) {
        if (!registered && villageId != null) {
            ServerRuntimeManager.getOrCreate(level.getServer()).registerStorage(villageId, this);
            registered = true;
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (villageId != null) {
            tag.putUUID(TAG_VILLAGE_ID, villageId.value());
        }
        tag.put(TAG_ITEMS, container.createTag(registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        villageId = tag.hasUUID(TAG_VILLAGE_ID) ? new VillageId(tag.getUUID(TAG_VILLAGE_ID)) : null;
        container.fromTag(tag.getList(TAG_ITEMS, Tag.TAG_COMPOUND), registries);
    }
}
