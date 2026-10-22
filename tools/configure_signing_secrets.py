"""Upload signing values to GitHub Secrets without logging values or adding newlines."""
from pathlib import Path
import argparse, base64, subprocess

parser=argparse.ArgumentParser()
parser.add_argument('--repo',required=True)
parser.add_argument('--properties',type=Path,default=Path(__file__).resolve().parents[1]/'keystore.properties')
args=parser.parse_args()
values=dict(line.split('=',1) for line in args.properties.read_text(encoding='utf-8-sig').splitlines() if '=' in line and not line.lstrip().startswith('#'))
secrets={'KEYSTORE_BASE64':base64.b64encode(Path(values['KEYSTORE_PATH']).read_bytes())}
secrets.update({name:values[name].encode() for name in ['KEYSTORE_PASSWORD','KEY_ALIAS','KEY_PASSWORD']})
for name,value in secrets.items():
    result=subprocess.run(['gh','secret','set',name,'--repo',args.repo],input=value,capture_output=True,timeout=60)
    if result.returncode: raise RuntimeError(f'Failed to set {name}: {result.stderr.decode(errors="replace")}')
    print(f'Configured {name}',flush=True)
