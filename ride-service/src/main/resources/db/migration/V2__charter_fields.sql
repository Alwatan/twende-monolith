-- Charter and scheduled booking support
ALTER TABLE rides ADD COLUMN service_category VARCHAR(20) NOT NULL DEFAULT 'RIDE';
ALTER TABLE rides ADD COLUMN booking_type VARCHAR(20) NOT NULL DEFAULT 'ON_DEMAND';
ALTER TABLE rides ADD COLUMN scheduled_pickup_at TIMESTAMPTZ;
ALTER TABLE rides ADD COLUMN trip_direction VARCHAR(20);
ALTER TABLE rides ADD COLUMN quality_tier VARCHAR(20);
ALTER TABLE rides ADD COLUMN return_pickup_at TIMESTAMPTZ;
ALTER TABLE rides ADD COLUMN payment_timing VARCHAR(20) NOT NULL DEFAULT 'AT_END';

CREATE INDEX idx_rides_service_category ON rides(service_category, status);
CREATE INDEX idx_rides_scheduled ON rides(scheduled_pickup_at) WHERE scheduled_pickup_at IS NOT NULL;
