#!/usr/bin/env python3
import argparse
import json
import struct
from pathlib import Path

HEADER_SIZE = 127
MAGIC = b"PMTiles"
SPEC_VERSION = 3
TILE_TYPE_MVT = 1

def u64(data: bytes, offset: int) -> int:
    return struct.unpack_from("<Q", data, offset)[0]

def i32(data: bytes, offset: int) -> int:
    return struct.unpack_from("<i", data, offset)[0]

def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--file", required=True)
    parser.add_argument("--target-lat", required=True, type=float)
    parser.add_argument("--target-lon", required=True, type=float)
    parser.add_argument("--required-maxzoom", type=int, default=14)
    parser.add_argument("--report")
    args = parser.parse_args()

    path = Path(args.file)
    if not path.is_file():
        raise SystemExit(f"PMTiles archive not found: {path}")
    size = path.stat().st_size
    if size < HEADER_SIZE:
        raise SystemExit(f"PMTiles archive is smaller than {HEADER_SIZE} bytes")

    with path.open("rb") as handle:
        header = handle.read(HEADER_SIZE)

    if header[:7] != MAGIC:
        raise SystemExit(f"Invalid PMTiles magic: {header[:7]!r}")
    version = header[7]
    tile_type = header[99]
    min_zoom = header[100]
    max_zoom = header[101]
    if version != SPEC_VERSION:
        raise SystemExit(f"Expected PMTiles v3, got v{version}")
    if tile_type != TILE_TYPE_MVT:
        raise SystemExit(f"Expected MVT tile type 1, got {tile_type}")
    if min_zoom > max_zoom:
        raise SystemExit(f"Invalid zoom range {min_zoom}..{max_zoom}")
    if max_zoom < args.required_maxzoom:
        raise SystemExit(
            f"Archive maxzoom {max_zoom} is below required {args.required_maxzoom}"
        )

    root_offset = u64(header, 8)
    root_length = u64(header, 16)
    metadata_offset = u64(header, 24)
    metadata_length = u64(header, 32)
    tile_data_offset = u64(header, 56)
    tile_data_length = u64(header, 64)
    for name, offset, length in (
        ("root", root_offset, root_length),
        ("metadata", metadata_offset, metadata_length),
        ("tile-data", tile_data_offset, tile_data_length),
    ):
        if offset < HEADER_SIZE or offset + length > size:
            raise SystemExit(
                f"{name} section is outside archive: offset={offset} length={length} size={size}"
            )

    min_lon = i32(header, 102) / 10_000_000.0
    min_lat = i32(header, 106) / 10_000_000.0
    max_lon = i32(header, 110) / 10_000_000.0
    max_lat = i32(header, 114) / 10_000_000.0
    if not (min_lat <= args.target_lat <= max_lat):
        raise SystemExit(
            f"Target latitude {args.target_lat} outside archive {min_lat}..{max_lat}"
        )
    if not (min_lon <= args.target_lon <= max_lon):
        raise SystemExit(
            f"Target longitude {args.target_lon} outside archive {min_lon}..{max_lon}"
        )

    report = {
        "file": str(path),
        "bytes": size,
        "spec_version": version,
        "tile_type": tile_type,
        "min_zoom": min_zoom,
        "max_zoom": max_zoom,
        "bounds": [min_lon, min_lat, max_lon, max_lat],
        "target": [args.target_lon, args.target_lat],
        "target_inside_bounds": True,
        "root_directory_bytes": root_length,
        "metadata_bytes": metadata_length,
        "tile_data_bytes": tile_data_length,
    }
    rendered = json.dumps(report, ensure_ascii=False, indent=2)
    print(rendered)
    if args.report:
        Path(args.report).write_text(rendered + "\n", encoding="utf-8")

if __name__ == "__main__":
    main()
