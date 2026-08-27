package com.kasztelanic.carcare.management;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ProfileInfoContributor implements InfoContributor {

    private final Environment environment;

    @Override
    public void contribute(Info.Builder builder) {
        builder
            .withDetail("activeProfiles", environment.getActiveProfiles())
            .withDetail("display-ribbon-on-profiles", environment.getProperty("info.display-ribbon-on-profiles"));
    }
}
