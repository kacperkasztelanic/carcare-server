package com.kasztelanic.carcare.web.rest.util;

import java.net.URI;
import java.net.URISyntaxException;

import com.kasztelanic.carcare.web.rest.errors.UnparseableURIException;

import lombok.experimental.UtilityClass;

@UtilityClass
public class URIUtil {

    public static URI buildURI(String uri) {
        try {
            return new URI(uri);
        } catch (URISyntaxException e) {
            throw new UnparseableURIException(e);
        }
    }
}
