package org.nibernate.session;

import org.nibernate.session.util.JdbcUtil;

public record EntityKey<T>(Class<T> entityClass, Object identifierValue) {
    public static <T> EntityKey<T> of(Class<T> entityType, Object identifierValue){
        return new EntityKey<>(entityType, identifierValue);
    }

    public static <T> EntityKey<T> valueOf(T entity){
        Class<T> entityType = (Class<T>) entity.getClass();
        var identifierValue = JdbcUtil.getIdentifierValue(entity);
        return new EntityKey<>(entityType, identifierValue);

    }
}
