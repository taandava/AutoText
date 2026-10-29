# ULTIMATE MASTER DIRECTIVE
# COMPLETE ANDROID APPLICATION MODERNIZATION
## Material 3 Expressive | Premium Orange | Compact UI | Full-Width | Edge-to-Edge | System Adaptive | Professional Codebase Organization

**Directive type:** Autonomous, repository-wide Android UI/UX modernization and safe refactoring.

**Primary objective:** Completely transform the existing Android application's glassmorphism-based interface into a cohesive, production-quality **Material 3 Expressive Android experience**, using an elegant orange brand identity, compact information-dense layouts, intelligent full-width responsiveness, native system integration, consistent expressive motion, and professionally organized code and resources.

**Secondary objective:** Audit and improve file names, class names, resource names, package organization, and UI architecture without breaking any existing functionality or compatibility contract.

**Absolute priority:** Preserve the complete working application while modernizing its presentation.

---

# 1. YOUR ROLE

Operate as a coordinated team of specialists:

1. Principal Android Software Architect
2. Senior Kotlin/Java Engineer
3. Jetpack Compose and Android Views Specialist
4. Material 3 Expressive Design Systems Architect
5. Professional Android UI/UX Designer
6. Responsive and Adaptive Layout Engineer
7. Android System Integration Specialist
8. Motion and Interaction Designer
9. Accessibility and Usability Engineer
10. Codebase Refactoring and Naming Specialist
11. Performance Optimization Engineer
12. Android Quality Assurance and Regression Testing Engineer

Make decisions using the actual repository, established Android engineering conventions, official documentation, and verified dependency capabilities.

Do not assume that a particular technology or UI architecture is present until it has been inspected.

Do not blindly apply generic redesign patterns.

Every modification must improve visual consistency, usability, responsiveness, accessibility, maintainability, or performance.

---

# 2. NON-NEGOTIABLE RULES

## 2.1 Preserve all functionality

Do not remove, disable, replace, simulate, or silently change existing functionality.

Preserve:

- Navigation and destinations
- Business logic
- Authentication and authorization
- API integrations
- Network operations
- Database operations and user data
- Application settings
- Background tasks and services
- Notifications
- Permissions
- Deep links
- File handling
- Search and filtering
- Sorting
- User inputs and forms
- Existing gestures and interactions
- Application state management
- Error handling
- Existing accessibility behavior

Never replace live functionality with placeholders, mock data, fake callbacks, or decorative components.

## 2.2 Inspect before modifying

Read the relevant project structure and dependencies before editing.

Determine whether the application uses:

- Jetpack Compose
- Android XML Views
- Hybrid Compose and Views
- Custom views
- Multiple modules
- Other UI frameworks

Use the correct modernization approach for the actual architecture.

A complete framework migration is not authorized merely because another framework offers convenient design components.

## 2.3 Centralize the design system

Use one authoritative design system for colors, typography, shapes, spacing, motion, and component defaults.

Avoid multiple competing themes.

## 2.4 Prioritize compact layouts

Do not automatically create oversized headers, cards, controls, empty spaces, or padding.

Compact does not mean unreadable or inaccessible.

## 2.5 Utilize available screen width

Phone screens should use their available width effectively.

Large screens should use adaptive arrangements rather than stretched phone layouts.

## 2.6 Follow the Android system

Respect current system settings, navigation conventions, display configurations, and accessibility requirements.

## 2.7 Rename files intelligently

Rename files, classes, resources, and packages only when their current names are misleading, inconsistent, obsolete, or poorly structured.

Never rename everything simply for the sake of renaming.

## 2.8 Validate incrementally

Avoid massive unverified rewrites.

Make logical, reviewable changes and test after each migration batch.

---

# 3. FULL REPOSITORY DISCOVERY

Perform a recursive project audit.

Inspect all relevant application modules, source sets, and resource directories.

Identify:

- Kotlin files
- Java files
- XML layouts
- Composable functions
- Activities
- Fragments
- ViewModels
- Navigation graphs
- UI state models
- Repositories
- Dependency injection definitions
- Existing themes
- Colors and styles
- Typography definitions
- Shape resources
- Drawable resources
- Fonts and icons
- Animations
- Lists and grids
- Dialogs and bottom sheets
- Search fields and forms
- Navigation components
- Build scripts
- Version catalogs
- AndroidManifest.xml files
- Testing infrastructure
- Existing responsive layout logic
- System insets and edge-to-edge handling
- Existing glassmorphism helpers

Identify the actual UI entry points and all navigation destinations.

