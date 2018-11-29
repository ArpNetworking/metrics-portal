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
package com.arpnetworking.metrics.portal.reports;

import java.util.Properties;

import javax.mail.BodyPart;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;


public class EmailBuilder {
    public static MimeMessage buildImageEmail(String recipient, String subject, String html, byte[] pdf) throws MessagingException {
        Properties props = new Properties();
        props.put("mail.smtp.host", "localhost");
        props.put("mail.smtp.port", "25");
        final MimeMessage mailMessage = new MimeMessage(Session.getDefaultInstance(props));
        mailMessage.addRecipients(Message.RecipientType.TO, recipient);
        mailMessage.setFrom("no-reply+amp-reporting@dropbox.com");
        mailMessage.setSubject(subject);

        final MimeMultipart multipart = new MimeMultipart();
        BodyPart pdfPart = new MimeBodyPart();
        pdfPart.setContent(pdf, "application/pdf");
        multipart.addBodyPart(pdfPart);
        BodyPart htmlPart = new MimeBodyPart();
        htmlPart.setContent(html, "text/html");
        multipart.addBodyPart(htmlPart);

        mailMessage.setContent(multipart);

        return mailMessage;
    }
}
