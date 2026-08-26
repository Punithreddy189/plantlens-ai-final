"""
PlantLens AI - Selenium Automated Test Runner
Usage:
    python tests/run_tests.py [--url <URL>] [--headless <true/false>] [--report]
"""

import sys
import os
import argparse
import subprocess

def main():
    parser = argparse.ArgumentParser(description="PlantLens AI - Selenium Phase 7 Test Suite Runner")
    parser.add_argument(
        "--url",
        default=os.environ.get("BASE_URL", "http://localhost:5173"),
        help="Base URL for testing (e.g. GitHub Pages LIVE URL or local server)"
    )
    parser.add_argument(
        "--headless",
        default="true",
        choices=["true", "false"],
        help="Run browser in headless mode (default: true)"
    )
    parser.add_argument(
        "--report",
        action="store_true",
        default=True,
        help="Generate standalone HTML test report in tests/reports/"
    )
    parser.add_argument(
        "-k",
        "--keyword",
        default="",
        help="Filter test cases by keyword (e.g. -k TC_WEB_AUTH)"
    )

    args = parser.parse_args()

    os.makedirs("tests/reports", exist_ok=True)
    os.makedirs("tests/screenshots", exist_ok=True)

    print("=" * 70)
    print("  PLANTLENS AI - SELENIUM AUTOMATION TEST SUITE (PHASE 7)")
    print("=" * 70)
    print(f"Target Base URL : {args.url}")
    print(f"Headless Mode   : {args.headless}")
    print(f"HTML Report     : tests/reports/report.html")
    print("=" * 70)

    pytest_cmd = [
        sys.executable,
        "-m",
        "pytest",
        "tests/test_web_phase7.py",
        f"--base-url={args.url}",
        f"--headless={args.headless}",
        "-v",
        "--html=tests/reports/report.html",
        "--self-contained-html"
    ]

    if args.keyword:
        pytest_cmd.extend(["-k", args.keyword])

    result = subprocess.run(pytest_cmd)
    sys.exit(result.returncode)

if __name__ == "__main__":
    main()
