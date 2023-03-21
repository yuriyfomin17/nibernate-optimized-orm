package org.nibernate;

import org.nibernate.session.Session;
import org.nibernate.session.SessionFactory;
import org.nibernate.session.entities.Person;
import org.nibernate.session.impl.SessionFactoryImpl;
import org.postgresql.ds.PGSimpleDataSource;

import javax.sql.DataSource;

public class Main {
    public static void main(String[] args) {
        SessionFactory sessionFactory = new SessionFactoryImpl(initializeDataSource());
        Session session = sessionFactory.createSession();
        Person person = session.find(Person.class, 1L);
        person.setFirstName("Yuriy");
        person.setLastName("Fomin");
        System.out.println(person);
        session.close();
    }


    private static DataSource initializeDataSource() {
        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        dataSource.setURL("jdbc:postgresql://localhost:5432/postgres");
        return dataSource;
    }
}