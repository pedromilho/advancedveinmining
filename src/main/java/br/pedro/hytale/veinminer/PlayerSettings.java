package br.pedro.hytale.veinminer;

import java.util.Set;

record PlayerSettings(Set<String> blocks, Set<String> tools, int radius, Activation activation) {
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
        if (radius < 1 || radius > MAX_RADIUS) throw new IllegalArgumentException("Distancia deve ser de 1 a 32.");
        if (activation == null) throw new IllegalArgumentException("Escolha uma acao de ativacao.");
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
        CROUCH("Agachar", "Crouch"), WALK("Caminhar", "Walk"), SPRINT("Correr", "Sprint");
        final String label;
        final String inputBinding;

        Activation(String label, String inputBinding) { this.label = label; this.inputBinding = inputBinding; }

        boolean active(boolean crouching, boolean forcedCrouching, boolean walking, boolean sprinting) {
            return switch (this) {
                case CROUCH -> crouching && !forcedCrouching;
                case WALK -> walking;
                case SPRINT -> sprinting;
            };
        }
    }
}
