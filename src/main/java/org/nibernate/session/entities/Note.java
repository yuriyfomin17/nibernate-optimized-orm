package org.nibernate.session.entities;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.nibernate.annotations.Column;
import org.nibernate.annotations.Id;
import org.nibernate.annotations.ManyToOne;
import org.nibernate.annotations.Table;

@Table(name = "notes")
@ToString
@Getter
@Setter
public class Note {
    @Id
    private Long id;
    @Column(name = "body")
    private String body;

    @ManyToOne
    @Column(name = "person_id")
    private Person person;
}
