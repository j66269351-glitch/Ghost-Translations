"""Reads input/ (app.zip OR link.txt, optional name.txt, icon.png) and prepares the Android project."""
import os, re, sys, glob, shutil, zipfile, tempfile
from xml.sax.saxutils import escape
from PIL import Image

INP, ASSETS, RES = 'input', 'app/src/main/assets', 'app/src/main/res'

def read(name, default=''):
    p = os.path.join(INP, name)
    return open(p, encoding='utf-8').read().strip() if os.path.exists(p) else default

name = read('name.txt', 'My App') or 'My App'
slug = re.sub(r'[^a-z0-9]', '', name.lower()) or 'app'
if slug[0].isdigit(): slug = 'a' + slug
app_id = 'studio.j.' + slug
link = read('link.txt')

# clean assets except the bridge
for f in os.listdir(ASSETS):
    if f != 'android-bridge.js':
        p = os.path.join(ASSETS, f)
        shutil.rmtree(p) if os.path.isdir(p) else os.remove(p)

web_root = None
if link:
    if not re.match(r'https?://', link): link = 'https://' + link
    open(os.path.join(ASSETS, 'url.txt'), 'w').write(link)
    print('MODE: link ->', link)
else:
    zips = sorted(glob.glob(os.path.join(INP, '*.zip')))
    if not zips:
        sys.exit('ERROR: input/ me app.zip ya link.txt daalo.')
    tmp = tempfile.mkdtemp()
    with zipfile.ZipFile(zips[0]) as z: z.extractall(tmp)
    cands = []
    for d, _, files in os.walk(tmp):
        if '__MACOSX' in d: continue
        if 'index.html' in files: cands.append(d)
    if not cands:
        sys.exit('ERROR: zip ke andar index.html nahi mili. Web app ki zip daalo.')
    web_root = min(cands, key=lambda d: d.count(os.sep))
    for item in os.listdir(web_root):
        if item == '__MACOSX': continue
        s, t = os.path.join(web_root, item), os.path.join(ASSETS, item)
        shutil.copytree(s, t) if os.path.isdir(s) else shutil.copy2(s, t)
    print('MODE: zip ->', zips[0], '(root:', os.path.relpath(web_root, tmp), ')')

# app name
open(os.path.join(RES, 'values', 'strings.xml'), 'w', encoding='utf-8').write(
    '<resources><string name="app_name">%s</string></resources>' % escape(name))

# icon
icon = None
if os.path.exists(os.path.join(INP, 'icon.png')): icon = os.path.join(INP, 'icon.png')
elif web_root:
    for c in ['icons/icon-512.png','icons/icon-192.png','icon-512.png','icon-192.png','icon.png','logo.png','favicon.png']:
        if os.path.exists(os.path.join(web_root, c)): icon = os.path.join(web_root, c); break
if icon:
    src = Image.open(icon).convert('RGBA')
    for d, s in {'mdpi':48,'hdpi':72,'xhdpi':96,'xxhdpi':144,'xxxhdpi':192}.items():
        os.makedirs(f'{RES}/mipmap-{d}', exist_ok=True)
        src.resize((s, s), Image.LANCZOS).save(f'{RES}/mipmap-{d}/ic_launcher.png')
    print('ICON:', icon)

with open(os.environ.get('GITHUB_ENV', os.devnull), 'a') as f:
    f.write(f'APP_ID={app_id}\n')
print('APP ID:', app_id, '| NAME:', name)
