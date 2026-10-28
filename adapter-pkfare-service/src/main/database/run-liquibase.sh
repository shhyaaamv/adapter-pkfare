#!/bin/bash

echo "Starting liquibase update-sql with context $context "
liquibase --driver=org.postgresql.Driver --contexts=$context update-sql --liquibase-schema-name=$LIQUIBASE_LIQUIBASE_SCHEMA_NAME -Dconfigurator_tbls=$configurator_schema
echo "====================================================="

# Run Liquibase update command
echo "Starting liquibase update with context $context "
liquibase --driver=org.postgresql.Driver --contexts=$context update --liquibase-schema-name=$LIQUIBASE_LIQUIBASE_SCHEMA_NAME -Dconfigurator_tbls=$configurator_schema
echo "====================================================="

# Reload vdc-configurator
echo "Reloading vdc-configurator and old configurator at $configurator_endpoint "
curl -i -X POST https://$configurator_endpoint/vdc-configurator/evaluator/reload
curl --location https://$configurator_endpoint/configurator/rest/clearcache

exit 0