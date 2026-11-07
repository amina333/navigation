package com.air.navigation.mail;

import org.springframework.web.util.HtmlUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Gabarits HTML des e-mails de la plateforme.
 * Mise en page en tableaux + CSS inline : compatible Gmail, Outlook, Apple Mail et mobile.
 */
public final class EmailTemplates {

    private EmailTemplates() {}

    private static final String NAVY = "#0a1f44";
    private static final String BLUE = "#0066cc";
    private static final String CYAN = "#38bdf8";
    private static final String TEXT = "#0f172a";
    private static final String MUTED = "#64748b";
    private static final String BORDER = "#e2e8f0";
    private static final String FONT = "'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif";
    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("dd/MM/yyyy 'à' HH:mm");

    // ══════════════════════════════════════════════════════
    // E-MAILS
    // ══════════════════════════════════════════════════════

    /** Bienvenue + identifiants de connexion */
    public static String welcome(String firstName, String email, String password, List<String> roles, String loginUrl) {
        String body =
                greeting(firstName)
                + paragraph("Votre compte sur la <strong>Plateforme intégrée d'assistance à la navigation aérienne</strong> vient d'être créé par un administrateur. Bienvenue à bord !")
                + sectionTitle("Vos identifiants")
                + credentialBox(Map.of(), email, password)
                + keyValues(List.of(
                        new String[]{"Rôle(s)", String.join(", ", roles)},
                        new String[]{"Adresse de connexion", "<a href=\"" + esc(loginUrl) + "\" style=\"color:" + BLUE + ";text-decoration:none;\">" + esc(loginUrl) + "</a>"}), true)
                + button("Se connecter", loginUrl)
                + notice("🔒", "Pour votre sécurité, changez ce mot de passe dès votre première connexion (Profil → Sécurité). Ne le communiquez jamais, même à un administrateur.")
                + paragraph("<span style=\"color:" + MUTED + ";\">Vous avez accès à la messagerie interne, au suivi des vols et à l'assistant SkyAI selon les droits de votre rôle.</span>");
        return layout("Bienvenue " + firstName + " — vos identifiants de connexion",
                "Bienvenue à bord ✈", "Votre compte est prêt", body);
    }

    /** Notification de modification du compte */
    public static String accountUpdated(String firstName, List<String[]> changes, String newPassword, String updatedBy, String loginUrl) {
        StringBuilder b = new StringBuilder();
        b.append(greeting(firstName));
        b.append(paragraph("Les informations de votre compte ont été mises à jour" + (updatedBy != null ? " par <strong>" + esc(updatedBy) + "</strong>" : "") + " le " + LocalDateTime.now().format(DT) + "."));
        if (!changes.isEmpty()) {
            b.append(sectionTitle("Modifications"));
            b.append(changesTable(changes));
        }
        if (newPassword != null) {
            b.append(sectionTitle("Nouveau mot de passe"));
            b.append(credentialBox(Map.of(), null, newPassword));
            b.append(notice("🔒", "Un nouveau mot de passe vous a été attribué. Changez-le dès votre prochaine connexion."));
        }
        b.append(button("Accéder à mon compte", loginUrl));
        b.append(notice("⚠️", "Vous n'êtes pas à l'origine de cette demande et ces changements vous semblent anormaux ? Contactez immédiatement votre administrateur."));
        return layout("Votre compte a été mis à jour", "Compte mis à jour", "Récapitulatif des modifications", b.toString());
    }

    /** Code de réinitialisation du mot de passe */
    public static String resetCode(String firstName, String code, int minutes, String resetUrl) {
        String body =
                greeting(firstName)
                + paragraph("Nous avons reçu une demande de réinitialisation du mot de passe de votre compte. Saisissez le code ci-dessous dans l'application :")
                + codeBox(code, minutes)
                + button("Réinitialiser mon mot de passe", resetUrl)
                + notice("🛡️", "Ce code est personnel et à usage unique. Il expire dans <strong>" + minutes + " minutes</strong> et est bloqué après 5 essais incorrects.")
                + paragraph("<span style=\"color:" + MUTED + ";\">Vous n'avez rien demandé ? Ignorez simplement cet e-mail : votre mot de passe actuel reste inchangé.</span>");
        return layout("Votre code de réinitialisation : " + code, "Réinitialisation", "Code de vérification", body);
    }

