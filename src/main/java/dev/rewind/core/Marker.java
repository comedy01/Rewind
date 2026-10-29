package dev.rewind.core;

public record Marker(Kind kind, int tick, String label) {
    public enum Kind {
        DEATH(0xFFE0443C),
        PET_DEATH(0xFFF08CC0),
        EXPLOSION(0xFFFFA23A),
        TRAVEL(0xFFB07CFF);

        private final int color;

        Kind(int color) {
            this.color = color;
        }

        public int color() {
            return color;
        }
    }
}