Find screens that are dynamically registered or conditionally displayed.

Inspect custom components, not just screen-level files.

Do not mistake unused or generated code for live UI implementation.

Exclude generated build artifacts, caches, external dependencies, and irrelevant binary files from modification.

## Required audit document

Create:

`docs/material3-expressive-migration.md`

Record the current architecture, screen inventory, shared components, proposed changes, constraints, and verification results.

Use this document throughout the migration.

---

# 4. TARGET DESIGN LANGUAGE

The final application must follow **Google Material 3 Expressive**, adapted into a professional, compact orange-themed product.

Target qualities:

- Clean
- Modern
- Compact
- Full-width
- Responsive
- Native
- Expressive
- Consistent
- Efficient
- Accessible
- Visually refined
- Smooth
- Readable
- Functionally complete

Use official Material components and supported expressive variants wherever appropriate.

Expression should come from purposeful visual hierarchy, typography, shapes, responsive interactions, and motion.

Do not create expressive effects randomly.

Avoid making the application look like a collection of unrelated design experiments.

## Design hierarchy

Prioritize, in order:

1. Functionality
2. Accessibility
3. Layout correctness
4. Information clarity
5. System compatibility
6. Consistency
7. Performance
8. Visual expression

---

# 5. ORANGE MATERIAL COLOR SYSTEM

## 5.1 Brand colors

Use these initial brand references:

| Role | Reference |
|---|---|
| Primary Orange | `#E76F00` |
| Accent Orange | `#FFB77A` |
| Light Background | `#FFF3E7` |
| Warm Surface | `#F8E2CF` |
| Supporting Brown | `#563316` |
| Dark Text | `#1F1B17` |
| Success Reference | `#BBD89E` |
| Error Reference | `#BA1A1A` |

These values are color references, not an entire finalized Material scheme.

Generate complete accessible tonal color schemes.

Implement all applicable Material semantic roles, including primary, secondary, tertiary, surface, background, error, container, outline, inverse, and their associated on-colors.

Use official supported APIs.

## 5.2 Color behavior

Orange should primarily appear in:

- Main actions
- Active navigation
- Selected controls
- Focus indicators
- Important highlights
- Progress elements
- Brand accents

Warm neutral surfaces should dominate ordinary content.

Avoid orange saturation across the entire interface.

Do not use orange for all borders or secondary labels.

Keep success, warning, and error semantics visually distinct.

## 5.3 Light mode

Use:

- Warm neutral background
- Readable dark text
- Orange primary actions
- Subtle tonal containers
- Consistent outline colors
- Accessible selected states

## 5.4 Dark mode

Use:

- Deep neutral surfaces
- Adapted orange accents
- Appropriate dark-theme containers
- Readable light text
- Proper system-bar contrast
- Correct inverse surfaces and scrims

Never simply invert the light palette.

## 5.5 Dynamic color

Default color identity: **Brand Orange**.

Where supported, offer optional wallpaper-derived dynamic color.

Do not silently override the orange brand palette.

Theme behavior:

Color preference:
- Brand Orange (default)
- System Dynamic (optional)

Appearance preference:
- Follow System (default)
- Light
- Dark

Reuse existing preferences where available.

Persist settings safely.

---

# 6. COMPACT DESIGN SYSTEM — MANDATORY

The application must maximize useful information density while preserving readability.

Avoid artificially spacious UI.

## 6.1 Spacing scale

Use centralized spacing tokens.

Recommended starting values:

| Token | Value |
|---|---|
| Micro | 2 dp |
| Extra Small | 4 dp |
| Small | 6 dp |
| Medium | 8 dp |
| Standard | 12 dp |
| Large | 16 dp |
| Extra Large | 20 dp |
| Major Section | 24 dp |

These are layout guidelines, not replacements for official component dimensions or accessibility requirements.

Adjust based on content and window size.

## 6.2 Compact screen gutters

For compact phones, generally use 12–16 dp horizontal content gutters.

Avoid repeatedly adding padding at nested container levels.

Use full-bleed backgrounds while maintaining safe interactive gutters.

## 6.3 Compact lists

Prefer efficient row structures with:

- Compact vertical spacing
- Consistent text hierarchy
- Concise metadata
- Proper icon alignment
- Accessible touch targets
- Selective dividers
- Minimal decorative containers

Avoid boxing every row.

## 6.4 Compact cards

Use cards selectively.

Do not place every section inside large rounded rectangles.

When a card is justified, keep its padding proportional to its content.

Avoid cards nested inside multiple other cards.

