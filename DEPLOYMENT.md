# FINScore Deployment

## Railway backend

1. Create a Railway service from the repository and set the root directory to `finscore-backend`.
2. Railway can use the included `Dockerfile`, or run `mvn spring-boot:run` only for development. For production, use the Dockerfile.
3. Configure:
   - `PORT` — supplied by Railway automatically.
   - `CORS_ALLOWED_ORIGINS` — the Vercel production URL, plus a comma-separated local origin if needed.
   - `SPRING_PROFILES_ACTIVE=prod` optionally.
4. Verify `https://<railway-domain>/api/analysis/health` returns `status=UP`.
5. The service is stateless. Uploaded PDFs are processed temporarily and are not persisted.

## Vercel frontend

1. Create a Vercel project from the repository and set the root directory to `finscore-frontend`.
2. Build command: `npm run build`.
3. Output directory: `dist`.
4. Add environment variable:

```text
VITE_API_BASE_URL=https://<railway-domain>
```

5. Deploy and verify the upload workflow with `sample-reports/FINScore-Demonstration-Report-Fictional.pdf`.

The included `vercel.json` provides SPA fallback routing. Configure the Railway CORS origin to exactly match the deployed Vercel origin.
