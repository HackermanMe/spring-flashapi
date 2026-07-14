package io.github.hackermanme.flashapi.entity;

import io.github.hackermanme.flashapi.annotation.FlashEntity;
import io.github.hackermanme.flashapi.annotation.FlashSecured;
import jakarta.persistence.*;

@Entity
@FlashEntity
@FlashSecured(roles = "ADMIN", read = "USER", create = "EDITOR", delete = "ADMIN")
public class SecuredItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    private String description;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
