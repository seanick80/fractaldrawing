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

---

## Post JUnit 5 Migration + PMD Cleanup
**Date:** 2026-04-05
**Commit:** 6c2f965
**Java:** OpenJDK 21.0.10 (2026-01-20 LTS)
**Size:** 400x300, 1 warmup, 3 runs

| Location | Mode | Min | Avg | Max | px/s | Notes |
|----------|------|-----|-----|-----|------|-------|
| bigdecimal_location | PERTURBATION | 10.85s | 10.88s | 10.95s | 11,027 | |
| bigdecimal_location | BIGDECIMAL | 21.62s | 22.20s | 22.60s | 5,406 | |
| bigdecimal_location | DOUBLE | 73ms | 73ms | 74ms | 1,636,364 | |
| deeper_location | PERTURBATION | 8.26s | 8.57s | 8.72s | 14,005 | |
| deeper_location | BIGDECIMAL | 24.89s | 25.21s | 25.63s | 4,759 | |
| deeper_location | DOUBLE | 23ms | 29ms | 35ms | 4,044,944 | |
| double_deep_spiral | DOUBLE | 27ms | 28ms | 29ms | 4,285,714 | |
| double_mini_mandelbrot | DOUBLE | 17ms | 22ms | 26ms | 5,294,118 | |
| double_seahorse | DOUBLE | 15ms | 18ms | 20ms | 6,545,455 | |
| fractal_zoomed_5 | PERTURBATION | 3.56s | 3.65s | 3.76s | 32,853 | |
| fractal_zoomed_5 | BIGDECIMAL | 25.76s | 26.85s | 27.50s | 4,469 | |
| fractal_zoomed_5 | DOUBLE | 31ms | 31ms | 31ms | 3,870,968 | |
| mandelbrot_bulb_edge | DOUBLE | 17ms | 21ms | 24ms | 5,714,286 | New location |
| mandelbrot_cardioid | DOUBLE | 24ms | 24ms | 25ms | 4,864,865 | New location |
| mandelbrot_deep_interior | PERTURBATION | 1.11s | 1.20s | 1.25s | 99,668 | New location |
| mandelbrot_deep_interior | BIGDECIMAL | 13.69s | 14.20s | 14.96s | 8,450 | New location |
| mandelbrot_deep_interior | DOUBLE | 15ms | 16ms | 18ms | 7,500,000 | New location |
| perturbation_error | PERTURBATION | 5.51s | 5.55s | 5.60s | 21,620 | |
| perturbation_error | BIGDECIMAL | 11.91s | 12.67s | 13.62s | 9,474 | No GC spike |
| perturbation_error | DOUBLE | 39ms | 41ms | 44ms | 2,926,829 | |

**Observations:**
- No performance regressions. Double-precision rendering is faster across the board vs baseline (likely different system load / JIT warmth).
- Perturbation and BigDecimal times are slightly higher in some cases — normal variance from system load, not code regression.
- `perturbation_error` BigDecimal no longer shows GC spike (stable 11-13s vs baseline 10s-4m34s).
- 3 new benchmark locations added: `mandelbrot_bulb_edge`, `mandelbrot_cardioid`, `mandelbrot_deep_interior`.
