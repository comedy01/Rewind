package dev.rewind.core;

public final class Chunks {
    private Chunks() {
    }

    public static long pack(int x, int z) {
        return (x & 0xFFFFFFFFL) | (z & 0xFFFFFFFFL) << 32;
    }

    public static int x(long chunk) {
        return (int) chunk;
    }

    public static int z(long chunk) {
        return (int) (chunk >>> 32);
    }
}
