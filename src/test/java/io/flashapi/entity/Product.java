package io.flashapi.entity;

import io.flashapi.annotation.FlashEntity;
import io.flashapi.annotation.FlashAudit;
import io.flashapi.annotation.FlashReadOnly;
import io.flashapi.annotation.FlashWriteOnly;
import io.flashapi.annotation.FlashHidden;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@FlashEntity(softDelete = true)
@FlashAudit(trackFields = true)
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @FlashReadOnly
    private Long id;

    private String name;

    private BigDecimal price;

    private Integer stock;

    @FlashWriteOnly
    private String internalCode;

    @FlashHidden
    private String secretToken;

    @FlashReadOnly
    private Instant createdAt;

    private Instant deletedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public Integer getStock() { return stock; }
    public void setStock(Integer stock) { this.stock = stock; }
    public String getInternalCode() { return internalCode; }
    public void setInternalCode(String internalCode) { this.internalCode = internalCode; }
    public String getSecretToken() { return secretToken; }
    public void setSecretToken(String secretToken) { this.secretToken = secretToken; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getDeletedAt() { return deletedAt; }
    public void setDeletedAt(Instant deletedAt) { this.deletedAt = deletedAt; }
}
