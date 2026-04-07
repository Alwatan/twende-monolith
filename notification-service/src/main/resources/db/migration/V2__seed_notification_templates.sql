-- V2__seed_notification_templates.sql
-- Seed notification templates in Swahili (sw-TZ) and English (en) for PUSH channel

INSERT INTO notification_templates (id, template_key, locale, channel, subject, body) VALUES

  -- Trip OTP (to rider when driver arrives)
  (gen_random_uuid(), 'trip.otp', 'sw-TZ', 'PUSH', null,
   'Dereva amefika. Mpe code hii: {otp}'),
  (gen_random_uuid(), 'trip.otp', 'en', 'PUSH', null,
   'Your driver has arrived. Share code {otp} to start your trip.'),

  -- Trip OTP resend
  (gen_random_uuid(), 'trip.otp.resend', 'sw-TZ', 'PUSH', null,
   'Code mpya ya safari: {otp}'),
  (gen_random_uuid(), 'trip.otp.resend', 'en', 'PUSH', null,
   'New trip code: {otp}'),

  -- Rejection counter nudge (to rider after 3 rejections)
  (gen_random_uuid(), 'ride.rejection.nudge', 'sw-TZ', 'PUSH', null,
   'Madereva {count} walipita. Ongeza bei ili kupata haraka zaidi.'),
  (gen_random_uuid(), 'ride.rejection.nudge', 'en', 'PUSH', null,
   '{count} drivers passed. Boost your fare to get picked up faster.'),

  -- New ride offer (to driver)
  (gen_random_uuid(), 'driver.offer', 'sw-TZ', 'PUSH', null,
   'Safari mpya: {distanceKm} km mbali. TSh {fare}. Kukubali?'),
  (gen_random_uuid(), 'driver.offer', 'en', 'PUSH', null,
   'New ride {distanceKm} km away — {currency} {fare}. Accept?'),

  -- Ride taken by another driver
  (gen_random_uuid(), 'driver.offer.taken', 'sw-TZ', 'PUSH', null,
   'Safari hiyo imechukuliwa na dereva mwingine.'),
  (gen_random_uuid(), 'driver.offer.taken', 'en', 'PUSH', null,
   'That ride was taken by another driver.'),

  -- Ride assigned to rider (driver on the way)
  (gen_random_uuid(), 'ride.assigned.rider', 'sw-TZ', 'PUSH', null,
   'Dereva {driverName} yuko njiani. Anakuja kwa {eta} dakika.'),
  (gen_random_uuid(), 'ride.assigned.rider', 'en', 'PUSH', null,
   '{driverName} is on the way. Arriving in {eta} minutes.'),

  -- Ride completed (to rider)
  (gen_random_uuid(), 'ride.completed.rider', 'sw-TZ', 'PUSH', null,
   'Safari imekamilika. Umelipa TSh {amount}. Asante kwa kutumia Twende!'),
  (gen_random_uuid(), 'ride.completed.rider', 'en', 'PUSH', null,
   'Trip completed. You paid {currency} {amount}. Thanks for riding with Twende!'),

  -- Subscription expired (to driver)
  (gen_random_uuid(), 'subscription.expired.driver', 'sw-TZ', 'PUSH', null,
   'Pakiti yako imeisha. Nunua pakiti mpya ili uendelee kupata abiria.'),
  (gen_random_uuid(), 'subscription.expired.driver', 'en', 'PUSH', null,
   'Your bundle has expired. Purchase a new bundle to continue receiving rides.'),

  -- Driver approved
  (gen_random_uuid(), 'driver.approved', 'sw-TZ', 'PUSH', null,
   'Hongera! Akaunti yako imeidhinishwa. Nunua pakiti na uanze kupata abiria.'),
  (gen_random_uuid(), 'driver.approved', 'en', 'PUSH', null,
   'Congratulations! Your account has been approved. Purchase a bundle and start receiving rides.'),

  -- Loyalty free ride offer earned (to rider)
  (gen_random_uuid(), 'loyalty.offer.earned', 'sw-TZ', 'PUSH', null,
   'Umepata safari ya bure ya {vehicleType}! Tumia ndani ya siku {validDays}.'),
  (gen_random_uuid(), 'loyalty.offer.earned', 'en', 'PUSH', null,
   'You earned a free {vehicleType} ride! Use it within {validDays} days.');
