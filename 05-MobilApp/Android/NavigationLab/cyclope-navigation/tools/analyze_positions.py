#!/usr/bin/env python3
"""
Analyse a CSV recorded by the Cyclope Nav Lab app (button "Start log").

Usage examples
--------------
  # Any test: availability, update rate, reported accuracy per source
  python analyze_positions.py poslog_20261120_101500_Pixel7.csv

  # Static test: you stood still at a known point -> real error per source
  python analyze_positions.py poslog.csv --ref 48.71234,2.16789

  # Walk test with "Mark point" presses at known corners -> error at each mark
  python analyze_positions.py poslog.csv --marks marks_reference.csv
      (marks_reference.csv: label,lat,lon  e.g.  M1,48.71234,2.16789)

Outputs: a Markdown table on screen + <csv>_results.md, and <csv>_tracks.png
(map of the 5 sources in metres). Needs: python3 with pandas, numpy, matplotlib.

Metric definitions
------------------
  fixes          number of position lines for this source
  rate_hz        1 / median time between two fixes
  availability   % of the test seconds that have at least one fix of this source
  acc_med/p95    accuracy *reported by the phone* (radius in m, 68 % confidence)
  err_cep50/95   *real* horizontal error vs the reference point: 50 % / 95 % of fixes are closer than this
  spread_med     median distance to the mean position of the source (precision, without reference)
"""
import argparse
import math
import os
import sys

import numpy as np
import pandas as pd

POSITION_SOURCES = ["GNSS", "NETWORK", "FUSED", "PDR"]
R_EARTH = 6371008.8


def to_local(lat, lon, lat0, lon0):
    """Metres east/north of (lat0, lon0) (flat approximation, fine for < few km)."""
    east = np.radians(np.asarray(lon) - lon0) * R_EARTH * math.cos(math.radians(lat0))
    north = np.radians(np.asarray(lat) - lat0) * R_EARTH
    return east, north


def haversine(lat1, lon1, lat2, lon2):
    lat1, lon1, lat2, lon2 = map(np.radians, (lat1, lon1, lat2, lon2))
    h = np.sin((lat2 - lat1) / 2) ** 2 + np.cos(lat1) * np.cos(lat2) * np.sin((lon2 - lon1) / 2) ** 2
    return 2 * R_EARTH * np.arcsin(np.sqrt(np.clip(h, 0, 1)))


def fmt(v, digits=1):
    return "-" if v is None or (isinstance(v, float) and math.isnan(v)) else f"{v:.{digits}f}"


