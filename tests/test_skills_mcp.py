import io, json, socketserver, tarfile, tempfile, threading, unittest
from http.server import BaseHTTPRequestHandler
from pathlib import Path
from unittest.mock import patch, Mock
import mobile_agent as agent
import skill_store
import mcp_client

class SkillMcpTests(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory(); agent.configure(self.temp.name)
    def tearDown(self): self.temp.cleanup()

    def test_skills_list_read_and_safe_paths(self):
        root=Path(agent.skills_root()); skill=root/'docx'; (skill/'references').mkdir(parents=True)
        (skill/'SKILL.md').write_text('---\nname: docx\ndescription: Word documents\n---\nInstructions',encoding='utf-8')
        (skill/'references'/'guide.md').write_text('Reference',encoding='utf-8')
        self.assertEqual(json.loads(agent.list_skills())[0]['name'],'docx')
        self.assertIn('Instructions',agent._tool('skill_read',{'name':'docx'})['content'])
        self.assertEqual(agent._tool('skill_read',{'name':'docx','path':'references/guide.md'})['content'],'Reference')
        self.assertFalse(agent._tool('skill_read',{'name':'docx','path':'../../outside'})['success'])

    def test_install_skill_archive_without_path_escape(self):
        data=io.BytesIO()
        with tarfile.open(fileobj=data,mode='w:gz') as tar:
            for name,content in [('project-main/skills/doc/SKILL.md',b'---\nname: doc\n---\nReference skill'),('project-main/skills/doc/refs/a.md',b'guide'),('project-main/skills/doc/../../escape',b'bad')]:
                item=tarfile.TarInfo(name); item.size=len(content); tar.addfile(item,io.BytesIO(content))
        response=Mock(); response.status_code=200; response.iter_content.return_value=[data.getvalue()]; response.raise_for_status.return_value=None
        with patch('requests.get',return_value=response):
            result=skill_store.install_github(agent.skills_root(),'org/repo','skills/doc','main')
        self.assertEqual(result['name'],'doc'); self.assertTrue((Path(agent.skills_root())/'doc/refs/a.md').is_file())
        self.assertFalse((Path(agent.skills_root())/'escape').exists())
        with self.assertRaises(ValueError): skill_store.install_github(agent.skills_root(),'org/repo','skills/doc','main')

    def test_no_iteration_limit(self):
        sid=json.loads(agent.create_session('work'))['id']; provider=Mock(); provider.url=None
        action=json.dumps({'tool':'read_file','parameters':{'path':'file.txt'}})
        provider.send_message.side_effect=[{'success':True,'response':'```json\n'+action+'\n```'} for _ in range(25)]+[{'success':True,'response':'Terminé'}]
        with patch.object(agent,'SessionProvider',return_value=provider), patch.object(agent,'_tool',return_value={'success':True,'content':'ok'}):
            result=json.loads(agent.send(sid,'Travaille sur le dossier'))
        self.assertTrue(result['ok']); self.assertEqual(provider.send_message.call_count,26)

    def test_mcp_http_initialize_paginate_call_and_session(self):
        class Handler(BaseHTTPRequestHandler):
            calls=[]
            def log_message(self,*args): pass
            def do_POST(self):
                data=json.loads(self.rfile.read(int(self.headers['Content-Length'])))
                self.calls.append((data['method'],self.headers.get('Mcp-Session-Id'),self.headers.get('Authorization')))
                if data['method']=='notifications/initialized': self.send_response(202); self.end_headers(); return
                if data['method']=='initialize': result={'protocolVersion':'2025-11-25','capabilities':{},'serverInfo':{'name':'test','version':'1'}}
                elif data['method']=='tools/list':
                    if data['params'].get('cursor'): result={'tools':[{'name':'second'}]}
                    else: result={'tools':[{'name':'first'}],'nextCursor':'next'}
                elif data['method']=='tools/call': result={'content':[{'type':'text','text':'done'}]}
                else: result={}
                body=json.dumps({'jsonrpc':'2.0','id':data['id'],'result':result}).encode()
                if data['method']=='tools/call': body=b'data: '+body+b'\n\n'; ct='text/event-stream'
                else: ct='application/json'
                self.send_response(200); self.send_header('Mcp-Session-Id','test-session'); self.send_header('Content-Type',ct); self.send_header('Content-Length',str(len(body))); self.end_headers(); self.wfile.write(body)
        server=socketserver.TCPServer(('127.0.0.1',0),Handler)
        thread=threading.Thread(target=server.serve_forever,daemon=True); thread.start()
        try:
            client=mcp_client.McpClient('http','http://127.0.0.1:%d/mcp'%server.server_address[1],'id','secret-test')
            self.assertEqual([t['name'] for t in client.list_tools()],['first','second'])
            self.assertEqual(client.call_tool('first',{})['content'][0]['text'],'done')
            self.assertEqual(Handler.calls[1][1],'test-session')
            self.assertTrue(all(x[2]=='Bearer secret-test' for x in Handler.calls));client.close()
        finally: server.shutdown();server.server_close();thread.join()

    def test_mcp_registry_and_tools(self):
        server=json.loads(agent.mcp_save('Test','http','http://127.0.0.1:1234/mcp'))
        self.assertTrue(server['ok']); self.assertEqual(agent._tool('mcp_list',{})['servers'][0]['name'],'Test')
        client=Mock();client.list_tools.return_value=[{'name':'say'}];client.call_tool.return_value={'content':[{'type':'text','text':'ok'}]}
        with patch.object(agent,'_mcp_get',return_value=client):
            self.assertTrue(agent._tool('mcp_tools',{'server':'Test'})['success'])
            self.assertTrue(agent._tool('mcp_call',{'server':'Test','name':'say','arguments':{}})['success'])
        self.assertTrue(json.loads(agent.mcp_delete(server['id']))['ok'])
