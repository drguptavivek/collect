# Accessibility Checklist & Features

This document outlines the accessibility standards and features implemented in the AIIMS ODK Collect fork, targeting **WCAG 2.1 Level AA** compliance for the custom authentication and security flows.

---

## 1. Visual & Contrast
- **Color Contrast**: All text and meaningful UI elements meet the mandatory **4.5:1** contrast ratio (Standard) or **3:1** (Large text).
- **Dark Mode Support**: AIIMS-specific screens (Login, PIN, Settings) are fully themed for dark mode, ensuring consistent readability without eye strain.
- **Scalable Type**: All layouts use `sp` units for text and `dp` for containers, allowing the OS-level text scaling to work seamlessly.

## 2. Screen Reader Support (TalkBack)
- **Content Descriptions**: Every interactive element (buttons, text inputs, images) has an explicit `contentDescription` or `hint`.
- **Field Labeling**: `TextInputLayout` is used to provide permanent accessibility labels for all input fields.
- **Focus Order**: The traversal order for TalkBack is logically structured (Top-to-Bottom, Left-to-Right) on all custom screens.
- **Dynamic Updates**: State changes (e.g., "Login failed", "PIN set") use `AnnounceForAccessibility` or accessibility live regions to notify users immediately.

## 3. Keyboard & Switch Navigation
- **Logical Traversal**: Tab order is consistently mapped to the visual layout.
- **Visual Focus Indicators**: Standard Material focus states (highlight/glow) are preserved to clearly indicate the currently focused element.
- **Action Mapping**: All clickable elements are reachable via hardware keyboard (Enter/Space) and external switch devices.

## 4. Touch Targets
- **Minimum Size**: All interactive elements (buttons, checkboxes, links) maintain a minimum touch target size of **48x48dp**.
- **Spacing**: Appropriate padding is applied between interactive elements to prevent accidental touches.

## 5. Error Handling & Feedback
- **In-line Errors**: `setError()` on `TextInputLayout` provides immediate, accessible feedback for invalid data (e.g., wrong PIN format).
- **Haptic Feedback**: Subtle vibrations are triggered on failed attempts to provide multi-modal feedback.

---

## References
- **WCAG 2.1**: [Web Content Accessibility Guidelines](https://www.w3.org/WAI/standards-guidelines/wcag/)
- **Android Accessibility**: [Official Developer Guide](https://developer.android.com/guide/topics/ui/accessibility)
- **Beads Issues**: `collect-cdk` (Keyboard nav), `collect-4h6` (Screen reader), `collect-c6l` (Contrast), `collect-cu5` (Touch targets).
