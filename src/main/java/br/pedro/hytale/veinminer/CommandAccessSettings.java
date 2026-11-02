package br.pedro.hytale.veinminer;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;
import java.util.function.Consumer;

/** Server-wide command policy; only publishes changes after they have been saved. */
final class CommandAccessSettings {
    enum Mode { ADMIN_ONLY, ALL }
    private final Path file;
    private volatile Mode mode = Mode.ADMIN_ONLY;

    CommandAccessSettings(Path file, Consumer<String> warning) {
        this.file = file;
        if (!Files.exists(file)) return;
        try (var stream = Files.newInputStream(file)) {
            var values = new Properties();
            values.load(stream);
            mode = Mode.valueOf(values.getProperty("PermissionCommands", "ADMIN_ONLY"));
        } catch (IOException | IllegalArgumentException error) {
            warning.accept("Invalid permission command settings; using Admin only. " + error.getMessage());
        }
    }

    Mode mode() { return mode; }

    synchronized void save(Mode selected) throws IOException {
        Files.createDirectories(file.toAbsolutePath().getParent());
        Path temporary = Files.createTempFile(file.toAbsolutePath().getParent(), "commands-", ".tmp");
        try {
            var values = new Properties();
            values.setProperty("PermissionCommands", selected.name());
            try (var stream = Files.newOutputStream(temporary)) { values.store(stream, "Advanced Vein Mining - command access"); }
            try {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
            mode = selected;
        } finally { Files.deleteIfExists(temporary); }
    }
}
