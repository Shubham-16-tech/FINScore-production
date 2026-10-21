# Standardized FINScore report format

The parser supports a text-based PDF with these headings and labels. Values may use commas, decimals, and parentheses for negatives. The report should declare `Company`, `Currency`, `Unit`, and `Financial Years`.

```text
FINScore Standardized Financial Report
Company: Example Industries Ltd
Currency: INR
Unit: Crores
Financial Years: 2024, 2023

INCOME STATEMENT
Revenue | 1250.00 | 1100.00
Operating Profit | 210.00 | 180.00
Net Income | 145.00 | 120.00

BALANCE SHEET
Current Assets | 620.00 | 570.00
Current Liabilities | 310.00 | 300.00
Total Assets | 1800.00 | 1650.00
Total Liabilities | 850.00 | 800.00
Shareholders' Equity | 950.00 | 850.00

CASH FLOW STATEMENT
Operating Cash Flow | 190.00 | 160.00
Capital Expenditure | (80.00) | (70.00)
```

The first numeric column is the latest year and the second is the previous year. The backend rejects image-only PDFs and ambiguous or incomplete reports safely.
