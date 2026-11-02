package br.pedro.hytale.veinminer;

import com.hypixel.hytale.server.core.universe.PlayerRef;
import java.util.Comparator;
import java.util.UUID;

/** Snapshot used for stable player ordering, filtering and batched UI rendering. */
record PlayerListEntry(UUID uuid, String name, boolean owner, boolean admin, ConfigurationAccess.Level level) {
    static final Comparator<PlayerListEntry> ORDER = Comparator.comparing(PlayerListEntry::owner).reversed()
        .thenComparing(PlayerListEntry::name, String.CASE_INSENSITIVE_ORDER).thenComparing(PlayerListEntry::uuid);

    static PlayerListEntry from(PlayerRef player) {
        return new PlayerListEntry(player.getUuid(), player.getUsername(),
            ConfigurationAccess.isOwner(player),
            ConfigurationAccess.isAdmin(player), ConfigurationAccess.level(player));
    }

    String displayName() { return name + (owner ? " (owner)" : admin ? " (admin)" : ""); }

    enum Filter {
        ALL, SELECTED, UNSELECTED, ADMINS, MODIFY, READONLY;

        Filter next() { return values()[(ordinal() + 1) % values().length]; }

        boolean matches(PlayerListEntry player) {
            return switch (this) {
                case ALL -> true;
                case SELECTED -> player.level() != ConfigurationAccess.Level.OFF;
                case UNSELECTED -> player.level() == ConfigurationAccess.Level.OFF;
                case ADMINS -> player.admin();
                case MODIFY -> player.level() == ConfigurationAccess.Level.MODIFY;
                case READONLY -> player.level() == ConfigurationAccess.Level.READONLY;
            };
        }
    }
}
