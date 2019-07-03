package com.arpnetworking.metrics.portal.reports.impl.testing;

import com.google.common.collect.ImmutableMap;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import java.io.File;
import java.io.IOException;

public class Utils {
    public static final String CHROME_PATH = "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome";

    public static Config createChromeRendererConfig() throws IOException {
        if (!new File(CHROME_PATH).canExecute()) {
            throw new IOException("Utils#CHROME_PATH should point to an executable file; got " + CHROME_PATH);
        }

        return ConfigFactory.parseMap(ImmutableMap.of(
                "chromePath", CHROME_PATH,
                "chromeArgs", ImmutableMap.of(
                        "headless",true,
                        "no-sandbox", true
                )
        ));
    }

}
