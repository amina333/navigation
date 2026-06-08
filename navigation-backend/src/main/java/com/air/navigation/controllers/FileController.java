package com.air.navigation.controllers;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.MalformedURLException;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Sert les fichiers uploadés localement (fallback MinIO).
 * URL pattern : /api/files/{folder}/{filename}
 */
@RestController
@RequestMapping("/api/files")
@Slf4j
public class FileController {

    private static final String UPLOAD_DIR = "uploads";

    @GetMapping("/**")
    public ResponseEntity<Resource> serveFile(HttpServletRequest request) {
        // Extraire le chemin après /api/files/
        String requestPath = request.getRequestURI();
        String relativePath = requestPath.replaceFirst("^/api/files/", "");

        try {
            Path filePath = Paths.get(UPLOAD_DIR).resolve(relativePath).normalize();
            Resource resource = new UrlResource(filePath.toUri());

            if (!resource.exists() || !resource.isReadable()) {
                log.warn("Fichier non trouve : {}", relativePath);
                return ResponseEntity.notFound().build();
            }

            // Determiner le content-type
            String contentType;
            try {
                contentType = request.getServletContext().getMimeType(resource.getFile().getAbsolutePath());
            } catch (Exception e) {
                contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE;
            }
            if (contentType == null) contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE;

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "inline; filename=\"" + resource.getFilename() + "\"")
                    .body(resource);

        } catch (MalformedURLException e) {
            log.error("URL invalide pour le fichier : {}", relativePath);
            return ResponseEntity.badRequest().build();
        }
    }
}
