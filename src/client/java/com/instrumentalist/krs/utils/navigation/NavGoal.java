package com.instrumentalist.krs.utils.navigation;

public interface NavGoal {
    double CARDINAL = 4.0D;
    double DIAGONAL = CARDINAL * 1.41421356D;
    double UP = 2.5D;
    double DOWN = 2.6D;

    boolean reached(int x, int y, int z);

    double heuristic(int x, int y, int z);

    static double horizontal(int dx, int dz) {
        int ax = Math.abs(dx);
        int az = Math.abs(dz);
        int diagonal = Math.min(ax, az);
        return (ax + az - diagonal * 2) * CARDINAL + diagonal * DIAGONAL;
    }

    static double vertical(int dy) {
        return dy > 0 ? dy * UP : -dy * DOWN;
    }

    record Block(int x, int y, int z) implements NavGoal {
        @Override
        public boolean reached(int px, int py, int pz) {
            return px == x && py == y && pz == z;
        }

        @Override
        public double heuristic(int px, int py, int pz) {
            return horizontal(x - px, z - pz) + vertical(y - py);
        }
    }

    record Column(int x, int z) implements NavGoal {
        @Override
        public boolean reached(int px, int py, int pz) {
            return px == x && pz == z;
        }

        @Override
        public double heuristic(int px, int py, int pz) {
            return horizontal(x - px, z - pz);
        }
    }

    record Near(int x, int y, int z, int radius) implements NavGoal {
        @Override
        public boolean reached(int px, int py, int pz) {
            int dx = px - x;
            int dy = py - y;
            int dz = pz - z;
            return Math.abs(dy) <= 1 && dx * dx + dz * dz <= radius * radius;
        }

        @Override
        public double heuristic(int px, int py, int pz) {
            return horizontal(x - px, z - pz) + vertical(y - py);
        }
    }
}