## 6.5 Compact headers

Use ordinary or collapsing top bars based on actual information hierarchy.

Do not force large expressive headings onto utility screens.

Avoid redundant titles and repeated screen labels.

## 6.6 Compact controls

Use visually efficient buttons, chips, tabs, filters, and metadata groups.

Maintain appropriate Android touch target sizes, typically at least 48 dp.

Do not reduce interaction safety to make the layout smaller.

---

# 7. FULL-WIDTH LAYOUT ARCHITECTURE

Use available screen space intelligently.

## 7.1 Compact phones

Requirements:

- Appropriate full-width surfaces
- Consistent content gutters
- No arbitrary narrow parent containers
- Efficient lists
- Responsive buttons
- Properly sized search fields
- Adaptive carousels
- No unnecessary horizontal whitespace

Use width-filling APIs only where suitable.

Do not stretch icons, chips, or small actions simply to satisfy a full-width requirement.

## 7.2 Medium windows

Adapt layout structure where beneficial.

Possible techniques:

- Wider content sections
- Two-column arrangements
- Navigation rails
- Adaptive grids
- Side-by-side related controls

## 7.3 Expanded windows

Use suitable large-screen patterns:

- List-detail layouts
- Supporting panes
- Responsive grids
- Multi-column organization
- Appropriate navigation rails or drawers

Avoid excessively long text lines.

Do not merely scale up a phone screen.

## 7.4 Responsive rules

Use current window dimensions and official window-size guidance.

Do not assume device type from a fixed screen-width value.

Support resizing, rotation, and split-screen where available.

---

# 8. EDGE-TO-EDGE AND SYSTEM INSETS

Implement correct edge-to-edge behavior according to the application's SDK and official Android guidance.

## 8.1 System bar integration

Handle:

- Status bar
- Navigation bar
- Gesture navigation region
- Three-button navigation
- Display cutouts
- Relevant window decorations

Allow appropriate visual surfaces to extend behind system bars.

Keep interactive content accessible and safe.

## 8.2 Insets

Correctly handle:

- Safe drawing
- Safe gestures
- System bars
- Keyboard/IME
- Display cutouts
- Bottom navigation
- Floating controls

For Compose, use applicable WindowInsets, Scaffold padding, and consumption APIs.

For Views, use compatible WindowInsets APIs.

## 8.3 Prevent double padding

Never apply the same system inset multiple times.

Audit parent and child containers before adding padding.

Prevent excessive blank space below lists or above headers.

## 8.4 Bottom elements

Ensure:

- Last list items remain reachable
- Bottom bars do not obscure content
- FABs do not overlap system controls
- Sheets respect gesture regions
- Fixed action bars do not hide scrollable content

## 8.5 Keyboard behavior

When the keyboard appears:

- Keep the focused input visible
- Prevent overlapping buttons
- Preserve input state
- Handle IME animation smoothly
- Maintain usable bottom sheets
- Support native keyboard actions

---

# 9. COMPLETE ADAPTIVE RESPONSIVENESS

Support:

- Small phones
- Standard phones
- Large phones
- Tablets
- Foldables
- Portrait
- Landscape
- Multi-window
- Split-screen
- Display scaling
- Large font sizes
- RTL layouts
- Translated content

Avoid absolute positioning of critical controls.

Avoid rigid fixed heights for text-containing components.

Avoid hardcoded screen measurements.

Use constraints, weighted layouts, lazy containers, adaptive grids, and suitable responsive APIs.

Prevent horizontal overflow, clipped buttons, hidden labels, unreachable controls, and malformed dialogs.

---

# 10. MATERIAL 3 EXPRESSIVE COMPONENT MIGRATION

Audit and migrate all applicable component families.

## Navigation

- Top app bars
- Bottom navigation
- Navigation bars
- Navigation rails
- Drawers
- Tabs
- Destination indicators
- Predictive back where supported

## Actions

- Filled buttons
- Tonal buttons
- Outlined buttons
- Text buttons
- Icon buttons
- FABs
- Extended FABs
- Segmented buttons

## Selection

- Chips
- Checkboxes
- Radio buttons
- Switches
- Sliders
- Selection indicators

## Content

- Lists
- Grids
- Cards
- Carousels
- Images
- Status indicators
- Badges
- Progress components
- Dividers

## Inputs

- Text fields
- Search fields
- Password inputs
- Dropdowns
- Menus
- Form validation
- Focus states

## Overlays

- Dialogs
- Alert dialogs
- Modal sheets
- Popups
- Tooltips
- Snackbars

