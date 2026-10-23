"""Acceptance tests write data: use a disposable database only."""
import os,json,ssl,urllib.request,urllib.error,urllib.parse,http.cookiejar,uuid
from pathlib import Path
BASE=os.environ.get('EMERALD_TEST_BASE','http://127.0.0.1:8080/api');PASSWORD=os.environ['EMERALD_TEST_PASSWORD'];results=[]
def client():
 h=[urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar())]
 if os.environ.get('EMERALD_TEST_CA'):h.append(urllib.request.HTTPSHandler(context=ssl.create_default_context(cafile=os.environ['EMERALD_TEST_CA'])))
 return urllib.request.build_opener(*h)
admin=client();anon=client();csrf={}
def call(path,method='GET',body=None,headers=None,who=None,expected=200):
 h=dict(headers or {});data=body
 if isinstance(body,dict):data=json.dumps(body).encode();h['Content-Type']='application/json'
 if isinstance(data,str):data=data.encode()
 try:r=(who or admin).open(urllib.request.Request(BASE+path,data=data,headers=h,method=method),timeout=20)
 except urllib.error.HTTPError as e:r=e
 raw=r.read();assert r.status==expected,(path,method,r.status,expected,raw[:250])
 try:return json.loads(raw)
 except:return raw

def check(label,fn):fn();results.append(label);print('PASS',label,flush=True)
def login():
 global csrf
 csrf=call('/auth/csrf');call('/auth/login','POST',urllib.parse.urlencode({'username':'admin','password':PASSWORD}),{'Content-Type':'application/x-www-form-urlencoded',csrf['headerName']:csrf['token']});csrf=call('/auth/csrf')
def authheaders():return {csrf['headerName']:csrf['token']}
check('Database health',lambda:call('/actuator/health'))
check('Public content readable',lambda:call('/content',who=anon))
check('Anonymous enquiries blocked',lambda:call('/admin/contacts',who=anon,expected=401))
check('Anonymous history blocked',lambda:call('/admin/audit',who=anon,expected=401))
check('Legacy bearer credential rejected',lambda:call('/admin/content',headers={'Authorization':'Bearer invalid-old-token'},who=anon,expected=401))
check('Administrator login',login)
check('Session identity',lambda:call('/auth/me'))
check('Missing CSRF rejected',lambda:call('/admin/content/hero.line1','PUT',{'value':'Attack','version':-1},expected=403))
check('Unknown CMS key rejected',lambda:call('/admin/content/arbitrary','PUT',{'value':'Attack','version':-1},authheaders(),expected=400))
check('Blank CMS value rejected',lambda:call('/admin/content/hero.line1','PUT',{'value':' ','version':-1},authheaders(),expected=400))
key='hero.line1';old=call('/admin/content').get(key);version=old['version']if old else -1
out=call('/admin/content/'+key,'PUT',{'value':'Verified Emerald persistence','version':version},authheaders());results.append('Authenticated CMS save')
assert call('/content')[key]=='Verified Emerald persistence';results.append('Published content visible')
check('Stale edit returns conflict',lambda:call('/admin/content/'+key,'PUT',{'value':'Stale','version':version},authheaders(),expected=409))
check('Unsafe image URL rejected',lambda:call('/admin/content/media.portfolio.1','PUT',{'value':'javascript:alert(1)','version':-1},authheaders(),expected=400))
check('Invalid pagination rejected',lambda:call('/admin/contacts?size=101',expected=400))
check('Negative pagination rejected',lambda:call('/admin/contacts?page=-1',expected=400))
check('Malformed contact JSON rejected',lambda:call('/contact','POST','{broken',{'Content-Type':'application/json'},who=anon,expected=400))
contact=call('/contact','POST',{'name':'Acceptance Test','email':'acceptance@example.com','message':'Please verify our enquiry workflow.','company':'Test studio'},who=anon,expected=201)
lead=next(x for x in call('/admin/contacts')['content']if x['id']==contact['id']);results.append('Contact stored and listed')
updated=call('/admin/contacts/'+lead['id'],'PATCH',{'status':'IN_PROGRESS','notes':'Verified private note','version':lead['version']},authheaders());assert updated['notes']=='Verified private note';results.append('Enquiry follow-up saved')
check('Stale enquiry update rejected',lambda:call('/admin/contacts/'+lead['id'],'PATCH',{'status':'CLOSED','notes':'stale','version':lead['version']},authheaders(),expected=409))
check('Invalid enquiry status rejected',lambda:call('/admin/contacts/'+lead['id'],'PATCH',{'status':'INVALID','notes':'','version':updated['version']},authheaders(),expected=400))
def upload(payload,filename,expected=200):
 b='emerald-'+uuid.uuid4().hex;data=('--'+b+'\r\nContent-Disposition: form-data; name="file"; filename="'+filename+'"\r\nContent-Type: image/png\r\n\r\n').encode()+payload+('\r\n--'+b+'--\r\n').encode();return call('/admin/media','POST',data,{**authheaders(),'Content-Type':'multipart/form-data; boundary='+b},expected=expected)
check('Disguised SVG rejected',lambda:upload(b'<svg onload="alert(1)"></svg>','evil.png',400))
image=upload((Path(__file__).parents[1]/'assets/extracted/embedded-1.png').read_bytes(),'image.png');call(image['url'].removeprefix('/api'),who=anon);results.append('Image upload and public retrieval')
check('Invalid media path rejected',lambda:call('/media/not-an-image.png',who=anon,expected=404))
assert any(e['action']=='CONTENT_SAVED'for e in call('/admin/audit'));results.append('Audit trail persisted')
for i in range(5):
 try:call('/contact','POST',{},who=anon,expected=400)
 except AssertionError as e:assert e.args[0][2]==429,e
check('Contact abuse limit enforced',lambda:call('/contact','POST',{},who=anon,expected=429))
check('Logout',lambda:call('/auth/logout','POST',headers=authheaders(),expected=204))
check('Logged-out session rejected',lambda:call('/admin/contacts',expected=401))
attacker=client();t=call('/auth/csrf',who=attacker);codes=[]
for i in range(11):
 try:call('/auth/login','POST',urllib.parse.urlencode({'username':'admin','password':'wrong-password'}),{'Content-Type':'application/x-www-form-urlencoded',t['headerName']:t['token']},who=attacker,expected=401);codes.append(401)
 except AssertionError as e:assert e.args[0][2]==429,e;codes.append(429)
assert codes[-1]==429;results.append('Sign-in brute-force throttled')
Path(__file__).parents[1].joinpath('evidence/api-results.json').write_text(json.dumps({'passed':len(results),'checks':results},indent=2));print('API acceptance:',len(results),'passed',flush=True)
