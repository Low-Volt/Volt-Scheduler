package com.example.calendar.event;

import com.example.calendar.user.UserAccount;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

// Repository abstraction for CRUD operations on the events table.
public interface CalendarEventRepository extends JpaRepository<CalendarEvent, Long> {

    // Spring Data derives a SELECT query that returns only this user's events in display order.
    List<CalendarEvent> findAllByOwnerOrderByEventDateAscStartTimeAscIdAsc(UserAccount owner);

    // Used to make sure users can only load/update/delete their own rows.
    Optional<CalendarEvent> findByIdAndOwner(Long id, UserAccount owner);
}