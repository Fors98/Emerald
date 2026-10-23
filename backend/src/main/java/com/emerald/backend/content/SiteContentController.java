package com.emerald.backend.content;

import org.springframework.web.bind.annotation.*;
import java.util.Map;
import java.util.TreeMap;

@RestController
@RequestMapping("/content")
public class SiteContentController {

    private final SiteContentRepository repository;

    public SiteContentController(
            SiteContentRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public Map<String, String> getContent() {
        Map<String, String> content = new TreeMap<>();

        repository.findAll().forEach(item ->
            content.put(item.getKey(), item.getValue())
        );

        return content;
    }
}
