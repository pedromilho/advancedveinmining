package br.pedro.hytale.veinminer;

import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.permissions.PermissionQuery;
import com.hypixel.hytale.server.core.permissions.PermissionsModule;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

public final class PermissionsTest {
    public static void main(String[] args) throws Exception {
        try {
            var nativeOwner = java.util.UUID.fromString("f7a996a7-15c1-4d74-a52b-782a5981621d");
            com.hypixel.hytale.server.core.Options.parse(new String[] {"--singleplayer", "--owner-uuid", nativeOwner.toString(), "--universe", "build/permissions-test-universe"});
            var executor = java.util.UUID.randomUUID();
            var grants = new HashSet<String>();
            var sender = sender(executor, grants);
            var commandClass = Class.forName("br.pedro.hytale.veinminer.VeinMinerPlugin$SettingsCommand");
            var constructor = commandClass.getDeclaredConstructor(SettingsRepository.class);
            constructor.setAccessible(true);
            var repository = new SettingsRepository(Path.of("build", "permissions-test-settings"), warning -> { throw new AssertionError(warning); });
            var command = (AbstractCommand) constructor.newInstance(repository);
            var permissionCommand = command.getVariantByArgCount(3);
            for (String alias : Set.of(command.getName(), "advancedveinminer", "advancedveinmining", "veinmining")) {
                var resolved = alias.equals(command.getName()) || command.getAliases().contains(alias) ? command : null;
                check(resolved != null && resolved.getVariantByArgCount(3) == permissionCommand,
                    "Permission syntax is shared by menu alias: " + alias);
            }
            check(permissionCommand != null && permissionCommand.getPermission().equals(ConfigurationAccess.MANAGE_PERMISSION), "Three-argument variant requires management permission");
            check(PermissionCommand.parseLevel("modify", "on") == ConfigurationAccess.Level.MODIFY, "modify on enables editing");
            check(PermissionCommand.parseLevel("modify", "off") == ConfigurationAccess.Level.OFF, "modify off blocks menu");
            check(PermissionCommand.parseLevel("modify", "readonly") == ConfigurationAccess.Level.READONLY, "modify readonly allows viewing");
            check(PermissionCommand.parseLevel("readonly", "on") == null, "modify is a fixed command literal");
            check(PermissionCommand.parseLevel("modify", "unknown") == null, "Unknown access state is rejected");
            check(command.getAliases().equals(Set.of("advancedveinminer", "advancedveinmining", "veinmining")), "All aliases use the same command permission");
            check(!command.hasPermission(sender) && !ConfigurationAccess.allowed(sender), "No grants deny command and UI access");
            grants.add("unrelated.permission");
            check(!command.hasPermission(sender) && !ConfigurationAccess.allowed(sender), "Unrelated permission does not grant access");
            grants.add(ConfigurationAccess.PERMISSION);
            check(command.hasPermission(sender) && ConfigurationAccess.allowed(sender), "Explicit grant permits command and UI access");
            grants.remove(ConfigurationAccess.PERMISSION);
            check(!command.hasPermission(sender) && !ConfigurationAccess.allowed(sender), "Revocation takes effect on next check without cached grants");
            grants.add("advancedveinmining.*");
            check(command.hasPermission(sender) && ConfigurationAccess.allowed(sender), "Native plugin wildcard grants access");
            grants.clear();
            grants.add("*");
            check(command.hasPermission(sender) && ConfigurationAccess.allowed(sender), "Native administrator wildcard grants access");
            grants.clear();
            try {
                Class.forName("br.pedro.hytale.veinminer.SelfPermissionCommand");
                throw new AssertionError("Public self-test command must not ship in the normal build");
            } catch (ClassNotFoundException expected) { }
            var other = java.util.UUID.randomUUID();
            var userGrants = new java.util.HashMap<java.util.UUID, Set<String>>();
            userGrants.put(executor, grants);
            userGrants.put(other, new HashSet<>(Set.of(ConfigurationAccess.PERMISSION)));
            java.util.function.BiConsumer<java.util.UUID, Set<String>> add = (uuid, permissions) -> userGrants.get(uuid).addAll(permissions);
            java.util.function.BiConsumer<java.util.UUID, Set<String>> remove = (uuid, permissions) -> userGrants.get(uuid).removeAll(permissions);
            grants.add("*");
            ConfigurationAccess.setLevel(executor, ConfigurationAccess.Level.OFF, add, remove);
            check(!ConfigurationAccess.canView(sender) && !ConfigurationAccess.allowed(sender), "Off blocks menu even for an administrator with wildcard permission");
            check(permissionCommand.hasPermission(sender), "Administrators can restore access through the management variant while their menu is off");
            check(userGrants.get(other).equals(Set.of(ConfigurationAccess.PERMISSION)), "Other players are not affected");
            ConfigurationAccess.setLevel(executor, ConfigurationAccess.Level.MODIFY, add, remove);
            check(command.hasPermission(sender) && ConfigurationAccess.allowed(sender), "On removes test denial and restores access");
            check(!grants.contains("-" + ConfigurationAccess.PERMISSION), "On does not leave a stale denial");
            ConfigurationAccess.setLevel(executor, ConfigurationAccess.Level.OFF, add, remove);
            ConfigurationAccess.setLevel(executor, ConfigurationAccess.Level.OFF, add, remove);
            check(!ConfigurationAccess.allowed(sender), "Repeated off remains blocked");
            ConfigurationAccess.setLevel(executor, ConfigurationAccess.Level.MODIFY, add, remove);
            ConfigurationAccess.setLevel(executor, ConfigurationAccess.Level.MODIFY, add, remove);
            check(ConfigurationAccess.allowed(sender), "Repeated on remains enabled");
            ConfigurationAccess.setLevel(executor, ConfigurationAccess.Level.READONLY, add, remove);
            check(ConfigurationAccess.level(sender) == ConfigurationAccess.Level.READONLY, "Readonly overrides administrator editing wildcard");
            check(command.hasPermission(sender) && ConfigurationAccess.canView(sender), "Readonly may open menu through all aliases");
            for (String action : new String[] {"Search", "BlocksTab", "ToolsTab", "Filter", "General", "Cancel", "LoadAhead:1:30", "Edit:Tool:Tool_Pickaxe_Copper"}) {
                check(ConfigurationAccess.canPerform(sender, action), "Readonly browsing allowed: " + action);
            }
            for (String action : new String[] {"Players", "PlayerLoadAhead:2:30", "Save", "Radius", "RadiusPlus", "RadiusMinus", "Activation", "Defaults", "Clear", "ToolEnabled", "ModeOres", "ModeLogs", "ModeAll", "Select:Tool:Tool_Pickaxe_Copper", "Toggle:Block:Ore_Copper_Stone", "PlayerModify:" + other, "PlayerReadonly:" + other}) {
                check(!ConfigurationAccess.canPerform(sender, action), "Readonly mutations denied on server: " + action);
            }
            ConfigurationAccess.setLevel(executor, ConfigurationAccess.Level.OFF, add, remove);
            check(!ConfigurationAccess.canPerform(sender, "Search"), "Revocation also blocks browsing");
            grants.clear();
            grants.add(ConfigurationAccess.READ_PERMISSION);
            check(command.hasPermission(sender) && !permissionCommand.hasPermission(sender), "Readonly non-admin can view but cannot manage permissions");
            grants.clear();
            grants.add(ConfigurationAccess.PERMISSION);
            check(ConfigurationAccess.canPerform(sender, "Save") && !ConfigurationAccess.canPerform(sender, "PlayerModify:" + other), "Editing own settings does not grant player permission management");
            grants.add(ConfigurationAccess.MANAGE_PERMISSION);
            check(permissionCommand.hasPermission(sender) && ConfigurationAccess.canPerform(sender, "PlayerModify:" + other), "Explicit management grant permits player permission checks");
            check(!userGrants.get(other).contains(ConfigurationAccess.READ_PERMISSION), "Changing caller level never modifies another player");
            grants.clear();
            grants.addAll(Set.of("*", "-" + ConfigurationAccess.PERMISSION, "-" + ConfigurationAccess.READ_PERMISSION, "-" + ConfigurationAccess.MANAGE_PERMISSION));
            check(ConfigurationAccess.level(sender(nativeOwner, grants)) == ConfigurationAccess.Level.MODIFY, "Owner retains editing access despite persisted test denials or external permission changes");
            check(ConfigurationAccess.canManage(sender(nativeOwner, grants)), "Owner always retains permission management");
            check(ConfigurationAccess.level(sender) == ConfigurationAccess.Level.OFF && !ConfigurationAccess.canManage(sender), "Owner protection is not applied to ordinary administrators");
            var ownerId = nativeOwner;
            var writes = new java.util.concurrent.atomic.AtomicInteger();
            java.util.function.BiConsumer<java.util.UUID, Set<String>> write = (uuid, nodes) -> writes.incrementAndGet();
            for (var requested : ConfigurationAccess.Level.values()) {
                check(!ConfigurationAccess.setLevel(ownerId, requested, write, write), "Owner permission changes are rejected: " + requested);
            }
            check(command.hasPermission(sender(nativeOwner, grants)) && permissionCommand.hasPermission(sender(nativeOwner, grants)), "Owner can open settings and manage access regardless of explicit denies");
            check(!ConfigurationAccess.setLevel(nativeOwner, ConfigurationAccess.Level.OFF), "Native permission entry point rejects owner before touching providers");
            check(writes.get() == 0, "Owner command and forged checkbox changes never write permissions");
            check(ConfigurationAccess.setLevel(other, ConfigurationAccess.Level.READONLY, add, remove), "Normal players can still receive readonly access");
            var owner = new PlayerListEntry(java.util.UUID.randomUUID(), "ZHost", true, true, ConfigurationAccess.Level.MODIFY);
            var admin = new PlayerListEntry(java.util.UUID.randomUUID(), "AAdmin", false, true, ConfigurationAccess.Level.READONLY);
            var editor = new PlayerListEntry(java.util.UUID.randomUUID(), "BEditor", false, false, ConfigurationAccess.Level.MODIFY);
            var reader = new PlayerListEntry(java.util.UUID.randomUUID(), "CReader", false, false, ConfigurationAccess.Level.READONLY);
            var blocked = new PlayerListEntry(java.util.UUID.randomUUID(), "DBlocked", false, false, ConfigurationAccess.Level.OFF);
            var players = java.util.List.of(reader, editor, blocked, admin, owner).stream().sorted(PlayerListEntry.ORDER).toList();
            check(players.getFirst() == owner && players.get(1) == admin, "Owner is first regardless of nickname; remaining players are alphabetical");
            check(owner.displayName().equals("ZHost (owner)") && admin.displayName().equals("AAdmin (admin)") && editor.displayName().equals("BEditor"), "Owner/admin labels do not overlap or label ordinary editors");
            check(players.stream().filter(PlayerListEntry.Filter.ADMINS::matches).toList().equals(java.util.List.of(owner, admin)), "Admins filter follows administrator role, independent of menu access");
            check(players.stream().filter(PlayerListEntry.Filter.MODIFY::matches).toList().equals(java.util.List.of(owner, editor)), "Modify filter follows actual editing access");
            check(players.stream().filter(PlayerListEntry.Filter.READONLY::matches).toList().equals(java.util.List.of(admin, reader)), "Readonly filter excludes editors and blocked users");
            check(players.stream().filter(PlayerListEntry.Filter.UNSELECTED::matches).toList().equals(java.util.List.of(blocked)), "Unselected filter shows blocked players");
            check(players.stream().filter(PlayerListEntry.Filter.SELECTED::matches).count() == 4, "Selected includes editors and readers");
            var filter = PlayerListEntry.Filter.ALL;
            for (int step = 0; step < PlayerListEntry.Filter.values().length; step++) filter = filter.next();
            check(filter == PlayerListEntry.Filter.ALL, "Player filter cycle includes all six options and wraps");
            Path policyRoot = java.nio.file.Files.createTempDirectory(Path.of("build"), "command-policy-");
            var policy = new CommandAccessSettings(policyRoot.resolve("commands.properties"), warning -> { throw new AssertionError(warning); });
            ConfigurationAccess.configureCommands(policy);
            var regular = sender(java.util.UUID.randomUUID(), Set.of());
            var regularEditor = sender(java.util.UUID.randomUUID(), Set.of(ConfigurationAccess.PERMISSION));
            check(ConfigurationAccess.commandMode() == CommandAccessSettings.Mode.ADMIN_ONLY && !permissionCommand.hasPermission(regular), "Admin only is selected initially and blocks ordinary users");
            check(!ConfigurationAccess.canPerform(regularEditor, "CommandAll"), "Own-settings editing cannot change the server command policy");
            ConfigurationAccess.saveCommandMode(CommandAccessSettings.Mode.ALL);
            check(permissionCommand.hasPermission(regular) && command.hasPermission(regular), "All allows the management variant through all menu aliases without existing menu access");
            check(!ConfigurationAccess.canView(regular), "Command policy does not silently change the player's menu role");
            check(!ConfigurationAccess.canManage(regular) && !ConfigurationAccess.canPerform(regularEditor, "CommandAdminOnly"), "Public command access never grants the ability to change the policy itself");
            check(!ConfigurationAccess.canUsePermissionCommands(null), "All does not grant access to a missing sender");
            check(!ConfigurationAccess.setLevel(nativeOwner, ConfigurationAccess.Level.OFF), "All policy cannot remove owner access");
            check(ConfigurationAccess.canPerform(sender(nativeOwner, grants), "CommandAdminOnly"), "Owner can always restore Admin only");
            ConfigurationAccess.saveCommandMode(CommandAccessSettings.Mode.ADMIN_ONLY);
            check(!permissionCommand.hasPermission(regular), "Restoring Admin only immediately revokes public command use");
            check(!ConfigurationAccess.allowed(null), "Missing permission holder fails closed");
            System.out.println("Permissions: aliases, denied access, grants, wildcards and revocation passed.");
        } catch (Throwable failure) {
            var trace = new java.io.StringWriter();
            failure.printStackTrace(new java.io.PrintWriter(trace));
            java.nio.file.Files.writeString(Path.of("build/permissions-test-error.txt"), trace.toString());
            throw failure;
        }
    }
    private static CommandSender sender(java.util.UUID uuid, Set<String> grants) {
        return (CommandSender) Proxy.newProxyInstance(PermissionsTest.class.getClassLoader(),
            new Class<?>[] {CommandSender.class}, (proxy, method, values) -> {
                if (method.getName().equals("getUuid")) return uuid;
                if (!method.getName().equals("hasPermission")) throw new AssertionError("Unexpected operation " + method.getName());
                Boolean result = values[0] instanceof String node
                    ? PermissionsModule.hasPermission(grants, node)
                    : PermissionsModule.hasPermission(grants, (PermissionQuery) values[0]);
                return Boolean.TRUE.equals(result);
            });
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
