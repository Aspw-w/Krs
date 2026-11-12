package com.instrumentalist.krs.utils.navigation;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class NavSearch {
    private static final int NONE = Integer.MIN_VALUE;
    private static final int MAX_EXPANDED = 100_000;
    private static final int STALL_LIMIT = 2_500;
    private static final int MAX_TICKS = 100;
    private static final int MAX_FALL = 3;
    private static final int MAX_WATER_FALL = 24;
    private static final double WEIGHT = 1.4D;
    private static final double WALK = NavGoal.CARDINAL;
    private static final double DIAGONAL = NavGoal.DIAGONAL;
    private static final double JUMP = 2.5D;
    private static final double FALL = 2.0D;
    private static final double SWIM = 5.0D;
    private static final double SUBMERGED = 6.0D;
    private static final double BRIDGE = 22.0D;
    private static final double PILLAR = 12.0D;
    private static final double SWIM_VERTICAL = 8.0D;
    private static final int[] DX = {1, -1, 0, 0};
    private static final int[] DZ = {0, 0, 1, -1};
    private static final int[] DDX = {1, 1, -1, -1};
    private static final int[] DDZ = {1, -1, 1, -1};

    private final NavWorld world;
    private final NavGoal goal;
    private final Node start;
    private final Long2ObjectOpenHashMap<Node> nodes = new Long2ObjectOpenHashMap<>(8192);
    private Node[] heap = new Node[1024];
    private int heapSize;
    private Node best;
    private Node end;
    private int expanded;
    private int sinceImprovement;
    private int ticks;
    private boolean done;
    private boolean reached;

    public NavSearch(NavWorld world, BlockPos from, NavGoal goal) {
        this.world = world;
        this.goal = goal;
        this.start = new Node(from.getX(), from.getY(), from.getZ());
        start.g = 0.0D;
        start.h = goal.heuristic(start.x, start.y, start.z);
        start.f = start.h * WEIGHT;
        nodes.put(BlockPos.asLong(start.x, start.y, start.z), start);
        push(start);
        best = start;
    }

    public boolean step(long budgetNanos) {
        if (done)
            return true;

        ticks++;
        long deadline = System.nanoTime() + budgetNanos;
        int counter = 0;
        while (heapSize > 0) {
            Node node = pop();
            node.closed = true;
            if (goal.reached(node.x, node.y, node.z)) {
                end = node;
                reached = true;
                done = true;
                return true;
            }

            if (node.h < best.h - 0.01D) {
                best = node;
                sinceImprovement = 0;
            } else {
                sinceImprovement++;
            }

            expand(node);
            expanded++;
            if (expanded >= MAX_EXPANDED || sinceImprovement >= STALL_LIMIT && best != start)
                break;

            if ((++counter & 15) == 0 && System.nanoTime() >= deadline) {
                if (ticks < MAX_TICKS)
                    return false;
                break;
            }
        }

        done = true;
        end = best != start && best.h < start.h - 1.0D ? best : null;
        return true;
    }

    public boolean reachedGoal() {
        return reached;
    }

    public List<NavMove> moves() {
        List<NavMove> moves = new ArrayList<>();
        for (Node node = end; node != null && node.parent != null; node = node.parent)
            moves.add(new NavMove(new BlockPos(node.parent.x, node.parent.y, node.parent.z), new BlockPos(node.x, node.y, node.z), node.type));
        Collections.reverse(moves);
        return moves;
    }

    private void expand(Node node) {
        int x = node.x;
        int y = node.y;
        int z = node.z;
        boolean fromWater = world.space(x, y, z) == NavWorld.WATER;
        boolean fromFloor = world.standable(x, y - 1, z);
        boolean surfaced = world.space(x, y + 1, z) != NavWorld.WATER;

        for (int i = 0; i < 4; i++) {
            int nx = x + DX[i];
            int nz = z + DZ[i];
            float feet = world.space(nx, y, nz);
            float head = world.space(nx, y + 1, nz);
            if (feet != NavWorld.BLOCKED && head != NavWorld.BLOCKED) {
                boolean destWater = feet == NavWorld.WATER;
                double cost = WALK + pass(feet) + pass(head) + waterCost(fromWater, destWater, head == NavWorld.WATER);
                if (destWater || world.standable(nx, y - 1, nz)) {
                    relax(node, nx, y, nz, cost, NavMove.Type.WALK);
                } else {
                    int land = landing(nx, y - 1, nz, y);
                    if (land != NONE)
                        relax(node, nx, land, nz, cost + FALL * (y - land), NavMove.Type.DESCEND);
                    if (world.allowPlace && fromFloor && !fromWater && world.replaceable(nx, y - 1, nz))
                        relax(node, nx, y, nz, cost + BRIDGE, NavMove.Type.WALK);
                }
            }

            if ((fromFloor || fromWater && surfaced) && world.standable(nx, y, nz)) {
                float up = world.space(x, y + 2, z);
                float upFeet = world.space(nx, y + 1, nz);
                float upHead = world.space(nx, y + 2, nz);
                if (up != NavWorld.BLOCKED && upFeet != NavWorld.BLOCKED && upHead != NavWorld.BLOCKED)
                    relax(node, nx, y + 1, nz, WALK + JUMP + pass(up) + pass(upFeet) + pass(upHead), NavMove.Type.ASCEND);
            }
        }

        for (int i = 0; i < 4; i++) {
            int nx = x + DDX[i];
            int nz = z + DDZ[i];
            float feet = world.space(nx, y, nz);
            float head = world.space(nx, y + 1, nz);
            if (feet > 0.0F || head > 0.0F)
                continue;
            if (world.space(nx, y, z) > 0.0F || world.space(nx, y + 1, z) > 0.0F || world.space(x, y, nz) > 0.0F || world.space(x, y + 1, nz) > 0.0F)
                continue;

            boolean destWater = feet == NavWorld.WATER;
            if (destWater || world.standable(nx, y - 1, nz))
                relax(node, nx, y, nz, DIAGONAL + waterCost(fromWater, destWater, head == NavWorld.WATER) * 1.41421356D, NavMove.Type.WALK);
        }

        float above = world.space(x, y + 2, z);
        if (world.allowPlace && fromFloor && !fromWater && above != NavWorld.BLOCKED && world.replaceable(x, y, z))
            relax(node, x, y + 1, z, PILLAR + pass(above), NavMove.Type.PILLAR);

        if (fromWater && !surfaced && above <= 0.0F)
            relax(node, x, y + 1, z, SWIM_VERTICAL, NavMove.Type.SWIM_UP);

        float below = world.space(x, y - 1, z);
        if (below == NavWorld.WATER) {
            relax(node, x, y - 1, z, SWIM_VERTICAL, NavMove.Type.DOWN);
        } else if (below > 0.0F && below != NavWorld.BLOCKED && fromFloor) {
            int land = world.standable(x, y - 2, z) ? y - 1 : landing(x, y - 2, z, y);
            if (land != NONE)
                relax(node, x, land, z, below + FALL * (y - land), NavMove.Type.DOWN);
        }
    }

    private int landing(int x, int y, int z, int topY) {
        for (int feetY = y; feetY >= world.minY; feetY--) {
            float space = world.space(x, feetY, z);
            if (space == NavWorld.WATER)
                return feetY;
            if (space != NavWorld.FREE)
                return NONE;
            if (world.standable(x, feetY - 1, z))
                return topY - feetY <= MAX_FALL ? feetY : NONE;
            if (topY - feetY > MAX_WATER_FALL)
                return NONE;
        }
        return NONE;
    }

    private static double pass(float space) {
        return space > 0.0F ? space : 0.0D;
    }

    private static double waterCost(boolean fromWater, boolean destWater, boolean submerged) {
        double cost = fromWater || destWater ? SWIM : 0.0D;
        return submerged ? cost + SUBMERGED : cost;
    }

    private void relax(Node parent, int x, int y, int z, double cost, NavMove.Type type) {
        long key = BlockPos.asLong(x, y, z);
        Node node = nodes.get(key);
        if (node == null) {
            node = new Node(x, y, z);
            node.h = goal.heuristic(x, y, z);
            nodes.put(key, node);
        } else if (node.closed) {
            return;
        }

        double g = parent.g + cost;
        if (g >= node.g)
            return;

        node.g = g;
        node.f = g + node.h * WEIGHT;
        node.parent = parent;
        node.type = type;
        if (node.heapIndex < 0)
            push(node);
        else
            siftUp(node.heapIndex);
    }

    private void push(Node node) {
        if (heapSize == heap.length) {
            Node[] grown = new Node[heap.length * 2];
            System.arraycopy(heap, 0, grown, 0, heapSize);
            heap = grown;
        }
        heap[heapSize] = node;
        node.heapIndex = heapSize;
        siftUp(heapSize++);
    }

    private Node pop() {
        Node top = heap[0];
        Node last = heap[--heapSize];
        heap[heapSize] = null;
        top.heapIndex = -1;
        if (heapSize > 0) {
            heap[0] = last;
            last.heapIndex = 0;
            siftDown(0);
        }
        return top;
    }

    private void siftUp(int index) {
        Node node = heap[index];
        while (index > 0) {
            int parentIndex = (index - 1) >>> 1;
            Node parent = heap[parentIndex];
            if (parent.f <= node.f)
                break;
            heap[index] = parent;
            parent.heapIndex = index;
            index = parentIndex;
        }
        heap[index] = node;
        node.heapIndex = index;
    }

    private void siftDown(int index) {
        Node node = heap[index];
        int half = heapSize >>> 1;
        while (index < half) {
            int child = index * 2 + 1;
            int right = child + 1;
            if (right < heapSize && heap[right].f < heap[child].f)
                child = right;
            if (node.f <= heap[child].f)
                break;
            heap[index] = heap[child];
            heap[index].heapIndex = index;
            index = child;
        }
        heap[index] = node;
        node.heapIndex = index;
    }

    private static final class Node {
        private final int x;
        private final int y;
        private final int z;
        private double g = Double.POSITIVE_INFINITY;
        private double h;
        private double f;
        private Node parent;
        private NavMove.Type type;
        private int heapIndex = -1;
        private boolean closed;

        private Node(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }
}
