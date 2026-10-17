#!/usr/bin/env python3
"""One map API for every QueueGo role. Loopback by default; does not access Supabase."""
import argparse
import json
import mimetypes
from pathlib import Path
import re
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlsplit
from core import Engine

MAX_BODY=256*1024


def handler(engine,web_root,vector=None,origins=(),assets_root=None):
    root=Path(web_root).resolve()
    class API(SimpleHTTPRequestHandler):
        def __init__(self,*args,**kwargs):super().__init__(*args,directory=str(root),**kwargs)
        def log_message(self,*args):pass  # Never log GPS query strings or request bodies.
        def json_response(self,value,status=200):
            payload=json.dumps(value,ensure_ascii=False,allow_nan=False).encode()
            self.send_response(status);self.send_header('Content-Type','application/json; charset=utf-8');self.send_header('Content-Length',str(len(payload)));self.send_header('Cache-Control','no-store');self.send_header('X-Content-Type-Options','nosniff')
            origin=self.headers.get('Origin')
            if origin in origins:self.send_header('Access-Control-Allow-Origin',origin);self.send_header('Vary','Origin')
            self.end_headers();self.wfile.write(payload)
        def do_OPTIONS(self):
            origin=self.headers.get('Origin')
            if origin not in origins:self.json_response({'error':'origin_not_allowed'},403);return
            self.send_response(204);self.send_header('Access-Control-Allow-Origin',origin);self.send_header('Access-Control-Allow-Methods','GET,POST,OPTIONS');self.send_header('Access-Control-Allow-Headers','Content-Type');self.send_header('Content-Length','0');self.end_headers()
        def api(self,body):
            action=urlsplit(self.path).path.removeprefix('/map-api/').rstrip('/')
            actions={'geocode':engine.geocode,'reverse-geocode':engine.reverse,'nearest':engine.nearest,'route':engine.route,'route-alternatives':lambda b:engine.route(b,True),'matrix':engine.matrix,'optimize-stops':engine.optimize,'match':engine.match,'nearby':engine.nearby,'traffic-signals':lambda b:engine.road_features(b,'signals'),'road-restrictions':lambda b:engine.road_features(b,'restrictions'),'eta':engine.progress}
            if action=='health':self.json_response({**engine.metadata(),'capabilities':list(actions),'vector_map':bool(vector),'vector_url':'/map-api/vector.pmtiles' if vector else None});return
            if action not in actions:self.json_response({'error':'unknown_map_action'},404);return
            try:self.json_response(actions[action](body))
            except (ValueError,TypeError,KeyError,OverflowError) as error:self.json_response({'error':'invalid_or_unavailable_request','message':str(error)},400)
            except Exception:self.json_response({'error':'routing_unavailable','message':'No route was fabricated or fetched from a public fallback.'},503)
        def do_POST(self):
            if not self.path.startswith('/map-api/'):self.json_response({'error':'not_found'},404);return
            try:
                length=int(self.headers.get('Content-Length','0'))
                if not 0<length<=MAX_BODY:self.json_response({'error':'request_size_limit'},413);return
                if not self.headers.get('Content-Type','').startswith('application/json'):self.json_response({'error':'json_required'},415);return
                body=json.loads(self.rfile.read(length))
                if not isinstance(body,dict):raise ValueError('JSON object required')
            except (ValueError,UnicodeError):self.json_response({'error':'invalid_json'},400);return
            self.api(body)
        def vector_file(self,head=False):
            if not vector or not Path(vector).is_file():self.json_response({'error':'no_installed_vector_package'},404);return
            file=Path(vector);size=file.stat().st_size;start=0;end=size-1;status=200
            requested=self.headers.get('Range')
            if requested:
                match=re.fullmatch(r'bytes=(\d+)-(\d*)',requested)
                if not match:self.json_response({'error':'single_byte_range_required'},416);return
                start=int(match[1]);end=min(size-1,int(match[2]) if match[2] else size-1);status=206
                if start>end or start>=size:self.json_response({'error':'range_outside_file'},416);return
            self.send_response(status);self.send_header('Content-Type','application/vnd.pmtiles');self.send_header('Accept-Ranges','bytes');self.send_header('Content-Length',str(end-start+1));self.send_header('Cache-Control','public, max-age=3600')
            if status==206:self.send_header('Content-Range',f'bytes {start}-{end}/{size}')
            self.end_headers()
            if not head:
                with file.open('rb') as source:
                    source.seek(start);remaining=end-start+1
                    while remaining:
                        chunk=source.read(min(1024*1024,remaining))
                        if not chunk:break
                        self.wfile.write(chunk);remaining-=len(chunk)
        def do_GET(self):
            parsed=urlsplit(self.path)
            if parsed.path=='/map-api/vector.pmtiles':self.vector_file();return
            if parsed.path.startswith('/map-api/'):
                values={key:value[0] for key,value in parse_qs(parsed.query).items()}
                for key in ('bounds','points'):
                    if key in values:
                        try:values[key]=json.loads(values[key])
                        except ValueError:self.json_response({'error':'invalid_json_query'},400);return
                self.api(values);return
            self.serve_public()
        def do_HEAD(self):
            if urlsplit(self.path).path=='/map-api/vector.pmtiles':self.vector_file(True);return
            self.serve_public(head=True)
        def serve_public(self,head=False):
            path=urlsplit(self.path).path
            if path.startswith('/map-assets/'):
                from urllib.parse import unquote
                if not assets_root:self.send_error(404);return
                assets=Path(assets_root).resolve();file=(assets/unquote(path.removeprefix('/map-assets/'))).resolve()
                if not file.is_relative_to(assets) or not file.is_file():self.send_error(404);return
                data=file.read_bytes();self.send_response(200);self.send_header('Content-Type',mimetypes.guess_type(file.name)[0] or 'application/octet-stream');self.send_header('Content-Length',str(len(data)));self.send_header('Cache-Control','public, max-age=86400');self.end_headers()
                if not head:self.wfile.write(data)
                return
            if path=='/':self.send_response(302);self.send_header('Location','/map-view/');self.end_headers();return
            candidate=Path(self.translate_path(path)).resolve()
            if not candidate.is_relative_to(root) or any(part.startswith('.') for part in candidate.relative_to(root).parts):self.send_error(404);return
            if candidate.is_dir() and not (candidate/'index.html').is_file():self.send_error(404);return
            if head:super().do_HEAD()
            else:super().do_GET()
    return API


def main():
    parser=argparse.ArgumentParser();parser.add_argument('--database',required=True);parser.add_argument('--config',required=True);parser.add_argument('--web-root',default=str(Path(__file__).resolve().parents[1]));parser.add_argument('--vector');parser.add_argument('--assets-root');parser.add_argument('--host',default='127.0.0.1');parser.add_argument('--port',type=int,default=8765);parser.add_argument('--allowed-origin',action='append',default=[])
    args=parser.parse_args();engine=Engine(args.database,args.config)
    server=ThreadingHTTPServer((args.host,args.port),handler(engine,args.web_root,args.vector,args.allowed_origin,args.assets_root or str(Path(args.web_root)/'map-assets')));server.daemon_threads=True
    print(json.dumps({'listening':f'http://{args.host}:{args.port}','data':engine.metadata()},ensure_ascii=False),flush=True)
    server.serve_forever()

if __name__=='__main__':main()
