#!/usr/bin/env python
"""Minimal Android binary XML (AXML) parser - stdlib only."""
import struct, sys, zipfile

RES_STRING_POOL = 0x0001
RES_XML = 0x0003
RES_XML_START_ELEMENT = 0x0102
RES_XML_END_ELEMENT = 0x0103
RES_XML_RESOURCE_MAP = 0x0180
TYPE_STRING = 0x03
TYPE_INT_DEC = 0x10
TYPE_INT_HEX = 0x11
TYPE_INT_BOOLEAN = 0x12
TYPE_REFERENCE = 0x01


def parse_string_pool(data, off):
    (typ, hsz, size) = struct.unpack_from('<HHI', data, off)
    count, style_count, flags, strings_start, styles_start = struct.unpack_from('<IIIII', data, off + 8)
    utf8 = (flags & (1 << 8)) != 0
    offsets = struct.unpack_from('<%dI' % count, data, off + hsz)
    base = off + strings_start
    out = []
    for o in offsets:
        p = base + o
        if utf8:
            # utf16 length then utf8 length (each 1-2 bytes)
            n = data[p]
            if n & 0x80:
                p += 2
            else:
                p += 1
            n = data[p]
            if n & 0x80:
                n = ((n & 0x7f) << 8) | data[p + 1]
                p += 2
            else:
                p += 1
            out.append(data[p:p + n].decode('utf-8', 'replace'))
        else:
            n = struct.unpack_from('<H', data, p)[0]
            p += 2
            if n & 0x8000:
                n2 = struct.unpack_from('<H', data, p)[0]
                n = ((n & 0x7fff) << 16) | n2
                p += 2
            out.append(data[p:p + n * 2].decode('utf-16-le', 'replace'))
    return out


def parse_axml(data):
    typ, hsz, size = struct.unpack_from('<HHI', data, 0)
    assert typ == RES_XML, hex(typ)
    off = hsz
    strings = []
    resmap = []
    elements = []
    while off < size:
        ctyp, chsz, csize = struct.unpack_from('<HHI', data, off)
        if csize == 0:
            break
        if ctyp == RES_STRING_POOL:
            strings = parse_string_pool(data, off)
        elif ctyp == RES_XML_RESOURCE_MAP:
            n = (csize - chsz) // 4
            resmap = list(struct.unpack_from('<%dI' % n, data, off + chsz))
        elif ctyp == RES_XML_START_ELEMENT:
            ns_i, name_i = struct.unpack_from('<ii', data, off + chsz)
            attr_start, attr_size, attr_count = struct.unpack_from('<HHH', data, off + chsz + 8)
            attrs = []
            ap = off + chsz + attr_start
            for i in range(attr_count):
                a_ns, a_name, a_raw = struct.unpack_from('<iii', data, ap)
                a_type = data[ap + 15]
                a_data = struct.unpack_from('<I', data, ap + 16)[0]
                name = strings[a_name] if 0 <= a_name < len(strings) else '?%d' % a_name
                if 0 <= a_raw < len(strings):
                    val = strings[a_raw]
                elif a_type == TYPE_INT_BOOLEAN:
                    val = 'true' if a_data else 'false'
                elif a_type == TYPE_INT_HEX:
                    val = hex(a_data)
                elif a_type == TYPE_REFERENCE:
                    val = '@0x%08x' % a_data
                elif a_type in (TYPE_INT_DEC,):
                    val = str(a_data)
                else:
                    val = 'type0x%x:0x%x' % (a_type, a_data)
                attrs.append((name, val, a_type))
                ap += 20
            elements.append((strings[name_i] if 0 <= name_i < len(strings) else '?', attrs))
        off += csize
    return elements


def manifest_of(apk):
    with zipfile.ZipFile(apk) as z:
        return parse_axml(z.read('AndroidManifest.xml'))


if __name__ == '__main__':
    els = manifest_of(sys.argv[1])
    depth = 0
    for name, attrs in els:
        if name == 'manifest':
            print('== manifest attrs ==')
            for a, v, t in attrs:
                print('   %-28s = %s' % (a, v))
        elif name in ('application', 'uses-sdk', 'uses-permission', 'meta-data'):
            if name != 'uses-permission':
                print('%s%s' % ('  ' * min(depth, 3), name))
                for a, v, t in attrs:
                    print('%s   %-24s = %s' % ('  ' * min(depth, 3), a, v))
