.PHONY: build run test up down logs

build:
	./mvnw clean package -DskipTests

run:
	./mvnw spring-boot:run

test:
	./mvnw test

test-class:
	./mvnw test -Dtest=$(CLASS)

up:
	docker compose up -d postgres redis zipkin minio

down:
	docker compose down

logs:
	docker compose logs -f twende

db-shell:
	docker compose exec postgres psql -U twende -d twende

redis-cli:
	docker compose exec redis redis-cli

format:
	./mvnw spotless:apply
