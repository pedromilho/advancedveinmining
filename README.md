# Advanced Vein Mining

Open the settings menu:

```text
/veinminer
/advancedveinminer
/advancedveinmining
/veinmining
```

Manage an online player's access (requires `advancedveinmining.permissions`):

```text
/advancedveinmining <nickname> modify on
/advancedveinmining <nickname> modify off
/advancedveinmining <nickname> modify readonly
```

The same permission syntax works with `/veinminer`, `/advancedveinminer`, and `/veinmining`.

`on` allows viewing and editing; `readonly` allows viewing settings but blocks the player permissions list; `off` blocks the menu.
**Save** applies changes without closing the menu; **Close** exits without applying unsaved changes.
The **Player permissions** button shows online players with Modify/Read-only checks.
The world host is marked `(owner)` and listed first; administrators are marked `(admin)`.
Player filters include admins, modify access and read-only access. Permission changes apply immediately.

Permission nodes: `advancedveinmining.config` (edit), `advancedveinmining.readonly` (view), `advancedveinmining.permissions` (manage access).

The owner always retains full access and their permission checks are locked.

Permission commands default to **Admin only**. In the player permissions page, administrators can choose **All** to allow everyone to use those commands. This policy is saved across restarts and never allows changing the owner.

[All Rights Reserved](LICENSE).