## States

- Loading
- Empty
- Error
- Success
- Offline
- Disabled
- Selected
- Focused
- Pressed
- Expanded
- Collapsed

Prefer official supported Material components over unnecessary custom recreations.

Keep component appearance consistent across the app.

---

# 11. CONTAINERLESS DYNAMIC LISTS

Modernize list-based screens using appropriate Material patterns.

Where suitable, replace repetitive boxed grids with clean sequential layouts.

Use:

- Consistent alignment
- Clear content hierarchy
- Efficient vertical rhythm
- Meaningful grouping
- Stable row identity
- Proper selection states
- Smooth state transitions
- Accessible controls

For Compose, prefer suitable lazy layouts with stable keys.

For Views, retain efficient RecyclerView behavior and diffing.

Preserve scroll position.

Do not introduce expensive animations for frequent updates.

Do not convert every grid into a list without assessing its purpose.

---

# 12. RESPONSIVE MATERIAL CAROUSELS

Use official Material 3 carousel patterns where useful.

Suitable contexts include:

- Featured content
- Recommendations
- Media collections
- Category previews
- Discovery sections

Evaluate supported:

- Multi-browse carousels
- Uncontained carousels
- Hero carousels
- Full-screen patterns

Verify actual library support and API signatures before implementing.

Requirements:

- Adaptive item widths
- Appropriate visible item counts
- Partial-item previews where suitable
- Smooth native scrolling
- Accessible semantics
- Stable item keys
- Proper edge padding
- Predictable interactions
- No clipping
- No unnecessary nested scrolling

Keep carousel heights proportionate to screen size.

Do not add carousels where ordinary lists or grids are more usable.

---

# 13. EXPRESSIVE MOTION SYSTEM

Create centralized motion specifications.

Prefer official Material defaults and supported expressive motion.

Apply appropriate motion to:

- Selection changes
- Navigation
- Tabs
- Bottom sheets
- Expandable sections
- Buttons
- Chips
- List insertion/removal
- Carousel interactions
- State transitions
- Supported shared-element transitions

Motion should feel natural, fluid, responsive, and purposeful.

Avoid:

- Excessive bouncing
- Long animations
- Continuous decorative movement
- Random scaling effects
- Frequent layout remeasurement
- Flickering during recomposition
- Motion that blocks interaction
- Scroll performance regressions

Respect system animation settings.

Provide less motion where required for accessibility.

Use native ripple and appropriate haptics.

---

# 14. TYPOGRAPHY SYSTEM

Implement coherent Material typography.

Use supported roles:

- Display
- Headline
- Title
- Body
- Label

Choose appropriately readable fonts and weights.

Avoid oversized headings on routine screens.

Maintain contrast and hierarchy.

Support:

- Dynamic font scaling
- Multiline text
- RTL
- Localization
- Long labels
- Different display densities

Never force text into fixed-height containers that clip at larger font settings.

---

# 15. SHAPES AND SURFACE SYSTEM

Create a centralized shape scale.

Use rounded containers intentionally.

Use full-round shapes for suitable controls.

Avoid making every component pill-shaped.

Use tonal surfaces and spacing to communicate grouping.

Limit excessive shadows and decorative elevation.

Preserve appropriate native Material interaction states.

---

# 16. GLASSMORPHISM REMOVAL

Identify outdated presentation effects:

- Frosted glass
- Backdrop blur
- Transparent glass panels
- Decorative reflections
- Glass gradients
- White translucent outlines
- Heavy glow
- Unnecessary transparency
- Glass-specific helper components

Replace with appropriate Material surfaces, tonal containers, shapes, and native behavior.

Do not remove legitimate transparency, scrims, or image overlays required for functionality.

Remove obsolete glass dependencies only after confirming they are unused.

---

# 17. PROFESSIONAL FILE RENAMING AND ORGANIZATION

**This phase is mandatory, but renaming must be intelligent and reference-safe.**

Audit all relevant:

- Kotlin and Java files
- Screens
- Activities
- Fragments
- ViewModels
- Components
- State classes
- Models
- Repositories
- Utilities
- Packages
- XML layouts
- Drawables
- Icons
- Colors
- Themes
- Animations
- Navigation resources
- Test files
- Documentation

## 17.1 Naming conventions

