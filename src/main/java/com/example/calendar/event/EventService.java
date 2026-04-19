package com.example.calendar.event;

import com.example.calendar.user.UserAccount;
import com.example.calendar.user.UserService;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
// Business logic for translating web form input into event entities and repository operations.
public class EventService {

    private final CalendarEventRepository calendarEventRepository;
    private final UserService userService;

    public EventService(CalendarEventRepository calendarEventRepository, UserService userService) {
        this.calendarEventRepository = calendarEventRepository;
        this.userService = userService;
    }

    @Transactional(readOnly = true)
    public List<CalendarEvent> listEventsForUser(String username) {
        UserAccount owner = userService.getByUsername(username);
        // Repository method becomes a SQL SELECT against the events table filtered by owner_id.
        return calendarEventRepository.findAllByOwnerOrderByEventDateAscStartTimeAscIdAsc(owner);
    }

    @Transactional
    public void createEvent(CalendarEventForm form, String username) {
        UserAccount owner = userService.getByUsername(username);
        CalendarEvent event = new CalendarEvent();
        applyForm(event, form, owner);
        // save(...) issues an INSERT because this entity has no id yet.
        calendarEventRepository.save(event);
    }

    @Transactional(readOnly = true)
    public CalendarEvent getEventForUser(Long eventId, String username) {
        UserAccount owner = userService.getByUsername(username);
        // Fetches one event row and enforces ownership in the same query.
        return calendarEventRepository.findByIdAndOwner(eventId, owner)
                .orElseThrow(() -> new IllegalArgumentException("Event not found"));
    }

    @Transactional
    public void updateEvent(Long eventId, CalendarEventForm form, String username) {
        UserAccount owner = userService.getByUsername(username);
        CalendarEvent event = calendarEventRepository.findByIdAndOwner(eventId, owner)
                .orElseThrow(() -> new IllegalArgumentException("Event not found"));
        applyForm(event, form, owner);
        // Because the entity is managed inside a transaction, Hibernate writes the UPDATE on commit.
    }

    @Transactional
    public void deleteEvent(Long eventId, String username) {
        CalendarEvent event = getEventForUser(eventId, username);
        // delete(...) turns into a SQL DELETE for the matching events row.
        calendarEventRepository.delete(event);
    }

    public CalendarEventForm toForm(CalendarEvent event) {
        CalendarEventForm form = new CalendarEventForm();
        form.setTitle(event.getTitle());
        form.setEventDate(event.getEventDate());
        form.setStartTime(event.getStartTime());
        form.setEndTime(event.getEndTime());
        form.setDescription(event.getDescription());
        return form;
    }

    private void applyForm(CalendarEvent event, CalendarEventForm form, UserAccount owner) {
        // Copies validated form fields into the entity object that Hibernate persists.
        event.setTitle(form.getTitle().trim());
        event.setEventDate(form.getEventDate());
        event.setStartTime(form.getStartTime());
        event.setEndTime(form.getEndTime());
        event.setDescription(form.getDescription() == null ? null : form.getDescription().trim());
        event.setOwner(owner);
    }
}