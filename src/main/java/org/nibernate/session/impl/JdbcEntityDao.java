package org.nibernate.session.impl;

import lombok.SneakyThrows;
import org.nibernate.session.EntityKey;
import org.nibernate.session.laziList.LazyList;
import org.nibernate.session.util.JdbcUtil;

import javax.sql.DataSource;
import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

public class JdbcEntityDao {

    private final String SELECT_BY_ID = "SELECT * FROM %s where %s = ?";
    private final String UPDATE_BY_ID = "UPDATE %s set %s where %s = %s";
    private final DataSource dataSource;
    private final Map<EntityKey<?>, Object> firstLevelCache = new HashMap<>();
    private final Map<EntityKey<?>, Object[]> entitySnapshots = new HashMap<>();
    private final Queue<String> sqlQueue = new ConcurrentLinkedQueue<>();

    private boolean open = true;

    public JdbcEntityDao(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @SneakyThrows
    public <T> T findById(Class<T> entityClass, String identifierName, Object searchParameter) {
        verifySessionIsOpen();
        var entityKey = EntityKey.of(entityClass, searchParameter);
        if (firstLevelCache.containsKey(entityKey)) {
            return entityClass.cast(firstLevelCache.get(entityKey));
        }
        var list = findAllById(entityClass, identifierName, searchParameter);
        if (list.size() != 1) {
            throw new RuntimeException("Result List should have have size of 1");
        }
        return list.get(0);
    }

    @SneakyThrows
    public <T> List<T> findAllById(Class<T> entityClass, String identifierName, Object searchParameter) {
        verifySessionIsOpen();
        List<T> list = new ArrayList<>();
        try (Connection connection = dataSource.getConnection()) {
            String tableName = JdbcUtil.getTableName(entityClass);
            String formattedSqlStatement = prepareSqlStatement(tableName, identifierName);
            PreparedStatement preparedStatement = connection.prepareStatement(formattedSqlStatement);
            preparedStatement.setObject(1, searchParameter);
            System.out.println("SQL:" + preparedStatement);
            preparedStatement.executeQuery();
            ResultSet resultSet = preparedStatement.getResultSet();
            while (resultSet.next()) {
                list.add(buildEntityFormResultSet(entityClass, resultSet));
            }

        }
        return list;
    }

    @SneakyThrows
    private <T> T buildEntityFormResultSet(Class<T> entityClass, ResultSet resultSet) {
        var declaredFields = entityClass.getDeclaredFields();
        var newInstance = entityClass.getConstructor().newInstance();
        var snapshots = new ArrayList<>();
        for (Field field : declaredFields) {
            field.setAccessible(true);
            if (JdbcUtil.isCollectionField(field)) {
                String parentIdentifier = JdbcUtil.getIdentifierFieldName(entityClass);
                Object parentSearchParameter = resultSet.getObject(parentIdentifier);
                Class<T> childClass = JdbcUtil.getCollectionClass(field);
                String childParentIdentifier = JdbcUtil.getParentOneToManyIdentifier(entityClass, childClass);
                var lazyList = new LazyList<>(() -> findAllById(childClass, childParentIdentifier, parentSearchParameter));
                field.set(newInstance, lazyList);
            } else if (JdbcUtil.isParentField(field)) {
                Class<?> parentClass = field.getType();
                String parentIdentifier = JdbcUtil.getIdentifierFieldName(parentClass);
                String fieldName = JdbcUtil.resolveColumnName(field);
                Object parentSearchParameter = resultSet.getObject(fieldName);
                var parentInstance = findById(parentClass, parentIdentifier, parentSearchParameter);
                field.set(newInstance, parentInstance);
                snapshots.add(parentInstance);
            } else {
                var sqlColumnName = JdbcUtil.resolveColumnName(field);
                var resultSetValue = resultSet.getObject(sqlColumnName);
                field.set(newInstance, resultSetValue);
                snapshots.add(resultSetValue);
            }

        }
        var entityKey = EntityKey.valueOf(newInstance);
        entitySnapshots.put(entityKey, snapshots.toArray());
        System.out.println(snapshots);
        return cache(newInstance);
    }

    private <T> T cache(T newInstance) {
        var entityKey = EntityKey.valueOf(newInstance);
        var cachedValue = firstLevelCache.get(entityKey);
        if (cachedValue != null) {
            return (T) cachedValue;
        }
        firstLevelCache.put(entityKey, newInstance);
        return newInstance;

    }

    private String prepareSqlStatement(String tableName, String searchParameter) {
        return String.format(SELECT_BY_ID, tableName, searchParameter);
    }

    public void verifySessionIsOpen() {
        if (!isOpen()) {
            throw new RuntimeException("Session has been closed already");
        }
    }

    public boolean isOpen() {
        return open;
    }

    public void close() {
        this.open = false;
        compareSnapshot();
        this.firstLevelCache.clear();
        flushUpdates();
    }

    @SneakyThrows
    private void compareSnapshot() {
        for (Map.Entry<EntityKey<?>, Object[]> entityKeyEntry : entitySnapshots.entrySet()) {
            var entityKey = entityKeyEntry.getKey();
            var snapshots = entityKeyEntry.getValue();

            var cachedInstance = firstLevelCache.get(entityKey);
            var declaredFields = Arrays.stream(cachedInstance.getClass().getDeclaredFields())
                    .filter(field -> !JdbcUtil.isCollectionField(field))
                    .toList();
            var parametersToUpdate = new ArrayList<String>();
            for (int i = 0; i < declaredFields.size(); i++) {
                var field = declaredFields.get(i);
                field.setAccessible(true);
                var cachedFieldValue = field.get(cachedInstance);
                var snapshotValue = snapshots[i];
                if (!cachedFieldValue.equals(snapshotValue)) {
                    String columnName = JdbcUtil.resolveColumnName(field);
                    String parameter = String.format("%s='%s'", columnName, cachedFieldValue);
                    parametersToUpdate.add(parameter);
                }
            }
            String tableName = JdbcUtil.getTableName(cachedInstance.getClass());
            String parameters = String.join(",", parametersToUpdate);
            String identifierName = JdbcUtil.getIdentifierFieldName(cachedInstance.getClass());
            Object identifierValue = JdbcUtil.getIdentifierValue(cachedInstance);
            String formattedUpdateStatement = prepareUpdateStatement(tableName, parameters, identifierName, identifierValue);
            if (!parametersToUpdate.isEmpty()){
                sqlQueue.add(formattedUpdateStatement);
            }
        }
    }

    @SneakyThrows
    public void flushUpdates() {
        int idx = 0;
        while (!sqlQueue.isEmpty()) {

            var updateStatement = sqlQueue.poll();
            idx += 1;
            try (var connection = dataSource.getConnection()) {
                try (var statement = connection.prepareStatement(updateStatement)) {
                    statement.addBatch();
                    if (idx % 50 == 0 || sqlQueue.isEmpty()) {
                        statement.executeBatch();
                    }
                }
            }
        }
    }

    private String prepareUpdateStatement(String tableName, String parameters, String identifierName, Object identifier) {
        return String.format(UPDATE_BY_ID, tableName, parameters, identifierName, identifier);
    }

}
