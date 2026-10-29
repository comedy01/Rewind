package dev.rewind.core;

import java.util.Locale;

public final class Places {
    private Places() {
    }

    public static String name(String id) {
        return switch (id) {
            case "minecraft:overworld" -> "the Overworld";
            case "minecraft:the_nether" -> "the Nether";
            case "minecraft:the_end" -> "the End";
            default -> {
                String path = id.substring(id.indexOf(':') + 1);
                StringBuilder name = new StringBuilder();
                for (String word : path.split("[_/]")) {
                    if (!word.isEmpty()) {
                        name.append(name.isEmpty() ? "" : " ")
                                .append(word.substring(0, 1).toUpperCase(Locale.ROOT)).append(word.substring(1));
                    }
                }
                yield name.toString();
            }
        };
    }

    public static int color(String id) {
        return switch (id) {
            case "minecraft:overworld" -> 0xFF6CC24A;
            case "minecraft:the_nether" -> 0xFFE0563C;
            case "minecraft:the_end" -> 0xFFD9D38C;
            default -> 0xFFB0B0B0;
        };
    }
}
