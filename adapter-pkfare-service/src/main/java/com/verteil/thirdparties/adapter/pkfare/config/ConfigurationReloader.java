package com.verteil.thirdparties.adapter.pkfare.config;

import java.util.List;

/**
 * Re-reads {@code pkfare_configs} into this pod's memory (rows are otherwise loaded once, at startup). Reloading
 * every pod is {@link com.verteil.thirdparties.adapter.pkfare.config.sync.ClusterConfigurationReloader}'s job.
 */
public interface ConfigurationReloader {

    /** Rebuilds every configuration tree. */
    ReloadResult reload();

    /** Rebuilds one configuration tree; a tree name this pod doesn't have is reported as failed. */
    ReloadResult reload(String treeName);

    /** Tree names that were reloaded and that failed (and still serve their previous values). */
    record ReloadResult(List<String> reloaded, List<String> failed) {
        public boolean isSuccess() {
            return failed.isEmpty();
        }
    }
}
