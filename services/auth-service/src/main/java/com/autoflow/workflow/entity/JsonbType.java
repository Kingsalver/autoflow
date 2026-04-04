package com.autoflow.workflow.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.usertype.UserType;

import java.io.*;
import java.sql.*;

/**
 * Hibernate custom type for PostgreSQL JSONB columns.
 * Maps JSONB ↔ Jackson JsonNode.
 *
 * Usage: annotate the field with @Type(JsonbType.class)
 */
public class JsonbType implements UserType<JsonNode> {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public int getSqlType() {
        return Types.OTHER;
    }

    @Override
    public Class<JsonNode> returnedClass() {
        return JsonNode.class;
    }

    @Override
    public boolean equals(JsonNode x, JsonNode y) {
        if (x == null && y == null) return true;
        if (x == null || y == null) return false;
        return x.equals(y);
    }

    @Override
    public int hashCode(JsonNode x) {
        return x == null ? 0 : x.hashCode();
    }

    @Override
    public JsonNode nullSafeGet(ResultSet rs, int position,
                                SharedSessionContractImplementor session,
                                Object owner) throws SQLException {
        String value = rs.getString(position);
        if (value == null) return null;
        try {
            return MAPPER.readTree(value);
        } catch (IOException e) {
            throw new SQLException("Failed to deserialize JSONB value", e);
        }
    }

    @Override
    public void nullSafeSet(PreparedStatement st, JsonNode value, int index,
                            SharedSessionContractImplementor session) throws SQLException {
        if (value == null) {
            st.setNull(index, Types.OTHER);
        } else {
            try {
                st.setObject(index, MAPPER.writeValueAsString(value), Types.OTHER);
            } catch (IOException e) {
                throw new SQLException("Failed to serialize JSONB value", e);
            }
        }
    }

    @Override
    public JsonNode deepCopy(JsonNode value) {
        return value == null ? null : value.deepCopy();
    }

    @Override
    public boolean isMutable() {
        return true;
    }

    @Override
    public Serializable disassemble(JsonNode value) {
        if (value == null) return null;
        try {
            return MAPPER.writeValueAsString(value);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public JsonNode assemble(Serializable cached, Object owner) {
        if (cached == null) return null;
        try {
            return MAPPER.readTree((String) cached);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}