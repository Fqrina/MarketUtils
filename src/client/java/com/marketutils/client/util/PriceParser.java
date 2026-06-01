package com.marketutils.client.util;

import java.util.regex.Pattern;

public final class PriceParser {
    private static final Pattern MINECRAFT_FORMATTING_PATTERN = Pattern.compile("(?i)§[0-9a-fk-orx]");
    private static final Pattern NON_ALPHANUMERIC_DOT_PATTERN = Pattern.compile("[^0-9a-z.]");

    private PriceParser() {
        // Prevent instantiation
    }

    public static String stripFormatting(String input) {
        if (input == null) {
            return "";
        }
        return MINECRAFT_FORMATTING_PATTERN.matcher(input).replaceAll("");
    }

    public static long parsePrice(String inputText) {
        if (inputText == null) {
            return 0L;
        }

        String cleanedText = stripFormatting(inputText).toLowerCase().trim();
        cleanedText = NON_ALPHANUMERIC_DOT_PATTERN.matcher(cleanedText).replaceAll("");

        double multiplier = 1.0;
        if (cleanedText.endsWith("b")) {
            multiplier = 1_000_000_000.0;
            cleanedText = cleanedText.substring(0, cleanedText.length() - 1);
        } else if (cleanedText.endsWith("m")) {
            multiplier = 1_000_000.0;
            cleanedText = cleanedText.substring(0, cleanedText.length() - 1);
        } else if (cleanedText.endsWith("k")) {
            multiplier = 1_000.0;
            cleanedText = cleanedText.substring(0, cleanedText.length() - 1);
        }

        try {
            double numericValue = Double.parseDouble(cleanedText);
            return (long) (numericValue * multiplier);
        } catch (NumberFormatException exception) {
            return 0L;
        }
    }
}
