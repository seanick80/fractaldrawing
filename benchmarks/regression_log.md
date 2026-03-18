# Performance Regression Log

## Baseline (Pre-Refactoring)
**Date:** 2026-03-18
**Commit:** d312a57
**Java:** OpenJDK 21.0.10 (2026-01-20 LTS)
**Size:** 400x300, 1 warmup, 3 runs

| Location | Mode | Min | Avg | Max | px/s | Notes |
|----------|------|-----|-----|-----|------|-------|
| bigdecimal_location | PERTURBATION | 2.23s | 3.90s | 4.88s | 30,782 | |
| bigdecimal_location | BIGDECIMAL | 15.48s | 15.63s | 15.78s | 7,678 | |
| bigdecimal_location | DOUBLE | 50ms | 54ms | 63ms | 2,208,589 | |
| deeper_location | PERTURBATION | 5.91s | 6.02s | 6.19s | 19,932 | |
| deeper_location | BIGDECIMAL | 18.99s | 19.35s | 19.61s | 6,203 | |
| deeper_location | DOUBLE | 48ms | 56ms | 63ms | 2,130,178 | |
| double_deep_spiral | DOUBLE | 76ms | 81ms | 84ms | 1,481,481 | |
| double_mini_mandelbrot | DOUBLE | 43ms | 43ms | 44ms | 2,748,092 | |
| double_seahorse | DOUBLE | 30ms | 30ms | 31ms | 3,913,043 | |
| fractal_zoomed_5 | PERTURBATION | 1.39s | 1.41s | 1.43s | 85,026 | |
| fractal_zoomed_5 | BIGDECIMAL | 19.45s | 20.09s | 20.44s | 5,974 | |
| fractal_zoomed_5 | DOUBLE | 56ms | 56ms | 58ms | 2,117,647 | |
| perturbation_error | PERTURBATION | 5.23s | 5.34s | 5.52s | 22,456 | |
| perturbation_error | BIGDECIMAL | 10.67s | 1m38.7s | 4m34.5s | 1,216 | GC spike run 3 |
| perturbation_error | DOUBLE | 67ms | 86ms | 105ms | 1,389,961 | |

**Observations:**
- BigDecimal GC spike on `perturbation_error` run 3: 4m34s vs ~10s (27x). Validates the FixedPrecisionFloat investigation.
- Perturbation is 3-15x faster than pure BigDecimal at deep zoom.
- Double is 30-2400x faster than BigDecimal (but loses precision at deep zoom).
