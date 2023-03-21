package org.nibernate.session.impl;

import org.nibernate.session.Session;
import org.nibernate.session.util.JdbcUtil;

import javax.sql.DataSource;

public class SessionImpl implements Session {

    private JdbcEntityDao jdbcEntityDao;

    public SessionImpl(DataSource dataSource) {
        this.jdbcEntityDao = new JdbcEntityDao(dataSource);
    }

    @Override
    public <T> T find(Class<T> entityClass, Object id) {
        var identifierName = JdbcUtil.getIdentifierFieldName(entityClass);
        return this.jdbcEntityDao.findById(entityClass, identifierName, id);
    }

    @Override
    public void close() {
        this.jdbcEntityDao.close();

    }
}
