#!/bin/sh
set -e

echo "Waiting for Vault to be ready..."
until vault status > /dev/null 2>&1; do
  echo "Vault not ready, retrying in 2s..."
  sleep 2
done

echo "Vault is ready. Writing secrets..."

vault kv put secret/auth-service \
  "jwt.secret=${JWT_SECRET}" \
  "jwt.signing-key.private-key=${JWT_SIGNING_KEY_PRIVATE}" \
  "jwt.signing-key.public-key=${JWT_SIGNING_KEY_PUBLIC}" \
  "spring.datasource.password=${AUTH_DB_PASSWORD}" \
  "spring.redis.password=${REDIS_PASSWORD}" \
  "spring.mail.password=${MAIL_PASSWORD}" \
  "google.recaptcha.secret-key=${RECAPTCHA_SECRET_KEY}" \
  "twofactor.api.key=${TWOFACTOR_API_KEY}"

vault kv put secret/mycommunity-service \
  "spring.datasource.password=${MYCOMMUNITY_DB_PASSWORD}" \
  "aws.s3.access-key=${MYCOMMUNITY_AWS_ACCESS_KEY_ID}" \
  "aws.s3.secret-key=${MYCOMMUNITY_AWS_SECRET_ACCESS_KEY}"

echo "Vault secrets initialized successfully."
