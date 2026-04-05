package com.seanick80.drawingapp;

import org.junit.jupiter.api.Tag;
import java.lang.annotation.*;

/** Unit test — no I/O, no UI, no rendering. Target: < 50ms. */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Tag("small")
public @interface SmallTest {}
