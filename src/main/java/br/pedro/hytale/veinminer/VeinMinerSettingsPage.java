package br.pedro.hytale.veinminer;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.modules.i18n.I18nModule;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.io.IOException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/** Duas grades compartilham o rascunho; somente Salvar aplica as alterações. */
public final class VeinMinerSettingsPage extends InteractiveCustomUIPage<VeinMinerSettingsPage.Data> {
    private static final int COLUMNS = 5;
    private record Entry(String id, Item item, String name, String search) {}
    private final SettingsRepository settings;
    private final Set<String> blocks;
    private final Set<String> tools;
    private final List<Entry> blockCatalog;
    private List<Entry> toolCatalog;
    private final CatalogWindow<Entry> window = new CatalogWindow<>();
    private final Set<String> allowedBlocks = new HashSet<>();
    private final Set<String> allowedTools = new HashSet<>();
    private final Map<String, String> visibleCards = new HashMap<>();
    private int radius;
    private PlayerSettings.Activation activation;
    private String search = "";
    private boolean selectedOnly;
    private boolean toolMenu;
    private ScheduledFuture<?> pendingSearch;
    private long searchRevision;
    private boolean dismissed;
    private String status = "Alterações aplicadas ao salvar.";

    VeinMinerSettingsPage(PlayerRef playerRef, SettingsRepository settings) {
        super(playerRef, CustomPageLifetime.CanDismiss, Data.CODEC);
        this.settings = settings;
        PlayerSettings current = settings.get(playerRef.getUuid());
        blocks = new HashSet<>(current.blocks());
        tools = new HashSet<>(current.tools());
        radius = current.radius();
        activation = current.activation();
        blockCatalog = BlockType.getAssetMap().getAssetMap().values().stream()
            .filter(b -> b.getItem() != null && b.getGathering() != null && b.getGathering().getBreaking() != null)
            .filter(b -> !b.getId().startsWith("*") && !b.getId().equals(BlockType.EMPTY_KEY))
            .map(b -> entry(b.getId(), b.getItem())).sorted(order(blocks)).toList();
        blockCatalog.forEach(e -> allowedBlocks.add(e.id()));
    }

    private List<Entry> catalog() {
        if (!toolMenu) return blockCatalog;
        // O catálogo de ferramentas só é traduzido e ordenado quando sua aba é aberta.
        if (toolCatalog == null) {
            toolCatalog = Item.getAssetMap().getAssetMap().values().stream()
                .filter(i -> i.getTool() != null && !i.getId().startsWith("*") && !i.isState())
                .map(i -> entry(i.getId(), i)).sorted(order(tools)).toList();
            toolCatalog.forEach(e -> allowedTools.add(e.id()));
        }
        return toolCatalog;
    }

    private Entry entry(String id, Item item) {
        String name = I18nModule.get().getMessage(playerRef.getLanguage(), item.getTranslationKey());
        if (name == null || name.isBlank()) name = id.replace('_', ' ');
        return new Entry(id, item, name, CatalogSearch.normalize(id + " " + name));
    }

    private static Comparator<Entry> order(Set<String> selected) {
        return Comparator.<Entry, Boolean>comparing(e -> !selected.contains(e.id())).thenComparing(Entry::search);
    }

    @Override
    public void build(Ref<EntityStore> ref, UICommandBuilder commands, UIEventBuilder events, Store<EntityStore> store) {
        commands.append("Pages/VeinMinerSettings.ui");
        events.addEventBinding(CustomUIEventBindingType.ValueChanged, "#Search",
            EventData.of("Action", "Search").append("@Search", "#Search.Value"), false);
        events.addEventBinding(CustomUIEventBindingType.ValueChanged, "#Radius",
            EventData.of("Action", "Radius").append("@Radius", "#Radius.Value"), false);
        for (String action : List.of("BlocksTab", "ToolsTab", "Filter", "RadiusMinus", "RadiusPlus",
                "Activation", "Defaults", "Clear", "Cancel")) bind(events, "#" + action, action);
        // Lê também o valor atual ao salvar, mesmo sem sair do campo numérico.
        events.addEventBinding(CustomUIEventBindingType.Activating, "#Save",
            EventData.of("Action", "Save").append("@Radius", "#Radius.Value"));
        commands.set("#Radius.Value", radius);
        renderGrid(commands, events);
        renderControls(commands);
    }

    private Set<String> selection() { return toolMenu ? tools : blocks; }

    private void renderGrid(UICommandBuilder commands, UIEventBuilder events) {
        cancelPendingSearch();
        String[] words = CatalogSearch.words(search);
        List<Entry> filtered = catalog().stream()
            .filter(e -> !selectedOnly || selection().contains(e.id()))
            .filter(e -> CatalogSearch.matches(e.search(), words)).toList();
        commands.clear("#Grid");
        visibleCards.clear();
        window.reset(filtered);
        appendBatch(commands, events);
        if (filtered.isEmpty()) commands.appendInline("#Grid",
            "Label { Text: \"Nenhum resultado. Tente outro nome ou ID.\"; Anchor: (Height: 40); Style: (FontSize: 14, TextColor: #a8b6c6); }");
    }

