package com.ktb4.team16.mulo.upload.config;

import com.ktb4.team16.mulo.upload.conversion.HeicImageConverter;
import com.ktb4.team16.mulo.upload.conversion.NativeHeicImageConverter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(HeicConversionProperties.class)
public class HeicConversionConfiguration {
    @Bean
    HeicImageConverter heicImageConverter(HeicConversionProperties properties) {
        return new NativeHeicImageConverter(properties);
    }
}
