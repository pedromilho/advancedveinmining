package br.pedro.hytale.veinminer;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

/** Expande somente por blocos efetivamente quebrados: cancelamentos barram a busca. */
final class VeinTraversal {
    record Position(int x, int y, int z) {}

    static boolean withinRadius(Position origin, Position target, int radius) {
        long dx = (long) target.x - origin.x;
        long dy = (long) target.y - origin.y;
        long dz = (long) target.z - origin.z;
        // Evita overflow mesmo para coordenadas extremas recebidas pelo adaptador.
        if (Math.abs(dx) > radius || Math.abs(dy) > radius || Math.abs(dz) > radius) return false;
        return dx * dx + dy * dy + dz * dz <= (long) radius * radius;
    }

    static int mine(Position origin, int totalLimit, Predicate<Position> matches,
                    Predicate<Position> tryBreak, BooleanSupplier canContinue) {
        var visited = new HashSet<Position>();
        var queue = new ArrayDeque<Position>();
        visited.add(origin);
        addNeighbours(queue, origin);
        int attempted = 0;
        int broken = 0;
        // O bloco original já foi quebrado pelo jogo e conta no limite.
        while (!queue.isEmpty() && attempted < totalLimit - 1 && canContinue.getAsBoolean()) {
            Position next = queue.removeFirst();
            if (!visited.add(next) || !matches.test(next)) continue;
            attempted++;
            if (tryBreak.test(next)) {
                broken++;
                addNeighbours(queue, next);
            }
        }
        return broken;
    }

    private static void addNeighbours(ArrayDeque<Position> queue, Position p) {
        // Os 26 vizinhos incluem faces, arestas e cantos em todas as direções.
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    queue.add(new Position(p.x + dx, p.y + dy, p.z + dz));
                }
            }
        }
    }
}
