package com.verteil.thirdparties.adapter.pkfare.acceptance;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;

import static io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.PLUGIN_PROPERTY_NAME;

@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("feature")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "com.verteil.thirdparties.adapter.pkfare.acceptance")
@ConfigurationParameter(key = PLUGIN_PROPERTY_NAME, value = """
        pretty,
        html:build/reports/cucumber/cucumber.html,
        timeline:build/reports/cucumber/timeline
        """)
public class RunAcceptanceTest {
}
