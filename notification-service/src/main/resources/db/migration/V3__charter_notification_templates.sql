-- Charter/Booking notification templates

INSERT INTO notification_templates (template_key, locale, channel, subject, body, country_code) VALUES
  -- Booking confirmed (customer)
  ('booking.confirmed', 'sw-TZ', 'PUSH', null,
   'Nafasi yako imethibitishwa kwa {vehicleType} tarehe {date}. Nauli: TSh {fare}.', 'TZ'),
  ('booking.confirmed', 'en', 'PUSH', null,
   'Your booking for {vehicleType} on {date} is confirmed. Fare: {currency} {fare}.', 'TZ'),

  -- Booking reminder (customer + driver, 24h before)
  ('booking.reminder', 'sw-TZ', 'PUSH', null,
   'Ukumbusho: Safari yako ya {vehicleType} ni kesho saa {time}. Jiandae!', 'TZ'),
  ('booking.reminder', 'en', 'PUSH', null,
   'Reminder: Your {vehicleType} trip is tomorrow at {time}. Be ready!', 'TZ'),

  -- Driver accepted booking (customer)
  ('booking.driver.accepted', 'sw-TZ', 'PUSH', null,
   'Dereva amekubali nafasi yako ya {vehicleType}. Atakuja tarehe {date}.', 'TZ'),
  ('booking.driver.accepted', 'en', 'PUSH', null,
   'A driver has accepted your {vehicleType} booking for {date}.', 'TZ'),

  -- Charter completed (customer)
  ('charter.completed', 'sw-TZ', 'PUSH', null,
   'Safari ya charter imekamilika. Umelipa TSh {fare}. Asante kwa kutumia Twende!', 'TZ'),
  ('charter.completed', 'en', 'PUSH', null,
   'Charter trip completed. You paid {currency} {fare}. Thanks for using Twende!', 'TZ');
