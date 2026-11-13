-- Type-specific incident fields (MOTOR: licensePlate, vehicleModel; PROPERTY: propertyAddress, damageCategory).
-- JSONB keeps the claims table stable as lines of business add fields; the domain validates the keys per type.
ALTER TABLE claims ADD COLUMN incident_details JSONB NOT NULL DEFAULT '{}'::jsonb;
