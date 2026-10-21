# FINScore

FINScore is a full-stack financial fundamental-analysis application for educational use. Upload a standardized text-based financial-report PDF and receive extracted financial data, calculated ratios, a transparent FINScore from 1–100, risk classification, and visual explanations.

## Structure

- `finscore-frontend/` — React + Vite + Bootstrap + Recharts dashboard
- `finscore-backend/` — Java 17+ Spring Boot REST API with Apache PDFBox
- `sample-reports/` — project-owned standardized PDF and format documentation
- `DEPLOYMENT.md` — Railway and Vercel deployment guide

## Prerequisites

- JDK 17 or newer
- Maven 3.9+
- Node.js 18 or newer

## Local run

### Backend

```bash
cd finscore-backend
mvn spring-boot:run
```

The API runs on `http://localhost:8080`.

### Frontend

```bash
cd finscore-frontend
npm install
cp .env.example .env
npm run dev
```

The dashboard runs on `http://localhost:5173`. The frontend requires `VITE_API_BASE_URL` and calls `/api/analysis/upload` and `/api/analysis/health`; the committed `.env.example` uses `http://localhost:8080` for local development. Set the variable to the deployed Railway URL in Vercel production settings.

The backend also keeps the versioned compatibility routes `/api/v1/analysis` and `/api/v1/health`.

## Supported reports

The MVP intentionally supports the documented standardized report format in `sample-reports/FORMAT.md`. It is not an OCR engine and will safely reject image-only or unsupported annual-report layouts rather than invent values.

The primary demo fixture is [FINScore-Demonstration-Report-Fictional.pdf](sample-reports/FINScore-Demonstration-Report-Fictional.pdf). It is explicitly labelled as fictional data and is designed for the parser’s reliable demonstration workflow.

## Core guarantees

- PDFs are validated and limited to 25 MB on the backend.
- Financial values are extracted from the uploaded PDF using Apache PDFBox.
- Ratios and scores are calculated on the backend using `BigDecimal`.
- Missing and invalid data are reported explicitly; missing values are never treated as zero.
- Charts are rendered only from API response data.
- The backend is stateless and does not permanently store uploaded financial documents.

The official score weights Revenue Growth 20%, Net Profit Margin 20%, ROE 25%, ROA 15%, and Debt-to-Equity 20%. Unavailable metrics have nullable values and their weight is redistributed proportionally across available metrics.

## Tests

```bash
cd finscore-backend
mvn test

cd ../finscore-frontend
npm run build
```

FINScore is an educational analysis aid, not investment advice.
