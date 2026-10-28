package com.verteil.thirdparties.adapter.pkfare.client.model;

public record HttpCallResult(int statusCode, String body, long responseTimeMillis) {}
