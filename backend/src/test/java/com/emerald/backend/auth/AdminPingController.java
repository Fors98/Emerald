package com.emerald.backend.auth;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Test-only stand-in for a future /admin/** controller (AdminContactController etc.). */
@RestController
@RequestMapping("/admin")
class AdminPingController {

    @GetMapping("/ping")
    Map<String, String> ping() {
        return Map.of("status", "ok");
    }
}
