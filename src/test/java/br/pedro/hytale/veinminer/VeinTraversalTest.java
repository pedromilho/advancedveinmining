package br.pedro.hytale.veinminer;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import static br.pedro.hytale.veinminer.VeinTraversal.Position;

public final class VeinTraversalTest {
    public static void main(String[] args) {
        Position root = new Position(0, 0, 0);
        var cluster = new HashSet<Position>(Set.of(root, new Position(1, 0, 0),
            new Position(1, 1, 0), new Position(2, 1, 0), new Position(0, 0, 3)));
        check(VeinTraversal.mine(root, 32, cluster::contains, cluster::remove, () -> true) == 3,
            "Minerar so o veio conectado, sem saltar pelo ar");
        check(cluster.contains(new Position(0, 0, 3)), "Preservar veio separado");

        var diagonal = Set.of(new Position(1, 1, 0), new Position(-1, -1, -1), new Position(-2, -2, -2));
        check(VeinTraversal.mine(root, 32, diagonal::contains, p -> true, () -> true) == 3,
            "Conectar arestas, cantos e cadeias de diagonais");
        var neighbours = new HashSet<Position>();
        check(VeinTraversal.mine(root, 32,
            p -> Math.abs(p.x()) <= 1 && Math.abs(p.y()) <= 1 && Math.abs(p.z()) <= 1,
            p -> { check(neighbours.add(p), "Nao repetir vizinho"); return true; }, () -> true) == 26,
            "Conectar todos os 26 vizinhos sem incluir a origem");

        AtomicInteger calls = new AtomicInteger();
        check(VeinTraversal.mine(root, 32, p -> true,
            p -> { calls.incrementAndGet(); return true; }, () -> true) == 31,
            "Limite inclui o bloco original");
        check(calls.get() == 31, "Nao duplicar quebras nem ultrapassar limite");

        var line = Set.of(new Position(1, 0, 0), new Position(2, 0, 0));
        calls.set(0);
        VeinTraversal.mine(root, 32, line::contains, p -> { calls.incrementAndGet(); return false; }, () -> true);
        check(calls.get() == 1, "Quebra cancelada impede chegar ao bloco atras dela");

        calls.set(0);
        check(VeinTraversal.mine(root, 32, p -> true,
            p -> { calls.incrementAndGet(); return true; }, () -> calls.get() < 2) == 2,
            "Interromper quando ferramenta quebra ou jogador desativa");
        check(VeinTraversal.mine(root, 1, p -> true, p -> { throw new AssertionError(); }, () -> true) == 0,
            "Limite unitario preserva todos os blocos extras");

        var broken = new HashSet<Position>();
        VeinTraversal.mine(root, 32, p -> Math.abs(p.x()) <= 1 && Math.abs(p.y()) <= 1 && p.z() == 0,
            p -> { check(broken.add(p), "Visitar cada bloco so uma vez"); return true; }, () -> true);
        check(broken.size() == 8, "Veio com ciclos e coordenadas negativas");

        var rangeLine = new HashSet<Position>();
        for (int x = 1; x <= 10; x++) rangeLine.add(new Position(x, 0, 0));
        check(VeinTraversal.mine(root, 32,
            p -> VeinTraversal.withinRadius(root, p, 2) && rangeLine.contains(p),
            rangeLine::remove, () -> true) == 2, "Medir alcance a partir da origem sem avancar indefinidamente");
        check(rangeLine.contains(new Position(3, 0, 0)), "Preservar bloco fora do alcance");
        check(VeinTraversal.withinRadius(root, new Position(-3, 4, 0), 5), "Incluir borda exata do raio");
        check(!VeinTraversal.withinRadius(root, new Position(3, 4, 1), 5), "Respeitar distancia nas diagonais");
        check(!VeinTraversal.withinRadius(root, new Position(1, 1, 0), 1), "Raio um exclui diagonal mais distante");
        check(!VeinTraversal.withinRadius(new Position(Integer.MIN_VALUE, 0, 0),
            new Position(Integer.MAX_VALUE, 0, 0), 32), "Nao deixar coordenadas extremas causar overflow");
        System.out.println("VeinTraversal: todos os cenarios passaram.");
    }

    private static void check(boolean passed, String description) {
        if (!passed) throw new AssertionError(description);
    }
}
