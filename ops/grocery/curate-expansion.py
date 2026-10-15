"""Explicit package observations reviewed on 2026-10-13. No photos, prose or prices.
Size arrays are per-product source lists, never a cross-product of variants/sizes.
Re-running preserves the observation timestamp; import remains batch/idempotent.
"""
import json,datetime,pathlib
rows=[]
old=pathlib.Path('catalog/verified-expansion.json')
STAMP=json.loads(old.read_text())[0]['retrieved_at'] if old.exists() else datetime.datetime.now(datetime.timezone.utc).isoformat()
def add(brand,name,sizes,unit,category,url,kind='manufacturer',sale='ชิ้น',pack=1):
    for size in sizes:
        rows.append(dict(brand=brand,product_name_th=name,variant='',size_value=size,size_unit=unit,pack_size=pack,sales_unit=sale,category=category,source_url=url,source_type=kind,retrieved_at=STAMP,active=False))

u='https://www.betagen.co.th/product'
for name,sizes in [('นมเปรี้ยวสูตรนมพร่องมันเนย',[85,140,300,400]),('นมเปรี้ยวปราศจากน้ำตาลแลคโตส',[85,140,300]),('นมเปรี้ยวชนิดโยเกิร์ตรสสตรอเบอร์รี',[85,140,300]),('นมเปรี้ยวชนิดโยเกิร์ตรสสับปะรด',[85,140,300]),('นมเปรี้ยวชนิดโยเกิร์ตรสส้ม',[85,140,300]),('นมเปรี้ยวชนิดโยเกิร์ตคละรส',[85])]:
    add('บีทาเก้น',name,sizes,'ml','นมเปรี้ยว',u,sale='ขวด')
u='https://www.ichitangroup.com/th/products-and-brands/ichitan-green-tea'
for flavor,sizes in [('ต้นตำรับ',[280,420,500]),('เก๊กฮวยน้ำผึ้ง',[500]),('0 แคลอรี่ พลัสคาเทชิน กลิ่นมะลิ',[420]),('แคนดี้แอปเปิล',[420]),('ทีเพลย์',[420]),('องุ่นเคียวโฮ',[250,280,410]),('ข้าวญี่ปุ่น',[280,420,500]),('ฮันนี่เลมอน',[250,280,420,500,840])]:
    add('อิชิตัน','กรีนที '+flavor,sizes,'ml','ชา',u,sale='ขวด')
add('ไลปอนเอฟ','น้ำยาล้างจาน เอ็กซ์ตร้า ไฮจีนิค',[485,300,500,750,1700,3000],'ml','น้ำยาล้างจาน','https://www.sahapat.co.th/product/householdproduct/Lipon_F','distributor')
add('มาม่า','บะหมี่กึ่งสำเร็จรูป รสหมูน้ำตก',[60],'g','บะหมี่กึ่งสำเร็จรูป','https://www.mama.co.th/th/products-domestic-brand-list-detail/17/15/52/31/Instant-Noodles-Moo-Nam-Tok-Flavour',sale='ซอง')
u='https://www.tops.co.th/th/shop-by-brands/royal-umbrella/pantry-and-ingredients/rice'
for brand,name,sizes in [('ฉัตร','ข้าวขาวหอมมะลิใหม่',[5,1]),('ฉัตรไลท์','ข้าวกข.43',[5]),('ฉัตรทอง','ข้าวขาวหอมมะลิ',[5]),('ฉัตรส้ม','ข้าวหอมผสม',[5]),('ฉัตร','ข้าวเหนียว',[1]),('ฉัตร','ข้าวขาวหอมมะลิ',[5])]:
    add(brand,name,sizes,'kg','ข้าวสาร',u,'retailer',sale='ถุง')
u='https://www.lotuss.com/th/brand/sealect'
for name,size,pack in [('ทูน่าสเต็กในน้ำเกลือ',80,1),('ทูน่าสเต็กในน้ำเกลือ',165,1),('ทูน่าสเต็กในน้ำแร่',80,1),('ทูน่าสเต็กในน้ำแร่',165,1),('ทูน่าแซนวิชในน้ำเกลือ',165,1),('ทูน่าแซนวิชในน้ำมันถั่วเหลือง',165,1),('ทูน่าแซนวิชในน้ำแร่',165,1),('แมคเคอเรลในซอสมะเขือเทศฝาดึง',155,1),('แมคเคอเรลในซอสมะเขือเทศฝาดึง',155,10),('ซาร์ดีนในซอสมะเขือเทศฝาดึง',155,1),('ซาร์ดีนในซอสมะเขือเทศฝาดึง',155,10),('ปลาซาร์ดีนทอดราดพริก',155,1)]:
    add('ซีเล็ค',name,[size],'g','ปลากระป๋อง',u,'retailer',sale='แพ็ค' if pack>1 else 'กระป๋อง',pack=pack)
