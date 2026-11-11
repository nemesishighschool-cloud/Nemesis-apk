import json, tempfile, unittest, socket, io, base64
from pathlib import Path
from unittest.mock import patch, Mock
import mobile_agent as m

class MobileTests(unittest.TestCase):
    def setUp(self): self.temp = tempfile.TemporaryDirectory(); m.configure(self.temp.name)
    def tearDown(self): self.temp.cleanup()
    def test_sessions_are_persistent_and_distinct(self):
        a = json.loads(m.create_session('chat'))['id']; b = json.loads(m.create_session('code'))['id']
        self.assertNotEqual(a, b); self.assertEqual(len(json.loads(m.list_sessions())), 2)
        self.assertEqual(json.loads(m.get_session(a))['mode'], 'chat')
        m.configure(self.temp.name); self.assertEqual(len(json.loads(m.list_sessions())), 2)
    def test_web_search_reports_bot_challenge_as_error(self):
        from src.core.tools import web_search as search_module
        response = __import__("unittest.mock", fromlist=["MagicMock"]).MagicMock(); response.status = 202
        response.read.return_value = b"<html>anomaly-modal challenge</html>"
        response.__enter__ = Mock(return_value=response); response.__exit__ = Mock(return_value=False)
        with patch.object(search_module, "_ddg_search", side_effect=ImportError), \
             patch.object(search_module.urllib.request, "urlopen", return_value=response):
            result = m._tool("web_search", {"query":"NASA Artemis"})
        self.assertFalse(result["success"])
        self.assertIn("anti-robot", result["error"])

    def test_memory_enabled_by_default_crud_and_opt_out(self):
        self.assertTrue(json.loads(m.get_preferences())['memory_enabled'])
        saved=m._tool('memory_add',{'content':'Préfère le français'})
        self.assertTrue(saved['success']); key=saved['id']
        self.assertFalse(m._tool('memory_add',{'content':'Préfère le français'})['changed'])
        self.assertTrue(m._tool('memory_update',{'id':key,'content':'Préfère le français clair'})['success'])
        self.assertEqual(len(m._tool('memory_search',{'query':'FRANÇAIS'})['memories']),1)
        for mode in ('chat','work','code'):
            prompt=m._system_prompt(mode)
            self.assertIn('Préfère le français clair',prompt)
            self.assertIn(key,prompt)
        self.assertTrue(m._tool('memory_delete',{'id':key})['success'])
        m.save_preferences('',False)
        self.assertFalse(m._tool('memory_add',{'content':'ne pas garder'})['success'])
        self.assertFalse(m._tool('memory_get',{})['success'])

    def test_chat_can_call_memory_but_not_bash(self):
        sid=json.loads(m.create_session('chat'))['id']; provider=Mock();provider.url=None
        provider.send_message.side_effect=[{'success':True,'response':'```json\n{"tool":"memory_add","parameters":{"content":"Aime le café"}}\n```'}, {'success':True,'response':'Bonjour !'}]
        with patch.object(m,'SessionProvider',return_value=provider):
            result=json.loads(m.send(sid,'Bonjour'))
        self.assertTrue(result['ok']);self.assertEqual(len(json.loads(m.list_memory())),1)
        self.assertIn('mémoire',provider.send_message.call_args_list[0].kwargs['system_prefix'].lower())
        sid=json.loads(m.create_session('chat'))['id']; provider=Mock();provider.url=None
        provider.send_message.side_effect=[{'success':True,'response':'```json\n{"tool":"bash","parameters":{"command":"echo X"}}\n```'},{'success':True,'response':'OK'}]
        with patch.object(m,'SessionProvider',return_value=provider):
            self.assertTrue(json.loads(m.send(sid,'Test'))['ok'])
        tool=json.loads([x['text'] for x in json.loads(m.get_session(sid))['messages'] if x['role']=='tool'][0])
        self.assertFalse(tool['result']['success'])

    def test_old_tool_notice_is_removed_without_losing_response(self):
        sid=json.loads(m.create_session('work'))['id']
        m._message(sid, 'assistant', 'Réponse utile.\n\nAucun outil utilisé pour cette réponse.')
        m.configure(self.temp.name)
        self.assertEqual(json.loads(m.get_session(sid))['messages'][0]['text'],'Réponse utile.')

    def test_memory_in_first_message_of_each_new_mode(self):
        m.add_memory('Préfère un format court')
        for mode in ('chat','work','code'):
            sid=json.loads(m.create_session(mode))['id']; p=Mock();p.url=None
            p.send_message.return_value={'success':True,'response':'Bonjour'}
            with patch.object(m,'SessionProvider',return_value=p):
                self.assertTrue(json.loads(m.send(sid,'Salut'))['ok'])
            self.assertIn('Préfère un format court',p.send_message.call_args.kwargs['system_prefix'])

    def test_provider_uses_configured_private_endpoint(self):
        m.configure(self.temp.name, 43210, 'random-test-token')
        sid=json.loads(m.create_session('chat'))['id']
        provider=m.SessionProvider(sid,None)
        self.assertEqual(provider.base_url,'http://127.0.0.1:43210')
        self.assertEqual(provider._auth_headers()['Authorization'],'Bearer random-test-token')

    def test_automatic_memory_guidance_is_present_in_every_mode(self):
        for mode in ('chat','work','code'):
            prompt=m._system_prompt(mode)
            self.assertIn('appelle memory_add dans ce même tour',prompt)
            self.assertIn('sans appel réussi, rien n’a été mémorisé',prompt)

    def test_work_and_code_accept_memory_tool_calls(self):
        for mode in ('work','code'):
            sid=json.loads(m.create_session(mode))['id']; provider=Mock(); provider.url=None
            action=json.dumps({'tool':'memory_add','parameters':{'content':'Preference '+mode}})
            provider.send_message.side_effect=[{'success':True,'response':'```json\n'+action+'\n```'}, {'success':True,'response':'Merci'}]
            with patch.object(m,'SessionProvider',return_value=provider):
                self.assertTrue(json.loads(m.send(sid,'Bonjour'))['ok'])
            self.assertTrue(any(item['content']=='Preference '+mode for item in json.loads(m.list_memory())))

    def test_workspace_prevents_escape_without_confirmation(self):
        self.assertFalse(m._tool('write_file', {'path':'../../outside', 'content':'x'})['success'])
        self.assertTrue(m._tool('write_file', {'path':'test.txt','content':'hello'})['success'])
        self.assertEqual(m._tool('read_file', {'path':'test.txt'})['content'], 'hello')
        self.assertFalse(m._tool('bash', {'command':'pwd'})['success'])

    def test_shared_workspaces_are_isolated_by_mode_and_session(self):
        shared=Path(self.temp.name)/'external'/'NEMESIS'
        m.configure(self.temp.name,8090,'',str(shared))
        a=json.loads(m.create_session('work'))['id']; b=json.loads(m.create_session('code'))['id']; c=json.loads(m.create_session('work'))['id']
        for mode in ('Work','Code'): self.assertTrue((shared/mode).is_dir())
        self.assertTrue(m._tool('write_file',{'path':'note.txt','content':'work'},a)['success'])
        self.assertTrue(m._tool('write_file',{'path':'note.txt','content':'code'},b)['success'])
        self.assertEqual((shared/'Work'/a/'note.txt').read_text(),'work')
        self.assertEqual((shared/'Code'/b/'note.txt').read_text(),'code')
        self.assertEqual(json.loads(m.list_files(c)),[])
        self.assertFalse(m._tool('write_file',{'path':'../escape','content':'x'},a)['success'])
        outside=Path(self.temp.name)/'outside'; outside.mkdir(); (shared/'Work'/a/'link').symlink_to(outside, target_is_directory=True)
        self.assertFalse(m._tool('write_file',{'path':'link/escape','content':'x'},a)['success'])

    def test_tool_message_is_visible_while_running(self):
        sid=json.loads(m.create_session('work'))['id']; provider=Mock(); provider.url=None
        action=json.dumps({'tool':'write_file','parameters':{'path':'report.txt','content':'ok'}})
        provider.send_message.side_effect=[{'success':True,'response':'```json\n'+action+'\n```'}, {'success':True,'response':'Terminé'}]
        original=m._tool
        def run(name,args,session):
            recorded=json.loads(m.get_session(sid))['messages']
            tool=json.loads([x['text'] for x in recorded if x['role']=='tool'][-1])
            self.assertTrue(tool['result']['pending'])
            return original(name,args,session)
        with patch.object(m,'SessionProvider',return_value=provider), patch.object(m,'_tool',side_effect=run):
            self.assertTrue(json.loads(m.send(sid,'Créer un rapport'))['ok'])
        recorded=json.loads(m.get_session(sid))['messages']; tool=json.loads([x['text'] for x in recorded if x['role']=='tool'][-1])
        self.assertTrue(tool['result']['success'])

    def test_session_url_must_be_confirmed(self):
        sid=json.loads(m.create_session('chat'))['id']
        with m._db() as db: db.execute('UPDATE sessions SET uncertain=1 WHERE id=?',(sid,))
        self.assertFalse(json.loads(m.send(sid,'hello'))['ok'])

    def test_fetch_blocks_private_and_mixed_dns(self):
        public = (socket.AF_INET, socket.SOCK_STREAM, 6, '', ('93.184.216.34',443))
        private = (socket.AF_INET, socket.SOCK_STREAM, 6, '', ('127.0.0.1',443))
        for addresses in ([private], [public, private]):
            with patch.object(m.socket, 'getaddrinfo', return_value=addresses):
                self.assertFalse(m._tool('web_fetch', {'url':'https://example.com/'})['success'])
        self.assertFalse(m._tool('web_fetch', {'url':'http://example.com/'})['success'])
        self.assertFalse(m._tool('web_fetch', {'url':'https://example.com:8080/'})['success'])
    def test_fetch_connects_to_validated_ip_not_second_dns(self):
        addresses = [(socket.AF_INET, socket.SOCK_STREAM, 6, '', ('93.184.216.34',443))]
        sock = Mock()
        sock.makefile.return_value = io.BytesIO(b'HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\nContent-Length: 2\r\n\r\nOK')
        context = Mock(); context.wrap_socket.return_value = sock
        with patch.object(m.socket, 'getaddrinfo', return_value=addresses) as dns, \
             patch.object(m.socket, 'socket', return_value=sock), \
             patch.object(m.ssl, 'create_default_context', return_value=context):
            result = m._tool('web_fetch', {'url':'https://example.com/path'})
        self.assertEqual(result.get('content'), 'OK')
        dns.assert_called_once(); sock.connect.assert_called_once_with(('93.184.216.34',443))
        context.wrap_socket.assert_called_once_with(sock, server_hostname='example.com')

    def test_long_ssh_command_runs_without_approval_dialog(self):
        import sys, types
        bridge=Mock(); bridge.execute.return_value=json.dumps({'success':True,'output':'ok'})
        fake=types.SimpleNamespace(jclass=Mock(return_value=bridge))
        with patch.dict(sys.modules, {'java':fake}):
            result=m._tool('bash',{'command':'echo '+('x'*2000)})
        self.assertTrue(result['success']); bridge.execute.assert_called_once()

    def test_sftp_tool_stays_in_session_workspace(self):
        import sys, types
        shared=Path(self.temp.name)/'shared'; m.configure(self.temp.name,8090,'',str(shared))
        sid=json.loads(m.create_session('work'))['id']
        bridge=Mock(); bridge.download.return_value=json.dumps({'success':True,'size':7})
        with patch.dict(sys.modules, {'java':types.SimpleNamespace(jclass=Mock(return_value=bridge))}):
            result=m._tool('download_file',{'remote_path':'/workspace/result.pdf','path':'docs/result.pdf'},sid)
            denied=m._tool('download_file',{'remote_path':'/workspace/result.pdf','path':'../escape'},sid)
        self.assertTrue(result['success']);self.assertEqual(result['path'],'docs/result.pdf')
        self.assertFalse(denied['success']); bridge.download.assert_called_once()

    def test_ssh_rejects_unsafe_command_encoding(self):
        self.assertFalse(m._tool('bash', {'command':'echo ok\u202eevil'})['success'])
    def test_work_response_has_no_unrequested_tool_notice(self):
        sid = json.loads(m.create_session('work'))['id']
        provider = Mock(); provider.url = None
        provider.send_message.return_value = {'success':True, 'response':'Voici le résultat.'}
        with patch.object(m, 'SessionProvider', return_value=provider):
            result = json.loads(m.send(sid, 'bonjour'))
        self.assertTrue(result['ok'])
        self.assertNotIn('Aucun outil utilisé', result['text'])

    def test_partial_stream_hides_tool_calls(self):
        raw = 'Je lis le fichier maintenant.\n```json\n{"tool":"read_file","parameters":{"path":"secret"}}\n```'
        live = m._safe_partial(raw)
        self.assertIn('Je lis', live); self.assertNotIn('secret', live); self.assertNotIn('```', live)
    def test_text_attachment_is_passed_to_provider(self):
        sid = json.loads(m.create_session('chat'))['id']; folder=Path(self.temp.name)/'attachments'; folder.mkdir()
        file=folder/'a'; file.write_text('ATTACHMENT_TEST_TOKEN', encoding='utf-8')
        provider = Mock(); provider.url = None; provider.send_message.return_value={'success':True,'response':'OK'}
        with patch.object(m, 'SessionProvider', return_value=provider):
            result=json.loads(m.send(sid,'Lis le document',json.dumps([{'path':str(file),'name':'notes.txt','mime':'text/plain'}])))
        self.assertTrue(result['ok']); part=provider.send_message.call_args.kwargs['images'][0]
        self.assertEqual(part['type'],'file'); self.assertEqual(part['file']['filename'],'notes.txt')
        self.assertEqual(base64.b64decode(part['file']['file_data'].split(',',1)[1]),b'ATTACHMENT_TEST_TOKEN')
    def test_pdf_and_office_attachments_use_native_file_parts(self):
        sid=json.loads(m.create_session('work'))['id']; folder=Path(self.temp.name)/'attachments'; folder.mkdir()
        files=[]
        for i,(name,mime,contents) in enumerate([('document.pdf','application/pdf',b'%PDF-1.4 sample'),('rapport.docx','application/vnd.openxmlformats-officedocument.wordprocessingml.document',b'PK sample')]):
            path=folder/str(i); path.write_bytes(contents); files.append({'path':str(path),'name':name,'mime':mime})
        provider=Mock(); provider.url=None; provider.send_message.return_value={'success':True,'response':'OK'}
        with patch.object(m,'SessionProvider',return_value=provider): result=json.loads(m.send(sid,'Analyse',json.dumps(files)))
        self.assertTrue(result['ok']); parts=provider.send_message.call_args.kwargs['images']
        self.assertEqual([part['type'] for part in parts],['file','file'])
        self.assertEqual([part['file']['filename'] for part in parts],['document.pdf','rapport.docx'])
        self.assertEqual(base64.b64decode(parts[0]['file']['file_data'].split(',',1)[1]),b'%PDF-1.4 sample')

    def test_uncertain_session_can_only_be_explicitly_recovered_using_valid_url(self):
        sid=json.loads(m.create_session('work'))['id']; url='https://chat.deepseek.com/a/chat/s/testabc123'
        with m._db() as db: db.execute('UPDATE sessions SET conversation_url=?,uncertain=1 WHERE id=?',(url,sid))
        self.assertFalse(json.loads(m.recover_session(sid,'https://evil.example/'))['ok'])
        self.assertEqual(json.loads(m.get_session(sid))['uncertain'],1)
        self.assertTrue(json.loads(m.recover_session(sid))['ok']); self.assertEqual(json.loads(m.get_session(sid))['uncertain'],0)
        sid2=json.loads(m.create_session('chat'))['id']
        with m._db() as db: db.execute('UPDATE sessions SET uncertain=1 WHERE id=?',(sid2,))
        self.assertFalse(json.loads(m.recover_session(sid2))['ok'])
        self.assertTrue(json.loads(m.recover_session(sid2,url))['ok'])

    def test_attachment_escape_is_rejected(self):
        sid=json.loads(m.create_session('chat'))['id']; path=Path(self.temp.name)/'private'; path.write_text('secret')
        result=json.loads(m.send(sid,'lis',json.dumps([{'path':str(path),'name':'x','mime':'text/plain'}])))
        self.assertFalse(result['ok'])

    def test_stream_commits_only_a_confirmed_conversation(self):
        sid=json.loads(m.create_session('chat'))['id']; provider=m.SessionProvider(sid,None)
        url='https://chat.deepseek.com/a/chat/s/testabc123'
        events=[{'choices':[{'delta':{'content':'Bonjour en direct !'}}]}, {'nemesis_session':{'conversation_url':url,'url_captured':True}}]
        response=__import__("unittest.mock", fromlist=["MagicMock"]).MagicMock(); response.status_code=200; response.__enter__.return_value=response; response.__exit__.return_value=None
        response.iter_lines.return_value=['data: '+json.dumps(x) for x in events]+['data: [DONE]']
        with patch.object(provider._session, 'post', return_value=response):
            result=provider._post_cancellable({'messages':[]},None)
        self.assertEqual(result.json()['choices'][0]['message']['content'],'Bonjour en direct !')
        self.assertEqual(json.loads(m.get_session(sid))['conversation_url'],url)
    def test_stream_progress_updates_before_completion(self):
        sid=json.loads(m.create_session('chat'))['id']; provider=m.SessionProvider(sid,None)
        url='https://chat.deepseek.com/a/chat/s/testabc123'
        events=[{'choices':[{'delta':{'content':'Bonjour, ceci est la première partie. '}}]}, {'choices':[{'delta':{'content':'Voici la deuxième partie en direct.'}}]}, {'nemesis_session':{'conversation_url':url,'url_captured':True}}]
        response=__import__('unittest.mock',fromlist=['MagicMock']).MagicMock(); response.status_code=200; response.__enter__.return_value=response;response.__exit__.return_value=None
        response.iter_lines.return_value=['data: '+json.dumps(event) for event in events]+['data: [DONE]']
        with patch.object(provider._session,'post',return_value=response), patch.object(m.time,'monotonic',side_effect=[1.0,1.3]), patch.object(m,'_set_live',wraps=m._set_live) as progress:
            result=provider._post_cancellable({'messages':[]},None)
        chunks=[call.args[1] for call in progress.call_args_list]
        self.assertGreaterEqual(len(chunks),3)
        self.assertTrue(chunks[-2]); self.assertGreater(len(chunks[-1]),len(chunks[-2]))
        self.assertIn('deuxième partie',result.json()['choices'][0]['message']['content'])

    def test_stream_without_metadata_marks_session_uncertain(self):
        sid=json.loads(m.create_session('chat'))['id']; provider=m.SessionProvider(sid,None)
        response=__import__("unittest.mock", fromlist=["MagicMock"]).MagicMock(); response.status_code=200; response.__enter__.return_value=response; response.__exit__.return_value=None
        response.iter_lines.return_value=['data: [DONE]']
        with patch.object(provider._session, 'post', return_value=response):
            with self.assertRaises(RuntimeError): provider._post_cancellable({'messages':[]},None)
        self.assertEqual(json.loads(m.get_session(sid))['uncertain'],1)

    def test_stream_error_before_submit_does_not_lock_session(self):
        sid=json.loads(m.create_session('chat'))['id']; provider=m.SessionProvider(sid,None)
        response=__import__('unittest.mock',fromlist=['MagicMock']).MagicMock(); response.status_code=200; response.__enter__.return_value=response; response.__exit__.return_value=None
        response.iter_lines.return_value=['data: '+json.dumps({'error':{'message':'fichier refusé'},'not_submitted':True}),'data: [DONE]']
        with patch.object(provider._session,'post',return_value=response):
            with self.assertRaises(m._BeforeSubmitError): provider._post_cancellable({'messages':[]},None)
        self.assertEqual(json.loads(m.get_session(sid))['uncertain'],0)

    def test_navigation_failure_before_submit_does_not_lock_session(self):
        sid=json.loads(m.create_session('chat'))['id']; provider=m.SessionProvider(sid,None)
        response=__import__("unittest.mock",fromlist=["MagicMock"]).MagicMock(); response.status_code=504
        response.__enter__.return_value=response; response.__exit__.return_value=None
        response.json.return_value={'error':{'message':"Conversation DeepSeek non prête ; aucun message n'a été envoyé."}}
        with patch.object(provider._session, 'post', return_value=response): provider._post_cancellable({'messages':[]},None)
        self.assertEqual(json.loads(m.get_session(sid))['uncertain'],0)

    def test_navigation_503_with_explicit_not_submitted_unlocks_session(self):
        sid=json.loads(m.create_session('chat'))['id']; provider=m.SessionProvider(sid,None)
        response=__import__('unittest.mock',fromlist=['MagicMock']).MagicMock(); response.status_code=503
        response.__enter__.return_value=response; response.__exit__.return_value=None
        response.json.return_value={'error':{'message':'page non prête'},'not_submitted':True}
        with patch.object(provider._session,'post',return_value=response): provider._post_cancellable({'messages':[]},None)
        self.assertEqual(json.loads(m.get_session(sid))['uncertain'],0)

    def test_navigation_503_without_proof_remains_uncertain(self):
        sid=json.loads(m.create_session('chat'))['id']; provider=m.SessionProvider(sid,None)
        response=__import__('unittest.mock',fromlist=['MagicMock']).MagicMock(); response.status_code=503
        response.__enter__.return_value=response; response.__exit__.return_value=None
        response.json.return_value={'error':{'message':'erreur inconnue'}}
        with patch.object(provider._session,'post',return_value=response): provider._post_cancellable({'messages':[]},None)
        self.assertEqual(json.loads(m.get_session(sid))['uncertain'],1)

    def test_rename_pin_and_persistence(self):
        a=json.loads(m.create_session('chat'))['id']; b=json.loads(m.create_session('work'))['id']
        self.assertTrue(json.loads(m.rename_session(a,'Projet'))['ok']); self.assertTrue(json.loads(m.pin_session(a,True))['ok'])
        m.configure(self.temp.name); sessions=json.loads(m.list_sessions())
        self.assertEqual(sessions[0]['id'],a); self.assertEqual(sessions[0]['title'],'Projet'); self.assertEqual(sessions[0]['pinned'],1)
    def test_memory_and_preferences(self):
        self.assertTrue(json.loads(m.add_memory('Préfère des réponses courtes'))['ok'])
        m.save_preferences('Réponds en français',True); prompt=m._system_prompt('work')
        self.assertIn('Préfère des réponses courtes',prompt); self.assertIn('Réponds en français',prompt)
        m.configure(self.temp.name); self.assertEqual(len(json.loads(m.list_memory())),1)
        m.save_preferences('',False); self.assertNotIn('Préfère des réponses courtes',m._system_prompt('chat'))
    def test_code_file_tools_and_protection(self):
        ws=Path(self.temp.name)/'workspace'; (ws/'a.py').write_text('alpha\nbeta\n')
        self.assertIn('a.py',m._tool('glob',{'pattern':'*.py'})['files'])
        self.assertIn('a.py:2',m._tool('grep',{'pattern':'beta'})['matches'][0])
        self.assertTrue(m._tool('append',{'path':'a.py','content':'gamma'})['success'])
        self.assertTrue(m._tool('delete_file',{'path':'a.py'})['success'])
        self.assertFalse(m._tool('delete_file',{'path':'../outside'})['success'])
    def test_code_todos_are_session_scoped(self):
        a=json.loads(m.create_session('code'))['id']; b=json.loads(m.create_session('code'))['id']
        self.assertEqual(len(m._tool('todo',{'action':'add','content':'Tester'},a)['tasks']),1)
        self.assertEqual(m._tool('todo',{'action':'list'},b)['tasks'],[])
    def test_old_session_never_resends_system_prompt(self):
        sid=json.loads(m.create_session('work'))['id']; url='https://chat.deepseek.com/a/chat/s/testabc123'
        with m._db() as db: db.execute('UPDATE sessions SET conversation_url=? WHERE id=?',(url,sid))
        provider=Mock(); provider.url=url; provider.send_message.return_value={'success':True,'response':'OK'}
        with patch.object(m,'SessionProvider',return_value=provider):
            for text in ('Premier','Second'):
                m.save_preferences('Nouvelles préférences',True)
                self.assertTrue(json.loads(m.send(sid,text))['ok'])
                call=provider.send_message.call_args
                self.assertEqual(text,call.args[0]); self.assertEqual('',call.kwargs['system_prefix'])
        messages=json.loads(m.get_session(sid))['messages']; answer=messages[-1]
        self.assertEqual(answer['role'],'assistant'); self.assertIsNotNone(answer['duration_ms'])
        self.assertTrue(json.loads(m.rate_message(sid,answer['id'],1))['ok'])
        self.assertEqual(json.loads(m.get_session(sid))['messages'][-1]['rating'],1)
        self.assertFalse(json.loads(m.rate_message('other',answer['id'],-1))['ok'])
        self.assertFalse(json.loads(m.rate_message(sid,messages[0]['id'],1))['ok'])
        self.assertFalse(json.loads(m.rate_message(sid,answer['id'],3))['ok'])

if __name__ == '__main__': unittest.main()
