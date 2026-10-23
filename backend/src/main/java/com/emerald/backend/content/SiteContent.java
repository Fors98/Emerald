package com.emerald.backend.content;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "site_content")
public class SiteContent {

    @Id
    @Column(name = "content_key", length = 120)
    private String key;

    @Column(name = "content_value",
            nullable = false,
            columnDefinition = "TEXT")
    private String value;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Version private Long version;
    public Long getVersion(){return version;}
    protected SiteContent() {}

    public SiteContent(String key, String value) {
        this.key = key;
        this.value = value;
        this.updatedAt = OffsetDateTime.now();
    }

    public void updateValue(String newValue) {
        this.value = newValue;
        this.updatedAt = OffsetDateTime.now();
    }

    public String getKey() {
        return key;
    }

    public String getValue() {
        return value;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
