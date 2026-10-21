package me.luucka.travelplanner.checklist;

import java.util.UUID;

public record ItemResponse(UUID id, String text, boolean done, int position) {}
