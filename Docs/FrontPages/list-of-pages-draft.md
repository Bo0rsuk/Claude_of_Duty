# List of pages

## General idea

The idea is that every app-user can manage their own view/alligement of widgets.
This is done by drag-and-drop of widgets selected form drop-down lists of possible widgets.
There is a collapsable sidebar with selection of main panels.
The widgets may also be nested according to their origin (eg. user-based widgets inside the user management widget).
Or grouped according to app-user preferences.

## List of proposed widgets

There are panels for admin/ team lead/ user - this is accessibility constraint.
User is the lowest authority, team lead is higher and admin is the highest.
Each higher authority level can display panels of all lower authority levels.
For example: "User has some A, B, C panels. Team lead has D, E panels and access to all user's panels as well. Admin have even more panels and access to the rest."
Each panel have pool of its widgets. For example user-related widgets, alert-related widgets etc.

### Panels for admin:

### Panels for team lead:
- PANEL `Rules`
    - `Rule list` allows to filter and select a rule for which it feed the subwidgets:
        - `Rule editor`
            - `Filter assistant` allows to filter what it captures
            - `Severity selector` Critical, Warning, Info with prechoosen alert types. We can edit alert types manually as well.
            - `Instructions` steps that app-user should take when he/she receives alert triggered by this rule
- PANEL `Users`
    - `User list` allows to filter and select a user for which it feed the subwidgets:
        - `Manage user`
        - `Asign rules`
### Panels for user:
- PANEL `Alerts` allows filtering and selection of specific event that can be managed in widgets
    - `AlertList` allows to filter and select an alert for which it feed the subwidgets:
        - `AlertDetails` displaying details
        - `ManageAlert` manage alert (delete, resend, pass to another user, flag as resolved, undertaken etc.)
        - `Instructions` display steps how to resolve the problem    
- PANEL `Custom` this has access to all possible allowed widgets
