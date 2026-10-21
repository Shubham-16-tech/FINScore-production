package com.finscore.backend.parser;

import com.finscore.backend.exception.ApiExceptions.UnsupportedReportException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class StandardReportParser {
    private static final Pattern YEAR_PATTERN = Pattern.compile("(?:Financial Years?|Years)\\s*:\\s*(\\d{4})(?:\\s*(?:,|to|and)\\s*(\\d{4}))?", Pattern.CASE_INSENSITIVE);
    private static final Pattern META_PATTERN = Pattern.compile("(?:^|\\s)(Company Name|Company|Currency|Unit)\\s*:\\s*(.*?)(?=\\s+(?:Company Name|Company|Currency|Unit|Financial Years?|Years)\\s*:|$)", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);
    private static final Map<String, String> ALIASES = Map.ofEntries(
            Map.entry("revenue", "revenue"), Map.entry("sales", "revenue"), Map.entry("previous year revenue", "previousRevenue"), Map.entry("previous revenue", "previousRevenue"),
            Map.entry("revenue from operations", "revenue"), Map.entry("total revenue", "revenue"),
            Map.entry("operating profit", "operatingProfit"), Map.entry("ebit", "operatingProfit"),
            Map.entry("net income", "netIncome"), Map.entry("net profit", "netIncome"), Map.entry("previous year net profit", "previousNetIncome"), Map.entry("previous net profit", "previousNetIncome"), Map.entry("profit after tax", "netIncome"), Map.entry("profit for the year", "netIncome"),
            Map.entry("current assets", "currentAssets"), Map.entry("current liabilities", "currentLiabilities"),
            Map.entry("total assets", "totalAssets"), Map.entry("total liabilities", "totalLiabilities"), Map.entry("total debt", "totalDebt"), Map.entry("borrowings", "totalDebt"),
            Map.entry("shareholders' equity", "shareholdersEquity"), Map.entry("shareholders equity", "shareholdersEquity"), Map.entry("total equity", "shareholdersEquity"), Map.entry("net worth", "shareholdersEquity"),
            Map.entry("operating cash flow", "operatingCashFlow"), Map.entry("cash from operations", "operatingCashFlow"), Map.entry("capital expenditure", "capitalExpenditure"),
            Map.entry("capex", "capitalExpenditure"));

    public ParsedReport parse(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new UnsupportedReportException("The uploaded file is empty.");
        try (PDDocument document = Loader.loadPDF(file.getBytes())) {
            String text = new PDFTextStripper().getText(document).replace('\u00a0', ' ');
            if (text.isBlank()) throw new UnsupportedReportException("The PDF has no extractable text. Scanned PDFs are not supported.");
            Matcher years = YEAR_PATTERN.matcher(text);
            if (!years.find()) throw new UnsupportedReportException("Financial Years metadata was not found.");
            int latest = Integer.parseInt(years.group(1));
            Integer previous = years.group(2) == null ? previousYearFromLabel(text) : Integer.valueOf(years.group(2));
            String company = metadata(text, "Company", "Unnamed Company");
            String currency = metadata(text, "Currency", "Not specified");
            String unit = metadata(text, "Unit", "Not specified");
            Map<String, Values> values = new LinkedHashMap<>();
            List<String> warnings = new ArrayList<>();
            String[] lines = text.split("\\R");
            for (int index = 0; index < lines.length; index++) {
                String line = lines[index].trim().replaceAll("\\s+", " ");
                String[] parts;
                if (line.contains("|")) {
                    parts = line.split("\\|");
                } else {
                    String normalized = line.toLowerCase(Locale.ROOT);
                    String matchedLabel = ALIASES.keySet().stream().filter(normalized::startsWith).max(Comparator.comparingInt(String::length)).orElse(null);
                    if (matchedLabel == null) continue;
                    List<String> cells = new ArrayList<>();
                    String remainder = line.substring(matchedLabel.length()).trim();
                    Matcher numbers = Pattern.compile("\\(?[\\d,]+(?:\\.\\d+)?\\)?").matcher(remainder);
                    while (numbers.find() && cells.size() < 2) cells.add(numbers.group());
                    if (cells.isEmpty()) for (int lookahead = index + 1; lookahead < lines.length && cells.size() < 2; lookahead++) {
                        String candidate = lines[lookahead].trim();
                        try { parseNumber(candidate); cells.add(candidate); } catch (NumberFormatException ignored) { if (!cells.isEmpty()) break; }
                    }
                    parts = new String[] { matchedLabel, cells.size() > 0 ? cells.get(0) : "", cells.size() > 1 ? cells.get(1) : "" };
                }
                if (parts.length < 2) continue;
                String label = parts[0].trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
                String key = ALIASES.get(label);
                if (key == null) continue;
                try {
                    BigDecimal latestValue = parseNumber(parts[1]);
                    BigDecimal previousValue = parts.length >= 3 && previous != null && !parts[2].isBlank() ? parseNumber(parts[2]) : null;
                    if (key.equals("previousRevenue") || key.equals("previousNetIncome")) {
                        String baseKey = key.equals("previousRevenue") ? "revenue" : "netIncome";
                        Values existing = values.get(baseKey);
                        values.put(baseKey, new Values(existing == null ? null : existing.latest(), latestValue));
                    } else if (values.containsKey(key) && previousValue != null) {
                        Values existing = values.get(key);
                        values.put(key, new Values(latestValue, previousValue));
                    } else if (values.containsKey(key)) warnings.add("Duplicate field ignored: " + label);
                    else values.put(key, new Values(latestValue, previousValue));
                } catch (NumberFormatException ex) {
                    warnings.add("Invalid numeric value for " + label);
                }
            }
            List<String> required = List.of("revenue", "netIncome");
            List<String> missing = required.stream().filter(k -> !values.containsKey(k)).toList();
            if (!missing.isEmpty()) throw new UnsupportedReportException("Required financial fields are missing: " + String.join(", ", missing));
            return new ParsedReport(company, currency, unit, latest, previous, document.getNumberOfPages(), values, warnings);
        } catch (IOException ex) {
            throw new UnsupportedReportException("The uploaded file is not a readable PDF.");
        }
    }

    private String metadata(String text, String key, String fallback) {
        Matcher matcher = META_PATTERN.matcher(text);
        while (matcher.find()) if (matcher.group(1).equalsIgnoreCase(key) || (key.equalsIgnoreCase("Company") && matcher.group(1).equalsIgnoreCase("Company Name"))) return matcher.group(2).trim();
        return fallback;
    }

    private Integer previousYearFromLabel(String text) {
        Matcher matcher = Pattern.compile("Previous Financial Year\\s*:\\s*(\\d{4})", Pattern.CASE_INSENSITIVE).matcher(text);
        return matcher.find() ? Integer.valueOf(matcher.group(1)) : null;
    }

    public static BigDecimal parseNumber(String raw) {
        String value = raw.trim().replaceAll("[,₹$€]", "").replaceAll("\\s+", "");
        if (value.isBlank() || value.equals("-")) throw new NumberFormatException("empty");
        boolean negative = value.startsWith("(") && value.endsWith(")");
        if (negative) value = value.substring(1, value.length() - 1);
        BigDecimal number = new BigDecimal(value);
        return negative ? number.negate() : number;
    }

    public record Values(BigDecimal latest, BigDecimal previous) {}
    public record ParsedReport(String company, String currency, String unit, int latestYear, Integer previousYear, int pageCount, Map<String, Values> values, List<String> warnings) {}
}
