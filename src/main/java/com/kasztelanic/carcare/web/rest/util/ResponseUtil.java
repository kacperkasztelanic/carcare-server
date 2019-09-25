package com.kasztelanic.carcare.web.rest.util;

import java.util.List;

import org.springframework.http.ResponseEntity;

import lombok.experimental.UtilityClass;

public class ResponseUtil {

    public static <T> ResponseEntity<List<T>> createListOkResponse(List<T> list) {
        return ResponseEntity.ok()//
                .header("X-Total-Count", String.valueOf(list.size()))//
                .body(list);
    }

    private ResponseUtil() {
    }
}
