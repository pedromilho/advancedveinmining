package br.pedro.hytale.veinminer;

import java.util.List;

/** Limita o trabalho de criação da interface por atualização, mantendo todos os resultados acessíveis. */
final class CatalogWindow<T> {
    static final int BATCH_SIZE = 30;
    private List<T> entries = List.of();
    private int loaded;
    private long generation;

    void reset(List<T> entries) {
        this.entries = List.copyOf(entries);
        loaded = 0;
        generation++;
    }

    List<T> nextBatch() {
        int start = loaded;
        loaded = Math.min(entries.size(), loaded + BATCH_SIZE);
        return entries.subList(start, loaded);
    }

    int loaded() { return loaded; }
    int total() { return entries.size(); }
    boolean hasMore() { return loaded < entries.size(); }

    String cursor() { return generation + ":" + loaded; }

    boolean acceptsCursor(String cursor) { return hasMore() && cursor().equals(cursor); }
}
