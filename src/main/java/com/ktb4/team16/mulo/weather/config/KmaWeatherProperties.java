package com.ktb4.team16.mulo.weather.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "kma")
public class KmaWeatherProperties {
    private String baseUrl;
    private String serviceKey;

    public KmaWeatherProperties() {
    }

    public KmaWeatherProperties(String baseUrl, String serviceKey) {
        this.baseUrl = baseUrl;
        this.serviceKey = serviceKey;
    }

    public String baseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String serviceKey() {
        return serviceKey;
    }

    public void setServiceKey(String serviceKey) {
        this.serviceKey = serviceKey;
    }
}
