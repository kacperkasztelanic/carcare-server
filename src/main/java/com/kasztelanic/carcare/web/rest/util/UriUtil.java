package com.kasztelanic.carcare.web.rest.util;

import com.kasztelanic.carcare.web.rest.errors.UnparseableUriException;

import java.net.URI;
import java.net.URISyntaxException;

public class UriUtil {

    public static URI buildURI(String uri) {
        try {
            return new URI(uri);
        } catch (URISyntaxException e) {
            throw new UnparseableUriException(e);
        }
    }

    private UriUtil() {
    }
}
