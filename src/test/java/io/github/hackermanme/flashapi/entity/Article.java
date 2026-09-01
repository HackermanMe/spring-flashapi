package io.github.hackermanme.flashapi.entity;

import io.github.hackermanme.flashapi.annotation.FlashCounter;
import io.github.hackermanme.flashapi.annotation.FlashEntity;
import jakarta.persistence.*;

@Entity
@FlashEntity
public class Article {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;

    @FlashCounter(source = ArticleLike.class, relation = "article")
    private int likeCount;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public int getLikeCount() { return likeCount; }
    public void setLikeCount(int likeCount) { this.likeCount = likeCount; }
}
