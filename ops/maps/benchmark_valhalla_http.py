#!/usr/bin/env python3
"""Real Valhalla Actor HTTP transport for restricted hosts without ZeroMQ interface enumeration."""
import json
import sys
from http.server import BaseHTTPRequestHandler, HTTPServer
import valhalla

actor=valhalla.Actor(sys.argv[1])
class API(BaseHTTPRequestHandler):
    def do_POST(self):
        try:
            body=json.loads(self.rfile.read(int(self.headers.get('Content-Length','0'))))
            action={'route':'route','sources_to_targets':'matrix','trace_route':'trace_route','trace_attributes':'trace_attributes','locate':'locate'}[self.path.strip('/')]
            result=getattr(actor,action)(body)
            payload=json.dumps(result).encode();self.send_response(200);self.send_header('Content-Type','application/json');self.send_header('Content-Length',str(len(payload)));self.end_headers();self.wfile.write(payload)
        except Exception as error:
            self.send_response(400);self.end_headers();self.wfile.write(json.dumps({'error':str(error)}).encode())
    def log_message(self,*args):pass
HTTPServer(('127.0.0.1',8002),API).serve_forever()
