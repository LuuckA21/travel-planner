package me.luucka.travelplanner.checklist;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import me.luucka.travelplanner.common.NotFoundException;
import me.luucka.travelplanner.trip.Trip;
import me.luucka.travelplanner.trip.TripAccess;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ChecklistService {

    // UI copy stored as data: every new trip starts with these lists, in this order
    static final List<String> DEFAULT_NAMES = List.of("Bagaglio", "Prima di partire");

    private final ChecklistRepository checklists;
    private final ChecklistItemRepository items;
    private final TripAccess access;

    ChecklistService(ChecklistRepository checklists, ChecklistItemRepository items, TripAccess access) {
        this.checklists = checklists;
        this.items = items;
        this.access = access;
    }

    public void createDefaults(Trip trip) {
        for (int i = 0; i < DEFAULT_NAMES.size(); i++) {
            checklists.save(new Checklist(trip, DEFAULT_NAMES.get(i), i));
        }
    }

    @Transactional(readOnly = true)
    public List<ChecklistResponse> listForTrip(UUID tripId) {
        var byList = items.findByChecklistTripIdOrderByPositionAsc(tripId).stream()
                .collect(Collectors.groupingBy(i -> i.getChecklist().getId(),
                        Collectors.mapping(ChecklistService::response, Collectors.toList())));
        return checklists.findByTripIdOrderByPositionAsc(tripId).stream()
                .map(c -> response(c, byList.getOrDefault(c.getId(), List.of()))).toList();
    }

    ChecklistResponse create(UUID tripId, UUID userId, ChecklistRequest r) {
        Trip trip = access.requireOwned(tripId, userId);
        return response(checklists.save(new Checklist(trip, r.name(), checklists.nextPosition(tripId))), List.of());
    }

    ChecklistResponse rename(UUID id, UUID userId, ChecklistRequest r) {
        Checklist c = requireChecklist(id, userId);
        c.setName(r.name());
        return response(c, items.findByChecklistIdOrderByPositionAsc(id).stream().map(ChecklistService::response).toList());
    }

    void delete(UUID id, UUID userId) {
        checklists.delete(requireChecklist(id, userId));
    }

    ItemResponse addItem(UUID checklistId, UUID userId, ItemRequest r) {
        Checklist c = requireChecklist(checklistId, userId);
        return response(items.save(new ChecklistItem(c, r.text(), items.nextPosition(checklistId))));
    }

    ItemResponse patchItem(UUID id, UUID userId, ItemPatch p) {
        ChecklistItem i = requireItem(id, userId);
        i.patch(p);
        return response(i);
    }

    void deleteItem(UUID id, UUID userId) {
        items.delete(requireItem(id, userId));
    }

    private Checklist requireChecklist(UUID id, UUID userId) {
        return checklists.findByIdAndTripOwnerId(id, userId).orElseThrow(NotFoundException::new);
    }

    private ChecklistItem requireItem(UUID id, UUID userId) {
        return items.findByIdAndChecklistTripOwnerId(id, userId).orElseThrow(NotFoundException::new);
    }

    private static ItemResponse response(ChecklistItem i) {
        return new ItemResponse(i.getId(), i.getText(), i.isDone(), i.getPosition());
    }

    private static ChecklistResponse response(Checklist c, List<ItemResponse> items) {
        return new ChecklistResponse(c.getId(), c.getName(), c.getPosition(), items);
    }
}
