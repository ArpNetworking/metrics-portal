/*
 * Copyright 2019 Dropbox, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.arpnetworking.metrics.portal.reports.impl.chrome;

import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;

/**
 * A relatively minimal interface for a Chrome tab's dev tools.
 *
 * @author Spencer Pearson (spencerpearson at dropbox dot com)
 */
public interface DevToolsService {
    /**
     * Evaluates some JavaScript in the tab.
     *
     * @param js A JavaScript expression. (If you need multiple statements, wrap them in an
     *   <a href="https://developer.mozilla.org/en-US/docs/Glossary/IIFE">IIFE</a>.)
     * @return The result of the evaluation. (e.g. a String, a Double, a-- I don't know about arrays/objects.)
     */
    Object evaluate(String js);

    /**
     * Creates a PDF capturing how the page currently displays.
     *
     * @param pageWidth How wide the PDF's pages should be, in inches.
     * @param pageHeight How tall the PDF's pages should be, in inches.
     * @return Raw bytes of the PDF, suitable for e.g. writing to a .pdf file.
     */
    byte[] printToPdf(double pageWidth, double pageHeight);

    /**
     *
     * Forces the tab to navigate to a new URL.
     *
     * @param url The URL to navigate to.
     * @throws InterruptedException If the thread is interrupted while waiting for the page to load.
     * @throws ExecutionException If something goes wrong while navigating to the page.
     */
    void navigate(String url) throws InterruptedException, ExecutionException;

    /**
     * Closes the dev tools. After close() is called, any further interaction is illegal
     * (except further calls to close(), which are no-ops).
     */
    void close();

    /**
     * Create a {@link CompletionStage} that completes when {@code eventName} fires, or immediately if the event has already fired.
     *
     * (Context: event handlers can only be registered on a page after it's finished loading.
     * This introduces the possibility that the event you want to listen for will have <i>already happened</i> by the time
     * you attach a listener for it. It's impossible in the general case to tell whether this has happened,
     * but if you can reliably tell whether the event has fired, then you can ensure that you either catch the event
     * <i>or</i> notice that it's already fired.
     *
     * @param eventName The name of the JavaScript event to listen for.
     * @param ready Determine whether the event has already fired.
     * @return A {@link CompletionStage} that completes when the event has fired (or immediately, if {@code ready} returns true).
     */
    CompletionStage<Void> nowOrOnEvent(String eventName, Supplier<Boolean> ready);
}
