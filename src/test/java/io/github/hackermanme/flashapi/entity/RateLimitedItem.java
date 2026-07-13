package io.github.hackermanme.flashapi.entity;

import io.github.hackermanme.flashapi.annotation.FlashEntity;
import io.github.hackermanme.flashapi.annotation.FlashReadOnly;
import jakarta.persistence.*;

@Entity
@FlashEntity(rateLimit = true, rateLimitRequests = 3, rateLimitWindow = 60)
public class RateLimitedItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @FlashReadOnly
    private Long id;

    private String name;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}
