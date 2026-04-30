package com.eyren.hWIDBan.util;

import com.iridium.iridiumcolorapi.IridiumColorAPI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

public final class Colors {

    private Colors() {}

    /** Converte &-codes, hex (#rrggbb) e gradienti tramite IridiumColorAPI → stringa §-formattata. */
    public static String process(String input) {
        if (input == null) return "";
        return IridiumColorAPI.process(input);
    }

    public static String stripColor(String input) {
        if (input == null) return "";
        return IridiumColorAPI.stripColorFormatting(input);
    }

    /**
     * Converte una stringa §-formattata (output di {@link #process}) in un Adventure Component.
     * Usato per {@code player.kick(Component)} e {@code event.disallow(Result, Component)}
     * sulle versioni Paper ≥ 1.19.4 che hanno Adventure bundled.
     */
    public static Component toComponent(String sectionFormatted) {
        if (sectionFormatted == null || sectionFormatted.isEmpty()) return Component.empty();
        return LegacyComponentSerializer.legacySection().deserialize(sectionFormatted);
    }

    /**
     * Scorciatoia: process(&-input) → Component.
     */
    public static Component processComponent(String ampersandInput) {
        return toComponent(process(ampersandInput));
    }
}
