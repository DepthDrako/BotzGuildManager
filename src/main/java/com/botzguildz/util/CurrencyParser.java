package com.botzguildz.util;

import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.suggestion.Suggestions;
import net.minecraft.commands.SharedSuggestionProvider;

import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses player-typed currency amounts that may use denomination shorthand.
 *
 * Accepted formats
 * ─────────────────
 *   Plain number   : "512"       → 512 raw units
 *   Single denom   : "1s"        → 1 Seal  = 32 768
 *   Combined       : "1s2m3c"    → 1×32768 + 2×4096 + 3×512 = 42 496
 *   Full words     : "2seal"     → 65 536
 *
 * Denomination abbreviations
 * ───────────────────────────
 *   b / bit   =      1
 *   ch / chip =      8
 *   t / token =     64
 *   c / coin  =    512
 *   m / mark  =  4 096
 *   s / seal  = 32 768
 *
 * Note: ordering in the regex must keep "ch" before "c", and full words before
 * their single-letter abbreviations, so the pattern tries longer matches first.
 */
public class CurrencyParser {

    // Values parallel to the abbreviation array below
    private static final long[] VALUES = { 32_768L, 4_096L, 512L, 64L, 8L, 1L };

    /** Short abbreviations used for formatting and tab-complete suggestions. */
    private static final String[] SHORT = { "s", "m", "c", "t", "ch", "b" };

    /** Display names for error messages. */
    private static final String[] NAMES = {
            "Guild Seal", "Guild Mark", "Guild Coin",
            "Guild Token", "Guild Chip", "Guild Bit"
    };

    /**
     * Regex pattern that matches   &lt;digits&gt;&lt;suffix&gt;
     * Alternation order ensures longer/specific suffixes are tried first.
     */
    private static final Pattern DENOM_PATTERN = Pattern.compile(
            "(\\d+)(seal|mark|coin|token|chip|bit|s|m|ch|c|t|b)",
            Pattern.CASE_INSENSITIVE
    );

    /**
     * Parse a currency string and return its value in base units (Guild Bits).
     *
     * @throws IllegalArgumentException if the input cannot be parsed or is < 1.
     */
    public static long parse(String input) {
        if (input == null || input.isBlank())
            throw new IllegalArgumentException("Amount cannot be empty.");

        input = input.trim();

        // Plain integer fast-path
        try {
            long val = Long.parseLong(input);
            if (val < 1) throw new IllegalArgumentException("Amount must be at least 1.");
            return val;
        } catch (NumberFormatException ignored) {}

        // Denomination notation
        Matcher m = DENOM_PATTERN.matcher(input);
        long total = 0;
        boolean found = false;
        while (m.find()) {
            found = true;
            long count = Long.parseLong(m.group(1));
            total += count * denomValue(m.group(2).toLowerCase());
        }

        if (!found || total < 1)
            throw new IllegalArgumentException(
                    "Invalid amount '" + input + "'. Use a number or shorthand like 1s, 2m3c, 5t.");

        return total;
    }

    /**
     * Format a raw amount as a compact shorthand string (e.g. "1s2m3c").
     * Useful for command feedback and error messages.
     */
    public static String formatShort(long amount) {
        if (amount <= 0) return "0b";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < VALUES.length; i++) {
            long count = amount / VALUES[i];
            if (count > 0) {
                sb.append(count).append(SHORT[i]);
                amount %= VALUES[i];
            }
        }
        return sb.toString();
    }

    /**
     * Tab-complete suggestions shown when a currency argument is being typed.
     */
    public static CompletableFuture<Suggestions> suggest(SuggestionsBuilder builder) {
        String[] examples = {
                "1b", "1ch", "1t", "1c", "1m", "1s",
                "5c", "10c", "1s2m", "2s", "5s", "10s"
        };
        return SharedSuggestionProvider.suggest(examples, builder);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static long denomValue(String denom) {
        return switch (denom) {
            case "seal", "s"  -> 32_768L;
            case "mark", "m"  ->  4_096L;
            case "coin", "c"  ->    512L;
            case "token", "t" ->     64L;
            case "chip", "ch" ->      8L;
            case "bit",  "b"  ->      1L;
            default -> throw new IllegalArgumentException("Unknown denomination: " + denom);
        };
    }
}
