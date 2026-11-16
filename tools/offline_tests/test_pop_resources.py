import configparser
from pathlib import Path
import sys
import tempfile
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from prepare_pop_resources import identity, prepare, SETTINGS


class PopResourcesTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        root = Path(self.temp.name)
        self.tables, self.settings, self.dest = root / 'tables', root / 'settings', root / 'out'
        self.tables.mkdir()
        self.settings.mkdir()
        for prefix in ('SC', 'CWCM'):
            for i in range(9):
                (self.tables / f'{prefix}16Lut{i}').write_bytes(identity(16))
        for name in SETTINGS:
            config = configparser.ConfigParser()
            config.read_dict({'SCLut': {'lut_size': 16, 'SCLutNum': 9},
                              'CMCWLut': {'lut_size': 16, 'CMCWLutNum': 9},
                              'switch': {'LMT': 0, 'ODT': 0, 'SC': 1, 'CWCM': 1},
                              'forAppSwitch': {'LMT': 1, 'ODT': 1, 'SC': 1, 'CWCM': 1}})
            with (self.settings / name).open('w') as out:
                config.write(out)

    def test_both_engines_coexist(self):
        (self.dest / 'setting_Retro').mkdir(parents=True)
        (self.dest / 'setting_Retro/SimTool.ini').write_bytes(b'stock legacy configuration')
        (self.dest / 'lmt').mkdir()
        (self.dest / 'lmt/SCLut0').write_bytes(b'stock legacy table')
        prepare(self.tables, self.settings, self.dest)
        self.assertEqual((self.dest / 'setting_Retro/SimTool.ini').read_bytes(), b'stock legacy configuration')
        self.assertEqual((self.dest / 'lmt/SCLut0').read_bytes(), b'stock legacy table')
        self.assertEqual((self.dest / 'lmt/BaseLut').stat().st_size, 3145728)
        for name in SETTINGS:
            self.assertEqual((self.dest / 'setting_Retro' / name).read_bytes(), (self.settings / name).read_bytes())
        self.assertEqual(len(list((self.dest / 'lmt').glob('CWCM16Lut*'))), 9)
        self.assertEqual(len(list((self.dest / 'lmt').glob('SC16Lut*'))), 9)

    def test_missing_table_fails_before_writing(self):
        (self.tables / 'CWCM16Lut8').unlink()
        with self.assertRaises(FileNotFoundError):
            prepare(self.tables, self.settings, self.dest)
        self.assertFalse(self.dest.exists())

    def test_truncated_table_rejected(self):
        (self.tables / 'SC16Lut0').write_bytes(b'bad')
        with self.assertRaises(ValueError):
            prepare(self.tables, self.settings, self.dest)
        self.assertFalse(self.dest.exists())

    def test_wrong_axis_rejected(self):
        data = identity(16)
        plane = len(data) // 3
        (self.tables / 'SC16Lut4').write_bytes(data[2 * plane:] + data[plane:2 * plane] + data[:plane])
        with self.assertRaisesRegex(ValueError, 'neutral RGB layout'):
            prepare(self.tables, self.settings, self.dest)


if __name__ == '__main__':
    unittest.main()
