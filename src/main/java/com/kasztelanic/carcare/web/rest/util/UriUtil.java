package com.kasztelanic.carcare.web.rest.util;

import com.kasztelanic.carcare.web.rest.errors.UnparseableUriException;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.net.URI;
import java.net.URISyntaxException;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class UriUtil {

    public static URI buildURI(String uri) {
        try {
            return new URI(uri);
        } catch (URISyntaxException e) {
            throw new UnparseableUriException(e);
        }
    }
}
