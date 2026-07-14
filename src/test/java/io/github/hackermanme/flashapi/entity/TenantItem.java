package io.github.hackermanme.flashapi.entity;

import io.github.hackermanme.flashapi.annotation.FlashEntity;
import io.github.hackermanme.flashapi.annotation.FlashMultiTenant;
import jakarta.persistence.*;

@Entity
@FlashEntity
@FlashMultiTenant(field = "tenantId")
public class TenantItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    private String tenantId;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
}
