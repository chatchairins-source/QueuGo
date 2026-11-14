#!/usr/bin/env python3
"""Exercise the actual HTTP listener with stalled headers/body and overload.

No routing fixture or physical GPS claim: this checks connection admission and
recovery before engine dispatch over real loopback sockets.
"""
import http.client
from pathlib import Path
import socket
import sys
import tempfile
import threading
import time

sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'queuego-map-engine'))
from server import BoundedMapHTTPServer,handler

with tempfile.TemporaryDirectory() as root:
    server=BoundedMapHTTPServer(('127.0.0.1',0),handler(None,root),max_workers=1,request_timeout=.3)
    thread=threading.Thread(target=server.serve_forever,daemon=True)
    thread.start()
    address=server.server_address
    def request():
        connection=http.client.HTTPConnection(*address,timeout=2)
        connection.request('GET','/')
        response=connection.getresponse()
        value=response.status
        response.read()
        connection.close()
        return value
    def await_busy():
        deadline=time.monotonic()+2
        while time.monotonic()<deadline:
            if request()==503:return
        raise AssertionError('Stalled client did not occupy bounded admission slot')
    def await_recovery():
        deadline=time.monotonic()+2
        while time.monotonic()<deadline:
            if request()==302:return
            time.sleep(.01)
        raise AssertionError('Timed-out connection leaked its worker slot')
    try:
        # A client never finishes its headers. Extra requests get a truthful 503.
        with socket.create_connection(address,timeout=2) as stalled:
            stalled.sendall(b'GET /map-api/route HTTP/1.1\r\nHost: localhost\r\n')
            await_busy()
            await_recovery()
        # A declared JSON body also cannot hold a worker indefinitely.
        with socket.create_connection(address,timeout=2) as stalled:
            stalled.sendall(b'POST /map-api/route HTTP/1.1\r\nHost: localhost\r\nContent-Type: application/json\r\nContent-Length: 50\r\n\r\n{')
            await_busy()
            await_recovery()
        # Existing request-size rejection works and releases admission normally.
        connection=http.client.HTTPConnection(*address,timeout=2)
        connection.request('POST','/map-api/route',body=b'{}',headers={'Content-Type':'application/json','Content-Length':'262145'})
        response=connection.getresponse()
        assert response.status==413
        response.read();connection.close()
        await_recovery()
        for arguments in ({'max_workers':0},{'max_workers':129},{'request_timeout':0}):
            try:BoundedMapHTTPServer(('127.0.0.1',0),handler(None,root),**arguments)
            except ValueError:pass
            else:raise AssertionError('Unsafe listener configuration accepted')
        print('Map HTTP resource bounds: 9 checks / 0 failures (actual loopback sockets)')
    finally:
        server.shutdown();server.server_close();thread.join(timeout=2)