| Element | Convention | Example |
|---|---|---|
| Classes | PascalCase | `SettingsViewModel.kt` |
| Compose screens | PascalCase | `SettingsScreen.kt` |
| Components | PascalCase | `AppNavigationBar.kt` |
| Functions | camelCase | `loadSettings()` |
| Variables | camelCase | `selectedTheme` |
| Constants | Idiomatic Kotlin conventions | `DEFAULT_PAGE_SIZE` |
| Packages | lowercase | `feature.settings` |
| XML resources | snake_case | `activity_main.xml` |
| Drawable resources | snake_case | `ic_settings.xml` |
| String resources | snake_case | `settings_title` |
| Tests | Descriptive type names | `SettingsViewModelTest.kt` |

Follow applicable Kotlin, Java, Android, and resource naming rules.

## 17.2 Remove poor naming

Identify names such as:

- `NewScreen`
- `FinalScreen`
- `Screen2`
- `Helper2`
- `TempView`
- `CommonUtils`
- `MainFinal`
- `GlassThemeV2`
- Ambiguous abbreviations
- Misleading class names
- Unnecessary implementation suffixes

Replace only where a genuinely clearer name exists.

Do not create excessively long names.

## 17.3 Remove obsolete glass terminology

When a glass-specific component has become a general Material component, rename it semantically.

Examples:

| Existing | Suggested |
|---|---|
| `GlassTheme.kt` | `AppTheme.kt` |
| `GlassColors.kt` | `AppColorScheme.kt` |
| `GlassCard.kt` | `AppCard.kt` |
| `FrostedButton.kt` | `AppButton.kt` |
| `GlassBottomBar.kt` | `AppNavigationBar.kt` |
| `BlurredDialog.kt` | `AppDialog.kt` |

These are examples, not automatic replacements.

Choose names based on actual responsibilities.

Avoid prefixing every file with `Material3ExpressiveOrange`.

## 17.4 Safe reference updating

When renaming a file or symbol, update all relevant references:

- Imports
- Package declarations
- Call sites
- Constructors
- XML references
- Navigation graphs
- Manifest entries
- Dependency injection bindings
- Resource references
- Build configurations
- Reflection-based references
- Serialization references where affected
- ProGuard/R8 rules
- JNI bindings where relevant
- Tests
- Documentation

Prefer IDE-aware refactoring or reliable symbol-aware tools.

Do not assume a global text replacement is safe.

## 17.5 Protect compatibility contracts

Do not casually rename:

- Application ID
- Published package identifiers
- Public API contracts
- Persisted preference keys
- Database tables or columns
- Serialized field names
- Deep links
- Intent actions
- Content provider authorities
- Backend fields
- Persisted filesystem paths

Do not change these simply to improve naming.

Preserve existing-user upgrade compatibility.

Any necessary contract change must include a deliberate migration and compatibility strategy.

## 17.6 Package organization

Prefer clear organization based on the existing architecture.

An illustrative feature-based layout:

```text
app/
└── src/main/
    ├── java/com/example/app/
    │   ├── core/
    │   │   ├── designsystem/
    │   │   │   ├── theme/
    │   │   │   ├── components/
    │   │   │   └── motion/
    │   │   ├── navigation/
    │   │   └── util/
    │   ├── feature/
    │   │   ├── home/
    │   │   ├── settings/
    │   │   └── ...
    │   └── MainActivity.kt
    └── res/
        ├── drawable/
        ├── layout/
        ├── values/
        └── values-night/
```

Do not force this structure onto an incompatible application.

Avoid unnecessary module restructuring.

Avoid moving files solely for aesthetic reasons.

## 17.7 File responsibility

Prefer descriptive files with cohesive responsibilities.

Avoid enormous unrelated utility files.

Avoid excessive fragmentation into hundreds of tiny files.

Choose sensible component boundaries.

## 17.8 Rename tracking

Create:

`docs/file-renaming-map.md`

Record:

- Old path
- New path
- Reason
- Affected references
- Compatibility risk
- Verification result

Preserve Git history where practical.

Do not overwrite user changes.

---

# 18. DESIGN-SYSTEM FILE CONVENTIONS

Where suitable, organize core design definitions with descriptive names:

- `AppTheme.kt`
- `AppColorScheme.kt`
- `AppTypography.kt`
- `AppShapes.kt`
- `AppSpacing.kt`
- `AppMotion.kt`
- `AppDimensions.kt` when actually needed

Do not create duplicate abstractions for the same tokens.

Use existing conventions where they are already coherent.

Separate reusable design primitives from feature-specific UI.

---

# 19. SCREEN-BY-SCREEN MIGRATION

For every screen:

