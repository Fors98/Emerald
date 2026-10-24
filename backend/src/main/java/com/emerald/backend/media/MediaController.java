package com.emerald.backend.media;

import com.emerald.backend.audit.AuditService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.*;
import java.security.Principal;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@RestController
public class MediaController {
    private final Path directory;
    private final AuditService audit;

    public MediaController(@Value("${emerald.media.directory:./media}") String directory, AuditService audit) {
        this.directory = Path.of(directory).toAbsolutePath().normalize();
        this.audit = audit;
    }

    @PostMapping(value = "/admin/media", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public synchronized Map<String, String> upload(@RequestParam("file") MultipartFile file, Principal principal)
            throws IOException {
        if (file.isEmpty() || file.getSize() > 5L * 1024 * 1024) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose an image up to 5 MB");
        }
        BufferedImage decoded;
        String format;
        try (ImageInputStream input = ImageIO.createImageInputStream(file.getInputStream())) {
            if (input == null) throw invalidImage();
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw invalidImage();
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                format = reader.getFormatName().toLowerCase(java.util.Locale.ROOT);
                if (!format.equals("png") && !format.equals("jpeg")) throw invalidImage();
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || (long) width * height > 16_000_000) throw invalidImage();
                decoded = reader.read(0);
                if (decoded == null) throw invalidImage();
            } finally {
                reader.dispose();
            }
        } catch (IOException | IllegalArgumentException exception) {
            throw invalidImage();
        }
        String extension = format.equals("jpeg") ? "jpg" : "png";
        String name = UUID.randomUUID() + "." + extension;
        Files.createDirectories(directory);
        Path destination = directory.resolve(name);
        Path temporary = Files.createTempFile(directory, ".upload-", ".tmp");
        boolean published = false;
        try {
            // Fresh encoding discards original metadata and trailing embedded content.
            if (!ImageIO.write(decoded, format, temporary.toFile())) throw invalidImage();
            Files.move(temporary, destination);
            published = true;
            audit.record(principal.getName(), "IMAGE_UPLOADED", name);
        } catch (IOException | RuntimeException exception) {
            if (published) Files.deleteIfExists(destination);
            throw exception;
        } finally {
            Files.deleteIfExists(temporary);
        }
        return Map.of("url", "/api/media/" + name);
    }

    @GetMapping("/media/{filename}")
    public ResponseEntity<Resource> read(@PathVariable String filename) throws IOException {
        if (!filename.matches("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}\\.(png|jpg)")) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        Path path = directory.resolve(filename);
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        return ResponseEntity.ok()
                .contentType(filename.endsWith(".png") ? MediaType.IMAGE_PNG : MediaType.IMAGE_JPEG)
                .contentLength(Files.size(path))
                .cacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic().immutable())
                .body(new FileSystemResource(path));
    }

    private static ResponseStatusException invalidImage() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a valid PNG or JPEG up to 16 megapixels");
    }
}
