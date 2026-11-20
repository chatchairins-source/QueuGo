"""Outline launcher wordmarks so Android/SVG exports never depend on device fonts."""
import json
import os
from pathlib import Path
from fontTools.ttLib import TTFont
from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.pens.transformPen import TransformPen

font = TTFont(os.environ.get('QG_BRAND_FONT', '/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf'))
glyphs = font.getGlyphSet()
cmap = font.getBestCmap()
result = {}
for role, text in [('customer', 'QueueGo'), ('merchant', 'QueueMarket'), ('rider', 'Queue Rider')]:
    advance = sum(font['hmtx'][cmap[ord(c)]][0] for c in text)
    scale = min(7.8 / font['head'].unitsPerEm, 49 / advance)
    pen = SVGPathPen(glyphs)
    x = 54 - advance * scale / 2
    for char in text:
        name = cmap[ord(char)]
        glyphs[name].draw(TransformPen(pen, (scale, 0, 0, -scale, x, 75)))
        x += font['hmtx'][name][0] * scale
    result[role] = {'name': text, 'path': pen.getCommands()}
Path(__file__).with_name('wordmarks.json').write_text(json.dumps(result, indent=2) + '\n')
