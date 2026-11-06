package com.verteil.thirdparties.adapter.pkfare.config;

import com.verteil.configurator.core.ConfigurationEvaluator;
import com.verteil.configurator.core.ConfigurationTree;
import com.verteil.configurator.vdc.data.VdcConfiguration;
import com.verteil.configurator.vdc.data.VdcConfigurationContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.List;
import java.util.UUID;
import java.util.function.Function;

import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.PKFARE;
import static com.verteil.thirdparties.adapter.pkfare.common.utils.PkfareConstants.PKFARE_API;
import static com.verteil.thirdparties.adapter.pkfare.config.ConfiguratorConfigs.buildPkfareConfigTree;
import static org.assertj.core.api.Assertions.assertThat;

/** Runs the production query, tree and service against {@code pkfare_configs} in an in-memory H2 database. */
class EmbeddedVdcConfiguratorServiceTest {

    private static final String SEARCH = "Search";
    private static final String AIR_SHOPPING = "AirShopping";

    private JdbcTemplate jdbcTemplate;
    private Function<String, ConfigurationTree<String>> treeLoader;

    @BeforeEach
    void createTable() {
        var dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=PostgreSQL;NON_KEYWORDS=VALUE;DB_CLOSE_DELAY=-1");
        jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("create schema agency_adapter_tbls");
        jdbcTemplate.execute("create table agency_adapter_tbls.pkfare_configs(id bigserial primary key, "
                + "config_group varchar(100) not null, name varchar(150) not null, service varchar(100), value text)");
        var dataStream = new ConfiguratorConfigs().pkfareConfigDataStream(dataSource);
        treeLoader = _ -> buildPkfareConfigTree(dataStream);
    }

    @Test
    void serviceRowOverridesNullServiceRow() {
        insert("TIMEOUT_MILLIS", null, "10000");
        insert("TIMEOUT_MILLIS", AIR_SHOPPING, "30000");
        insert("HOST", null, "https://entry-gate");

        List<VdcConfiguration> configurations = service().fetchConfigurationMultiContexts(
                List.of(chainContext(SEARCH), chainContext(AIR_SHOPPING)));

        assertThat(configurations.get(0).getString(PKFARE_API, "TIMEOUT_MILLIS")).isEqualTo("10000");
        assertThat(configurations.get(1).getString(PKFARE_API, "TIMEOUT_MILLIS")).isEqualTo("30000");
        assertThat(configurations).allSatisfy(configuration ->
                assertThat(configuration.getString(PKFARE_API, "HOST")).isEqualTo("https://entry-gate"));
    }

    @Test
    void rowOfAnotherServiceIsNotReturned() {
        insert("ENABLE_SERVICE", SEARCH, "true");

        var service = service();

        assertThat(service.fetchConfiguration(chainContext(SEARCH)).getBoolean(PKFARE_API, "ENABLE_SERVICE", false))
                .isTrue();
        assertThat(service.fetchConfiguration(chainContext(AIR_SHOPPING)).containsKey(PKFARE_API + ".ENABLE_SERVICE"))
                .isFalse();
    }

    @Test
    void reloadPicksUpChangedRows() {
        insert("SEARCH_CACHE_TTL_SECONDS", null, "120");
        var service = service();

        jdbcTemplate.update("update agency_adapter_tbls.pkfare_configs set value = '0'");

        assertThat(service.fetchConfiguration(PKFARE_API, "SEARCH_CACHE_TTL_SECONDS", chainContext(SEARCH).getArgs()))
                .as("rows are served from memory until a reload")
                .isEqualTo("120");

        var result = service.reload();

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.reloaded()).containsExactly(ConfiguratorConfigs.PKFARE_CONFIG_TREE);
        assertThat(service.fetchConfiguration(PKFARE_API, "SEARCH_CACHE_TTL_SECONDS", chainContext(SEARCH).getArgs()))
                .isEqualTo("0");
    }

    @Test
    void reloadsOneTreeByName() {
        insert("HOST", null, "https://entry-gate");
        var service = service();

        jdbcTemplate.update("update agency_adapter_tbls.pkfare_configs set value = 'https://entry-gate-2'");
        var result = service.reload(ConfiguratorConfigs.PKFARE_CONFIG_TREE);

        assertThat(result.reloaded()).containsExactly(ConfiguratorConfigs.PKFARE_CONFIG_TREE);
        assertThat(service.fetchConfiguration(chainContext(SEARCH)).getString(PKFARE_API, "HOST"))
                .isEqualTo("https://entry-gate-2");
    }

    @Test
    void reloadOfAnUnknownTreeFails() {
        var result = service().reload("unknownTree");

        assertThat(result.reloaded()).isEmpty();
        assertThat(result.failed()).containsExactly("unknownTree");
    }

    @Test
    void failedReloadKeepsLoadedValues() {
        insert("HOST", null, "https://entry-gate");
        var evaluator = evaluator();
        var service = new EmbeddedVdcConfiguratorService(evaluator, _ -> {
            throw new IllegalStateException("database down");
        });

        var result = service.reload();

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.failed()).containsExactly(ConfiguratorConfigs.PKFARE_CONFIG_TREE);
        assertThat(service.fetchConfiguration(chainContext(SEARCH)).getString(PKFARE_API, "HOST"))
                .isEqualTo("https://entry-gate");
    }

    private EmbeddedVdcConfiguratorService service() {
        return new EmbeddedVdcConfiguratorService(evaluator(), treeLoader);
    }

    @SuppressWarnings("unchecked")
    private ConfigurationEvaluator<String> evaluator() {
        return new ConfigurationEvaluator<>(new ConfigurationTree[]{treeLoader.apply(ConfiguratorConfigs.PKFARE_CONFIG_TREE)});
    }

    /** The context connector-common's {@code AddProviderApiParamsInConfigContextCmd} builds for each service. */
    private static VdcConfigurationContext chainContext(String service) {
        return VdcConfigurationContext.newBuilder()
                .carrier(PKFARE)
                .apiService(service)
                .iata(PKFARE)
                .officeCode("SHA-TEST")
                .addParams(PKFARE_API)
                .build();
    }

    private void insert(String name, String service, String value) {
        jdbcTemplate.update("insert into agency_adapter_tbls.pkfare_configs(config_group, name, service, value) "
                + "values (?, ?, ?, ?)", PKFARE_API, name, service, value);
    }
}
