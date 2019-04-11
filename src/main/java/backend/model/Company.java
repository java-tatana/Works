package backend.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.TimeZone;

@Document
public class Company {

    @Id
    private String id;
    private String title;
    private String websiteUrl;
    private String supportUrl;
    private String notifyFromEmail;

    public Company() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getWebsiteUrl() {
        return websiteUrl;
    }

    public void setWebsiteUrl(String websiteUrl) {
        this.websiteUrl = websiteUrl;
    }

    public String getSupportUrl() {
        return supportUrl;
    }

    public void setSupportUrl(String supportUrl) {
        this.supportUrl = supportUrl;
    }

    public String getNotifyFromEmail() {
        return notifyFromEmail;
    }

    public void setNotifyFromEmail(String notifyFromEmail) {
        this.notifyFromEmail = notifyFromEmail;
    }
}

