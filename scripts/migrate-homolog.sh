#!/usr/bin/env bash
set -euo pipefail

: "${FLYWAY_URL:?Configure HOMOLOG_DB_URL in environment homolog}"
: "${FLYWAY_USER:?Configure HOMOLOG_DB_USERNAME in environment homolog}"
: "${FLYWAY_PASSWORD:?Configure HOMOLOG_DB_PASSWORD in environment homolog}"

if [[ "$FLYWAY_URL" != jdbc:postgresql://* ]]; then
  echo "::error::HOMOLOG_DB_URL must be a PostgreSQL JDBC URL."
  exit 1
fi

# Credentials are read from the environment, never passed as command arguments.
# migrate validates applied checksums before applying pending migrations.
./mvnw --batch-mode --no-transfer-progress \
  -Dflyway.cleanDisabled=true \
  -Dflyway.baselineOnMigrate=false \
  -Dflyway.validateOnMigrate=true \
  flyway:migrate

echo "### Homolog database" >> "$GITHUB_STEP_SUMMARY"
echo "- Flyway validation and pending migrations completed for commit $GITHUB_SHA." >> "$GITHUB_STEP_SUMMARY"
