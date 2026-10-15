"""Extract only factual names and explicit size lists from fetched official catalogs.
Run after fetching the referenced public pages into scratch. Never reads/copies images.
"""
import json,re,html,datetime,urllib.parse
stamp=datetime.datetime.now(datetime.timezone.utc).isoformat();rows=[]
s=open('/tmp/qg-thainamthip-all.html').read()
data=json.loads(re.search('<script id="__NEXT_DATA__"[^>]*>(.*?)</script>',s,re.S)[1])['props']['pageProps']
brands={b['id']:next(t['name'] for t in b['trans'] if t['lang']=='en') for b in data['brands']}
for p in data['products']:
 th=next(t for t in p['trans'] if t['lang']=='th');en=next((t for t in p['trans'] if t['lang']=='en'),{})
 size_text=html.unescape(re.sub('<[^>]+>',' ',th.get('available_sizes') or ''))
 brand=brands[p['brand_id']];name=th['name'];category='น้ำดื่ม' if re.search('น้ำทิพย์|น้ำดื่ม',name) else 'น้ำผลไม้' if re.search('มินิทเมด|มินิเมด|มินิทเมท',name) else 'น้ำอัดลม'
 for size,unit in re.findall(r'(\d+(?:\.\d+)?)\s*(มล\.?|ลิตร)',size_text):
  rows.append(dict(brand=brand,product_name_th=name,product_name_en=en.get('name'),variant='',size_value=size,size_unit=unit,pack_size=1,sales_unit='ชิ้น',category=category,source_url='https://www.thainamthip.co.th/product',source_type='manufacturer',source_product_id=str(p['product_id']),retrieved_at=stamp,active=False))
s=open('/tmp/qg-tiparos.html').read()
urls=list(dict.fromkeys(re.findall(r'href=["\'](https://www.tiparos.com/products/view/[^"\']+)["\']',s)))
for url in urls:
 name=urllib.parse.unquote(url).rsplit('/',1)[-1];name=re.sub(r'^\d+-\d+-','',name).replace('-',' ');name=re.sub(r'\s+',' ',name).strip()
 m=re.search(r'(\d+(?:\.\d+)?)\s*(มล|กรัม|ล\b)',name)
 if not m or ',' in name:continue # multiple concentration identity requires review
 brand=next((b for b in ['ทิพรส','โบว์แดง','เด็กอ้วน','ดีหนึ่ง'] if b in name),'ทิพรส')
 category='น้ำปลา' if 'น้ำปลา' in name else 'ซอส' if 'ซอส' in name else 'เครื่องปรุง'
 rows.append(dict(brand=brand,product_name_th=name,variant='',size_value=m[1],size_unit='ลิตร' if m[2]=='ล' else m[2],pack_size=1,sales_unit='ชิ้น',category=category,source_url='https://www.tiparos.com/products/',source_type='manufacturer',source_product_id=url.rsplit('/',1)[-1].split('-')[1],retrieved_at=stamp,active=False))
# Verified explicit package size in manufacturer's public product page, not invented variants.
rows.append(dict(brand='คาราบาวแดง',product_name_th='เครื่องดื่มชูกำลัง',variant='แบบขวด',size_value=150,size_unit='มล.',pack_size=1,sales_unit='ขวด',category='เครื่องดื่มชูกำลัง',source_url='https://www.carabao.co.th/Carabao-Energy-Drink',source_type='manufacturer',retrieved_at=stamp,active=False))
with open('catalog/manufacturer-facts.json','w')as f:json.dump(rows,f,ensure_ascii=False,indent=2)
print('Explicit manufacturer package facts:',len(rows))
