package br.pedro.hytale.veinminer;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;

public final class VeinMinerPlugin extends JavaPlugin {
    private VeinMiningSystem mining;

    public VeinMinerPlugin(JavaPluginInit init) {
        super(init);
    }

    @Override
    protected void setup() {
        // Mantém o local original para preservar as preferências após renomear o plugin.
        var settings = new SettingsRepository(getDataDirectory().resolveSibling("Pedro_VeinMiner").resolve("players"),
            message -> getLogger().atWarning().log(message));
        mining = new VeinMiningSystem(settings);
        getEntityStoreRegistry().registerSystem(mining);
        getCommandRegistry().registerCommand(new SettingsCommand(settings));
        getLogger().atInfo().log("Advenced Vein Mining 0.5.0 loaded. Use /veinminer to configure.");
    }

    @Override
    protected void shutdown() {
        if (mining != null) mining.close();
    }

    private static final class SettingsCommand extends AbstractPlayerCommand {
        private final SettingsRepository settings;

        SettingsCommand(SettingsRepository settings) {
            super("veinminer", "server.advancedVeinMining.commands.settings.description");
            addAliases("advancedveinminer", "advancedveinmining", "veinmining");
            this.settings = settings;
            requireNoPermission();
        }

        @Override
        protected void execute(CommandContext context, Store<EntityStore> store, Ref<EntityStore> ref,
                               PlayerRef playerRef, World world) {
            Player player = store.getComponent(ref, Player.getComponentType());
            if (player != null) player.getPageManager().openCustomPage(ref, store,
                new VeinMinerSettingsPage(playerRef, settings));
        }
    }
}
