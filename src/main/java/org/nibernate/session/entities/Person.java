package org.nibernate.session.entities;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.nibernate.annotations.Column;
import org.nibernate.annotations.Id;
import org.nibernate.annotations.OneToMany;
import org.nibernate.annotations.Table;

import java.util.ArrayList;
import java.util.List;

@NoArgsConstructor
@Table(name = "persons")
@ToString(exclude = "notes")
@Getter
@Setter
public class Person {
    @Id
    private Long id;

    @Column(name = "first_name")
    private String firstName;
    @Column(name = "last_name")
    private String lastName;


    @OneToMany
    private List<Note> notes = new ArrayList<>();
}
