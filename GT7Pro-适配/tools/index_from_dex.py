#!/usr/bin/env python
"""Build the tools/symbols index pickle directly from dex bytes (no dexdump needed).

Same structure as tools/symbols/index_dump.py produces:
  { class_descriptor: {super, ifaces, sfields, ifields, methods, dex} }
  method = {name, type(proto), strings, calls[(owner,name,proto)], fields[(op,owner,name,type)],
            ints, access, direct}

usage: index_from_dex.py <apk> <out.pkl>
"""
import sys, zipfile, pickle, struct

# 16-bit code-unit widths per opcode
W = [1] * 256
for op in (0x02, 0x05, 0x08, 0x13, 0x15, 0x16, 0x19, 0x1a, 0x1c, 0x1f, 0x20, 0x22, 0x23,
           0x29, 0x2d, 0x2e, 0x2f, 0x30, 0x31,
           0x32, 0x33, 0x34, 0x35, 0x36, 0x37, 0x38, 0x39, 0x3a, 0x3b, 0x3c, 0x3d,
           0x44, 0x45, 0x46, 0x47, 0x48, 0x49, 0x4a, 0x4b, 0x4c, 0x4d, 0x4e, 0x4f,
           0x50, 0x51, 0x52, 0x53, 0x54, 0x55, 0x56, 0x57, 0x58, 0x59, 0x5a, 0x5b,
           0x5c, 0x5d, 0x5e, 0x5f, 0x60, 0x61, 0x62, 0x63, 0x64, 0x65, 0x66, 0x67,
           0x68, 0x69, 0x6a, 0x6b, 0x6c, 0x6d, 0x90, 0x91, 0x92, 0x93, 0x94, 0x95,
           0x96, 0x97, 0x98, 0x99, 0x9a, 0x9b, 0x9c, 0x9d, 0x9e, 0x9f, 0xa0, 0xa1,
           0xa2, 0xa3, 0xa4, 0xa5, 0xa6, 0xa7, 0xa8, 0xa9, 0xaa, 0xab, 0xac, 0xad,
           0xae, 0xaf, 0xd0, 0xd1, 0xd2, 0xd3, 0xd4, 0xd5, 0xd6, 0xd7,
           0xd8, 0xd9, 0xda, 0xdb, 0xdc, 0xdd, 0xde, 0xdf, 0xe0, 0xe1, 0xe2,
           0xfe, 0xff):
    W[op] = 2
for op in (0x03, 0x06, 0x09, 0x14, 0x17, 0x1b, 0x24, 0x25, 0x26, 0x2a, 0x2b, 0x2c,
           0x6e, 0x6f, 0x70, 0x71, 0x72, 0x74, 0x75, 0x76, 0x77, 0x78, 0xfc, 0xfd):
    W[op] = 3
for op in (0xfa, 0xfb):
    W[op] = 4
W[0x18] = 5

CONST_STR = {0x1a, 0x1b}
INVOKE = {0x6e: 'virtual', 0x6f: 'super', 0x70: 'direct', 0x71: 'static', 0x72: 'interface',
          0x74: 'virtual/range', 0x75: 'super/range', 0x76: 'direct/range', 0x77: 'static/range',
          0x78: 'interface/range'}
IGET = {0x52: 'iget', 0x53: 'iget-wide', 0x54: 'iget-object', 0x55: 'iget-boolean', 0x56: 'iget-byte',
        0x57: 'iget-char', 0x58: 'iget-short'}
IPUT = {0x59: 'iput', 0x5a: 'iput-wide', 0x5b: 'iput-object', 0x5c: 'iput-boolean', 0x5d: 'iput-byte',
        0x5e: 'iput-char', 0x5f: 'iput-short'}
SGET = {0x60: 'sget', 0x61: 'sget-wide', 0x62: 'sget-object', 0x63: 'sget-boolean', 0x64: 'sget-byte',
        0x65: 'sget-char', 0x66: 'sget-short'}
SPUT = {0x67: 'sput', 0x68: 'sput-wide', 0x69: 'sput-object', 0x6a: 'sput-boolean', 0x6b: 'sput-byte',
        0x6c: 'sput-char', 0x6d: 'sput-short'}
CONST_INT = {0x12: '4', 0x13: '16', 0x14: '32', 0x15: 'high16'}
FIELD_OPS = {}
for _d in (IGET, IPUT, SGET, SPUT):
    FIELD_OPS.update(_d)


def uleb(d, off):
    r = s = 0
    while True:
        b = d[off]
        off += 1
        r |= (b & 0x7f) << s
        if not (b & 0x80):
            return r, off
        s += 7


