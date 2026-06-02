package com.marketutils.client.util;

import java.util.regex.Pattern;

public final class PriceParser {
    private static final Pattern MINECRAFT_FORMATTING_PATTERN = Pattern.compile("(?i)§[0-9a-fk-orx]");

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

        String cleanText = stripFormatting(inputText).trim();
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("([0-9.,]+)\\s*([kKmMbBtT]?)");
        java.util.regex.Matcher matcher = pattern.matcher(cleanText);

        if (matcher.find()) {
            String numberStr = matcher.group(1).replace(",", "");
            String suffix = matcher.group(2).toLowerCase();

            double multiplier = 1.0;
            if (suffix.equals("b")) {
                multiplier = 1_000_000_000.0;
            } else if (suffix.equals("m")) {
                multiplier = 1_000_000.0;
            } else if (suffix.equals("k")) {
                multiplier = 1_000.0;
            }

            try {
                double parsedValue = Double.parseDouble(numberStr);
                return (long) (parsedValue * multiplier);
            } catch (NumberFormatException exception) {
                return 0L;
            }
        }
        return 0L;
    }
}
