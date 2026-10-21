# Annual-report text parsing support

FINScore now supports the original pipe-delimited standardized format plus many common text-based annual-report layouts. It recognizes aliases including Revenue from Operations, Net Sales, Turnover, PAT, Profit After Tax, Net Profit, Borrowings, Total Borrowings, Net Worth, Shareholders' Equity, Cash from Operations, Cash Generated from Operations, and Net Cash from Operating Activities.

The parser also detects common year pairs such as `FY 2025 / FY 2024`, `2025 2024`, and `Financial Years: 2025, 2024`. It accepts comma-formatted values, bracketed negatives, currency symbols, and common amount-unit descriptions.

It remains deliberately conservative: image-only/scanned PDFs, ambiguous tables, and reports missing required revenue or profit values are rejected rather than scored with invented data. OCR and coordinate-based table extraction are future enhancements.