def analyse(df, ref=None):
    t0, t1 = df["time_ms"].min(), df["time_ms"].max()
    duration_s = max(1.0, (t1 - t0) / 1000.0)
    rows = []
    for src in POSITION_SOURCES:
        d = df[(df["source"] == src) & df["lat"].notna()].sort_values("time_ms")
        if d.empty:
            rows.append({"source": src, "fixes": 0})
            continue
        dt = np.diff(d["time_ms"].values) / 1000.0
        seconds_with_fix = np.unique(((d["time_ms"] - t0) // 1000).astype(int)).size
        row = {
            "source": src,
            "fixes": len(d),
            "rate_hz": 1.0 / np.median(dt) if len(dt) and np.median(dt) > 0 else float("nan"),
            "availability_pct": 100.0 * min(1.0, seconds_with_fix / duration_s),
            "first_fix_s": (d["time_ms"].iloc[0] - t0) / 1000.0,
            "acc_med": d["accuracy_m"].median(),
            "acc_p95": d["accuracy_m"].quantile(0.95),
        }
        mlat, mlon = d["lat"].mean(), d["lon"].mean()
        row["spread_med"] = float(np.median(haversine(d["lat"], d["lon"], mlat, mlon)))
        if ref is not None:
            err = haversine(d["lat"], d["lon"], ref[0], ref[1])
            row["err_cep50"] = float(np.percentile(err, 50))
            row["err_cep95"] = float(np.percentile(err, 95))
            row["err_max"] = float(np.max(err))
            # Is the phone honest? % of fixes whose real error is within the reported accuracy (ideal ~68 %)
            ok = d["accuracy_m"].notna()
            row["honest_pct"] = 100.0 * float(np.mean(err[ok.values] <= d["accuracy_m"][ok].values)) if ok.any() else float("nan")
        rows.append(row)
    return pd.DataFrame(rows), duration_s


def marks_errors(df, marks_ref, window_ms=3000):
    """Error of each source at each mark (closest fix within +/- window_ms of the button press)."""
    out = []
    for _, m in df[df["source"] == "MARK"].iterrows():
        label = str(m["note"])
        if label not in marks_ref.index:
            continue
        rlat, rlon = marks_ref.loc[label, "lat"], marks_ref.loc[label, "lon"]
        row = {"mark": label}
        for src in POSITION_SOURCES:
            d = df[(df["source"] == src) & df["lat"].notna()]
            d = d[(d["time_ms"] - m["time_ms"]).abs() <= window_ms]
            if d.empty:
                row[src] = float("nan")
                continue
            best = d.iloc[(d["time_ms"] - m["time_ms"]).abs().argsort().iloc[0]]
            row[src] = float(haversine(best["lat"], best["lon"], rlat, rlon))
        out.append(row)
    return pd.DataFrame(out)


def to_markdown(stats, duration_s, gnss_status, ref, marks_table, csv_name):
    lines = [f"## Results for `{csv_name}`", "", f"Test duration: {duration_s:.0f} s"]
    if ref is not None:
        lines.append(f"Reference point: {ref[0]:.6f}, {ref[1]:.6f}")
    if gnss_status is not None:
        lines.append(f"GNSS status: median {gnss_status[0]:.0f} satellites used / {gnss_status[1]:.0f} visible, "
                     f"median C/N0 of used satellites {gnss_status[2]:.1f} dB-Hz")
    lines.append("")
    cols = ["source", "fixes", "rate_hz", "availability_pct", "first_fix_s", "acc_med", "acc_p95", "spread_med"]
    if ref is not None:
        cols += ["err_cep50", "err_cep95", "err_max", "honest_pct"]
    lines.append("| " + " | ".join(cols) + " |")
    lines.append("|" + "---|" * len(cols))
    for _, r in stats.iterrows():
        cells = []
        for c in cols:
            v = r.get(c, float("nan"))
            cells.append(str(v) if c in ("source", "fixes") else fmt(v, 2 if c == "rate_hz" else 1))
        lines.append("| " + " | ".join(cells) + " |")
    if marks_table is not None and not marks_table.empty:
        lines += ["", "Error at each mark (m):", "", "| mark | " + " | ".join(POSITION_SOURCES) + " |",
                  "|" + "---|" * (len(POSITION_SOURCES) + 1)]
        for _, r in marks_table.iterrows():
            lines.append(f"| {r['mark']} | " + " | ".join(fmt(r[s]) for s in POSITION_SOURCES) + " |")
    return "\n".join(lines) + "\n"


def plot(df, ref, out_png):
    import matplotlib
    matplotlib.use("Agg")
    import matplotlib.pyplot as plt

    pos = df[df["source"].isin(POSITION_SOURCES) & df["lat"].notna()]
    if pos.empty:
        return
    lat0, lon0 = (ref if ref is not None else (pos["lat"].median(), pos["lon"].median()))
    fig, ax = plt.subplots(figsize=(7, 7))
    for src, marker in zip(POSITION_SOURCES, ["o", "s", "^", "x"]):
        d = pos[pos["source"] == src]
        if d.empty:
            continue
        e, n = to_local(d["lat"], d["lon"], lat0, lon0)
        ax.plot(e, n, marker=marker, linestyle="-", linewidth=0.6, markersize=3, alpha=0.7, label=f"{src} ({len(d)})")
    if ref is not None:
        ax.plot([0], [0], marker="*", color="black", markersize=14, linestyle="none", label="reference")
    ax.set_xlabel("east (m)")
    ax.set_ylabel("north (m)")
    ax.set_aspect("equal", adjustable="datalim")
    ax.grid(True, alpha=0.3)
    ax.legend()
    ax.set_title("Positions of the 5 sources (local metres)")
    fig.tight_layout()
    fig.savefig(out_png, dpi=130)


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("csv")
    ap.add_argument("--ref", help="reference point 'lat,lon' for a static test")
    ap.add_argument("--marks", help="CSV label,lat,lon with the true coordinates of the marks M1, M2...")
    args = ap.parse_args()

    df = pd.read_csv(args.csv, dtype={"note": str})
    if df.empty:
        sys.exit("empty CSV")
    ref = tuple(float(x) for x in args.ref.split(",")) if args.ref else None

    gs = df[df["source"] == "GNSS_STATUS"]
    gnss_status = None
    if not gs.empty:
        gnss_status = (gs["sats_used"].median(), gs["sats_visible"].median(), gs["mean_cn0_dbhz"].median())

    marks_table = None
    if args.marks:
        mref = pd.read_csv(args.marks, header=None, names=["label", "lat", "lon"], dtype={"label": str}).set_index("label")
        marks_table = marks_errors(df, mref)

    stats, duration_s = analyse(df, ref)
    base = os.path.splitext(args.csv)[0]
    md = to_markdown(stats, duration_s, gnss_status, ref, marks_table, os.path.basename(args.csv))
    print(md)
    with open(base + "_results.md", "w", encoding="utf-8") as f:
        f.write(md)
    plot(df, ref, base + "_tracks.png")
    print(f"Saved {base}_results.md and {base}_tracks.png")


if __name__ == "__main__":
    main()
