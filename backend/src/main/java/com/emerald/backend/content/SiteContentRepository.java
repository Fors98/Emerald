package com.emerald.backend.content;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SiteContentRepository
        extends JpaRepository<SiteContent, String> {
}
