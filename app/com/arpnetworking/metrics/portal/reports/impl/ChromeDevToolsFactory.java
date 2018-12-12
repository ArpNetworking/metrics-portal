package com.arpnetworking.metrics.portal.reports.impl;

import com.github.kklisura.cdt.launch.ChromeLauncher;
import com.github.kklisura.cdt.services.ChromeDevToolsService;
import com.github.kklisura.cdt.services.ChromeService;
import com.github.kklisura.cdt.services.types.ChromeTab;
import com.google.inject.Inject;

public class ChromeDevToolsFactory {

    private final ChromeLauncher launcher = new ChromeLauncher();
    private final ChromeService chromeService = launcher.launch(true);

    @Inject
    public ChromeDevToolsFactory() {}

    public ChromeDevToolsServiceWrapper create(boolean ignoreCertificateErrors) {
        final ChromeTab tab = chromeService.createTab();
        ChromeDevToolsService result = chromeService.createDevToolsService(tab);
        if (ignoreCertificateErrors) {
            result.getSecurity().setIgnoreCertificateErrors(true);
        }
        return new ChromeDevToolsServiceWrapper(result);
    }
}
