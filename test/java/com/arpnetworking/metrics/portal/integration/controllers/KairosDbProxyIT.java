package java.com.arpnetworking.metrics.portal.integration.controllers;

import com.arpnetworking.metrics.portal.AkkaClusteringConfigFactory;
import com.typesafe.config.ConfigFactory;
import play.Application;
import play.inject.guice.GuiceApplicationBuilder;
import play.test.WithApplication;

/**
 * @author Gilligan Markham (gmarkham at dropbox dot com)
 */
public class KairosDbProxyIT extends WithApplication {
    @Override
    protected Application provideApplication() {
        return new GuiceApplicationBuilder()
                .loadConfig(ConfigFactory.load("portal.application.conf"))
                .configure("proxy.filterRollups", true)
                .configure(AkkaClusteringConfigFactory.generateConfiguration())
                .build();
    }
}
