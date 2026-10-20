"""Minimal stand-in for the upstream tools/symbols/dx.py.

spec_eval.py only needs load() / D() / J(); the upstream module additionally imports refs.py
(author-specific paths) and carries the cross-version matchers, which are not needed to replay
a spec against one build.
"""
import os
import pickle

WORK = os.environ.get("SYMBOLS_WORK", ".")
_cache = {}


def load(tag):
    if tag not in _cache:
        _cache[tag] = pickle.load(open(os.path.join(WORK, "idx_%s.pkl" % tag), "rb"))
    return _cache[tag]


def D(n):  # java name -> descriptor
    return n if n.startswith("L") else "L" + n.replace(".", "/") + ";"


def J(d):  # descriptor -> java name
    return d[1:-1].replace("/", ".")
