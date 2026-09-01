package io.github.hackermanme.flashapi.entity;

import io.github.hackermanme.flashapi.annotation.FlashEntity;
import jakarta.persistence.*;

@Entity
@FlashEntity
public class ArticleLike {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    private Article article;

    private String userId;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Article getArticle() { return article; }
    public void setArticle(Article article) { this.article = article; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
}
