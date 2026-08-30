package io.github.hackermanme.flashapi.hooks;

import io.github.hackermanme.flashapi.entity.HookTestEntity;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class TestHookListener {
    public final List<String> events = new ArrayList<>();
    public final List<String> requestHeaders = new ArrayList<>();

    public void clear() {
        events.clear();
        requestHeaders.clear();
    }

    @FlashBeforeCreate
    public void beforeCreate(Object entity, HttpServletRequest request) {
        if (!(entity instanceof HookTestEntity)) return;
        HookTestEntity e = (HookTestEntity) entity;
        events.add("beforeCreate:" + e.getName());
        captureHeaders(request);
    }

    @FlashAfterCreate
    public void afterCreate(Object entity, HttpServletRequest request) {
        if (!(entity instanceof HookTestEntity)) return;
        HookTestEntity e = (HookTestEntity) entity;
        events.add("afterCreate:" + e.getName());
    }

    @FlashBeforeUpdate
    public void beforeUpdate(Object entity, HttpServletRequest request) {
        if (!(entity instanceof HookTestEntity)) return;
        HookTestEntity e = (HookTestEntity) entity;
        events.add("beforeUpdate:" + e.getName());
        captureHeaders(request);
    }

    @FlashAfterUpdate
    public void afterUpdate(Object entity, HttpServletRequest request) {
        if (!(entity instanceof HookTestEntity)) return;
        HookTestEntity e = (HookTestEntity) entity;
        events.add("afterUpdate:" + e.getName());
    }

    @FlashBeforeDelete
    public void beforeDelete(Object entity, HttpServletRequest request) {
        if (!(entity instanceof HookTestEntity)) return;
        HookTestEntity e = (HookTestEntity) entity;
        events.add("beforeDelete:" + e.getName());
        captureHeaders(request);
    }

    @FlashAfterDelete
    public void afterDelete(Object entity, HttpServletRequest request) {
        if (!(entity instanceof HookTestEntity)) return;
        HookTestEntity e = (HookTestEntity) entity;
        events.add("afterDelete:" + e.getName());
    }

    private void captureHeaders(HttpServletRequest request) {
        if (request != null) {
            String testHeader = request.getHeader("X-Test-Header");
            if (testHeader != null) {
                requestHeaders.add("X-Test-Header:" + testHeader);
            }
        }
    }
}
