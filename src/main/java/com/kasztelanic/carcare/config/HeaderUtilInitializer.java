package com.kasztelanic.carcare.config;

import com.kasztelanic.carcare.web.rest.util.HeaderUtil;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * Seeds {@link HeaderUtil}'s static application name from {@code spring.application.name},
 * since {@link HeaderUtil} is a static utility with no Spring injection point of its own.
 */
@Configuration
public class HeaderUtilInitializer implements InitializingBean {

    @Value("${spring.application.name}")
    private String applicationName;

    @Override
    public void afterPropertiesSet() {
        HeaderUtil.setApplicationName(applicationName);
    }
}
