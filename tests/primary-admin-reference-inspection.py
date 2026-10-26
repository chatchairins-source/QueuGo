#!/usr/bin/env python3
import importlib.util,json,tempfile,zipfile
from pathlib import Path
spec=importlib.util.spec_from_file_location('acq',Path(__file__).parent.parent/'scripts'/'acquire-thailand-primary-admin-reference.py')
m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
with tempfile.TemporaryDirectory() as td:
 p=Path(td)/'fixture.xlsx'
 shared='<?xml version="1.0" encoding="UTF-8" standalone="yes"?><sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" count="4" uniqueCount="4"><si><t>CODE</t></si><si><t>NAME</t></si><si><t>4029</t></si><si><t>เวียงเก่า</t></si></sst>'
 wb='<?xml version="1.0" encoding="UTF-8" standalone="yes"?><workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="codes" sheetId="1" r:id="rId1"/></sheets></workbook>'
 rel='<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/></Relationships>'
 sh='<?xml version="1.0" encoding="UTF-8" standalone="yes"?><worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData><row r="1"><c r="A1" t="s"><v>0</v></c><c r="B1" t="s"><v>1</v></c></row><row r="2"><c r="A2" t="s"><v>2</v></c><c r="B2" t="s"><v>3</v></c></row></sheetData></worksheet>'
 with zipfile.ZipFile(p,'w') as z:
  z.writestr('xl/workbook.xml',wb);z.writestr('xl/_rels/workbook.xml.rels',rel);z.writestr('xl/sharedStrings.xml',shared);z.writestr('xl/worksheets/sheet1.xml',sh)
 r=m.inspect_xlsx(p)
 assert r['valid_xlsx'] and r['sheets'][0]['rows']==2
 assert r['matched_rows'][0]['values']==['4029','เวียงเก่า']
 txt='4026|เวียงเก่า\nอื่น'.encode('cp874'); q=Path(td)/'a.txt';q.write_bytes(txt)
 t=m.inspect_text(q);assert t['encoding']=='cp874' and t['matched_lines'][0]['line']==1
 assert m.sha256_file(p) and len(m.sha256_file(p))==64
 pdf=m.inspect_pdf_text('432 4025 อำเภอโนนศิลา ขอนแก่น\n433 4029 อำเภอเวียงเก่า ขอนแก่น\n')
 assert pdf['code_4029_seen'] and pdf['wiang_kao_4029_name_match'] and not pdf['wiang_kao_4026_name_match']
 h=Path(td)/'h.txt';h.write_text('HTTP/2 200\nLast-Modified: Mon, 01 Jan 2026 00:00:00 GMT\nETag: abc\nSet-Cookie: secret\n',encoding='iso-8859-1')
 meta=m.response_metadata(h);assert meta['last-modified'].startswith('Mon') and meta['etag']=='abc' and 'set-cookie' not in meta
 print(json.dumps({'checks':9,'failures':0,'scope':'primary admin reference xlsx/text/pdf inspection and safe response metadata; no network or Production'}))
