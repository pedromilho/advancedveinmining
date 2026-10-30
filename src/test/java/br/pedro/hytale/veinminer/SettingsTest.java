package br.pedro.hytale.veinminer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;

public final class SettingsTest {
    public static void main(String[] args) throws Exception {
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
