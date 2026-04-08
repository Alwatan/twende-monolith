package tz.co.twende.notification.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tz.co.twende.notification.entity.NotificationTemplate;
import tz.co.twende.notification.repository.NotificationTemplateRepository;

@ExtendWith(MockitoExtension.class)
class TemplateResolverTest {

    @Mock private NotificationTemplateRepository templateRepository;

    @InjectMocks private TemplateResolver templateResolver;

    @Test
    void givenSwahiliLocale_whenResolveTemplate_thenSwahiliBodyReturned() {
        NotificationTemplate template = createTemplate("sw-TZ", "Dereva {driverName} yuko njiani.");
        when(templateRepository.findByKeyAndLocale("ride.assigned.rider", "sw-TZ"))
                .thenReturn(Optional.of(template));

        String result =
                templateResolver.resolveTemplate(
                        "ride.assigned.rider", "sw-TZ", Map.of("driverName", "John"));

        assertThat(result).isEqualTo("Dereva John yuko njiani.");
    }

    @Test
    void givenEnglishLocale_whenResolveTemplate_thenEnglishBodyReturned() {
        NotificationTemplate template = createTemplate("en", "{driverName} is on the way.");
        when(templateRepository.findByKeyAndLocale("ride.assigned.rider", "en"))
                .thenReturn(Optional.of(template));

        String result =
                templateResolver.resolveTemplate(
                        "ride.assigned.rider", "en", Map.of("driverName", "John"));

        assertThat(result).isEqualTo("John is on the way.");
    }

    @Test
    void givenUnknownLocale_whenResolveTemplate_thenFallsBackToEnglish() {
        when(templateRepository.findByKeyAndLocale("ride.assigned.rider", "fr-FR"))
                .thenReturn(Optional.empty());
        when(templateRepository.findByKeyAndLocale("ride.assigned.rider", "fr"))
                .thenReturn(Optional.empty());
        NotificationTemplate template = createTemplate("en", "{driverName} is on the way.");
        when(templateRepository.findByKeyAndLocale("ride.assigned.rider", "en"))
                .thenReturn(Optional.of(template));

        String result =
                templateResolver.resolveTemplate(
                        "ride.assigned.rider", "fr-FR", Map.of("driverName", "John"));

        assertThat(result).isEqualTo("John is on the way.");
    }

    @Test
    void givenLanguageOnlyMatch_whenResolveTemplate_thenLanguageTemplateReturned() {
        when(templateRepository.findByKeyAndLocale("ride.assigned.rider", "sw-KE"))
                .thenReturn(Optional.empty());
        NotificationTemplate template = createTemplate("sw", "Dereva {driverName} yuko njiani.");
        when(templateRepository.findByKeyAndLocale("ride.assigned.rider", "sw"))
                .thenReturn(Optional.of(template));

        String result =
                templateResolver.resolveTemplate(
                        "ride.assigned.rider", "sw-KE", Map.of("driverName", "John"));

        assertThat(result).isEqualTo("Dereva John yuko njiani.");
    }

    @Test
    void givenNoTemplateFound_whenResolveTemplate_thenFallbackReturned() {
        when(templateRepository.findByKeyAndLocale("unknown.key", "en"))
                .thenReturn(Optional.empty());

        String result = templateResolver.resolveTemplate("unknown.key", "en", Map.of());

        assertThat(result).contains("unknown.key");
    }

    @Test
    void givenMultiplePlaceholders_whenResolveTemplate_thenAllReplaced() {
        NotificationTemplate template =
                createTemplate("en", "Driver {driverName} arriving in {eta} minutes.");
        when(templateRepository.findByKeyAndLocale("ride.assigned.rider", "en"))
                .thenReturn(Optional.of(template));

        String result =
                templateResolver.resolveTemplate(
                        "ride.assigned.rider", "en", Map.of("driverName", "John", "eta", "5"));

        assertThat(result).isEqualTo("Driver John arriving in 5 minutes.");
    }

    @Test
    void givenNullParams_whenReplacePlaceholders_thenBodyUnchanged() {
        String result = templateResolver.replacePlaceholders("Hello {name}", null);
        assertThat(result).isEqualTo("Hello {name}");
    }

    @Test
    void givenEmptyParams_whenReplacePlaceholders_thenBodyUnchanged() {
        String result = templateResolver.replacePlaceholders("Hello {name}", Map.of());
        assertThat(result).isEqualTo("Hello {name}");
    }

    private NotificationTemplate createTemplate(String locale, String body) {
        NotificationTemplate template = new NotificationTemplate();
        template.setLocale(locale);
        template.setBody(body);
        template.setChannel("PUSH");
        template.setCountryCode("TZ");
        return template;
    }
}