add('เดนทีน','หมากฝรั่งไอซ์ชูการ์ฟรีมิดไนท์มิ้นท์',[11.2],'g','หมากฝรั่ง','https://www.tops.co.th/th/snacks-and-desserts/candies-and-chewing-gum/chewing-gum','retailer')
add('ฟาร์มเฮ้าส์','ขนมปังโฮลวีต',[250],'g','ขนมปัง','https://www.tops.co.th/en/fresh-food-bakery/bakery','retailer',sale='ถุง')
add('สก๊อตต์','กระดาษอเนกประสงค์แบบแผ่น',[84],'sheet','ทิชชู่','https://officework.co.th/product-detail.html?pid=1030-0035','distributor',sale='แพ็ค',pack=3)
add('เบอร์ดี้','กาแฟพร้อมดื่ม แบล็ค ซีโร่',[180],'ml','กาแฟพร้อมดื่ม','https://www.ajinomoto.co.th/th/our-product/retail-product/birdy/birdy-black-zero-1/',sale='กระป๋อง')
add('โฟร์โมสต์','นมยูเอชที รสจืด',[225],'ml','นม','https://www.foremostthailand.com/product/100-uht-plain-milk/',sale='กล่อง')
add('โฟร์โมสต์','นมยูเอชทีไขมัน 0% รสจืด',[225],'ml','นม','https://www.foremostthailand.com/product/100-uht-non-fat-plain-milk/',sale='กล่อง')
add('เด็กสมบูรณ์','ซีอิ๊วขาวสูตร 1',[700],'ml','ซีอิ๊ว','https://shop.deksomboon.com/th/product/1092665/ซีอิ๊วขาว-สูตร1-700ml-soysauce',sale='ขวด')
add('เด็กสมบูรณ์','ซีอิ๊วขาวฉลากน้ำเงิน',[700],'ml','ซีอิ๊ว','https://shop.deksomboon.com/th/category/142934/ซีอิ๊วขาวฉลากน้ำเงิน',sale='แพ็ค',pack=4)
add('โอรีโอ','ดาร์กแอนด์ไวท์คุกกี้แซนด์วิช',[105],'g','คุกกี้','https://www.tops.co.th/th/shop-by-concept/snacker/snacks-and-desserts/biscuits-cookies-crackers','retailer',sale='ห่อ')
add('โรซ่า','ผักกาดดองเค็ม',[170],'g','อาหารกระป๋อง','https://www.hiqfood.com/product/salted-pickled-cabbage/',sale='กระป๋อง')
add('โรซ่า','ผักกาดดองเผ็ดหวาน',[170],'g','อาหารกระป๋อง','https://www.hiqfood.com/product/roza-sweet-pickle-mustard-with-chilli-170g/',sale='กระป๋อง')
add('เซลล็อกซ์','กระดาษชำระ PREMIUM',[1],'piece','กระดาษชำระ','https://www.homepro.co.th/p/1072838','retailer',sale='แพ็ค',pack=6)
add('เซลล็อกซ์','กระดาษชำระ PREMIUM BOTANIS',[1],'piece','กระดาษชำระ','https://www.homepro.co.th/p/1263640','retailer',sale='แพ็ค',pack=6)
add('แฟซ่า','แชมพูผมดำเงางาม',[340],'ml','ยาสระผม','https://allonline.7eleven.co.th/p/แฟซ่า-แชมพูผมดำเงางาม-340-มล/478173/','retailer',sale='ขวด')
add('ซอลส์','แปรงสีฟัน แบมบู',[1],'piece','แปรงสีฟัน','https://www.bigc.co.th/product/salz-toothbrush-bamboo-pack-3.92491','retailer',sale='แพ็ค',pack=3)
add('มาจิคลีน','น้ำยาทำความสะอาดพื้น สวีท โรส การ์เด้น',[900],'ml','น้ำยาทำความสะอาด','https://www.homepro.co.th/p/1199507','retailer',sale='ขวด')
add('ซันโว','ถุงขยะดำพับใบ 20x30 นิ้ว',[1000],'g','ถุงขยะ','https://www.ofm.co.th/product/ถุงขยะดำพับใบ-20x30-นิ้ว-แพ็ค1กก-ดำ-ซันโว-p.0009540','retailer',sale='แพ็ค')
add('เอลิส','ผ้าอนามัยแบบกางเกง เซฟ ไนท์ กลิ่นรีแล็กซ์ ลาเวนเดอร์ Size L',[1],'piece','ผ้าอนามัย','https://www.watsons.co.th/th/all-brands/list/156140/elis','retailer',sale='แพ็ค',pack=2)
add('พานาโซนิค','ถ่านไฟฉาย NEO R14NT/2SL ขนาด C',[1],'piece','สินค้าจิปาถะ','https://www.ofm.co.th/product/ถ่านไฟฉาย-r14nt-2sl-c-แพ็ค-2-ก้อน-panasonic-neo-p.8030550','retailer',sale='แพ็ค',pack=2)
add('ไฮยีน','น้ำยาซักผ้า เอ็กซ์เพิร์ท วอช มิลค์กี้ ทัช',[600],'ml','น้ำยาซักผ้า','https://shop.ip-one.com/th/product/1323824/hygiene-liquid-detergent-200-600ml',sale='ถุง')
json.dump(rows,open('catalog/verified-expansion.json','w'),ensure_ascii=False,indent=2)
print('Reviewed package observations:',len(rows))
