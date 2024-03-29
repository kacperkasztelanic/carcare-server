package com.kasztelanic.carcare.config;

import org.ehcache.config.builders.CacheConfigurationBuilder;
import org.ehcache.config.builders.ExpiryPolicyBuilder;
import org.ehcache.config.builders.ResourcePoolsBuilder;
import org.ehcache.jsr107.Eh107Configuration;
import org.hibernate.cache.jcache.ConfigSettings;
import org.springframework.boot.autoconfigure.cache.JCacheManagerCustomizer;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tech.jhipster.config.JHipsterProperties;

import java.time.Duration;

@Configuration
@EnableCaching
public class CacheConfiguration {

    private final javax.cache.configuration.Configuration<Object, Object> jcacheConfiguration;

    public CacheConfiguration(JHipsterProperties jHipsterProperties) {
        JHipsterProperties.Cache.Ehcache ehcache = jHipsterProperties.getCache().getEhcache();

        jcacheConfiguration = Eh107Configuration.fromEhcacheCacheConfiguration(
            CacheConfigurationBuilder.newCacheConfigurationBuilder(Object.class, Object.class,
                    ResourcePoolsBuilder.heap(ehcache.getMaxEntries()))
                .withExpiry(ExpiryPolicyBuilder.timeToLiveExpiration(Duration.ofSeconds(ehcache.getTimeToLiveSeconds())))
                .build());
    }

    @Bean
    public HibernatePropertiesCustomizer hibernatePropertiesCustomizer(javax.cache.CacheManager cacheManager) {
        return hibernateProperties -> hibernateProperties.put(ConfigSettings.CACHE_MANAGER, cacheManager);
    }

    @Bean
    public JCacheManagerCustomizer cacheManagerCustomizer() {
        return cm -> {
            createCache(cm, com.kasztelanic.carcare.repository.UserRepository.USERS_BY_LOGIN_CACHE);
            createCache(cm, com.kasztelanic.carcare.repository.UserRepository.USERS_BY_EMAIL_CACHE);
            createCache(cm, com.kasztelanic.carcare.domain.User.class.getName());
            createCache(cm, com.kasztelanic.carcare.domain.Authority.class.getName());
            createCache(cm, com.kasztelanic.carcare.domain.User.class.getName() + ".authorities");
            createCache(cm, com.kasztelanic.carcare.domain.Vehicle.class.getName());
            createCache(cm, com.kasztelanic.carcare.domain.VehicleDetails.class.getName());
            createCache(cm, com.kasztelanic.carcare.domain.FuelType.class.getName());
            createCache(cm, com.kasztelanic.carcare.domain.InsuranceType.class.getName());
            createCache(cm, com.kasztelanic.carcare.domain.Insurance.class.getName());
            createCache(cm, com.kasztelanic.carcare.domain.Inspection.class.getName());
            createCache(cm, com.kasztelanic.carcare.domain.Repair.class.getName());
            createCache(cm, com.kasztelanic.carcare.domain.RoutineService.class.getName());
            createCache(cm, com.kasztelanic.carcare.domain.Refuel.class.getName());
            createCache(cm, com.kasztelanic.carcare.domain.ReminderAdvance.class.getName());
        };
    }


    private void createCache(javax.cache.CacheManager cm, String cacheName) {
        javax.cache.Cache<Object, Object> cache = cm.getCache(cacheName);
        if (cache != null) {
            cm.destroyCache(cacheName);
        }
        cm.createCache(cacheName, jcacheConfiguration);
    }
}
