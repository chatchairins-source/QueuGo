import valhalla,json,time
from pathlib import Path
import argparse
parser=argparse.ArgumentParser()
parser.add_argument('--assets',required=True)
parser.add_argument('--output',required=True)
args=parser.parse_args()
root=Path(args.assets).resolve()/'routing'
manifest=json.loads((root/'manifest.json').read_text())
c=json.loads((root/'valhalla.json').read_text())
c['mjolnir']['tile_dir']=''
c['mjolnir']['tile_extract']=str(root/'tiles.tar')
assert not c['mjolnir'].get('tile_url') and not c['mjolnir'].get('traffic_extract')
a=valhalla.Actor(c)
output=Path(args.output); output.parent.mkdir(parents=True,exist_ok=True)
pairs=[('buriram',(14.9942,103.1020),(15.0054,103.1114)),('bangkok',(13.7563,100.5018),(13.7465,100.5340)),('chiangmai',(18.7883,98.9853),(18.7901,98.9987)),('khonkaen',(16.4322,102.8236),(16.4451,102.8358)),('hatyai',(7.0086,100.4747),(7.0164,100.4891)),('bangkok-chiangmai',(13.7563,100.5018),(18.7883,98.9853))]
results=[]
for name,source,dest in pairs:
 req={'locations':[{'lat':p[0],'lon':p[1],'type':'break','search_cutoff':250} for p in [source,dest]],'costing':'motorcycle','units':'kilometers','language':'en-US','shape_format':'polyline6','directions_type':'instructions','costing_options':{'motorcycle':{'use_highways':0,'use_tolls':0}}}
 start=time.perf_counter()
 try:
  out=a.route(req); out=json.loads(out) if isinstance(out,str) else out
  path=output.parent/(name+'-route.json');path.write_text(json.dumps(out,ensure_ascii=False))
  results.append({'name':name,'from':source,'to':dest,'summary':out['trip']['summary'],'maneuvers':len(out['trip']['legs'][0]['maneuvers']),'seconds':round(time.perf_counter()-start,3),'status':'PASS_LINUX_ENGINE_ONLY'})
 except Exception as e:results.append({'name':name,'status':'FAIL','error':str(e)})
 print(json.dumps(results[-1]),flush=True)
output.write_text(json.dumps({'engine':'pyvalhalla 3.9.1','dataset_sha256':manifest['source']['extract_sha256'],'graph_sha256':manifest['graph']['sha256'],'platform':'Linux x86_64; not Android emulator or physical road QA','results':results},indent=2))

if any(result['status']=='FAIL' for result in results):raise SystemExit(1)