    /** Confirmation de changement de mot de passe */
    public static String passwordChanged(String firstName, String loginUrl) {
        String body =
                greeting(firstName)
                + paragraph("Le mot de passe de votre compte a été modifié avec succès le " + LocalDateTime.now().format(DT) + ".")
                + successBadge("Mot de passe mis à jour")
                + button("Se connecter", loginUrl)
                + notice("⚠️", "Si vous n'êtes pas à l'origine de ce changement, contactez immédiatement votre administrateur : votre compte pourrait être compromis.");
        return layout("Votre mot de passe a été modifié", "Sécurité du compte", "Mot de passe modifié", body);
    }

    /** Activation / désactivation du compte */
    public static String accountStatus(String firstName, boolean active, String loginUrl) {
        String body = greeting(firstName)
                + (active
                    ? paragraph("Votre compte a été <strong>réactivé</strong>. Vous pouvez de nouveau vous connecter à la plateforme.")
                      + successBadge("Compte actif") + button("Se connecter", loginUrl)
                    : paragraph("Votre compte a été <strong>désactivé</strong> par un administrateur. Vous ne pouvez plus vous connecter pour le moment.")
                      + notice("ℹ️", "Pour toute question, rapprochez-vous de votre responsable ou de l'administrateur de la plateforme."));
        return layout(active ? "Votre compte est réactivé" : "Votre compte est désactivé",
                "Statut du compte", active ? "Compte réactivé" : "Compte désactivé", body);
    }

    /** Code de connexion (double authentification) */
    public static String twoFactorCode(String firstName, String code, int minutes) {
        String body = greeting(firstName)
                + paragraph("Une connexion à votre compte vient d'être demandée avec votre mot de passe. Pour la confirmer, saisissez ce code de vérification :")
                + codeBox(code, minutes)
                + notice("🛡️", "Vous n'êtes pas à l'origine de cette connexion ? Votre mot de passe est peut-être compromis : changez-le immédiatement et prévenez votre administrateur.");
        return layout("Code de connexion : " + code, "Double authentification", "Confirmez votre connexion", body);
    }

    /** Nouveau message reçu (conversation privée ou groupe) */
    public static String newMessage(String firstName, String senderName, String roomName, boolean privateConversation,
                                    String preview, String chatUrl) {
        String context = privateConversation
                ? "<strong>" + esc(senderName) + "</strong> vous a envoyé un message privé."
                : "<strong>" + esc(senderName) + "</strong> a écrit dans le groupe <strong>" + esc(roomName) + "</strong>.";
        String quote = "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"margin:0 0 8px;\"><tr>"
                + "<td style=\"width:4px;background:" + BLUE + ";border-radius:4px;\"></td>"
                + "<td style=\"padding:12px 16px;background:#f8fafc;border-radius:0 12px 12px 0;color:" + TEXT + ";font-size:15px;line-height:1.6;\">"
                + esc(preview) + "</td></tr></table>";
        String body = greeting(firstName) + paragraph(context) + quote
                + button("Répondre", chatUrl)
                + paragraph("<span style=\"color:" + MUTED + ";font-size:13px;\">Vous recevez cet e-mail car les notifications par e-mail sont activées. Désactivez-les dans Paramètres → Notifications.</span>");
        return layout("Nouveau message de " + senderName, "Messagerie interne", "Nouveau message", body);
    }

    // ══════════════════════════════════════════════════════
    // MISE EN PAGE
    // ══════════════════════════════════════════════════════

