package com.arpnetworking.metrics.portal.reports;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Properties;

import javax.mail.*;
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
