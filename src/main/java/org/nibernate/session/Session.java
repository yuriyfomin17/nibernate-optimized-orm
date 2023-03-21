package org.nibernate.session;

public interface Session {

    <T> T find(Class<T> entityClass, Object id);

    void close();
}
