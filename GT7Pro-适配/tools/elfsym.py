#!/usr/bin/env python
"""Dump / search dynamic symbols of an ELF64 shared object (stdlib only).

  elfsym.py <lib.so>                  list every defined function symbol
  elfsym.py <lib.so> --grep <regex>   filter by demangled-ish name
  elfsym.py <lib.so> --sections       section headers with addr/size/offset
"""
import re
import struct
import sys


def load(path):
    data = open(path, 'rb').read()
    assert data[:4] == b'\x7fELF', 'not an ELF'
    assert data[4] == 2 and data[5] == 1, 'expect ELF64 little endian'
    e_shoff, = struct.unpack_from('<Q', data, 0x28)
    e_shentsize, e_shnum, e_shstrndx = struct.unpack_from('<HHH', data, 0x3a)
    secs = []
    for i in range(e_shnum):
        off = e_shoff + i * e_shentsize
        name, typ, flags, addr, offset, size, link, info, align, entsize = struct.unpack_from('<IIQQQQIIQQ', data, off)
        secs.append(dict(name_off=name, type=typ, addr=addr, offset=offset, size=size,
                         link=link, entsize=entsize))
    shstr = secs[e_shstrndx]
    for s in secs:
        s['name'] = cstr(data, shstr['offset'] + s['name_off'])
    return data, secs


def cstr(data, off):
    end = data.index(b'\0', off)
    return data[off:end].decode('utf-8', 'replace')


def symbols(data, secs):
    for s in secs:
        if s['type'] not in (2, 11):    # SHT_SYMTAB / SHT_DYNSYM
            continue
        strtab = secs[s['link']]
        n = s['size'] // s['entsize'] if s['entsize'] else 0
        out = []
        for i in range(n):
            off = s['offset'] + i * s['entsize']
            name, info, other, shndx, value, size = struct.unpack_from('<IBBHQQ', data, off)
            if name == 0 or shndx == 0:
                continue
            typ = info & 0xf
            if typ not in (1, 2, 10):   # OBJECT, FUNC, GNU_IFUNC
                continue
            out.append(dict(name=cstr(data, strtab['offset'] + name), value=value,
                            size=size, type=typ, tab=s['name']))
        yield s['name'], out


def main():
    path = sys.argv[1]
    grep = None
    if '--grep' in sys.argv:
        grep = re.compile(sys.argv[sys.argv.index('--grep') + 1])
    data, secs = load(path)
    if '--sections' in sys.argv:
        for s in secs:
            if s['name'] and s['type'] in (1, 8, 9):
                print('%-24s type=%d addr=0x%08x size=0x%08x off=0x%08x' %
                      (s['name'], s['type'], s['addr'], s['size'], s['offset']))
        return
    seen = set()
    for tab, syms in symbols(data, secs):
        for sym in syms:
            key = (sym['name'], sym['value'])
            if key in seen:
                continue
            seen.add(key)
            if grep and not grep.search(sym['name']):
                continue
            print('0x%08x %6d %-8s %s' % (sym['value'], sym['size'], tab, sym['name']))
    print('# %d symbols' % len(seen), file=sys.stderr)


if __name__ == '__main__':
    main()
