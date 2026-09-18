CREATE TABLE weather (
    weather_id BIGINT NOT NULL AUTO_INCREMENT,
    city_name VARCHAR(20) NOT NULL,
    weather_condition VARCHAR(20) NOT NULL,
    temperature DECIMAL(3, 1) NOT NULL,
    fetched_at DATETIME NOT NULL,

    CONSTRAINT pk_weather PRIMARY KEY (weather_id),
    CONSTRAINT uk_weather_city_name UNIQUE (city_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;