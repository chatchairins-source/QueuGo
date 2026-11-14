#!/usr/bin/env python3
"""Build one benchmark engine from the same immutable OSM extract; retain real resource evidence."""
import argparse
import json
import os
from pathlib import Path
import subprocess
import time


def measured(command, log_path, cwd=None):
    started = time.perf_counter()
    with open(log_path, 'w') as log:
        process = subprocess.Popen(command, stdout=log, stderr=subprocess.STDOUT, cwd=cwd)
        _, status, usage = os.wait4(process.pid, 0)
        process.returncode = os.waitstatus_to_exitcode(status)
    evidence = {'command': command, 'exit_code': process.returncode, 'wall_seconds': round(time.perf_counter()-started,3), 'cpu_user_seconds': usage.ru_utime, 'cpu_system_seconds':usage.ru_stime, 'peak_rss_kib':usage.ru_maxrss}
    if process.returncode: raise RuntimeError(json.dumps(evidence)+'\n'+Path(log_path).read_text()[-9000:])
    return evidence


def build(engine, pbf, target, tools, java, admin_pbf=None):
    target=Path(target).resolve();target.mkdir(parents=True,exist_ok=True);pbf=str(Path(pbf).resolve())
    builds=[]
    if engine=='valhalla':
        import valhalla
        tiles=target/'tiles';tiles.mkdir(exist_ok=True)
        config=valhalla.get_config(tile_extract='',tile_dir=tiles)
        config['mjolnir']['concurrency']=2
        config['mjolnir']['admin']=str(target/'admins.sqlite')
        config['mjolnir']['logging']={'type':'std_out','color':False}
        (target/'valhalla.json').write_text(json.dumps(config,indent=2))
        if admin_pbf:
            admin_binary=Path(valhalla.__file__).parent/'bin'/'valhalla_build_admins'
            builds.append(measured([str(admin_binary),'-c',str(target/'valhalla.json'),str(Path(admin_pbf).resolve())],target/'admins-build.log'))
        binary=Path(valhalla.__file__).parent/'bin'/'valhalla_build_tiles'
        builds.append(measured([str(binary),'-c',str(target/'valhalla.json'),'-j','2',pbf],target/'build.log'))
        version=valhalla.__version__
    elif engine=='osrm':
        root=Path(tools).resolve()
        prefix=[str(root/'lib/ld-musl-x86_64.so.1'),'--library-path',str(root/'lib')+':'+str(root/'usr/lib')]
        import shutil
        profile=target/'profiles'
        if not profile.exists(): shutil.copytree(root/'opt',profile)
        car=profile/'car.lua'
        car.write_text(car.read_text().replace('left_hand_driving              = false', 'left_hand_driving              = true'))
        input_path=target/'buriram.osm.pbf'
        if not input_path.exists(): input_path.symlink_to(pbf)
        for program,arguments in [('osrm-extract',['-t','2','-p',str(profile/'car.lua'),str(input_path)]),('osrm-partition',['-t','2',str(target/'buriram.osrm')]),('osrm-customize',['-t','2',str(target/'buriram.osrm')])]:
            builds.append(measured(prefix+[str(root/'usr/local/bin'/program)]+arguments,target/(program+'.log')))
        version='v6.0.0';(target/'image-manifest.json').write_text((root/'image-manifest.json').read_text())
    elif engine=='graphhopper':
        config=f'''graphhopper:
  datareader.file: {pbf}
  graph.location: {target / 'graph-cache'}
  profiles:
    - name: car
      turn_costs:
        vehicle_types: [motorcar, motor_vehicle]
        u_turn_costs: 60
      custom_model_files: [car.json, car_avoid_private_etc.json]
    - name: motorcycle
      turn_costs:
        vehicle_types: [motorcycle, motor_vehicle]
        u_turn_costs: 60
      custom_model_files: [motorcycle.json]
  graph.encoded_values: car_access,car_average_speed,road_access,track_type,road_class,surface
  import.osm.ignored_highways: footway,cycleway,path,pedestrian,steps,corridor,platform,raceway
  prepare.min_network_size: 200
  profiles_ch: []
  profiles_lm: []
  datareader.worker_threads: 2
server:
  application_connectors:
    - type: http
      port: 8989
      bind_host: 127.0.0.1
  admin_connectors:
    - type: http
      port: 8990
      bind_host: 127.0.0.1
logging:
  level: WARN
  appenders:
    - type: console
'''
        (target/'graphhopper.yml').write_text(config)
        builds.append(measured([java,'-XX:ActiveProcessorCount=2','-Xmx3g','-jar',str(Path(tools).resolve()),'import',str(target/'graphhopper.yml')],target/'build.log',cwd=target))
        version='11.0'
    else: raise ValueError(engine)
    size=sum(f.stat().st_size for f in target.rglob('*') if f.is_file() and not f.is_symlink() and not f.name.endswith('.log'))
    manifest=json.loads(Path(pbf).with_suffix('.manifest.json').read_text())
    report={'engine':engine,'version':version,'dataset_sha256':manifest['extract_sha256'],'builds':builds,'graph_and_config_bytes':size,'cpu_limit':2,'licence':{'valhalla':'MIT','graphhopper':'Apache-2.0','osrm':'BSD-2-Clause'}[engine]}
    (target/'build-report.json').write_text(json.dumps(report,indent=2)+'\n');print(json.dumps(report))

if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('engine',choices=['valhalla','graphhopper','osrm']);p.add_argument('pbf');p.add_argument('target');p.add_argument('--tools');p.add_argument('--java',default='java');p.add_argument('--admin-pbf');a=p.parse_args();build(a.engine,a.pbf,a.target,a.tools,a.java,a.admin_pbf)
