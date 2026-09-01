package io.github.hackermanme.flashapi.entity;

import io.github.hackermanme.flashapi.annotation.FlashEntity;
import io.github.hackermanme.flashapi.annotation.FlashSecured;
import jakarta.persistence.*;

@Entity
@FlashEntity(currentUserField = "author")
@FlashSecured(roles = "authenticated", ownerField = "author", ownerAdminRoles = {"ADMIN"})
public class BlogPost {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;

    @ManyToOne
    private Author author;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public Author getAuthor() { return author; }
    public void setAuthor(Author author) { this.author = author; }
}
