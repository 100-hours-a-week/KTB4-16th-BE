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
    cache_date DATE NOT NULL,
    forecast_at DATETIME NOT NULL,
    temperature DECIMAL(3, 1) NOT NULL,
    weather_condition ENUM(
        'CLEAR',
        'CLOUDY',
        'OVERCAST',
        'RAIN',
        'SNOW',
        'RAIN_SNOW',
        'SHOWER'
    ) NOT NULL,
    base_at DATETIME NOT NULL,
    fetched_at DATETIME NOT NULL,

    CONSTRAINT pk_weather_grid_forecasts PRIMARY KEY (weather_grid_forecast_id),
    CONSTRAINT fk_weather_grid_forecasts_grid
        FOREIGN KEY (weather_grid_id) REFERENCES weather_grids(weather_grid_id),
    CONSTRAINT uk_weather_grid_forecasts_daily_slot
        UNIQUE (weather_grid_id, cache_date, forecast_at),
    INDEX idx_weather_grid_forecasts_lookup
        (weather_grid_id, cache_date, forecast_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
