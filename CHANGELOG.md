# Changelog

## [3.1.2](https://github.com/HackermanMe/spring-flashapi/compare/v3.1.1...v3.1.2) (2026-09-05)


### Bug Fixes

* resolve ClassLoader mismatch with Spring Boot DevTools ([39d74e7](https://github.com/HackermanMe/spring-flashapi/commit/39d74e787de16b9af6e409efc51c8644843ad754))

## [3.1.1](https://github.com/HackermanMe/spring-flashapi/compare/v3.1.0...v3.1.1) (2026-09-04)


### Bug Fixes

* auto-configure HookRegistry bean instead of relying on component scan ([3eb5d46](https://github.com/HackermanMe/spring-flashapi/commit/3eb5d4647d8bfe97b904279db37c106bca48afb4))
* improve error handling for currentUserField injection and add write-protection tests ([ce52717](https://github.com/HackermanMe/spring-flashapi/commit/ce52717edf2f93c1016e24d340e8a68501f25ee7))

## [3.1.0](https://github.com/HackermanMe/spring-flashapi/compare/v3.0.1...v3.1.0) (2026-09-02)


### Features

* add FlashPrincipalResolver for type-safe ownership and user injection (Closes [#23](https://github.com/HackermanMe/spring-flashapi/issues/23)) ([934b1f6](https://github.com/HackermanMe/spring-flashapi/commit/934b1f60d01ceb908cbce3ee7b4715667b209f75))

## [3.0.1](https://github.com/HackermanMe/spring-flashapi/compare/v3.0.0...v3.0.1) (2026-09-01)


### Bug Fixes

* update README version to 3.0.0 and fix release-please marker placement ([7fe3cc3](https://github.com/HackermanMe/spring-flashapi/commit/7fe3cc39164e4c9442148d53b01b3b0be8f692c0))

## [3.0.0](https://github.com/HackermanMe/spring-flashapi/compare/v2.0.0...v3.0.0) (2026-09-01)


### ⚠ BREAKING CHANGES

* @FlashAudit, @FlashMultiTenant, @FlashWebhook, and @FeatureGuard are deprecated. Use @FlashEntity attributes instead: audit/trackFields, tenantField, webhook/webhookEvents, maxRecords. Old annotations still work but will be removed in the next major version.

### Features

* add declarative counters via @FlashCounter (Closes [#18](https://github.com/HackermanMe/spring-flashapi/issues/18)) ([8d6aa90](https://github.com/HackermanMe/spring-flashapi/commit/8d6aa90e8f6ce857ab9290ecac2feb397378b6b4))
* add owner-based access control via @FlashSecured ownerField (Closes [#16](https://github.com/HackermanMe/spring-flashapi/issues/16)) ([f5ca20e](https://github.com/HackermanMe/spring-flashapi/commit/f5ca20e26b6c48c7b1b67fd16e16cf433300ecae))
* auto-inject authenticated user via @FlashEntity(currentUserField) (Closes [#17](https://github.com/HackermanMe/spring-flashapi/issues/17)) ([154e4d5](https://github.com/HackermanMe/spring-flashapi/commit/154e4d503d0387398d36d6816777354a4a301608))


### Bug Fixes

* sync README version to 2.0.0 and fix release-please config ([c39efd4](https://github.com/HackermanMe/spring-flashapi/commit/c39efd4c2360b9169404a73e165466b0476d6cfd))


### Documentation

* add migration guide and update release notes for v2.0.0 ([2bac4e2](https://github.com/HackermanMe/spring-flashapi/commit/2bac4e241fde4658940cd20d09082807e18b4793))


### Code Refactoring

* consolidate @FlashAudit, @FlashMultiTenant, @FlashWebhook, @FeatureGuard into @FlashEntity (Closes [#19](https://github.com/HackermanMe/spring-flashapi/issues/19)) ([6d3f237](https://github.com/HackermanMe/spring-flashapi/commit/6d3f23721c8f41067db2493614d358cf2a73efa2))

## [2.0.0](https://github.com/HackermanMe/spring-flashapi/compare/v1.1.0...v2.0.0) (2026-08-31)


### ⚠ BREAKING CHANGES

* flashapi.soft-delete.column-name is renamed to flashapi.soft-delete.attribute-name to accurately reflect that it expects a Java attribute name (e.g., deletedAt), not a SQL column name (e.g., deleted_at)

### Features

* add @FeatureGuard annotation for plan/record-limit enforcement ([#7](https://github.com/HackermanMe/spring-flashapi/issues/7)) ([6876a60](https://github.com/HackermanMe/spring-flashapi/commit/6876a607790e7031d620f6323b00a659bebeb4a0))
* add interactive CRUD dashboard with HTMX and real-time WebSocket ([4754cdf](https://github.com/HackermanMe/spring-flashapi/commit/4754cdfc4a66aae05b3ffb05f9cddb0be5d5f118))
* add lifecycle hooks and relation filters support (Closes [#12](https://github.com/HackermanMe/spring-flashapi/issues/12), [#13](https://github.com/HackermanMe/spring-flashapi/issues/13)) ([f76dc12](https://github.com/HackermanMe/spring-flashapi/commit/f76dc12881a0df7c7cb56672f79aa84878cd2e15))
* add sparse fieldset support with ?fields= query parameter (Closes [#11](https://github.com/HackermanMe/spring-flashapi/issues/11)) ([f12bf40](https://github.com/HackermanMe/spring-flashapi/commit/f12bf4000f79d44b0f875c53f91b90e60bba5482))
* resolve @ManyToOne relations via FK ID in create/update requests (Closes [#8](https://github.com/HackermanMe/spring-flashapi/issues/8)) ([22e6709](https://github.com/HackermanMe/spring-flashapi/commit/22e67095a41d90f604e3c0dbd613e00e5ebaa432))


### Bug Fixes

* add typed error responses for constraint violations and invalid input (Closes [#9](https://github.com/HackermanMe/spring-flashapi/issues/9)) ([9c92903](https://github.com/HackermanMe/spring-flashapi/commit/9c92903cea37baa7468cef26465a5ff8c73925ff))
* correct test assertions and docs for hooks and relation filters ([8b195f4](https://github.com/HackermanMe/spring-flashapi/commit/8b195f41ae99da9127a8a943ae1a518752437381))
* rename soft-delete config from column-name to attribute-name (Closes [#10](https://github.com/HackermanMe/spring-flashapi/issues/10)) ([3a845aa](https://github.com/HackermanMe/spring-flashapi/commit/3a845aafa7ad9ec3c768446f73a7ab61743e1188))
* resolve duplicate operationId in OpenAPI spec for custom controllers ([#4](https://github.com/HackermanMe/spring-flashapi/issues/4)) ([3d4faed](https://github.com/HackermanMe/spring-flashapi/commit/3d4faed3ac906b452fd1f6312b92053d975949cb))
* sync release-please manifest and README version to 1.1.0 ([a135a3a](https://github.com/HackermanMe/spring-flashapi/commit/a135a3a7f2866ddfdd1f36c4628ff303acea85bc)), closes [#4](https://github.com/HackermanMe/spring-flashapi/issues/4)

## [1.1.0](https://github.com/HackermanMe/spring-flashapi/compare/v1.0.0...v1.1.0) (2026-08-11)


### Features

* add production health check endpoints (/health and /ready) ([5a886e9](https://github.com/HackermanMe/spring-flashapi/commit/5a886e94b6de581168bb6681c279135e6918096f))


### Documentation

* add release-please version marker in README ([a6c29e0](https://github.com/HackermanMe/spring-flashapi/commit/a6c29e0158f2880b6bde412311ef6a04cf795acb))
