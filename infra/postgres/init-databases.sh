#!/bin/bash
set -e

for db in twende_auth twende_config twende_users twende_drivers twende_locations \
           twende_rides twende_pricing twende_matching twende_payments \
           twende_subscriptions twende_notifications twende_loyalty \
           twende_ratings twende_analytics twende_compliance; do
    psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" <<-EOSQL
        CREATE DATABASE $db;
        GRANT ALL PRIVILEGES ON DATABASE $db TO $POSTGRES_USER;
EOSQL
done
