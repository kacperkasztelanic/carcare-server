package com.kasztelanic.carcare.service.dto;

import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;

@Value(staticConstructor = "of")
@EqualsAndHashCode(of = { "name" })
@ToString(of = { "name" }, includeFieldNames = false)
public class Report {

     String name;
     byte[] bytes;
}
