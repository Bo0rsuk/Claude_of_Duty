# List of pages

## General idea

The idea is that every app-user can manage their own view/alligement of widgets.
This is done by drag-and-drop of widgets selected form drop-down lists of possible widgets.
The widgets may also be nested according to their origin (eg. user-based widgets inside the user management widget).
Or grouped according to app-user preferences.

## List of proposed widgets

Widgets are grouped into panels for admin/ team lead/ user - this is accessibility constraint.
User is the lowest authority, team lead is higher and admin is the highest.
Each higher authority level can display widgets of all lower authority levels.
For example: "User has some A, B, C widgets. Team lead has D, E widgets and access to all user's widgets as well. Admin have even more widgets and access to the rest."
Furthermore, widgets are divided into categories. For example user-related widgets, event-related widgets etc. Each category has its own pool of subwidgets that are automatically grouped and are accessible (aka displayable aka turned on/off) only through its master widget.

### Panel for admin:

### Panel for team lead:
- `RuleList`
- `UserList`
### Panel for user:
- `EventList` allows filtering and selection of specific event that can be managed in subwidgets
    - `EventDetails` displaying details, manage widget (delete, resend, pass to another user, flag as resolved, undertaken etc.)
