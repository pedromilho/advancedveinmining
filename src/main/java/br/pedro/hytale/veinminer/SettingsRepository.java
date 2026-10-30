package br.pedro.hytale.veinminer;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
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

    SettingsRepository(Path directory, Consumer<String> warning) {
        this.directory = directory;
        this.warning = warning;
    }

    PlayerSettings get(UUID player) {
        return cache.computeIfAbsent(player, this::load);
    }

    synchronized void save(UUID player, PlayerSettings settings) throws IOException {
        Files.createDirectories(directory);
        Properties values = new Properties();
        values.setProperty("Radius", Integer.toString(settings.radius()));
        values.setProperty("Activation", settings.activation().name());
        values.setProperty("Blocks", settings.blocks().stream().sorted().collect(Collectors.joining(",")));
        values.setProperty("Tools", settings.tools().stream().sorted().collect(Collectors.joining(",")));
        Path temporary = Files.createTempFile(directory, player.toString(), ".tmp");
        try {
            try (var stream = Files.newOutputStream(temporary)) { values.store(stream, "Advenced Vein Mining - player settings"); }
            try {
                Files.move(temporary, file(player), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, file(player), StandardCopyOption.REPLACE_EXISTING);
            }
            cache.put(player, settings);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private PlayerSettings load(UUID player) {
        if (!Files.exists(file(player))) return PlayerSettings.defaults();
        try {
            Properties values = new Properties();
            try (var stream = Files.newInputStream(file(player))) { values.load(stream); }
            String rawBlocks = values.getProperty("Blocks");
            Set<String> blocks = rawBlocks == null ? PlayerSettings.DEFAULT_BLOCKS
                : Arrays.stream(rawBlocks.split(",")).map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toSet());
            return new PlayerSettings(blocks,
                values.getProperty("Tools") == null ? PlayerSettings.DEFAULT_TOOLS
                    : Arrays.stream(values.getProperty("Tools").split(",")).map(String::trim)
                        .filter(s -> !s.isEmpty()).collect(Collectors.toSet()),
                Integer.parseInt(values.getProperty("Radius", "8")),
                PlayerSettings.Activation.valueOf(values.getProperty("Activation", "CROUCH")));
        } catch (IOException | IllegalArgumentException error) {
            warning.accept("Advenced Vein Mining: invalid settings for " + player + "; using defaults. " + error.getMessage());
            return PlayerSettings.defaults();
        }
    }

    private Path file(UUID player) { return directory.resolve(player + ".properties"); }
}
