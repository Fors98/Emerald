# Emerald studio application

Animated public website plus private studio control at `/admin.html`: password sign-in, text CMS, image uploads, paginated enquiries, notes/status and change history. Built on Java 21, Spring Boot 3.5.16 and PostgreSQL 16.

See DEPLOY.md for deployment and RELEASE.md for verified results and remaining host-specific checks. This completes the recovered website/CMS application; it does not simulate an AI agency orchestrator or external CRM.

Tests: `mvn -f backend/pom.xml verify`. Live API and browser tests are in tests/ and write test data: use a disposable database only. No default admin password is supplied. The deployment supports one administrator and one API instance. Multi-instance operation requires shared sessions and a distributed limiter.

Session cookies are Secure/HttpOnly/SameSite=Strict in production, expire after 30 idle minutes, and are invalidated on API restart. Content versions prevent overwriting another edit. Contact data is not automatically deleted. Unlinked images remain stored. Both PostgreSQL and media require backups.
