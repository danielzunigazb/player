#!/usr/bin/env python3
"""Writes HISTORIAL-COMMITS.md: every commit with date, author, message, files and lines, grouped by
the app version in each commit and by published release. For the owner only: run it by hand and
keep the result private; it is never attached to releases, the website or the repo.

    python3 scripts/historial.py [salida.md]
"""
import subprocess, datetime, sys
def git(*a): return subprocess.run(['git',*a],capture_output=True,text=True,check=True).stdout
SEP='\x1e'; FS='\x1f'
fmt=FS.join(['%H','%h','%an','%ae','%ad','%cn','%cd','%P','%s','%b'])+SEP
def has(ref):
    return subprocess.run(['git','rev-parse','--verify','--quiet',ref],capture_output=True).returncode==0
MAIN='origin/main' if has('origin/main') else 'HEAD'
REFS=['HEAD'] + ([MAIN] if MAIN!='HEAD' else [])
in_main=set(git('rev-list',MAIN).split())
in_branch=set(git('rev-list','HEAD').split())
branch=git('rev-parse','--abbrev-ref','HEAD').strip()
raw=git('log',*REFS,'--date-order','--date=format:%Y-%m-%d %H:%M:%S %z',f'--format={fmt}')
commits=[c.strip('\n').split(FS) for c in raw.split(SEP) if c.strip()]

STATUS={'A':'añadido','M':'modificado','D':'borrado','R':'renombrado','C':'copiado','T':'tipo cambiado'}
def esc(t): return t.replace('|','\\|')
import re, json, urllib.request
def version_at(h):
    try:
        src=git('show',f'{h}:app/build.gradle.kts')
    except subprocess.CalledProcessError:
        return '—'
    m=re.search(r'versionName\s*=\s*"([^"]+)"',src)
    return m.group(1) if m else '—'
# Published releases, from GitHub (tag, date, commit), newest first.
try:
    with urllib.request.urlopen('https://api.github.com/repos/danielzunigazb/player/releases?per_page=20') as r:
        RELEASES=[x for x in json.load(r) if re.match(r'^v\d',x['tag_name'])]
except Exception:
    RELEASES=[]
REL_BY_COMMIT={x['target_commitish']:x['tag_name'] for x in RELEASES}
def files(h,parents):
    # A merge is compared with its first parent: what it brought into that branch.
    if parents:
        st=git('diff','--name-status','-M',parents[0],h); ns=git('diff','--numstat','-M',parents[0],h)
    else:
        st=git('show','--name-status','--format=','-M',h); ns=git('show','--numstat','--format=','-M',h)
    sts=[l.split('\t') for l in st.splitlines() if l.strip()]
    nums=[l.split('\t') for l in ns.splitlines() if l.strip()]
    return [(STATUS.get(s[0][0],s[0]),' → '.join(s[1:]),n[0],n[1]) for s,n in zip(sts,nums)]
COL=datetime.timezone(datetime.timedelta(hours=-5))
def fmtdate(d):
    # Every date in Colombia time (UTC−5), whatever zone the commit was made in.
    dt=datetime.datetime.strptime(d,'%Y-%m-%d %H:%M:%S %z').astimezone(COL)
    return dt.strftime('%Y-%m-%d %H:%M:%S')
