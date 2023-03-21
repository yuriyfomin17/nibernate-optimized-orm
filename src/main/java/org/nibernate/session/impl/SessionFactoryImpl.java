package org.nibernate.session.impl;

import org.nibernate.session.Session;
import org.nibernate.session.SessionFactory;

import javax.sql.DataSource;

public class SessionFactoryImpl  implements SessionFactory {
    private final DataSource dataSource;
    public SessionFactoryImpl(DataSource dataSource){
        this.dataSource = dataSource;
    }
    @Override
    public Session createSession() {
        return new SessionImpl(dataSource);
    }
}
