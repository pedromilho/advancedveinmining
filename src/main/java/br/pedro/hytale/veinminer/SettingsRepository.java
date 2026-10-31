package br.pedro.hytale.veinminer;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.HashMap;
import java.util.function.Supplier;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/** Arquivos individuais por UUID; só publica alterações após concluir a gravação. */
final class SettingsRepository {
    private final Path directory;
    private final Consumer<String> warning;
    private final ConcurrentHashMap<UUID, PlayerSettings> cache = new ConcurrentHashMap<>();

    private final Supplier<PlayerSettings> defaults;
    private final Supplier<PlayerSettings> automaticLogs;
    private final Supplier<PlayerSettings> automaticOres;

    SettingsRepository(Path directory, Consumer<String> warning) {
        this(directory, warning, PlayerSettings::defaults);
    }

    SettingsRepository(Path directory, Consumer<String> warning, Supplier<PlayerSettings> defaults) {
        this(directory, warning, defaults, () -> new PlayerSettings(Set.of(), Set.of(), 8, PlayerSettings.Activation.CROUCH));
    }

    SettingsRepository(Path directory, Consumer<String> warning, Supplier<PlayerSettings> defaults,
                       Supplier<PlayerSettings> automaticLogs) {
        this(directory, warning, defaults, automaticLogs,
            () -> new PlayerSettings(Set.of(), Set.of(), 8, PlayerSettings.Activation.CROUCH));
    }

    SettingsRepository(Path directory, Consumer<String> warning, Supplier<PlayerSettings> defaults,
                       Supplier<PlayerSettings> automaticLogs, Supplier<PlayerSettings> automaticOres) {
        this.automaticOres = automaticOres;
        this.automaticLogs = automaticLogs;
        this.defaults = defaults;
        this.directory = directory;
        this.warning = warning;
    }

    PlayerSettings get(UUID player) {
        return cache.computeIfAbsent(player, this::load);
    }

    synchronized void save(UUID player, PlayerSettings settings) throws IOException {
        write(player, settings);
        cache.put(player, settings);
    }

    private void write(UUID player, PlayerSettings settings) throws IOException {
        Files.createDirectories(directory);
        Properties values = new Properties();
        values.setProperty("Radius", Integer.toString(settings.radius()));
        values.setProperty("Activation", settings.activation().name());
        values.setProperty("Blocks", settings.blocks().stream().sorted().collect(Collectors.joining(",")));
        values.setProperty("Tools", settings.tools().stream().sorted().collect(Collectors.joining(",")));
        settings.toolModes().forEach((id, mode) -> values.setProperty("ToolMode." + id, mode.name()));
        PlayerSettings discovered = automaticLogs.get();
        values.setProperty("KnownHatchets", String.join(",", new java.util.TreeSet<>(discovered.tools())));
        values.setProperty("KnownLogs", String.join(",", new java.util.TreeSet<>(discovered.blocks())));
        PlayerSettings discoveredOres = automaticOres.get();
        values.setProperty("KnownPickaxes", String.join(",", new java.util.TreeSet<>(discoveredOres.tools())));
        values.setProperty("KnownOres", String.join(",", new java.util.TreeSet<>(discoveredOres.blocks())));
        Path temporary = Files.createTempFile(directory, player.toString(), ".tmp");
        try {
            try (var stream = Files.newOutputStream(temporary)) { values.store(stream, "Advenced Vein Mining - player settings"); }
            try {
                Files.move(temporary, file(player), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, file(player), StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private PlayerSettings load(UUID player) {
        if (!Files.exists(file(player))) return defaults.get();
        try {
            Properties values = new Properties();
            try (var stream = Files.newInputStream(file(player))) { values.load(stream); }
            String rawBlocks = values.getProperty("Blocks");
            Set<String> blocks = rawBlocks == null ? PlayerSettings.DEFAULT_BLOCKS
                : Arrays.stream(rawBlocks.split(",")).map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toSet());
            var modes = new HashMap<String, PlayerSettings.ToolBlockMode>();
            for (String key : values.stringPropertyNames()) {
                if (key.startsWith("ToolMode.")) {
                    modes.put(key.substring(9), PlayerSettings.ToolBlockMode.valueOf(values.getProperty(key)));
                }
            }
            PlayerSettings loaded = new PlayerSettings(blocks,
                values.getProperty("Tools") == null ? PlayerSettings.DEFAULT_TOOLS
                    : Arrays.stream(values.getProperty("Tools").split(",")).map(String::trim)
                        .filter(s -> !s.isEmpty()).collect(Collectors.toSet()),
                Integer.parseInt(values.getProperty("Radius", "8")),
                PlayerSettings.Activation.valueOf(values.getProperty("Activation", "CROUCH")), modes);
            PlayerSettings discovered = automaticLogs.get();
            var updatedTools = new java.util.HashSet<>(loaded.tools());
            var updatedBlocks = new java.util.HashSet<>(loaded.blocks());
            var newTools = new java.util.HashSet<>(discovered.tools());
            var newBlocks = new java.util.HashSet<>(discovered.blocks());
            newTools.removeAll(Arrays.asList(values.getProperty("KnownHatchets", "").split(",")));
            newBlocks.removeAll(Arrays.asList(values.getProperty("KnownLogs", "").split(",")));
            updatedTools.addAll(newTools);
            updatedBlocks.addAll(newBlocks);
            newTools.forEach(id -> modes.putIfAbsent(id, PlayerSettings.ToolBlockMode.LOGS));
            PlayerSettings discoveredOres = automaticOres.get();
            var newPickaxes = new java.util.HashSet<>(discoveredOres.tools());
            var newOres = new java.util.HashSet<>(discoveredOres.blocks());
            newPickaxes.removeAll(Arrays.asList(values.getProperty("KnownPickaxes", "").split(",")));
            newOres.removeAll(Arrays.asList(values.getProperty("KnownOres", "").split(",")));
            updatedTools.addAll(newPickaxes);
            updatedBlocks.addAll(newOres);
            newPickaxes.forEach(id -> modes.putIfAbsent(id, PlayerSettings.ToolBlockMode.ORES));
            var updated = new PlayerSettings(updatedBlocks, updatedTools, loaded.radius(), loaded.activation(), modes);
            if (!newTools.isEmpty() || !newBlocks.isEmpty() || !newPickaxes.isEmpty() || !newOres.isEmpty()
                || !values.containsKey("KnownHatchets") || !values.containsKey("KnownPickaxes")) {
                try { write(player, updated); }
                catch (IOException error) { warning.accept("Could not persist newly detected tools/blocks: " + error.getMessage()); }
            }
            return updated;
        } catch (IOException | IllegalArgumentException error) {
            warning.accept("Advenced Vein Mining: invalid settings for " + player + "; using defaults. " + error.getMessage());
            return defaults.get();
        }
    }

    private Path file(UUID player) { return directory.resolve(player + ".properties"); }
}
