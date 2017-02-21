#!/usr/bin/env python3
# Utilities for icons from icons8.com
# by PW, 2-20, 2017

import os
import sys


def dir_for_action(size):
    if size == '24':    # mdpi
        return 'mipmap-hdpi'
    elif size == '36':  # hdpi
        return 'mipmap-mdpi'
    elif size == '48':  # xdpi
        return 'mipmap-xhdpi'
    elif size == '72':  # xxdpi
        return 'mipmap-xxhdpi'
    elif size == '96':  # xxxdpi
        return 'mipmap-xxxhdpi'
    else:
        return None


def dir_for_pixle(size, mode='action'):
    if mode == 'action':
        return dir_for_action(size)
    else:
        return None


def classify_by_dip(src, dest=None, suffix=""):
    if not dest:
        dest = src
    for name in os.listdir(src):
        base, ext = os.path.splitext(name)
        if ext != '.png':
            continue
        base, size = base.split('_')
        base = 'ic_' + base.replace(' ', '_').lower() + suffix
        
        dir = dir_for_pixle(size)
        if dir is None:
            continue
        
        path = os.path.join(dest, dir)
        if not os.path.exists(path):
            os.mkdir(path)
        os.rename(os.path.join(src, name), os.path.join(path, base + ext))

def main(argv):
    classify_by_dip(argv[1])
    return 0

if __name__ == '__main__':
    sys.argv.append(r'D:\down\qiyu')
    sys.exit(main(sys.argv))
