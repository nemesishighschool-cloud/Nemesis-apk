"""MCP tools client: Streamable HTTP and a persistent stdio process over SSH.

Implements initialize, initialized, paginated tools/list and tools/call. This is
not an OAuth, resources, prompts, sampling, or legacy SSE client.
"""
import json, threading, uuid
from urllib.parse import urlparse

class McpError(Exception): pass

class McpClient:
    def __init__(self, transport, target, server_id, secret=''):
        self.transport=transport; self.target=target; self.server_id=server_id; self.secret=secret
        self.lock=threading.RLock(); self.session_id=None; self.protocol=None; self.initialized=False
        self._http=None
        if transport=='http':
            import requests
            parsed=urlparse(target)
            if parsed.scheme not in ('http','https') or not parsed.hostname or parsed.username or parsed.password or parsed.fragment:
                raise McpError('URL MCP HTTP(S) invalide')
            self._http=requests.Session(); self._http.trust_env=False
        elif transport!='ssh_stdio': raise McpError('Transport MCP non pris en charge')

    def close(self):
        if self._http:
            try: self._http.close()
            except Exception: pass
        if self.transport=='ssh_stdio':
            try:
                from java import jclass
                jclass('com.nemesis.apk.McpSshBridge').close(self.server_id)
            except Exception: pass
        self.initialized=False

    def _exchange(self, payload):
        ident=payload.get('id')
        if self.transport=='ssh_stdio':
            try:
                from java import jclass
                raw=str(jclass('com.nemesis.apk.McpSshBridge').exchange(self.server_id,self.target,json.dumps(payload,ensure_ascii=False)))
                value=json.loads(raw)
            except Exception as exc: raise McpError('MCP SSH indisponible : '+str(exc))
            if isinstance(value,dict) and value.get('_transport_error'): raise McpError(str(value['_transport_error']))
            return value
        headers={'Accept':'application/json, text/event-stream','Content-Type':'application/json'}
        if self.secret: headers['Authorization']='Bearer '+self.secret
        if self.session_id: headers['Mcp-Session-Id']=self.session_id
        if self.protocol: headers['MCP-Protocol-Version']=self.protocol
        try:
            response=self._http.post(self.target,json=payload,headers=headers,stream=True,timeout=(15,None),allow_redirects=False)
            with response:
                if response.status_code in (301,302,303,307,308): raise McpError('Redirection MCP refusée')
                if response.status_code>=400: raise McpError('Serveur MCP : HTTP '+str(response.status_code))
                self.session_id=response.headers.get('Mcp-Session-Id') or self.session_id
                if ident is None: return {}
                ct=response.headers.get('Content-Type','').lower()
                if 'application/json' in ct: value=response.json()
                elif 'text/event-stream' in ct:
                    value=None; data=[]
                    for raw in response.iter_lines(decode_unicode=True):
                        line=raw.decode('utf-8') if isinstance(raw,bytes) else (raw or '')
                        if not line:
                            if data:
                                event=json.loads('\n'.join(data)); data=[]
                                if event.get('id') == ident: value=event; break
                        elif line.startswith('data:'): data.append(line[5:].lstrip())
                    if value is None: raise McpError('Flux MCP terminé sans réponse à la requête')
                else: raise McpError('Type de réponse MCP inattendu : '+ct)
        except McpError: raise
        except Exception as exc: raise McpError('Connexion MCP impossible : '+str(exc))
        if not isinstance(value,dict) or value.get('id') != ident: raise McpError('Réponse MCP sans identifiant correspondant')
        return value

    def _request(self, method, params=None):
        payload={'jsonrpc':'2.0','id':uuid.uuid4().hex,'method':method,'params':params or {}}
        value=self._exchange(payload)
        if value.get('error'):
            error=value['error']; raise McpError(str(error.get('message',error) if isinstance(error,dict) else error))
        return value.get('result') or {}

    def _initialize(self):
        if self.initialized: return
        result=self._request('initialize',{'protocolVersion':'2025-11-25','capabilities':{},'clientInfo':{'name':'NEMESIS','version':'0.1.0'}})
        self.protocol=str(result.get('protocolVersion') or '2025-11-25')
        self._exchange({'jsonrpc':'2.0','method':'notifications/initialized'})
        self.initialized=True

    def list_tools(self):
        with self.lock:
            self._initialize(); tools=[]; cursor=None; seen=set()
            while True:
                result=self._request('tools/list',{'cursor':cursor} if cursor else {})
                tools.extend(result.get('tools') or [])
                cursor=result.get('nextCursor')
                if not cursor: return tools
                if cursor in seen: raise McpError('Pagination MCP cyclique')
                seen.add(cursor)

    def call_tool(self,name,arguments):
        with self.lock:
            self._initialize()
            if not isinstance(arguments,dict): raise McpError('Arguments MCP invalides')
            return self._request('tools/call',{'name':str(name),'arguments':arguments})
