-- Acceptance content of agency_adapter_tbls.pkfare_configs; it replaces the table's rows for the length of a run.
-- Search and AirShopping read the same values, so every row has a NULL service. Differences from the Liquibase
-- defaults are deliberate: HOST is WireMock, SEARCH_CACHE_TTL_SECONDS = 0 so every scenario reaches Entry-Gate.
INSERT INTO agency_adapter_tbls.pkfare_configs(config_group, name, service, value)
VALUES ('PKFARE_API', 'ENABLE_SERVICE', NULL, 'true'),
       ('PKFARE_API', 'MESSAGE_LOG', NULL, 'true'),
       ('PKFARE_API', 'HOST', NULL, 'http://localhost:8091'),
       ('PKFARE_API', 'AUTH_PATH', NULL, '/oauth2/token'),
       ('PKFARE_API', 'USERNAME', NULL, 'acceptance-client'),
       ('PKFARE_API', 'PASSWORD', NULL, 'acceptance-secret'),
       ('PKFARE_API', 'VENDOR_CODES', NULL, 'SHA-TEST'),
       ('PKFARE_API', 'PATH', NULL, '/entrygate/rest/request'),
       ('PKFARE_API', 'TIMEOUT_MILLIS', NULL, '30000'),
       ('PKFARE_API', 'FARE_PREFERENCES', NULL, 'PUBL,PVT'),
       ('PKFARE_API', 'SHOP_RESULT_PREFERENCE', NULL, 'OPTIMIZED'),
       ('PKFARE_API', 'SHOP_RESULT_PREFERENCE_TAGGED', NULL, 'OPTIMIZED'),
       ('PKFARE_API', 'SORT_ORDER', NULL, 'STOP,PRICE,DEPARTURE_TIME'),
       ('PKFARE_API', 'TICKET_ISSUE_TIME', NULL, '120'),
       ('PKFARE_API', 'DEFAULT_PRODUCT_TYPE', NULL, 'PUB'),
       ('PKFARE_API', 'SEARCH_CACHE_TTL_SECONDS', NULL, '0');
