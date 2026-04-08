package tz.co.twende.common.entity;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.hibernate.annotations.IdGeneratorType;

/**
 * Custom ID generator annotation that produces monotonically increasing ULIDs stored as UUIDs.
 *
 * <p>Replaces the deprecated {@code @GenericGenerator} approach. Use this on the {@code @Id} field
 * instead of {@code @GeneratedValue} + {@code @GenericGenerator}.
 *
 * <p>If the entity already has a non-null ID (e.g. set from a Kafka event like
 * UserRegisteredEvent), the existing ID is preserved and no new ULID is generated.
 *
 * <p>Usage:
 *
 * <pre>{@code
 * @Id
 * @UlidId
 * @Column(updatable = false, nullable = false)
 * private UUID id;
 * }</pre>
 */
@IdGeneratorType(UlidGenerator.class)
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD})
public @interface UlidId {}
