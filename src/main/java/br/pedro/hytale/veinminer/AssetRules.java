package br.pedro.hytale.veinminer;

import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import java.util.HashSet;
import java.util.Locale;

/** Uses registered assets, including inherited metadata from other mods. */
final class AssetRules {
    private AssetRules() {}

    static boolean ore(BlockType block) {
        if (log(block)) return false;
        Item item = block.getItem();
        String gather = block.getGathering() == null || block.getGathering().getBreaking() == null
            ? "" : block.getGathering().getBreaking().getGatherType();
        return token(block.getId(), "ore") || lower(gather).startsWith("ore")
            || tag(item, "Ore") || tag(block.getData(), "Ore") || category(item, "Blocks.Ores");
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
        // Custom tools with different IDs can still be recognized by their effective gather power.
        if (item.getTool() != null && item.getTool().getSpecs() != null) {
            float woods = 0, rocks = 0;
            for (var spec : item.getTool().getSpecs()) {
                if (spec.isIncorrect()) continue;
                String gather = lower(spec.getGatherType());
                if (gather.equals("woods")) woods = Math.max(woods, spec.getPower());
                if (gather.equals("rocks") || gather.startsWith("ore")) rocks = Math.max(rocks, spec.getPower());
            }
            if (woods > rocks) return PlayerSettings.ToolBlockMode.LOGS;
            if (rocks > woods) return PlayerSettings.ToolBlockMode.ORES;
        }
        return PlayerSettings.ToolBlockMode.ALL;
    }

    static boolean allows(PlayerSettings settings, Item item, BlockType block) {
        if (item == null) return false;
        var mode = settings.modeFor(item.getId(), defaultMode(item));
        return settings.blocks().contains(block.getId()) && mode.allows(ore(block), log(block));
    }

    static PlayerSettings pickaxeDefaults() {
        var blocks = new HashSet<String>();
        var tools = new HashSet<String>();
        BlockType.getAssetMap().getAssetMap().values().stream()
            .filter(b -> !b.getId().startsWith("*") && ore(b)).forEach(b -> blocks.add(b.getId()));
        Item.getAssetMap().getAssetMap().values().stream()
            .filter(i -> i.getTool() != null && !i.isState() && !i.getId().startsWith("*"))
            .filter(i -> defaultMode(i) == PlayerSettings.ToolBlockMode.ORES).forEach(i -> tools.add(i.getId()));
        return new PlayerSettings(blocks, tools, PlayerSettings.DEFAULT_RADIUS, PlayerSettings.Activation.CROUCH);
    }

    static PlayerSettings hatchetDefaults() {
        var blocks = new HashSet<String>();
        var tools = new HashSet<String>();
        BlockType.getAssetMap().getAssetMap().values().stream()
            .filter(b -> !b.getId().startsWith("*") && log(b)).forEach(b -> blocks.add(b.getId()));
        Item.getAssetMap().getAssetMap().values().stream()
            .filter(i -> i.getTool() != null && !i.isState() && !i.getId().startsWith("*"))
            .filter(i -> defaultMode(i) == PlayerSettings.ToolBlockMode.LOGS).forEach(i -> tools.add(i.getId()));
        return new PlayerSettings(blocks, tools, PlayerSettings.DEFAULT_RADIUS, PlayerSettings.Activation.CROUCH);
    }

    static PlayerSettings defaults() {
        var blocks = new HashSet<String>();
        var tools = new HashSet<String>();
        BlockType.getAssetMap().getAssetMap().values().stream()
            .filter(b -> b.getItem() != null && !b.getId().startsWith("*") && (ore(b) || log(b)))
            .forEach(b -> blocks.add(b.getId()));
        Item.getAssetMap().getAssetMap().values().stream()
            .filter(i -> i.getTool() != null && !i.isState() && !i.getId().startsWith("*"))
            .filter(i -> defaultMode(i) != PlayerSettings.ToolBlockMode.ALL).forEach(i -> tools.add(i.getId()));
        return new PlayerSettings(blocks, tools, PlayerSettings.DEFAULT_RADIUS, PlayerSettings.Activation.CROUCH);
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
