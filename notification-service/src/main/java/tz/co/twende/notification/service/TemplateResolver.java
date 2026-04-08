package tz.co.twende.notification.service;

import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tz.co.twende.notification.entity.NotificationTemplate;
import tz.co.twende.notification.repository.NotificationTemplateRepository;

@Slf4j
@Service
@RequiredArgsConstructor
public class TemplateResolver {

    private final NotificationTemplateRepository templateRepository;

    public String resolveTemplate(String templateKey, String locale, Map<String, String> params) {
        NotificationTemplate template = findTemplate(templateKey, locale);
        return replacePlaceholders(template.getBody(), params);
    }

    public NotificationTemplate findTemplate(String templateKey, String locale) {
        // 1. Try exact locale (e.g. "sw-TZ")
        Optional<NotificationTemplate> template =
                templateRepository.findByKeyAndLocale(templateKey, locale);
        if (template.isPresent()) {
            return template.get();
        }

        // 2. Fall back to language only (e.g. "sw")
        if (locale != null && locale.contains("-")) {
            String languageOnly = locale.split("-")[0];
            template = templateRepository.findByKeyAndLocale(templateKey, languageOnly);
            if (template.isPresent()) {
                return template.get();
            }
        }

        // 3. Fall back to English
        template = templateRepository.findByKeyAndLocale(templateKey, "en");
        if (template.isPresent()) {
            return template.get();
        }

        log.warn("No template found for key={}, locale={}", templateKey, locale);
        return createFallbackTemplate(templateKey);
    }

    public String replacePlaceholders(String body, Map<String, String> params) {
        if (params == null || params.isEmpty()) {
            return body;
        }
        String result = body;
        for (Map.Entry<String, String> entry : params.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return result;
    }

    private NotificationTemplate createFallbackTemplate(String templateKey) {
        NotificationTemplate fallback = new NotificationTemplate();
        fallback.setTemplateKey(templateKey);
        fallback.setLocale("en");
        fallback.setChannel("PUSH");
        fallback.setBody("Notification: " + templateKey);
        fallback.setCountryCode("TZ");
        return fallback;
    }
}
