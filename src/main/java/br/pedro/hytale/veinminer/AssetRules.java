package br.pedro.hytale.veinminer;

import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Uses registered assets, including inherited metadata from other mods. */
final class AssetRules {
    private static final String SOILS_GATHER_TYPE = "soils";
    private static final String WOODS_GATHER_TYPE = "woods";

    private AssetRules() {}

    static boolean ore(BlockType block) {
        if (log(block)) return false;
        Item item = block.getItem();
        return token(block.getId(), "ore") || gatherType(block).startsWith("ore")
            || tag(item, "Ore") || tag(block.getData(), "Ore") || category(item, "Blocks.Ores");
    }

    private static boolean stone(BlockType block) {
        String gather = gatherType(block);
        if ("rocks".equals(gather) || "volcanicrocks".equals(gather)) return true;
        // Gravel can share the Rocks catalog category while remaining a soil for mining.
        if (SOILS_GATHER_TYPE.equals(gather)) return false;
        Item item = block.getItem();
        return tag(item, "Rock") || tag(item, "Stone") || tag(block.getData(), "Rock")
            || tag(block.getData(), "Stone") || category(item, "Blocks.Rocks");
    }

    private static String gatherType(BlockType block) {
        var gathering = block.getGathering();
        return gathering == null || gathering.getBreaking() == null
            ? "" : lower(gathering.getBreaking().getGatherType());
    }

    static boolean log(BlockType block) {
        Item item = block.getItem();
        if (tag(item, "Trunk") || tag(item, "Log") || tag(block.getData(), "Trunk") || tag(block.getData(), "Log")
            || token(block.getId(), "trunk") || token(block.getId(), "log")) return true;
        if (item != null && item.getResourceTypes() != null) {
            for (var resource : item.getResourceTypes()) {
                if (token(resource.id, "trunk") || token(resource.id, "log")) return true;
            }
        }
        return false;
    }

    static PlayerSettings.ToolBlockMode defaultMode(Item item) {
        if (item == null) return PlayerSettings.ToolBlockMode.ALL;
        String identity = item.getId() + " " + item.getPlayerAnimationsId() + " " + item.getModel();
        if (token(identity, "hatchet") || token(identity, "axe") || tag(item, "Hatchet") || category(item, "Items.Tools.Hatchets"))
            return PlayerSettings.ToolBlockMode.LOGS;
        if (token(identity, "pickaxe") || tag(item, "Pickaxe") || category(item, "Items.Tools.Pickaxes"))
            return PlayerSettings.ToolBlockMode.ORES;
        if (token(identity, "shovel") || token(identity, "spade") || tag(item, "Shovel") || tag(item, "Spade")
            || category(item, "Items.Tools.Shovels"))
            return PlayerSettings.ToolBlockMode.DIRT_SAND;
        // Custom tools with different IDs can still be recognized by their effective gather power.
        if (item.getTool() != null && item.getTool().getSpecs() != null) {
            float woods = 0, rocks = 0;
            for (var spec : item.getTool().getSpecs()) {
                if (spec.isIncorrect()) continue;
                String gather = lower(spec.getGatherType());
                if (WOODS_GATHER_TYPE.equals(gather)) woods = Math.max(woods, spec.getPower());
                if (gather.equals("rocks") || gather.startsWith("ore")) rocks = Math.max(rocks, spec.getPower());
            }
            if (woods > rocks) return PlayerSettings.ToolBlockMode.LOGS;
            if (rocks > woods) return PlayerSettings.ToolBlockMode.ORES;
        }
        return PlayerSettings.ToolBlockMode.ALL;
    }

    static boolean allows(PlayerSettings settings, Item item, BlockType block) {
        if (item == null || !settings.blocks().contains(block.getId())) return false;
        var mode = settings.modeFor(item.getId(), defaultMode(item));
        return switch (mode) {
            case ORES -> ore(block) || (stone(block) && !log(block));
            case LOGS -> WOODS_GATHER_TYPE.equals(gatherType(block)) || log(block);
            case DIRT_SAND -> SOILS_GATHER_TYPE.equals(gatherType(block));
            case ALL -> true;
        };
    }

    static PlayerSettings pickaxeDefaults() {
        var blocks = new HashSet<String>();
        var tools = new HashSet<String>();
        BlockType.getAssetMap().getAssetMap().values().stream()
            .filter(b -> !b.getId().startsWith("*") && ore(b)).forEach(b -> blocks.add(b.getId()));
        configurableTools()
            .filter(i -> defaultMode(i) == PlayerSettings.ToolBlockMode.ORES).forEach(i -> tools.add(i.getId()));
        return new PlayerSettings(blocks, tools, PlayerSettings.DEFAULT_RADIUS, PlayerSettings.Activation.CROUCH);
    }

    static PlayerSettings hatchetDefaults() {
        var blocks = new HashSet<String>();
        var tools = new HashSet<String>();
        BlockType.getAssetMap().getAssetMap().values().stream()
            .filter(b -> !b.getId().startsWith("*") && log(b)).forEach(b -> blocks.add(b.getId()));
        configurableTools()
            .filter(i -> defaultMode(i) == PlayerSettings.ToolBlockMode.LOGS).forEach(i -> tools.add(i.getId()));
        return new PlayerSettings(blocks, tools, PlayerSettings.DEFAULT_RADIUS, PlayerSettings.Activation.CROUCH);
    }

    static PlayerSettings defaults() {
        var blocks = new HashSet<String>();
        var tools = new HashSet<String>();
        BlockType.getAssetMap().getAssetMap().values().stream()
            .filter(b -> b.getItem() != null && !b.getId().startsWith("*") && (ore(b) || log(b)))
            .forEach(b -> blocks.add(b.getId()));
        configurableTools()
            .filter(i -> defaultMode(i) != PlayerSettings.ToolBlockMode.ALL).forEach(i -> tools.add(i.getId()));
        return new PlayerSettings(blocks, tools, PlayerSettings.DEFAULT_RADIUS, PlayerSettings.Activation.CROUCH);
    }

    static Set<String> shovelDefaults() {
        return configurableTools().filter(i -> defaultMode(i) == PlayerSettings.ToolBlockMode.DIRT_SAND)
            .map(Item::getId).collect(Collectors.toSet());
    }

    private static Stream<Item> configurableTools() {
        return Item.getAssetMap().getAssetMap().values().stream()
            .filter(i -> i.getTool() != null && !i.isState() && !i.getId().startsWith("*"));
    }

    private static boolean tag(Item item, String value) {
        return item != null && tag(item.getData(), value);
    }

    private static boolean tag(com.hypixel.hytale.assetstore.AssetExtraInfo.Data data, String value) {
        if (data == null || data.getRawTags() == null) return false;
        String[] types = data.getRawTags().get("Type");
        if (types != null) for (String type : types) if (value.equalsIgnoreCase(type)) return true;
        return false;
    }

    private static boolean category(Item item, String value) {
        if (item != null && item.getCategories() != null)
            for (String category : item.getCategories()) if (value.equalsIgnoreCase(category)) return true;
        return false;
    }

    private static String lower(String value) { return value == null ? "" : value.toLowerCase(Locale.ROOT); }
    private static boolean token(String value, String token) {
        return java.util.Arrays.asList(lower(value).split("[^a-z0-9]+")).contains(token);
    }
}
