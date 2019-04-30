package models.view.reports;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import models.internal.impl.DefaultEmailRecipient;

import java.util.UUID;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = EmailRecipient.class, name = "Email"),
})
public abstract class Recipient {
    public void setId(final UUID id) {
        this._id = id;
    }

    public void setAddress(final String address) {
        this._address = address;
    }

    public void setFormat(final ReportFormat format) {
        this._format = format;
    }

    static Recipient fromInternal(final models.internal.reports.Recipient recipient, final ReportFormat format) {
        final models.internal.reports.Recipient.Visitor<Recipient> visitor = new models.internal.reports.Recipient.Visitor<Recipient>() {
            @Override
            public Recipient visit(final DefaultEmailRecipient emailRecipient) {
                return EmailRecipient.fromInternal(recipient, format);
            }
        };
        return visitor.visit(recipient);
    }

    @JsonProperty("id")
    private UUID _id;
    @JsonProperty("address")
    private String _address;
    @JsonProperty("format")
    private ReportFormat _format;
}
