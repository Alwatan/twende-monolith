-- V4: Cargo notification templates (Swahili + English)

INSERT INTO notification_templates (template_key, locale, channel, subject, body, country_code) VALUES
  ('cargo.confirmed', 'sw-TZ', 'PUSH', null,
   'Usafiri wako wa mizigo umethibitishwa kwa {date}. Gari: {vehicleType}.', 'TZ'),
  ('cargo.confirmed', 'en', 'PUSH', null,
   'Your cargo booking is confirmed for {date}. Vehicle: {vehicleType}.', 'TZ'),
  ('cargo.completed', 'sw-TZ', 'PUSH', null,
   'Mizigo yako imefika salama. Umelipa TSh {amount}. Asante!', 'TZ'),
  ('cargo.completed', 'en', 'PUSH', null,
   'Your cargo has been delivered. You paid {currency} {amount}. Thank you!', 'TZ'),
  ('cargo.driver.accepted', 'sw-TZ', 'PUSH', null,
   'Dereva amekubali kubeba mizigo yako. Jina: {driverName}.', 'TZ'),
  ('cargo.driver.accepted', 'en', 'PUSH', null,
   'A driver has accepted your cargo booking. Driver: {driverName}.', 'TZ');
