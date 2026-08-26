package com.kasztelanic.carcare.web.rest.util;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import static org.assertj.core.api.Assertions.assertThat;

class HeaderUtilTest {

    @Test
    void createsCreationAlertWithClientHeaderPrefix() {
        assertAlertHeaders(HeaderUtil.createEntityCreationAlert("vehicle", "42"), "carcareApp.vehicle.created", "42");
    }

    @Test
    void createsUpdateAlertWithClientHeaderPrefix() {
        assertAlertHeaders(HeaderUtil.createEntityUpdateAlert("vehicle", "42"), "carcareApp.vehicle.updated", "42");
    }

    @Test
    void createsDeletionAlertWithClientHeaderPrefix() {
        assertAlertHeaders(HeaderUtil.createEntityDeletionAlert("vehicle", "42"), "carcareApp.vehicle.deleted", "42");
    }

    @Test
    void createsFailureAlertWithClientHeaderPrefix() {
        HttpHeaders headers = HeaderUtil.createFailureAlert("vehicle", "idexists", "Vehicle already exists");

        assertThat(headers.keySet()).containsExactlyInAnyOrder("X-carcareApp-error", "X-carcareApp-params");
        assertThat(headers.getFirst("X-carcareApp-error")).isEqualTo("error.idexists");
        assertThat(headers.getFirst("X-carcareApp-params")).isEqualTo("vehicle");
    }

    private static void assertAlertHeaders(HttpHeaders headers, String alertValue, String paramValue) {
        assertThat(headers.keySet()).containsExactlyInAnyOrder("X-carcareApp-alert", "X-carcareApp-params");
        assertThat(headers.getFirst("X-carcareApp-alert")).isEqualTo(alertValue);
        assertThat(headers.getFirst("X-carcareApp-params")).isEqualTo(paramValue);
    }
}