    private static String layout(String preheader, String eyebrow, String title, String body) {
        return """
            <!DOCTYPE html>
            <html lang="fr">
            <head>
              <meta charset="UTF-8">
              <meta name="viewport" content="width=device-width, initial-scale=1.0">
              <meta name="color-scheme" content="light">
              <title>%s</title>
            </head>
            <body style="margin:0;padding:0;background:#eef2f7;font-family:%s;">
              <div style="display:none;max-height:0;overflow:hidden;opacity:0;">%s</div>
              <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="background:#eef2f7;padding:32px 12px;">
                <tr><td align="center">
                  <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="max-width:600px;">

                    <!-- En-tête -->
                    <tr><td style="background:%s;background-image:linear-gradient(135deg,%s 0%%,#123a7a 60%%,%s 100%%);border-radius:20px 20px 0 0;padding:32px 36px 28px;">
                      <table role="presentation" width="100%%" cellpadding="0" cellspacing="0"><tr>
                        <td style="vertical-align:middle;">
                          <span style="display:inline-block;width:40px;height:40px;line-height:40px;text-align:center;border-radius:12px;background:rgba(255,255,255,0.16);color:#ffffff;font-size:20px;">✈</span>
                          <span style="display:inline-block;vertical-align:middle;margin-left:10px;color:#ffffff;font-size:15px;font-weight:700;letter-spacing:0.02em;">Skyguide Navigation</span>
                        </td>
                        <td align="right" style="vertical-align:middle;color:rgba(255,255,255,0.75);font-size:12px;font-weight:600;letter-spacing:0.08em;">OACA · TUNISIE</td>
                      </tr></table>
                      <p style="margin:28px 0 6px;color:%s;font-size:12px;font-weight:700;letter-spacing:0.14em;text-transform:uppercase;">%s</p>
                      <h1 style="margin:0;color:#ffffff;font-size:26px;line-height:1.25;font-weight:700;">%s</h1>
                    </td></tr>

                    <!-- Corps -->
                    <tr><td style="background:#ffffff;padding:32px 36px 12px;border-left:1px solid %s;border-right:1px solid %s;">
                      %s
                    </td></tr>

                    <!-- Pied -->
                    <tr><td style="background:#ffffff;border-radius:0 0 20px 20px;padding:4px 36px 28px;border:1px solid %s;border-top:none;">
                      <hr style="border:none;border-top:1px solid %s;margin:8px 0 18px;">
                      <p style="margin:0;color:%s;font-size:12px;line-height:1.6;">
                        Cet e-mail a été envoyé automatiquement par la plateforme Skyguide Navigation — merci de ne pas y répondre.<br>
                        Office de l'Aviation Civile et des Aéroports · Tunisie
                      </p>
                    </td></tr>

                    <tr><td align="center" style="padding:18px 0 0;color:#94a3b8;font-size:11px;">© %d Skyguide Navigation · Plateforme de support opérationnel</td></tr>
                  </table>
                </td></tr>
              </table>
            </body>
            </html>
            """.formatted(esc(preheader), FONT, esc(preheader),
                NAVY, NAVY, BLUE, CYAN, esc(eyebrow), esc(title),
                BORDER, BORDER, body, BORDER, BORDER, MUTED, LocalDateTime.now().getYear());
    }

    private static String greeting(String firstName) {
        return "<p style=\"margin:0 0 14px;color:" + TEXT + ";font-size:17px;font-weight:600;\">Bonjour " + esc(capitalize(firstName)) + ",</p>";
    }

    private static String paragraph(String html) {
        return "<p style=\"margin:0 0 18px;color:#334155;font-size:15px;line-height:1.65;\">" + html + "</p>";
    }

    private static String sectionTitle(String t) {
        return "<p style=\"margin:26px 0 10px;color:" + MUTED + ";font-size:12px;font-weight:700;letter-spacing:0.1em;text-transform:uppercase;\">" + esc(t) + "</p>";
    }

