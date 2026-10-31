package br.pedro.hytale.veinminer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;

public final class SettingsTest {
    public static void main(String[] args) throws Exception {
        for (String locale : new String[] {"pt", "pt-BR", "PT_br", " pt-PT ", "pt-AO"}) {
            check(UiLanguage.from(locale) == UiLanguage.PORTUGUESE, "Detectar portugues: " + locale);
        }
        for (String locale : new String[] {null, "", "en-US", "en-GB", "es-ES", "fr-FR", "de-DE", "ja-JP", "ptx", "unknown"}) {
            check(UiLanguage.from(locale) == UiLanguage.ENGLISH, "Usar ingles como fallback: " + locale);
        }
        check(UiLanguage.PORTUGUESE.keys().equals(UiLanguage.ENGLISH.keys()), "Ambos os idiomas cobrem todos os textos");
        for (UiLanguage language : UiLanguage.values()) {
            for (String key : language.keys()) {
                check(!language.text(key, 5, 2, 8, "Crouch").isBlank(), "Traducao formatada valida: " + key);
            }
        }
        check(UiLanguage.PORTUGUESE.text("activation.CROUCH").equals("Agachar"), "Acao em portugues");
        check(UiLanguage.ENGLISH.text("activation.CROUCH").equals("Crouch"), "Acao em ingles");
        check(UiLanguage.PORTUGUESE.text("status.pending").contains("Alterações"), "Preservar acentos UTF-8");
        check(UiLanguage.ENGLISH.text("summary", 5, 2).equals("5 blocks · 2 tools"), "Resumo em ingles com contagem");
        check(UiLanguage.PORTUGUESE.text("saved", 5, 2, 8, "Agachar").contains("ativação: Agachar"), "Mensagem salva localizada");
        check(UiLanguage.from("fr-FR").assetLocale.equals("en-US"), "Nomes do catalogo usam ingles para outros idiomas");
        check(UiLanguage.from("pt-PT").assetLocale.equals("pt-BR"), "Nomes portugueses usam assets portugueses disponiveis");

        PlayerSettings defaults = PlayerSettings.defaults();
        check(defaults.activation() == PlayerSettings.Activation.CROUCH, "Agachar por padrao");
        check(defaults.radius() == 8 && !defaults.blocks().isEmpty(), "Padrao preserva minerios");
        check(defaults.tools().equals(PlayerSettings.DEFAULT_TOOLS), "Picaretas permitidas por padrao");
        check(defaults.allowsTool("Tool_Pickaxe_Copper", false), "Aceitar ferramenta marcada");
        check(!defaults.allowsTool("Tool_Pickaxe_Copper", true), "Parar com ferramenta quebrada");
        check(!defaults.allowsTool("Tool_Hatchet_Copper", false), "Rejeitar ferramenta nao marcada");
        check(!defaults.activation().active(false, false, false, false), "Nao ativar sem agachar");
        check(defaults.activation().active(true, false, false, false), "Ativar agachado");
        check(!defaults.activation().active(true, true, false, false), "Nao ativar por agachamento forcado");
        check(!defaults.activation().active(false, false, true, true), "Correr e caminhar nao substituem agachar");
        check(PlayerSettings.Activation.WALK.active(false, false, true, false), "Ativacao alternativa caminhar");
        check(PlayerSettings.Activation.SPRINT.active(false, false, false, true), "Ativacao alternativa correr");
        check(!PlayerSettings.Activation.SPRINT.active(true, false, false, false), "Alternativa nao aciona ao agachar");
        for (int invalid : new int[] { -1, 0, 33, Integer.MAX_VALUE }) {
            try { new PlayerSettings(Set.of(), invalid, defaults.activation()); throw new AssertionError("Aceitou raio invalido"); }
            catch (IllegalArgumentException expected) { }
        }
        var draft = new java.util.HashSet<>(defaults.blocks());
        var snapshot = new PlayerSettings(draft, 1, defaults.activation());
        draft.clear();
        check(!snapshot.blocks().isEmpty(), "Rascunho nao modifica configuracao em uso");

        Files.createDirectories(Path.of("build", "settings-tests"));
        Path root = Files.createTempDirectory(Path.of("build", "settings-tests"), "run-");
        var warnings = new ArrayList<String>();
        var repository = new SettingsRepository(root, warnings::add);
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        check(repository.get(first).equals(defaults), "Jogador novo recebe padroes");
        var chosen = new PlayerSettings(Set.of("Ore_Copper_Stone", "Rock_Stone"),
            Set.of("Tool_Hatchet_Copper", "CustomMod_MiningTool"), 12, PlayerSettings.Activation.WALK);
        check(chosen.allowsTool("Tool_Hatchet_Copper", false), "Permitir outra categoria de ferramenta marcada");
        check(chosen.allowsTool("CustomMod_MiningTool", false), "Permitir ferramenta de outro mod marcada");
        check(!chosen.allowsTool("Tool_Pickaxe_Copper", false), "Desmarcar picareta realmente impede uso");
        repository.save(first, chosen);
        check(repository.get(first).equals(chosen), "Aplicar configuracao depois de salvar");
        check(repository.get(second).equals(defaults), "Isolar jogadores");
        var restarted = new SettingsRepository(root, warnings::add);
        check(restarted.get(first).equals(chosen), "Persistir entre reinicios");
        var modesDraft = new java.util.HashMap<String, PlayerSettings.ToolBlockMode>();
        modesDraft.put("OtherMod:Pickaxe", PlayerSettings.ToolBlockMode.ALL);
        modesDraft.put("OtherMod:Hatchet", PlayerSettings.ToolBlockMode.LOGS);
        var perTool = new PlayerSettings(chosen.blocks(), chosen.tools(), chosen.radius(), chosen.activation(), modesDraft);
        modesDraft.clear();
        repository.save(first, perTool);
        check(new SettingsRepository(root, warnings::add).get(first).equals(perTool), "Persist tool modes, including mod IDs");
        check(perTool.modeFor("OtherMod:Pickaxe", PlayerSettings.ToolBlockMode.ORES) == PlayerSettings.ToolBlockMode.ALL, "Explicit setting overrides inferred type");
        check(perTool.modeFor("NewMod:Hatchet", PlayerSettings.ToolBlockMode.LOGS) == PlayerSettings.ToolBlockMode.LOGS, "New tools inherit detected type");
        check(PlayerSettings.ToolBlockMode.ORES.allows(true, false), "Pickaxe accepts ores");
        check(!PlayerSettings.ToolBlockMode.ORES.allows(false, true), "Pickaxe rejects logs");
        check(PlayerSettings.ToolBlockMode.LOGS.allows(false, true), "Hatchet accepts logs");
        check(!PlayerSettings.ToolBlockMode.LOGS.allows(true, false), "Hatchet rejects ores");
        check(!PlayerSettings.ToolBlockMode.LOGS.allows(false, false), "Logs excludes planks and other blocks");
        check(PlayerSettings.ToolBlockMode.ALL.allows(false, false), "All accepts other selected blocks");
        check(new SettingsRepository(root.resolve("dynamic"), warnings::add, () -> perTool).get(second).equals(perTool), "New player defaults come from registered assets");
        var empty = new PlayerSettings(Set.of(), Set.of(), 32, PlayerSettings.Activation.CROUCH);
        restarted.save(first, empty);
        check(new SettingsRepository(root, warnings::add).get(first).equals(empty), "Lista vazia permanece vazia");
        check(!empty.allowsTool("Tool_Pickaxe_Copper", false), "Lista de ferramentas vazia desativa uso");

        UUID legacy = UUID.randomUUID();
        Files.writeString(root.resolve(legacy + ".properties"), "Radius=5\nActivation=SPRINT\nBlocks=Ore_Copper_Stone\n");
        var migrated = new SettingsRepository(root, warnings::add).get(legacy);
        check(migrated.tools().equals(PlayerSettings.DEFAULT_TOOLS), "Config antiga recebe picaretas padrao");
        check(migrated.radius() == 5 && migrated.activation() == PlayerSettings.Activation.SPRINT
            && migrated.blocks().equals(Set.of("Ore_Copper_Stone")), "Migracao preserva as escolhas antigas");

        // Upgrades must enable every discovered hatchet/log once, including later mod additions.
        var automatic = new PlayerSettings(Set.of("Mod:Tree_Log", "Wood_Beech_Trunk"),
            Set.of("Mod:Hatchet", "Tool_Hatchet_Mithril"), 8, PlayerSettings.Activation.CROUCH);
        var migratedRepo = new SettingsRepository(root, warnings::add, PlayerSettings::defaults, () -> automatic);
        var upgraded = migratedRepo.get(legacy);
        check(upgraded.tools().containsAll(automatic.tools()) && upgraded.blocks().containsAll(automatic.blocks()), "Old settings receive all discovered hatchets and logs automatically");
        check(upgraded.modeFor("Mod:Hatchet", PlayerSettings.ToolBlockMode.ALL) == PlayerSettings.ToolBlockMode.LOGS, "New mod hatchet defaults to logs");
        check(upgraded.radius() == 5 && upgraded.activation() == PlayerSettings.Activation.SPRINT, "Migration preserves range and controls");
        check(new SettingsRepository(root, warnings::add, PlayerSettings::defaults, () -> automatic).get(legacy).equals(upgraded), "Migration is persisted without opening settings");
        var manualModes = new java.util.HashMap<>(upgraded.toolModes());
        manualModes.put("Tool_Hatchet_Mithril", PlayerSettings.ToolBlockMode.ORES);
        var manual = new PlayerSettings(Set.of("Ore_Copper_Stone"), Set.of("Tool_Hatchet_Mithril"), 5, upgraded.activation(), manualModes);
        migratedRepo.save(legacy, manual);
        check(new SettingsRepository(root, warnings::add, PlayerSettings::defaults, () -> automatic).get(legacy).equals(manual), "Manual ore restriction and disabled tools/logs survive restart");
        var additionalMod = new PlayerSettings(Set.of("Mod:Tree_Log", "Wood_Beech_Trunk", "NewMod:Tree_Log"),
            Set.of("Mod:Hatchet", "Tool_Hatchet_Mithril", "NewMod:Hatchet"), 8, PlayerSettings.Activation.CROUCH);
        var withNewMod = new SettingsRepository(root, warnings::add, PlayerSettings::defaults, () -> additionalMod).get(legacy);
        check(withNewMod.tools().contains("NewMod:Hatchet") && withNewMod.blocks().contains("NewMod:Tree_Log"), "Newly installed mods receive log defaults");
        check(!withNewMod.tools().contains("Mod:Hatchet") && !withNewMod.blocks().contains("Mod:Tree_Log"), "New mod does not re-enable manually disabled existing entries");
        check(withNewMod.modeFor("Tool_Hatchet_Mithril", PlayerSettings.ToolBlockMode.LOGS) == PlayerSettings.ToolBlockMode.ORES, "Detected defaults never overwrite explicit ore mode");

        var automaticOres = new PlayerSettings(Set.of("Mod:Ruby_Ore", "Ore_Copper_Stone"),
            Set.of("Mod:Ruby_Pickaxe", "Tool_Pickaxe_Copper"), 8, PlayerSettings.Activation.CROUCH);
        var oreRepo = new SettingsRepository(root, warnings::add, PlayerSettings::defaults, () -> additionalMod, () -> automaticOres);
        var oresUpgraded = oreRepo.get(legacy);
        check(oresUpgraded.tools().containsAll(automaticOres.tools()) && oresUpgraded.blocks().containsAll(automaticOres.blocks()), "Existing players automatically receive all detected pickaxes and ores");
        check(oresUpgraded.modeFor("Mod:Ruby_Pickaxe", PlayerSettings.ToolBlockMode.ALL) == PlayerSettings.ToolBlockMode.ORES, "Mod pickaxe defaults to ores only");
        check(!oresUpgraded.tools().contains("Mod:Hatchet") && !oresUpgraded.blocks().contains("Mod:Tree_Log"), "Ore upgrade preserves disabled hatchets and logs");
        var disabledOreModes = new java.util.HashMap<>(oresUpgraded.toolModes());
        disabledOreModes.put("Tool_Pickaxe_Copper", PlayerSettings.ToolBlockMode.ALL);
        var oreManual = new PlayerSettings(Set.of(), Set.of("Tool_Pickaxe_Copper"), 11, PlayerSettings.Activation.WALK, disabledOreModes);
        oreRepo.save(legacy, oreManual);
        check(new SettingsRepository(root, warnings::add, PlayerSettings::defaults, () -> additionalMod, () -> automaticOres).get(legacy).equals(oreManual), "Manual pickaxe mode and disabled ores survive restart");
        var newOreMod = new PlayerSettings(Set.of("Mod:Ruby_Ore", "Ore_Copper_Stone", "NewMod:Sapphire_Ore"),
            Set.of("Mod:Ruby_Pickaxe", "Tool_Pickaxe_Copper", "NewMod:Sapphire_Pickaxe"), 8, PlayerSettings.Activation.CROUCH);
        var oreModInstalled = new SettingsRepository(root, warnings::add, PlayerSettings::defaults, () -> additionalMod, () -> newOreMod).get(legacy);
        check(oreModInstalled.tools().contains("NewMod:Sapphire_Pickaxe") && oreModInstalled.blocks().contains("NewMod:Sapphire_Ore"), "Later mods automatically enable new pickaxes and ores");
        check(!oreModInstalled.tools().contains("Mod:Ruby_Pickaxe") && !oreModInstalled.blocks().contains("Mod:Ruby_Ore"), "New mod preserves manually disabled existing pickaxes and ores");
        check(oreModInstalled.modeFor("Tool_Pickaxe_Copper", PlayerSettings.ToolBlockMode.ORES) == PlayerSettings.ToolBlockMode.ALL, "New mod preserves explicit pickaxe type override");
        check(oreModInstalled.radius() == 11 && oreModInstalled.activation() == PlayerSettings.Activation.WALK, "Ore detection preserves range and activation");

        String localized = CatalogSearch.normalize("Ore_Copper_Stone Minério de Cobre em Pedra");
        check(CatalogSearch.matches(localized, CatalogSearch.normalize("MINERIO COBRE")), "Buscar sem acentos e sem diferenciar maiusculas");
        check(CatalogSearch.matches(localized, CatalogSearch.normalize("cobre pedra")), "Buscar palavras separadas no nome");
        check(CatalogSearch.matches(localized, CatalogSearch.normalize("ore_copper")), "Buscar ID do bloco");
        check(!CatalogSearch.matches(localized, CatalogSearch.normalize("ferro")), "Nao trazer resultado diferente");
        check(CatalogSearch.matches(localized, ""), "Busca vazia mostra catalogo");

        var largeCatalog = java.util.stream.IntStream.range(0, 5_003).boxed().toList();
        var window = new CatalogWindow<Integer>();
        window.reset(largeCatalog);
        var firstBatch = window.nextBatch();
        String firstCursor = window.cursor();
        check(window.acceptsCursor(firstCursor), "Ultimas linhas do lote atual podem carregar outro lote");
        check(firstBatch.size() == 30 && window.loaded() == 30 && window.total() == 5_003,
            "Abrir catalogo grande cria apenas 30 cartoes");
        var loaded = new ArrayList<>(firstBatch);
        while (window.hasMore()) {
            var batch = window.nextBatch();
            check(!window.acceptsCursor(firstCursor), "Eventos repetidos do lote anterior nao carregam novamente");
            check(batch.size() <= 30, "Cada carregamento limita o trabalho de renderizacao");
            loaded.addAll(batch);
        }
        check(loaded.equals(largeCatalog), "Carregar mais preserva ordem sem perder ou duplicar itens");
        check(firstBatch.equals(largeCatalog.subList(0, 30)), "Acrescentar lote nao modifica cartoes anteriores");
        check(window.nextBatch().isEmpty(), "Carregar apos fim nao duplica resultados");
        check(!window.acceptsCursor(window.cursor()), "Fim do catalogo ignora gatilhos automaticos");
        window.reset(largeCatalog);
        window.nextBatch();
        check(!window.acceptsCursor(firstCursor), "Troca de consulta invalida cursor mesmo com 30 itens carregados");
        check(!window.acceptsCursor("invalido"), "Ignorar evento automatico invalido");
        window.reset(java.util.List.of(81, 82));
        check(window.loaded() == 0 && window.nextBatch().equals(java.util.List.of(81, 82)) && !window.hasMore(),
            "Nova busca ou aba elimina itens e contagem da lista anterior");
        window.reset(java.util.List.of());
        check(window.nextBatch().isEmpty() && window.total() == 0 && !window.hasMore(),
            "Busca sem resultados nao oferece carregar mais");

        Files.writeString(root.resolve(second + ".properties"), "Radius=999\nActivation=INVALID\n");
        check(new SettingsRepository(root, warnings::add).get(second).equals(defaults), "Arquivo invalido usa padroes");
        check(warnings.size() == 1, "Avisar ao carregar arquivo invalido");
        Path blocked = root.resolve("blocked");
        Files.writeString(blocked, "arquivo no lugar da pasta");
        var failing = new SettingsRepository(blocked, warnings::add);
        try { failing.save(first, chosen); throw new AssertionError("Save deveria falhar"); }
        catch (java.io.IOException expected) { }
        check(failing.get(first).equals(defaults), "Falha de gravacao nao publica configuracao");
        System.out.println("Settings: ativacao, validacao, isolamento e persistencia passaram.");
    }

    private static void check(boolean result, String message) {
        if (!result) throw new AssertionError(message);
    }
}
