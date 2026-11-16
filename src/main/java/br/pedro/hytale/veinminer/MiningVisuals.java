package br.pedro.hytale.veinminer;

import com.hypixel.hytale.common.plugin.PluginIdentifier;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.math.matrix.Matrix4dUtil;
import com.hypixel.hytale.protocol.DebugShape;
import com.hypixel.hytale.protocol.packets.player.DisplayDebug;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.modules.debug.DebugUtils;
import com.hypixel.hytale.server.core.plugin.PluginManager;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.TargetUtil;
import org.joml.Matrix4d;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.Map;
import java.util.WeakHashMap;

import static br.pedro.hytale.veinminer.VeinTraversal.Position;

final class MiningVisuals extends TickingSystem<EntityStore> {
    static final PluginIdentifier LUCKY = new PluginIdentifier("Buuz135", "LuckyMining");
    private static final long FRAME_NANOS = 100_000_000;
    private static final float FRAME_SECONDS = 0.14f;
    private static final float LUCKY_SECONDS = 3f;
    private static final int LUCKY_DISTANCE = 24;
    private static final double OVERLAY_DEPTH = .20;
    private static final Vector3fc GREEN = new Vector3f(0.18f, 1f, 0.28f);
    private final SettingsRepository settings;
    private final Map<World, WorldVisuals> worlds = Collections.synchronizedMap(new WeakHashMap<>());

    private static final class WorldVisuals {
        long nextFrame;
        final Map<Position, LuckyMark> lucky = new HashMap<>();
    }

    private record LuckyMark(long created, int blockId) {}

    MiningVisuals(SettingsRepository settings) {
        this.settings = settings;
    }

    static boolean luckyAvailable() {
        var plugin = PluginManager.get().getPlugin(LUCKY);
        return plugin != null && plugin.isEnabled();
    }

    void markLuckyOre(World world, Position position, int blockId) {
        var marks = worldVisuals(world).lucky;
        if (marks.size() < 128 || marks.containsKey(position)) {
            marks.put(position, new LuckyMark(System.nanoTime(), blockId));
        }
    }

    private WorldVisuals worldVisuals(World world) {
        return worlds.computeIfAbsent(world, ignored -> new WorldVisuals());
    }

    @Override
    public void tick(float dt, int index, Store<EntityStore> store) {
        World world = store.getExternalData().getWorld();
        if (world.getPlayerRefs().isEmpty()) return;
        var visuals = worldVisuals(world);
        long now = System.nanoTime();
        if (now < visuals.nextFrame) return;
        visuals.nextFrame = now + FRAME_NANOS;
        var chunks = world.getChunkStore();
        visuals.lucky.entrySet().removeIf(entry -> {
            var mark = entry.getValue();
            return (now - mark.created()) * 1e-9 >= LUCKY_SECONDS
                || VeinMiningSystem.blockAt(chunks, entry.getKey()) != mark.blockId();
        });
        for (PlayerRef player : world.getPlayerRefs()) {
            var ref = player.getReference();
            if (ref == null || !ref.isValid() || ref.getStore() != store) continue;
            PlayerSettings config = settings.get(player.getUuid());
            if (config.veinOutline()) drawVein(world, store, player, config);
            if (config.luckyOutline()) drawLucky(store, player, visuals, now, config.luckyThroughBlocks());
        }
    }

    private static void drawVein(World world, Store<EntityStore> store, PlayerRef player, PlayerSettings config) {
        var ref = player.getReference();
        if (!VeinMiningSystem.activationHeld(ref, store, config)) return;
        var tool = InventoryComponent.getItemInHand(store, ref);
        if (!VeinMiningSystem.usableTool(tool, config)) return;
        var target = TargetUtil.getTargetBlock(ref, 5.0, store);
        if (target == null) return;
        var origin = new Position(target.x, target.y, target.z);
        var chunks = world.getChunkStore();
        int id = VeinMiningSystem.blockAt(chunks, origin);
        if (id <= BlockType.EMPTY_ID) return;
        BlockType type = BlockType.getAssetMap().getAsset(id);
        if (type == null || !AssetRules.allows(config, tool.getItem(), type)) return;
        var edges = new HashSet<Edge>();
        addEdges(edges, origin);
        VeinTraversal.mine(origin, VeinMiningSystem.LIMIT,
            p -> VeinTraversal.withinRadius(origin, p, config.radius()) && VeinMiningSystem.blockAt(chunks, p) == id,
            p -> { addEdges(edges, p); return true; }, () -> true);
        var projection = config.veinThroughBlocks() ? Projection.from(player, store) : null;
        for (var edge : edges) drawEdge(player, edge, DebugUtils.COLOR_WHITE, 1f, 0.014, projection);
    }

