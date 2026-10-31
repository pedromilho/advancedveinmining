package br.pedro.hytale.veinminer;

import java.util.Set;
import java.util.Map;

record PlayerSettings(Set<String> blocks, Set<String> tools, int radius, Activation activation, Map<String, ToolBlockMode> toolModes) {
    static final int DEFAULT_RADIUS = 8;
    static final int MAX_RADIUS = 32;
    static final Set<String> DEFAULT_TOOLS = Set.of(
        "Tool_Pickaxe_Adamantite", "Tool_Pickaxe_Cobalt", "Tool_Pickaxe_Copper",
        "Tool_Pickaxe_Crude", "Tool_Pickaxe_Iron", "Tool_Pickaxe_Mithril",
        "Tool_Pickaxe_Onyxium", "Tool_Pickaxe_Scrap", "Tool_Pickaxe_Thorium", "Tool_Pickaxe_Wood"
    );
    static final Set<String> DEFAULT_BLOCKS = Set.of(
        "Ore_Adamantite_Magma", "Ore_Adamantite_Magma_Cracked",
        "Ore_Cobalt_Shale", "Ore_Cobalt_Slate", "Ore_Cobalt_Slate_Cracked",
        "Ore_Copper_Sandstone", "Ore_Copper_Shale", "Ore_Copper_Stone",
        "Ore_Gold_Basalt", "Ore_Gold_Calcite", "Ore_Gold_Sandstone", "Ore_Gold_Shale",
        "Ore_Gold_Stone", "Ore_Gold_Volcanic", "Ore_Iron_Basalt", "Ore_Iron_Basalt_Cracked",
        "Ore_Iron_Sandstone", "Ore_Iron_Shale", "Ore_Iron_Slate", "Ore_Iron_Stone",
        "Ore_Iron_Volcanic", "Ore_Mithril_Stone", "Ore_Silver_Basalt", "Ore_Silver_Sandstone",
        "Ore_Silver_Shale", "Ore_Silver_Slate", "Ore_Silver_Stone", "Ore_Silver_Volcanic",
        "Ore_Thorium_Mud", "Ore_Thorium_Mud_Cracked", "Ore_Thorium_Sandstone"
    );

    PlayerSettings {
        blocks = Set.copyOf(blocks);
        tools = Set.copyOf(tools);
        toolModes = Map.copyOf(toolModes);
        if (radius < 1 || radius > MAX_RADIUS) throw new IllegalArgumentException("Distance must be between 1 and 32.");
        if (activation == null) throw new IllegalArgumentException("Choose an activation action.");
    }

    PlayerSettings(Set<String> blocks, Set<String> tools, int radius, Activation activation) {
        this(blocks, tools, radius, activation, Map.of());
    }

    ToolBlockMode modeFor(String toolId, ToolBlockMode inferred) {
        return toolModes.getOrDefault(toolId, inferred);
    }

    enum ToolBlockMode {
        ORES, LOGS, ALL;
        boolean allows(boolean ore, boolean log) {
            return this == ALL || (this == ORES && ore) || (this == LOGS && log);
        }
    }

    static PlayerSettings defaults() {
        return new PlayerSettings(DEFAULT_BLOCKS, DEFAULT_TOOLS, DEFAULT_RADIUS, Activation.CROUCH);
    }

    // Mantém os padrões de ferramentas de configurações anteriores à seleção de ferramentas.
    PlayerSettings(Set<String> blocks, int radius, Activation activation) {
        this(blocks, DEFAULT_TOOLS, radius, activation);
    }

    boolean allowsTool(String itemId, boolean broken) { return !broken && tools.contains(itemId); }

    enum Activation {
        CROUCH("Crouch"), WALK("Walk"), SPRINT("Sprint");
        final String inputBinding;

        Activation(String inputBinding) { this.inputBinding = inputBinding; }

        boolean active(boolean crouching, boolean forcedCrouching, boolean walking, boolean sprinting) {
            return switch (this) {
                case CROUCH -> crouching && !forcedCrouching;
                case WALK -> walking;
                case SPRINT -> sprinting;
            };
        }
    }
}
