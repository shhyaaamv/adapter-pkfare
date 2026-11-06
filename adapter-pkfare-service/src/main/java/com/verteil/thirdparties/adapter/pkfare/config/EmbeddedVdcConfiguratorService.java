package com.verteil.thirdparties.adapter.pkfare.config;

import com.verteil.configurator.core.ConfigurationEvaluator;
import com.verteil.configurator.core.ConfigurationTree;
import com.verteil.configurator.vdc.VdcConfiguratorService;
import com.verteil.configurator.vdc.data.VdcConfiguration;
import com.verteil.configurator.vdc.data.VdcConfigurationContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * {@link VdcConfiguratorService} evaluated in process from the configuration trees, instead of over HTTP from
 * vdc-configurator. It is the vdc-configurator server's evaluation without its argument enhancers. Cluster sync
 * (every pod reloading together) is in {@code config.sync}, on top of {@link ConfigurationReloader}. The library
 * implementation other services use ({@code VdcConfiguratorServiceImpl} in {@code vdc-configurator-lib}) ships
 * its own, incompatible copies of {@link VdcConfiguratorService} and {@link VdcConfiguration}, so it can't be used
 * next to connector-common.
 */
@Slf4j
@RequiredArgsConstructor
public class EmbeddedVdcConfiguratorService implements VdcConfiguratorService, ConfigurationReloader {

    private final ConfigurationEvaluator<String> evaluator;
    /** Builds a fresh tree (reading the database again) for a tree name. */
    private final Function<String, ConfigurationTree<String>> treeLoader;

    @Override
    public VdcConfiguration fetchConfiguration(VdcConfigurationContext context) {
        return new VdcConfiguration(evaluator.fetchConfiguration(context.getParams(), context.getArgs()));
    }

    @Override
    public String fetchConfiguration(String group, String name, Map<String, String> args) {
        return evaluator.fetchConfiguration(group, name, args);
    }

    @Override
    public List<VdcConfiguration> fetchConfigurationMultiContexts(List<VdcConfigurationContext> contexts) {
        return contexts.stream().map(this::fetchConfiguration).toList();
    }

    @Override
    public Map<String, VdcConfiguration> fetchMasterDataConfigs(VdcConfigurationContext context) {
        Map<String, Collection<String>> params = context.getParams();
        if (params.size() != 1) {
            throw new IllegalArgumentException(params.size() + " groups in the context; master data needs exactly one");
        }
        return fetchMasterDataConfigs(params.keySet().iterator().next(), context.getArgs());
    }

    @Override
    public Map<String, VdcConfiguration> fetchMasterDataConfigs(String group, Map<String, String> args) {
        return evaluator.fetchMasterDataConfig(group, args).entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> new VdcConfiguration(entry.getValue())));
    }

    @Override
    public void reloadConfigurations() {
        reload();
    }

    /**
     * Rebuilds every tree from the database, so changed rows apply without a restart. Trees are loaded once at
     * startup and otherwise only on a reload ({@code POST /reload}, or a reload broadcast from another pod); lookups
     * never touch the database. A tree that fails to load (e.g. database unreachable) keeps serving its last loaded
     * values. Reloads are serialized, so a local and a broadcast reload never interleave.
     */
    @Override
    public synchronized ReloadResult reload() {
        // copy: putConfigurationTree replaces entries of the map being iterated
        return reloadTrees(List.copyOf(evaluator.getConfigurationTrees().keySet()));
    }

    @Override
    public synchronized ReloadResult reload(String treeName) {
        if (!evaluator.getConfigurationTrees().containsKey(treeName)) {
            log.warn("No configuration tree {} to reload", treeName);
            return new ReloadResult(List.of(), List.of(treeName));
        }
        return reloadTrees(List.of(treeName));
    }

    private ReloadResult reloadTrees(List<String> treeNames) {
        Map<Boolean, List<String>> reloadedByOutcome = treeNames.stream()
                .sorted()
                .collect(Collectors.partitioningBy(this::reloadTree));
        return new ReloadResult(reloadedByOutcome.get(true), reloadedByOutcome.get(false));
    }

    private boolean reloadTree(String treeName) {
        try {
            evaluator.putConfigurationTree(treeLoader.apply(treeName));
            log.info("Reloaded configuration tree {}", treeName);
            return true;
        } catch (RuntimeException e) {
            log.error("Reloading configuration tree {} failed; keeping the loaded values", treeName, e);
            return false;
        }
    }
}
