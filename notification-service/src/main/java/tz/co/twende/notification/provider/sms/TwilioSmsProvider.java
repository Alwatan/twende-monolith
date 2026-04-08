package tz.co.twende.notification.provider.sms;

import java.util.Set;
import org.springframework.stereotype.Component;
import tz.co.twende.notification.provider.SmsProvider;

@Component
public class TwilioSmsProvider implements SmsProvider {

    private static final Set<String> SUPPORTED_COUNTRIES = Set.of("KE");

    @Override
    public String getId() {
        return "twilio";
    }

    @Override
    public void sendSms(String phoneNumber, String message) {
        throw new UnsupportedOperationException("Twilio SMS provider not yet implemented");
    }

    @Override
    public boolean supportsCountry(String countryCode) {
        return SUPPORTED_COUNTRIES.contains(countryCode);
    }
}
