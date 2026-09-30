package com.emerald.backend.contact;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/contact")
public class ContactController {

    private final ContactRepository contactRepository;

    public ContactController(ContactRepository contactRepository) {
        this.contactRepository = contactRepository;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> submitContactForm(
            @Valid @RequestBody ContactRequest request) {

        Contact contact = new Contact(
                request.getName(),
                request.getEmail(),
                request.getCompany(),
                request.getProjectType(),
                request.getMessage()
        );

        Contact savedContact = contactRepository.save(contact);

        return Map.of(
                "id", savedContact.getId(),
                "receivedAt", savedContact.getReceivedAt(),
                "status", "received"
        );
    }
}
