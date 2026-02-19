import pathlib
import re
path = pathlib.Path('hexnovels_live.log')
data = path.read_text(errors='ignore')
for match in re.finditer('novelimg', data, flags=re.I):
    start = max(0, match.start()-60)
    end = min(len(data), match.end()+60)
    snippet = data[start:end].replace('\n', ' ')
    print('line', data.count('\n',0, match.start())+1, snippet)
