package tz.co.twende.notification.provider;

public interface SmsProvider {

    String getId();

    void sendSms(String phoneNumber, String message);

    boolean supportsCountry(String countryCode);
}
