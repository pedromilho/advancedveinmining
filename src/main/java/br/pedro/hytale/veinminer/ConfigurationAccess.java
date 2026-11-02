package br.pedro.hytale.veinminer;

import com.hypixel.hytale.server.core.permissions.PermissionHolder;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.Constants;
import com.hypixel.hytale.server.core.modules.singleplayer.SingleplayerModule;
import com.hypixel.hytale.server.core.permissions.PermissionsModule;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;

/** All decisions use current server grants, including explicit denials and permission providers. */
final class ConfigurationAccess {
    static final String PERMISSION = "advancedveinmining.config";
    static final String READ_PERMISSION = "advancedveinmining.readonly";
    static final String MANAGE_PERMISSION = "advancedveinmining.permissions";
    enum Level { OFF, READONLY, MODIFY }

    private static volatile CommandAccessSettings commandAccess;

    private ConfigurationAccess() {}

    static void configureCommands(CommandAccessSettings settings) { commandAccess = settings; }
    static CommandAccessSettings.Mode commandMode() {
        var settings = commandAccess;
        return settings == null ? CommandAccessSettings.Mode.ADMIN_ONLY : settings.mode();
    }
    static boolean canUsePermissionCommands(PermissionHolder player) {
        return player != null && (commandMode() == CommandAccessSettings.Mode.ALL || canManage(player));
    }
    static void saveCommandMode(CommandAccessSettings.Mode selected) throws java.io.IOException {
        var settings = commandAccess;
        if (settings == null) throw new java.io.IOException("Command settings are not initialized");
        settings.save(selected);
    }

    static boolean allowed(PermissionHolder player) { return canModify(player); }
    static boolean isOwner(UUID uuid) {
        return uuid != null && Constants.SINGLEPLAYER && SingleplayerModule.isOwner(uuid);
    }
    static boolean isOwner(PermissionHolder player) {
        return Constants.SINGLEPLAYER && player instanceof CommandSender sender && isOwner(sender.getUuid());
    }
    static boolean canModify(PermissionHolder player) { return level(player) == Level.MODIFY; }
    static boolean canView(PermissionHolder player) { return level(player) != Level.OFF; }
    static boolean canManage(PermissionHolder player) {
        return isOwner(player) || (player != null && player.hasPermission(MANAGE_PERMISSION));
    }
    static Level level(PermissionHolder player) {
        if (isOwner(player)) return Level.MODIFY;
        if (player == null) return Level.OFF;
        return player.hasPermission(PERMISSION) ? Level.MODIFY : player.hasPermission(READ_PERMISSION) ? Level.READONLY : Level.OFF;
    }
    static boolean isAdmin(PlayerRef player) {
        return player.hasPermission("*") || PermissionsModule.get().getGroupsForUser(player.getUuid()).contains("hytale:Admin");
    }
    static Level change(Level current, Level requested, boolean enabled) {
        if (requested == Level.OFF) throw new IllegalArgumentException("Choose modify or readonly");
        return enabled ? requested : current == requested ? Level.OFF : current;
    }
    static boolean isBrowsingAction(String action) {
        return action != null && (Set.of("Cancel", "Search", "BlocksTab", "ToolsTab", "Filter", "General").contains(action)
            || action.startsWith("LoadAhead:") || action.startsWith("Edit:Tool:"));
    }
    static boolean canPerform(PermissionHolder player, String action) {
        if (!canView(player)) return false;
        if (action != null && (action.equals("Players") || action.startsWith("PlayerLoadAhead:"))) return canModify(player);
        if (action != null && (action.equals("CommandAdminOnly") || action.equals("CommandAll")))
            return canModify(player) && canManage(player);
        if (isBrowsingAction(action)) return true;
        if (action != null && (action.startsWith("PlayerModify:") || action.startsWith("PlayerReadonly:")))
            return canModify(player) && canManage(player);
        return canModify(player);
    }
    static boolean setLevel(UUID target, Level level) {
        if (isOwner(target)) return false;
        var permissions = PermissionsModule.get();
        return setLevel(target, level, permissions::addUserPermission, permissions::removeUserPermission);
    }
    static boolean setLevel(UUID target, Level level, BiConsumer<UUID, Set<String>> add, BiConsumer<UUID, Set<String>> remove) {
        if (isOwner(target)) return false;
        // Apply denials before removing old grants, avoiding a temporary wildcard escalation.
        add.accept(target, switch (level) {
            case MODIFY -> Set.of(PERMISSION, "-" + READ_PERMISSION);
            case READONLY -> Set.of("-" + PERMISSION, READ_PERMISSION);
            case OFF -> Set.of("-" + PERMISSION, "-" + READ_PERMISSION);
        });
        remove.accept(target, switch (level) {
            case MODIFY -> Set.of("-" + PERMISSION, READ_PERMISSION);
            case READONLY -> Set.of(PERMISSION, "-" + READ_PERMISSION);
            case OFF -> Set.of(PERMISSION, READ_PERMISSION);
        });
        return true;
    }
}
