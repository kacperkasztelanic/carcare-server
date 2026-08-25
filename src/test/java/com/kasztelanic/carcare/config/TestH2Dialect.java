package com.kasztelanic.carcare.config;

import org.hibernate.dialect.H2Dialect;

/**
 * Test-only H2 dialect. Production entities declare {@code length = 65535} on their long-text
 * columns, and Liquibase's own {@code TEXT} mapping creates genuine H2 CLOB columns for them
 * (see {@code changelog/20190922082653_changelog.xml}). Stock {@link H2Dialect} reports a
 * {@code getMaxVarcharLength()} of 1,048,576, so Hibernate 6 maps those fields to VARCHAR and
 * schema validation fails against the real CLOB columns. Lowering the boundary below 65,535
 * promotes the mapping to CLOB without touching production entities, Liquibase, or
 * {@code hibernate.hbm2ddl.auto: validate}.
 */
public class TestH2Dialect extends H2Dialect {

    @Override
    public int getMaxVarcharLength() {
        return 65534;
    }
}
