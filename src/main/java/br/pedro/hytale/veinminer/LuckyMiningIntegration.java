package br.pedro.hytale.veinminer;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.OrderPriority;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.component.system.ISystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent;
import com.hypixel.hytale.server.core.plugin.PluginManager;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import static br.pedro.hytale.veinminer.VeinTraversal.Position;

final class LuckyMiningIntegration {
    private record Snapshot(Position origin, int oreId, int[] blocks) {}

    private final Map<BreakBlockEvent, Snapshot> snapshots = Collections.synchronizedMap(new WeakHashMap<>());
    private final Class<? extends ISystem<EntityStore>> luckySystem;
    private final MiningVisuals visuals;

    private LuckyMiningIntegration(Class<? extends ISystem<EntityStore>> luckySystem, MiningVisuals visuals) {
        this.luckySystem = luckySystem;
        this.visuals = visuals;
    }

    static void register(ComponentRegistryProxy<EntityStore> registry, MiningVisuals visuals, HytaleLogger logger) {
        var plugin = PluginManager.get().getPlugin(MiningVisuals.LUCKY);
        if (plugin == null) return;
        try {
            var system = plugin.getClass().getClassLoader().loadClass("com.buuz135.luckymining.BreakBlockEventSystem")
                .asSubclass(EntityEventSystem.class);
            @SuppressWarnings("unchecked")
            var typed = (Class<? extends ISystem<EntityStore>>) (Class<?>) system;
            if (!EntityStore.REGISTRY.hasSystemClass(typed)) {
                logger.atWarning().log("Lucky Mining's block event system is unavailable.");
                return;
            }
            var integration = new LuckyMiningIntegration(typed, visuals);
            registry.registerSystem(integration.new Before());
            registry.registerSystem(integration.new After());
            logger.atInfo().log("Lucky Mining ore highlights attached.");
        } catch (ClassNotFoundException | LinkageError | RuntimeException e) {
            logger.atWarning().withCause(e).log("Could not attach Lucky Mining ore highlights.");
        }
    }

    private abstract class Observer extends EntityEventSystem<EntityStore, BreakBlockEvent> {
        private final Order order;

        Observer(Order order) {
            super(BreakBlockEvent.class);
            this.order = order;
        }

        @Override
        public Query<EntityStore> getQuery() {
            return PlayerRef.getComponentType();
        }

        @Override
        public Set<Dependency<EntityStore>> getDependencies() {
            return Set.of(new SystemDependency<>(order, luckySystem, OrderPriority.CLOSEST));
        }
    }

    private final class Before extends Observer {
        Before() { super(Order.BEFORE); }

        @Override
        public void handle(int index, ArchetypeChunk<EntityStore> chunk, Store<EntityStore> store,
                           CommandBuffer<EntityStore> buffer, BreakBlockEvent event) {
            var ore = event.getBlockType();
            if (event.isCancelled() || !AssetRules.ore(ore)) return;
            var target = event.getTargetBlock();
            var origin = new Position(target.x, target.y, target.z);
            int oreId = BlockType.getAssetMap().getIndex(ore.getId());
            var chunks = store.getExternalData().getWorld().getChunkStore();
            int[] previous = new int[27];
            for (int i = 0; i < previous.length; i++) previous[i] = VeinMiningSystem.blockAt(chunks, neighbor(origin, i));
            snapshots.put(event, new Snapshot(origin, oreId, previous));
        }
    }

    private final class After extends Observer {
        After() { super(Order.AFTER); }

        @Override
        public void handle(int index, ArchetypeChunk<EntityStore> chunk, Store<EntityStore> store,
                           CommandBuffer<EntityStore> buffer, BreakBlockEvent event) {
            var snapshot = snapshots.remove(event);
            if (snapshot == null) return;
            var world = store.getExternalData().getWorld();
            var chunks = world.getChunkStore();
            for (int i = 0; i < snapshot.blocks().length; i++) {
                if (snapshot.blocks()[i] < 0 || snapshot.blocks()[i] == snapshot.oreId()) continue;
                Position position = neighbor(snapshot.origin(), i);
                if (VeinMiningSystem.blockAt(chunks, position) == snapshot.oreId()) {
                    visuals.markLuckyOre(world, position, snapshot.oreId());
                }
            }
        }
    }

    // Lucky Mining 1.0.9 places the ore in the immediate neighborhood of the broken block.
    private static Position neighbor(Position origin, int index) {
        return new Position(origin.x() + index / 9 - 1, origin.y() + index / 3 % 3 - 1, origin.z() + index % 3 - 1);
    }
}
