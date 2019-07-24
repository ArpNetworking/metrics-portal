package com.arpnetworking.metrics.portal.reports.impl.chrome;

import java.util.Map;

/**
 * A factory that sits atop a Chrome instance and creates tabs / dev-tools instances.
 *
 * @author Spencer Pearson (spencerpearson at dropbox dot com)
 */
public interface DevToolsFactory {
    /**
     * Create a {@link DevToolsService}.
     *
     * @param ignoreCertificateErrors whether the created tab should ignore certificate errors when loading resources.
     * @param chromeArgs any extra command-line flags that should be passed to Chrome.
     * @return the created service.
     */
    DevToolsService create(boolean ignoreCertificateErrors, Map<String, Object> chromeArgs);
}
