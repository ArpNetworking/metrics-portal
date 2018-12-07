/*
 * Copyright 2018 Dropbox
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
package com.arpnetworking.metrics.portal.reports.impl;

import com.arpnetworking.metrics.portal.reports.Report;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.simplejavamail.email.Email;
import org.simplejavamail.mailer.Mailer;

import java.io.IOException;

/**
 * Tests class <code>EmailReportSink</code>.
 *
 * @author Brandon Arp (brandon dot arp at smartsheet dot com)
 */
public class EmailReportSinkTest {

    @Test
    public void testSend() throws IOException {
        Mailer mailer = Mockito.mock(Mailer.class);
        EmailReportSink sink = new EmailReportSink("recip@invalid", mailer);

        sink.send(new Report("Today's P75 TTI", "my html", "my pdf".getBytes()));
        ArgumentCaptor<Email> message = ArgumentCaptor.forClass(Email.class);
        Mockito.verify(mailer).sendMail(message.capture());

        Assert.assertEquals("[Report] Today's P75 TTI", message.getValue().getSubject());
        Assert.assertEquals("my html", message.getValue().getHTMLText());
        Assert.assertEquals("my pdf", message.getValue().getAttachments().get(0).readAllData());
    }

}
