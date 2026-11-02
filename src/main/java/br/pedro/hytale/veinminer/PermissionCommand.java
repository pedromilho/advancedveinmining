package br.pedro.hytale.veinminer;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import java.util.Locale;

/** Three-argument usage variant; the no-argument command continues to open settings. */
final class PermissionCommand extends CommandBase {
    private final RequiredArg<PlayerRef> target;
    private final RequiredArg<String> mode;
    private final RequiredArg<String> enabled;

    PermissionCommand() {
        super("server.advancedVeinMining.commands.permissions.description");
        target = withRequiredArg("nickname", "server.advancedVeinMining.commands.permissions.player", ArgTypes.PLAYER_REF);
        mode = withRequiredArg("mode", "server.advancedVeinMining.commands.permissions.mode", ArgTypes.STRING);
        enabled = withRequiredArg("state", "server.advancedVeinMining.commands.permissions.state", ArgTypes.STRING);
        requirePermission(ConfigurationAccess.MANAGE_PERMISSION);
    }

    static ConfigurationAccess.Level parseLevel(String literal, String state) {
        if (literal == null || state == null || !literal.equalsIgnoreCase("modify")) return null;
        return switch (state.toLowerCase(Locale.ROOT)) {
            case "on" -> ConfigurationAccess.Level.MODIFY;
            case "off" -> ConfigurationAccess.Level.OFF;
            case "readonly" -> ConfigurationAccess.Level.READONLY;
            default -> null;
        };
    }

    @Override
    public boolean hasPermission(com.hypixel.hytale.server.core.command.system.CommandSender sender) {
        return ConfigurationAccess.canUsePermissionCommands(sender);
    }

    @Override
    protected void executeSync(CommandContext context) {
        var language = context.sender() instanceof PlayerRef playerRef ? UiLanguage.from(playerRef.getLanguage()) : UiLanguage.ENGLISH;
        if (!ConfigurationAccess.canUsePermissionCommands(context.sender())) {
            context.sendMessage(Message.raw(language.text("permission.manageDenied")));
            return;
        }
        var updated = parseLevel(mode.get(context), enabled.get(context));
        if (updated == null) {
            context.sendMessage(Message.raw(language.text("permission.command.usage")));
            return;
        }
        PlayerRef player = target.get(context);
        if (!ConfigurationAccess.setLevel(player.getUuid(), updated)) {
            context.sendMessage(Message.raw(language.text("permission.ownerProtected")));
            return;
        }
        context.sendMessage(Message.raw(language.text("permission.changed", player.getUsername(), language.text("permission.level." + updated.name()))));
    }
}
