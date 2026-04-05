package com.seanick80.drawingapp;

import org.junit.jupiter.api.Tag;
import java.lang.annotation.*;

/** Render test — full fractal renders, golden checksums. Target: < 5s. */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Tag("large")
public @interface LargeTest {}
