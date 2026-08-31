package io.github.hackermanme.flashapi.entity;

import io.github.hackermanme.flashapi.annotation.FlashEntity;
import io.github.hackermanme.flashapi.annotation.FlashSecured;
import jakarta.persistence.*;

@Entity
@FlashEntity
@FlashSecured(
        roles = "authenticated",
        ownerField = "ownerId",
        ownerAdminRoles = {"ADMIN"}
)
public class OwnedItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    private String ownerId;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getOwnerId() { return ownerId; }
    public void setOwnerId(String ownerId) { this.ownerId = ownerId; }
}