1. Identify its purpose.
2. Inspect existing behavior.
3. Identify navigation and state dependencies.
4. Review visual hierarchy.
5. Apply the orange theme.
6. Apply compact spacing.
7. Utilize available width.
8. Correct edge-to-edge handling.
9. Replace obsolete glass styling.
10. Use suitable Material components.
11. Modernize relevant lists and carousels.
12. Apply meaningful motion.
13. Standardize component naming.
14. Update references where renamed.
15. Verify light and dark modes.
16. Verify system settings.
17. Verify responsiveness.
18. Verify accessibility.
19. Test existing interactions.
20. Update migration documentation.

Do not stop after changing the home screen.

Include rarely visited screens and nested UI states.

---

# 20. ANDROID SYSTEM INTEGRATION

Follow applicable system settings.

## Appearance

- Follow system dark mode by default.
- Preserve selected theme preferences.
- Adapt system bar icon appearance.
- Avoid theme-flash behavior.

## Display

- Respect font size.
- Respect display scaling.
- Support different densities.
- Handle window resizing.

## Navigation

- Support gesture navigation.
- Support three-button navigation.
- Preserve back behavior.
- Support predictive back where appropriate.

## Accessibility

- Respect screen-reader semantics.
- Support focus navigation.
- Ensure usable touch targets.
- Reduce unnecessary motion.
- Preserve readable contrast.

## Localization

- Support RTL where applicable.
- Respect system locale.
- Use locale-aware formatting.
- Handle translated text expansion.

## Keyboard

- Respect IME insets.
- Prevent input obstruction.
- Use native keyboard actions.
- Preserve form state.

---

# 21. ACCESSIBILITY REQUIREMENTS

Target applicable Android and WCAG AA guidelines.

Verify:

- Text contrast
- Non-text contrast
- Touch target size
- Content descriptions
- Screen-reader semantics
- Focus order
- Keyboard accessibility
- Selected-state announcements
- Expanded-state announcements
- Error messages
- Large font scaling
- Color-independent communication
- Motion accessibility

Compactness must never compromise essential accessibility.

---

# 22. PERFORMANCE REQUIREMENTS

Keep rendering efficient.

Prioritize:

- Stable scrolling
- Minimal overdraw
- Efficient Compose recomposition
- Stable lazy-list keys
- Efficient RecyclerView updates
- Appropriate state hoisting
- Efficient image rendering
- Controlled animations
- Avoidance of obsolete blur passes
- Memory-conscious components
- Correct lifecycle behavior

Do not introduce heavyweight effects merely to demonstrate Material Expressive motion.

Avoid unnecessary dependencies.

---

# 23. ARCHITECTURE PRESERVATION

Respect existing architecture, whether MVVM, MVI, layered, feature-based, modular, or another established pattern.

Do not rebuild working infrastructure for UI modernization.

Preserve:

- ViewModels
- Repositories
- Use cases
- State flows
- Data sources
- Dependency injection
- Navigation contracts
- Persistence
- Networking

Refactor only when required to eliminate duplicated design implementations, correct unsafe coupling, or support the new UI consistently.

Keep UI-only changes separate from logic refactoring where practical.

---

# 24. SAFE AUTONOMOUS EXECUTION

Work through the repository independently without requiring confirmation for routine, reversible implementation decisions.

However:

- Do not perform destructive Git operations.
- Do not discard unrelated work.
- Do not overwrite uncommitted changes.
- Do not delete user data.
- Do not change application identity.
- Do not modify external contracts unnecessarily.
- Do not perform unreviewable mass renames.
- Do not fabricate test results.

Before substantial changes, establish a safe version-control checkpoint or dedicated working branch where appropriate and permitted.

Make targeted, reviewable changes.

If a proposed change is highly risky, prefer a compatible implementation or document the limitation rather than introducing a destructive regression.

---

# 25. PHASED EXECUTION PLAN

## Phase 1 — Repository Audit

Inspect project structure, architecture, screens, resources, styling, and dependencies.

Create migration documentation.

## Phase 2 — Naming and Architecture Plan

Audit file and symbol naming.

Create the proposed rename mapping.

Identify compatibility-sensitive identifiers.

Do not execute risky renames blindly.

## Phase 3 — Central Theme Foundation

Implement orange Material color schemes, typography, shapes, spacing, and motion tokens.

Verify basic rendering and compilation.

## Phase 4 — Edge-to-Edge Foundation

Correct system bar behavior, safe-area handling, IME insets, and window layout rules.

## Phase 5 — Reusable Components

Modernize shared navigation, buttons, lists, cards, inputs, dialogs, sheets, and states.

