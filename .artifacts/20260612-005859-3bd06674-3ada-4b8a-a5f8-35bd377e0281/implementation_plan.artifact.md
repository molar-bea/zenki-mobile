# Implementation Plan - Checklist Progress Screen

The goal is to build a "Checklist Progress" screen that displays a student's admission requirements, allows status updates, and supports offline-first synchronization with Supabase.

## User Review Required

> [!NOTE]
> The project is implemented in **Kotlin/Jetpack Compose**, although the prompt mentioned Flutter. I will proceed with the native Android implementation to match the existing codebase.

- **Status Mapping**: I will map the UI labels to the following Supabase enum values:
  - To-Do -> `prepared`
  - On-Going -> `pending_review`
  - Completed -> `verified`
- **Active Task**: I will highlight the first task with status `pending_review` (ON-GOING) as the "active" task with a blue border.

## Proposed Changes

### Data & Service Layer

#### [ChecklistService.kt](file:///C:/Users/gian suico/StudioProjects/zenki-mobile/app/src/main/java/services/ChecklistService.kt)

- Fix `getChecklistForUser` to use `prepared` instead of `todo` for default status.
- Implement `updateChecklistStatus` to update Supabase and the local database.
- Integrate `DatabaseService` for local caching and offline support.

---

### ViewModel Layer

#### [AppViewModel.kt](file:///C:/Users/gian suico/StudioProjects/zenki-mobile/app/src/main/java/com/symphonix/enrollmate/AppViewModel.kt)

- Add `_checklist` `MutableStateFlow<List<ChecklistProgressWithRequirement>>`.
- Implement `refreshChecklist()` to fetch data and update the flow.
- Implement `updateChecklistStatus()` with optimistic update logic (updates the flow immediately, reverts on failure).

---

### UI Layer

#### [ChecklistScreen.kt](file:///C:/Users/gian suico/StudioProjects/zenki-mobile/app/src/main/java/com/symphonix/enrollmate/ui/screens/ChecklistScreen.kt)

- Update `ChecklistScreen` to observe the `checklist` state from `AppViewModel`.
- Modify `TaskCard` to support a blue border for active tasks.
- Implement a `DropdownMenu` or `ModalBottomSheet` triggered by clicking the `StatusPill`.
- Map UI status labels to backend enum values correctly.

## Verification Plan

### Automated Tests
- I will verify the logic by running the app and inspecting logs, as there are no existing unit tests for UI components.

### Manual Verification
1. **Status Update**:
   - Change a task status from "To-Do" to "On-Going".
   - Verify the pill color changes and the card gains a blue border.
   - Verify the change is persisted in Supabase.
2. **Optimistic Update**:
   - Simulate a network failure (or just observe the UI change before the network call completes).
   - Ensure the UI reflects the change immediately.
3. **Offline Sync**:
   - Check the `DatabaseService` logs to ensure data is upserted locally.
