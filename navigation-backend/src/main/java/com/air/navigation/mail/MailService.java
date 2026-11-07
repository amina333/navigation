package com.air.navigation.mail;

import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Envoi des e-mails transactionnels (bienvenue, modification de compte, code de réinitialisation…).
 *
 * • SMTP configuré (MAIL_USERNAME / MAIL_PASSWORD) → envoi réel.
 * • Sinon (développement) → l'e-mail est écrit en HTML dans le dossier « mail-outbox »
 *   pour pouvoir le prévisualiser dans un navigateur. Aucune donnée n'est perdue.
 * Les envois sont asynchrones : une panne SMTP ne bloque jamais la création d'un compte.
 */
@Service
@Slf4j
public class MailService {

    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final String username;
    private final String from;
    private final String fromName;
    private final String frontendUrl;
    private final Path outbox;

    public MailService(ObjectProvider<JavaMailSender> mailSenderProvider,
                       @Value("${spring.mail.username:}") String username,
                       @Value("${app.mail.from:}") String from,
                       @Value("${app.mail.from-name:Skyguide Navigation}") String fromName,
                       @Value("${app.frontend-url:http://localhost:4200}") String frontendUrl,
                       @Value("${app.mail.outbox-dir:mail-outbox}") String outboxDir) {
        this.mailSenderProvider = mailSenderProvider;
        this.username = username;
        this.from = (from == null || from.isBlank()) ? username : from;
        this.fromName = fromName;
        this.frontendUrl = frontendUrl.replaceAll("/+$", "");
        this.outbox = Path.of(outboxDir).toAbsolutePath();
        log.info("Mail — {}", isSmtpConfigured()
                ? "SMTP actif (expéditeur " + this.from + ")"
                : "SMTP non configuré : les e-mails seront enregistrés dans " + this.outbox);
    }

    @Value("${spring.mail.host:}")
    private String host;

    @Value("${spring.mail.port:587}")
    private int port;

    /** État de la configuration (pour l'écran d'administration) */
    public java.util.Map<String, Object> status() {
        java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("smtpConfigured", isSmtpConfigured());
        m.put("host", host);
        m.put("port", port);
        m.put("from", from);
        m.put("outbox", outbox.toString());
        return m;
    }

    /** Envoi de test SYNCHRONE : renvoie le diagnostic exact en cas d'échec */
    public java.util.Map<String, Object> sendTest(String to) {
        java.util.Map<String, Object> res = new java.util.LinkedHashMap<>(status());
        String html = EmailTemplates.passwordChanged("Test", loginUrl())
                .replace("Mot de passe modifié", "E-mail de test")
                .replace("Le mot de passe de votre compte a été modifié avec succès", "La configuration SMTP de la plateforme fonctionne. Cet e-mail de test a été envoyé");
        if (!isSmtpConfigured()) {
            saveToOutbox(to, "E-mail de test", html, "test");
            res.put("sent", false);
            res.put("message", "SMTP non configuré : l'e-mail a été enregistré dans " + outbox
                    + ". Créez mail.local.properties (voir mail.local.properties.example) puis redémarrez le backend.");
            return res;
        }
        try {
            JavaMailSender sender = mailSenderProvider.getObject();
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setFrom(new InternetAddress(from, fromName, StandardCharsets.UTF_8.name()));
            helper.setTo(to);
            helper.setSubject("Skyguide Navigation — e-mail de test");
            helper.setText(htmlToText(html), html);
            sender.send(message);
            res.put("sent", true);
            res.put("message", "E-mail de test envoyé à " + to + ". Vérifiez aussi le dossier Spam / Courrier indésirable.");
        } catch (Exception e) {
            String raw = rootMessage(e);
            res.put("sent", false);
            res.put("error", raw);
            res.put("message", explain(raw));
            log.error("[MAIL] Test SMTP échoué : {}", raw);
        }
        return res;
    }

    private static String rootMessage(Throwable e) {
        Throwable t = e;
        while (t.getCause() != null && t.getCause() != t) t = t.getCause();
        return (e.getMessage() != null ? e.getMessage() : "") + (t != e && t.getMessage() != null ? " | " + t.getMessage() : "");
    }

    /** Traduit les erreurs SMTP courantes en action concrète */
    private String explain(String raw) {
        String r = raw.toLowerCase();
        if (r.contains("smtpclientauthentication is disabled") || r.contains("5.7.139"))
            return "Microsoft 365 refuse l'authentification SMTP pour cette boîte (SMTP AUTH désactivé par l'administrateur du tenant). Utilisez un compte Gmail avec mot de passe d'application.";
        if (r.contains("application-specific password") || r.contains("534-5.7.9") || r.contains("5.7.9"))
            return "Gmail exige un mot de passe d'application (validation en 2 étapes activée) : créez-le sur https://myaccount.google.com/apppasswords.";
        if (r.contains("username and password not accepted") || r.contains("535") || r.contains("authentication failed") || r.contains("authenticationfailed"))
            return "Identifiant ou mot de passe SMTP refusé. Pour Gmail, utilisez le mot de passe d'application (16 lettres, sans espaces), pas le mot de passe du compte.";
        if (r.contains("unknownhost") || r.contains("couldn't connect") || r.contains("connect timed out") || r.contains("connection refused"))
            return "Impossible de joindre le serveur SMTP " + host + ":" + port + " (réseau, pare-feu ou proxy de l'école qui bloque le port 587).";
        if (r.contains("sender address rejected") || r.contains("send as"))
            return "L'adresse d'expéditeur (app.mail.from) doit être celle du compte SMTP.";
        return "Échec de l'envoi : " + raw;
    }

