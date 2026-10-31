package br.pedro.hytale.veinminer;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent;
import com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.interaction.BlockHarvestUtils;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.joml.Vector3i;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class VeinMiningSystem extends EntityEventSystem<EntityStore, BreakBlockEvent> {
    private static final int LIMIT = 32;
    private final SettingsRepository settings;
    private final Set<UUID> busy = ConcurrentHashMap.newKeySet();
    private volatile boolean closed;

    VeinMiningSystem(SettingsRepository settings) {
        super(BreakBlockEvent.class);
        this.settings = settings;
    }

    void close() {
        closed = true;
    }

    @Override
    public Query<EntityStore> getQuery() {
        return Query.and(Player.getComponentType(), PlayerRef.getComponentType());
    }

    @Override
    public void handle(int index, ArchetypeChunk<EntityStore> chunk, Store<EntityStore> store,
                       CommandBuffer<EntityStore> buffer, BreakBlockEvent event) {
        if (closed || event.isCancelled()) return;
        ItemStack tool = event.getItemInHand();
        UUID uuid = chunk.getComponent(index, PlayerRef.getComponentType()).getUuid();
        PlayerSettings config = settings.get(uuid);
        if (!usableTool(tool, config)) return;
        if (!AssetRules.allows(config, tool.getItem(), event.getBlockType())) return;
        Ref<EntityStore> player = chunk.getReferenceTo(index);
        if (!activationHeld(player, buffer, config) || !busy.add(uuid)) return;
        World world = store.getExternalData().getWorld();
        Vector3i target = event.getTargetBlock();
        var origin = new VeinTraversal.Position(target.x, target.y, target.z);
        String blockId = event.getBlockType().getId();
        // Sai do despacho do evento; observa também cancelamentos de sistemas posteriores.
        try {
            world.execute(() -> {
                try {
                    if (!canContinue(player, world.getEntityStore().getStore(), config) || event.isCancelled()
                        || !event.getTargetBlock().equals(new Vector3i(origin.x(), origin.y(), origin.z()))) return;
                    var chunks = world.getChunkStore();
                    if (blockAt(chunks, origin) != BlockType.EMPTY_ID) return;
                    var entities = world.getEntityStore().getStore();
                    var drops = new DropRedirector(entities, origin);
                    VeinTraversal.mine(origin, LIMIT,
                        p -> VeinTraversal.withinRadius(origin, p, config.radius()) && matches(chunks, p, blockId),
                        p -> breakExtra(player, entities, chunks, p, tool, blockId, drops),
                        () -> canContinue(player, entities, config)
                            && sameTool(InventoryComponent.getItemInHand(entities, player), tool, config));
                } finally {
                    busy.remove(uuid);
                }
            });
        } catch (RuntimeException error) {
            busy.remove(uuid);
            throw error;
        }
    }

    private boolean canContinue(Ref<EntityStore> player, Store<EntityStore> entities, PlayerSettings config) {
        return !closed && player.isValid() && activationHeld(player, entities, config);
    }

    private static boolean activationHeld(Ref<EntityStore> player,
            ComponentAccessor<EntityStore> entities, PlayerSettings config) {
        var component = entities.getComponent(player, MovementStatesComponent.getComponentType());
        if (component == null || component.getMovementStates() == null) return false;
        var state = component.getMovementStates();
        return config.activation().active(state.crouching, state.forcedCrouching, state.walking, state.sprinting);
    }

    private static boolean usableTool(ItemStack tool, PlayerSettings config) {
        return !ItemStack.isEmpty(tool) && config.allowsTool(tool.getItemId(), tool.isBroken());
    }

    private static boolean sameTool(ItemStack current, ItemStack original, PlayerSettings config) {
        return usableTool(current, config) && current.isEquivalentType(original)
            && current.getMaxDurability() == original.getMaxDurability()
            && current.getQualityIndex() == original.getQualityIndex();
    }

    private static int blockAt(ChunkStore chunks, VeinTraversal.Position p) {
        Ref<ChunkStore> section = chunks.getChunkSectionReferenceAtBlock(p.x(), p.y(), p.z());
        if (section == null || !section.isValid()) return -1;
        BlockSection blocks = chunks.getStore().getComponent(section, BlockSection.getComponentType());
        return blocks == null ? -1 : blocks.get(p.x(), p.y(), p.z());
    }

    private static boolean matches(ChunkStore chunks, VeinTraversal.Position p, String blockId) {
        int id = blockAt(chunks, p);
        BlockType type = id < 0 ? null : BlockType.getAssetMap().getAsset(id);
        return type != null && blockId.equals(type.getId());
    }

    private PlayerSettings settingsForPlayer(Store<EntityStore> entities, Ref<EntityStore> player) {
        return settings.get(entities.getComponent(player, PlayerRef.getComponentType()).getUuid());
    }

    private boolean breakExtra(Ref<EntityStore> player, Store<EntityStore> entities,
                                      ChunkStore chunks, VeinTraversal.Position p,
                                      ItemStack original, String blockId,
                                      ComponentAccessor<EntityStore> drops) {
        ItemStack current = InventoryComponent.getItemInHand(entities, player);
        PlayerSettings latest = settingsForPlayer(entities, player);
        if (!sameTool(current, original, latest) || !matches(chunks, p, blockId)) return false;
        BlockType block = BlockType.getAssetMap().getAsset(blockAt(chunks, p));
        if (block == null || !AssetRules.allows(latest, current.getItem(), block)) return false;
        Ref<ChunkStore> section = chunks.getChunkSectionReferenceAtBlock(p.x(), p.y(), p.z());
        if (section == null || !section.isValid()) return false;
        // Uma batida acelerada por bloco; o jogo calcula drops, desgaste e eventos canceláveis.
        // Não substituímos os blocos diretamente por ar.
        boolean broken = BlockHarvestUtils.performBlockDamage(player, player,
            new Vector3i(p.x(), p.y(), p.z()), current, null, null, false,
            1_000_000.0f, 0, false, section, drops, chunks.getStore());
        return broken && blockAt(chunks, p) == BlockType.EMPTY_ID;
    }
}
