package me.luucka.travelplanner.checklist;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface ChecklistItemRepository extends JpaRepository<ChecklistItem, UUID> {

    Optional<ChecklistItem> findByIdAndChecklistTripOwnerId(UUID id, UUID ownerId);

    List<ChecklistItem> findByChecklistIdOrderByPositionAsc(UUID checklistId);

    List<ChecklistItem> findByChecklistTripIdOrderByPositionAsc(UUID tripId);

    @Query("select coalesce(max(i.position), -1) + 1 from ChecklistItem i where i.checklist.id = :checklistId")
    int nextPosition(UUID checklistId);
}
