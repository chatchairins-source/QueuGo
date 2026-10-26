#!/usr/bin/env python3
import hashlib,json,re,subprocess,sys,zipfile
from pathlib import Path
from xml.etree import ElementTree as ET

SOURCES=[
    {'id':'bora_ccaa_page','kind':'html','url':'https://stat.bora.dopa.go.th/stat/statnew/statMenu/newStat/ccaa.php'},
    {'id':'bora_ccaatt_xlsx','kind':'xlsx','url':'https://stat.bora.dopa.go.th/dload/ccaatt.xlsx'},
    {'id':'bora_2568_district_text','kind':'text','url':'https://stat.bora.dopa.go.th/new_stat/file/68/stat_a68.txt'},
    {'id':'bora_2568_subdistrict_text','kind':'text','url':'https://stat.bora.dopa.go.th/new_stat/file/68/stat_t68.txt'},
]
MATCH_TERMS=('4026','4029','เวียงเก่า','402602','402608','402615','402901','402902','402903')
NS={'m':'http://schemas.openxmlformats.org/spreadsheetml/2006/main','r':'http://schemas.openxmlformats.org/officeDocument/2006/relationships','pr':'http://schemas.openxmlformats.org/package/2006/relationships'}

def sha256_file(p):
    h=hashlib.sha256()
    with open(p,'rb') as f:
        for b in iter(lambda:f.read(1024*1024),b''): h.update(b)
    return h.hexdigest()

def _cell_text(cell,shared):
    typ=cell.attrib.get('t')
    if typ=='inlineStr': return ''.join(t.text or '' for t in cell.findall('.//m:t',NS))
    v=cell.find('m:v',NS)
    if v is None: return ''
    raw=v.text or ''
    if typ=='s':
        try:return shared[int(raw)]
        except (ValueError,IndexError):return raw
    return raw

def inspect_xlsx(path):
    with zipfile.ZipFile(path) as z:
        bad=z.testzip()
        if bad: raise ValueError('corrupt xlsx member: '+bad)
        names=set(z.namelist())
        if 'xl/workbook.xml' not in names: raise ValueError('missing xl/workbook.xml')
        shared=[]
        if 'xl/sharedStrings.xml' in names:
            root=ET.fromstring(z.read('xl/sharedStrings.xml'))
            shared=[''.join(t.text or '' for t in si.findall('.//m:t',NS)) for si in root.findall('m:si',NS)]
        wb=ET.fromstring(z.read('xl/workbook.xml'))
        rels={}
        if 'xl/_rels/workbook.xml.rels' in names:
            rr=ET.fromstring(z.read('xl/_rels/workbook.xml.rels'))
            rels={x.attrib['Id']:x.attrib['Target'] for x in rr}
        sheets=[]; matches=[]; date_candidates=[]
        date_re=re.compile(r'(?:\d{1,2}[/-]\d{1,2}[/-](?:25|20)?\d{2}|(?:25|20)\d{2}[/-]\d{1,2}[/-]\d{1,2}|(?:ปรับปรุง|ข้อมูล|ณ วันที่).{0,60})')
        for s in wb.findall('.//m:sheet',NS):
            name=s.attrib.get('name','')
            rid=s.attrib.get('{%s}id'%NS['r'])
            target=rels.get(rid,'')
            if target.startswith('/'): member=target.lstrip('/')
            else: member='xl/'+target.lstrip('/') if not target.startswith('xl/') else target
            member=str(Path(member).as_posix())
            if member not in names: continue
            root=ET.fromstring(z.read(member)); row_count=0
            for row in root.findall('.//m:sheetData/m:row',NS):
                row_count+=1
                vals=[_cell_text(c,shared).strip() for c in row.findall('m:c',NS)]
                joined=' | '.join(vals)
                if any(term in joined for term in MATCH_TERMS):
                    matches.append({'sheet':name,'row':int(row.attrib.get('r',row_count)),'values':vals})
                for v in vals:
                    if v and date_re.search(v): date_candidates.append({'sheet':name,'row':int(row.attrib.get('r',row_count)),'value':v[:240]})
            sheets.append({'name':name,'member':member,'rows':row_count})
        return {'valid_xlsx':True,'sheets':sheets,'matched_rows':matches,'source_date_candidates':date_candidates[:50]}

def decode_text(data):
    for enc in ('utf-8-sig','cp874','tis-620'):
        try:return data.decode(enc),enc
        except UnicodeDecodeError: pass
    return data.decode('latin-1','replace'),'latin-1-replacement'

