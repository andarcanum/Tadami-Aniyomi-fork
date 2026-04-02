# Quick task 260402-qai: Diagnose curl page turn speed - Plan

1. Trace how curl edge taps trigger page navigation.
2. Find where the configured page turn speed is calculated.
3. Verify whether that duration reaches `PageCurlState.prev()/next()` or is dropped.
4. Wire the preset duration into curl navigation animation.
5. Add a regression test for the timing mapping.