    private static void drawLucky(Store<EntityStore> store, PlayerRef player, WorldVisuals visuals,
                                  long now, boolean throughBlocks) {
        if (visuals.lucky.isEmpty()) return;
        var projection = throughBlocks ? Projection.from(player, store) : null;
        var eye = player.getTransform().getPosition();
        visuals.lucky.forEach((p, mark) -> {
            if (eye.distanceSquared(p.x() + .5, p.y() + .5, p.z() + .5) > LUCKY_DISTANCE * LUCKY_DISTANCE) return;
            float age = (now - mark.created()) * 1e-9f;
            float opacity = Math.clamp(Math.min(age / .35f, (LUCKY_SECONDS - age) / .7f), 0f, 1f);
            if (opacity <= 0) return;
            var edges = new HashSet<Edge>();
            addEdges(edges, p);
            for (var edge : edges) drawEdge(player, edge, GREEN, opacity, 0.028, projection);
        });
    }

    private enum Axis { X, Y, Z }

    private record Edge(int x, int y, int z, Axis axis) {}

    private static void addEdges(Set<Edge> edges, Position block) {
        for (int a = 0; a <= 1; a++) {
            for (int b = 0; b <= 1; b++) {
                edges.add(new Edge(block.x(), block.y() + a, block.z() + b, Axis.X));
                edges.add(new Edge(block.x() + a, block.y(), block.z() + b, Axis.Y));
                edges.add(new Edge(block.x() + a, block.y() + b, block.z(), Axis.Z));
            }
        }
    }

    private record Projection(Vector3d eye, Vector3d forward) {
        static Projection from(PlayerRef player, Store<EntityStore> store) {
            var look = TargetUtil.getLook(player.getReference(), store);
            return new Projection(look.getPosition(), look.getDirection());
        }
    }

    private static void drawEdge(PlayerRef player, Edge edge, Vector3fc color, float opacity,
                                 double thickness, Projection projection) {
        double x = edge.axis() == Axis.X ? 1 : 0;
        double y = edge.axis() == Axis.Y ? 1 : 0;
        double z = edge.axis() == Axis.Z ? 1 : 0;
        double centerX = edge.x() + x * .5;
        double centerY = edge.y() + y * .5;
        double centerZ = edge.z() + z * .5;
        double scale = 1;
        if (projection != null) {
            var eye = projection.eye();
            var forward = projection.forward();
            double dx = centerX - eye.x;
            double dy = centerY - eye.y;
            double dz = centerZ - eye.z;
            double depth = forward.dot(dx, dy, dz);
            if (depth <= .05 || dx * dx + dy * dy + dz * dz > depth * depth * 12) return;

            // Uniformly scaling about the eye preserves perspective while bringing the edge
            // in front of nearby terrain. The server only has the first-person eye transform.
            scale = Math.min(1, OVERLAY_DEPTH / depth);
            centerX = eye.x + dx * scale;
            centerY = eye.y + dy * scale;
            centerZ = eye.z + dz * scale;
        }
        var transform = new Matrix4d().translation(centerX, centerY, centerZ)
            .scale((x + thickness) * scale, (y + thickness) * scale, (z + thickness) * scale);
        player.getPacketHandler().writeNoCache(new DisplayDebug(DebugShape.Cube, Matrix4dUtil.asFloatData(transform),
            color, FRAME_SECONDS, (byte) DebugUtils.FLAG_NO_WIREFRAME, null, opacity));
    }
}
