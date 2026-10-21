package me.luucka.travelplanner.checklist;

import java.util.List;
import java.util.UUID;

public record ChecklistResponse(UUID id, String name, int position, List<ItemResponse> items) {}
