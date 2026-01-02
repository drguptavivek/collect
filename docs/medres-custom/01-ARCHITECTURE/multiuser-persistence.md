# Feature: Multi-User Project Persistence (collect-8p4) - [IMPLEMENTED]

> **Status**: Implemented
> **Architecture Ref**: [medres_vs_standard_boundary.md](medres_vs_standard_boundary.md)

## Objective
Enable multi-user support on a single shared device by preserving project-specific data (forms, instances, settings) when switching between projects.

## Problem Statement
Currently, switching projects for MEDRES authentication (especially manual configuration) might lead to data loss if existing projects are deleted. In field use, multiple users may share a device, and each user's project context (including their respective forms and drafts) must be preserved on disk even when their project is not currently "active".

## Requirements
1.  **Non-Destructive Switching**: When changing project configuration (e.g., via QR scan or settings icon), the app should search for an existing project matching the server URL/Project ID.
2.  **Persistence**: Do NOT delete existing project records or files on disk when a new project is configured.
3.  **Single-Project Visibility**: Only the "currently active" project should be visible and accessible through the MEDRES login and main menu context.
4.  **Shared Device Safety**: Ensure that switching back to a previous project ID restores all ODK-related data (blank forms, saved instances) for that project.

## Implementation Strategy
- Refactor `MedresLoginActivity.manualConfigureProject` to implement a "Find or Create" pattern.
- Leverage ODK's existing `ProjectsRepository` to manage multiple project UUIDs.
- Ensure `ProjectCleaner` (data isolation) is only triggered on explicit user-initiated resets or logout (if configured), rather than on every project configuration change.
