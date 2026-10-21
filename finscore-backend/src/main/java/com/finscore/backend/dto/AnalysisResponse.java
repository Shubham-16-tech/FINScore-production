package com.finscore.backend.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record AnalysisResponse(
        String analysisId,
        Source source,
        Company company,
        Map<String, Map<String, FinancialValue>> financialData,
        List<Ratio> ratios,
        Score score,
        List<Finding> findings,
        DataQuality dataQuality,
        boolean success,
        String companyName,
        int financialYear,
        ParsedFinancialValues parsedFinancialValues,
        FinancialAnalysis financialAnalysis,
        ScoreBreakdown scoreBreakdown,
        List<String> warnings,
        String riskLevel,
        int finalScore) {
    public record Source(String filename, int pageCount, String unit, int latestYear, Integer previousYear) {}
    public record Company(String name, String currency) {}
    public record FinancialValue(BigDecimal latest, BigDecimal previous) {}
    public record Ratio(String key, String label, BigDecimal value, String formattedValue, String category, String status, String interpretation, boolean available, String formula) {}
    public record Score(int value, String riskLevel, String riskLabel, int coveragePercent, String confidence, List<ComponentScore> componentScores) {}
    public record ComponentScore(String key, int score, int maxScore, int weight) {}
    public record Finding(String type, String severity, String message) {}
    public record DataQuality(List<String> missingFields, List<String> invalidFields, List<String> warnings) {}
    public record ParsedFinancialValues(BigDecimal revenue, BigDecimal previousRevenue, BigDecimal netProfit, BigDecimal previousNetProfit, BigDecimal totalAssets, BigDecimal totalDebt, BigDecimal totalEquity, BigDecimal operatingCashFlow) {}
    public record FinancialAnalysis(BigDecimal revenueGrowth, BigDecimal netProfitMargin, BigDecimal returnOnAssets, BigDecimal returnOnEquity, BigDecimal debtToEquity) {}
    public record ScoreBreakdown(Integer revenueGrowthScore, Integer netProfitMarginScore, Integer roeScore, Integer roaScore, Integer debtToEquityScore) {}
}
