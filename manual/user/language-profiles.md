---
label: Language profiles
order: 850
icon: globe
---

# Language profiles

A **language profile** is one language you learn. You can have several, also of the same language (for example
*German - work* and *German - travel*): each one keeps its own things apart from the others.

A profile has:

| Field            |                                                                                                    |
|------------------|----------------------------------------------------------------------------------------------------|
| **Template**     | The language: **Japanese** or **German** for now. The template decides the flag and which screens the *Learning* tab shows. It cannot be changed later. |
| **Profile name** | Your own name for the profile. It starts as the name of the template.                              |
| **Note**         | Free text shown below the name in the selector, for example *evening class*.                       |

## The first profile

A new account has no profile. The first time you open the workspace, the **New language** window opens and does not
close until you create one: pick the **Language**, change the **Profile name** if you want, and click **OK**.

## Switching profiles

The selector at the top of the **Learning** tab lists your profiles with their flags and notes. Choose one to work in
it. LingoLLM remembers the profile you left on and opens it again at the next login. Whatever you create while a profile
is selected belongs to it.

The **+** button next to the selector opens **New language**, where the existing profiles are listed above the form.

!!!
The selector is part of the *Learning* tab, so you cannot switch while another tab is open; the other tabs
(*Settings*, *Inference Providers*...) are the same for all profiles.
!!!

## What is in a profile

Below the selector the **Learning** tab has the tabs of the profile (on a phone they scroll sideways):

- **Level** - the level you have in the language, as a CEFR level from A1 to C2, and your impressions: what you can and
  cannot do, your strong and weak points. The lessons are fitted to it. LingoLLM prefers that you take the grade test
  (**Take Grade Test**, the robot button) instead of choosing the level by hand; fill it in yourself only when you are sure.
  Click **Save** to keep it. The grade test itself is not available yet.
- **Free chat** - not available yet.

A profile that has no level yet shows only the **Level** tab; the other tabs unlock when you save the level.

The language screens are still being built. The data model for **free chats** exists - a conversation is a tree of
messages, and attachments are links to your [resources](resources.md) that survive the deletion of the resource - but
there is no chat screen yet.
