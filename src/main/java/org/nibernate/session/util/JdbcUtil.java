package org.nibernate.session.util;

import lombok.SneakyThrows;
import org.nibernate.annotations.*;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Optional;

public class JdbcUtil {


    public static <T>  Class<T> getCollectionClass(Field field) {
        ParameterizedType type = (ParameterizedType) field.getGenericType();
        Type[] typeArgs = type.getActualTypeArguments();
        return (Class<T>) typeArgs[0];
    }

    public static String getParentOneToManyIdentifier(Class<?> parentEntityClass, Class<?> childEntity) {
        var childParentField = Arrays.stream(childEntity.getDeclaredFields())
                .filter(field -> field.getType().equals(parentEntityClass))
                .findAny().orElseThrow(() -> new RuntimeException("Unable to find parent field in child entity"));
        return Optional.ofNullable(childParentField.getAnnotation(Column.class))
                .map(Column::name)
                .orElse(childParentField.getName());
    }

    public static String resolveColumnName(Field field) {
        return Optional.ofNullable(field.getAnnotation(Column.class))
                .map(Column::name)
                .orElse(field.getName());
    }

    public static String getTableName(Class<?> entityClass) {
        return Optional.ofNullable(entityClass.getAnnotation(Table.class))
                .map(Table::name)
                .orElse(entityClass.getSimpleName());
    }

    public static boolean isCollectionField(Field field) {
        return field.isAnnotationPresent(OneToMany.class);
    }
    public static boolean isParentField(Field field){
        return field.isAnnotationPresent(ManyToOne.class);
    }

    public static String getIdentifierFieldName(Class<?> entityClass) {
        return Arrays.stream(entityClass.getDeclaredFields())
                .filter(field -> field.isAnnotationPresent(Id.class))
                .findAny()
                .map(Field::getName)
                .orElseThrow(() -> new RuntimeException("Every class should be annotated with ID"));
    }


    @SneakyThrows
    public static <T> Object getIdentifierValue(T entity) {
        Field identifierField = Arrays.stream(entity.getClass().getDeclaredFields())
                .filter(field -> field.isAnnotationPresent(Id.class))
                .findAny()
                .orElseThrow(() -> new RuntimeException("Every class should be annotated with ID"));
        identifierField.setAccessible(true);
        return identifierField.get(entity);
    }
}
