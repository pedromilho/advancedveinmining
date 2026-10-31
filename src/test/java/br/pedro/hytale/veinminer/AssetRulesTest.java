package br.pedro.hytale.veinminer;

import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemTool;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemToolSpec;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockGathering;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockBreakingDropType;
import java.util.Set;

public final class AssetRulesTest {
    private static final class Tool extends Item {
        Tool(String id, float woods, float rocks) {
            super(id);
            tool = new ItemTool(new ItemToolSpec[] {
                new ItemToolSpec("Woods", woods, 1), new ItemToolSpec("Rocks", rocks, 1)
            }, 1, null);
        }
    }
    private static final class Gathering extends BlockGathering {
        Gathering(String type) { breaking = new BlockBreakingDropType(type, 1, 1, null, null); }
    }
    private static final class Block extends BlockType {
        private final Item item;
        Block(String id, String gather, String... itemCategories) {
            super(id);
            item = new Item(id) { @Override public String[] getCategories() { return itemCategories; } };
            gathering = new Gathering(gather);
        }
        @Override public Item getItem() { return item; }
    }
    public static void main(String[] args) throws Exception {
        try {
            verifyDrops();
            var pick = new Tool("OtherMod:Tool_Pickaxe_Ruby", 0.05f, 0.35f);
            var axe = new Tool("OtherMod:Tool_Hatchet_Ruby", 0.2f, 0.05f);
            check(AssetRules.defaultMode(pick) == PlayerSettings.ToolBlockMode.ORES, "Mod pickaxe");
            check(AssetRules.defaultMode(axe) == PlayerSettings.ToolBlockMode.LOGS, "Mod hatchet");
            check(AssetRules.defaultMode(new Tool("OtherMod:Drill", 0, 0.8f)) == PlayerSettings.ToolBlockMode.ORES, "Recognize mining specs with custom ID");
            check(AssetRules.defaultMode(new Tool("OtherMod:LumberTool", 0.8f, 0.1f)) == PlayerSettings.ToolBlockMode.LOGS, "Recognize wood specs with custom ID");
            var ore = new Block("OtherMod:RubyDeposit", "OreRuby");
            var categoryOre = new Block("OtherMod:CustomDeposit", "CustomGather", "Blocks.Ores");
            var log = new Block("OtherMod:Tree_Log", "Woods");
            var planks = new Block("OtherMod:Planks", "Woods", "Blocks.Wood");
            check(AssetRules.ore(ore) && AssetRules.ore(categoryOre), "Mod ores from gather type or categories");
            check(AssetRules.log(log) && !AssetRules.log(planks), "Logs exclude planks");
            var settings = new PlayerSettings(Set.of(ore.getId(), log.getId(), planks.getId()), Set.of(pick.getId(), axe.getId()), 8, PlayerSettings.Activation.CROUCH);
            check(AssetRules.allows(settings, pick, ore) && !AssetRules.allows(settings, pick, log), "Pickaxe restrictions");
            check(AssetRules.allows(settings, axe, log) && !AssetRules.allows(settings, axe, ore), "Hatchet restrictions");
            var all = new PlayerSettings(settings.blocks(), settings.tools(), 8, settings.activation(), java.util.Map.of(axe.getId(), PlayerSettings.ToolBlockMode.ALL));
            check(AssetRules.allows(all, axe, planks), "All permits selected planks");
            check(!AssetRules.allows(all, axe, categoryOre), "All still respects global block selection");
            var oreOnlyHatchet = new PlayerSettings(settings.blocks(), settings.tools(), 8, settings.activation(),
                java.util.Map.of(axe.getId(), PlayerSettings.ToolBlockMode.ORES));
            check(!AssetRules.allows(oreOnlyHatchet, axe, log), "Hatchet explicitly set to ORES cannot vein mine logs");
            check(!AssetRules.allows(oreOnlyHatchet, axe, planks), "Hatchet explicitly set to ORES cannot vein mine planks");
            check(AssetRules.allows(oreOnlyHatchet, axe, ore), "Hatchet explicitly set to ORES can vein mine selected ore");
            var misleadingLog = new Block("Ore:Tree_Log", "Woods", "Blocks.Ores");
            check(!AssetRules.ore(misleadingLog) && AssetRules.log(misleadingLog), "Wood is never classified as ore due to mod namespace or inherited ore category");
            System.out.println("Asset rules: mod tools, ore/log classification and per-tool restrictions passed.");
        } catch (Throwable failure) {
            var trace = new java.io.StringWriter();
            failure.printStackTrace(new java.io.PrintWriter(trace));
            java.nio.file.Files.writeString(java.nio.file.Path.of("build/asset-rules-test-error.txt"), trace.toString());
            throw failure;
        }
    }
    private static final class DropStack extends com.hypixel.hytale.server.core.inventory.ItemStack {
        DropStack() {
            itemId = "OtherMod:Ruby";
            quantity = 3;
            metadata = new org.bson.BsonDocument("Custom", new org.bson.BsonString("preserved"));
        }
    }

