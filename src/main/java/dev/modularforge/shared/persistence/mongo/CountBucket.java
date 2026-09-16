package dev.modularforge.shared.persistence.mongo;

import org.springframework.data.mongodb.core.mapping.Field;

public record CountBucket(@Field("_id") String key, long count) { }