    private void appendBatch(UICommandBuilder commands, UIEventBuilder events) {
        List<Entry> batch = window.nextBatch();
        int start = window.loaded() - batch.size();
        for (int offset = 0; offset < batch.size(); offset++) {
            int i = start + offset;
            int row = i / COLUMNS;
            if (i % COLUMNS == 0) commands.appendInline("#Grid",
                "Group #Row" + row + " { LayoutMode: Left; Anchor: (Height: 156); }");
            Entry entry = batch.get(offset);
            String card = "#Row" + row + "[" + i % COLUMNS + "]";
            String key = (toolMenu ? "Tool:" : "Block:") + entry.id();
            visibleCards.put(key, card);
            commands.append("#Row" + row, "Pages/VeinMinerItemCard.ui");
            commands.set(card + " #Preview.ItemId", entry.item().getId());
            commands.set(card + " #Name.Text", entry.name());
            commands.set(card + " #Id.Text", entry.id());
            commands.set(card + " #Selected.Value", selection().contains(entry.id()));
            bind(events, card + " #Toggle", "Toggle:" + key);
            events.addEventBinding(CustomUIEventBindingType.ValueChanged, card + " #Selected",
                EventData.of("Action", "Select:" + key).append("@Selected", card + " #Selected.Value"));
            if (window.hasMore() && i >= window.loaded() - COLUMNS * 2) {
                EventData loadAhead = EventData.of("Action", "LoadAhead:" + window.cursor());
                events.addEventBinding(CustomUIEventBindingType.MouseEntered, card + " #Toggle", loadAhead, false);
            }
        }
    }

    private void renderControls(UICommandBuilder commands) {
        commands.set("#Filter.Text", selectedOnly ? "Mostrar todos" : "Somente marcados");
        commands.set("#BlocksTab.Text", "Blocos (" + blocks.size() + ")" + (toolMenu ? "" : " •"));
        commands.set("#ToolsTab.Text", "Ferramentas (" + tools.size() + ")" + (toolMenu ? " •" : ""));
        commands.set("#CatalogTitle.Text", (toolMenu ? "FERRAMENTAS PERMITIDAS" : "BLOCOS AFETADOS")
            + " (" + window.total() + ")");
        commands.set("#ActivationName.Text", activation.label + " (");
        // HotkeyLabel resolve o atalho no cliente, acompanhando remapeamentos e controle.
        commands.set("#ActivationKey.InputBindingKey", activation.inputBinding);
        commands.set("#Summary.Text", blocks.size() + " blocos · " + tools.size() + " ferramentas");
        commands.set("#ActivationHint.Text", switch (activation) {
            case CROUCH -> "Segure agachar ao minerar. Ao soltar, a mineração volta ao normal.";
            case WALK -> "Ativa enquanto o jogo indica caminhar. Requer estar em movimento.";
            case SPRINT -> "Ativa enquanto o jogo indica correr. Requer estar em movimento.";
        });
        commands.set("#Status.Text", status);
    }

    private static void bind(UIEventBuilder events, String selector, String action) {
        events.addEventBinding(CustomUIEventBindingType.Activating, selector, EventData.of("Action", action));
    }

