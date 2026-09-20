package com.ktb4.team16.mulo.weather.service;

import com.ktb4.team16.mulo.weather.domain.GridCoordinate;
import org.springframework.stereotype.Component;

@Component
public class GridCoordinateConverter {
    private static final double EARTH_RADIUS = 6371.00877;
    private static final double GRID_KILOMETERS = 5.0;
    private static final double STANDARD_LATITUDE_1 = 30.0;
    private static final double STANDARD_LATITUDE_2 = 60.0;
    private static final double ORIGIN_LONGITUDE = 126.0;
    private static final double ORIGIN_LATITUDE = 38.0;
    private static final double ORIGIN_X = 43.0;
    private static final double ORIGIN_Y = 136.0;

    public GridCoordinate convert(double latitude, double longitude) {
        double radian = Math.PI / 180.0;
        double re = EARTH_RADIUS / GRID_KILOMETERS;
        double slat1 = STANDARD_LATITUDE_1 * radian;
        double slat2 = STANDARD_LATITUDE_2 * radian;
        double olon = ORIGIN_LONGITUDE * radian;
        double olat = ORIGIN_LATITUDE * radian;

        double sn = Math.tan(Math.PI * 0.25 + slat2 * 0.5)
                / Math.tan(Math.PI * 0.25 + slat1 * 0.5);
        sn = Math.log(Math.cos(slat1) / Math.cos(slat2)) / Math.log(sn);
        double sf = Math.pow(Math.tan(Math.PI * 0.25 + slat1 * 0.5), sn)
                * Math.cos(slat1) / sn;
        double ro = re * sf
                / Math.pow(Math.tan(Math.PI * 0.25 + olat * 0.5), sn);
        double ra = re * sf / Math.pow(
                Math.tan(Math.PI * 0.25 + latitude * radian * 0.5), sn);
        double theta = longitude * radian - olon;
        if (theta > Math.PI) {
            theta -= 2.0 * Math.PI;
        }
        if (theta < -Math.PI) {
            theta += 2.0 * Math.PI;
        }
        theta *= sn;

        short x = (short) Math.floor(ra * Math.sin(theta) + ORIGIN_X + 0.5);
        short y = (short) Math.floor(ro - ra * Math.cos(theta) + ORIGIN_Y + 0.5);
        return new GridCoordinate(x, y);
    }
}