    public boolean isSmtpConfigured() {
        return username != null && !username.isBlank() && mailSenderProvider.getIfAvailable() != null;
    }

    public String loginUrl() { return frontendUrl + "/login"; }

    public String resetUrl(String email) { return frontendUrl + "/forgot-password?email=" + java.net.URLEncoder.encode(email, StandardCharsets.UTF_8); }

    // ══════════════════════════════════════════════════════
    // E-MAILS MÉTIER
    // ══════════════════════════════════════════════════════

    @Async
    public void sendWelcome(String to, String firstName, String password, List<String> roles) {
        send(to, "Bienvenue sur Skyguide Navigation — vos identifiants",
                EmailTemplates.welcome(firstName, to, password, roles, loginUrl()), "welcome");
    }

    @Async
    public void sendAccountUpdated(String to, String firstName, List<String[]> changes, String newPassword, String updatedBy) {
        send(to, "Votre compte Skyguide Navigation a été mis à jour",
                EmailTemplates.accountUpdated(firstName, changes, newPassword, updatedBy, loginUrl()), "account-updated");
    }

    @Async
    public void sendResetCode(String to, String firstName, String code, int minutes) {
        send(to, "Code de réinitialisation : " + code,
                EmailTemplates.resetCode(firstName, code, minutes, resetUrl(to)), "reset-code");
    }

    @Async
    public void sendPasswordChanged(String to, String firstName) {
        send(to, "Votre mot de passe a été modifié",
                EmailTemplates.passwordChanged(firstName, loginUrl()), "password-changed");
    }

    @Async
    public void sendTwoFactorCode(String to, String firstName, String code, int minutes) {
        send(to, "Code de connexion : " + code, EmailTemplates.twoFactorCode(firstName, code, minutes), "2fa-code");
    }

    /** Anti-spam : au plus un e-mail par conversation et par destinataire toutes les 10 minutes */
    private final java.util.Map<String, Long> lastMessageMail = new java.util.concurrent.ConcurrentHashMap<>();

    @Async
    public void sendNewMessage(String to, String firstName, String senderName, String roomName,
                               boolean privateConversation, String preview, Long roomId) {
        String key = to + "#" + roomId;
        long now = System.currentTimeMillis();
        Long last = lastMessageMail.get(key);
        if (last != null && now - last < 10 * 60_000L) return;
        lastMessageMail.put(key, now);
        String text = preview == null ? "" : (preview.length() > 300 ? preview.substring(0, 300) + "…" : preview);
        send(to, (privateConversation ? "Message privé de " : "Nouveau message dans " + roomName + " — ") + senderName,
                EmailTemplates.newMessage(firstName, senderName, roomName, privateConversation, text, frontendUrl + "/dashboard/chat"),
                "new-message");
    }

    @Async
    public void sendAccountStatus(String to, String firstName, boolean active) {
        send(to, active ? "Votre compte a été réactivé" : "Votre compte a été désactivé",
                EmailTemplates.accountStatus(firstName, active, loginUrl()), "account-status");
    }

    // ══════════════════════════════════════════════════════
    // TRANSPORT
    // ══════════════════════════════════════════════════════

    private void send(String to, String subject, String html, String kind) {
        if (to == null || to.isBlank()) return;
        if (isSmtpConfigured()) {
            try {
                JavaMailSender sender = mailSenderProvider.getObject();
                MimeMessage message = sender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
                helper.setFrom(new InternetAddress(from, fromName, StandardCharsets.UTF_8.name()));
                helper.setTo(to);
                helper.setSubject(subject);
                helper.setText(htmlToText(html), html);
                sender.send(message);
                log.info("[MAIL] « {} » envoyé à {}", subject, to);
                return;
            } catch (Exception e) {
                log.error("[MAIL] Échec d'envoi à {} ({}) — copie enregistrée dans l'outbox", to, e.getMessage());
            }
        }
        saveToOutbox(to, subject, html, kind);
    }

    private void saveToOutbox(String to, String subject, String html, String kind) {
        try {
            Files.createDirectories(outbox);
            String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS"));
            String safeTo = to.replaceAll("[^A-Za-z0-9@._-]", "_");
            Path file = outbox.resolve(stamp + "_" + kind + "_" + safeTo + ".html");
            String withMeta = "<!-- To: " + to + " | Subject: " + subject + " -->\n" + html;
            Files.writeString(file, withMeta, StandardCharsets.UTF_8);
            log.info("[MAIL] (outbox) « {} » pour {} → {}", subject, to, file);
        } catch (Exception e) {
            log.error("[MAIL] Impossible d'écrire l'e-mail dans l'outbox : {}", e.getMessage());
        }
    }

    /** Version texte brut (meilleure délivrabilité / anti-spam) */
    private String htmlToText(String html) {
        return html.replaceAll("(?s)<style.*?</style>", "")
                .replaceAll("(?s)<div style=\"display:none.*?</div>", "")
                .replaceAll("<br\\s*/?>", "\n")
                .replaceAll("</(p|tr|h1|table)>", "\n")
                .replaceAll("<[^>]+>", "")
                .replace("&nbsp;", " ").replace("&rarr;", "→").replace("&#39;", "'").replace("&quot;", "\"").replace("&amp;", "&")
                .replaceAll("[ \\t]+", " ")
                .replaceAll("\\n\\s*\\n+", "\n\n")
                .trim();
    }
}
