"""Resolve the actual Rider debug identity from Gradle signingReport, never release keys."""
from pathlib import Path
import re
import sys


def resolve(report: str) -> tuple[Path, str]:
    blocks = re.split(r'^Variant: ', report, flags=re.MULTILINE)
    debug = [block for block in blocks[1:] if block.splitlines()[0].strip() == 'debug']
    if len(debug) != 1:
        raise ValueError('Expected exactly one Rider debug signing variant')
    values = {}
    for label in ('Config', 'Store', 'Alias'):
        matches = re.findall(r'^' + label + r': (.+)$', debug[0], flags=re.MULTILINE)
        if len(matches) != 1:
            raise ValueError('Missing or ambiguous Rider debug signing ' + label)
        values[label] = matches[0].strip()
    if values['Config'] != 'debug' or any('\n' in value or '\r' in value for value in values.values()):
        raise ValueError('Invalid Rider debug signing identity')
    store = Path(values['Store']).expanduser().resolve()
    if not store.is_file() or store.stat().st_size == 0 or values['Alias'].lower() == 'null':
        raise ValueError('Reported Rider debug keystore/alias is unavailable')
    return store, values['Alias']


if __name__ == '__main__':
    store, alias = resolve(Path(sys.argv[1]).read_text())
    print('QG_QA_DEBUG_KEYSTORE=' + str(store))
    print('QG_QA_DEBUG_ALIAS=' + alias)
