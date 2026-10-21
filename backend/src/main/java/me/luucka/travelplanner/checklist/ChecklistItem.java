package me.luucka.travelplanner.checklist;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
public class ChecklistItem {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "checklist_id")
    private Checklist checklist;

    private String text;
    private boolean done;
    private int position;

    @CreationTimestamp
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;

    protected ChecklistItem() {}

    ChecklistItem(Checklist checklist, String text, int position) {
        this.checklist = checklist;
        this.text = text;
        this.position = position;
    }

    void patch(ItemPatch p) {
        if (p.text() != null) {
            this.text = p.text();
        }
        if (p.done() != null) {
            this.done = p.done();
        }
    }

    UUID getId() {
        return id;
    }

    Checklist getChecklist() {
        return checklist;
    }

    String getText() {
        return text;
    }

    boolean isDone() {
        return done;
    }

    int getPosition() {
        return position;
    }
}
