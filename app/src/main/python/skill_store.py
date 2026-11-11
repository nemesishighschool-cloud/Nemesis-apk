"""Load and install standard Agent Skills, preserving upstream files unchanged."""
import re, shutil, tarfile, tempfile
from pathlib import Path, PurePosixPath


def _skill(root, name):
    root=Path(root).resolve()
    if not re.fullmatch(r'[A-Za-z0-9][A-Za-z0-9_.-]*', str(name)): raise ValueError('Nom de skill invalide')
    target=(root/str(name)).resolve()
    if root not in target.parents or not (target/'SKILL.md').is_file(): raise ValueError('Skill introuvable')
    return target


def list_skills(root):
    root=Path(root); root.mkdir(parents=True,exist_ok=True); rows=[]
    for folder in sorted(root.iterdir()):
        if not folder.is_dir() or folder.is_symlink() or not (folder/'SKILL.md').is_file(): continue
        content=(folder/'SKILL.md').read_text(encoding='utf-8',errors='replace')
        m=re.search(r'^description:\s*(.*)$',content,re.M)
        description=(m.group(1).strip().strip('"\'') if m else '')
        rows.append({'name':folder.name,'description':description})
    return rows


def read_skill(root, name, path='SKILL.md'):
    folder=_skill(root,name)
    if not isinstance(path,str) or not path: raise ValueError('Chemin de référence invalide')
    part=Path(path)
    if part.is_absolute(): raise ValueError('Chemin absolu interdit')
    target=(folder/part).resolve()
    if folder not in target.parents or not target.is_file(): raise ValueError('Référence du skill introuvable')
    if target.is_symlink(): raise ValueError('Lien interdit')
    return {'name':name,'path':path,'content':target.read_text(encoding='utf-8')}


def install_github(root, repo, path='', ref='main', name=None):
    """Copy one skill directory from a GitHub public repo without executing it."""
    import requests
    root=Path(root).resolve(); root.mkdir(parents=True,exist_ok=True)
    if not re.fullmatch(r'[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+',str(repo)): raise ValueError('Dépôt attendu : propriétaire/dépôt')
    if not re.fullmatch(r'[A-Za-z0-9_./-]+',str(ref)) or '..' in PurePosixPath(ref).parts: raise ValueError('Révision Git invalide')
    path=str(path).strip('/')
    parts=PurePosixPath(path).parts if path else ()
    if any(p in ('.','..') for p in parts): raise ValueError('Chemin de skill invalide')
    name=str(name or (parts[-1] if parts else repo.split('/')[-1]))
    if not re.fullmatch(r'[A-Za-z0-9][A-Za-z0-9_.-]*',name): raise ValueError('Nom de skill invalide')
    target=root/name
    if target.exists(): raise ValueError('Ce skill existe déjà ; aucun fichier existant ne sera écrasé')
    url='https://codeload.github.com/'+repo+'/tar.gz/'+ref
    with tempfile.TemporaryDirectory(prefix='.install-',dir=root) as tmp:
        archive=Path(tmp)/'source.tar.gz'; stage=Path(tmp)/'skill'; stage.mkdir()
        response=requests.get(url,stream=True,timeout=(15,120),allow_redirects=False)
        response.raise_for_status()
        if response.status_code!=200: raise ValueError('Téléchargement GitHub non disponible')
        with archive.open('wb') as out:
            for chunk in response.iter_content(65536):
                if chunk: out.write(chunk)
        with tarfile.open(archive,'r:gz') as tar:
            for member in tar:
                member_parts=PurePosixPath(member.name).parts
                if not member_parts or '..' in member_parts or member.name.startswith('/'): continue
                relative=member_parts[1:]
                if tuple(relative[:len(parts)]) != tuple(parts): continue
                sub=relative[len(parts):]
                if not sub or not member.isfile(): continue
                dest=stage.joinpath(*sub)
                if stage not in dest.resolve().parents: raise ValueError('Archive contenant un chemin invalide')
                dest.parent.mkdir(parents=True,exist_ok=True)
                src=tar.extractfile(member)
                if src is None: continue
                with src, dest.open('wb') as output: shutil.copyfileobj(src,output)
        if not (stage/'SKILL.md').is_file(): raise ValueError('Le dossier sélectionné ne contient pas SKILL.md')
        stage.rename(target)
    return {'name':name,'source':'https://github.com/'+repo+'/tree/'+ref+('/'+path if path else '')}
