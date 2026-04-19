package com.example.calendar.controller;

import com.example.calendar.event.CalendarEvent;
import com.example.calendar.event.CalendarEventForm;
import com.example.calendar.event.EventService;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
// Serves the dashboard and routes event create/update/delete requests to the service layer.
public class DashboardController {

    private final EventService eventService;

    public DashboardController(EventService eventService) {
        this.eventService = eventService;
    }

    @GetMapping("/events")
    public String dashboard(Model model,
                            Principal principal,
                            @RequestParam(value = "panel", required = false) String panel) {
        if (!model.containsAttribute("eventForm")) {
            model.addAttribute("eventForm", new CalendarEventForm());
        }
        // Reads the current user's events from PostgreSQL and puts them into the Thymeleaf model.
        populateDashboard(model, principal.getName());
        model.addAttribute("activePanel", panel == null ? "events" : panel);
        return "dashboard";
    }

    @PostMapping("/events")
    public String createEvent(@Valid @ModelAttribute("eventForm") CalendarEventForm eventForm,
                              BindingResult bindingResult,
                              Model model,
                              Principal principal,
                              RedirectAttributes redirectAttributes) {
        validateTimeRange(eventForm, bindingResult);
        if (bindingResult.hasErrors()) {
            populateDashboard(model, principal.getName());
            return "dashboard";
        }

        // EventService translates the form into an entity and inserts a row into the events table.
        eventService.createEvent(eventForm, principal.getName());
        redirectAttributes.addFlashAttribute("successMessage", "Event saved.");
        return "redirect:/events";
    }

    @GetMapping("/events/{eventId}/edit")
    public String editEvent(@PathVariable Long eventId, Model model, Principal principal) {
        // Loads a single event row that belongs to the signed-in user.
        CalendarEvent event = eventService.getEventForUser(eventId, principal.getName());
        model.addAttribute("editingEventId", event.getId());
        model.addAttribute("eventForm", eventService.toForm(event));
        model.addAttribute("activePanel", "events");
        populateDashboard(model, principal.getName());
        return "dashboard";
    }

    @PostMapping("/events/{eventId}")
    public String updateEvent(@PathVariable Long eventId,
                              @Valid @ModelAttribute("eventForm") CalendarEventForm eventForm,
                              BindingResult bindingResult,
                              Model model,
                              Principal principal,
                              RedirectAttributes redirectAttributes) {
        validateTimeRange(eventForm, bindingResult);
        if (bindingResult.hasErrors()) {
            model.addAttribute("editingEventId", eventId);
            model.addAttribute("activePanel", "events");
            populateDashboard(model, principal.getName());
            return "dashboard";
        }

        // JPA updates the existing events row inside EventService's transaction.
        eventService.updateEvent(eventId, eventForm, principal.getName());
        redirectAttributes.addFlashAttribute("successMessage", "Event updated.");
        return "redirect:/events";
    }

    @PostMapping("/events/{eventId}/delete")
    public String deleteEvent(@PathVariable Long eventId,
                              Principal principal,
                              RedirectAttributes redirectAttributes) {
        // Deletes the matching event row after verifying the event belongs to the current user.
        eventService.deleteEvent(eventId, principal.getName());
        redirectAttributes.addFlashAttribute("successMessage", "Event deleted.");
        return "redirect:/events";
    }

    private void populateDashboard(Model model, String username) {
        // One service call fetches the user's event list; the repository turns into a SQL SELECT.
        List<CalendarEvent> events = eventService.listEventsForUser(username);
        model.addAttribute("events", events);
        model.addAttribute("username", username);
    }

    private void validateTimeRange(CalendarEventForm eventForm, BindingResult bindingResult) {
        if (eventForm.getStartTime() != null
                && eventForm.getEndTime() != null
                && eventForm.getEndTime().isBefore(eventForm.getStartTime())) {
            bindingResult.rejectValue("endTime", "time.invalid", "End time must be after the start time.");
        }
    }
}