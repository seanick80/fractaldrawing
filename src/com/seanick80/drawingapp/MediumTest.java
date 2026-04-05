package com.seanick80.drawingapp;

import org.junit.jupiter.api.Tag;
import java.lang.annotation.*;

/** Integration test — file I/O, Swing components, multi-class. Target: < 500ms. */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Tag("medium")
public @interface MediumTest {}
