package com.sacco.mvp.service;

import org.springframework.web.util.HtmlUtils;

import java.util.regex.Pattern;

/** Shared presentation for outgoing messages; legacy queued text is branded at delivery. */
final class NotificationMessageTemplate {
    private static final Pattern LEGACY_BRAND = Pattern.compile("(?i)\\bLoan\\s+Application\\s+Portal\\b");
    private static final Pattern OTP_LINE = Pattern.compile("Your OTP code is: ([0-9]{6})");
    private static final Pattern WEB_LINK = Pattern.compile("https?://[^\\s<>\"']+");

    private NotificationMessageTemplate() {
    }

    static String text(String value) {
        return value == null ? "" : LEGACY_BRAND.matcher(value).replaceAll("Microfinance");
    }

    static String html(String subject, String message) {
        StringBuilder body = new StringBuilder();
        for (String line : text(message).split("\\R")) {
            if (line.isBlank()) {
                continue;
            }
            var code = OTP_LINE.matcher(line.trim());
            if (code.matches()) {
                body.append("<div style=\"margin:20px 0;padding:20px;background:#f1f5f9;border:1px solid #d5dbdb;text-align:center;\">")
                    .append("<p style=\"margin:0 0 8px;font-size:14px;color:#475569;\">Your OTP code</p>")
                    .append("<p style=\"margin:0;font-size:32px;font-weight:700;letter-spacing:6px;color:#232f3e;\">")
                    .append(code.group(1)).append("</p></div>");
            } else {
                body.append("<p style=\"margin:0 0 16px;font-size:16px;line-height:1.6;overflow-wrap:anywhere;word-break:break-word;\">")
                    .append(linkedText(line)).append("</p>");
            }
        }
        return """
            <!doctype html>
            <html>
            <head><meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1"><title>%s</title></head>
            <body style="margin:0;padding:0;background:#eef2f5;color:#16191f;font-family:Arial,Helvetica,sans-serif;">
              <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="background:#eef2f5;">
                <tr><td align="center" style="padding:24px 12px;">
                  <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="max-width:600px;background:#ffffff;border:1px solid #d5dbdb;">
                    <tr><td style="padding:20px 24px;background:#232f3e;color:#ffffff;font-size:22px;font-weight:700;">Microfinance</td></tr>
                    <tr><td style="padding:24px;">
                      <h1 style="margin:0 0 20px;font-size:22px;line-height:1.4;overflow-wrap:anywhere;">%s</h1>
                      %s
                    </td></tr>
                  </table>
                </td></tr>
              </table>
            </body>
            </html>
            """.formatted(escape(text(subject)), escape(text(subject)), body);
    }

    private static String linkedText(String line) {
        var links = WEB_LINK.matcher(line);
        StringBuilder result = new StringBuilder();
        int position = 0;
        while (links.find()) {
            result.append(escape(line.substring(position, links.start())));
            String url = escape(links.group());
            result.append("<a style=\"color:#0972d3;text-decoration:underline;overflow-wrap:anywhere;word-break:break-word;\" href=\"")
                .append(url).append("\">").append(url).append("</a>");
            position = links.end();
        }
        return result.append(escape(line.substring(position))).toString();
    }

    private static String escape(String value) {
        return HtmlUtils.htmlEscape(value, "UTF-8");
    }
}
