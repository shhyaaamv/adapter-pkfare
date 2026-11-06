package com.verteil.thirdparties.adapter.pkfare.config.sync;

import com.verteil.thirdparties.adapter.pkfare.config.ConfigurationReloader;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/** Records reload calls: a tree name, or {@link #ALL} for a full reload. Safe to call from the subscriber's thread. */
class RecordingReloader implements ConfigurationReloader {

    static final String ALL = "*";
    static final String TREE = "pkfareConfigTree";

    final BlockingQueue<String> calls = new LinkedBlockingQueue<>();
    private final ReloadResult result;

    RecordingReloader() {
        this(new ReloadResult(List.of(TREE), List.of()));
    }

    RecordingReloader(ReloadResult result) {
        this.result = result;
    }

    @Override
    public ReloadResult reload() {
        calls.add(ALL);
        return result;
    }

    @Override
    public ReloadResult reload(String treeName) {
        calls.add(treeName);
        return result;
    }
}
