#!/bin/bash

# Everything stays in the adapter's schema: the seeded rows (${agency_adapter_tbls}) and Liquibase's own
# databasechangelog/databasechangeloglock tables, so the migration user needs no access to any other schema
# (it does need CREATE on agency_adapter_tbls for the tracking tables on the first run).
agency_adapter_schema=${agency_adapter_schema:-agency_adapter_tbls}
liquibase_schema=${LIQUIBASE_LIQUIBASE_SCHEMA_NAME:-$agency_adapter_schema}

echo "Starting liquibase update-sql with context $context "
liquibase --driver=org.postgresql.Driver --contexts=$context update-sql --liquibase-schema-name=$liquibase_schema -Dagency_adapter_tbls=$agency_adapter_schema
echo "====================================================="

# Run Liquibase update command
echo "Starting liquibase update with context $context "
liquibase --driver=org.postgresql.Driver --contexts=$context update --liquibase-schema-name=$liquibase_schema -Dagency_adapter_tbls=$agency_adapter_schema
echo "====================================================="

# No configurator reload: the adapter reads pkfare_configs itself at startup. To apply changed rows, call
# POST /reload once on the adapter (it broadcasts the reload to every pod over Kafka), or restart the pods.

exit 0