## Phase 6 — Screen Migration

Migrate all UI screens in manageable groups.

Apply the compact, full-width, responsive design system.

## Phase 7 — Safe File Renaming

Rename obsolete or misleading files and symbols using reference-aware tools.

Update all affected references.

Verify compilation and navigation after each group.

Rename earlier when needed to establish correct shared-component structure, but avoid mixing unrelated large changes.

## Phase 8 — Lists and Carousels

Modernize content patterns appropriately.

Maintain usability and scrolling performance.

## Phase 9 — Expressive Motion

Apply centralized native-feeling animations to meaningful interactions.

## Phase 10 — Responsive Optimization

Test compact, medium, expanded, portrait, landscape, and resized layouts.

## Phase 11 — Cleanup

Remove unused glass resources, redundant themes, duplicate components, and obsolete styling code.

Only remove items confirmed unused.

## Phase 12 — Verification

Build, lint, test, inspect, and fix introduced regressions.

## Phase 13 — Final Audit

Verify all screens, naming consistency, accessibility, theme behavior, responsiveness, and functionality.

---

# 26. CONTINUOUS PROGRESS AND PROJECT MEMORY

Maintain two documents:

`docs/material3-expressive-migration.md`

`docs/file-renaming-map.md`

The first must track:

- Architecture
- Design decisions
- Screen inventory
- Component mapping
- Completed work
- Pending work
- Known issues
- Test results
- Verification status
- Next logical actions

The second must track:

- Old and new names
- Rename reasons
- Reference changes
- Compatibility considerations
- Verification results

Update documents after meaningful work batches.

When continuing after an interrupted coding session, read these documents and check the current repository state.

Do not rely on stale assumptions.

Do not repeatedly scan unrelated files when their existing inventory remains valid.

Use context and tokens efficiently.

---

# 27. QUALITY GATES

After each batch, check:

- Compilation
- Imports
- Resource references
- UI state behavior
- Navigation
- Affected tests
- Theme compatibility
- Dependency compatibility
- Naming consistency

For applicable screens also verify:

- Compact spacing
- Full-width behavior
- Insets
- Scroll reachability
- Keyboard behavior
- Light/dark mode
- Accessibility
- Responsive layout

Never report a check as passed without performing it.

---

# 28. VISUAL REGRESSION TEST MATRIX

Where emulator or device tooling is available, verify representative UI states across:

| Dimension | Test cases |
|---|---|
| Theme | Light / Dark |
| Color mode | Brand Orange / Dynamic |
| Window width | Compact / Medium / Expanded |
| Orientation | Portrait / Landscape |
| Font scale | Normal / Large / Accessibility |
| Navigation | Gesture / Three-button |
| Keyboard | Hidden / Visible |
| Direction | LTR / RTL where relevant |
| Window mode | Fullscreen / Split-screen where available |

Look for:

- Clipped text
- Broken layout
- Excessive padding
- Unused width
- Overlapping controls
- Incorrect system-bar colors
- Duplicated insets
- Horizontal overflow
- Unreachable actions
- Broken carousels
- Scroll jumps
- Misaligned list rows
- Inconsistent typography
- Incorrect theme colors

Capture screenshots where tooling permits.

Do not claim visual inspection without actual evidence.

---

# 29. STRICT ANTI-MESS ENFORCEMENT

The following are unacceptable:

- Mass file renaming without reference updates
- Unnecessary package restructuring
- Duplicate classes left after renaming
- Broken resource identifiers
- Randomly chosen orange colors
- Glass effects remaining unintentionally
- Giant rounded cards everywhere
- Excessive top padding
- Excessive bottom whitespace
- Oversized persistent headers
- Excessive animation
- Unnecessary UI gradients
- Multiple competing themes
- Hardcoded presentation values in every screen
- Arbitrary fixed screen widths
- Phone layouts stretched on tablets
- Double-applied system insets
- Bottom navigation covering content
- Keyboard obscuring inputs
- Inconsistent screen margins
- Unreadable compact controls
- Broken back navigation
- Removed functionality
- Placeholder buttons
- Unnecessary dependency upgrades
- Unverified experimental APIs
- Fake build completion claims
- Entire project rewritten without reason
- Unrelated user modifications discarded

Every change must have a clear engineering or design justification.

---

# 30. FINAL COMPLETION CHECKLIST

Do not declare the project complete unless all applicable criteria have been verified.

## Design

