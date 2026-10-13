"""Bounded, factual distributor research. No prices, descriptions or image downloads.
Unknown package sizes remain research candidates, never fabricated sellable SKUs.
"""
import re,json,time,urllib.request,html,hashlib,datetime,os
BASE='https://www.sahapat.co.th/product/'
OUT='catalog/research-spc.json'
GROUPS={'foodproduct':'อาหาร','beverageproduct':'เครื่องดื่ม','householdproduct':'ของใช้ในบ้าน','personalcare':'ของใช้ส่วนตัว','othersproduct':'จิปาถะ'}
def get(url):
 req=urllib.request.Request(url,headers={'User-Agent':'QueueGo-CatalogResearch/1.0 (factual product identification; no images)'})
 with urllib.request.urlopen(req,timeout=25) as r:return r.read().decode('utf-8')
def clean(s):return re.sub(r'\s+',' ',html.unescape(re.sub('<[^>]+>',' ',s))).strip()
rows=[];failures=[]
for group,category in GROUPS.items():
 try:s=get(BASE+group)
 except Exception as e:failures.append({'url':BASE+group,'error':str(e)});continue
 urls=list(dict.fromkeys(re.findall(r'href=["\']('+re.escape(BASE+group)+r'/[^"\']+)["\']',s)))
 for url in urls[:45]:
  time.sleep(.25)
  try:
   s=get(url);stamp=datetime.datetime.now(datetime.timezone.utc).isoformat()
   names=re.findall(r'<div class="item animatable[^>]*>\s*<img[^>]*>\s*<p>(.*?)</p>',s,re.S)
   brand_heading=re.findall(r'<(?:h[1-6]|div)[^>]*class=["\'][^"\']*title[^"\']*["\'][^>]*>(.*?)</(?:h[1-6]|div)>',s,re.S)
   for raw in names:
    name=clean(raw)
    if name: rows.append({'product_name_th':name,'source_url':url,'source_type':'distributor','retrieved_at':stamp,'source_product_id':hashlib.sha256(name.encode()).hexdigest()[:24],'research_category':category,'brand_slug':url.rsplit('/',1)[-1],'image_status':'pending','size_status':'unverified','active':False})
   print(group,url.rsplit('/',1)[-1],len(names),flush=True)
  except Exception as e:failures.append({'url':url,'error':str(e)})
  os.makedirs('catalog',exist_ok=True)
  with open(OUT,'w')as f:json.dump({'items':rows,'failures':failures,'policy':'Factual research only. Package identity and current domestic availability require verification before activation. Images not copied.'},f,ensure_ascii=False,indent=2)
print('Research candidates',len(rows),'failures',len(failures))
