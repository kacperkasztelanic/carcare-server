package com.kasztelanic.carcare.web.rest.util;

import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Optional;

public class ResponseUtil {

    public static <T> ResponseEntity<List<T>> createListOkResponse(List<T> list) {
        return ResponseEntity.ok()//
                .header("X-Total-Count", String.valueOf(list.size()))//
                .body(list);
    }

    private ResponseUtil() {
    }
}