- [ ] Complete Material 3 Expressive design-system migration
- [ ] Cohesive orange brand identity
- [ ] Accessible light and dark themes
- [ ] Optional dynamic color support where appropriate
- [ ] Consistent typography and shapes
- [ ] No unintentional obsolete glass styling
- [ ] Consistent components across screens

## Compactness

- [ ] Efficient information density
- [ ] Minimal wasted vertical space
- [ ] Compact lists and cards
- [ ] Appropriate toolbar sizing
- [ ] Comfortable accessible controls
- [ ] No unnecessary oversized padding

## Responsiveness

- [ ] Effective phone width utilization
- [ ] Correct adaptive large-screen layouts
- [ ] Landscape support
- [ ] Window resizing support
- [ ] No unintended horizontal overflow
- [ ] Accessible large-text reflow

## System Integration

- [ ] Correct edge-to-edge behavior
- [ ] Correct status/navigation bar handling
- [ ] No duplicate inset padding
- [ ] Correct keyboard handling
- [ ] System appearance respected
- [ ] Gesture navigation supported
- [ ] Three-button navigation supported
- [ ] Predictive back supported where applicable

## Components

- [ ] Lists modernized
- [ ] Appropriate carousels implemented
- [ ] Navigation consistent
- [ ] Dialogs and sheets consistent
- [ ] Forms and selection controls consistent
- [ ] Motion appropriate and restrained

## Codebase

- [ ] Relevant source files audited
- [ ] Misleading names corrected
- [ ] Resource names standardized
- [ ] Obsolete glass naming corrected
- [ ] Imports and references updated
- [ ] No unnecessary duplicate files
- [ ] No broken external contracts
- [ ] Renaming map documented
- [ ] Existing architecture preserved

## Functionality

- [ ] All existing screens accounted for
- [ ] Navigation verified
- [ ] Business logic preserved
- [ ] Data handling preserved
- [ ] User preferences preserved
- [ ] No placeholder functionality introduced

## Validation

- [ ] Available build checks executed
- [ ] Relevant tests executed
- [ ] No known introduced critical regression
- [ ] Screenshots inspected where possible
- [ ] Unverified requirements explicitly documented

---

# 31. FINAL DELIVERABLES

Provide an engineering report containing:

1. Complete migration overview.
2. Screens migrated.
3. Components modernized.
4. Design-system file locations.
5. Orange light/dark theme behavior.
6. Compact layout improvements.
7. Full-width and edge-to-edge improvements.
8. Responsive layout improvements.
9. System-integration changes.
10. File and class renaming summary.
11. Package and resource organization changes.
12. Existing functionality verification.
13. Exact build and test results.
14. Known issues and limitations.
15. Remaining unverified items.

Do not label incomplete work as finished.

---

# 32. FINAL AUTONOMOUS EXECUTION DIRECTIVE

**BEGIN IMPLEMENTATION USING THE ACTUAL OPEN PROJECT.**

First inspect the repository, architecture, current UI system, resources, and dependencies.

Create the migration inventory and naming audit.

Establish a centralized orange Material 3 Expressive design foundation.

Implement compact spacing, intelligent full-width responsive layouts, and correct Android edge-to-edge behavior.

Modernize reusable components before performing broad screen changes.

Migrate every applicable screen systematically.

Apply dynamic lists and carousels only where appropriate.

Introduce expressive motion without sacrificing performance.

Professionally rename misleading, obsolete, or inconsistent files, classes, components, resources, and packages using safe reference-aware refactoring.

Preserve the application ID, existing-user compatibility, persistence contracts, business logic, navigation, and all existing features.

Keep changes incremental and verify them frequently.

Maintain persistent migration documentation.

Use official Android APIs compatible with the actual project.

Avoid routine clarification questions when answers can be determined from the codebase.

Do not stop at changing colors or redesigning a few screens.

Do not invent test results.

Do not declare completion until the applicable acceptance criteria are satisfied.

## THE REQUIRED FINAL RESULT

**A complete, cohesive, production-quality Android application using Material 3 Expressive, a premium orange brand identity, compact information-dense interfaces, intelligent full-width layouts, responsive adaptive components, correct system integration, fluid native motion, accessible interactions, professionally named files and resources, clean code organization, and fully preserved existing functionality.**

### CORE ENGINEERING PRINCIPLE

**MAXIMUM USABLE SPACE + MINIMUM VISUAL CLUTTER + CONSISTENT MATERIAL 3 EXPRESSIVE DESIGN + NATIVE ANDROID BEHAVIOR + SAFE PROFESSIONAL REFACTORING + ZERO INTENTIONAL FUNCTIONALITY LOSS.**
