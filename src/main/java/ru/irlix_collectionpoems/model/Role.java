    package ru.irlix_collectionpoems.model;

    import lombok.Getter;
    import lombok.Setter;

    import jakarta.persistence.*;

    @Setter
    @Getter
    @Entity
    @Table(name = "roles")
    public class Role {

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private long id;
        @Column(length = 60)
        private String name;
    }
