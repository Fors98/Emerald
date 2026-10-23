#!/usr/bin/env python3
"""Offline, read-only Emerald release consistency checks."""
from pathlib import Path
import re, json, sys
root=Path(__file__).resolve().parent
index=(root/'index.html').read_text()
admin=(root/'admin.html').read_text()
cms=(root/'cms.js').read_text()
security=(root/'backend/src/main/java/com/emerald/backend/security/SecurityConfig.java').read_text()
prod=(root/'backend/src/main/resources/application-prod.properties').read_text()
keys=set(re.findall(r'data-cms="([^"]+)"',index))
match=re.search(r'const keys=(\[.*?\]);\nconst defaults=',admin,re.S)
assert match, 'Admin field configuration missing'
admin_keys=[item[0] for item in json.loads(match.group(1))]
assert len(admin_keys)==len(set(admin_keys)), 'Duplicate admin keys'
assert set(admin_keys)==keys, 'CMS/admin key mismatch: '+str(keys-set(admin_keys))
assert 'textContent = content[key]' in cms, 'Unsafe or missing CMS text rendering'
assert 'const defaults=' in admin and 'area.value=content[key]??defaults[key]' in admin, 'CMS editor must display original text'
assert 'Cannot save empty content' in admin, 'Blank CMS value guard missing'
assert 'Authorization' in admin and 'Bearer ' in admin, 'Admin token integration missing'
assert 'hasRole("ADMIN")' in security, 'Admin route protection missing'
assert 'MessageDigest.isEqual' in security, 'Constant-time token comparison missing'
assert 'server.servlet.context-path=/api' in prod, 'Production API path missing'
assert 'emerald.admin.token=${EMERALD_ADMIN_TOKEN}' in prod, 'Production admin token binding missing'
assert 'spring.jpa.hibernate.ddl-auto=validate' in prod, 'Production schema validation missing'
assert (root/'script.js').is_file() and (root/'styles.css').is_file(), 'Original frontend assets missing'
print(f'PASS: {len(keys)} CMS fields match admin; safe text rendering, token protection, production configuration and frontend files checked.')
print('NOT VERIFIED: Java tests, browser E2E, deployment, security penetration testing.')
