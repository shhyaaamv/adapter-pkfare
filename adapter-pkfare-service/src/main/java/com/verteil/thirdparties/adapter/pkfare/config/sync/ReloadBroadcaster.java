package com.verteil.thirdparties.adapter.pkfare.config.sync;

import java.util.Collection;

/** Tells the other pods to reload configuration trees. */
public interface ReloadBroadcaster {

    /**
     * Returns once every message is acknowledged.
     *
     * @throws RuntimeException when a message couldn't be sent in time
     */
    void broadcastReload(Collection<String> treeNames);
}
