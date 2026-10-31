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
import com.hypixel.hytale.server.core.ui.Value;
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
    private final UiLanguage language;
    private final Set<String> blocks;
    private final Set<String> tools;
    private final List<Entry> blockCatalog;
    private List<Entry> toolCatalog;
    private final CatalogWindow<Entry> window = new CatalogWindow<>();
    private final Set<String> allowedBlocks = new HashSet<>();
    private final Set<String> allowedTools = new HashSet<>();
    private final Map<String, String> visibleCards = new HashMap<>();
    private final Map<String, PlayerSettings.ToolBlockMode> toolModes = new HashMap<>();
    private String editingTool;
    private int radius;
    private PlayerSettings.Activation activation;
    private String search = "";
    private enum SelectionFilter {
        ALL, SELECTED, UNSELECTED;

        boolean matches(boolean selected) {
            return this == ALL || (this == SELECTED ? selected : !selected);
        }

        SelectionFilter next() { return values()[(ordinal() + 1) % values().length]; }
    }

    private SelectionFilter selectionFilter = SelectionFilter.ALL;
    private boolean toolMenu;
    private ScheduledFuture<?> pendingSearch;
    private long searchRevision;
    private boolean dismissed;
    private String statusKey = "status.pending";

    VeinMinerSettingsPage(PlayerRef playerRef, SettingsRepository settings) {
        super(playerRef, CustomPageLifetime.CanDismiss, Data.CODEC);
        this.settings = settings;
        language = UiLanguage.from(playerRef.getLanguage());
        PlayerSettings current = settings.get(playerRef.getUuid());
        blocks = new HashSet<>(current.blocks());
        tools = new HashSet<>(current.tools());
        toolModes.putAll(current.toolModes());
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
        String name = I18nModule.get().getMessage(language.assetLocale, item.getTranslationKey());
        if (name == null || name.isBlank()) name = I18nModule.get().getMessage("en-US", item.getTranslationKey());
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
                "Activation", "Defaults", "Clear", "Cancel", "General")) bind(events, "#" + action, action);
        // Lê também o valor atual ao salvar, mesmo sem sair do campo numérico.
        events.addEventBinding(CustomUIEventBindingType.Activating, "#Save",
            EventData.of("Action", "Save").append("@Radius", "#Radius.Value"));
        commands.set("#Radius.Value", radius);
        renderGrid(commands, events);
        renderControls(commands, events);
    }

    private Set<String> selection() { return toolMenu ? tools : blocks; }

    private void renderGrid(UICommandBuilder commands, UIEventBuilder events) {
        cancelPendingSearch();
        String[] words = CatalogSearch.words(search);
        List<Entry> filtered = catalog().stream()
            .filter(e -> selectionFilter.matches(selection().contains(e.id())))
            .filter(e -> CatalogSearch.matches(e.search(), words)).toList();
        commands.clear("#Grid");
        visibleCards.clear();
        window.reset(filtered);
        appendBatch(commands, events);
        if (filtered.isEmpty()) {
            commands.appendInline("#Grid", "Label #Empty { Anchor: (Height: 40); Style: (FontSize: 14, TextColor: #a8b6c6); }");
            commands.set("#Empty.Text", language.text("empty"));
        }
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
            selectedCard(commands, card, selection().contains(entry.id()));
            bind(events, card + " #Toggle", (toolMenu ? "Edit:" : "Toggle:") + key);
            events.addEventBinding(CustomUIEventBindingType.ValueChanged, card + " #Selected",
                EventData.of("Action", "Select:" + key).append("@Selected", card + " #Selected.Value"));
            if (window.hasMore() && i >= window.loaded() - COLUMNS * 2) {
                EventData loadAhead = EventData.of("Action", "LoadAhead:" + window.cursor());
                events.addEventBinding(CustomUIEventBindingType.MouseEntered, card + " #Toggle", loadAhead, false);
            }
        }
    }

    private void renderControls(UICommandBuilder commands, UIEventBuilder events) {
        commands.set("#GeneralSettings.Visible", editingTool == null);
        commands.set("#ToolSettings.Visible", editingTool != null);
        if (editingTool != null) {
            for (String action : List.of("ModeOres", "ModeLogs", "ModeAll")) {
                events.addEventBinding(CustomUIEventBindingType.Activating, "#" + action,
                    EventData.of("Action", action).append("Tool", editingTool));
            }
            events.addEventBinding(CustomUIEventBindingType.ValueChanged, "#ToolEnabled",
                EventData.of("Action", "ToolEnabled").append("Tool", editingTool)
                    .append("@Selected", "#ToolEnabled.Value"));
            Item item = Item.getAssetMap().getAsset(editingTool);
            var mode = toolModes.getOrDefault(editingTool, AssetRules.defaultMode(item));
            commands.set("#ToolHeading.Text", language.text("tool.heading"));
            commands.set("#ToolPreview.ItemId", editingTool);
            commands.set("#ToolName.Text", entry(editingTool, item).name());
            commands.set("#ToolEnabled.Value", tools.contains(editingTool));
            commands.set("#ToolEnabledLabel.Text", language.text("tool.enabled"));
            commands.set("#ToolTypeLabel.Text", language.text("tool.type"));
            commands.set("#ToolHint.Text", language.text("tool.hint"));
            commands.set("#General.Text", language.text("tool.general"));
            commands.set("#ModeOres.Text", language.text("tool.ores"));
            selectedButton(commands, "#ModeOres", mode == PlayerSettings.ToolBlockMode.ORES);
            commands.set("#ModeLogs.Text", language.text("tool.logs"));
            selectedButton(commands, "#ModeLogs", mode == PlayerSettings.ToolBlockMode.LOGS);
            commands.set("#ModeAll.Text", language.text("tool.all"));
            selectedButton(commands, "#ModeAll", mode == PlayerSettings.ToolBlockMode.ALL);
        }
        commands.set("#PageHeading.Text", language.text("title"));
        commands.set("#Search.PlaceholderText", language.text("search"));
        commands.set("#RangeSection.Text", language.text("range.section"));
        commands.set("#RangeTitle.Text", language.text("range.title"));
        commands.set("#RangeHint.Text", language.text("range.hint"));
        commands.set("#ActivationTitle.Text", language.text("activation.title"));
        commands.set("#ControlsHint.Text", language.text("controls.hint"));
        commands.set("#Defaults.Text", language.text("defaults"));
        commands.set("#Clear.Text", language.text("clear"));
        commands.set("#Cancel.Text", language.text("cancel"));
        commands.set("#Save.Text", language.text("save"));
        selectedButton(commands, "#BlocksTab", !toolMenu);
        selectedButton(commands, "#ToolsTab", toolMenu);
        commands.set("#Filter.Text", language.text("filter.mode." + selectionFilter.name()));
        commands.set("#BlocksTab.Text", language.text("tab.blocks") + " (" + blocks.size() + ")");
        commands.set("#ToolsTab.Text", language.text("tab.tools") + " (" + tools.size() + ")");
        commands.set("#CatalogTitle.Text", language.text(toolMenu ? "catalog.tools" : "catalog.blocks")
            + " (" + window.total() + ")");
        commands.set("#ActivationName.Text", language.text("activation." + activation.name()) + " (");
        // HotkeyLabel resolve o atalho no cliente, acompanhando remapeamentos e controle.
        commands.set("#ActivationKey.InputBindingKey", activation.inputBinding);
        commands.set("#Summary.Text", language.text("summary", blocks.size(), tools.size()));
        commands.set("#ActivationHint.Text", language.text("hint." + activation.name()));
        commands.set("#Status.Text", language.text(statusKey));
    }

    private static void selectedButton(UICommandBuilder commands, String selector, boolean selected) {
        commands.set(selector + ".Style", selected
            ? Value.ref("Pages/VeinMinerSettings.ui", "SelectedButtonStyle")
            : Value.ref("Common.ui", "SmallSecondaryTextButtonStyle"));
    }

    private static void selectedCard(UICommandBuilder commands, String card, boolean selected) {
        commands.set(card + " #Toggle.Style", Value.ref("Pages/VeinMinerSettings.ui",
            selected ? "SelectedCardButtonStyle" : "CardButtonStyle"));
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
                renderControls(searchCommands, searchEvents);
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
                editingTool = null;
                search = ""; selectionFilter = SelectionFilter.ALL;
                commands.set("#Search.Value", "");
                refreshGrid = true;
            }
            case "General" -> editingTool = null;
            case "ModeOres", "ModeLogs", "ModeAll" -> {
                if (data.tool != null && allowedTools.contains(data.tool)) {
                    toolModes.put(data.tool, switch (data.action) {
                        case "ModeOres" -> PlayerSettings.ToolBlockMode.ORES;
                        case "ModeLogs" -> PlayerSettings.ToolBlockMode.LOGS;
                        default -> PlayerSettings.ToolBlockMode.ALL;
                    });
                    statusKey = "status.selection";
                }
            }
            case "ToolEnabled" -> {
                if (data.tool != null && allowedTools.contains(data.tool)) {
                    if (Boolean.TRUE.equals(data.selected)) tools.add(data.tool); else tools.remove(data.tool);
                    String card = visibleCards.get("Tool:" + data.tool);
                    if (card != null) {
                        commands.set(card + " #Selected.Value", tools.contains(data.tool));
                        selectedCard(commands, card, tools.contains(data.tool));
                    }
                    if (selectionFilter != SelectionFilter.ALL) refreshGrid = true;
                    statusKey = "status.selection";
                }
            }
            case "Filter" -> { selectionFilter = selectionFilter.next(); refreshGrid = true; }
            case "Radius" -> {
                if (validRadius(data.radius)) { radius = data.radius; statusKey = "status.range"; }
                else statusKey = "status.invalidRange";
            }
            case "RadiusMinus", "RadiusPlus" -> {
                radius = Math.clamp(radius + (data.action.equals("RadiusPlus") ? 1 : -1), 1, PlayerSettings.MAX_RADIUS);
                commands.set("#Radius.Value", radius);
                statusKey = "status.range";
            }
            case "Activation" -> {
                var values = PlayerSettings.Activation.values();
                activation = values[(activation.ordinal() + 1) % values.length];
                statusKey = "status.activation";
            }
            case "Clear" -> {
                selection().clear(); refreshGrid = true;
                statusKey = toolMenu ? "status.clearTools" : "status.clearBlocks";
            }
            case "Defaults" -> {
                selection().clear();
                var defaults = AssetRules.defaults();
                selection().addAll(toolMenu ? defaults.tools() : defaults.blocks());
                if (toolMenu) toolModes.clear();
                refreshGrid = true;
                statusKey = "status.defaults";
            }
            case "Save" -> {
                if (!validRadius(data.radius)) { statusKey = "status.invalidSave"; break; }
                radius = data.radius;
                try {
                    settings.save(playerRef.getUuid(), new PlayerSettings(blocks, tools, radius, activation, toolModes));
                    playerRef.sendMessage(Message.raw(language.text("saved", blocks.size(), tools.size(), radius,
                        language.text("activation." + activation.name()))));
                    dismissed = true; cancelPendingSearch(); close(); return;
                } catch (IOException error) { statusKey = "status.saveError"; }
            }
            default -> {
                if (data.action.startsWith("LoadAhead:")) {
                    if (!window.acceptsCursor(data.action.substring("LoadAhead:".length()))) { sendUpdate(); return; }
                    if (pendingSearch != null) refreshGrid = true;
                    else appendBatch(commands, events);
                    break;
                }
                if (data.action.startsWith("Edit:Tool:")) {
                    String id = data.action.substring("Edit:Tool:".length());
                    if (toolMenu && allowedTools.contains(id)) editingTool = id;
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
                if (card != null) {
                    commands.set(card + " #Selected.Value", checked);
                    selectedCard(commands, card, checked);
                }
                // Atualiza só a caixa marcada para conservar a posição de rolagem.
                if (selectionFilter != SelectionFilter.ALL && tool == toolMenu) refreshGrid = true;
                statusKey = "status.selection";
            }
        }
        if (refreshGrid) renderGrid(commands, events);
        renderControls(commands, events);
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
            .append(new KeyedCodec<>("Tool", Codec.STRING), (d, v) -> d.tool = v, d -> d.tool).add()
            .append(new KeyedCodec<>("@Search", Codec.STRING), (d, v) -> d.search = v, d -> d.search).add()
            .append(new KeyedCodec<>("@Radius", Codec.INTEGER), (d, v) -> d.radius = v, d -> d.radius).add()
            .append(new KeyedCodec<>("@Selected", Codec.BOOLEAN), (d, v) -> d.selected = v, d -> d.selected).add()
            .build();
        private String action;
        private String tool;
        private String search;
        private Integer radius;
        private Boolean selected;
        public Data() {}
    }
}
