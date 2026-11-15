# Stable window navigation

Updated 2026-11-15. Navigation keeps the current window size, position and window
mode, including manual resizing, maximization and fullscreen. Users can still resize
the window normally. The initial scene remains 1280×800 with the existing minimum
window dimensions defined in App; navigating does not reapply a default size.

SceneNavigator reuses the Stage's existing Scene and replaces its root content.
Back stores/restores view roots and titles, preserving form contents without restoring
an old viewport size. Show, Replace, fallback Back and Clear-and-show all share this
display path. Sign-out and role/workspace changes retain window geometry while
clearing navigation and undo history according to the existing behavior.

Buyer Overview, My Demands, Supplier Overview and connected Admin Overview use
SceneNavigator.onShown to detect both initial display and restored cached content.
The callback detaches the previous scene's window listener. This preserves refresh
on Back and avoids accumulating listeners on the reused Scene. The Admin supply
composition widget also removes detached scene listeners; polling stops when its
root leaves the scene and restarts when restored.

The FXML navigation test checks width, height and position around actions across
Buyer, Admin, Supplier, details, Back and logout. It resolves CSS/layout before
looking up controls on newly attached content. Native-window checks additionally
covered manual resizing before Back, retained draft state, display callbacks,
chart polling, maximize/fullscreen navigation and restoring the user's normal bounds.
No database or business lifecycle changes are involved.
