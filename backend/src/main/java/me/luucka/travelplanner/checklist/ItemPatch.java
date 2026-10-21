package me.luucka.travelplanner.checklist;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Null fields are left unchanged. */
record ItemPatch(@Size(max = 300) @Pattern(regexp = "(?s).*\\S.*") String text,
                 Boolean done) {}