class DexIndex:
    def __init__(self, data, dexname):
        self.d = data
        (self.n_str, self.o_str, self.n_type, self.o_type, self.n_proto, self.o_proto,
         self.n_field, self.o_field, self.n_method, self.o_method,
         self.n_class, self.o_class) = struct.unpack_from('<12I', data, 0x38)
        self.dexname = dexname
        self._s = {}
        self._t = {}
        self._p = {}
        self._m = {}
        self._f = {}

    def s(self, i):
        v = self._s.get(i)
        if v is None:
            p = struct.unpack_from('<I', self.d, self.o_str + i * 4)[0]
            _, p = uleb(self.d, p)
            e = self.d.index(b'\0', p)
            v = self.d[p:e].decode('utf-8', 'replace')
            self._s[i] = v
        return v

    def t(self, i):
        v = self._t.get(i)
        if v is None:
            v = self.s(struct.unpack_from('<I', self.d, self.o_type + i * 4)[0])
            self._t[i] = v
        return v

    def p(self, i):
        v = self._p.get(i)
        if v is None:
            _, ret, po = struct.unpack_from('<III', self.d, self.o_proto + i * 12)
            if po:
                n = struct.unpack_from('<I', self.d, po)[0]
                args = [self.t(struct.unpack_from('<H', self.d, po + 4 + j * 2)[0]) for j in range(n)]
            else:
                args = []
            v = '(' + ''.join(args) + ')' + self.t(ret)
            self._p[i] = v
        return v

    def m(self, i):
        v = self._m.get(i)
        if v is None:
            c, pr, nm = struct.unpack_from('<HHI', self.d, self.o_method + i * 8)
            v = (self.t(c), self.s(nm), self.p(pr))
            self._m[i] = v
        return v

    def f(self, i):
        v = self._f.get(i)
        if v is None:
            c, ty, nm = struct.unpack_from('<HHI', self.d, self.o_field + i * 8)
            v = (self.t(c), self.s(nm), self.t(ty))
            self._f[i] = v
        return v

    def scan_code(self, code_off, out):
        d = self.d
        insns_size = struct.unpack_from('<I', d, code_off + 12)[0]
        p = code_off + 16
        end = p + insns_size * 2
        strings, calls, fields, ints = out['strings'], out['calls'], out['fields'], out['ints']
        unpack = struct.unpack_from
        while p < end:
            u = unpack('<H', d, p)[0]
            op = u & 0xff
            if u == 0x0100:                     # packed-switch payload
                p += (unpack('<H', d, p + 2)[0] * 2 + 4) * 2
                continue
            if u == 0x0200:                     # sparse-switch payload
                p += (unpack('<H', d, p + 2)[0] * 4 + 2) * 2
                continue
            if u == 0x0300:                     # fill-array-data payload
                width = unpack('<H', d, p + 2)[0]
                n = unpack('<I', d, p + 4)[0]
                p += (((n * width + 1) // 2) + 4) * 2
                continue
            if op in CONST_STR:
                idx = unpack('<H', d, p + 2)[0] if op == 0x1a else unpack('<I', d, p + 2)[0]
                strings.append(self.s(idx))
            elif op in INVOKE:
                idx = unpack('<H', d, p + 2)[0]
                if idx < self.n_method:
                    calls.append(self.m(idx))
            elif op in FIELD_OPS:
                idx = unpack('<H', d, p + 2)[0]
                if idx < self.n_field:
                    o, n, ty = self.f(idx)
                    fields.append((FIELD_OPS[op], o, n, ty))
            elif op in CONST_INT:
                if op == 0x12:
                    v = (u >> 12) & 0xf
                    if v & 0x8:
                        v -= 16
                elif op == 0x13:
                    v = unpack('<h', d, p + 2)[0]
                elif op == 0x14:
                    v = unpack('<i', d, p + 2)[0]
                else:
                    v = unpack('<h', d, p + 2)[0] << 16
                ints.append(v)
            p += W[op] * 2

    def classes(self):
        for i in range(self.n_class):
            off = self.o_class + i * 32
            (ci, acc, si, io, sf, ao, cdo, sv) = struct.unpack_from('<8I', self.d, off)
            name = self.t(ci)
            c = {'super': self.t(si) if si != 0xffffffff else None, 'ifaces': [],
                 'sfields': [], 'ifields': [], 'methods': [], 'dex': self.dexname}
            if io:
                p = io
                n = struct.unpack_from('<I', self.d, p)[0]
                c['ifaces'] = [self.t(struct.unpack_from('<H', self.d, p + 4 + j * 2)[0]) for j in range(n)]
            if not cdo:
                yield name, c
                continue
            p = cdo
            (nsf, nif, ndm, nvm), p = (lambda a, b: (a, b))(*self._ulebs(p, 4))
            idx = 0
            for _ in range(nsf):
                df, p = uleb(self.d, p); a, p = uleb(self.d, p); idx += df
                o, n, ty = self.f(idx)
                c['sfields'].append({'name': n, 'type': ty, 'access': a})
            idx = 0
            for _ in range(nif):
                df, p = uleb(self.d, p); a, p = uleb(self.d, p); idx += df
                o, n, ty = self.f(idx)
                c['ifields'].append({'name': n, 'type': ty, 'access': a})
            for kind, cnt in (('direct', ndm), ('virtual', nvm)):
                idx = 0
                for _ in range(cnt):
                    df, p = uleb(self.d, p); a, p = uleb(self.d, p); co, p = uleb(self.d, p); idx += df
                    o, n, pr = self.m(idx)
                    mm = {'name': n, 'type': pr, 'access': a, 'strings': [], 'calls': [],
                          'fields': [], 'ints': [], 'direct': kind == 'direct'}
                    if co:
                        self.scan_code(co, mm)
                    c['methods'].append(mm)
            yield name, c

    def _ulebs(self, p, n):
        out = []
        for _ in range(n):
            v, p = uleb(self.d, p)
            out.append(v)
        return out, p


def main():
    apk, out = sys.argv[1], sys.argv[2]
    idx = {}
    with zipfile.ZipFile(apk) as z:
        names = sorted(n for n in z.namelist() if n.endswith('.dex'))
        for k, n in enumerate(names):
            data = z.read(n)
            for cname, c in DexIndex(data, n).classes():
                idx[cname] = c
            print('  %s (%d classes total)' % (n, len(idx)), flush=True)
    pickle.dump(idx, open(out, 'wb'), protocol=4)
    print('wrote', out, len(idx), 'classes')


if __name__ == '__main__':
    main()