def inspect_text(path):
    data=Path(path).read_bytes(); text,enc=decode_text(data)
    matches=[]
    for i,line in enumerate(text.splitlines(),1):
        if any(term in line for term in MATCH_TERMS): matches.append({'line':i,'text':line[:1000]})
    return {'encoding':enc,'lines':len(text.splitlines()),'matched_lines':matches[:500]}

def inspect_html(path):
    data=Path(path).read_bytes(); text,enc=decode_text(data)
    hrefs=sorted(set(re.findall(r'''(?:href|src)\s*=\s*["']([^"']+)["']''',text,re.I)))
    relevant=[x for x in hrefs if 'ccaatt' in x.lower() or 'rcode' in x.lower() or 'dload' in x.lower()]
    snippets=[]
    for term in ('ccaatt.xlsx','ข้อมูลปรับปรุง','ปรับปรุง','วันที่'):
        pos=text.lower().find(term.lower())
        if pos>=0: snippets.append(text[max(0,pos-160):pos+360].replace('\r',' ').replace('\n',' ')[:520])
    return {'encoding':enc,'relevant_links':relevant[:100],'date_or_link_snippets':snippets[:20]}

def acquire_one(src,outdir):
    body=outdir/(src['id']+'.bin'); headers=outdir/(src['id']+'.headers.txt')
    fmt='%{http_code}\n%{url_effective}\n%{content_type}\n'
    cmd=['curl','--silent','--show-error','--location','--connect-timeout','20','--max-time','180','--retry','2','--retry-delay','3','--retry-all-errors','--dump-header',str(headers),'--output',str(body),'--write-out',fmt,src['url']]
    proc=subprocess.run(cmd,text=True,capture_output=True)
    parts=proc.stdout.splitlines(); http=parts[0] if len(parts)>0 else '000'; effective=parts[1] if len(parts)>1 else None; ctype=parts[2] if len(parts)>2 else None
    r={'id':src['id'],'kind':src['kind'],'requested_url':src['url'],'curl_exit':proc.returncode,'http_code':http,'effective_url':effective,'content_type':ctype,'stderr':proc.stderr[-2000:] if proc.stderr else ''}
    if body.exists():
        r['bytes']=body.stat().st_size; r['sha256']=sha256_file(body)
    else:r['bytes']=0;r['sha256']=None
    r['retrieved']=proc.returncode==0 and http=='200' and r['bytes']>0
    if r['retrieved']:
        try:
            if src['kind']=='xlsx': r['inspection']=inspect_xlsx(body)
            elif src['kind']=='text': r['inspection']=inspect_text(body)
            elif src['kind']=='html': r['inspection']=inspect_html(body)
        except Exception as e:
            r['inspection_error']=type(e).__name__+': '+str(e)
            if src['kind']=='xlsx': r['retrieved']=False
    return r

def main():
    if len(sys.argv)!=2: raise SystemExit('usage: acquire_thailand_primary_admin_reference.py OUTPUT_DIR')
    out=Path(sys.argv[1]); out.mkdir(parents=True,exist_ok=True)
    if any(out.iterdir()): raise SystemExit('output directory must be empty')
    results=[acquire_one(s,out) for s in SOURCES]
    x=next(r for r in results if r['id']=='bora_ccaatt_xlsx')
    receipt={
      'execution':'review_only_no_database_write','publisher':'Department of Provincial Administration / Bureau of Registration Administration (BORA)',
      'sources':results,
      'status':'acquired_primary_bytes' if x.get('retrieved') else 'blocked_external_source',
      'identity_approval':'not_performed','production_import_allowed':False,
      'limits':'Acquisition/provenance evidence only. A successful download does not approve canonical codes, geometry, freshness, hierarchy or Production import.',
      'next':'If official bytes were acquired, inspect source date/schema and reconcile 4026/4029 plus all missing/extra rows without remapping. If blocked, preserve receipt and continue deterministic guards.'
    }
    (out/'receipt.json').write_text(json.dumps(receipt,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    print(json.dumps({'status':receipt['status'],'sources':[{'id':r['id'],'http_code':r['http_code'],'curl_exit':r['curl_exit'],'retrieved':r['retrieved'],'bytes':r['bytes'],'sha256':r['sha256']} for r in results]},ensure_ascii=False))
if __name__=='__main__': main()
