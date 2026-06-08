package com.air.navigation.services;

import io.minio.*;
import io.minio.http.Method;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class MinioService {

    private final MinioClient minioClient;
    private final String bucket;
    private boolean minioAvailable = false;

    /** Dossier de fallback si MinIO est indisponible */
    private static final String LOCAL_UPLOAD_DIR = "uploads";

    public MinioService(
            @Value("${minio.endpoint:http://localhost:9000}") String endpoint,
            @Value("${minio.access-key:minioadmin}")         String accessKey,
            @Value("${minio.secret-key:minioadmin}")         String secretKey,
            @Value("${minio.bucket:skyguide-chat}")          String bucket) {
        this.bucket = bucket;
        this.minioClient = MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
        ensureBucketExists();
    }

    private void ensureBucketExists() {
        try {
            boolean exists = minioClient.bucketExists(
                    BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                log.info("MinIO — Bucket '{}' cree.", bucket);
            }
            minioAvailable = true;
            log.info("MinIO — Connexion etablie. Bucket: '{}'", bucket);
        } catch (Exception e) {
            minioAvailable = false;
            log.warn("MinIO — Indisponible, fallback stockage local active : {}", e.getMessage());
            initLocalStorage();
        }
    }

    private void initLocalStorage() {
        try {
            Path dir = Paths.get(LOCAL_UPLOAD_DIR);
            if (!Files.exists(dir)) {
                Files.createDirectories(dir);
                log.info("MinIO fallback — Dossier '{}' cree.", LOCAL_UPLOAD_DIR);
            }
        } catch (Exception e) {
            log.error("Impossible de creer le dossier uploads local : {}", e.getMessage());
        }
    }

    /**
     * Upload un fichier.
     * Essaie MinIO en priorite ; si indisponible, sauvegarde localement.
     * @return URL d'acces au fichier
     */
    public String uploadFile(MultipartFile file, String folder) {
        if (minioAvailable) {
            try {
                return uploadToMinio(file, folder);
            } catch (Exception e) {
                log.warn("MinIO — echec upload, passage au stockage local : {}", e.getMessage());
                minioAvailable = false;
            }
        }
        return saveLocally(file, folder);
    }

    private String uploadToMinio(MultipartFile file, String folder) throws Exception {
        String ext = getExtension(file.getOriginalFilename());
        String objectName = folder + "/" + UUID.randomUUID() + ext;

        minioClient.putObject(PutObjectArgs.builder()
                .bucket(bucket)
                .object(objectName)
                .stream(file.getInputStream(), file.getSize(), -1)
                .contentType(file.getContentType())
                .build());

        log.info("MinIO — Fichier uploade : {}", objectName);
        return getPresignedUrl(objectName);
    }

    /**
     * Fallback : sauvegarde locale dans /uploads/{folder}/
     * @return URL servie par /api/files/...
     */
    private String saveLocally(MultipartFile file, String folder) {
        try {
            String ext      = getExtension(file.getOriginalFilename());
            String filename = UUID.randomUUID() + ext;
            Path   dir      = Paths.get(LOCAL_UPLOAD_DIR, folder);
            Files.createDirectories(dir);
            Path dest = dir.resolve(filename);
            Files.copy(file.getInputStream(), dest);
            log.info("Stockage local — Fichier sauvegarde : {}/{}", folder, filename);
            return "/api/files/" + folder + "/" + filename;
        } catch (Exception e) {
            log.error("Erreur stockage local : {}", e.getMessage());
            throw new RuntimeException("Impossible de sauvegarder le fichier : " + e.getMessage());
        }
    }

    /**
     * Genere une URL presidnee valide 7 jours (MinIO uniquement).
     */
    public String getPresignedUrl(String objectName) {
        try {
            return minioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(bucket)
                    .object(objectName)
                    .expiry(7, TimeUnit.DAYS)
                    .build());
        } catch (Exception e) {
            log.error("MinIO — Erreur URL presidnee : {}", e.getMessage());
            return "";
        }
    }

    public InputStream downloadFile(String objectName) {
        try {
            return minioClient.getObject(GetObjectArgs.builder()
                    .bucket(bucket).object(objectName).build());
        } catch (Exception e) {
            throw new RuntimeException("Erreur telechargement fichier : " + e.getMessage());
        }
    }

    public void deleteFile(String objectName) {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(bucket).object(objectName).build());
        } catch (Exception e) {
            log.warn("MinIO — Erreur suppression '{}' : {}", objectName, e.getMessage());
        }
    }

    public boolean isMinioAvailable() {
        return minioAvailable;
    }

    private String getExtension(String filename) {
        if (filename == null || !filename.contains(".")) return "";
        return "." + filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
    }
}
