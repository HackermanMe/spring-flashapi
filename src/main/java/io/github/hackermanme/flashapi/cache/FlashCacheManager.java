package io.github.hackermanme.flashapi.cache;

import io.github.hackermanme.flashapi.registry.EntityMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

public final class FlashCacheManager {

    private static final Logger log = LoggerFactory.getLogger(FlashCacheManager.class);
    private static final String CACHE_PREFIX = "flashapi:";

    private final CacheManager cacheManager;

    public FlashCacheManager(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    public Object getFromCache(EntityMetadata meta, String key) {
        if (!meta.cacheEnabled() || cacheManager == null) return null;
        Cache cache = cacheManager.getCache(cacheName(meta));
        if (cache == null) return null;
        Cache.ValueWrapper wrapper = cache.get(key);
        return wrapper != null ? wrapper.get() : null;
    }

    public void putInCache(EntityMetadata meta, String key, Object value) {
        if (!meta.cacheEnabled() || cacheManager == null) return;
        Cache cache = cacheManager.getCache(cacheName(meta));
        if (cache != null) {
            cache.put(key, value);
        }
    }

    public void evict(EntityMetadata meta) {
        if (!meta.cacheEnabled() || cacheManager == null) return;
        Cache cache = cacheManager.getCache(cacheName(meta));
        if (cache != null) {
            cache.clear();
            log.debug("FlashAPI: cache evicted for {}", meta.entityName());
        }
    }

    public void evictEntry(EntityMetadata meta, String key) {
        if (!meta.cacheEnabled() || cacheManager == null) return;
        Cache cache = cacheManager.getCache(cacheName(meta));
        if (cache != null) {
            cache.evict(key);
        }
    }

    public boolean isAvailable() {
        return cacheManager != null;
    }

    private String cacheName(EntityMetadata meta) {
        return CACHE_PREFIX + meta.path();
    }
}
