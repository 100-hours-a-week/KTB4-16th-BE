ALTER TABLE places
    DROP COLUMN external_place_id,
    DROP COLUMN place_name,
    CHANGE COLUMN dong_name legal_dong_name VARCHAR(100) NULL DEFAULT NULL,
    ADD COLUMN legal_dong_code VARCHAR(20) NULL DEFAULT NULL AFTER place_id,
    ADD INDEX idx_places_legal_dong_code (legal_dong_code),
    ADD CONSTRAINT uk_places_coordinates UNIQUE (latitude, longitude),
    ADD CONSTRAINT chk_places_legal_dong_pair CHECK (
        (legal_dong_code IS NULL AND legal_dong_name IS NULL)
        OR
        (legal_dong_code IS NOT NULL AND legal_dong_name IS NOT NULL)
    );

DROP TABLE weather;

CREATE TABLE weather_grids (
    weather_grid_id BIGINT NOT NULL AUTO_INCREMENT,
    grid_x SMALLINT NOT NULL,
    grid_y SMALLINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_weather_grids PRIMARY KEY (weather_grid_id),
    CONSTRAINT uk_weather_grids_xy UNIQUE (grid_x, grid_y)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE weather_grid_forecasts (
    weather_grid_forecast_id BIGINT NOT NULL AUTO_INCREMENT,
    weather_grid_id BIGINT NOT NULL,
    forecast_at DATETIME NOT NULL,
    temperature DECIMAL(3, 1) NOT NULL,
    weather_condition ENUM(
        'CLEAR', 'CLOUDY', 'OVERCAST', 'RAIN', 'SNOW', 'RAIN_SNOW', 'SHOWER'
    ) NOT NULL,
    base_at DATETIME NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NULL DEFAULT NULL,

    CONSTRAINT pk_weather_grid_forecasts PRIMARY KEY (weather_grid_forecast_id),
    CONSTRAINT fk_weather_grid_forecasts_grid
        FOREIGN KEY (weather_grid_id) REFERENCES weather_grids(weather_grid_id),
    CONSTRAINT uk_weather_grid_forecasts_grid_forecast_at
        UNIQUE (weather_grid_id, forecast_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
