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
    private static final Pattern EXPLICIT_YEARS = Pattern.compile("(?:financial years?|reporting years?|years?)\\s*[:\\-]?\\s*(20\\d{2})(?:\\s*(?:,|/|to|and|&|\\-)\\s*(20\\d{2}))?", Pattern.CASE_INSENSITIVE);
    private static final Pattern YEAR_PAIR = Pattern.compile("\\b(20\\d{2})\\b[^\\r\\n]{0,24}\\b(20\\d{2})\\b");
    private static final Pattern SINGLE_YEAR = Pattern.compile("\\b(20\\d{2})\\b");
    private static final Pattern META_LINE = Pattern.compile("^\\s*(company(?:\\s+name)?|entity name|name|currency|unit|financial year|reporting period|year ended|previous financial year)\\s*[:\\-]\\s*(.*?)\\s*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern NUMBER = Pattern.compile("(?:\\(?\\s*[₹$€£]?\\s*[+-]?(?:\\d{1,3}(?:,\\d{3})+|\\d+)(?:\\.\\d+)?\\s*\\)?|\\-)");
    private static final Set<String> HEADINGS = Set.of("income statement", "statement of profit and loss", "profit and loss account", "balance sheet", "statement of financial position", "cash flow statement", "cash flows", "consolidated financial statements", "standalone financial statements");
    private static final List<Alias> ALIASES = aliases();

    public ParsedReport parse(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new UnsupportedReportException("The uploaded file is empty.");
        try (PDDocument document = Loader.loadPDF(file.getBytes())) {
            String text = new PDFTextStripper().getText(document).replace('\u00a0', ' ');
            if (text.isBlank()) throw new UnsupportedReportException("The PDF has no extractable text. Scanned PDFs are not supported yet.");

            String[] rawLines = text.split("\\R");
            List<String> lines = Arrays.stream(rawLines).map(this::cleanLine).filter(s -> !s.isBlank()).toList();
            YearPair years = findYears(text, lines);
            if (years == null) throw new UnsupportedReportException("Could not identify two financial years in the report. Include two years such as 2024 and 2023.");

            String company = metadata(lines, "company");
            if (company == null) company = inferCompany(lines);
            String currency = metadata(lines, "currency");
            String unit = metadata(lines, "unit");
            String amountContext = firstMatchingLine(lines, "(?i)(amounts?|figures?|values?)\\s+(?:are\\s+)?(?:in|reported in)");
            if ((currency == null || unit == null) && amountContext != null) {
                String[] inferred = inferCurrencyUnit(amountContext);
                if (currency == null) currency = inferred[0];
                if (unit == null) unit = inferred[1];
            }
            if (currency == null) currency = inferCurrency(lines, "INR");
            if (unit == null) unit = inferUnit(lines, "Not specified");

            Map<String, Values> values = new LinkedHashMap<>();
            List<String> warnings = new ArrayList<>();
            for (int index = 0; index < lines.size(); index++) {
                String line = lines.get(index);
                LabelMatch match = matchLabel(line);
                if (match == null) continue;
                List<String> cells = numericCells(match.remainder(), years);
                if (cells.size() < 2) {
                    for (int lookahead = index + 1; lookahead < lines.size() && cells.size() < 2 && lookahead <= index + 4; lookahead++) {
                        String candidate = lines.get(lookahead);
                        if (matchLabel(candidate) != null || isHeading(candidate)) break;
                        List<String> next = numericCells(candidate, years);
                        if (!next.isEmpty()) cells.addAll(next);
                    }
                }
                if (cells.isEmpty()) continue;
                String key = match.key();
                try {
                    BigDecimal latestValue = parseNumber(cells.get(0));
                    BigDecimal previousValue = cells.size() > 1 && years.previous() != null ? parseNumber(cells.get(1)) : null;
                    merge(values, warnings, key, latestValue, previousValue, match.label());
                } catch (NumberFormatException ex) {
                    warnings.add("Invalid numeric value for " + match.label());
                }
            }

            List<String> required = List.of("revenue", "netIncome");
            List<String> missing = required.stream().filter(k -> !values.containsKey(k)).toList();
            if (!missing.isEmpty()) throw new UnsupportedReportException("Required financial fields are missing: " + String.join(", ", missing) + ". Check that the report contains revenue and net profit/income.");
            if (company == null || company.isBlank()) company = "Unnamed Company";
            return new ParsedReport(company, currency, unit, years.latest(), years.previous(), document.getNumberOfPages(), values, warnings);
        } catch (IOException | IllegalArgumentException ex) {
            if (ex instanceof UnsupportedReportException unsupported) throw unsupported;
            throw new UnsupportedReportException("The uploaded file is not a readable PDF.");
        }
    }

    private void merge(Map<String, Values> values, List<String> warnings, String key, BigDecimal latest, BigDecimal previous, String label) {
        if (key.equals("previousRevenue") || key.equals("previousNetIncome")) {
            String base = key.equals("previousRevenue") ? "revenue" : "netIncome";
            Values existing = values.get(base);
            values.put(base, new Values(existing == null ? null : existing.latest(), latest));
        } else if (!values.containsKey(key)) {
            values.put(key, new Values(latest, previous));
        } else {
            Values existing = values.get(key);
            if (existing.latest() == null || previous != null) values.put(key, new Values(latest, previous));
            else warnings.add("Duplicate field ignored: " + label);
        }
    }

    private LabelMatch matchLabel(String line) {
        String normalized = normalizeLabel(line);
        for (Alias alias : ALIASES) {
            if (alias.key().equals("shareholdersEquity") && normalized.startsWith("total equity and liabilities")) continue;
            if (normalized.equals(alias.label()) || normalized.startsWith(alias.label() + " ") || normalized.startsWith(alias.label() + " (")) {
                String remainder = line.substring(Math.min(line.length(), alias.originalLength(line))).trim();
                remainder = remainder.replaceFirst("^[\\s:|\\-]+", "");
                return new LabelMatch(alias.key(), alias.label(), remainder);
            }
        }
        return null;
    }

    private List<String> numericCells(String input, YearPair years) {
        List<String> result = new ArrayList<>();
        if (input == null || input.isBlank()) return result;
        Matcher matcher = NUMBER.matcher(input);
        while (matcher.find() && result.size() < 3) {
            String token = matcher.group().trim();
            try {
                BigDecimal number = parseNumber(token);
                String yearToken = token.replaceAll("[()\\s]", "");
                if (yearToken.matches("\\d{4}") && number.compareTo(BigDecimal.valueOf(1900)) >= 0 && number.compareTo(BigDecimal.valueOf(2100)) <= 0) continue;
                result.add(token);
            } catch (NumberFormatException ignored) { }
        }
        return result;
    }

    private YearPair findYears(String text, List<String> lines) {
        Matcher explicit = EXPLICIT_YEARS.matcher(text);
        if (explicit.find()) return pair(explicit.group(1), explicit.group(2));
        for (String line : lines) {
            Matcher pair = YEAR_PAIR.matcher(line);
            if (pair.find()) return pair(pair.group(1), pair.group(2));
        }
        List<Integer> found = new ArrayList<>();
        Matcher all = SINGLE_YEAR.matcher(text);
        while (all.find()) found.add(Integer.valueOf(all.group(1)));
        return found.size() >= 2 ? new YearPair(Math.max(found.get(0), found.get(1)), Math.min(found.get(0), found.get(1))) : null;
    }

    private YearPair pair(String first, String second) { return second == null ? new YearPair(Integer.parseInt(first), null) : new YearPair(Math.max(Integer.parseInt(first), Integer.parseInt(second)), Math.min(Integer.parseInt(first), Integer.parseInt(second))); }

    private String metadata(List<String> lines, String key) {
        for (String line : lines) {
            Matcher m = META_LINE.matcher(line);
            if (!m.matches()) continue;
            String label = m.group(1).toLowerCase(Locale.ROOT);
            if ((key.equals("company") && (label.startsWith("company") || label.equals("entity name") || label.equals("name"))) || label.equals(key)) return m.group(2).trim();
        }
        return null;
    }

    private String inferCompany(List<String> lines) {
        for (String line : lines) {
            String lower = line.toLowerCase(Locale.ROOT);
            if (line.length() > 2 && line.length() < 120 && !lower.contains("finscore") && !lower.contains("annual report") && !isHeading(line) && !line.matches(".*\\b20\\d{2}\\b.*") && !line.matches(".*\\d{3,}.*")) return line.replaceAll("\\s+", " ").trim();
        }
        return null;
    }

    private String inferCurrency(List<String> lines, String fallback) { for (String line : lines) if (line.matches(".*(?i)(₹|inr|rupees|usd|\\$|dollars|eur|€|gbp|£).*")) return line.toLowerCase(Locale.ROOT).contains("usd") || line.contains("$") ? "USD" : line.toLowerCase(Locale.ROOT).contains("eur") || line.contains("€") ? "EUR" : line.toLowerCase(Locale.ROOT).contains("gbp") || line.contains("£") ? "GBP" : "INR"; return fallback; }
    private String inferUnit(List<String> lines, String fallback) { for (String line : lines) if (line.matches(".*(?i)(crore|million|billion|thousand|lakh|mn|bn|000s).*")) return line; return fallback; }
    private String[] inferCurrencyUnit(String line) { return new String[]{inferCurrency(List.of(line), "Not specified"), inferUnit(List.of(line), "Not specified")}; }
    private String firstMatchingLine(List<String> lines, String regex) { Pattern p = Pattern.compile(regex); return lines.stream().filter(l -> p.matcher(l).find()).findFirst().orElse(null); }
    private boolean isHeading(String line) { return HEADINGS.contains(normalizeLabel(line)); }
    private String cleanLine(String line) { return line == null ? "" : line.trim().replaceAll("\\s+", " "); }
    private String normalizeLabel(String line) { return line.toLowerCase(Locale.ROOT).replaceAll("[|:;]+.*$", "").replaceAll("\\([^)]*(?:₹|\\$|€|inr|usd|million|crore|mn|bn)[^)]*\\)", "").replaceAll("[^a-z0-9'& ]", " ").replaceAll("\\s+", " ").trim(); }

    public static BigDecimal parseNumber(String raw) {
        String value = raw.trim().replaceAll("[₹$€£,]", "").replaceAll("\\s+", "");
        if (value.isBlank() || value.equals("-")) throw new NumberFormatException("empty");
        boolean negative = value.startsWith("(") && value.endsWith(")");
        if (negative) value = value.substring(1, value.length() - 1);
        BigDecimal number = new BigDecimal(value);
        return negative ? number.negate() : number;
    }

    private static List<Alias> aliases() {
        Map<String, String> map = new LinkedHashMap<>();
        add(map, "revenue from operations", "revenue"); add(map, "revenue", "revenue"); add(map, "net sales", "revenue"); add(map, "sales revenue", "revenue"); add(map, "sales", "revenue"); add(map, "turnover", "revenue"); add(map, "total revenue", "revenue"); add(map, "previous year revenue", "previousRevenue"); add(map, "previous revenue", "previousRevenue");
        add(map, "profit attributable to owners", "netIncome"); add(map, "profit after tax", "netIncome"); add(map, "net profit", "netIncome"); add(map, "net income", "netIncome"); add(map, "profit for the period", "netIncome"); add(map, "profit for the year", "netIncome"); add(map, "pat", "netIncome"); add(map, "previous year net profit", "previousNetIncome"); add(map, "previous net profit", "previousNetIncome");
        add(map, "operating profit", "operatingProfit"); add(map, "ebitda", "operatingProfit"); add(map, "ebit", "operatingProfit"); add(map, "current assets", "currentAssets"); add(map, "current liabilities", "currentLiabilities"); add(map, "total assets", "totalAssets"); add(map, "total liabilities", "totalLiabilities"); add(map, "total debt", "totalDebt"); add(map, "total borrowings", "totalDebt"); add(map, "borrowings", "totalDebt"); add(map, "long term debt", "totalDebt"); add(map, "total shareholders' equity", "shareholdersEquity"); add(map, "shareholders' equity", "shareholdersEquity"); add(map, "shareholders equity", "shareholdersEquity"); add(map, "total equity", "shareholdersEquity"); add(map, "total shareholders funds", "shareholdersEquity"); add(map, "net worth", "shareholdersEquity"); add(map, "operating cash flow", "operatingCashFlow"); add(map, "cash flow from operating activities", "operatingCashFlow"); add(map, "net cash from operating activities", "operatingCashFlow"); add(map, "cash generated from operations", "operatingCashFlow"); add(map, "cash from operations", "operatingCashFlow"); add(map, "capital expenditure", "capitalExpenditure"); add(map, "capex", "capitalExpenditure");
        return map.entrySet().stream().map(e -> new Alias(e.getKey(), e.getValue())).sorted(Comparator.comparingInt((Alias a) -> a.label().length()).reversed()).toList();
    }
    private static void add(Map<String,String> map,String label,String key){ map.putIfAbsent(label,key); }
    private record Alias(String label, String key) { int originalLength(String line) { String lower=line.toLowerCase(Locale.ROOT); int idx=lower.indexOf(label); return idx < 0 ? label.length() : idx + label.length(); } }
    private record LabelMatch(String key, String label, String remainder) {}
    private record YearPair(int latest, Integer previous) {}
    public record Values(BigDecimal latest, BigDecimal previous) {}
    public record ParsedReport(String company, String currency, String unit, int latestYear, Integer previousYear, int pageCount, Map<String, Values> values, List<String> warnings) {}
}
