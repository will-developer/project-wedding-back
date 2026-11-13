#!/usr/bin/env bash
set -euo pipefail

alias_name=homolog
package=artifact/wedding-api.zip
object_key="homolog/${GITHUB_SHA}/${GITHUB_RUN_ID}-${GITHUB_RUN_ATTEMPT}.zip"

# Fail before changing code if the existing function differs from our packaging.
configuration=$(aws lambda get-function-configuration --function-name "$LAMBDA_FUNCTION" \
  --query '{Runtime:Runtime,Handler:Handler,Wrapper:Environment.Variables.AWS_LAMBDA_EXEC_WRAPPER,SnapStart:SnapStart.ApplyOn,RevisionId:RevisionId}')
jq -e '.Runtime == "java21" and .Handler == "run.sh" and .Wrapper == "/opt/bootstrap" and .SnapStart == "PublishedVersions"' <<< "$configuration" > /dev/null
revision=$(jq -r '.RevisionId' <<< "$configuration")

alias_state=$(aws lambda get-alias --function-name "$LAMBDA_FUNCTION" --name "$alias_name")
previous_version=$(jq -r '.FunctionVersion' <<< "$alias_state")
alias_revision=$(jq -r '.RevisionId' <<< "$alias_state")
# This workflow expects one version receiving all traffic.
jq -e '(.RoutingConfig.AdditionalVersionWeights // {} | length) == 0' <<< "$alias_state" > /dev/null
health_url=$(aws lambda get-function-url-config --function-name "$LAMBDA_FUNCTION" \
  --qualifier "$alias_name" --query FunctionUrl --output text)
echo "::add-mask::$health_url"

aws s3 cp "$package" "s3://$ARTIFACT_BUCKET/$object_key" --only-show-errors
code_hash=$(openssl dgst -sha256 -binary "$package" | openssl base64 -A)
aws lambda update-function-code --function-name "$LAMBDA_FUNCTION" \
  --s3-bucket "$ARTIFACT_BUCKET" --s3-key "$object_key" --revision-id "$revision" \
  --query CodeSha256 --output text > /dev/null
aws lambda wait function-updated-v2 --function-name "$LAMBDA_FUNCTION"

revision=$(aws lambda get-function-configuration --function-name "$LAMBDA_FUNCTION" --query RevisionId --output text)
version=$(aws lambda publish-version --function-name "$LAMBDA_FUNCTION" \
  --code-sha256 "$code_hash" --revision-id "$revision" \
  --description "commit $GITHUB_SHA run $GITHUB_RUN_ID/$GITHUB_RUN_ATTEMPT" \
  --query Version --output text)
aws lambda wait function-active-v2 --function-name "$LAMBDA_FUNCTION" --qualifier "$version"
aws lambda get-function-configuration --function-name "$LAMBDA_FUNCTION" --qualifier "$version" \
  --query SnapStart.OptimizationStatus --output text | grep -qx On

updated_revision=$(aws lambda update-alias --function-name "$LAMBDA_FUNCTION" \
  --name "$alias_name" --function-version "$version" --revision-id "$alias_revision" \
  --query RevisionId --output text)

# Revert only if this workflow still owns the alias revision.
healthy=false
for attempt in {1..6}; do
  if curl --silent --fail --connect-timeout 10 --max-time 45 \
    "${health_url%/}/actuator/health" | jq -e '.status == "UP"' > /dev/null; then
    healthy=true
    break
  fi
  sleep 10
done

if [[ "$healthy" != true ]]; then
  aws lambda update-alias --function-name "$LAMBDA_FUNCTION" --name "$alias_name" \
    --function-version "$previous_version" --revision-id "$updated_revision" \
    --query FunctionVersion --output text
  echo "::error::Health check failed. Restored the previous homolog alias version."
  exit 1
fi

{
  echo "### Homolog deployment"
  echo "- Commit: $GITHUB_SHA"
  echo "- Lambda version: $version"
  echo "- Previous version: $previous_version"
  echo "- ZIP SHA-256: $(sha256sum "$package" | cut -d ' ' -f 1)"
  echo "- Health: UP"
  echo "- Flyway: disabled pending schema review"
} >> "$GITHUB_STEP_SUMMARY"