    @SuppressWarnings("unchecked")
    private static void verifyDrops() {
        var registry = new com.hypixel.hytale.component.ComponentRegistry<com.hypixel.hytale.server.core.universe.world.storage.EntityStore>();
        var itemType = registry.registerComponent(com.hypixel.hytale.server.core.modules.entity.item.ItemComponent.class,
            com.hypixel.hytale.server.core.modules.entity.item.ItemComponent::new);
        var transformType = registry.registerComponent(com.hypixel.hytale.server.core.modules.entity.component.TransformComponent.class,
            com.hypixel.hytale.server.core.modules.entity.component.TransformComponent::new);
        var item = registry.newHolder();
        var stack = new DropStack();
        item.addComponent(itemType, new com.hypixel.hytale.server.core.modules.entity.item.ItemComponent(stack));
        var transform = new com.hypixel.hytale.server.core.modules.entity.component.TransformComponent();
        transform.setPosition(new org.joml.Vector3d(12.5, 60.5, -4.5));
        item.addComponent(transformType, transform);
        var other = registry.newHolder();
        var otherTransform = new com.hypixel.hytale.server.core.modules.entity.component.TransformComponent();
        otherTransform.setPosition(new org.joml.Vector3d(100, 30, 100));
        other.addComponent(transformType, otherTransform);
        var inserted = new java.util.ArrayList<Object>();
        var delegate = (com.hypixel.hytale.component.ComponentAccessor<com.hypixel.hytale.server.core.universe.world.storage.EntityStore>)
            java.lang.reflect.Proxy.newProxyInstance(AssetRulesTest.class.getClassLoader(),
                new Class<?>[] {com.hypixel.hytale.component.ComponentAccessor.class}, (proxy, method, args) -> {
                    if (method.getName().equals("addEntities")) {
                        inserted.addAll(java.util.Arrays.asList((Object[]) args[0]));
                    } else if (method.getName().equals("addEntity")) inserted.add(args[0]);
                    else throw new AssertionError("Unexpected operation " + method.getName());
                    return null;
                });
        var drops = new DropRedirector(delegate, new VeinTraversal.Position(-7, 22, 9), itemType, transformType);
        drops.addEntities(new com.hypixel.hytale.component.Holder[] {item, other}, com.hypixel.hytale.component.AddReason.SPAWN);
        check(transform.getPosition().equals(new org.joml.Vector3d(-6.5, 22.5, 9.5)), "Drops appear at initial block center, including negative coordinates");
        check(otherTransform.getPosition().equals(new org.joml.Vector3d(100, 30, 100)), "Non-item entities keep their position");
        check(item.getComponent(itemType).getItemStack() == stack && stack.getQuantity() == 3, "Preserve native drop item, quantity and metadata");
        check(inserted.size() == 2 && inserted.getFirst() == item, "Forward original holders once without recreating drops");
        inserted.clear();
        drops.addEntity(item, com.hypixel.hytale.component.AddReason.SPAWN);
        check(inserted.size() == 1, "Single-entity drops use same redirection");
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
