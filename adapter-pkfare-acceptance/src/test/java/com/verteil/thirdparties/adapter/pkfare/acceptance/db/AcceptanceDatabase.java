package com.verteil.thirdparties.adapter.pkfare.acceptance.db;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Seeds {@code agency_adapter_tbls.pkfare_configs} for a run and puts the previous rows back afterwards.
 * {@code acceptance.gradle} runs {@code seed} before starting the adapter (which loads the table at startup) and
 * {@code restore} when the run ends. {@code seed} saves the rows it replaces to a backup file first, and restores a
 * backup an interrupted run left behind, so a developer's own configuration survives. Only this table is touched,
 * so it works as the restricted {@code pkfare_user}.
 */
public final class AcceptanceDatabase {

    private static final String TABLE = "agency_adapter_tbls.pkfare_configs";
    private static final String SEED_SQL = "/db/pkfare_configs.sql";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final String url;
    private final String user;
    private final String password;
    private final Path backupFile;

    /** One {@code pkfare_configs} row, as kept in the backup file. */
    record Row(long id, String configGroup, String name, String service, String value) {
    }

    private AcceptanceDatabase(String url, String user, String password, Path backupFile) {
        this.url = url;
        this.user = user;
        this.password = password;
        this.backupFile = backupFile;
    }

    public static void main(String[] args) throws SQLException, IOException {
        var database = new AcceptanceDatabase(required("acceptance.db.url"), required("acceptance.db.user"),
                required("acceptance.db.password"), Path.of(required("acceptance.db.backupFile")));
        switch (args.length == 1 ? args[0] : "") {
            case "seed" -> database.seed();
            case "restore" -> database.restore();
            default -> throw new IllegalArgumentException("Usage: AcceptanceDatabase seed|restore");
        }
    }

    private void seed() throws SQLException, IOException {
        restore();
        try (Connection connection = connect()) {
            connection.setAutoCommit(false);
            List<Row> previousRows = readRows(connection);
            Files.createDirectories(backupFile.getParent());
            OBJECT_MAPPER.writeValue(backupFile.toFile(), previousRows);
            deleteRows(connection);
            try (Statement statement = connection.createStatement()) {
                statement.execute(seedSql());
            }
            connection.commit();
            System.out.printf("Seeded %s for acceptance; %d previous rows saved to %s%n", TABLE, previousRows.size(), backupFile);
        }
    }

    private void restore() throws SQLException, IOException {
        if (!Files.exists(backupFile)) {
            return;
        }
        List<Row> rows = OBJECT_MAPPER.readValue(backupFile.toFile(), new TypeReference<>() {
        });
        try (Connection connection = connect()) {
            connection.setAutoCommit(false);
            deleteRows(connection);
            insertRows(connection, rows);
            connection.commit();
        }
        Files.delete(backupFile);
        System.out.printf("Restored %d rows of %s%n", rows.size(), TABLE);
    }

    private Connection connect() {
        try {
            return DriverManager.getConnection(url, user, password);
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot connect to the acceptance database " + url + " as " + user
                    + ". Set ACCEPTANCE_DB_URL / ACCEPTANCE_DB_USER / ACCEPTANCE_DB_PASSWORD.", e);
        }
    }

    private static List<Row> readRows(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT id, config_group, name, service, value FROM " + TABLE + " ORDER BY id")) {
            List<Row> rows = new ArrayList<>();
            while (resultSet.next()) {
                rows.add(new Row(resultSet.getLong(1), resultSet.getString(2), resultSet.getString(3),
                        resultSet.getString(4), resultSet.getString(5)));
            }
            return rows;
        }
    }

    private static void deleteRows(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM " + TABLE);
        }
    }

    private static void insertRows(Connection connection, List<Row> rows) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO " + TABLE + "(id, config_group, name, service, value) VALUES (?, ?, ?, ?, ?)")) {
            rows.forEach(row -> addBatch(statement, row));
            statement.executeBatch();
        }
    }

    private static void addBatch(PreparedStatement statement, Row row) {
        try {
            statement.setLong(1, row.id());
            statement.setString(2, row.configGroup());
            statement.setString(3, row.name());
            statement.setString(4, row.service());
            statement.setString(5, row.value());
            statement.addBatch();
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot restore row " + row, e);
        }
    }

    private static String seedSql() {
        try (InputStream inputStream = Objects.requireNonNull(AcceptanceDatabase.class.getResourceAsStream(SEED_SQL),
                SEED_SQL + " not found")) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String required(String property) {
        return Objects.requireNonNull(System.getProperty(property), "System property " + property + " is not set");
    }
}
