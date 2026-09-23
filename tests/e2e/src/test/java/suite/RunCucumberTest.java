package suite;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;

import static io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME;

/**
 * Entry point that lets Surefire discover and run the Cucumber scenarios.
 * Without this, cucumber-junit-platform-engine is on the classpath but never
 * invoked: Surefire only picks up classes named with a "Test" prefix or
 * suffix, and none of the page objects or step-definition classes match.
 */
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "steps")
public class RunCucumberTest {
}