    @Override
    public void handleDataEvent(Ref<EntityStore> ref, Store<EntityStore> store, Data data) {
        if (dismissed) return;
        if (data.action == null) { sendUpdate(); return; }
        if (data.action.equals("Cancel")) { dismissed = true; cancelPendingSearch(); close(); return; }
        if (data.action.equals("Search")) {
            String query = data.search == null ? "" : data.search;
            String normalized = CatalogSearch.normalize(query.substring(0, Math.min(query.length(), 128)));
            if (search.equals(normalized)) { sendUpdate(); return; }
            search = normalized;
            cancelPendingSearch();
            long revision = searchRevision;
            pendingSearch = store.getExternalData().getWorld().scheduleAfter(() -> {
                if (dismissed || searchRevision != revision || !ref.isValid()) return;
                var searchCommands = new UICommandBuilder();
                var searchEvents = new UIEventBuilder();
                renderGrid(searchCommands, searchEvents);
                renderControls(searchCommands);
                sendUpdate(searchCommands, searchEvents, false);
            }, 200, TimeUnit.MILLISECONDS);
            sendUpdate();
            return;
        }
        var commands = new UICommandBuilder();
        var events = new UIEventBuilder();
        boolean refreshGrid = false;
        switch (data.action) {
            case "BlocksTab", "ToolsTab" -> {
                toolMenu = data.action.equals("ToolsTab");
                search = ""; selectedOnly = false;
                commands.set("#Search.Value", "");
                refreshGrid = true;
            }
            case "Filter" -> { selectedOnly = !selectedOnly; refreshGrid = true; }
            case "Radius" -> {
                if (validRadius(data.radius)) { radius = data.radius; status = "Distância alterada. Salve para aplicar."; }
                else status = "Digite uma distância inteira de 1 a 32.";
            }
            case "RadiusMinus", "RadiusPlus" -> {
                radius = Math.clamp(radius + (data.action.equals("RadiusPlus") ? 1 : -1), 1, PlayerSettings.MAX_RADIUS);
                commands.set("#Radius.Value", radius);
                status = "Distância alterada. Salve para aplicar.";
            }
            case "Activation" -> {
                var values = PlayerSettings.Activation.values();
                activation = values[(activation.ordinal() + 1) % values.length];
                status = "Ativação alterada. Salve para aplicar.";
            }
            case "Clear" -> {
                selection().clear(); refreshGrid = true;
                status = toolMenu ? "Ferramentas desmarcadas no rascunho." : "Blocos desmarcados no rascunho.";
            }
            case "Defaults" -> {
                selection().clear();
                selection().addAll(toolMenu ? PlayerSettings.DEFAULT_TOOLS : PlayerSettings.DEFAULT_BLOCKS);
                refreshGrid = true;
                status = "Padrões desta lista restaurados no rascunho.";
            }
            case "Save" -> {
                if (!validRadius(data.radius)) { status = "Digite uma distância inteira de 1 a 32 antes de salvar."; break; }
                radius = data.radius;
                try {
                    settings.save(playerRef.getUuid(), new PlayerSettings(blocks, tools, radius, activation));
                    playerRef.sendMessage(Message.raw("Advenced Vein Mining salvo: " + blocks.size() + " blocos, " + tools.size()
                        + " ferramentas, alcance " + radius + ", ativação: " + activation.label + "."));
                    dismissed = true; cancelPendingSearch(); close(); return;
                } catch (IOException error) { status = "Não foi possível salvar. O rascunho continua neste menu."; }
            }
            default -> {
                if (data.action.startsWith("LoadAhead:")) {
                    if (!window.acceptsCursor(data.action.substring("LoadAhead:".length()))) { sendUpdate(); return; }
                    if (pendingSearch != null) refreshGrid = true;
                    else appendBatch(commands, events);
                    break;
                }
                boolean toggle = data.action.startsWith("Toggle:");
                if (!toggle && !data.action.startsWith("Select:")) { sendUpdate(); return; }
                String key = data.action.substring(7);
                boolean tool = key.startsWith("Tool:");
                if (!tool && !key.startsWith("Block:")) { sendUpdate(); return; }
                String id = key.substring(tool ? 5 : 6);
                if (!(tool ? allowedTools : allowedBlocks).contains(id)) { sendUpdate(); return; }
                Set<String> chosen = tool ? tools : blocks;
                boolean checked = toggle ? !chosen.contains(id) : Boolean.TRUE.equals(data.selected);
                if (checked) chosen.add(id); else chosen.remove(id);
                String card = visibleCards.get(key);
                if (card != null) commands.set(card + " #Selected.Value", checked);
                // Atualiza só a caixa marcada para conservar a posição de rolagem.
                if (selectedOnly && tool == toolMenu) refreshGrid = true;
                status = "Seleção alterada. Salve para aplicar.";
            }
        }
        if (refreshGrid) renderGrid(commands, events);
        renderControls(commands);
        sendUpdate(commands, events, false);
    }

    private static boolean validRadius(Integer value) { return value != null && value >= 1 && value <= PlayerSettings.MAX_RADIUS; }

    private void cancelPendingSearch() {
        searchRevision++;
        if (pendingSearch != null) { pendingSearch.cancel(false); pendingSearch = null; }
    }

    @Override
    public void onDismiss(Ref<EntityStore> ref, Store<EntityStore> store) {
        dismissed = true;
        cancelPendingSearch();
        super.onDismiss(ref, store);
    }

    public static final class Data {
        public static final BuilderCodec<Data> CODEC = BuilderCodec.builder(Data.class, Data::new)
            .append(new KeyedCodec<>("Action", Codec.STRING), (d, v) -> d.action = v, d -> d.action).add()
            .append(new KeyedCodec<>("@Search", Codec.STRING), (d, v) -> d.search = v, d -> d.search).add()
            .append(new KeyedCodec<>("@Radius", Codec.INTEGER), (d, v) -> d.radius = v, d -> d.radius).add()
            .append(new KeyedCodec<>("@Selected", Codec.BOOLEAN), (d, v) -> d.selected = v, d -> d.selected).add()
            .build();
        private String action;
        private String search;
        private Integer radius;
        private Boolean selected;
        public Data() {}
    }
}