    /** Bloc identifiants : fond bleu nuit, mot de passe en police monospace */
    private static String credentialBox(Map<String, String> ignored, String email, String password) {
        StringBuilder sb = new StringBuilder();
        sb.append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"background:")
          .append(NAVY).append(";background-image:linear-gradient(135deg,").append(NAVY).append(",#143a73);border-radius:14px;margin:0 0 16px;\"><tr><td style=\"padding:20px 22px;\">");
        if (email != null) {
            sb.append("<p style=\"margin:0 0 4px;color:#93c5fd;font-size:11px;font-weight:700;letter-spacing:0.1em;text-transform:uppercase;\">Identifiant</p>")
              .append("<p style=\"margin:0 0 16px;color:#ffffff;font-size:16px;font-weight:600;word-break:break-all;\">").append(esc(email)).append("</p>");
        }
        sb.append("<p style=\"margin:0 0 6px;color:#93c5fd;font-size:11px;font-weight:700;letter-spacing:0.1em;text-transform:uppercase;\">Mot de passe temporaire</p>")
          .append("<p style=\"margin:0;display:inline-block;padding:8px 14px;border-radius:10px;background:rgba(255,255,255,0.1);border:1px dashed rgba(147,197,253,0.6);color:#ffffff;font-family:'SFMono-Regular',Consolas,'Courier New',monospace;font-size:18px;letter-spacing:0.06em;\">")
          .append(esc(password)).append("</p>");
        sb.append("</td></tr></table>");
        return sb.toString();
    }

    private static String keyValues(List<String[]> rows, boolean valuesAreHtml) {
        StringBuilder sb = new StringBuilder("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"margin:0 0 8px;border:1px solid " + BORDER + ";border-radius:12px;\">");
        for (int i = 0; i < rows.size(); i++) {
            String[] r = rows.get(i);
            String border = i < rows.size() - 1 ? "border-bottom:1px solid " + BORDER + ";" : "";
            sb.append("<tr><td style=\"padding:12px 16px;").append(border).append("color:").append(MUTED).append(";font-size:13px;width:40%;\">").append(esc(r[0])).append("</td>")
              .append("<td style=\"padding:12px 16px;").append(border).append("color:").append(TEXT).append(";font-size:14px;font-weight:600;\">")
              .append(valuesAreHtml ? r[1] : esc(r[1])).append("</td></tr>");
        }
        return sb.append("</table>").toString();
    }

    /** Tableau « champ : avant → après » */
    private static String changesTable(List<String[]> changes) {
        StringBuilder sb = new StringBuilder("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"margin:0 0 8px;border:1px solid " + BORDER + ";border-radius:12px;\">");
        for (int i = 0; i < changes.size(); i++) {
            String[] c = changes.get(i);
            String border = i < changes.size() - 1 ? "border-bottom:1px solid " + BORDER + ";" : "";
            sb.append("<tr><td style=\"padding:12px 16px;").append(border).append("\">")
              .append("<p style=\"margin:0 0 4px;color:").append(MUTED).append(";font-size:12px;font-weight:600;\">").append(esc(c[0])).append("</p>")
              .append("<p style=\"margin:0;font-size:14px;\">")
              .append("<span style=\"color:#94a3b8;text-decoration:line-through;\">").append(esc(blankDash(c[1]))).append("</span>")
              .append("<span style=\"color:").append(BLUE).append(";font-weight:700;\">&nbsp;&nbsp;→&nbsp;&nbsp;</span>")
              .append("<span style=\"color:").append(TEXT).append(";font-weight:600;\">").append(esc(blankDash(c[2]))).append("</span></p>")
              .append("</td></tr>");
        }
        return sb.append("</table>").toString();
    }

    /** Code à 6 chiffres, chaque chiffre dans sa case */
    private static String codeBox(String code, int minutes) {
        StringBuilder digits = new StringBuilder();
        for (char ch : code.toCharArray()) {
            digits.append("<td style=\"padding:0 4px;\"><span style=\"display:inline-block;width:44px;height:56px;line-height:56px;text-align:center;border-radius:12px;background:#ffffff;border:2px solid #bfdbfe;color:")
                  .append(NAVY).append(";font-family:'SFMono-Regular',Consolas,'Courier New',monospace;font-size:28px;font-weight:700;\">")
                  .append(ch).append("</span></td>");
        }
        return "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"background:#eff6ff;border-radius:16px;margin:4px 0 20px;\"><tr><td align=\"center\" style=\"padding:24px 12px 18px;\">"
                + "<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\"><tr>" + digits + "</tr></table>"
                + "<p style=\"margin:14px 0 0;color:" + MUTED + ";font-size:12px;\">⏱ Valable " + minutes + " minutes</p>"
                + "</td></tr></table>";
    }

    private static String button(String label, String url) {
        return "<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" style=\"margin:22px 0 22px;\"><tr><td style=\"border-radius:12px;background:" + BLUE
                + ";background-image:linear-gradient(135deg,#1677e5," + BLUE + ");box-shadow:0 8px 20px -8px rgba(0,102,204,0.6);\">"
                + "<a href=\"" + esc(url) + "\" style=\"display:inline-block;padding:14px 28px;color:#ffffff;font-size:15px;font-weight:700;text-decoration:none;border-radius:12px;\">"
                + esc(label) + " &nbsp;→</a></td></tr></table>";
    }

    private static String notice(String icon, String html) {
        return "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"margin:0 0 18px;background:#fffbeb;border:1px solid #fde68a;border-radius:12px;\"><tr>"
                + "<td style=\"padding:14px 16px;color:#92400e;font-size:13px;line-height:1.55;\">" + icon + "&nbsp; " + html + "</td></tr></table>";
    }

    private static String successBadge(String text) {
        return "<p style=\"margin:4px 0 8px;\"><span style=\"display:inline-block;padding:8px 14px;border-radius:999px;background:#ecfdf5;border:1px solid #a7f3d0;color:#047857;font-size:13px;font-weight:700;\">✓ "
                + esc(text) + "</span></p>";
    }

    private static String esc(String s) {
        return s == null ? "" : HtmlUtils.htmlEscape(s, "UTF-8");
    }

    private static String blankDash(String s) {
        return s == null || s.isBlank() ? "—" : s;
    }

    private static String capitalize(String s) {
        if (s == null || s.isBlank()) return "";
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