rows=[]; details=[]; tot_add=tot_del=0; n=len(commits); by_version={}
for i,(H,h,an,ae,ad,cn,cd,P,subj,body) in enumerate(commits):
    parents=P.split() if P else []
    where=[w for w,ok in (('`main`',H in in_main),('`'+branch+'`',H in in_branch and branch not in ('main','HEAD'))) if ok]
    fl=files(H,parents); num=n-i; date=fmtdate(ad); merge=len(parents)>1
    adds=sum(int(a) for _,_,a,_ in fl if a.isdigit()); dels=sum(int(x) for _,_,_,x in fl if x.isdigit())
    if not merge:
        tot_add+=adds; tot_del+=dels
    kind=' (merge)' if merge else ''
    ver=version_at(H); by_version.setdefault(ver,[]).append((num,h,date,subj,H in in_main))
    tag=REL_BY_COMMIT.get(H); tagmark=f' 🏷 **{tag}**' if tag else ''
    rows.append(f'| {num} | {date[:16]} | [`{h}`](#{h}) | {ver} | {an} | {esc(subj)}{kind}{tagmark} | {len(fl)} | +{adds} / −{dels} |')
    d=[f'<a id="{h}"></a>',f'### {num}. {subj}','',f'- **Hash:** `{H}`',f'- **Fecha:** {date}',f'- **Autor:** {an} <{ae}>']
    if cn!=an or cd!=ad: d.append(f'- **Commit por:** {cn} · {fmtdate(cd)}')
    d.append('- **Padres:** '+(', '.join(f'`{p[:7]}`' for p in parents) if parents else 'ninguno (primer commit)'))
    d.append(f'- **Versión en el código:** {ver}')
    if tag: d.append(f'- **Release:** [{tag}](https://github.com/danielzunigazb/player/releases/tag/{tag}) se publicó desde este commit')
    d.append('- **Está en:** '+(' y '.join(where) if where else '—') + ('' if H in in_main else ' (todavía no está en `main`)'))
    if merge:
        d.append(f'- **Cambios que trajo** (respecto a `{parents[0][:7]}`): {len(fl)} archivo(s), +{adds} / −{dels} líneas')
    else:
        d.append(f'- **Cambios:** {len(fl)} archivo(s), +{adds} / −{dels} líneas')
    d+=['','**Mensaje:**','','```text',subj]+(['',body.strip()] if body.strip() else [])+['```','']
    if merge and not fl:
        d+=['_Merge que solo une historias: no cambia ningún archivo respecto a su primer padre._','']
    elif fl:
        d+=['| Estado | Archivo | + | − |','|---|---|---:|---:|']
        d+=[f'| {s} | `{p}` | {a if a!="-" else "bin"} | {x if x!="-" else "bin"} |' for s,p,a,x in fl]+['']
    d+=['---','']
    details.append('\n'.join(d))
def releases_md():
    if not RELEASES: return []
    out=['## Releases publicados','','| Versión | Publicado | Commit | Archivos |','|---|---|---|---|']
    for x in RELEASES:
        pub=datetime.datetime.strptime(x['published_at'],'%Y-%m-%dT%H:%M:%SZ').replace(tzinfo=datetime.timezone.utc).astimezone(COL).strftime('%Y-%m-%d %H:%M:%S')
        c=x['target_commitish'][:7]
        assets=', '.join(f'`{a["name"]}`' for a in x.get('assets',[]))
        out.append(f'| [{x["tag_name"]}](https://github.com/danielzunigazb/player/releases/tag/{x["tag_name"]}) | {pub} | [`{c}`](#{c}) | {assets} |')
    out+=['','Notas de cada versión en `docs/releases/`.','']
    return out
def versions_md():
    out=['## Commits por versión','','Agrupados por el `versionName` que tenía `app/build.gradle.kts` en cada commit. Los merges y squashes de `main` repiten trabajo que ya aparece en su versión.','']
    def key(v):
        try: return tuple(int(x) for x in v.split('.'))
        except ValueError: return (-1,)
    for v in sorted(by_version, key=key, reverse=True):
        items=sorted(by_version[v], key=lambda t:t[2])
        out.append(f'### {v} · {len(items)} commit(s) · {items[0][2][:16]} → {items[-1][2][:16]}')
        out.append('')
        out+= [f'- {d[:16]} · [`{h}`](#{h}) {esc(t)}' + ('' if m else ' · _todavía no está en `main`_') for _,h,d,t,m in items]
        out.append('')
    return out
authors={}
for c in commits: authors[c[2]]=authors.get(c[2],0)+1
now=datetime.datetime.now(COL).strftime('%Y-%m-%d %H:%M') + ' (hora de Colombia)'
merges=sum(1 for c in commits if len(c[7].split())>1)
md=['# Historial de commits: Player','',
f'Repositorio `danielzunigazb/player`, del commit más reciente al más antiguo. Generado el {now} con `scripts/historial.py`.','',
'## Resumen','',f'- **Commits:** {n} ({merges} merges)',f'- **Periodo:** {min(fmtdate(c[4]) for c in commits)} → {max(fmtdate(c[4]) for c in commits)}',
'- **Autores:** '+', '.join(f'{a} ({k})' for a,k in authors.items()),
f'- **Líneas:** +{tot_add} / −{tot_del} (suma de los commits sin merge; `main` guarda la misma obra en commits squash, así que hay trabajo contado dos veces)','',
'Fechas y horas en hora de Colombia (UTC−5).','',
*releases_md(),*versions_md(),'## Índice','','| # | Fecha | Commit | Versión | Autor | Título | Archivos | Líneas |','|---:|---|---|---|---|---|---:|---|',*rows,'','## Detalle','',*details]
out=sys.argv[1] if len(sys.argv)>1 else 'HISTORIAL-COMMITS.md'
open(out,'w').write('\n'.join(md)+'\n')
print(f'{out}: {n} commits, {merges} merges')
