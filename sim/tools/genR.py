# Generates the Android R classes the SDK jars expect, and the string table the Context shim serves.
# usage: genR.py <aar-dir> <java-out> <res-out>
import collections, os, re, sys, xml.etree.ElementTree as ET

aar, out, res = sys.argv[1:4]
next_id = 0x7F000001
strings = {}
ids = {}
# string values from every library first: an AAR's R.txt also lists its dependencies' strings
values = {}
for name in sorted(os.listdir(aar)):
    vdir = os.path.join(aar, name, "res", "values")
    if not os.path.isdir(vdir):
        continue
    for vf in sorted(os.listdir(vdir)):
        if vf.endswith(".xml"):
            for e in ET.parse(os.path.join(vdir, vf)).getroot():
                if e.tag == "string" and e.get("name") not in values:
                    # Android string escapes: \' \" \n \\
                    v = "".join(e.itertext())
                    v = re.sub(r"\\(.)", lambda m: {"n": "\n", "t": "\t"}.get(m.group(1), m.group(1)), v)
                    values[e.get("name")] = v
for name in sorted(os.listdir(aar)):
    manifest = os.path.join(aar, name, "AndroidManifest.xml")
    rtxt = os.path.join(aar, name, "R.txt")
    if not os.path.exists(manifest) or not os.path.exists(rtxt) or os.path.getsize(rtxt) == 0:
        continue
    pkg = re.search(r'package="([^"]+)"', open(manifest, encoding="utf-8").read()).group(1)
    types = collections.defaultdict(list)
    for line in open(rtxt, encoding="utf-8"):
        p = line.split()
        if len(p) < 3:
            continue
        if p[0] == "int[]":
            types[p[1]].append(f"public static int[] {p[2]} = new int[0];")
        else:
            types[p[1]].append(f"public static int {p[2]} = {next_id};")
            ids.setdefault(f"{p[1]}/{p[2]}", next_id)
            if p[1] == "string":
                strings[next_id] = values.get(p[2], p[2])
            next_id += 1
    d = os.path.join(out, *pkg.split("."))
    os.makedirs(d, exist_ok=True)
    with open(os.path.join(d, "R.java"), "w", encoding="utf-8") as f:
        f.write(f"package {pkg};\npublic final class R {{\n")
        for t, fields in types.items():
            f.write(f" public static final class {t} {{\n  " + "\n  ".join(fields) + "\n }\n")
        f.write("}\n")
os.makedirs(res, exist_ok=True)
with open(os.path.join(res, "strings.properties"), "w", encoding="utf-8") as f:
    for k, v in strings.items():
        f.write(f"{k}=" + v.replace("\\", "\\\\").replace("\n", "\\n") + "\n")
with open(os.path.join(res, "ids.properties"), "w", encoding="utf-8") as f:
    for k, v in ids.items():
        f.write(f"{k}={v}\n")
