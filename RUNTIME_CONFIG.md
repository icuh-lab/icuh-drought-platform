# Runtime Configuration

## public-api

- `PUBLIC_SERVER_PORT` defaults to `8081`
- `PUBLIC_LOCAL_DB_URL` or legacy `LOCAL_DB_URL`
- `PUBLIC_LOCAL_DB_USERNAME` or legacy `LOCAL_DB_USERNAME`
- `PUBLIC_LOCAL_DB_PASSWORD` or legacy `LOCAL_DB_PASSWORD`
- `PUBLIC_PROD_DB_URL` or legacy `PROD_DB_URL`
- `PUBLIC_PROD_DB_USERNAME` or legacy `PROD_DB_USERNAME`
- `PUBLIC_PROD_DB_PASSWORD` or legacy `PROD_DB_PASSWORD`
- `PUBLIC_CORS_ALLOWED_ORIGINS` or legacy `CORS_ALLOWED_ORIGINS`
- `PUBLIC_S3_BUCKET_NAME` or legacy `S3_BUCKET_NAME`
- `PUBLIC_AWS_REGION` defaults to `ap-northeast-2`
- `PUBLIC_AWS_ACCESS_KEY_ID` or legacy `AWS_ACCESS_KEY_ID`
- `PUBLIC_AWS_SECRET_ACCESS_KEY` or legacy `AWS_SECRET_ACCESS_KEY`
- `PUBLIC_LOGGING_CONFIG` defaults to local or prod logback by profile

## admin-api

- `ADMIN_SERVER_PORT` defaults to `8082`
- `ADMIN_DB_URL` defaults to the local `ACTUAL_DRGHT` MySQL URL
- `ADMIN_DB_USERNAME`
- `ADMIN_DB_PASSWORD`
- `ADMIN_CORS_ALLOWED_ORIGINS` defaults to `http://localhost:3000,http://localhost:3001,http://localhost:5173`
- `ADMIN_JPA_SHOW_SQL` defaults to `false`
- `ADMIN_JPA_FORMAT_SQL` defaults to `false`
- `ADMIN_S3_BUCKET_NAME`
- `ADMIN_AWS_REGION` defaults to `ap-northeast-2`
- `ADMIN_AWS_ACCESS_KEY_ID`
- `ADMIN_AWS_SECRET_ACCESS_KEY`

## open-api

- `OPEN_API_SERVER_PORT` defaults to `8083`
- `OPEN_API_DB_URL` or legacy `DB_URL`
- `OPEN_API_DB_USERNAME` or legacy `DB_USERNAME`
- `OPEN_API_DB_PASSWORD` or legacy `DB_PASSWORD`
- `OPEN_API_CORS_ALLOWED_ORIGINS` defaults to `http://localhost:3000,http://localhost:3001,http://localhost:5173`

## batch

- `spring.main.web-application-type` is fixed to `none`.
- `spring.profiles.default` is `local`.

## Notes

- Runtime secrets must be injected through environment variables or an external secret manager.
- `application-private.yml` and `application-secret.yml` contain placeholders only.
- Rotate any key that was previously committed in plaintext.
