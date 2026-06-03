package com.marketutils.client.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PriceParser {

    private static final Pattern FORMATTING_CODES = Pattern.compile("(?i)\u00A7[0-9a-fk-orx]");
    private static final Pattern PRICE_NUMBER = Pattern.compile("([\\d,.]+)\\s*([kKmMbBtT]?)");

    private PriceParser() {}

    public static String stripFormatting(String input) {
        if (input == null) {
            return "";
        }
        return FORMATTING_CODES.matcher(input).replaceAll("");
    }

    public static long parsePrice(String inputText) {
        if (inputText == null) {
            return 0L;
        }

        String clean = stripFormatting(inputText).trim();
        Matcher matcher = PRICE_NUMBER.matcher(clean);

        if (!matcher.find()) {
            return 0L;
        }

        String numberPart = matcher.group(1).replace(",", "");
        String suffix = matcher.group(2).toLowerCase();

        double multiplier = switch (suffix) {
            case "b", "t" -> 1_000_000_000.0;
            case "m" -> 1_000_000.0;
            case "k" -> 1_000.0;
            default -> 1.0;
        };

        try {
            double value = Double.parseDouble(numberPart);
            return (long) (value * multiplier);
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }
}
