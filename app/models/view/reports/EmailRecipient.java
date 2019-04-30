package models.view.reports;

import models.internal.impl.DefaultEmailRecipient;

public class EmailRecipient extends Recipient {
    public static EmailRecipient fromInternal(final DefaultEmailRecipient recipient, final ReportFormat format) {
        final EmailRecipient viewRecipient = new EmailRecipient();
        viewRecipient.setId(recipient.getId());
        viewRecipient.setAddress(recipient.getAddress());
        viewRecipient.setFormat(format);
        return viewRecipient;
    }
}
